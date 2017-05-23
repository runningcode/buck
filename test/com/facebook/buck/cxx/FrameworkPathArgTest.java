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

package com.facebook.buck.cxx;

import static org.junit.Assert.assertThat;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import org.hamcrest.Matchers;
import org.junit.Test;

public class FrameworkPathArgTest {

  private static class TestFrameworkPathArg extends FrameworkPathArg {
    public TestFrameworkPathArg(FrameworkPath frameworkPath) {
      super(ImmutableSet.of(frameworkPath));
    }

    @Override
    public void appendToCommandLine(
        ImmutableCollection.Builder<String> builder, SourcePathResolver pathResolver) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testGetDeps() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);

    BuildTarget genruleTarget = BuildTargetFactory.newInstance("//:genrule");
    Genrule genrule =
        GenruleBuilder.newGenruleBuilder(genruleTarget)
            .setOut("foo/bar.o")
            .build(ruleResolver, filesystem);

    FrameworkPath sourcePathFrameworkPath =
        FrameworkPath.ofSourcePath(genrule.getSourcePathToOutput());

    FrameworkPathArg sourcePathFrameworkPathArg = new TestFrameworkPathArg(sourcePathFrameworkPath);
    assertThat(sourcePathFrameworkPathArg.getDeps(ruleFinder), Matchers.contains(genrule));
  }
}
