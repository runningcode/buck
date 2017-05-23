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

package com.facebook.buck.rules.keys;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.util.cache.FileHashCache;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.util.function.Supplier;

public class UncachedRuleKeyBuilder extends RuleKeyBuilder<HashCode> {

  private final RuleKeyFactory<RuleKey> ruleKeyFactory;
  private final Supplier<UncachedRuleKeyBuilder> subKeySupplier;

  public UncachedRuleKeyBuilder(
      SourcePathRuleFinder ruleFinder,
      SourcePathResolver resolver,
      FileHashCache hashCache,
      RuleKeyFactory<RuleKey> ruleKeyFactory) {
    this(ruleFinder, resolver, hashCache, createHasher(), ruleKeyFactory);
  }

  public UncachedRuleKeyBuilder(
      SourcePathRuleFinder ruleFinder,
      SourcePathResolver resolver,
      FileHashCache hashCache,
      RuleKeyHasher<HashCode> hasher,
      RuleKeyFactory<RuleKey> ruleKeyFactory) {
    super(ruleFinder, resolver, hashCache, hasher);
    this.ruleKeyFactory = ruleKeyFactory;
    this.subKeySupplier = createSubKeySupplier(ruleFinder, resolver, hashCache, ruleKeyFactory);
  }

  private static RuleKeyHasher<HashCode> createHasher() {
    return new GuavaRuleKeyHasher(Hashing.sha1().newHasher());
  }

  private static Supplier<UncachedRuleKeyBuilder> createSubKeySupplier(
      SourcePathRuleFinder ruleFinder,
      final SourcePathResolver resolver,
      final FileHashCache hashCache,
      final RuleKeyFactory<RuleKey> ruleKeyFactory) {
    return () -> new UncachedRuleKeyBuilder(ruleFinder, resolver, hashCache, ruleKeyFactory);
  }

  @Override
  protected UncachedRuleKeyBuilder setBuildRule(BuildRule rule) {
    setBuildRuleKey(ruleKeyFactory.build(rule));
    return this;
  }

  @Override
  protected UncachedRuleKeyBuilder setAppendableRuleKey(RuleKeyAppendable appendable) {
    RuleKeyBuilder<HashCode> subKeyBuilder = subKeySupplier.get();
    appendable.appendToRuleKey(subKeyBuilder);
    RuleKey subKey = subKeyBuilder.build(RuleKey::new);
    setAppendableRuleKey(subKey);
    return this;
  }

  @Override
  protected RuleKeyBuilder<HashCode> setSourcePath(SourcePath sourcePath) throws IOException {
    if (sourcePath instanceof ExplicitBuildTargetSourcePath) {
      return setSourcePathAsRule((ExplicitBuildTargetSourcePath) sourcePath);
    } else {
      return setSourcePathDirectly(sourcePath);
    }
  }

  @Override
  protected RuleKeyBuilder<HashCode> setNonHashingSourcePath(SourcePath sourcePath) {
    return setNonHashingSourcePathDirectly(sourcePath);
  }
}
