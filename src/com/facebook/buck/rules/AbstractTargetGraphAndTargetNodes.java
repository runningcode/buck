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

package com.facebook.buck.rules;

import com.facebook.buck.util.immutables.BuckStyleImmutable;
import org.immutables.value.Value;

/** Holds a TargetGraph and a set of BuildTargets. */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractTargetGraphAndTargetNodes {
  abstract TargetGraph getTargetGraph();

  abstract Iterable<TargetNode<?, ?>> getTargetNodes();

  public static TargetGraphAndTargetNodes fromTargetGraphAndBuildTargets(
      TargetGraphAndBuildTargets targetGraphAndBuildTargets) {
    TargetGraph targetGraph = targetGraphAndBuildTargets.getTargetGraph();
    return TargetGraphAndTargetNodes.builder()
        .setTargetGraph(targetGraph)
        .setTargetNodes(targetGraph.getAll(targetGraphAndBuildTargets.getBuildTargets()))
        .build();
  }
}
