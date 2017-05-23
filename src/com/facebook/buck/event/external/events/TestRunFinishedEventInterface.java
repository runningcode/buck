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

package com.facebook.buck.event.external.events;

import java.util.List;

/**
 * Describes the results of the tests on all the targets. This type is intended to be used by
 * external applications (like the Intellij Buck plugin) to deserialize events coming from the
 * webserver.
 */
public interface TestRunFinishedEventInterface<T> extends BuckEventExternalInterface {
  // Sent when the test run has finished
  String RUN_COMPLETE = "RunComplete";
  /**
   * @return a list all the test results available after running buck test. For buck it returns a
   *     list of TestResults and for external a list of TestResultsExternalInterface.
   * @see com.facebook.buck.event.external.elements.TestResultsExternalInterface
   */
  List<T> getResults();
}
