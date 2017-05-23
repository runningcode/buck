/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.shell;

import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.util.Escaper;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.environment.Platform;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WorkerProcessPoolFactory class is designed to provide you an instance of WorkerProcessPool based
 * on the params for the job that you provide. It manages that pool, that is, creates one if it is
 * missing. You then may use the resulting pool to borrow new WorkerProcess instances from it to
 * perform your job.
 */
public class WorkerProcessPoolFactory {

  private final ProjectFilesystem filesystem;

  public WorkerProcessPoolFactory(ProjectFilesystem filesystem) {
    this.filesystem = filesystem;
  }

  /**
   * Returns an existing WorkerProcessPool for the given job params if one exists, otherwise creates
   * a new one.
   */
  public WorkerProcessPool getWorkerProcessPool(
      final ExecutionContext context, WorkerProcessParams paramsToUse) {
    ConcurrentMap<String, WorkerProcessPool> processPoolMap;
    final String key;
    final HashCode workerHash;
    if (paramsToUse.getPersistentWorkerKey().isPresent()
        && context.getPersistentWorkerPools().isPresent()) {
      processPoolMap = context.getPersistentWorkerPools().get();
      key = paramsToUse.getPersistentWorkerKey().get();
      workerHash = paramsToUse.getWorkerHash().get();
    } else {
      processPoolMap = context.getWorkerProcessPools();
      key = Joiner.on(' ').join(getCommand(context.getPlatform(), paramsToUse));
      workerHash = Hashing.sha1().hashString(key, StandardCharsets.UTF_8);
    }

    // If the worker pool has a different hash, recreate the pool.
    WorkerProcessPool pool = processPoolMap.get(key);
    if (pool != null && !pool.getPoolHash().equals(workerHash)) {
      if (processPoolMap.remove(key, pool)) {
        pool.close();
      }
      pool = processPoolMap.get(key);
    }

    if (pool == null) {
      pool = createWorkerProcessPool(context, paramsToUse, processPoolMap, key, workerHash);
    }

    int poolCapacity = pool.getCapacity();
    if (poolCapacity != paramsToUse.getMaxWorkers()) {
      context.postEvent(
          ConsoleEvent.warning(
              "There are two 'worker_tool' targets declared with the same command (%s), but "
                  + "different 'max_worker' settings (%d and %d). Only the first capacity is applied. "
                  + "Consolidate these workers to avoid this warning.",
              key, poolCapacity, paramsToUse.getMaxWorkers()));
    }

    return pool;
  }

  private WorkerProcessPool createWorkerProcessPool(
      final ExecutionContext context,
      final WorkerProcessParams paramsToUse,
      ConcurrentMap<String, WorkerProcessPool> processPoolMap,
      String key,
      final HashCode workerHash) {
    final ProcessExecutorParams processParams =
        ProcessExecutorParams.builder()
            .setCommand(getCommand(context.getPlatform(), paramsToUse))
            .setEnvironment(getEnvironmentForProcess(context, paramsToUse))
            .setDirectory(filesystem.getRootPath())
            .build();

    final Path workerTmpDir = paramsToUse.getTempDir();
    final AtomicInteger workerNumber = new AtomicInteger(0);

    WorkerProcessPool newPool =
        new WorkerProcessPool(paramsToUse.getMaxWorkers(), workerHash) {
          @Override
          protected WorkerProcess startWorkerProcess() throws IOException {
            Path tmpDir = workerTmpDir.resolve(Integer.toString(workerNumber.getAndIncrement()));
            filesystem.mkdirs(tmpDir);
            WorkerProcess process = createWorkerProcess(processParams, context, tmpDir);
            process.ensureLaunchAndHandshake();
            return process;
          }
        };
    WorkerProcessPool previousPool = processPoolMap.putIfAbsent(key, newPool);
    // If putIfAbsent does not return null, then that means another thread beat this thread
    // into putting an WorkerProcessPool in the map for this key. If that's the case, then we
    // should ignore newPool and return the existing one.
    return previousPool == null ? newPool : previousPool;
  }

  public ImmutableList<String> getCommand(Platform platform, WorkerProcessParams paramsToUse) {
    ImmutableList<String> executionArgs =
        platform == Platform.WINDOWS
            ? ImmutableList.of("cmd.exe", "/c")
            : ImmutableList.of("/bin/bash", "-e", "-c");

    return ImmutableList.<String>builder()
        .addAll(executionArgs)
        .add(
            FluentIterable.from(paramsToUse.getStartupCommand())
                .transform(Escaper.SHELL_ESCAPER)
                .append(paramsToUse.getStartupArgs())
                .join(Joiner.on(' ')))
        .build();
  }

  @VisibleForTesting
  ImmutableMap<String, String> getEnvironmentForProcess(
      ExecutionContext context, WorkerProcessParams workerJobParams) {
    Path tmpDir = workerJobParams.getTempDir();

    Map<String, String> envVars = Maps.newHashMap(context.getEnvironment());
    envVars.put("TMP", filesystem.resolve(tmpDir).toString());
    envVars.putAll(workerJobParams.getStartupEnvironment());
    return ImmutableMap.copyOf(envVars);
  }

  @VisibleForTesting
  WorkerProcess createWorkerProcess(
      ProcessExecutorParams processParams, ExecutionContext context, Path tmpDir)
      throws IOException {
    return new WorkerProcess(context.getProcessExecutor(), processParams, filesystem, tmpDir);
  }
}
