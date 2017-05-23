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

package com.facebook.buck.android;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;

public class AssembleDirectoriesTest {
  private ExecutionContext context;
  private ProjectFilesystem filesystem;

  @Before
  public void setUp() throws InterruptedException {
    filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();
    context = TestExecutionContext.newInstance();
  }

  @Test
  public void testAssembleFoldersWithRelativePath() throws IOException, InterruptedException {
    Path tmp = filesystem.getRootPath();
    Files.createDirectories(tmp.resolve("folder_a"));
    Files.write(tmp.resolve("folder_a/a.txt"), "".getBytes(UTF_8));
    Files.write(tmp.resolve("folder_a/b.txt"), "".getBytes(UTF_8));
    Files.createDirectories(tmp.resolve("folder_b"));
    Files.write(tmp.resolve("folder_b/c.txt"), "".getBytes(UTF_8));
    Files.write(tmp.resolve("folder_b/d.txt"), "".getBytes(UTF_8));

    BuildTarget target =
        BuildTargetFactory.newInstance(filesystem.getRootPath(), "//:output_folder");
    BuildRuleParams buildRuleParams =
        new FakeBuildRuleParamsBuilder(target).setProjectFilesystem(filesystem).build();
    ImmutableList<SourcePath> directories =
        ImmutableList.of(
            new FakeSourcePath(filesystem, "folder_a"), new FakeSourcePath(filesystem, "folder_b"));
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(ruleResolver));
    AssembleDirectories assembleDirectories = new AssembleDirectories(buildRuleParams, directories);
    ruleResolver.addToIndex(assembleDirectories);

    ImmutableList<Step> steps =
        assembleDirectories.getBuildSteps(
            FakeBuildContext.withSourcePathResolver(pathResolver), new FakeBuildableContext());
    for (Step step : steps) {
      assertEquals(0, step.execute(context).getExitCode());
    }
    Path outputFile = pathResolver.getAbsolutePath(assembleDirectories.getSourcePathToOutput());
    try (DirectoryStream<Path> dir = Files.newDirectoryStream(outputFile)) {
      assertEquals(4, Iterables.size(dir));
    }
  }
}
