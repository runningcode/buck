/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.dalvik;

import com.facebook.buck.jvm.java.classes.FileLike;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Helper to write a Zip file used by {@link DalvikAwareZipSplitter}. */
public class DalvikAwareOutputStreamHelper implements ZipOutputStreamHelper {

  private static final int MAX_METHOD_REFERENCES = 64 * 1024;
  // Making this 60k for now instead of 64 because the analyzer doesn't find all field references.
  // This only comes into play in rare cases, so it's not hi-pri to fix.
  private static final int MAX_FIELD_REFERENCES = 60 * 1024;

  private final ZipOutputStream outStream;
  private final Set<String> entryNames = Sets.newHashSet();
  private final long linearAllocLimit;
  private final Writer reportFileWriter;
  private final DalvikStatsCache dalvikStatsCache;

  private final Set<DalvikStatsTool.MethodReference> currentMethodReferences = Sets.newHashSet();
  private final Set<DalvikStatsTool.FieldReference> currentFieldReferences = Sets.newHashSet();
  private long currentLinearAllocSize;

  DalvikAwareOutputStreamHelper(
      Path outputFile, long linearAllocLimit, Path reportDir, DalvikStatsCache dalvikStatsCache)
      throws IOException {
    this.outStream =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)));
    this.linearAllocLimit = linearAllocLimit;
    Path reportFile = reportDir.resolve(outputFile.getFileName().toString() + ".txt");
    this.reportFileWriter = Files.newBufferedWriter(reportFile, Charsets.UTF_8);
    this.dalvikStatsCache = dalvikStatsCache;
  }

  private boolean isEntryTooBig(FileLike entry) {
    DalvikStatsTool.Stats stats = dalvikStatsCache.getStats(entry);
    if (currentLinearAllocSize + stats.estimatedLinearAllocSize > linearAllocLimit) {
      return true;
    }
    int newMethodRefs = Sets.difference(stats.methodReferences, currentMethodReferences).size();
    if (currentMethodReferences.size() + newMethodRefs > MAX_METHOD_REFERENCES) {
      return true;
    }
    int newFieldRefs = Sets.difference(stats.fieldReferences, currentFieldReferences).size();
    if (currentFieldReferences.size() + newFieldRefs > MAX_FIELD_REFERENCES) {
      return true;
    }
    return false;
  }

  @Override
  public boolean canPutEntry(FileLike fileLike) {
    return !isEntryTooBig(fileLike);
  }

  @Override
  public boolean containsEntry(FileLike fileLike) {
    return entryNames.contains(fileLike.getRelativePath());
  }

  @Override
  public void putEntry(FileLike fileLike) throws IOException {
    String name = fileLike.getRelativePath();
    // Tracks unique entry names and avoids duplicates.  This is, believe it or not, how
    // proguard seems to handle merging multiple -injars into a single -outjar.
    if (!containsEntry(fileLike)) {
      entryNames.add(name);
      outStream.putNextEntry(new ZipEntry(name));
      try (InputStream in = fileLike.getInput()) {
        ByteStreams.copy(in, outStream);
      }

      // Make sure FileLike#getSize didn't lie (or we forgot to call canPutEntry).
      DalvikStatsTool.Stats stats = dalvikStatsCache.getStats(fileLike);
      Preconditions.checkState(
          !isEntryTooBig(fileLike),
          "Putting entry %s (%s) exceeded maximum size of %s",
          name,
          stats.estimatedLinearAllocSize,
          linearAllocLimit);
      currentLinearAllocSize += stats.estimatedLinearAllocSize;
      currentMethodReferences.addAll(stats.methodReferences);
      currentFieldReferences.addAll(stats.fieldReferences);
      String report =
          String.format(
              "%d %d %s\n", stats.estimatedLinearAllocSize, stats.methodReferences.size(), name);
      reportFileWriter.append(report);
    }
  }

  @Override
  public void close() throws IOException {
    outStream.close();
    reportFileWriter.close();
  }
}
