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

package com.facebook.buck.cxx;

import com.facebook.buck.graph.AbstractBreadthFirstTraversal;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * A helper class for building the included and excluded omnibus roots to pass to the omnibus
 * builder.
 */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractOmnibusRoots {

  /** @return the {@link NativeLinkTarget} roots that are included in omnibus linking. */
  abstract ImmutableMap<BuildTarget, NativeLinkTarget> getIncludedRoots();

  /** @return the {@link NativeLinkable} roots that are excluded from omnibus linking. */
  abstract ImmutableMap<BuildTarget, NativeLinkable> getExcludedRoots();

  public static Builder builder(CxxPlatform cxxPlatform, ImmutableSet<BuildTarget> excludes) {
    return new Builder(cxxPlatform, excludes);
  }

  public static class Builder {

    private final CxxPlatform cxxPlatform;
    private final ImmutableSet<BuildTarget> excludes;

    private final Map<BuildTarget, NativeLinkTarget> includedRoots = new LinkedHashMap<>();
    private final Map<BuildTarget, NativeLinkable> excludedRoots = new LinkedHashMap<>();

    private Builder(CxxPlatform cxxPlatform, ImmutableSet<BuildTarget> excludes) {
      this.cxxPlatform = cxxPlatform;
      this.excludes = excludes;
    }

    /** Add a root which is included in omnibus linking. */
    public void addIncludedRoot(NativeLinkTarget root) {
      includedRoots.put(root.getBuildTarget(), root);
    }

    /** Add a root which is excluded from omnibus linking. */
    public void addExcludedRoot(NativeLinkable root) {
      excludedRoots.put(root.getBuildTarget(), root);
    }

    /**
     * Add a node which may qualify as either an included root, and excluded root, or neither.
     *
     * @return whether the node was added as a root.
     */
    public void addPotentialRoot(NativeLinkable node) {
      Optional<NativeLinkTarget> target = NativeLinkables.getNativeLinkTarget(node, cxxPlatform);
      if (target.isPresent() && !excludes.contains(node.getBuildTarget())) {
        addIncludedRoot(target.get());
      } else {
        addExcludedRoot(node);
      }
    }

    private ImmutableMap<BuildTarget, NativeLinkable> buildExcluded() {
      final Map<BuildTarget, NativeLinkable> excluded = new LinkedHashMap<>();
      excluded.putAll(excludedRoots);

      // Find all excluded nodes reachable from the included roots.
      Map<BuildTarget, NativeLinkable> includedRootDeps = new LinkedHashMap<>();
      for (NativeLinkTarget target : includedRoots.values()) {
        for (NativeLinkable linkable : target.getNativeLinkTargetDeps(cxxPlatform)) {
          includedRootDeps.put(linkable.getBuildTarget(), linkable);
        }
      }
      new AbstractBreadthFirstTraversal<NativeLinkable>(includedRootDeps.values()) {
        @Override
        public Iterable<NativeLinkable> visit(NativeLinkable linkable) throws RuntimeException {
          if (linkable.getPreferredLinkage(cxxPlatform) == NativeLinkable.Linkage.SHARED) {
            excluded.put(linkable.getBuildTarget(), linkable);
            return ImmutableSet.of();
          }
          return Iterables.concat(
              linkable.getNativeLinkableDepsForPlatform(cxxPlatform),
              linkable.getNativeLinkableExportedDepsForPlatform(cxxPlatform));
        }
      }.start();

      // Prepare the final map of excluded roots, starting with the pre-defined ones.
      final Map<BuildTarget, NativeLinkable> updatedExcludedRoots = new LinkedHashMap<>();
      updatedExcludedRoots.putAll(excludedRoots);

      // Recursively expand the excluded nodes including any preloaded deps, as we'll need this full
      // list to know which roots to exclude from omnibus linking.
      new AbstractBreadthFirstTraversal<NativeLinkable>(excluded.values()) {
        @Override
        public Iterable<NativeLinkable> visit(NativeLinkable linkable) {
          if (includedRoots.containsKey(linkable.getBuildTarget())) {
            updatedExcludedRoots.put(linkable.getBuildTarget(), linkable);
          }
          return Iterables.concat(
              linkable.getNativeLinkableDepsForPlatform(cxxPlatform),
              linkable.getNativeLinkableExportedDepsForPlatform(cxxPlatform));
        }
      }.start();

      return ImmutableMap.copyOf(updatedExcludedRoots);
    }

    private ImmutableMap<BuildTarget, NativeLinkTarget> buildIncluded(
        ImmutableSet<BuildTarget> excluded) {
      return ImmutableMap.copyOf(
          Maps.filterKeys(includedRoots, Predicates.not(excluded::contains)));
    }

    public boolean isEmpty() {
      return includedRoots.isEmpty() && excludedRoots.isEmpty();
    }

    public OmnibusRoots build() {
      ImmutableMap<BuildTarget, NativeLinkable> excluded = buildExcluded();
      ImmutableMap<BuildTarget, NativeLinkTarget> included = buildIncluded(excluded.keySet());
      return OmnibusRoots.of(included, excluded);
    }
  }
}
