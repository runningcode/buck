/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.timing;

/**
 * {@link Clock} implementation that invokes the {@link System} calls, adjusted to use the given
 * nanos epoch.
 */
public class NanosAdjustedClock extends DefaultClock {
  private final long nanosEpoch;

  public NanosAdjustedClock(long nanosEpoch) {
    this(nanosEpoch, true);
  }

  public NanosAdjustedClock(long nanosEpoch, boolean enableThreadCpuTime) {
    super(enableThreadCpuTime);
    this.nanosEpoch = nanosEpoch - System.nanoTime();
  }

  @Override
  public long nanoTime() {
    return System.nanoTime() + nanosEpoch;
  }
}
