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

import com.facebook.buck.cxx.CxxGenruleDescription;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.File;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
abstract class AbstractHaskellSources implements RuleKeyAppendable {

  @Value.NaturalOrder
  abstract ImmutableSortedMap<String, SourcePath> getModuleMap();

  public static HaskellSources from(
      BuildTarget target,
      BuildRuleResolver ruleResolver,
      SourcePathResolver pathResolver,
      SourcePathRuleFinder ruleFinder,
      CxxPlatform cxxPlatform,
      String parameter,
      SourceList sources)
      throws NoSuchBuildTargetException {
    HaskellSources.Builder builder = HaskellSources.builder();
    for (Map.Entry<String, SourcePath> ent :
        sources.toNameMap(target, pathResolver, parameter).entrySet()) {
      builder.putModuleMap(
          ent.getKey().substring(0, ent.getKey().lastIndexOf('.')).replace(File.separatorChar, '.'),
          CxxGenruleDescription.fixupSourcePath(
              ruleResolver, ruleFinder, cxxPlatform, ent.getValue()));
    }
    return builder.build();
  }

  public ImmutableSortedSet<String> getModuleNames() {
    return getModuleMap().keySet();
  }

  public ImmutableCollection<SourcePath> getSourcePaths() {
    return getModuleMap().values();
  }

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    sink.setReflectively("modules", getModuleMap());
  }

  public Iterable<BuildRule> getDeps(SourcePathRuleFinder ruleFinder) {
    return ruleFinder.filterBuildRuleInputs(getSourcePaths());
  }
}
