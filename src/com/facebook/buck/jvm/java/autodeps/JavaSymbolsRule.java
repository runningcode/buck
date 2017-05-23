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

package com.facebook.buck.jvm.java.autodeps;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildOutputInitializer;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.BuildableProperties;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.InitializableFromDisk;
import com.facebook.buck.rules.OnDiskBuildInfo;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.util.ObjectMappers;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

final class JavaSymbolsRule implements BuildRule, InitializableFromDisk<Symbols> {

  interface SymbolsFinder extends RuleKeyAppendable {
    Symbols extractSymbols() throws IOException;
  }

  private static final String TYPE = "java_symbols";
  public static final Flavor JAVA_SYMBOLS = InternalFlavor.of(TYPE);

  private final BuildTarget buildTarget;

  @AddToRuleKey private final SymbolsFinder symbolsFinder;

  private final ProjectFilesystem projectFilesystem;
  private final Path outputPath;
  private final BuildOutputInitializer<Symbols> outputInitializer;

  JavaSymbolsRule(
      BuildTarget javaLibraryBuildTarget,
      SymbolsFinder symbolsFinder,
      ProjectFilesystem projectFilesystem) {
    this.buildTarget = javaLibraryBuildTarget.withFlavors(JAVA_SYMBOLS);
    this.symbolsFinder = symbolsFinder;
    this.projectFilesystem = projectFilesystem;
    this.outputPath = BuildTargets.getGenPath(getProjectFilesystem(), buildTarget, "__%s__.json");
    this.outputInitializer = new BuildOutputInitializer<>(buildTarget, this);
  }

  public Symbols getFeatures() {
    return outputInitializer.getBuildOutput();
  }

  @Override
  public Symbols initializeFromDisk(OnDiskBuildInfo onDiskBuildInfo) throws IOException {
    List<String> lines = onDiskBuildInfo.getOutputFileContentsByLine(outputPath);
    Preconditions.checkArgument(lines.size() == 1, "Should be one line of JSON: %s", lines);
    return ObjectMappers.readValue(lines.get(0), Symbols.class);
  }

  @Override
  public BuildOutputInitializer<Symbols> getBuildOutputInitializer() {
    return outputInitializer;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    Step mkdirStep = MkdirStep.of(getProjectFilesystem(), outputPath.getParent());
    Step extractSymbolsStep =
        new AbstractExecutionStep("java-symbols") {
          @Override
          public StepExecutionResult execute(ExecutionContext context) throws IOException {
            try (OutputStream output = getProjectFilesystem().newFileOutputStream(outputPath)) {
              ObjectMappers.WRITER.writeValue(output, symbolsFinder.extractSymbols());
            }

            return StepExecutionResult.SUCCESS;
          }
        };

    return ImmutableList.of(mkdirStep, extractSymbolsStep);
  }

  @Override
  public BuildTarget getBuildTarget() {
    return buildTarget;
  }

  @Override
  public String toString() {
    return getFullyQualifiedName();
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public BuildableProperties getProperties() {
    return BuildableProperties.NONE;
  }

  @Override
  public ImmutableSortedSet<BuildRule> getBuildDeps() {
    return ImmutableSortedSet.of();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), outputPath);
  }

  @Override
  public ProjectFilesystem getProjectFilesystem() {
    return projectFilesystem;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof BuildRule)) {
      return false;
    }

    BuildRule that = (BuildRule) obj;
    return this.getBuildTarget().equals(that.getBuildTarget());
  }

  @Override
  public int hashCode() {
    return buildTarget.hashCode();
  }

  @Override
  public boolean isCacheable() {
    return true;
  }
}
