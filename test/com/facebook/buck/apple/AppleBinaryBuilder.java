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

package com.facebook.buck.apple;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;

public class AppleBinaryBuilder
    extends AbstractNodeBuilder<
        AppleBinaryDescriptionArg.Builder, AppleBinaryDescriptionArg, AppleBinaryDescription,
        BuildRule> {

  protected AppleBinaryBuilder(BuildTarget target) {
    super(FakeAppleRuleDescriptions.BINARY_DESCRIPTION, target);
  }

  public static AppleBinaryBuilder createBuilder(BuildTarget target) {
    return new AppleBinaryBuilder(target);
  }

  public AppleBinaryBuilder setConfigs(
      ImmutableSortedMap<String, ImmutableMap<String, String>> configs) {
    getArgForPopulating().setConfigs(configs);
    return this;
  }

  public AppleBinaryBuilder setCompilerFlags(ImmutableList<String> compilerFlags) {
    getArgForPopulating().setCompilerFlags(compilerFlags);
    return this;
  }

  public AppleBinaryBuilder setPlatformCompilerFlags(
      PatternMatchedCollection<ImmutableList<String>> platformPreprocessorFlags) {
    getArgForPopulating().setPlatformPreprocessorFlags(platformPreprocessorFlags);
    return this;
  }

  public AppleBinaryBuilder setPreprocessorFlags(ImmutableList<String> preprocessorFlags) {
    getArgForPopulating().setPreprocessorFlags(preprocessorFlags);
    return this;
  }

  public AppleBinaryBuilder setLinkerFlags(ImmutableList<StringWithMacros> linkerFlags) {
    getArgForPopulating().setLinkerFlags(linkerFlags);
    return this;
  }

  public AppleBinaryBuilder setExportedLinkerFlags(
      ImmutableList<StringWithMacros> exportedLinkerFlags) {
    getArgForPopulating().setExportedLinkerFlags(exportedLinkerFlags);
    return this;
  }

  public AppleBinaryBuilder setSrcs(ImmutableSortedSet<SourceWithFlags> srcs) {
    getArgForPopulating().setSrcs(srcs);
    return this;
  }

  public AppleBinaryBuilder setExtraXcodeSources(ImmutableList<SourcePath> extraXcodeSources) {
    getArgForPopulating().setExtraXcodeSources(extraXcodeSources);
    return this;
  }

  public AppleBinaryBuilder setHeaders(SourceList headers) {
    getArgForPopulating().setHeaders(headers);
    return this;
  }

  public AppleBinaryBuilder setHeaders(ImmutableSortedSet<SourcePath> headers) {
    return setHeaders(SourceList.ofUnnamedSources(headers));
  }

  public AppleBinaryBuilder setHeaders(ImmutableSortedMap<String, SourcePath> headers) {
    return setHeaders(SourceList.ofNamedSources(headers));
  }

  public AppleBinaryBuilder setFrameworks(ImmutableSortedSet<FrameworkPath> frameworks) {
    getArgForPopulating().setFrameworks(frameworks);
    return this;
  }

  public AppleBinaryBuilder setLibraries(ImmutableSortedSet<FrameworkPath> libraries) {
    getArgForPopulating().setLibraries(libraries);
    return this;
  }

  public AppleBinaryBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    getArgForPopulating().setDeps(deps);
    return this;
  }

  public AppleBinaryBuilder setExportedDeps(ImmutableSortedSet<BuildTarget> exportedDeps) {
    getArgForPopulating().setExportedDeps(exportedDeps);
    return this;
  }

  public AppleBinaryBuilder setHeaderPathPrefix(Optional<String> headerPathPrefix) {
    getArgForPopulating().setHeaderPathPrefix(headerPathPrefix);
    return this;
  }

  public AppleBinaryBuilder setPrefixHeader(Optional<SourcePath> prefixHeader) {
    getArgForPopulating().setPrefixHeader(prefixHeader);
    return this;
  }

  public AppleBinaryBuilder setTests(ImmutableSortedSet<BuildTarget> tests) {
    getArgForPopulating().setTests(tests);
    return this;
  }
}
