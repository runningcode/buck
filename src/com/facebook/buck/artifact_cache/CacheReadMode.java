/*
 * Copyright 2017-present Facebook, Inc.
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

package com.facebook.buck.artifact_cache;

/** Describes whether the cache allows stores. */
public enum CacheReadMode {
  // No stores allowed.
  READONLY(false),
  // "Depends who's asking". Stores allowed, however only from prior caches.
  PASSTHROUGH(true),
  // All stores allowed.
  READWRITE(true),
  ;

  private final boolean writable;

  CacheReadMode(boolean writable) {
    this.writable = writable;
  }

  public boolean isWritable() {
    return writable;
  }
}
