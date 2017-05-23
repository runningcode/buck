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

import com.facebook.buck.artifact_cache.ArtifactInfo;
import com.facebook.buck.artifact_cache.CacheReadMode;
import com.facebook.buck.artifact_cache.CacheResult;
import com.facebook.buck.artifact_cache.CacheResultType;
import com.facebook.buck.artifact_cache.DirArtifactCache;
import com.facebook.buck.distributed.thrift.BuildJobStateFileHashEntry;
import com.facebook.buck.io.BorrowablePath;
import com.facebook.buck.io.LazyPath;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.rules.RuleKey;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class LocalFsContentsProvider implements FileContentsProvider {
  private static final String CACHE_NAME = "stampede_source_dircache";

  private final DirArtifactCache dirCache;

  public LocalFsContentsProvider(Path cacheDirAbsPath) throws InterruptedException, IOException {
    Preconditions.checkArgument(
        Files.isDirectory(cacheDirAbsPath),
        "The cache directory must exist. cacheDirAbsPath=[%s]",
        cacheDirAbsPath);
    this.dirCache =
        new DirArtifactCache(
            CACHE_NAME,
            new ProjectFilesystem(cacheDirAbsPath),
            Paths.get(CACHE_NAME),
            CacheReadMode.READWRITE,
            Optional.empty());
  }

  @Override
  public boolean materializeFileContents(BuildJobStateFileHashEntry entry, Path targetAbsPath)
      throws IOException {
    RuleKey key = new RuleKey(entry.getHashCode());
    CacheResult cacheResult = dirCache.fetch(key, LazyPath.ofInstance(targetAbsPath));
    return cacheResult.getType() == CacheResultType.HIT;
  }

  public void writeFileAndGetInputStream(BuildJobStateFileHashEntry entry, Path absPath)
      throws IOException {
    RuleKey key = new RuleKey(entry.getHashCode());
    ArtifactInfo artifactInfo = ArtifactInfo.builder().setRuleKeys(ImmutableList.of(key)).build();
    BorrowablePath nonBorrowablePath = BorrowablePath.notBorrowablePath(absPath);
    try {
      dirCache.store(artifactInfo, nonBorrowablePath).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException("Failed to store artifact to DirCache.", e);
    }
  }
}
