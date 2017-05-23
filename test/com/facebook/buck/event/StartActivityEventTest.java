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

import static com.facebook.buck.event.TestEventConfigurator.configureTestEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.facebook.buck.model.BuildTargetFactory;
import org.hamcrest.Matchers;
import org.junit.Test;

public class StartActivityEventTest {
  @Test
  public void testEquals() throws Exception {
    StartActivityEvent.Started started =
        configureTestEvent(
            StartActivityEvent.started(BuildTargetFactory.newInstance("//foo:bar"), "com.foo.bar"));
    StartActivityEvent.Started startedTwo =
        configureTestEvent(
            StartActivityEvent.started(BuildTargetFactory.newInstance("//foo:bar"), "com.foo.bar"));
    StartActivityEvent finished = configureTestEvent(StartActivityEvent.finished(started, false));
    StartActivityEvent finishedTwo =
        configureTestEvent(StartActivityEvent.finished(started, false));
    StartActivityEvent finishedSucceed =
        configureTestEvent(StartActivityEvent.finished(started, true));

    assertEquals(started, started);
    assertNotEquals(started, finished);
    assertNotEquals(started, startedTwo);
    try {
      finished.equals(finishedSucceed);
      fail("Expected an UnsupportedOperationException.");
    } catch (UnsupportedOperationException e) {
      assertThat(e.toString(), Matchers.stringContainsInOrder("conflicting", "events"));
    }

    assertThat(started.isRelatedTo(finished), Matchers.is(true));
    assertThat(started.isRelatedTo(finishedTwo), Matchers.is(true));
    assertThat(finished.isRelatedTo(started), Matchers.is(true));

    assertThat(started.isRelatedTo(startedTwo), Matchers.is(false));
  }
}
