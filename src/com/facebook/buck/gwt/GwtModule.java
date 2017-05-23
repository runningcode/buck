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

package com.facebook.buck.gwt;

import com.facebook.buck.jvm.java.CopyResourcesStep;
import com.facebook.buck.jvm.java.JarDirectoryStep;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;

/**
 * {@link com.facebook.buck.rules.BuildRule} whose output file is a JAR containing the .java files
 * and resources suitable for a GWT module. (It differs slightly from a source JAR because it
 * contains resources.)
 */
public class GwtModule extends AbstractBuildRule {

  private final Path outputFile;
  @AddToRuleKey private final ImmutableSortedSet<SourcePath> filesForGwtModule;
  private final SourcePathRuleFinder ruleFinder;

  GwtModule(
      BuildRuleParams params,
      SourcePathRuleFinder ruleFinder,
      ImmutableSortedSet<SourcePath> filesForGwtModule) {
    super(params);
    this.ruleFinder = ruleFinder;
    BuildTarget target = params.getBuildTarget();
    this.outputFile =
        BuildTargets.getGenPath(
            getProjectFilesystem(),
            target,
            "__gwt_module_%s__/" + target.getShortNameAndFlavorPostfix() + ".jar");
    this.filesForGwtModule = filesForGwtModule;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    Path workingDirectory = outputFile.getParent();
    steps.addAll(MakeCleanDirectoryStep.of(getProjectFilesystem(), workingDirectory));

    // A CopyResourcesStep is needed so that a file that is at java/com/example/resource.txt in the
    // repository will be added as com/example/resource.txt in the resulting JAR (assuming that
    // "/java/" is listed under src_roots in .buckconfig).
    Path tempJarFolder = workingDirectory.resolve("tmp");
    steps.add(
        new CopyResourcesStep(
            getProjectFilesystem(),
            context.getSourcePathResolver(),
            ruleFinder,
            getBuildTarget(),
            filesForGwtModule,
            tempJarFolder,
            context.getJavaPackageFinder()));

    steps.add(
        new JarDirectoryStep(
            getProjectFilesystem(),
            outputFile,
            /* entriesToJar */ ImmutableSortedSet.of(tempJarFolder),
            /* mainClass */ null,
            /* manifestFile */ null));

    buildableContext.recordArtifact(outputFile);

    return steps.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), outputFile);
  }
}
