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

package com.facebook.buck.go;

import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Map;

public class GoTestMainStep extends ShellStep {
  private final ImmutableMap<String, String> environment;
  private final ImmutableList<String> generatorCommandPrefix;
  private final String coverageMode;
  private final ImmutableMap<Path, ImmutableMap<String, Path>> coverageVariables;
  private final Path packageName;
  private final ImmutableList<Path> testFiles;
  private final Path output;

  public GoTestMainStep(
      Path workingDirectory,
      ImmutableMap<String, String> environment,
      ImmutableList<String> generatorCommandPrefix,
      String coverageMode,
      ImmutableMap<Path, ImmutableMap<String, Path>> coverageVariables,
      Path packageName,
      ImmutableList<Path> testFiles,
      Path output) {
    super(workingDirectory);
    this.environment = environment;
    this.generatorCommandPrefix = generatorCommandPrefix;
    this.coverageMode = coverageMode;
    this.coverageVariables = coverageVariables;
    this.packageName = packageName;
    this.testFiles = testFiles;
    this.output = output;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    ImmutableList.Builder<String> command =
        ImmutableList.<String>builder()
            .addAll(generatorCommandPrefix)
            .add("--output", output.toString())
            .add("--import-path", packageName.toString())
            .add("--cover-mode", coverageMode);

    for (Map.Entry<Path, ImmutableMap<String, Path>> pkg : coverageVariables.entrySet()) {
      if (pkg.getValue().isEmpty()) {
        continue;
      }

      StringBuilder pkgFlag = new StringBuilder();
      pkgFlag.append(pkg.getKey().toString());
      pkgFlag.append(':');

      boolean first = true;
      for (Map.Entry<String, Path> pkgVars : pkg.getValue().entrySet()) {
        if (!first) {
          pkgFlag.append(',');
        }
        first = false;

        pkgFlag.append(pkgVars.getKey());
        pkgFlag.append('=');
        pkgFlag.append(pkgVars.getValue().toString());
      }

      command.add("--cover-pkgs", pkgFlag.toString());
    }

    for (Path source : testFiles) {
      command.add(source.toString());
    }

    return command.build();
  }

  @Override
  public ImmutableMap<String, String> getEnvironmentVariables(ExecutionContext context) {
    return environment;
  }

  @Override
  public String getShortName() {
    return "go test main gen";
  }
}
