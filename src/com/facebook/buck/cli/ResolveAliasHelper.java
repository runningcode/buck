/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.cli;

import com.facebook.buck.json.BuildFileParseException;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.Parser;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Helper class with functionality to resolve alias in `targets` command. */
public class ResolveAliasHelper {
  private ResolveAliasHelper() {}

  /**
   * Assumes each argument passed to this command is an alias defined in .buckconfig, or a fully
   * qualified (non-alias) target to be verified by checking the build files. Prints the build
   * target that each alias maps to on its own line to standard out.
   */
  public static int resolveAlias(
      CommandRunnerParams params,
      ListeningExecutorService executor,
      boolean enableProfiling,
      List<String> aliases) {

    List<String> resolvedAliases = new ArrayList<>();
    for (String alias : aliases) {
      ImmutableSet<String> buildTargets;
      if (alias.startsWith("//")) {
        String buildTarget =
            validateBuildTargetForFullyQualifiedTarget(
                params, executor, enableProfiling, alias, params.getParser());
        if (buildTarget == null) {
          throw new HumanReadableException("%s is not a valid target.", alias);
        }
        buildTargets = ImmutableSet.of(buildTarget);
      } else {
        buildTargets = getBuildTargetForAlias(params.getBuckConfig(), alias);
        if (buildTargets.isEmpty()) {
          throw new HumanReadableException("%s is not an alias.", alias);
        }
      }
      resolvedAliases.addAll(buildTargets);
    }

    for (String resolvedAlias : resolvedAliases) {
      params.getConsole().getStdOut().println(resolvedAlias);
    }

    return 0;
  }

  /** Verify that the given target is a valid full-qualified (non-alias) target. */
  @Nullable
  static String validateBuildTargetForFullyQualifiedTarget(
      CommandRunnerParams params,
      ListeningExecutorService executor,
      boolean enableProfiling,
      String target,
      Parser parser) {

    BuildTarget buildTarget = getBuildTargetForFullyQualifiedTarget(params.getBuckConfig(), target);

    Cell owningCell = params.getCell().getCell(buildTarget);
    Path buildFile;
    try {
      buildFile = owningCell.getAbsolutePathToBuildFile(buildTarget);
    } catch (Cell.MissingBuildFileException e) {
      throw new HumanReadableException(e);
    }

    // Get all valid targets in our target directory by reading the build file.
    ImmutableSet<TargetNode<?, ?>> targetNodes;
    try {
      targetNodes =
          parser.getAllTargetNodes(
              params.getBuckEventBus(), owningCell, enableProfiling, executor, buildFile);
    } catch (BuildFileParseException e) {
      throw new HumanReadableException(e);
    }

    // Check that the given target is a valid target.
    for (TargetNode<?, ?> candidate : targetNodes) {
      if (candidate.getBuildTarget().equals(buildTarget)) {
        return buildTarget.getFullyQualifiedName();
      }
    }
    return null;
  }

  /** @return the name of the build target identified by the specified alias or an empty set. */
  private static ImmutableSet<String> getBuildTargetForAlias(BuckConfig buckConfig, String alias) {
    return buckConfig.getBuildTargetForAliasAsString(alias);
  }

  /** @return the build target identified by the specified full path or {@code null}. */
  private static BuildTarget getBuildTargetForFullyQualifiedTarget(
      BuckConfig buckConfig, String target) {
    return buckConfig.getBuildTargetForFullyQualifiedTarget(target);
  }
}
