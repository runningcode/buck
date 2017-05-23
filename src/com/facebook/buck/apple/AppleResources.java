/*
 * Copyright 2015-present Facebook, Inc.
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

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.function.Predicate;

public class AppleResources {

  @VisibleForTesting
  public static final Predicate<TargetNode<?, ?>> IS_APPLE_BUNDLE_RESOURCE_NODE =
      node -> node.getDescription() instanceof HasAppleBundleResourcesDescription;

  // Utility class, do not instantiate.
  private AppleResources() {}

  /**
   * Collect resources from recursive dependencies.
   *
   * @param targetGraph The {@link TargetGraph} containing the node and its dependencies.
   * @param targetNodes {@link TargetNode} at the tip of the traversal.
   * @return The recursive resource buildables.
   */
  public static ImmutableSet<AppleResourceDescriptionArg> collectRecursiveResources(
      final TargetGraph targetGraph,
      final Optional<AppleDependenciesCache> cache,
      Iterable<? extends TargetNode<?, ?>> targetNodes) {
    return FluentIterable.from(targetNodes)
        .transformAndConcat(
            node ->
                AppleBuildRules.getRecursiveTargetNodeDependenciesOfTypes(
                    targetGraph,
                    cache,
                    AppleBuildRules.RecursiveDependenciesMode.COPYING,
                    node,
                    ImmutableSet.of(AppleResourceDescription.class)))
        .transform(input -> (AppleResourceDescriptionArg) input.getConstructorArg())
        .toSet();
  }

  public static <T> AppleBundleResources collectResourceDirsAndFiles(
      final TargetGraph targetGraph,
      BuildRuleResolver resolver,
      final Optional<AppleDependenciesCache> cache,
      TargetNode<T, ?> targetNode) {
    AppleBundleResources.Builder builder = AppleBundleResources.builder();

    Iterable<TargetNode<?, ?>> resourceNodes =
        AppleBuildRules.getRecursiveTargetNodeDependenciesOfTypes(
            targetGraph,
            cache,
            AppleBuildRules.RecursiveDependenciesMode.COPYING,
            targetNode,
            IS_APPLE_BUNDLE_RESOURCE_NODE);
    ProjectFilesystem filesystem = targetNode.getFilesystem();

    for (TargetNode<?, ?> resourceNode : resourceNodes) {
      @SuppressWarnings("unchecked")
      TargetNode<Object, ?> node = (TargetNode<Object, ?>) resourceNode;

      @SuppressWarnings("unchecked")
      HasAppleBundleResourcesDescription<Object> description =
          (HasAppleBundleResourcesDescription<Object>) node.getDescription();

      description.addAppleBundleResources(builder, node, filesystem, resolver);
    }
    return builder.build();
  }

  public static ImmutableSet<AppleResourceDescriptionArg> collectDirectResources(
      TargetGraph targetGraph, TargetNode<?, ?> targetNode) {
    ImmutableSet.Builder<AppleResourceDescriptionArg> builder = ImmutableSet.builder();
    Iterable<TargetNode<?, ?>> deps = targetGraph.getAll(targetNode.getBuildDeps());
    for (TargetNode<?, ?> node : deps) {
      if (node.getDescription() instanceof AppleResourceDescription) {
        builder.add((AppleResourceDescriptionArg) node.getConstructorArg());
      }
    }
    return builder.build();
  }
}
