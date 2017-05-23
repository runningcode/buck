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
package com.facebook.buck.apple;

import com.facebook.buck.cxx.CxxFlags;
import com.facebook.buck.cxx.FrameworkDependencies;
import com.facebook.buck.cxx.HasSystemFrameworkAndLibraries;
import com.facebook.buck.cxx.NativeLinkable;
import com.facebook.buck.cxx.StripStyle;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.HasDeclaredDeps;
import com.facebook.buck.rules.MetadataProvidingDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.util.RichStream;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.versions.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.regex.Pattern;
import org.immutables.value.Value;

public class PrebuiltAppleFrameworkDescription
    implements Description<PrebuiltAppleFrameworkDescriptionArg>,
        Flavored,
        MetadataProvidingDescription<PrebuiltAppleFrameworkDescriptionArg> {

  private final FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain;

  public PrebuiltAppleFrameworkDescription(
      FlavorDomain<AppleCxxPlatform> appleCxxPlatformsFlavorDomain) {
    this.appleCxxPlatformsFlavorDomain = appleCxxPlatformsFlavorDomain;
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    // This class supports flavors that other apple targets support.
    // It's mainly there to be compatible with other apple rules which blindly add flavor tags to
    // all its targets.
    return RichStream.from(flavors)
            .filter(flavor -> !appleCxxPlatformsFlavorDomain.getFlavors().contains(flavor))
            .filter(flavor -> !AppleDebugFormat.FLAVOR_DOMAIN.getFlavors().contains(flavor))
            .filter(flavor -> !AppleDescriptions.INCLUDE_FRAMEWORKS.getFlavors().contains(flavor))
            .filter(flavor -> !StripStyle.FLAVOR_DOMAIN.getFlavors().contains(flavor))
            .count()
        == 0;
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    return Optional.of(
        ImmutableSet.of(
            appleCxxPlatformsFlavorDomain,
            AppleDebugFormat.FLAVOR_DOMAIN,
            AppleDescriptions.INCLUDE_FRAMEWORKS,
            StripStyle.FLAVOR_DOMAIN));
  }

  @Override
  public Class<PrebuiltAppleFrameworkDescriptionArg> getConstructorArgType() {
    return PrebuiltAppleFrameworkDescriptionArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      final BuildRuleParams params,
      final BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      final PrebuiltAppleFrameworkDescriptionArg args)
      throws NoSuchBuildTargetException {
    return new PrebuiltAppleFramework(
        params,
        resolver,
        new SourcePathResolver(new SourcePathRuleFinder(resolver)),
        args.getFramework(),
        args.getPreferredLinkage(),
        args.getFrameworks(),
        args.getSupportedPlatformsRegex(),
        input ->
            CxxFlags.getFlagsWithPlatformMacroExpansion(
                args.getExportedLinkerFlags(), args.getExportedPlatformLinkerFlags(), input));
  }

  @Override
  public <U> Optional<U> createMetadata(
      BuildTarget buildTarget,
      BuildRuleResolver resolver,
      PrebuiltAppleFrameworkDescriptionArg args,
      Optional<ImmutableMap<BuildTarget, Version>> selectedVersions,
      Class<U> metadataClass)
      throws NoSuchBuildTargetException {
    if (metadataClass.isAssignableFrom(FrameworkDependencies.class)) {
      BuildRule buildRule = resolver.requireRule(buildTarget);
      ImmutableSet<SourcePath> sourcePaths = ImmutableSet.of(buildRule.getSourcePathToOutput());
      return Optional.of(metadataClass.cast(FrameworkDependencies.of(sourcePaths)));
    }
    return Optional.empty();
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractPrebuiltAppleFrameworkDescriptionArg
      extends CommonDescriptionArg, HasDeclaredDeps, HasSystemFrameworkAndLibraries {
    SourcePath getFramework();

    Optional<Pattern> getSupportedPlatformsRegex();

    ImmutableList<String> getExportedLinkerFlags();

    @Value.Default
    default PatternMatchedCollection<ImmutableList<String>> getExportedPlatformLinkerFlags() {
      return PatternMatchedCollection.of();
    }

    NativeLinkable.Linkage getPreferredLinkage();
  }
}
