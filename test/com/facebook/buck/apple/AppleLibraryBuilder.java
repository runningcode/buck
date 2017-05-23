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

import com.facebook.buck.cxx.CxxSource;
import com.facebook.buck.cxx.NativeLinkable;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;

public class AppleLibraryBuilder
    extends AbstractNodeBuilder<
        AppleLibraryDescriptionArg.Builder, AppleLibraryDescriptionArg, AppleLibraryDescription,
        BuildRule> {

  protected AppleLibraryBuilder(BuildTarget target) {
    super(FakeAppleRuleDescriptions.LIBRARY_DESCRIPTION, target);
  }

  public static AppleLibraryBuilder createBuilder(BuildTarget target) {
    return new AppleLibraryBuilder(target);
  }

  public AppleLibraryBuilder setModular(boolean modular) {
    getArgForPopulating().setModular(modular);
    return this;
  }

  public AppleLibraryBuilder setConfigs(
      ImmutableSortedMap<String, ImmutableMap<String, String>> configs) {
    getArgForPopulating().setConfigs(configs);
    return this;
  }

  public AppleLibraryBuilder setCompilerFlags(ImmutableList<String> compilerFlags) {
    getArgForPopulating().setCompilerFlags(compilerFlags);
    return this;
  }

  public AppleLibraryBuilder setPreprocessorFlags(ImmutableList<String> preprocessorFlags) {
    getArgForPopulating().setPreprocessorFlags(preprocessorFlags);
    return this;
  }

  public AppleLibraryBuilder setLangPreprocessorFlags(
      ImmutableMap<CxxSource.Type, ImmutableList<String>> langPreprocessorFlags) {
    getArgForPopulating().setLangPreprocessorFlags(langPreprocessorFlags);
    return this;
  }

  public AppleLibraryBuilder setExportedPreprocessorFlags(
      ImmutableList<String> exportedPreprocessorFlags) {
    getArgForPopulating().setExportedPreprocessorFlags(exportedPreprocessorFlags);
    return this;
  }

  public AppleLibraryBuilder setLinkerFlags(ImmutableList<StringWithMacros> linkerFlags) {
    getArgForPopulating().setLinkerFlags(linkerFlags);
    return this;
  }

  public AppleLibraryBuilder setExportedLinkerFlags(
      ImmutableList<StringWithMacros> exportedLinkerFlags) {
    getArgForPopulating().setExportedLinkerFlags(exportedLinkerFlags);
    return this;
  }

  public AppleLibraryBuilder setSrcs(ImmutableSortedSet<SourceWithFlags> srcs) {
    getArgForPopulating().setSrcs(srcs);
    return this;
  }

  public AppleLibraryBuilder setExtraXcodeSources(ImmutableList<SourcePath> extraXcodeSources) {
    getArgForPopulating().setExtraXcodeSources(extraXcodeSources);
    return this;
  }

  public AppleLibraryBuilder setExtraXcodeFiles(ImmutableList<SourcePath> extraXcodeFiles) {
    getArgForPopulating().setExtraXcodeFiles(extraXcodeFiles);
    return this;
  }

  public AppleLibraryBuilder setHeaders(SourceList headers) {
    getArgForPopulating().setHeaders(headers);
    return this;
  }

  public AppleLibraryBuilder setHeaders(ImmutableSortedSet<SourcePath> headers) {
    return setHeaders(SourceList.ofUnnamedSources(headers));
  }

  public AppleLibraryBuilder setHeaders(ImmutableSortedMap<String, SourcePath> headers) {
    return setHeaders(SourceList.ofNamedSources(headers));
  }

  public AppleLibraryBuilder setExportedHeaders(SourceList exportedHeaders) {
    getArgForPopulating().setExportedHeaders(exportedHeaders);
    return this;
  }

  public AppleLibraryBuilder setExportedHeaders(ImmutableSortedSet<SourcePath> exportedHeaders) {
    return setExportedHeaders(SourceList.ofUnnamedSources(exportedHeaders));
  }

  public AppleLibraryBuilder setExportedHeaders(
      ImmutableSortedMap<String, SourcePath> exportedHeaders) {
    return setExportedHeaders(SourceList.ofNamedSources(exportedHeaders));
  }

  public AppleLibraryBuilder setFrameworks(ImmutableSortedSet<FrameworkPath> frameworks) {
    getArgForPopulating().setFrameworks(frameworks);
    return this;
  }

  public AppleLibraryBuilder setLibraries(ImmutableSortedSet<FrameworkPath> libraries) {
    getArgForPopulating().setLibraries(libraries);
    return this;
  }

  public AppleLibraryBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    getArgForPopulating().setDeps(deps);
    return this;
  }

  public AppleLibraryBuilder setExportedDeps(ImmutableSortedSet<BuildTarget> exportedDeps) {
    getArgForPopulating().setExportedDeps(exportedDeps);
    return this;
  }

  public AppleLibraryBuilder setHeaderPathPrefix(Optional<String> headerPathPrefix) {
    getArgForPopulating().setHeaderPathPrefix(headerPathPrefix);
    return this;
  }

  public AppleLibraryBuilder setPrefixHeader(Optional<SourcePath> prefixHeader) {
    getArgForPopulating().setPrefixHeader(prefixHeader);
    return this;
  }

  public AppleLibraryBuilder setTests(ImmutableSortedSet<BuildTarget> tests) {
    getArgForPopulating().setTests(tests);
    return this;
  }

  public AppleLibraryBuilder setBridgingHeader(Optional<SourcePath> bridgingHeader) {
    getArgForPopulating().setBridgingHeader(bridgingHeader);
    return this;
  }

  public AppleLibraryBuilder setPreferredLinkage(NativeLinkable.Linkage linkage) {
    getArgForPopulating().setPreferredLinkage(Optional.of(linkage));
    return this;
  }
}
