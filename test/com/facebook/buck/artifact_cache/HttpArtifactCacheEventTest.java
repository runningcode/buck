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

import com.facebook.buck.event.listener.ArtifactCacheTestUtils;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.rules.RuleKey;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class HttpArtifactCacheEventTest {

  private static final ImmutableSet<RuleKey> TEST_RULE_KEYS =
      ImmutableSet.of(new RuleKey("1234567890"), new RuleKey("123456"), new RuleKey("1234"));

  private static final RuleKey TEST_RULE_KEY = new RuleKey("4321");

  @Test
  public void storeDataContainsRuleKeys() throws IOException {
    HttpArtifactCacheEvent.Started started =
        ArtifactCacheTestUtils.newUploadConfiguredStartedEvent(
            new BuildId("monkey"), Optional.of("target"), TEST_RULE_KEYS);
    HttpArtifactCacheEvent.Finished finished =
        ArtifactCacheTestUtils.newFinishedEvent(started, true);
    Assert.assertEquals(TEST_RULE_KEYS, finished.getStoreData().getRuleKeys());
  }

  @Test
  public void fetchDataContainsRuleKey() throws IOException {
    HttpArtifactCacheEvent.Finished finished =
        ArtifactCacheTestUtils.newFetchFinishedEvent(
            ArtifactCacheTestUtils.newFetchConfiguredStartedEvent(TEST_RULE_KEY),
            CacheResult.hit("super source", ArtifactCacheMode.dir));
    Assert.assertEquals(TEST_RULE_KEY, finished.getFetchData().getRequestedRuleKey());
  }
}
