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

package com.facebook.buck.python;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.SourceList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import java.nio.file.Path;
import org.junit.Test;

public class PythonUtilTest {

  @Test
  public void toModuleMapWithExplicitMap() {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
    ImmutableMap<Path, SourcePath> srcs =
        PythonUtil.toModuleMap(
            target,
            new SourcePathResolver(
                new SourcePathRuleFinder(
                    new BuildRuleResolver(
                        TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()))),
            "srcs",
            target.getBasePath(),
            ImmutableList.of(
                SourceList.ofNamedSources(
                    ImmutableSortedMap.of("hello.py", new FakeSourcePath("goodbye.py")))));
    assertEquals(
        ImmutableMap.<Path, SourcePath>of(
            target.getBasePath().resolve("hello.py"), new FakeSourcePath("goodbye.py")),
        srcs);
  }
}
