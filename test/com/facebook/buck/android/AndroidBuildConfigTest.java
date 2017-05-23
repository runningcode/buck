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

import com.facebook.buck.android.AndroidBuildConfig.ReadValuesStep;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.coercer.BuildConfigFields;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.easymock.EasyMock;
import org.junit.Test;

/** Unit test for {@link AndroidBuildConfig}. */
public class AndroidBuildConfigTest {

  public static final BuildTarget BUILD_TARGET =
      BuildTargetFactory.newInstance("//java/com/example:build_config");
  private static final ProjectFilesystem filesystem = new FakeProjectFilesystem();

  @Test
  public void testGetPathToOutput() {
    AndroidBuildConfig buildConfig = createSimpleBuildConfigRule();
    assertEquals(
        new ExplicitBuildTargetSourcePath(
            BUILD_TARGET,
            BuildTargets.getGenPath(filesystem, BUILD_TARGET, "__%s__/BuildConfig.java")),
        buildConfig.getSourcePathToOutput());
  }

  @Test
  public void testBuildInternal() throws IOException {
    AndroidBuildConfig buildConfig = createSimpleBuildConfigRule();
    List<Step> steps =
        buildConfig.getBuildSteps(FakeBuildContext.NOOP_CONTEXT, new FakeBuildableContext());
    Step generateBuildConfigStep = steps.get(2);
    GenerateBuildConfigStep expectedStep =
        new GenerateBuildConfigStep(
            new FakeProjectFilesystem(),
            BuildTargetFactory.newInstance("//java/com/example:build_config")
                .getUnflavoredBuildTarget(),
            /* javaPackage */ "com.example",
            /* useConstantExpressions */ false,
            /* constants */ Suppliers.ofInstance(BuildConfigFields.empty()),
            BuildTargets.getGenPath(filesystem, BUILD_TARGET, "__%s__/BuildConfig.java"));
    assertEquals(expectedStep, generateBuildConfigStep);
  }

  @Test
  public void testGetTypeMethodOfBuilder() {
    assertEquals(
        "android_build_config",
        Description.getBuildRuleType(AndroidBuildConfigDescription.class).getName());
  }

  @Test
  public void testReadValuesStep() throws IOException {
    Path pathToValues = Paths.get("src/values.txt");

    ProjectFilesystem projectFilesystem = EasyMock.createMock(ProjectFilesystem.class);
    EasyMock.expect(projectFilesystem.readLines(pathToValues))
        .andReturn(ImmutableList.of("boolean DEBUG = false", "String FOO = \"BAR\""));
    EasyMock.replay(projectFilesystem);

    ReadValuesStep step = new ReadValuesStep(projectFilesystem, pathToValues);
    ExecutionContext context = TestExecutionContext.newBuilder().build();
    int exitCode = step.execute(context).getExitCode();
    assertEquals(0, exitCode);
    assertEquals(
        BuildConfigFields.fromFields(
            ImmutableList.of(
                BuildConfigFields.Field.of("boolean", "DEBUG", "false"),
                BuildConfigFields.Field.of("String", "FOO", "\"BAR\""))),
        step.get());

    EasyMock.verify(projectFilesystem);
  }

  private static AndroidBuildConfig createSimpleBuildConfigRule() {
    // First, create the BuildConfig object.
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(BUILD_TARGET).build();
    return new AndroidBuildConfig(
        params,
        /* javaPackage */ "com.example",
        /* values */ BuildConfigFields.empty(),
        /* valuesFile */ Optional.empty(),
        /* useConstantExpressions */ false);
  }

  // TODO(nickpalmer): Add another unit test that passes in a non-trivial DependencyGraph and verify
  // that the resulting set of libraryManifestPaths is computed correctly.
}
