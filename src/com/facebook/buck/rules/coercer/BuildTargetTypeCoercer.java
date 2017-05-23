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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.UnflavoredBuildTarget;
import com.facebook.buck.parser.BuildTargetParseException;
import com.facebook.buck.parser.BuildTargetParser;
import com.facebook.buck.parser.BuildTargetPatternParser;
import com.facebook.buck.rules.CellPathResolver;
import java.nio.file.Path;

public class BuildTargetTypeCoercer extends LeafTypeCoercer<BuildTarget> {

  @Override
  public Class<BuildTarget> getOutputClass() {
    return BuildTarget.class;
  }

  @Override
  public BuildTarget coerce(
      CellPathResolver cellRoots,
      ProjectFilesystem alsoUnused,
      Path pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (!(object instanceof String)) {
      throw CoerceFailedException.simple(object, getOutputClass());
    }
    String param = (String) object;

    try {
      String baseName =
          UnflavoredBuildTarget.BUILD_TARGET_PREFIX
              + MorePaths.pathWithUnixSeparators(pathRelativeToProjectRoot);

      return BuildTargetParser.INSTANCE.parse(
          param, BuildTargetPatternParser.forBaseName(baseName), cellRoots);
    } catch (BuildTargetParseException e) {
      throw new CoerceFailedException(
          String.format(
              "Failed to coerce %s to %s, %s",
              object, getOutputClass(), e.getHumanReadableErrorMessage()),
          e);
    }
  }
}
