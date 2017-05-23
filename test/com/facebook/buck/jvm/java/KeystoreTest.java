/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.jvm.java;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.Step;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class KeystoreTest {

  private static Keystore createKeystoreRuleForTest() throws NoSuchBuildTargetException {
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    return KeystoreBuilder.createBuilder(BuildTargetFactory.newInstance("//keystores:debug"))
        .setStore(new FakeSourcePath("keystores/debug.keystore"))
        .setProperties(new FakeSourcePath("keystores/debug.keystore.properties"))
        .build(ruleResolver);
  }

  @Test
  public void testObservers() throws Exception {
    Keystore keystore = createKeystoreRuleForTest();
    assertEquals("keystore", keystore.getType());

    assertEquals(new FakeSourcePath("keystores/debug.keystore"), keystore.getPathToStore());
    assertEquals(
        new FakeSourcePath("keystores/debug.keystore.properties"),
        keystore.getPathToPropertiesFile());
  }

  @Test
  public void testBuildInternal() throws Exception {
    BuildContext buildContext = FakeBuildContext.NOOP_CONTEXT;

    Keystore keystore = createKeystoreRuleForTest();
    List<Step> buildSteps = keystore.getBuildSteps(buildContext, new FakeBuildableContext());
    assertEquals(ImmutableList.<Step>of(), buildSteps);
  }
}
