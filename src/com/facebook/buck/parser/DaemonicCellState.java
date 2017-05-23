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

package com.facebook.buck.parser;

import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetException;
import com.facebook.buck.model.UnflavoredBuildTarget;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.util.concurrent.AutoCloseableLock;
import com.facebook.buck.util.concurrent.AutoCloseableReadWriteUpdateLock;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.concurrent.GuardedBy;

class DaemonicCellState {

  private static final Logger LOG = Logger.get(DaemonicCellState.class);

  private class CacheImpl<T> implements PipelineNodeCache.Cache<BuildTarget, T> {

    @GuardedBy("rawAndComputedNodesLock")
    public final ConcurrentMapCache<BuildTarget, T> allComputedNodes =
        new ConcurrentMapCache<>(parsingThreads);

    @Override
    public Optional<T> lookupComputedNode(Cell cell, BuildTarget target)
        throws BuildTargetException {
      try (AutoCloseableLock readLock = rawAndComputedNodesLock.readLock()) {
        return Optional.ofNullable(allComputedNodes.getIfPresent(target));
      }
    }

    @Override
    public T putComputedNodeIfNotPresent(Cell cell, BuildTarget target, T targetNode)
        throws BuildTargetException {
      try (AutoCloseableLock writeLock = rawAndComputedNodesLock.writeLock()) {
        T updatedNode = allComputedNodes.putIfAbsentAndGet(target, targetNode);
        Preconditions.checkState(
            allRawNodeTargets.contains(target.getUnflavoredBuildTarget()),
            "Added %s to computed nodes, which isn't present in raw nodes",
            target);
        if (updatedNode.equals(targetNode)) {
          targetsCornucopia.put(target.getUnflavoredBuildTarget(), target);
        }
        return updatedNode;
      }
    }
  }

  private final Path cellRoot;
  private final Optional<String> cellCanonicalName;
  private AtomicReference<Cell> cell;

  @GuardedBy("rawAndComputedNodesLock")
  private final SetMultimap<Path, Path> buildFileDependents;

  @GuardedBy("rawAndComputedNodesLock")
  private final SetMultimap<UnflavoredBuildTarget, BuildTarget> targetsCornucopia;

  @GuardedBy("rawAndComputedNodesLock")
  private final Map<Path, ImmutableMap<String, Optional<String>>> buildFileEnv;

  @GuardedBy("rawAndComputedNodesLock")
  private final ConcurrentMapCache<Path, ImmutableSet<Map<String, Object>>> allRawNodes;
  // Tracks all targets in `allRawNodes`.  Used to verify that every target in `allComputedNodes`
  // is also in `allRawNodes`, as we use the latter for bookkeeping invalidations.
  @GuardedBy("rawAndComputedNodesLock")
  private final Set<UnflavoredBuildTarget> allRawNodeTargets;

  @GuardedBy("rawAndComputedNodesLock")
  private final ConcurrentMap<Class<?>, CacheImpl<?>> typedNodeCaches;

  private final AutoCloseableReadWriteUpdateLock rawAndComputedNodesLock;
  private final int parsingThreads;

  DaemonicCellState(Cell cell, int parsingThreads) {
    this.cell = new AtomicReference<>(cell);
    this.parsingThreads = parsingThreads;
    this.cellRoot = cell.getRoot();
    this.cellCanonicalName = cell.getCanonicalName();
    this.buildFileDependents = HashMultimap.create();
    this.targetsCornucopia = HashMultimap.create();
    this.buildFileEnv = new HashMap<>();
    this.allRawNodes = new ConcurrentMapCache<>(parsingThreads);
    this.allRawNodeTargets = new HashSet<>();
    this.typedNodeCaches = Maps.newConcurrentMap();
    this.rawAndComputedNodesLock = new AutoCloseableReadWriteUpdateLock();
  }

  // TODO(mzlee): Only needed for invalidateBasedOn which does not have access to cell metadata
  Cell getCell() {
    return Preconditions.checkNotNull(cell.get());
  }

  Path getCellRoot() {
    return cellRoot;
  }

  @SuppressWarnings("unchecked")
  public <T> CacheImpl<T> getOrCreateCache(Class<T> type) {
    try (AutoCloseableLock updateLock = rawAndComputedNodesLock.updateLock()) {
      CacheImpl<?> cache = typedNodeCaches.get(type);
      if (cache == null) {
        try (AutoCloseableLock writeLock = rawAndComputedNodesLock.writeLock()) {
          cache = new CacheImpl<>();
          typedNodeCaches.put(type, cache);
        }
      }
      return (CacheImpl<T>) cache;
    }
  }

  @SuppressWarnings("unchecked")
  public <T> CacheImpl<T> getCache(Class<T> type) {
    try (AutoCloseableLock readLock = rawAndComputedNodesLock.readLock()) {
      return (CacheImpl<T>) typedNodeCaches.get(type);
    }
  }

  Optional<ImmutableSet<Map<String, Object>>> lookupRawNodes(Path buildFile) {
    try (AutoCloseableLock readLock = rawAndComputedNodesLock.readLock()) {
      return Optional.ofNullable(allRawNodes.getIfPresent(buildFile));
    }
  }

  ImmutableSet<Map<String, Object>> putRawNodesIfNotPresentAndStripMetaEntries(
      final Path buildFile,
      final ImmutableSet<Map<String, Object>> withoutMetaIncludes,
      final ImmutableSet<Path> dependentsOfEveryNode,
      ImmutableMap<String, Optional<String>> env) {
    try (AutoCloseableLock writeLock = rawAndComputedNodesLock.writeLock()) {
      ImmutableSet<Map<String, Object>> updated =
          allRawNodes.putIfAbsentAndGet(buildFile, withoutMetaIncludes);
      for (Map<String, Object> node : updated) {
        allRawNodeTargets.add(
            RawNodeParsePipeline.parseBuildTargetFromRawRule(
                cellRoot, cellCanonicalName, node, buildFile));
      }
      buildFileEnv.put(buildFile, env);
      if (updated == withoutMetaIncludes) {
        // We now know all the nodes. They all implicitly depend on everything in
        // the "dependentsOfEveryNode" set.
        for (Path dependent : dependentsOfEveryNode) {
          buildFileDependents.put(dependent, buildFile);
        }
      }
      return updated;
    }
  }

  int invalidatePath(Path path) {
    try (AutoCloseableLock writeLock = rawAndComputedNodesLock.writeLock()) {
      int invalidatedRawNodes = 0;
      ImmutableSet<Map<String, Object>> rawNodes = allRawNodes.getIfPresent(path);
      if (rawNodes != null) {
        // Increment the counter
        invalidatedRawNodes = rawNodes.size();
        for (Map<String, Object> rawNode : rawNodes) {
          UnflavoredBuildTarget target =
              RawNodeParsePipeline.parseBuildTargetFromRawRule(
                  cellRoot, cellCanonicalName, rawNode, path);
          LOG.debug("Invalidating target for path %s: %s", path, target);
          for (CacheImpl<?> cache : typedNodeCaches.values()) {
            cache.allComputedNodes.invalidateAll(targetsCornucopia.get(target));
          }
          targetsCornucopia.removeAll(target);
          allRawNodeTargets.remove(target);
        }
        allRawNodes.invalidate(path);
      }

      // We may have been given a file that other build files depend on. Iteratively remove those.
      Iterable<Path> dependents = buildFileDependents.get(path);
      LOG.debug("Invalidating dependents for path %s: %s", path, dependents);
      for (Path dependent : dependents) {
        if (dependent.equals(path)) {
          continue;
        }
        invalidatedRawNodes += invalidatePath(dependent);
      }
      buildFileDependents.removeAll(path);
      buildFileEnv.remove(path);

      return invalidatedRawNodes;
    }
  }

  Optional<MapDifference<String, String>> invalidateIfEnvHasChanged(Cell cell, Path buildFile) {
    // Invalidate if env vars have changed.
    ImmutableMap<String, Optional<String>> usedEnv;
    try (AutoCloseableLock readLock = rawAndComputedNodesLock.readLock()) {
      usedEnv = buildFileEnv.get(buildFile);
    }
    if (usedEnv == null) {
      this.cell.set(cell);
      return Optional.empty();
    }
    for (Map.Entry<String, Optional<String>> ent : usedEnv.entrySet()) {
      Optional<String> value =
          Optional.ofNullable(cell.getBuckConfig().getEnvironment().get(ent.getKey()));
      if (!value.equals(ent.getValue())) {
        LOG.verbose("invalidating for env change: %s (%s != %s)", buildFile, value, ent.getValue());
        invalidatePath(buildFile);
        this.cell.set(cell);
        return Optional.of(
            Maps.difference(
                value.map(v -> ImmutableMap.of(ent.getKey(), v)).orElse(ImmutableMap.of()),
                ent.getValue()
                    .map(v -> ImmutableMap.of(ent.getKey(), v))
                    .orElse(ImmutableMap.of())));
      }
    }
    return Optional.empty();
  }
}
