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
package com.facebook.buck.versions;

import static org.junit.Assert.assertThat;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.coercer.VersionMatchedCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.hamcrest.Matchers;
import org.junit.Test;

public class VersionMatchedCollectionTest {

  private static final BuildTarget A = BuildTargetFactory.newInstance("//:a");
  private static final BuildTarget B = BuildTargetFactory.newInstance("//:b");
  private static final Version V1 = Version.of("1.0");
  private static final Version V2 = Version.of("2.0");
  private static final VersionMatchedCollection<String> COLLECTION =
      VersionMatchedCollection.<String>builder()
          .add(ImmutableMap.of(A, V1, B, V1), "a-1.0,b-1.0")
          .add(ImmutableMap.of(A, V1, B, V2), "a-1.0,b-2.0")
          .add(ImmutableMap.of(A, V2, B, V1), "a-2.0,b-1.0")
          .add(ImmutableMap.of(A, V2, B, V2), "a-2.0,b-2.0")
          .build();

  @Test
  public void testGetMatchingValues() {
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(A, V1)),
        Matchers.equalTo(ImmutableList.of("a-1.0,b-1.0", "a-1.0,b-2.0")));
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(A, V2)),
        Matchers.equalTo(ImmutableList.of("a-2.0,b-1.0", "a-2.0,b-2.0")));
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(B, V1)),
        Matchers.equalTo(ImmutableList.of("a-1.0,b-1.0", "a-2.0,b-1.0")));
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(B, V2)),
        Matchers.equalTo(ImmutableList.of("a-1.0,b-2.0", "a-2.0,b-2.0")));
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(A, V1, B, V1)),
        Matchers.equalTo(ImmutableList.of("a-1.0,b-1.0")));
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(A, V1, B, V2)),
        Matchers.equalTo(ImmutableList.of("a-1.0,b-2.0")));
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(A, V2, B, V1)),
        Matchers.equalTo(ImmutableList.of("a-2.0,b-1.0")));
    assertThat(
        COLLECTION.getMatchingValues(ImmutableMap.of(A, V2, B, V2)),
        Matchers.equalTo(ImmutableList.of("a-2.0,b-2.0")));
  }

  @Test
  public void testGetOnlyMatchingValue() {
    assertThat(
        COLLECTION.getOnlyMatchingValue(ImmutableMap.of(A, V1, B, V1)),
        Matchers.equalTo("a-1.0,b-1.0"));
  }

  @Test(expected = IllegalStateException.class)
  public void testGetOnlyMatchingValueThrowsOnTooManyValues() {
    System.out.println(COLLECTION.getOnlyMatchingValue(ImmutableMap.of(A, V1)));
  }

  @Test(expected = IllegalStateException.class)
  public void testGetOnlyMatchingValueThrowsOnNoMatches() {
    System.out.println(COLLECTION.getOnlyMatchingValue(ImmutableMap.of(A, Version.of("3.0"))));
  }
}
