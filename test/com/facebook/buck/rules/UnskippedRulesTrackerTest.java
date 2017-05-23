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

package com.facebook.buck.rules;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

import com.facebook.buck.event.AbstractBuckEvent;
import com.facebook.buck.event.BuckEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.DefaultBuckEventBus;
import com.facebook.buck.event.EventKey;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.timing.FakeClock;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UnskippedRulesTrackerTest {

  private SourcePathResolver sourcePathResolver;

  private UnskippedRulesTracker unskippedRulesTracker;
  private BuckEventBus eventBus;
  private BlockingQueue<BuckEvent> events = new LinkedBlockingQueue<>();

  private BuildRule ruleA;
  private BuildRule ruleB;
  private BuildRule ruleC;
  private BuildRule ruleD;
  private BuildRule ruleE;
  private BuildRule ruleF;
  private BuildRule ruleG;
  private BuildRule ruleH;

  @Before
  public void setUp() {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    sourcePathResolver = new SourcePathResolver(ruleFinder);
    RuleDepsCache depsCache = new RuleDepsCache(resolver);
    unskippedRulesTracker = new UnskippedRulesTracker(depsCache, resolver);
    eventBus = new DefaultBuckEventBus(new FakeClock(1), new BuildId());
    eventBus.register(
        new Object() {
          @Subscribe
          public void onUnskippedRuleCountUpdated(BuckEvent event) {
            events.add(event);
          }
        });
    ruleH = resolver.addToIndex(createRule("//:h"));
    ruleG = resolver.addToIndex(createRule("//:g"));
    ruleF = resolver.addToIndex(createRule("//:f"));
    ruleE = resolver.addToIndex(createRule("//:e", ImmutableSet.of(ruleG, ruleH)));
    ruleD = resolver.addToIndex(createRule("//:d", ImmutableSet.of(ruleG), ImmutableSet.of(ruleF)));
    ruleC = resolver.addToIndex(createRule("//:c", ImmutableSet.of(ruleD, ruleE)));
    ruleB = resolver.addToIndex(createRule("//:b", ImmutableSet.of(), ImmutableSet.of(ruleD)));
    ruleA = resolver.addToIndex(createRule("//:a", ImmutableSet.of(ruleD)));
  }

  @After
  public void checkForExtraEvents() throws InterruptedException {
    assertNoNewEvents();
  }

  // Visualisation of the action graph (rules depend on rules below them):
  //
  // a b c
  //  \|/ \
  //   d   e
  //  / \ / \
  // f   g   h
  //
  // b -> d and d -> f are runtime dependencies.

  @Test
  public void addingRuleMarksItsTransitiveDepsAsUnskipped() throws InterruptedException {
    unskippedRulesTracker.registerTopLevelRule(ruleA, eventBus);
    assertReceivedEvent(4);
    unskippedRulesTracker.registerTopLevelRule(ruleC, eventBus);
    assertReceivedEvent(7);
  }

  @Test
  public void addingRulesDepsDoesNotChangeState() throws InterruptedException {
    unskippedRulesTracker.registerTopLevelRule(ruleA, eventBus);
    assertReceivedEvent(4);
    unskippedRulesTracker.registerTopLevelRule(ruleD, eventBus);
    assertNoNewEvents();
  }

  @Test
  public void usingRuleMarksItsDepsAsSkipped() throws InterruptedException {
    unskippedRulesTracker.registerTopLevelRule(ruleC, eventBus);
    assertReceivedEvent(6);
    unskippedRulesTracker.markRuleAsUsed(ruleE, eventBus);
    assertReceivedEvent(5);
  }

  @Test
  public void usedRuleIsNeverSkipped() throws InterruptedException {
    unskippedRulesTracker.registerTopLevelRule(ruleA, eventBus);
    assertReceivedEvent(4);
    unskippedRulesTracker.markRuleAsUsed(ruleF, eventBus);
    assertNoNewEvents();
    unskippedRulesTracker.markRuleAsUsed(ruleD, eventBus);
    assertReceivedEvent(3);
    unskippedRulesTracker.markRuleAsUsed(ruleA, eventBus);
    assertNoNewEvents();
  }

  @Test
  public void rulesCanBeMarkedAsUsedEvenIfNoTopLevelRuleIsRegistered() throws InterruptedException {
    unskippedRulesTracker.markRuleAsUsed(ruleF, eventBus);
    assertReceivedEvent(1);
    unskippedRulesTracker.markRuleAsUsed(ruleD, eventBus);
    assertReceivedEvent(2);
    unskippedRulesTracker.registerTopLevelRule(ruleA, eventBus);
    assertReceivedEvent(3);
  }

  @Test
  public void onlyTopLevelRuleExecuted() throws InterruptedException {
    unskippedRulesTracker.registerTopLevelRule(ruleA, eventBus);
    assertReceivedEvent(4);
    unskippedRulesTracker.markRuleAsUsed(ruleA, eventBus);
    assertReceivedEvent(1);
  }

  @Test
  public void usingARuleDoesNotMarkItsRuntimeDepsAsSkipped() throws InterruptedException {
    unskippedRulesTracker.registerTopLevelRule(ruleB, eventBus);
    assertReceivedEvent(4);
    unskippedRulesTracker.markRuleAsUsed(ruleB, eventBus);
    assertNoNewEvents();
    unskippedRulesTracker.markRuleAsUsed(ruleD, eventBus);
    assertReceivedEvent(3);
    unskippedRulesTracker.markRuleAsUsed(ruleF, eventBus);
    assertNoNewEvents();
  }

  @Test
  public void multipleTopLevelRules() throws InterruptedException {
    unskippedRulesTracker.registerTopLevelRule(ruleA, eventBus);
    assertReceivedEvent(4);
    unskippedRulesTracker.markRuleAsUsed(ruleF, eventBus);
    assertNoNewEvents();
    unskippedRulesTracker.registerTopLevelRule(ruleB, eventBus);
    assertReceivedEvent(5);
    unskippedRulesTracker.registerTopLevelRule(ruleC, eventBus);
    assertReceivedEvent(8);
    unskippedRulesTracker.markRuleAsUsed(ruleD, eventBus);
    assertNoNewEvents();
    unskippedRulesTracker.markRuleAsUsed(ruleA, eventBus);
    assertNoNewEvents();
    unskippedRulesTracker.markRuleAsUsed(ruleC, eventBus);
    assertReceivedEvent(5);
    unskippedRulesTracker.markRuleAsUsed(ruleB, eventBus);
    assertNoNewEvents();
  }

  private void assertReceivedEvent(int numRules) throws InterruptedException {
    BuckEvent event = events.take();
    assertThat(event, is(instanceOf(BuildEvent.UnskippedRuleCountUpdated.class)));
    BuildEvent.UnskippedRuleCountUpdated countEvent = (BuildEvent.UnskippedRuleCountUpdated) event;
    assertThat(countEvent.getNumRules(), is(equalTo(numRules)));
  }

  private void assertNoNewEvents() throws InterruptedException {
    // BuckEventBus is asynchronous so it is not enough to check that the events queue is empty.
    // Instead, post a fake event and check that there were no pending events before it.
    BuckEvent sentinel = new FakeEvent();
    eventBus.post(sentinel);
    assertThat(
        "An event was received but no event was expected",
        events.take(),
        is(sameInstance(sentinel)));
  }

  private BuildRule createRule(String buildTarget) {
    return new FakeBuildRule(
        BuildTargetFactory.newInstance(buildTarget), sourcePathResolver, ImmutableSortedSet.of());
  }

  private BuildRule createRule(String buildTarget, ImmutableSet<BuildRule> deps) {
    return new FakeBuildRule(
        BuildTargetFactory.newInstance(buildTarget),
        sourcePathResolver,
        ImmutableSortedSet.copyOf(deps));
  }

  private BuildRule createRule(
      String buildTarget, ImmutableSet<BuildRule> deps, ImmutableSet<BuildRule> runtimeDeps) {
    return new FakeBuildRuleWithRuntimeDeps(
        BuildTargetFactory.newInstance(buildTarget),
        sourcePathResolver,
        ImmutableSortedSet.copyOf(deps),
        ImmutableSortedSet.copyOf(runtimeDeps));
  }

  private static class FakeBuildRuleWithRuntimeDeps extends FakeBuildRule
      implements HasRuntimeDeps {

    private final ImmutableSortedSet<BuildRule> runtimeDeps;

    public FakeBuildRuleWithRuntimeDeps(
        BuildTarget target,
        SourcePathResolver resolver,
        ImmutableSortedSet<BuildRule> deps,
        ImmutableSortedSet<BuildRule> runtimeDeps) {
      super(target, resolver, deps);
      this.runtimeDeps = runtimeDeps;
    }

    @Override
    public Stream<BuildTarget> getRuntimeDeps() {
      return runtimeDeps.stream().map(BuildRule::getBuildTarget);
    }
  }

  private static class FakeEvent extends AbstractBuckEvent {

    public FakeEvent() {
      super(EventKey.unique());
    }

    @Override
    protected String getValueString() {
      return "Fake event";
    }

    @Override
    public String getEventName() {
      return "FakeEvent";
    }
  }
}
