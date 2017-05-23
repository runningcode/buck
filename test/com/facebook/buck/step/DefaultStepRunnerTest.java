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

package com.facebook.buck.step;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusFactory;
import com.facebook.buck.event.FakeBuckEventListener;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Optional;
import org.junit.Test;

public class DefaultStepRunnerTest {

  @Test
  public void testEventsFired() throws StepFailedException, InterruptedException, IOException {
    Step passingStep = new FakeStep("step1", "fake step 1", 0);
    Step failingStep = new FakeStep("step1", "fake step 1", 1);

    // The EventBus should be updated with events indicating how the steps were run.
    BuckEventBus eventBus = BuckEventBusFactory.newInstance();
    FakeBuckEventListener listener = new FakeBuckEventListener();
    eventBus.register(listener);

    ExecutionContext context = TestExecutionContext.newBuilder().setBuckEventBus(eventBus).build();
    DefaultStepRunner runner = new DefaultStepRunner();
    runner.runStepForBuildTarget(context, passingStep, Optional.empty());
    try {
      runner.runStepForBuildTarget(context, failingStep, Optional.empty());
      fail("Failing step should have thrown an exception");
    } catch (StepFailedException e) {
      assertEquals(e.getStep(), failingStep);
    }

    ImmutableList<StepEvent> events =
        FluentIterable.from(listener.getEvents()).filter(StepEvent.class).toList();
    assertEquals(4, events.size());

    assertTrue(events.get(0) instanceof StepEvent.Started);
    assertEquals(events.get(0).getShortStepName(), "step1");
    assertEquals(events.get(0).getDescription(), "fake step 1");

    assertTrue(events.get(1) instanceof StepEvent.Finished);
    assertEquals(events.get(1).getShortStepName(), "step1");
    assertEquals(events.get(1).getDescription(), "fake step 1");

    assertTrue(events.get(2) instanceof StepEvent.Started);
    assertEquals(events.get(2).getShortStepName(), "step1");
    assertEquals(events.get(2).getDescription(), "fake step 1");

    assertTrue(events.get(3) instanceof StepEvent.Finished);
    assertEquals(events.get(3).getShortStepName(), "step1");
    assertEquals(events.get(3).getDescription(), "fake step 1");

    assertTrue(events.get(0).isRelatedTo(events.get(1)));
    assertTrue(events.get(2).isRelatedTo(events.get(3)));

    assertFalse(events.get(0).isRelatedTo(events.get(2)));
    assertFalse(events.get(0).isRelatedTo(events.get(3)));
    assertFalse(events.get(1).isRelatedTo(events.get(2)));
    assertFalse(events.get(1).isRelatedTo(events.get(3)));
  }

  @Test
  public void testExplodingStep() throws InterruptedException, IOException {
    ExecutionContext context = TestExecutionContext.newInstance();

    DefaultStepRunner runner = new DefaultStepRunner();
    try {
      runner.runStepForBuildTarget(context, new ExplosionStep(), Optional.empty());
      fail("Should have thrown a StepFailedException!");
    } catch (StepFailedException e) {
      assertTrue(e.getMessage().startsWith("Failed on step explode with an exception:\n#yolo"));
    }
  }

  private static class ExplosionStep implements Step {
    @Override
    public StepExecutionResult execute(ExecutionContext context) {
      throw new RuntimeException("#yolo");
    }

    @Override
    public String getShortName() {
      return "explode";
    }

    @Override
    public String getDescription(ExecutionContext context) {
      return "MOAR EXPLOSIONS!!!!";
    }
  }
}
