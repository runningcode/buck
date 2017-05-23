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

import static org.junit.Assert.assertTrue;

import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.testutil.TargetGraphFactory;
import java.nio.file.Paths;
import org.junit.Test;

public class AndroidLibraryTest {

  @Test
  public void testAndroidAnnotation() throws Exception {
    BuildTarget processorTarget = BuildTargetFactory.newInstance("//java/processor:processor");
    TargetNode<?, ?> processorNode =
        JavaLibraryBuilder.createBuilder(processorTarget)
            .addSrc(Paths.get("java/processor/processor.java"))
            .build();

    BuildTarget libTarget = BuildTargetFactory.newInstance("//java/lib:lib");
    TargetNode<?, ?> libraryNode =
        AndroidLibraryBuilder.createBuilder(libTarget)
            .addProcessor("MyProcessor")
            .addProcessorBuildTarget(processorNode.getBuildTarget())
            .build();

    TargetGraph targetGraph = TargetGraphFactory.newInstance(processorNode, libraryNode);
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());

    AndroidLibrary library = (AndroidLibrary) ruleResolver.requireRule(libTarget);

    assertTrue(library.getGeneratedSourcePath().isPresent());
  }
}
