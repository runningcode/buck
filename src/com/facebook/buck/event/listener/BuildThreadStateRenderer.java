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

package com.facebook.buck.event.listener;

import com.facebook.buck.event.LeafEvent;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRuleEvent;
import com.facebook.buck.util.Ansi;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;

public class BuildThreadStateRenderer implements MultiStateRenderer {

  private final CommonThreadStateRenderer commonThreadStateRenderer;
  private final ImmutableMap<Long, ThreadRenderingInformation> threadInformationMap;

  public BuildThreadStateRenderer(
      Ansi ansi,
      Function<Long, String> formatTimeFunction,
      long currentTimeMs,
      Map<Long, Optional<? extends LeafEvent>> runningStepsByThread,
      BuildRuleThreadTracker buildRuleThreadTracker) {
    this.threadInformationMap =
        getThreadInformationMap(currentTimeMs, runningStepsByThread, buildRuleThreadTracker);
    this.commonThreadStateRenderer =
        new CommonThreadStateRenderer(
            ansi, formatTimeFunction, currentTimeMs, threadInformationMap);
  }

  private static ImmutableMap<Long, ThreadRenderingInformation> getThreadInformationMap(
      long currentTimeMs,
      Map<Long, Optional<? extends LeafEvent>> runningStepsByThread,
      BuildRuleThreadTracker buildRuleThreadTracker) {
    ImmutableMap.Builder<Long, ThreadRenderingInformation> threadInformationMapBuilder =
        ImmutableMap.builder();
    Map<Long, Optional<? extends BuildRuleEvent.BeginningBuildRuleEvent>> buildEventsByThread =
        buildRuleThreadTracker.getBuildEventsByThread();
    ImmutableList<Long> threadIds = ImmutableList.copyOf(buildEventsByThread.keySet());
    for (long threadId : threadIds) {
      Optional<? extends BuildRuleEvent.BeginningBuildRuleEvent> buildRuleEvent =
          buildEventsByThread.get(threadId);
      if (buildRuleEvent == null) {
        continue;
      }
      Optional<BuildTarget> buildTarget = Optional.empty();
      long elapsedTimeMs = 0;
      if (buildRuleEvent.isPresent()) {
        buildTarget = Optional.of(buildRuleEvent.get().getBuildRule().getBuildTarget());
        elapsedTimeMs =
            currentTimeMs
                - buildRuleEvent.get().getTimestamp()
                + buildRuleEvent.get().getDuration().getWallMillisDuration();
      }
      threadInformationMapBuilder.put(
          threadId,
          new ThreadRenderingInformation(
              buildTarget,
              buildRuleEvent,
              Optional.empty(),
              Optional.empty(),
              runningStepsByThread.getOrDefault(threadId, Optional.empty()),
              elapsedTimeMs));
    }
    return threadInformationMapBuilder.build();
  }

  @Override
  public String getExecutorCollectionLabel() {
    return "THREADS";
  }

  @Override
  public int getExecutorCount() {
    return commonThreadStateRenderer.getThreadCount();
  }

  @Override
  public ImmutableList<Long> getSortedExecutorIds(boolean sortByTime) {
    return commonThreadStateRenderer.getSortedThreadIds(sortByTime);
  }

  @Override
  public String renderStatusLine(long threadId, StringBuilder lineBuilder) {
    ThreadRenderingInformation threadInformation =
        Preconditions.checkNotNull(threadInformationMap.get(threadId));
    return commonThreadStateRenderer.renderLine(
        threadInformation.getBuildTarget(),
        threadInformation.getStartEvent(),
        threadInformation.getRunningStep(),
        threadInformation.getRunningStep().map(LeafEvent::getCategory),
        Optional.of("checking_cache"),
        threadInformation.getElapsedTimeMs(),
        lineBuilder);
  }

  @Override
  public String renderShortStatus(long threadId) {
    ThreadRenderingInformation threadInformation =
        Preconditions.checkNotNull(threadInformationMap.get(threadId));
    return commonThreadStateRenderer.renderShortStatus(
        threadInformation.getStartEvent().isPresent(),
        !threadInformation.getRunningStep().isPresent(),
        threadInformation.getElapsedTimeMs());
  }
}
