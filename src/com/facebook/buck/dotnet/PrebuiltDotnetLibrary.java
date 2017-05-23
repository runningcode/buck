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

package com.facebook.buck.dotnet;

import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRuleWithResolver;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

public class PrebuiltDotnetLibrary extends AbstractBuildRuleWithResolver {

  private final Path output;
  private final SourcePath assembly;

  protected PrebuiltDotnetLibrary(
      BuildRuleParams params, SourcePathResolver resolver, SourcePath assembly) {
    super(params, resolver);

    this.assembly = assembly;

    Path resolvedPath = resolver.getAbsolutePath(assembly);
    this.output =
        BuildTargets.getGenPath(getProjectFilesystem(), params.getBuildTarget(), "%s")
            .resolve(resolvedPath.getFileName());
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.add(RmStep.of(getProjectFilesystem(), output));
    steps.add(MkdirStep.of(getProjectFilesystem(), output.getParent()));
    steps.add(
        CopyStep.forFile(getProjectFilesystem(), getResolver().getAbsolutePath(assembly), output));

    return steps.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), output);
  }
}
