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

package com.facebook.buck.cxx;

import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.immutables.value.Value;

/**
 * Tracks flags passed to the preprocessor or compiler.
 *
 * <p>Flags derived from the "platform" are differentiated from flags derived from the "rule" to
 * allow for rule flags to override platform flags. This is particularly important when the platform
 * and rule flags from different sources are merged together.
 *
 * <p>Users should use the API in this class instead of the concrete implementations.
 */
public abstract class CxxToolFlags {
  private static final CxxToolFlags EMPTY_FLAGS = CxxToolFlags.explicitBuilder().build();

  /** Flags that precede flags from {@code #getRuleFlags()}. */
  public abstract Iterable<String> getPlatformFlags();

  /** Flags that succeed flags from {@code #getPlatformFlags()}. */
  public abstract Iterable<String> getRuleFlags();

  /** Returns all flags in the appropriate order. */
  public final Iterable<String> getAllFlags() {
    return Iterables.concat(getPlatformFlags(), getRuleFlags());
  }

  /** Returns a builder for explicitly specifying the flags. */
  public static ExplicitCxxToolFlags.Builder explicitBuilder() {
    return ExplicitCxxToolFlags.builder();
  }

  /** Returns the empty lists of flags. */
  public static CxxToolFlags of() {
    return EMPTY_FLAGS;
  }

  /** Directly construct an instance from the given members. */
  public static CxxToolFlags copyOf(Iterable<String> platformFlags, Iterable<String> ruleFlags) {
    return ExplicitCxxToolFlags.of(platformFlags, ruleFlags);
  }

  /** Concatenate multiple flags in a pairwise manner. */
  public static CxxToolFlags concat(CxxToolFlags... parts) {
    Iterable<String> platformFlags = ImmutableList.of();
    Iterable<String> ruleFlags = ImmutableList.of();
    for (CxxToolFlags part : parts) {
      platformFlags = Iterables.concat(platformFlags, part.getPlatformFlags());
      ruleFlags = Iterables.concat(ruleFlags, part.getRuleFlags());
    }
    return IterableCxxToolFlags.of(platformFlags, ruleFlags);
  }
}

interface CxxToolFlagsBuilder {
  CxxToolFlags build();
}

/** {@code CxxToolFlags} implementation where the flags are stored explicitly as lists. */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractExplicitCxxToolFlags extends CxxToolFlags {
  public abstract static class Builder implements CxxToolFlagsBuilder {}

  @Override
  @Value.Parameter
  public abstract ImmutableList<String> getPlatformFlags();

  @Override
  @Value.Parameter
  public abstract ImmutableList<String> getRuleFlags();

  public static void addCxxToolFlags(ExplicitCxxToolFlags.Builder builder, CxxToolFlags flags) {
    builder.addAllPlatformFlags(flags.getPlatformFlags());
    builder.addAllRuleFlags(flags.getRuleFlags());
  }
}

/**
 * {@code CxxToolFlags} implementation where the flags are stored as composed Iterables.
 *
 * <p>This improves sharing and reduce copying and memory pressure.
 */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractIterableCxxToolFlags extends CxxToolFlags {
  @Override
  public abstract Iterable<String> getPlatformFlags();

  @Override
  public abstract Iterable<String> getRuleFlags();
}
