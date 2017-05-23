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

package com.facebook.buck.artifact_cache;

import com.facebook.buck.io.BorrowablePath;
import com.facebook.buck.io.LazyPath;
import com.facebook.buck.rules.RuleKey;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import javax.annotation.Nullable;

public class DummyArtifactCache extends NoopArtifactCache {

  @Nullable public RuleKey storeKey;

  public void reset() {
    storeKey = null;
  }

  @Override
  public CacheResult fetch(RuleKey ruleKey, LazyPath output) {
    return ruleKey.equals(storeKey)
        ? CacheResult.hit("cache", ArtifactCacheMode.http)
        : CacheResult.miss();
  }

  @Override
  public ListenableFuture<Void> store(ArtifactInfo info, BorrowablePath output) {
    storeKey = Iterables.getFirst(info.getRuleKeys(), null);
    return Futures.immediateFuture(null);
  }

  @Override
  public CacheReadMode getCacheReadMode() {
    return CacheReadMode.READWRITE;
  }
}
