/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.zip;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SrcZipAwareFileBundler {

  private final Path basePath;

  public SrcZipAwareFileBundler(BuildTarget target) {
    this(target.getBasePath());
  }

  public SrcZipAwareFileBundler(Path basePath) {
    this.basePath = Preconditions.checkNotNull(basePath);
  }

  private void findAndAddRelativePathToMap(
      Path absoluteFilePath,
      Path relativeFilePath,
      Path assumedAbsoluteBasePath,
      Map<Path, Path> relativePathMap) {
    Path pathRelativeToBaseDir;

    if (relativeFilePath.startsWith(basePath)) {
      pathRelativeToBaseDir = basePath.relativize(relativeFilePath);
    } else {
      pathRelativeToBaseDir = assumedAbsoluteBasePath.relativize(absoluteFilePath);
    }

    if (relativePathMap.containsKey(pathRelativeToBaseDir)) {
      throw new HumanReadableException(
          "The file '%s' appears twice in the hierarchy", pathRelativeToBaseDir.getFileName());
    }
    relativePathMap.put(pathRelativeToBaseDir, absoluteFilePath);
  }

  private ImmutableMap<Path, Path> createRelativeMap(
      ProjectFilesystem filesystem,
      final SourcePathResolver resolver,
      ImmutableSortedSet<SourcePath> toCopy) {
    Map<Path, Path> relativePathMap = new HashMap<>();

    for (SourcePath sourcePath : toCopy) {
      Path absoluteBasePath = resolver.getAbsolutePath(sourcePath);
      try {
        if (Files.isDirectory(absoluteBasePath)) {
          ImmutableSet<Path> files = filesystem.getFilesUnderPath(absoluteBasePath);
          Path absoluteBasePathParent = absoluteBasePath.getParent();

          for (Path file : files) {
            Path absoluteFilePath = filesystem.resolve(file);

            findAndAddRelativePathToMap(
                absoluteFilePath, file, absoluteBasePathParent, relativePathMap);
          }
        } else {
          findAndAddRelativePathToMap(
              absoluteBasePath,
              resolver.getRelativePath(sourcePath),
              absoluteBasePath.getParent(),
              relativePathMap);
        }
      } catch (IOException e) {
        throw new RuntimeException(
            String.format("Couldn't read directory [%s].", absoluteBasePath.toString()), e);
      }
    }

    return ImmutableMap.copyOf(relativePathMap);
  }

  public void copy(
      ProjectFilesystem filesystem,
      final SourcePathResolver resolver,
      ImmutableList.Builder<Step> steps,
      Path destinationDir,
      ImmutableSortedSet<SourcePath> toCopy) {

    Map<Path, Path> relativeMap = createRelativeMap(filesystem, resolver, toCopy);

    for (Map.Entry<Path, Path> pathEntry : relativeMap.entrySet()) {
      Path relativePath = pathEntry.getKey();
      Path absolutePath = Preconditions.checkNotNull(pathEntry.getValue());
      Path destination = destinationDir.resolve(relativePath);

      if (relativePath.toString().endsWith(Javac.SRC_ZIP)
          || relativePath.toString().endsWith(Javac.SRC_JAR)) {
        steps.add(new UnzipStep(filesystem, absolutePath, destination.getParent()));
        continue;
      }

      if (destination.getParent() != null) {
        steps.add(MkdirStep.of(filesystem, destination.getParent()));
      }
      steps.add(CopyStep.forFile(filesystem, absolutePath, destination));
    }
  }
}
