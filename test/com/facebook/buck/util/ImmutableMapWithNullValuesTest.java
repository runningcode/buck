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

package com.facebook.buck.util;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import java.util.HashMap;
import org.junit.Test;

public class ImmutableMapWithNullValuesTest {
  @Test
  public void emptyInsertionOrder() {
    assertEquals(ImmutableMap.of(), ImmutableMapWithNullValues.Builder.insertionOrder().build());
  }

  @Test
  public void noNullsInsertionOrder() {
    assertEquals(
        ImmutableMap.of(1, "A"),
        ImmutableMapWithNullValues.Builder.insertionOrder().put(1, "A").build());
  }

  @Test
  public void justNullsInsertionOrder() {
    ImmutableMapWithNullValues<Integer, String> map =
        ImmutableMapWithNullValues.Builder.<Integer, String>insertionOrder()
            .put(2, null)
            .put(1, null)
            .build();
    assertEquals(
        new HashMap<Integer, String>() {
          {
            put(2, null);
            put(1, null);
          }
        },
        map);
    assertEquals(ImmutableList.of(2, 1), ImmutableList.copyOf(map.keySet()));
  }

  @Test
  public void mixedNullsInsertionOrder() {
    ImmutableMapWithNullValues<Integer, String> map =
        ImmutableMapWithNullValues.Builder.<Integer, String>insertionOrder()
            .put(2, null)
            .put(1, "one")
            .put(3, "three")
            .build();
    assertEquals(
        new HashMap<Integer, String>() {
          {
            put(2, null);
            put(1, "one");
            put(3, "three");
          }
        },
        map);
    assertEquals(ImmutableList.of(2, 1, 3), ImmutableList.copyOf(map.keySet()));
  }

  @Test
  public void emptySorted() {
    assertEquals(ImmutableSortedMap.of(), ImmutableMapWithNullValues.Builder.sorted().build());
  }

  @Test
  public void noNullsSorted() {
    assertEquals(
        ImmutableSortedMap.of(1, "A"),
        ImmutableMapWithNullValues.Builder.sorted().put(1, "A").build());
  }

  @Test
  public void justNullsSorted() {
    ImmutableMapWithNullValues<Integer, String> map =
        ImmutableMapWithNullValues.Builder.<Integer, String>sorted()
            .put(2, null)
            .put(1, null)
            .build();
    assertEquals(
        new HashMap<Integer, String>() {
          {
            put(2, null);
            put(1, null);
          }
        },
        map);
    assertEquals(ImmutableList.of(1, 2), ImmutableList.copyOf(map.keySet()));
  }

  @Test
  public void mixedNullsSorted() {
    ImmutableMapWithNullValues<Integer, String> map =
        ImmutableMapWithNullValues.Builder.<Integer, String>sorted()
            .put(2, null)
            .put(1, "one")
            .put(3, "three")
            .build();
    assertEquals(
        new HashMap<Integer, String>() {
          {
            put(2, null);
            put(1, "one");
            put(3, "three");
          }
        },
        map);
    assertEquals(ImmutableList.of(1, 2, 3), ImmutableList.copyOf(map.keySet()));
  }
}
