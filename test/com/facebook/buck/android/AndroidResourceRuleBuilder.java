/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.android;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;

public class AndroidResourceRuleBuilder {

  private AndroidResourceRuleBuilder() {
    // Utility class
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {

    private SourcePathRuleFinder ruleFinder;
    private BuildRuleParams buildRuleParams;
    private ImmutableSortedSet<BuildRule> deps = ImmutableSortedSet.of();
    private SourcePath res;
    private ImmutableSortedMap<Path, SourcePath> resSrcs = ImmutableSortedMap.of();
    private String rDotJavaPackage;
    private SourcePath assets;
    private ImmutableSortedMap<Path, SourcePath> assetsSrcs = ImmutableSortedMap.of();
    private SourcePath manifest;
    private boolean hasWhitelistedStrings = false;

    public AndroidResource build() {
      return new AndroidResource(
          buildRuleParams,
          ruleFinder,
          deps,
          res,
          resSrcs,
          rDotJavaPackage,
          assets,
          assetsSrcs,
          manifest,
          hasWhitelistedStrings);
    }

    public Builder setBuildRuleParams(BuildRuleParams params) {
      this.buildRuleParams = params;
      return this;
    }

    public Builder setRuleFinder(SourcePathRuleFinder ruleFinder) {
      this.ruleFinder = ruleFinder;
      return this;
    }

    public Builder setBuildTarget(BuildTarget buildTarget) {
      buildRuleParams = new FakeBuildRuleParamsBuilder(buildTarget).build();
      return this;
    }

    public Builder setDeps(ImmutableSortedSet<BuildRule> deps) {
      this.deps = deps;
      return this;
    }

    public Builder setRes(SourcePath res) {
      this.res = res;
      return this;
    }

    public Builder setResSrcs(ImmutableSortedMap<Path, SourcePath> resSrcs) {
      this.resSrcs = resSrcs;
      return this;
    }

    public Builder setRDotJavaPackage(String rDotJavaPackage) {
      this.rDotJavaPackage = rDotJavaPackage;
      return this;
    }

    public Builder setAssets(SourcePath assets) {
      this.assets = assets;
      return this;
    }

    public Builder setAssetsSrcs(ImmutableSortedMap<Path, SourcePath> assetsSrcs) {
      this.assetsSrcs = assetsSrcs;
      return this;
    }

    public Builder setManifest(SourcePath manifest) {
      this.manifest = manifest;
      return this;
    }
  }
}
