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

package com.facebook.buck.event;

import com.facebook.buck.event.api.BuckTracing;
import com.facebook.buck.event.api.BuckTracingInterface;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/** Bridges the {@link BuckTracing} API (in the system ClassLoader) with {@link BuckEventBus}. */
public class BuckTracingEventBusBridge implements BuckTracingInterface {
  private static final Logger LOG = Logger.get(BuckTracingEventBusBridge.class);

  private final BuckEventBus eventBus;
  private final BuildTarget buildTarget;
  private final Deque<CompilerPluginDurationEvent.Started> eventStack = new ArrayDeque<>();

  public BuckTracingEventBusBridge(BuckEventBus eventBus, BuildTarget buildTarget) {
    this.eventBus = eventBus;
    this.buildTarget = buildTarget;
  }

  @Override
  public void begin(
      final String pluginName, final String eventName, final Map<String, String> args) {
    final CompilerPluginDurationEvent.Started startedEvent =
        CompilerPluginDurationEvent.started(
            buildTarget, pluginName, eventName, ImmutableMap.copyOf(args));

    eventStack.push(startedEvent);

    eventBus.post(startedEvent);
  }

  @Override
  public void end(final Map<String, String> args) {
    if (eventStack.isEmpty()) {
      LOG.warn(new Throwable(), "Compiler plugin event stack underflow.");
      return;
    }

    final CompilerPluginDurationEvent.Finished finishedEvent =
        CompilerPluginDurationEvent.finished(eventStack.pop(), ImmutableMap.copyOf(args));

    eventBus.post(finishedEvent);
  }
}
