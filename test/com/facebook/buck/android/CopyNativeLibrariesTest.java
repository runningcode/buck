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

package com.facebook.buck.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.facebook.buck.android.NdkCxxPlatforms.TargetCpuType;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.Matchers;
import org.junit.Test;

public class CopyNativeLibrariesTest {

  @Test
  public void testCopyNativeLibraryCommandWithoutCpuFilter() {
    final Path source = Paths.get("/path/to/source").toAbsolutePath();
    final Path destination = Paths.get("/path/to/destination/").toAbsolutePath();
    createAndroidBinaryRuleAndTestCopyNativeLibraryCommand(
        ImmutableSet.of() /* cpuFilters */,
        source,
        destination,
        ImmutableList.of(
            String.format("cp -R %s%s* %s", source, File.separator, destination),
            "rename_native_executables"));
  }

  @Test
  public void testCopyNativeLibraryCommand() {
    final Path source = Paths.get("/path/to/source").toAbsolutePath();
    final Path destination = Paths.get("/path/to/destination/").toAbsolutePath();
    createAndroidBinaryRuleAndTestCopyNativeLibraryCommand(
        ImmutableSet.of(NdkCxxPlatforms.TargetCpuType.ARMV7),
        source,
        destination,
        ImmutableList.of(
            String.format(
                "[ -d %s ] && mkdir -p %s && cp -R %s%s* %s",
                source.resolve("armeabi-v7a"),
                destination.resolve("armeabi-v7a"),
                source.resolve("armeabi-v7a"),
                File.separator,
                destination.resolve("armeabi-v7a")),
            "rename_native_executables"));
  }

  @Test
  public void testCopyNativeLibraryCommandWithMultipleCpuFilters() {
    final Path source = Paths.get("/path/to/source").toAbsolutePath();
    final Path destination = Paths.get("/path/to/destination/").toAbsolutePath();
    createAndroidBinaryRuleAndTestCopyNativeLibraryCommand(
        ImmutableSet.of(NdkCxxPlatforms.TargetCpuType.ARM, NdkCxxPlatforms.TargetCpuType.X86),
        source,
        destination,
        ImmutableList.of(
            String.format(
                "[ -d %s ] && mkdir -p %s && cp -R %s%s* %s",
                source.resolve("armeabi"),
                destination.resolve("armeabi"),
                source.resolve("armeabi"),
                File.separator,
                destination.resolve("armeabi")),
            String.format(
                "[ -d %s ] && mkdir -p %s && cp -R %s%s* %s",
                source.resolve("x86"),
                destination.resolve("x86"),
                source.resolve("x86"),
                File.separator,
                destination.resolve("x86")),
            "rename_native_executables"));
  }

  @Test
  public void testCopyNativeLibrariesCopiesLibDirsInReverseTopoOrder() {
    BuildTarget target = BuildTargetFactory.newInstance("//:test");
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    CopyNativeLibraries copyNativeLibraries =
        new CopyNativeLibraries(
            new FakeBuildRuleParamsBuilder(target).build(),
            ImmutableSet.of(new FakeSourcePath("lib1"), new FakeSourcePath("lib2")),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            "dex");

    ImmutableList<Step> steps =
        copyNativeLibraries.getBuildSteps(
            FakeBuildContext.withSourcePathResolver(pathResolver), new FakeBuildableContext());

    Iterable<String> descriptions =
        Iterables.transform(steps, step -> step.getDescription(TestExecutionContext.newInstance()));
    assertThat(
        "lib1 contents should be copied *after* lib2",
        Iterables.indexOf(descriptions, Predicates.containsPattern("lib1")),
        Matchers.greaterThan(Iterables.indexOf(descriptions, Predicates.containsPattern("lib2"))));
  }

  private void createAndroidBinaryRuleAndTestCopyNativeLibraryCommand(
      ImmutableSet<TargetCpuType> cpuFilters,
      Path sourceDir,
      Path destinationDir,
      ImmutableList<String> expectedCommandDescriptions) {
    // Invoke copyNativeLibrary to populate the steps.
    ImmutableList.Builder<Step> stepsBuilder = ImmutableList.builder();
    CopyNativeLibraries.copyNativeLibrary(
        new FakeProjectFilesystem(), sourceDir, destinationDir, cpuFilters, stepsBuilder);
    ImmutableList<Step> steps = stepsBuilder.build();

    assertEquals(steps.size(), expectedCommandDescriptions.size());
    ExecutionContext context = TestExecutionContext.newInstance();

    for (int i = 0; i < steps.size(); ++i) {
      String description = steps.get(i).getDescription(context);
      assertEquals(expectedCommandDescriptions.get(i), description);
    }
  }
}
