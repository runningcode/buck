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

package com.facebook.buck.model;

import com.facebook.buck.io.BuckPaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Set;

/** Static helpers for working with build targets. */
public class BuildTargets {

  /** Utility class: do not instantiate. */
  private BuildTargets() {}

  /**
   * Return a path to a file in the buck-out/bin/ directory. {@code format} will be prepended with
   * the {@link BuckPaths#getScratchDir()} and the target base path, then formatted with the target
   * short name.
   *
   * @param target The {@link BuildTarget} to scope this path to.
   * @param format {@link String#format} string for the path name. It should contain one "%s", which
   *     will be filled in with the rule's short name. It should not start with a slash.
   * @return A {@link java.nio.file.Path} under buck-out/bin, scoped to the base path of {@code
   *     target}.
   */
  public static Path getScratchPath(
      ProjectFilesystem filesystem, BuildTarget target, String format) {
    Preconditions.checkArgument(
        !format.startsWith("/"), "format string should not start with a slash");
    return filesystem
        .getBuckPaths()
        .getScratchDir()
        .resolve(target.getBasePath())
        .resolve(String.format(format, target.getShortNameAndFlavorPostfix()));
  }

  /**
   * Return a path to a file in the buck-out/annotation/ directory. {@code format} will be prepended
   * with the {@link BuckPaths#getAnnotationDir()} and the target base path, then formatted with the
   * target short name.
   *
   * @param target The {@link BuildTarget} to scope this path to.
   * @param format {@link String#format} string for the path name. It should contain one "%s", which
   *     will be filled in with the rule's short name. It should not start with a slash.
   * @return A {@link java.nio.file.Path} under buck-out/annotation, scoped to the base path of
   *     {@code target}.
   */
  public static Path getAnnotationPath(
      ProjectFilesystem filesystem, BuildTarget target, String format) {
    Preconditions.checkArgument(
        !format.startsWith("/"), "format string should not start with a slash");
    return filesystem
        .getBuckPaths()
        .getAnnotationDir()
        .resolve(target.getBasePath())
        .resolve(String.format(format, target.getShortNameAndFlavorPostfix()));
  }

  /**
   * Return a relative path to a file in the buck-out/gen/ directory. {@code format} will be
   * prepended with the {@link BuckPaths#getGenDir()} and the target base path, then formatted with
   * the target short name.
   *
   * @param target The {@link BuildTarget} to scope this path to.
   * @param format {@link String#format} string for the path name. It should contain one "%s", which
   *     will be filled in with the rule's short name. It should not start with a slash.
   * @return A {@link java.nio.file.Path} under buck-out/gen, scoped to the base path of {@code
   *     target}.
   */
  public static Path getGenPath(ProjectFilesystem filesystem, BuildTarget target, String format) {
    Preconditions.checkArgument(
        !format.startsWith("/"), "format string should not start with a slash");
    return filesystem
        .getBuckPaths()
        .getGenDir()
        .resolve(target.getBasePath())
        .resolve(String.format(format, target.getShortNameAndFlavorPostfix()));
  }

  /**
   * Takes the {@link BuildTarget} for {@code hasBuildTarget} and derives a new {@link BuildTarget}
   * from it with the specified flavor.
   *
   * @throws IllegalArgumentException if the original {@link BuildTarget} already has a flavor.
   */
  public static BuildTarget createFlavoredBuildTarget(
      UnflavoredBuildTarget buildTarget, Flavor flavor) {
    return BuildTarget.builder(buildTarget).addFlavors(flavor).build();
  }

  public static Predicate<BuildTarget> containsFlavors(final FlavorDomain<?> domain) {
    return input -> {
      ImmutableSet<Flavor> flavorSet =
          Sets.intersection(domain.getFlavors(), input.getFlavors()).immutableCopy();
      return !flavorSet.isEmpty();
    };
  }

  public static Predicate<BuildTarget> containsFlavor(final Flavor flavor) {
    return input -> input.getFlavors().contains(flavor);
  }

  /**
   * Propagate flavors represented by the given {@link FlavorDomain} objects from a parent target to
   * its dependencies.
   */
  public static ImmutableSortedSet<BuildTarget> propagateFlavorDomains(
      BuildTarget target, Iterable<FlavorDomain<?>> domains, Iterable<BuildTarget> deps) {

    Set<Flavor> flavors = Sets.newHashSet();

    // For each flavor domain, extract the corresponding flavor from the parent target and
    // verify that each dependency hasn't already set this flavor.
    for (FlavorDomain<?> domain : domains) {

      // Now extract all relevant domain flavors from our parent target.
      ImmutableSet<Flavor> flavorSet =
          Sets.intersection(domain.getFlavors(), target.getFlavors()).immutableCopy();

      if (flavorSet.isEmpty()) {
        throw new HumanReadableException("%s: no flavor for \"%s\"", target, domain.getName());
      }
      flavors.addAll(flavorSet);

      // First verify that our deps are not already flavored for our given domains.
      for (BuildTarget dep : deps) {
        if (domain.getFlavor(dep).isPresent()) {
          throw new HumanReadableException(
              "%s: dep %s already has flavor for \"%s\" : %s",
              target, dep, domain.getName(), flavorSet.toString());
        }
      }
    }

    ImmutableSortedSet.Builder<BuildTarget> flavoredDeps = ImmutableSortedSet.naturalOrder();

    // Now flavor each dependency with the relevant flavors.
    for (BuildTarget dep : deps) {
      flavoredDeps.add(BuildTarget.builder(dep).addAllFlavors(flavors).build());
    }

    return flavoredDeps.build();
  }

  /**
   * Propagate a build target's flavors in a certain domain to a list of other build targets.
   *
   * @param domain the flavor domain to be propagated.
   * @param buildTarget the build target containing the flavors to be propagated
   * @param deps list of BuildTargets to propagate the flavors to. If a target already contains one
   *     or more flavors in domain, it is left unchanged.
   * @return the list of BuildTargets with any flavors propagated.
   */
  public static FluentIterable<BuildTarget> propagateFlavorsInDomainIfNotPresent(
      FlavorDomain<?> domain, BuildTarget buildTarget, FluentIterable<BuildTarget> deps) {
    if (domain.containsAnyOf(buildTarget.getFlavors())) {
      FluentIterable<BuildTarget> targetsWithFlavorsAlready =
          deps.filter(BuildTargets.containsFlavors(domain));

      FluentIterable<BuildTarget> targetsWithoutFlavors =
          deps.filter(Predicates.not(BuildTargets.containsFlavors(domain)));

      deps =
          targetsWithFlavorsAlready.append(
              BuildTargets.propagateFlavorDomains(
                  buildTarget, ImmutableSet.of(domain), targetsWithoutFlavors));
    }

    return deps;
  }
}
