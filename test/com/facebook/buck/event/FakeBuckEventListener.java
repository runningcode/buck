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

package com.facebook.buck.event;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import java.util.LinkedList;
import java.util.List;

public class FakeBuckEventListener {
  private final List<BuckEvent> events = new LinkedList<>();

  @Subscribe
  public void eventFired(BuckEvent event) {
    synchronized (events) {
      events.add(event);
    }
  }

  public List<BuckEvent> getEvents() {
    synchronized (events) {
      return ImmutableList.copyOf(events);
    }
  }
}
