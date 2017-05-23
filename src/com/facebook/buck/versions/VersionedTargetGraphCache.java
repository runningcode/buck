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

package com.facebook.buck.versions;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.event.BuckEvent;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.log.Logger;
import com.facebook.buck.rules.TargetGraphAndBuildTargets;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import com.google.common.collect.ImmutableMap;
import java.util.concurrent.ForkJoinPool;
import javax.annotation.Nullable;
import org.immutables.value.Value;

public class VersionedTargetGraphCache {

  private static final Logger LOG = Logger.get(VersionedTargetGraphCache.class);

  @Nullable private CachedVersionedTargetGraph cachedVersionedTargetGraph = null;

  /** @return a new versioned target graph. */
  private TargetGraphAndBuildTargets createdVersionedTargetGraph(
      TargetGraphAndBuildTargets targetGraphAndBuildTargets,
      ImmutableMap<String, VersionUniverse> versionUniverses,
      ForkJoinPool pool,
      TypeCoercerFactory typeCoercerFactory)
      throws VersionException, InterruptedException {
    return VersionedTargetGraphBuilder.transform(
        new VersionUniverseVersionSelector(
            targetGraphAndBuildTargets.getTargetGraph(), versionUniverses),
        targetGraphAndBuildTargets,
        pool,
        typeCoercerFactory);
  }

  private VersionedTargetGraphCacheResult getVersionedTargetGraph(
      TargetGraphAndBuildTargets targetGraphAndBuildTargets,
      ImmutableMap<String, VersionUniverse> versionUniverses,
      ForkJoinPool pool,
      TypeCoercerFactory typeCoercerFactory)
      throws VersionException, InterruptedException {

    // If new inputs match old ones, we can used the cached graph, if present.
    VersionedTargetGraphInputs newInputs =
        VersionedTargetGraphInputs.of(targetGraphAndBuildTargets, versionUniverses);
    if (cachedVersionedTargetGraph != null
        && newInputs.equals(cachedVersionedTargetGraph.getInputs())) {
      return VersionedTargetGraphCacheResult.of(
          ResultType.HIT, cachedVersionedTargetGraph.getTargetGraphAndBuildTargets());
    }

    // Build and cache new versioned target graph.
    ResultType resultType =
        cachedVersionedTargetGraph == null ? ResultType.EMPTY : ResultType.MISMATCH;
    TargetGraphAndBuildTargets newVersionedTargetGraph =
        createdVersionedTargetGraph(
            targetGraphAndBuildTargets, versionUniverses, pool, typeCoercerFactory);
    cachedVersionedTargetGraph = CachedVersionedTargetGraph.of(newInputs, newVersionedTargetGraph);
    return VersionedTargetGraphCacheResult.of(resultType, newVersionedTargetGraph);
  }

  /**
   * @return a versioned target graph, either generated from the parameters or retrieved from a
   *     cache.
   */
  public VersionedTargetGraphCacheResult getVersionedTargetGraph(
      BuckEventBus eventBus,
      TypeCoercerFactory typeCoercerFactory,
      TargetGraphAndBuildTargets targetGraphAndBuildTargets,
      ImmutableMap<String, VersionUniverse> versionUniverses,
      ForkJoinPool pool)
      throws VersionException, InterruptedException {

    VersionedTargetGraphEvent.Started started = VersionedTargetGraphEvent.started();
    eventBus.post(started);
    try {
      VersionedTargetGraphCacheResult result =
          getVersionedTargetGraph(
              targetGraphAndBuildTargets, versionUniverses, pool, typeCoercerFactory);
      LOG.info("versioned target graph " + result.getType().getDescription());
      eventBus.post(result.getType().getEvent());
      return result;
    } finally {
      eventBus.post(VersionedTargetGraphEvent.finished(started));
    }
  }

  public TargetGraphAndBuildTargets toVersionedTargetGraph(
      BuckEventBus eventBus,
      BuckConfig buckConfig,
      TypeCoercerFactory typeCoercerFactory,
      TargetGraphAndBuildTargets targetGraphAndBuildTargets)
      throws VersionException, InterruptedException {
    return getVersionedTargetGraph(
            eventBus,
            typeCoercerFactory,
            targetGraphAndBuildTargets,
            new VersionBuckConfig(buckConfig).getVersionUniverses(),
            new ForkJoinPool(buckConfig.getNumThreads()))
        .getTargetGraphAndBuildTargets();
  }

  /**
   * A collection of anything which affects/changes how the versioned target graph is generated. If
   * any of these items changes between runs, we cannot use the cached versioned target graph and
   * must re-generate it.
   */
  @Value.Immutable
  @BuckStyleTuple
  interface AbstractVersionedTargetGraphInputs {

    /** @return the un-versioned target graph to be transformed. */
    TargetGraphAndBuildTargets getTargetGraphAndBuildTargets();

    /** @return the version universes used when generating the versioned target graph. */
    ImmutableMap<String, VersionUniverse> getVersionUniverses();
  }

  /**
   * Tuple to store the previously cached versioned target graph along with all inputs that affect
   * how it's generated (for invalidation detection).
   */
  @Value.Immutable
  @BuckStyleTuple
  interface AbstractCachedVersionedTargetGraph {

    /** @return any inputs which, when changed, may produce a different versioned target graph. */
    VersionedTargetGraphInputs getInputs();

    /** @return a versioned target graph. */
    TargetGraphAndBuildTargets getTargetGraphAndBuildTargets();
  }

  @Value.Immutable
  @BuckStyleTuple
  interface AbstractVersionedTargetGraphCacheResult {

    /** @return the type of result. */
    ResultType getType();

    /** @return a versioned target graph. */
    TargetGraphAndBuildTargets getTargetGraphAndBuildTargets();
  }

  /** The possible result types using the cache. */
  public enum ResultType {

    /** A miss in the cache due to the inputs changing. */
    MISMATCH {
      @Override
      BuckEvent getEvent() {
        return VersionedTargetGraphEvent.Cache.miss();
      }

      @Override
      String getDescription() {
        return "cache miss (mismatch)";
      }
    },

    /** A miss in the cache due to the cache being empty. */
    EMPTY {
      @Override
      BuckEvent getEvent() {
        return VersionedTargetGraphEvent.Cache.miss();
      }

      @Override
      String getDescription() {
        return "cache miss (empty)";
      }
    },

    /** A hit in the cache. */
    HIT {
      @Override
      BuckEvent getEvent() {
        return VersionedTargetGraphEvent.Cache.hit();
      }

      @Override
      String getDescription() {
        return "cache hit";
      }
    },
    ;

    abstract BuckEvent getEvent();

    abstract String getDescription();
  }
}
