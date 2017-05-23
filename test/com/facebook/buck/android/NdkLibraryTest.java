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

package com.facebook.buck.android;

import static com.facebook.buck.rules.BuildableProperties.Kind.ANDROID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class NdkLibraryTest {

  private ExecutionContext executionContext;
  private String ndkBuildCommand;
  private ProjectFilesystem projectFilesystem;

  @Before
  public void setUp() throws InterruptedException {
    AssumeAndroidPlatform.assumeNdkIsAvailable();
    projectFilesystem = new ProjectFilesystem(Paths.get(".").toAbsolutePath());
    AndroidDirectoryResolver resolver =
        new DefaultAndroidDirectoryResolver(
            projectFilesystem.getRootPath().getFileSystem(),
            ImmutableMap.copyOf(System.getenv()),
            Optional.empty(),
            Optional.empty());

    AndroidPlatformTarget androidPlatformTarget =
        AndroidPlatformTarget.getDefaultPlatformTarget(
            resolver, Optional.empty(), Optional.empty());
    executionContext =
        TestExecutionContext.newBuilder()
            .setAndroidPlatformTargetSupplier(Suppliers.ofInstance(androidPlatformTarget))
            .build();
    ndkBuildCommand =
        new ExecutableFinder()
            .getOptionalExecutable(Paths.get("ndk-build"), resolver.getNdkOrAbsent().get())
            .get()
            .toAbsolutePath()
            .toString();
  }

  @Test
  public void testSimpleNdkLibraryRule() throws Exception {
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    BuildContext context = FakeBuildContext.NOOP_CONTEXT;

    String basePath = "java/src/com/facebook/base";
    BuildTarget target = BuildTargetFactory.newInstance(String.format("//%s:base", basePath));
    NdkLibrary ndkLibrary =
        new NdkLibraryBuilder(target)
            .setFlags(ImmutableList.of("flag1", "flag2"))
            .setIsAsset(true)
            .build(ruleResolver, projectFilesystem);

    assertEquals("ndk_library", ndkLibrary.getType());

    assertTrue(ndkLibrary.getProperties().is(ANDROID));
    assertTrue(ndkLibrary.isAsset());
    assertEquals(
        projectFilesystem.getBuckPaths().getGenDir().resolve(basePath).resolve("__libbase"),
        ndkLibrary.getLibraryPath());

    List<Step> steps = ndkLibrary.getBuildSteps(context, new FakeBuildableContext());

    String libbase =
        projectFilesystem
            .getBuckPaths()
            .getScratchDir()
            .resolve(basePath)
            .resolve("__libbase")
            .toString();
    MoreAsserts.assertShellCommands(
        "ndk_library() should invoke ndk-build on the given path with some -j value",
        ImmutableList.of(
            String.format(
                "%s -j %d -C %s flag1 flag2 "
                    + "APP_PROJECT_PATH=%s "
                    + "APP_BUILD_SCRIPT=%s "
                    + "NDK_OUT=%s "
                    + "NDK_LIBS_OUT=%s "
                    + "BUCK_PROJECT_DIR=../../../../.. "
                    + "host-echo-build-step=%s "
                    + "--silent",
                ndkBuildCommand,
                Runtime.getRuntime().availableProcessors(),
                Paths.get(basePath).toString(),
                /* APP_PROJECT_PATH */ projectFilesystem.resolve(libbase) + File.separator,
                /* APP_BUILD_SCRIPT */ projectFilesystem.resolve(
                    NdkLibraryDescription.getGeneratedMakefilePath(target, projectFilesystem)),
                /* NDK_OUT */ projectFilesystem.resolve(libbase) + File.separator,
                /* NDK_LIBS_OUT */ projectFilesystem.resolve(Paths.get(libbase, "libs")),
                /* host-echo-build-step */ Platform.detect() == Platform.WINDOWS ? "@REM" : "@#")),
        steps.subList(3, 4),
        executionContext);
  }
}
