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

package com.facebook.buck.io;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/** Testable interface for a Watchman client. */
public interface WatchmanClient extends AutoCloseable {
  Optional<? extends Map<String, ? extends Object>> queryWithTimeout(
      long timeoutNanos, Object... query) throws IOException, InterruptedException;

  @Override
  public void close() throws IOException;
}
