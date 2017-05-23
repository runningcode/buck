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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Helper to write to secondary DEX files. */
abstract class SecondaryDexHelper<ZIP_OUTPUT_STREAM_HELPER extends ZipOutputStreamHelper> {

  private final String storeName;
  private final Path outSecondaryDir;
  private final String secondaryPattern;
  private final ZipSplitter.CanaryStrategy canaryStrategy;

  private int currentSecondaryIndex;

  @Nullable private ZIP_OUTPUT_STREAM_HELPER currentSecondaryOut;
  private boolean newSecondaryOutOnNextEntry;
  private ImmutableList.Builder<Path> secondaryFiles;

  SecondaryDexHelper(
      String storeName,
      Path outSecondaryDir,
      String secondaryPattern,
      ZipSplitter.CanaryStrategy canaryStrategy) {
    this.storeName = storeName;
    this.outSecondaryDir = outSecondaryDir;
    this.secondaryPattern = secondaryPattern;
    this.canaryStrategy = canaryStrategy;
    this.secondaryFiles = ImmutableList.builder();
  }

  void reset() {
    currentSecondaryIndex = 0;
    currentSecondaryOut = null;
    secondaryFiles = ImmutableList.builder();
  }

  ZIP_OUTPUT_STREAM_HELPER getOutputToWriteTo(FileLike entry) throws IOException {
    // Going to write this entry to a secondary zip.
    if (currentSecondaryOut == null
        || !currentSecondaryOut.canPutEntry(entry)
        || newSecondaryOutOnNextEntry) {
      if (currentSecondaryOut != null) {
        currentSecondaryOut.close();
      }
      currentSecondaryIndex++;
      Path newSecondaryFile =
          outSecondaryDir.resolve(String.format(secondaryPattern, currentSecondaryIndex));
      secondaryFiles.add(newSecondaryFile);
      currentSecondaryOut = newZipOutput(newSecondaryFile);
      newSecondaryOutOnNextEntry = false;
      if (canaryStrategy == ZipSplitter.CanaryStrategy.INCLUDE_CANARIES) {
        // Make sure the first class in the new secondary dex can be safely loaded.
        FileLike canaryFile = CanaryFactory.create(storeName, currentSecondaryIndex);
        currentSecondaryOut.putEntry(canaryFile);
      }
      // We've already tested for this. It really shouldn't happen.
      Preconditions.checkState(currentSecondaryOut.canPutEntry(entry));
    }

    return currentSecondaryOut;
  }

  void close() throws IOException {
    if (currentSecondaryOut != null) {
      currentSecondaryOut.close();
    }
  }

  ImmutableList<Path> getFiles() {
    return secondaryFiles.build();
  }

  protected abstract ZIP_OUTPUT_STREAM_HELPER newZipOutput(Path file) throws IOException;
}
