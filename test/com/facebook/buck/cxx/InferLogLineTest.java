/*
 * Copyright 2016-present Facebook, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.UnflavoredBuildTarget;
import com.facebook.buck.util.environment.Platform;
import java.nio.file.Paths;
import java.util.Optional;
import org.hamcrest.junit.ExpectedException;
import org.junit.Rule;
import org.junit.Test;

public class InferLogLineTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void testFromBuildTargetThrowsWhenPathIsNotAbsolute() {
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Path must be absolute");
    BuildTarget testBuildTarget =
        BuildTarget.builder()
            .setUnflavoredBuildTarget(
                UnflavoredBuildTarget.of(
                    Paths.get("/User/user/src"), Optional.empty(), "//target", "short"))
            .addFlavors(CxxInferEnhancer.InferFlavors.INFER.get())
            .build();

    InferLogLine.fromBuildTarget(testBuildTarget, Paths.get("buck-out/a/b/c/"));
  }

  @Test
  public void testToStringWithCell() {
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    BuildTarget testBuildTarget =
        BuildTarget.builder()
            .setUnflavoredBuildTarget(
                UnflavoredBuildTarget.of(
                    Paths.get("/User/user/src"), Optional.of("cellname"), "//target", "short"))
            .addFlavors(CxxInferEnhancer.InferFlavors.INFER.get())
            .build();

    String expectedOutput = "cellname//target:short#infer\t[infer]\t/User/user/src/buck-out/a/b/c";
    assertEquals(
        expectedOutput,
        InferLogLine.fromBuildTarget(testBuildTarget, Paths.get("/User/user/src/buck-out/a/b/c/"))
            .toString());
  }

  @Test
  public void testToStringWithoutCell() {
    assumeTrue(Platform.detect() != Platform.WINDOWS);
    BuildTarget testBuildTarget =
        BuildTarget.builder()
            .setUnflavoredBuildTarget(
                UnflavoredBuildTarget.of(
                    Paths.get("/User/user/src"), Optional.empty(), "//target", "short"))
            .addFlavors(CxxInferEnhancer.InferFlavors.INFER.get())
            .build();

    String expectedOutput = "//target:short#infer\t[infer]\t/User/user/src/buck-out/a/b/c";
    assertEquals(
        expectedOutput,
        InferLogLine.fromBuildTarget(testBuildTarget, Paths.get("/User/user/src/buck-out/a/b/c/"))
            .toString());
  }
}
