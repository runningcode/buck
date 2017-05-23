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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import org.immutables.value.Value;

/** Simple type representing a list of {@link SourceWithFlags}. */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractSourceWithFlagsList {

  public static final SourceWithFlagsList EMPTY =
      SourceWithFlagsList.ofUnnamedSources(ImmutableSortedSet.of());

  public enum Type {
    UNNAMED,
    NAMED,
  }

  @Value.Parameter
  public abstract Type getType();

  @Value.Parameter
  public abstract Optional<ImmutableSortedSet<SourceWithFlags>> getUnnamedSources();

  @Value.Parameter
  public abstract Optional<ImmutableSortedMap<String, SourceWithFlags>> getNamedSources();

  @Value.Check
  protected void check() {
    switch (getType()) {
      case UNNAMED:
        Preconditions.checkArgument(getUnnamedSources().isPresent());
        Preconditions.checkArgument(!getNamedSources().isPresent());
        break;
      case NAMED:
        Preconditions.checkArgument(!getUnnamedSources().isPresent());
        Preconditions.checkArgument(getNamedSources().isPresent());
        break;
      default:
        throw new RuntimeException("Unhandled type: " + getType());
    }
  }

  public static SourceWithFlagsList ofUnnamedSources(
      ImmutableSortedSet<SourceWithFlags> unnamedSources) {
    return SourceWithFlagsList.of(Type.UNNAMED, Optional.of(unnamedSources), Optional.empty());
  }

  public static SourceWithFlagsList ofNamedSources(
      ImmutableSortedMap<String, SourceWithFlags> namedSources) {
    return SourceWithFlagsList.of(Type.NAMED, Optional.empty(), Optional.of(namedSources));
  }
}
