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

package com.facebook.buck.util.environment;

import com.facebook.buck.util.immutables.BuckStyleTuple;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/** Test utility implementation of {@link ExecutionEnvironment} based on an immutable value type. */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractFakeExecutionEnvironment implements ExecutionEnvironment {
  @Override
  public abstract String getHostname();

  @Override
  public abstract String getUsername();

  @Override
  public abstract int getAvailableCores();

  @Override
  public abstract long getTotalMemory();

  @Override
  public abstract Platform getPlatform();

  @Override
  public abstract Network getLikelyActiveNetwork();

  @Override
  public abstract Optional<String> getWifiSsid();

  public abstract Map<String, String> getEnvironment();

  @Override
  public String getenv(String key, String defaultValue) {
    return getWithDefault(getEnvironment(), key, defaultValue);
  }

  private static String getWithDefault(
      Map<String, String> values, String key, String defaultValue) {
    String result = values.get(key);
    if (result != null) {
      return result;
    } else {
      return defaultValue;
    }
  }
}
