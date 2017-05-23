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

package com.facebook.buck.halide;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

public class HalideCompile extends AbstractBuildRule {

  @AddToRuleKey private final Tool halideCompiler;

  @AddToRuleKey private final String targetPlatform;

  @AddToRuleKey private final Optional<ImmutableList<String>> compilerInvocationFlags;

  @AddToRuleKey private final Optional<String> functionNameOverride;

  public HalideCompile(
      BuildRuleParams params,
      Tool halideCompiler,
      String targetPlatform,
      Optional<ImmutableList<String>> compilerInvocationFlags,
      Optional<String> functionNameOverride) {
    super(params);
    this.halideCompiler = halideCompiler;
    this.targetPlatform = targetPlatform;
    this.compilerInvocationFlags = compilerInvocationFlags;
    this.functionNameOverride = functionNameOverride;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    Path outputDir = context.getSourcePathResolver().getRelativePath(getSourcePathToOutput());
    buildableContext.recordArtifact(
        objectOutputPath(getBuildTarget(), getProjectFilesystem(), functionNameOverride));
    buildableContext.recordArtifact(
        headerOutputPath(getBuildTarget(), getProjectFilesystem(), functionNameOverride));

    ImmutableList.Builder<Step> commands = ImmutableList.builder();
    ProjectFilesystem projectFilesystem = getProjectFilesystem();
    commands.addAll(MakeCleanDirectoryStep.of(projectFilesystem, outputDir));

    commands.add(
        new HalideCompilerStep(
            projectFilesystem.getRootPath(),
            halideCompiler.getEnvironment(context.getSourcePathResolver()),
            halideCompiler.getCommandPrefix(context.getSourcePathResolver()),
            outputDir,
            fileOutputName(getBuildTarget(), functionNameOverride),
            targetPlatform,
            compilerInvocationFlags));
    return commands.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(
        getBuildTarget(), pathToOutput(getBuildTarget(), getProjectFilesystem()));
  }

  private static Path pathToOutput(BuildTarget buildTarget, ProjectFilesystem filesystem) {
    return BuildTargets.getGenPath(filesystem, buildTarget, "%s");
  }

  public static Path objectOutputPath(
      BuildTarget buildTarget,
      ProjectFilesystem filesystem,
      Optional<String> functionNameOverride) {
    String functionName = fileOutputName(buildTarget, functionNameOverride);
    return pathToOutput(buildTarget, filesystem).resolve(functionName + ".o");
  }

  public static Path headerOutputPath(
      BuildTarget buildTarget,
      ProjectFilesystem filesystem,
      Optional<String> functionNameOverride) {
    String functionName = fileOutputName(buildTarget, functionNameOverride);
    return pathToOutput(buildTarget, filesystem).resolve(functionName + ".h");
  }

  public static String fileOutputName(
      BuildTarget buildTarget, Optional<String> functionNameOverride) {
    return functionNameOverride.orElse(buildTarget.getShortName());
  }
}
