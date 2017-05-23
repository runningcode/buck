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

package com.facebook.buck.json;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;

/*
 * Concatenates Json arrays in files
 */
public class JsonConcatenate extends AbstractBuildRule {

  private final String stepShortName;
  private final String stepDescription;
  private ImmutableSortedSet<Path> inputs;
  private Path outputDirectory;
  private Path output;

  public JsonConcatenate(
      BuildRuleParams buildRuleParams,
      ImmutableSortedSet<Path> inputs,
      String stepShortName,
      String stepDescription,
      String outputDirectoryPrefix,
      String outputName) {
    super(buildRuleParams);
    this.inputs = inputs;
    this.outputDirectory =
        BuildTargets.getGenPath(
            getProjectFilesystem(), this.getBuildTarget(), outputDirectoryPrefix + "-%s");
    this.output = this.outputDirectory.resolve(outputName);
    this.stepShortName = stepShortName;
    this.stepDescription = stepDescription;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    buildableContext.recordArtifact(output);
    ProjectFilesystem projectFilesystem = getProjectFilesystem();
    return ImmutableList.<Step>builder()
        .add(MkdirStep.of(projectFilesystem, outputDirectory))
        .add(
            new JsonConcatenateStep(
                projectFilesystem, inputs, output, stepShortName, stepDescription))
        .build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), output);
  }
}
