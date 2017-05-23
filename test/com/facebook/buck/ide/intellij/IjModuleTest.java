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

package com.facebook.buck.ide.intellij;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.ide.intellij.model.IjModule;
import com.facebook.buck.ide.intellij.model.IjModuleType;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.TargetNode;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class IjModuleTest {

  @Test
  public void testPathToModuleFile() {
    TargetNode<?, ?> javaLibrary =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/src/com/facebook/foo:foo"))
            .build();

    IjModule javaLibraryModule = createModule(javaLibrary);

    assertEquals(
        Paths.get("java", "src", "com", "facebook", "foo"), javaLibraryModule.getModuleBasePath());
    assertEquals("java_src_com_facebook_foo", javaLibraryModule.getName());
    assertEquals(
        Paths.get("java", "src", "com", "facebook", "foo", "java_src_com_facebook_foo.iml"),
        javaLibraryModule.getModuleImlFilePath());
  }

  private static <T> IjModule createModule(TargetNode<?, ?> targetNode) {
    Path moduleBasePath = targetNode.getBuildTarget().getBasePath();
    return IjModule.builder()
        .setTargets(ImmutableSet.of(targetNode.getBuildTarget()))
        .setModuleBasePath(moduleBasePath)
        .setModuleType(IjModuleType.JAVA_MODULE)
        .build();
  }
}
