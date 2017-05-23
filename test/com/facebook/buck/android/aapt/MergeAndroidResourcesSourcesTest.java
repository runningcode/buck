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

package com.facebook.buck.android.aapt;

import static org.junit.Assert.assertThat;

import com.facebook.buck.io.ProjectFilesystem;
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
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MergeAndroidResourcesSourcesTest {
  private static final String RESOURCES_XML_HEADER =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n";
  private static final String RESOURCES_XML_FOOTER = "</resources>";

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private final Function<Step, String> stepDescriptionFunction =
      new Function<Step, String>() {
        @Override
        public String apply(Step input) {
          return input.getDescription(context);
        }
      };

  private ExecutionContext context;
  private ProjectFilesystem filesystem;

  @Before
  public void setUp() throws Exception {
    filesystem = new ProjectFilesystem(tmp.getRoot().toPath());
    context = TestExecutionContext.newInstance();

    tmp.newFolder("res_in_1");
    tmp.newFolder("res_in_1", "values");
    tmp.newFolder("res_in_1", "drawable");
    tmp.newFile("res_in_1/drawable/one.png");

    tmp.newFolder("res_in_2");
    tmp.newFolder("res_in_2", "values");
    tmp.newFolder("res_in_2", "drawable");
    tmp.newFile("res_in_2/drawable/two.png");

    filesystem.writeContentsToPath(
        RESOURCES_XML_HEADER
            + "<string name=\"override_me\">one</string>\n"
            + "<string name=\"only_in_first\">first</string>\n"
            + RESOURCES_XML_FOOTER,
        Paths.get("res_in_1/values/strings.xml"));

    filesystem.writeContentsToPath(
        RESOURCES_XML_HEADER
            + "<string name=\"override_me\">two</string>\n"
            + "<string name=\"only_in_second\">second</string>\n"
            + RESOURCES_XML_FOOTER,
        Paths.get("res_in_2/values/strings.xml"));

    filesystem.writeContentsToPath("png, trust me", Paths.get("res_in_1/drawable/one.png"));
    filesystem.writeContentsToPath("png, trust me", Paths.get("res_in_2/drawable/two.png"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRuleStepCreation() throws IOException, InterruptedException {
    BuildRuleParams buildRuleParams =
        new FakeBuildRuleParamsBuilder("//:output_folder").setProjectFilesystem(filesystem).build();
    ImmutableList<SourcePath> directories =
        ImmutableList.of(
            new FakeSourcePath(filesystem, "res_in_1"), new FakeSourcePath(filesystem, "res_in_2"));
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    MergeAndroidResourceSources mergeAndroidResourceSourcesStep =
        new MergeAndroidResourceSources(buildRuleParams, directories);

    ImmutableList<Step> steps =
        mergeAndroidResourceSourcesStep.getBuildSteps(
            FakeBuildContext.withSourcePathResolver(pathResolver), new FakeBuildableContext());
    assertThat(
        steps,
        Matchers.contains(
            Matchers.instanceOf(RmStep.class),
            Matchers.instanceOf(MkdirStep.class),
            Matchers.instanceOf(MergeAndroidResourceSourcesStep.class)));
    String resIn1 = filesystem.getRootPath().resolve("res_in_1").toString();
    String resIn2 = filesystem.getRootPath().resolve("res_in_2").toString();

    assertThat(
        FluentIterable.from(steps).transform(stepDescriptionFunction),
        Matchers.contains(
            Matchers.containsString("rm"),
            Matchers.containsString("mkdir"),
            Matchers.startsWith(String.format("merge-resources %s,%s -> ", resIn1, resIn2))));
  }

  @Test
  public void testStepExecution() throws IOException, InterruptedException {
    Path rootPath = tmp.getRoot().toPath();
    File outFolder = tmp.newFolder("out");
    File tmpFolder = tmp.newFolder("tmp");

    MergeAndroidResourceSourcesStep step =
        MergeAndroidResourceSourcesStep.builder()
            .setResPaths(
                ImmutableList.of(rootPath.resolve("res_in_1"), rootPath.resolve("res_in_2")))
            .setOutFolderPath(outFolder.toPath())
            .setTmpFolderPath(tmpFolder.toPath())
            .build();
    step.execute(context);
    assertThat(
        filesystem.getFilesUnderPath(outFolder.toPath()),
        Matchers.containsInAnyOrder(
            Paths.get("out", "drawable", "one.png"),
            Paths.get("out", "drawable", "two.png"),
            Paths.get("out", "values", "values.xml")));
    assertThat(
        filesystem.readFileIfItExists(outFolder.toPath().resolve("values/values.xml")).get(),
        Matchers.equalTo(
            RESOURCES_XML_HEADER
                + "    <string name=\"only_in_first\">first</string>\n"
                + "    <string name=\"only_in_second\">second</string>\n"
                + "    <string name=\"override_me\">two</string>\n"
                + RESOURCES_XML_FOOTER));
  }
}
