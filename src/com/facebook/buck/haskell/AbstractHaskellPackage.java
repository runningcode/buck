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

package com.facebook.buck.haskell;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.util.stream.Stream;
import org.immutables.value.Value;

/** Represents a Haskell package used as a dependency during compilation. */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractHaskellPackage implements RuleKeyAppendable {

  /** @return the package identifying information (i.e. name, version, identifier). */
  public abstract HaskellPackageInfo getInfo();

  /** @return the path to the package DB. */
  protected abstract SourcePath getPackageDb();

  /** @return the path to all libraries included in the package. */
  @Value.NaturalOrder
  protected abstract ImmutableSortedSet<SourcePath> getLibraries();

  /** @return the path to all interface directories included in the package. */
  @Value.NaturalOrder
  protected abstract ImmutableSortedSet<SourcePath> getInterfaces();

  /**
   * Adds information to the rule key that affects compilations that build using this package as a
   * dependency.
   */
  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    sink.setReflectively("info", getInfo());
    sink.setReflectively("libraries", getLibraries());
    sink.setReflectively("interfaces", getInterfaces());
    sink.setReflectively("package-db", getPackageDb());
  }

  /** @return all dependencies required to use this package at build time. */
  public final Stream<BuildRule> getDeps(SourcePathRuleFinder ruleFinder) {
    return Stream.of(ImmutableList.of(getPackageDb()), getLibraries(), getInterfaces())
        .flatMap(input -> ruleFinder.filterBuildRuleInputs(input).stream());
  }
}
