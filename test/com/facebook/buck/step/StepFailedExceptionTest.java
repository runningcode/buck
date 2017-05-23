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

package com.facebook.buck.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.Verbosity;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class StepFailedExceptionTest {

  private ExecutionContext verboseContext;
  private ExecutionContext silentContext;

  @Before
  public void setUp() {
    ExecutionContext context = TestExecutionContext.newInstance();
    TestConsole verboseConsole = new TestConsole(Verbosity.ALL);
    TestConsole silentConsole = new TestConsole(Verbosity.SILENT);

    verboseContext =
        ExecutionContext.builder()
            .from(context)
            .setConsole(verboseConsole)
            .setExecutors(context.getExecutors())
            .build();

    silentContext =
        ExecutionContext.builder()
            .from(context)
            .setConsole(silentConsole)
            .setExecutors(context.getExecutors())
            .build();
  }

  @Test
  public void testCreateForFailingStepForExitCodeWithBuildTarget() {
    final int exitCode = 17;
    final StepExecutionResult executionResult = StepExecutionResult.of(exitCode);
    Step step = new FakeStep("cp", "cp foo bar", exitCode);
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:bar");
    StepFailedException exception =
        StepFailedException.createForFailingStepWithExitCode(
            step, verboseContext, executionResult, Optional.of(buildTarget));

    assertEquals(step, exception.getStep());
    assertEquals(exitCode, exception.getExitCode());
    assertEquals("//foo:bar failed with exit code 17:\ncp foo bar", exception.getMessage());
  }

  @Test
  public void testCreateForFailingStepForExitCodeWithoutBuildTarget() {
    final int exitCode = 17;
    final StepExecutionResult executionResult = StepExecutionResult.of(exitCode);
    Step step = new FakeStep("cp", "cp foo bar", exitCode);
    StepFailedException exception =
        StepFailedException.createForFailingStepWithExitCode(
            step, verboseContext, executionResult, Optional.empty());

    assertEquals(step, exception.getStep());
    assertEquals(exitCode, exception.getExitCode());
    assertEquals("Failed with exit code 17:\ncp foo bar", exception.getMessage());
  }

  @Test
  public void testCreateForFailingStepWithSilentConsole() {
    final int exitCode = 17;
    final StepExecutionResult executionResult = StepExecutionResult.of(exitCode);
    Step step = new FakeStep("cp", "cp foo bar", exitCode);
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:bar");
    StepFailedException exception =
        StepFailedException.createForFailingStepWithExitCode(
            step, silentContext, executionResult, Optional.of(buildTarget));

    assertEquals(step, exception.getStep());
    assertEquals("//foo:bar failed with exit code 17:\ncp", exception.getMessage());
  }

  @Test
  public void testCreateForFailingStepWithBuildTarget() {
    final int exitCode = 17;
    Step step = new FakeStep("cp", "cp foo bar", exitCode);
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:bar");
    StepFailedException exception =
        StepFailedException.createForFailingStepWithException(
            step, silentContext, new IOException("Copy failed!"), Optional.of(buildTarget));

    assertEquals(step, exception.getStep());
    assertEquals(1, exception.getExitCode());
    assertTrue(
        exception
            .getMessage()
            .startsWith("//foo:bar failed on step cp with an exception:\nCopy failed!"));
  }

  @Test
  public void testCreateForFailingStepWithoutBuildTarget() {
    final int exitCode = 17;
    Step step = new FakeStep("cp", "cp foo bar", exitCode);
    StepFailedException exception =
        StepFailedException.createForFailingStepWithException(
            step, silentContext, new IOException("Copy failed!"), Optional.empty());

    assertEquals(step, exception.getStep());
    assertEquals(1, exception.getExitCode());
    assertTrue(
        exception.getMessage().startsWith("Failed on step cp with an exception:\nCopy failed!"));
  }
}
