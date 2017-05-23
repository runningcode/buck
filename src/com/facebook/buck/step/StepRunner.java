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

import com.facebook.buck.model.BuildTarget;
import java.util.Optional;

public interface StepRunner {

  /**
   * Runs a BuildStep for a given BuildRule.
   *
   * <p>Note that this method blocks until the specified command terminates.
   */
  void runStepForBuildTarget(ExecutionContext context, Step step, Optional<BuildTarget> buildTarget)
      throws StepFailedException, InterruptedException;
}
