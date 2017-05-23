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

import com.facebook.buck.cxx.CxxCompilationDatabase;
import com.facebook.buck.cxx.CxxInferEnhancer;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;

public class MultiarchFileInfos {

  // Utility class, do not instantiate.
  private MultiarchFileInfos() {}

  /**
   * Inspect the given build target and return information about it if its a fat binary.
   *
   * @return non-empty when the target represents a fat binary.
   * @throws com.facebook.buck.util.HumanReadableException when the target is a fat binary but has
   *     incompatible flavors.
   */
  public static Optional<MultiarchFileInfo> create(
      final FlavorDomain<AppleCxxPlatform> appleCxxPlatforms, BuildTarget target) {
    ImmutableList<ImmutableSortedSet<Flavor>> thinFlavorSets =
        generateThinFlavors(appleCxxPlatforms.getFlavors(), target.getFlavors());
    if (thinFlavorSets.size() <= 1) { // Actually a thin binary
      return Optional.empty();
    }

    if (!Sets.intersection(target.getFlavors(), FORBIDDEN_BUILD_ACTIONS).isEmpty()) {
      throw new HumanReadableException(
          "%s: Fat binaries is only supported when building an actual binary.", target);
    }

    AppleCxxPlatform representativePlatform = null;
    AppleSdk sdk = null;
    for (SortedSet<Flavor> flavorSet : thinFlavorSets) {
      AppleCxxPlatform platform =
          Preconditions.checkNotNull(appleCxxPlatforms.getValue(flavorSet).orElse(null));
      if (sdk == null) {
        sdk = platform.getAppleSdk();
        representativePlatform = platform;
      } else if (sdk != platform.getAppleSdk()) {
        throw new HumanReadableException(
            "%s: Fat binaries can only be generated from binaries compiled for the same SDK.",
            target);
      }
    }

    MultiarchFileInfo.Builder builder =
        MultiarchFileInfo.builder()
            .setFatTarget(target)
            .setRepresentativePlatform(Preconditions.checkNotNull(representativePlatform));

    BuildTarget platformFreeTarget = target.withoutFlavors(appleCxxPlatforms.getFlavors());
    for (SortedSet<Flavor> flavorSet : thinFlavorSets) {
      builder.addThinTargets(platformFreeTarget.withFlavors(flavorSet));
    }

    return Optional.of(builder.build());
  }

  /**
   * Expand flavors representing a fat binary into its thin binary equivalents.
   *
   * <p>Useful when dealing with functions unaware of fat binaries.
   *
   * <p>This does not actually check that the particular flavor set is valid.
   */
  public static ImmutableList<ImmutableSortedSet<Flavor>> generateThinFlavors(
      Set<Flavor> platformFlavors, SortedSet<Flavor> flavors) {
    Set<Flavor> platformFreeFlavors = Sets.difference(flavors, platformFlavors);
    ImmutableList.Builder<ImmutableSortedSet<Flavor>> thinTargetsBuilder = ImmutableList.builder();
    for (Flavor flavor : flavors) {
      if (platformFlavors.contains(flavor)) {
        thinTargetsBuilder.add(
            ImmutableSortedSet.<Flavor>naturalOrder()
                .addAll(platformFreeFlavors)
                .add(flavor)
                .build());
      }
    }
    return thinTargetsBuilder.build();
  }

  /**
   * Generate a fat rule from thin rules.
   *
   * <p>Invariant: thinRules contain all the thin rules listed in info.getThinTargets().
   */
  public static BuildRule requireMultiarchRule(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      MultiarchFileInfo info,
      ImmutableSortedSet<BuildRule> thinRules) {
    Optional<BuildRule> existingRule = resolver.getRuleOptional(info.getFatTarget());
    if (existingRule.isPresent()) {
      return existingRule.get();
    }

    for (BuildRule rule : thinRules) {
      if (rule.getSourcePathToOutput() == null) {
        throw new HumanReadableException("%s: no output so it cannot be a multiarch input", rule);
      }
    }

    ImmutableSortedSet<SourcePath> inputs =
        FluentIterable.from(thinRules)
            .transform(BuildRule::getSourcePathToOutput)
            .toSortedSet(Ordering.natural());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    MultiarchFile multiarchFile =
        new MultiarchFile(
            params.copyReplacingDeclaredAndExtraDeps(
                Suppliers.ofInstance(ImmutableSortedSet.of()), Suppliers.ofInstance(thinRules)),
            ruleFinder,
            info.getRepresentativePlatform().getLipo(),
            inputs,
            BuildTargets.getGenPath(params.getProjectFilesystem(), params.getBuildTarget(), "%s"));
    resolver.addToIndex(multiarchFile);
    return multiarchFile;
  }

  private static final ImmutableSet<Flavor> FORBIDDEN_BUILD_ACTIONS =
      ImmutableSet.<Flavor>builder()
          .addAll(CxxInferEnhancer.InferFlavors.getAll())
          .add(CxxCompilationDatabase.COMPILATION_DATABASE)
          .build();
}
