/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.io;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

public class FakeExecutableFinder extends ExecutableFinder {

  private final ImmutableCollection<Path> knownPaths;

  public FakeExecutableFinder(Path... knownPaths) {
    this(ImmutableList.copyOf(knownPaths));
  }

  public FakeExecutableFinder(ImmutableCollection<Path> knownPaths) {
    this.knownPaths = knownPaths;
  }

  @Override
  public Optional<Path> getOptionalExecutable(
      Path suggestedPath,
      ImmutableCollection<Path> searchPath,
      ImmutableCollection<String> fileSuffixes) {
    for (Path path : knownPaths) {
      if (suggestedPath.equals(path.getFileName())) {
        return Optional.of(path);
      }
    }
    System.out.println("suggestedPath = " + suggestedPath);
    return Optional.empty();
  }
}
