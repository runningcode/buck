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

package com.facebook.buck.ocaml;

import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.google.common.collect.ImmutableList;

/**
 * Creates a debug launch script. The script will run OCaml debugger with the target binary loaded.
 * This works with bytecode and provides limited debugging functionality like stepping, breakpoints,
 * etc.
 */
public class OcamlDebugLauncher extends AbstractBuildRule {
  @AddToRuleKey private final OcamlDebugLauncherStep.Args args;

  public OcamlDebugLauncher(BuildRuleParams params, OcamlDebugLauncherStep.Args args) {
    super(params);
    this.args = args;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    buildableContext.recordArtifact(args.getOutput());
    return ImmutableList.of(
        MkdirStep.of(getProjectFilesystem(), args.getOutput().getParent()),
        new OcamlDebugLauncherStep(getProjectFilesystem(), context.getSourcePathResolver(), args));
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), args.getOutput());
  }
}
