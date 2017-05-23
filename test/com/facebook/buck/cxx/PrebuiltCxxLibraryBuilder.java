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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.coercer.VersionMatchedCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import java.util.regex.Pattern;

public class PrebuiltCxxLibraryBuilder
    extends AbstractNodeBuilder<
        PrebuiltCxxLibraryDescriptionArg.Builder, PrebuiltCxxLibraryDescriptionArg,
        PrebuiltCxxLibraryDescription, BuildRule> {

  public PrebuiltCxxLibraryBuilder(BuildTarget target, FlavorDomain<CxxPlatform> cxxPlatforms) {
    super(new PrebuiltCxxLibraryDescription(CxxPlatformUtils.DEFAULT_CONFIG, cxxPlatforms), target);
  }

  public PrebuiltCxxLibraryBuilder(BuildTarget target) {
    this(target, CxxTestUtils.createDefaultPlatforms());
  }

  public PrebuiltCxxLibraryBuilder setIncludeDirs(ImmutableList<String> includeDirs) {
    getArgForPopulating().setIncludeDirs(includeDirs);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setLibName(String libName) {
    getArgForPopulating().setLibName(Optional.of(libName));
    return this;
  }

  public PrebuiltCxxLibraryBuilder setLibDir(String libDir) {
    getArgForPopulating().setLibDir(Optional.of(libDir));
    return this;
  }

  public PrebuiltCxxLibraryBuilder setLinkWithoutSoname(boolean linkWithoutSoname) {
    getArgForPopulating().setLinkWithoutSoname(linkWithoutSoname);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedHeaders(SourceList exportedHeaders) {
    getArgForPopulating().setExportedHeaders(exportedHeaders);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedPlatformHeaders(
      PatternMatchedCollection<SourceList> collection) {
    getArgForPopulating().setExportedPlatformHeaders(collection);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setHeaderNamespace(String headerNamespace) {
    getArgForPopulating().setHeaderNamespace(Optional.of(headerNamespace));
    return this;
  }

  public PrebuiltCxxLibraryBuilder setHeaderOnly(boolean headerOnly) {
    getArgForPopulating().setHeaderOnly(Optional.of(headerOnly));
    return this;
  }

  public PrebuiltCxxLibraryBuilder setProvided(boolean provided) {
    getArgForPopulating().setProvided(provided);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedLinkerFlags(ImmutableList<String> linkerFlags) {
    getArgForPopulating().setExportedLinkerFlags(linkerFlags);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setSoname(String soname) {
    getArgForPopulating().setSoname(Optional.of(soname));
    return this;
  }

  public PrebuiltCxxLibraryBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    getArgForPopulating().setDeps(deps);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setForceStatic(boolean forceStatic) {
    getArgForPopulating().setForceStatic(Optional.of(forceStatic));
    return this;
  }

  public PrebuiltCxxLibraryBuilder setExportedDeps(ImmutableSortedSet<BuildTarget> exportedDeps) {
    getArgForPopulating().setExportedDeps(exportedDeps);
    return this;
  }

  public PrebuiltCxxLibraryBuilder setSupportedPlatformsRegex(Pattern supportedPlatformsRegex) {
    getArgForPopulating().setSupportedPlatformsRegex(Optional.of(supportedPlatformsRegex));
    return this;
  }

  public PrebuiltCxxLibraryBuilder setVersionedSubDir(
      VersionMatchedCollection<String> versionedSubDir) {
    getArgForPopulating().setVersionedSubDir(Optional.of(versionedSubDir));
    return this;
  }
}
