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

package com.facebook.buck.rules;

import com.facebook.buck.event.AbstractBuckEvent;
import com.facebook.buck.event.EventKey;
import com.facebook.buck.event.WorkAdvanceEvent;
import com.facebook.buck.event.external.events.IndividualTesEventFinishedExternalInterface;
import com.facebook.buck.test.TestResults;

public abstract class IndividualTestEvent extends AbstractBuckEvent implements WorkAdvanceEvent {

  private IndividualTestEvent(int secret) {
    super(EventKey.slowValueKey("IndividualTestEvent", secret));
  }

  public static Started started(Iterable<String> targets) {
    return new Started(targets.hashCode());
  }

  public static Finished finished(Iterable<String> targets, TestResults results) {
    return new Finished(targets.hashCode(), results);
  }

  public static class Started extends IndividualTestEvent {

    public Started(int secret) {
      super(secret);
    }

    @Override
    public String getEventName() {
      return INDIVIDUAL_TEST_AWAITING_RESULTS;
    }

    @Override
    protected String getValueString() {
      return "waiting for test results";
    }
  }

  public static class Finished extends IndividualTestEvent
      implements IndividualTesEventFinishedExternalInterface<TestResults> {

    private final TestResults results;

    private Finished(int secret, TestResults results) {
      // You have no idea how much fun it is to write "super secret" in a private class.
      super(secret);
      this.results = results;
    }

    @Override
    public TestResults getResults() {
      return results;
    }

    @Override
    public String getEventName() {
      return RESULTS_AVAILABLE;
    }

    @Override
    protected String getValueString() {
      return String.format(
          "%s (%d failed in %d test cases)",
          results.isSuccess() ? "PASS" : "FAIL",
          results.getFailureCount(),
          results.getTestCases().size());
    }
  }
}
