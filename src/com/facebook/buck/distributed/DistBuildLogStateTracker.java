/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.distributed;

import com.facebook.buck.distributed.thrift.BuildSlaveInfo;
import com.facebook.buck.distributed.thrift.LogDir;
import com.facebook.buck.distributed.thrift.LogLineBatch;
import com.facebook.buck.distributed.thrift.LogLineBatchRequest;
import com.facebook.buck.distributed.thrift.LogStreamType;
import com.facebook.buck.distributed.thrift.RunId;
import com.facebook.buck.distributed.thrift.SlaveStream;
import com.facebook.buck.distributed.thrift.StreamLogs;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.NamedTemporaryFile;
import com.facebook.buck.zip.Unzip;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DistBuildLogStateTracker implements Closeable {
  private static final Logger LOG = Logger.get(DistBuildLogStateTracker.class);
  private static final List<LogStreamType> SUPPORTED_STREAM_TYPES =
      ImmutableList.of(LogStreamType.STDOUT, LogStreamType.STDERR);

  private final Path logDirectoryPath;
  private final ProjectFilesystem filesystem;
  private Map<SlaveStream, SlaveStreamState> seenSlaveLogs = new HashMap<>();
  private Set<String> createdLogDirRootsByRunId = Sets.newHashSet();

  public DistBuildLogStateTracker(Path logDirectoryPath, ProjectFilesystem filesystem) {
    this.logDirectoryPath = logDirectoryPath;
    this.filesystem = filesystem;
  }

  public List<LogLineBatchRequest> createRealtimeLogRequests(
      Collection<BuildSlaveInfo> latestBuildSlaveInfos) {
    List<LogLineBatchRequest> requests = new ArrayList<>();
    for (LogStreamType streamType : SUPPORTED_STREAM_TYPES) {
      for (BuildSlaveInfo buildSlaveInfo : latestBuildSlaveInfos) {
        createRealtimeLogRequests(buildSlaveInfo, streamType, requests);
      }
    }
    return requests;
  }

  public void processStreamLogs(List<StreamLogs> multiStreamLogs) {
    for (StreamLogs streamLogs : multiStreamLogs) {
      if (streamLogs.isSetErrorMessage()) {
        LOG.error(
            "Failed to get stream logs for runId [%]. Error: %s",
            streamLogs.slaveStream.runId, streamLogs.errorMessage);

        continue;
      }

      processStreamLogs(streamLogs);
    }
  }

  public List<RunId> runIdsToMaterializeLogDirsFor(
      Collection<BuildSlaveInfo> latestBuildSlaveInfos) {
    List<RunId> runIds = new ArrayList<>();
    for (BuildSlaveInfo buildSlaveInfo : latestBuildSlaveInfos) {
      if (!buildSlaveInfo.isSetLogDirZipWritten()) {
        LOG.error("No log dir written for runId [%s]", buildSlaveInfo.runId);
        continue;
      }

      runIds.add(buildSlaveInfo.runId);
    }

    return runIds;
  }

  public void materializeLogDirs(List<LogDir> logDirs) {
    for (LogDir logDir : logDirs) {
      if (logDir.isSetErrorMessage()) {
        LOG.error(
            "Failed to fetch log dir for runId [%s]. Error: %s", logDir.runId, logDir.errorMessage);
        continue;
      }

      try {
        writeLogDirToDisk(logDir);
      } catch (IOException e) {
        LOG.error(e, "Erorr while materializing log dir for runId [%s]", logDir.runId);
      }
    }
  }

  @Override
  public void close() throws IOException {}

  /*
   *******************************
   *  Helpers
   *******************************
   */

  private void processStreamLogs(StreamLogs streamLogs) {
    if (!seenSlaveLogs.containsKey(streamLogs.slaveStream)) {
      seenSlaveLogs.put(streamLogs.slaveStream, new SlaveStreamState());
    }

    SlaveStreamState seenStreamState =
        Preconditions.checkNotNull(seenSlaveLogs.get(streamLogs.slaveStream));

    LogLineBatch lastReceivedBatch =
        streamLogs.logLineBatches.get(streamLogs.logLineBatches.size() - 1);

    if (seenStreamState.seenBatchNumber > lastReceivedBatch.batchNumber
        || (seenStreamState.seenBatchNumber == lastReceivedBatch.batchNumber
            && seenStreamState.seenBatchLineCount >= lastReceivedBatch.lines.size())) {
      LOG.warn(
          "Received stale logs for runID [%s] and stream [%s]",
          streamLogs.slaveStream.runId, streamLogs.slaveStream.streamType);
      return;
    }

    // Determines which log lines need writing, and then writes them to disk.
    List<String> newLines = new ArrayList<>();
    for (LogLineBatch batch : streamLogs.logLineBatches) {
      if (batch.batchNumber < seenStreamState.seenBatchNumber) {
        continue;
      }

      if (batch.batchNumber == seenStreamState.seenBatchNumber) {
        if (batch.lines.size() == seenStreamState.seenBatchLineCount) {
          continue;
        }
        newLines.addAll(
            batch.lines.subList(seenStreamState.seenBatchLineCount, batch.lines.size()));
      } else {
        newLines.addAll(batch.lines);
      }
    }

    writeLogStreamLinesToDisk(streamLogs.slaveStream, newLines);

    seenStreamState.seenBatchNumber = lastReceivedBatch.batchNumber;
    seenStreamState.seenBatchLineCount = lastReceivedBatch.lines.size();
  }

  private void createRealtimeLogRequests(
      BuildSlaveInfo buildSlaveInfo, LogStreamType streamType, List<LogLineBatchRequest> requests) {
    RunId runId = buildSlaveInfo.runId;
    SlaveStream slaveStream = new SlaveStream();
    slaveStream.setRunId(runId);
    slaveStream.setStreamType(streamType);

    int latestBatchNumber = getLatestBatchNumber(buildSlaveInfo, streamType);

    // No logs have been created for this slave stream yet
    if (latestBatchNumber == 0) {
      return;
    }

    // Logs exist, but no requests have been made yet => request everything
    if (!seenSlaveLogs.containsKey(slaveStream)) {
      requests.add(createRequest(slaveStream, 1));
      return;
    }

    int latestBatchLineNumber = getLatestBatchLineNumber(buildSlaveInfo, streamType);
    SlaveStreamState seenState = seenSlaveLogs.get(slaveStream);
    // Logs exists, but we have seen them all already.
    if (seenState.seenBatchNumber > latestBatchNumber
        || (seenState.seenBatchNumber == latestBatchNumber
            && seenState.seenBatchLineCount >= latestBatchLineNumber)) {
      return;
    }

    // New logs exists, that we haven't seen yet.
    requests.add(createRequest(slaveStream, seenState.seenBatchNumber));
  }

  private static int getLatestBatchNumber(BuildSlaveInfo buildSlaveInfo, LogStreamType streamType) {
    switch (streamType) {
      case STDOUT:
        return buildSlaveInfo.getStdOutCurrentBatchNumber();
      case STDERR:
        return buildSlaveInfo.getStdErrCurrentBatchNumber();
      case UNKNOWN:
      default:
        throw new RuntimeException("Unsupported stream type: " + streamType.toString());
    }
  }

  private static int getLatestBatchLineNumber(
      BuildSlaveInfo buildSlaveInfo, LogStreamType streamType) {
    switch (streamType) {
      case STDOUT:
        return buildSlaveInfo.getStdOutCurrentBatchLineCount();
      case STDERR:
        return buildSlaveInfo.getStdErrCurrentBatchLineCount();
      case UNKNOWN:
      default:
        throw new RuntimeException("Unsupported stream type: " + streamType.toString());
    }
  }

  private static LogLineBatchRequest createRequest(SlaveStream slaveStream, int batchNumber) {
    LogLineBatchRequest request = new LogLineBatchRequest();
    request.setSlaveStream(slaveStream);
    request.setBatchNumber(batchNumber);
    return request;
  }

  /*
   *******************************
   *  Path utils
   *******************************
   */

  private Path getLogDirForRunId(String runId) {
    Path runIdLogDir =
        filesystem
            .resolve(logDirectoryPath)
            .resolve(String.format(BuckConstant.DIST_BUILD_SLAVE_LOG_DIR_NAME_TEMPLATE, runId));

    if (!createdLogDirRootsByRunId.contains(runId)) {
      try {
        filesystem.mkdirs(runIdLogDir);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      createdLogDirRootsByRunId.add(runId);
    }

    return runIdLogDir;
  }

  private Path getStreamLogFilePath(String runId, String streamType) {
    return getLogDirForRunId(runId).resolve(String.format("%s.log", streamType));
  }

  private Path getBuckOutUnzipPath(String runId) {
    return getLogDirForRunId(runId).resolve("buck-out");
  }

  /*
   *******************************
   *  Streaming log materialization
   *******************************
   */

  private void writeLogStreamLinesToDisk(SlaveStream slaveStream, List<String> newLines) {
    Path outputLogFilePath =
        getStreamLogFilePath(slaveStream.runId.id, slaveStream.streamType.toString());
    try (OutputStream outputStream =
        new BufferedOutputStream(new FileOutputStream(outputLogFilePath.toFile(), true))) {
      for (String logLine : newLines) {
        outputStream.write(logLine.getBytes(Charsets.UTF_8));
      }
      outputStream.flush();
    } catch (IOException e) {
      LOG.debug("Failed to write to %s", outputLogFilePath.toAbsolutePath(), e);
    }
  }

  /*
   *******************************
   *  Log dir re-materialization
   *******************************
   */

  private void writeLogDirToDisk(LogDir logDir) throws IOException {
    if (logDir.data.array().length == 0) {
      LOG.warn(
          "Skipping materialiation of buck-out dir for runId [%s]" + " as content length was zero",
          logDir.runId);
      return;
    }

    Path buckOutUnzipPath = getBuckOutUnzipPath(logDir.runId.id);

    try (NamedTemporaryFile zipFile = new NamedTemporaryFile("runBuckOut", "zip")) {
      Files.write(zipFile.get(), logDir.data.array());
      Unzip.extractZipFile(
          zipFile.get(),
          filesystem,
          buckOutUnzipPath,
          Unzip.ExistingFileMode.OVERWRITE_AND_CLEAN_DIRECTORIES);
    }
  }

  /*
   *******************************
   *  Inner classes
   *******************************
   */

  private static class SlaveStreamState {
    private int seenBatchNumber;
    private int seenBatchLineCount;
  }
}
