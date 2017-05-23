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

import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.graph.AcyclicDepthFirstPostOrderTraversal;
import com.facebook.buck.graph.GraphTraversable;
import com.facebook.buck.halide.HalideLibraryDescription;
import com.facebook.buck.log.Logger;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.swift.SwiftLibraryDescription;
import com.facebook.buck.util.RichStream;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;

/** Helpers for reading properties of Apple target build rules. */
public final class AppleBuildRules {

  private static final Logger LOG = Logger.get(AppleBuildRules.class);

  // Utility class not to be instantiated.
  private AppleBuildRules() {}

  public static final ImmutableSet<Class<? extends Description<?>>>
      XCODE_TARGET_DESCRIPTION_CLASSES =
          ImmutableSet.of(
              AppleLibraryDescription.class,
              CxxLibraryDescription.class,
              AppleBinaryDescription.class,
              AppleBundleDescription.class,
              AppleTestDescription.class,
              HalideLibraryDescription.class);

  private static final ImmutableSet<Class<? extends BuildRule>> XCODE_TARGET_BUILD_RULE_TEST_TYPES =
      ImmutableSet.of(AppleTest.class);

  private static final ImmutableSet<Class<? extends Description<?>>>
      RECURSIVE_DEPENDENCIES_STOP_AT_DESCRIPTION_CLASSES =
          ImmutableSet.of(AppleBundleDescription.class, AppleResourceDescription.class);

  private static final ImmutableSet<AppleBundleExtension> XCODE_TARGET_TEST_BUNDLE_EXTENSIONS =
      ImmutableSet.of(AppleBundleExtension.XCTEST);

  private static final ImmutableSet<Class<? extends Description<?>>>
      WRAPPER_RESOURCE_DESCRIPTION_CLASSES =
          ImmutableSet.of(CoreDataModelDescription.class, SceneKitAssetsDescription.class);

  private static final ImmutableSet<Class<? extends Description<?>>>
      APPLE_ASSET_CATALOG_DESCRIPTION_CLASSES = ImmutableSet.of(AppleAssetCatalogDescription.class);

  public static final ImmutableSet<Class<? extends Description<?>>>
      CORE_DATA_MODEL_DESCRIPTION_CLASSES = ImmutableSet.of(CoreDataModelDescription.class);

  public static final ImmutableSet<Class<? extends Description<?>>>
      SCENEKIT_ASSETS_DESCRIPTION_CLASSES = ImmutableSet.of(SceneKitAssetsDescription.class);

  /** Whether the build rule type is equivalent to some kind of Xcode target. */
  public static boolean isXcodeTargetDescription(Description<?> description) {
    return XCODE_TARGET_DESCRIPTION_CLASSES.contains(description.getClass());
  }

  /** Whether the build rule type is a test target. */
  public static boolean isXcodeTargetTestBuildRule(BuildRule rule) {
    return XCODE_TARGET_BUILD_RULE_TEST_TYPES.contains(rule.getClass());
  }

  /** Whether the bundle extension is a test bundle extension. */
  public static boolean isXcodeTargetTestBundleExtension(AppleBundleExtension extension) {
    return XCODE_TARGET_TEST_BUNDLE_EXTENSIONS.contains(extension);
  }

  public static String getOutputFileNameFormatForLibrary(boolean isSharedLibrary) {
    if (isSharedLibrary) {
      return "lib%s.dylib";
    } else {
      return "lib%s.a";
    }
  }

  public enum RecursiveDependenciesMode {
    /** Will traverse all rules that are built. */
    BUILDING,
    /**
     * Will also not traverse the dependencies of bundles, as those are copied inside the bundle.
     */
    COPYING,
    /** Will also not traverse the dependencies of shared libraries, as those are linked already. */
    LINKING,
  }

  public static ImmutableSet<TargetNode<?, ?>> getRecursiveTargetNodeDependenciesOfTypes(
      final TargetGraph targetGraph,
      final Optional<AppleDependenciesCache> cache,
      final RecursiveDependenciesMode mode,
      final TargetNode<?, ?> targetNode,
      final Optional<ImmutableSet<Class<? extends Description<?>>>> descriptionClasses) {
    LOG.verbose(
        "Getting recursive dependencies of node %s, mode %s, including only types %s\n",
        targetNode, mode, descriptionClasses);
    Predicate<TargetNode<?, ?>> isDependencyNode =
        descriptionClasses
            .map(
                classes ->
                    (Predicate<TargetNode<?, ?>>)
                        node -> classes.contains(node.getDescription().getClass()))
            .orElse(x -> true);

    ImmutableSet<TargetNode<?, ?>> result =
        getRecursiveTargetNodeDependenciesOfTypes(
            targetGraph, cache, mode, targetNode, isDependencyNode);

    LOG.verbose(
        "Got recursive dependencies of node %s mode %s types %s: %s\n",
        targetNode, mode, descriptionClasses, result);

    return result;
  }

  public static ImmutableSet<TargetNode<?, ?>> getRecursiveTargetNodeDependenciesOfTypes(
      TargetGraph targetGraph,
      Optional<AppleDependenciesCache> cache,
      RecursiveDependenciesMode mode,
      TargetNode<?, ?> targetNode,
      Predicate<TargetNode<?, ?>> isDependencyNode) {

    @SuppressWarnings("unchecked")
    GraphTraversable<TargetNode<?, ?>> graphTraversable =
        node -> {
          if (!isXcodeTargetDescription(node.getDescription())
              || SwiftLibraryDescription.isSwiftTarget(node.getBuildTarget())) {
            return Collections.emptyIterator();
          }

          LOG.verbose("Finding children of node: %s", node);

          ImmutableSortedSet<TargetNode<?, ?>> defaultDeps;
          ImmutableSortedSet<TargetNode<?, ?>> exportedDeps;
          if (!cache.isPresent()) {
            ImmutableSortedSet.Builder<TargetNode<?, ?>> defaultDepsBuilder =
                ImmutableSortedSet.naturalOrder();
            ImmutableSortedSet.Builder<TargetNode<?, ?>> exportedDepsBuilder =
                ImmutableSortedSet.naturalOrder();
            addDirectAndExportedDeps(targetGraph, node, defaultDepsBuilder, exportedDepsBuilder);
            defaultDeps = defaultDepsBuilder.build();
            exportedDeps = exportedDepsBuilder.build();
          } else {
            defaultDeps = cache.get().getDefaultDeps(node);
            exportedDeps = cache.get().getExportedDeps(node);
          }

          if (node.getDescription() instanceof AppleBundleDescription) {
            AppleBundleDescriptionArg arg = (AppleBundleDescriptionArg) node.getConstructorArg();

            ImmutableSortedSet.Builder<TargetNode<?, ?>> editedDeps =
                ImmutableSortedSet.naturalOrder();
            ImmutableSortedSet.Builder<TargetNode<?, ?>> editedExportedDeps =
                ImmutableSortedSet.naturalOrder();
            for (TargetNode<?, ?> rule : defaultDeps) {
              if (!rule.getBuildTarget().equals(arg.getBinary())) {
                editedDeps.add(rule);
              } else {
                addDirectAndExportedDeps(
                    targetGraph, targetGraph.get(arg.getBinary()), editedDeps, editedExportedDeps);
              }
            }

            ImmutableSortedSet<TargetNode<?, ?>> newDefaultDeps = editedDeps.build();
            ImmutableSortedSet<TargetNode<?, ?>> newExportedDeps = editedExportedDeps.build();
            LOG.verbose(
                "Transformed deps for bundle %s: %s -> %s, exported deps %s -> %s",
                node, defaultDeps, newDefaultDeps, exportedDeps, newExportedDeps);
            defaultDeps = newDefaultDeps;
            exportedDeps = newExportedDeps;
          }

          LOG.verbose("Default deps for node %s mode %s: %s", node, mode, defaultDeps);
          if (!exportedDeps.isEmpty()) {
            LOG.verbose("Exported deps for node %s mode %s: %s", node, mode, exportedDeps);
          }

          ImmutableSortedSet<TargetNode<?, ?>> deps = ImmutableSortedSet.of();

          if (node != targetNode) {
            switch (mode) {
              case LINKING:
              case COPYING:
                boolean nodeIsAppleLibrary =
                    node.getDescription() instanceof AppleLibraryDescription;
                boolean nodeIsCxxLibrary = node.getDescription() instanceof CxxLibraryDescription;
                if (nodeIsAppleLibrary || nodeIsCxxLibrary) {
                  if (AppleLibraryDescription.isNotStaticallyLinkedLibraryNode(
                      (TargetNode<CxxLibraryDescription.CommonArg, ?>) node)) {
                    deps = exportedDeps;
                  } else {
                    deps = defaultDeps;
                  }
                } else if (RECURSIVE_DEPENDENCIES_STOP_AT_DESCRIPTION_CLASSES.contains(
                    node.getDescription().getClass())) {
                  deps = exportedDeps;
                } else {
                  deps = defaultDeps;
                }
                break;
              case BUILDING:
                deps = defaultDeps;
                break;
            }
          } else {
            deps = defaultDeps;
          }

          LOG.verbose("Walking children of node %s: %s", node, deps);
          return deps.iterator();
        };

    final ImmutableSet.Builder<TargetNode<?, ?>> filteredRules = ImmutableSet.builder();
    AcyclicDepthFirstPostOrderTraversal<TargetNode<?, ?>> traversal =
        new AcyclicDepthFirstPostOrderTraversal<>(graphTraversable);
    try {
      for (TargetNode<?, ?> node : traversal.traverse(ImmutableList.of(targetNode))) {
        if (node != targetNode && isDependencyNode.test(node)) {
          filteredRules.add(node);
        }
      }
    } catch (AcyclicDepthFirstPostOrderTraversal.CycleException e) {
      // actual load failures and cycle exceptions should have been caught at an earlier stage
      throw new RuntimeException(e);
    }

    return filteredRules.build();
  }

  public static ImmutableSet<TargetNode<?, ?>> getRecursiveTargetNodeDependenciesOfTypes(
      TargetGraph targetGraph,
      Optional<AppleDependenciesCache> cache,
      RecursiveDependenciesMode mode,
      TargetNode<?, ?> input,
      ImmutableSet<Class<? extends Description<?>>> descriptionClasses) {
    return getRecursiveTargetNodeDependenciesOfTypes(
        targetGraph, cache, mode, input, Optional.of(descriptionClasses));
  }

  static void addDirectAndExportedDeps(
      TargetGraph targetGraph,
      TargetNode<?, ?> targetNode,
      ImmutableSortedSet.Builder<TargetNode<?, ?>> directDepsBuilder,
      ImmutableSortedSet.Builder<TargetNode<?, ?>> exportedDepsBuilder) {
    directDepsBuilder.addAll(targetGraph.getAll(targetNode.getBuildDepsStream()::iterator));
    if (targetNode.getDescription() instanceof AppleLibraryDescription
        || targetNode.getDescription() instanceof CxxLibraryDescription) {
      CxxLibraryDescription.CommonArg arg =
          (CxxLibraryDescription.CommonArg) targetNode.getConstructorArg();
      LOG.verbose("Exported deps of node %s: %s", targetNode, arg.getExportedDeps());
      Iterable<TargetNode<?, ?>> exportedNodes = targetGraph.getAll(arg.getExportedDeps());
      directDepsBuilder.addAll(exportedNodes);
      exportedDepsBuilder.addAll(exportedNodes);
    }
  }

  public static ImmutableSet<TargetNode<?, ?>> getSchemeBuildableTargetNodes(
      TargetGraph targetGraph,
      Optional<AppleDependenciesCache> cache,
      TargetNode<?, ?> targetNode) {
    Iterable<TargetNode<?, ?>> targetNodes =
        Iterables.concat(
            getRecursiveTargetNodeDependenciesOfTypes(
                targetGraph,
                cache,
                RecursiveDependenciesMode.BUILDING,
                targetNode,
                Optional.empty()),
            ImmutableSet.of(targetNode));

    return ImmutableSet.copyOf(
        Iterables.filter(targetNodes, input -> isXcodeTargetDescription(input.getDescription())));
  }

  public static <T> ImmutableSet<AppleAssetCatalogDescriptionArg> collectRecursiveAssetCatalogs(
      TargetGraph targetGraph,
      Optional<AppleDependenciesCache> cache,
      Iterable<TargetNode<T, ?>> targetNodes) {
    return FluentIterable.from(targetNodes)
        .transformAndConcat(
            input ->
                getRecursiveTargetNodeDependenciesOfTypes(
                    targetGraph,
                    cache,
                    RecursiveDependenciesMode.COPYING,
                    input,
                    APPLE_ASSET_CATALOG_DESCRIPTION_CLASSES))
        .transform(input -> (AppleAssetCatalogDescriptionArg) input.getConstructorArg())
        .toSet();
  }

  public static <T> ImmutableSet<AppleWrapperResourceArg> collectRecursiveWrapperResources(
      TargetGraph targetGraph,
      Optional<AppleDependenciesCache> cache,
      Iterable<TargetNode<T, ?>> targetNodes) {
    return FluentIterable.from(targetNodes)
        .transformAndConcat(
            input ->
                getRecursiveTargetNodeDependenciesOfTypes(
                    targetGraph,
                    cache,
                    RecursiveDependenciesMode.COPYING,
                    input,
                    WRAPPER_RESOURCE_DESCRIPTION_CLASSES))
        .transform(input -> (AppleWrapperResourceArg) input.getConstructorArg())
        .toSet();
  }

  @SuppressWarnings("unchecked")
  public static <T> ImmutableSet<T> collectTransitiveBuildRules(
      TargetGraph targetGraph,
      Optional<AppleDependenciesCache> cache,
      ImmutableSet<Class<? extends Description<?>>> descriptionClasses,
      Collection<TargetNode<?, ?>> targetNodes) {
    return RichStream.from(targetNodes)
        .flatMap(
            targetNode ->
                getRecursiveTargetNodeDependenciesOfTypes(
                        targetGraph,
                        cache,
                        RecursiveDependenciesMode.COPYING,
                        targetNode,
                        descriptionClasses)
                    .stream())
        .map(input -> (T) input.getConstructorArg())
        .toImmutableSet();
  }

  public static ImmutableSet<AppleAssetCatalogDescriptionArg> collectDirectAssetCatalogs(
      TargetGraph targetGraph, TargetNode<?, ?> targetNode) {
    ImmutableSet.Builder<AppleAssetCatalogDescriptionArg> builder = ImmutableSet.builder();
    Iterable<TargetNode<?, ?>> deps = targetGraph.getAll(targetNode.getBuildDeps());
    for (TargetNode<?, ?> node : deps) {
      if (node.getDescription() instanceof AppleAssetCatalogDescription) {
        builder.add((AppleAssetCatalogDescriptionArg) node.getConstructorArg());
      }
    }
    return builder.build();
  }
}
