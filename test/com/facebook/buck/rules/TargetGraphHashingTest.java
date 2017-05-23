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

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.DefaultBuckEventBus;
import com.facebook.buck.graph.AcyclicDepthFirstPostOrderTraversal;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.timing.IncrementingFakeClock;
import com.facebook.buck.util.cache.FileHashCache;
import com.facebook.buck.util.cache.NullFileHashCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Test;

public class TargetGraphHashingTest {

  @Test
  public void emptyTargetGraphHasEmptyHashes()
      throws IOException, InterruptedException, AcyclicDepthFirstPostOrderTraversal.CycleException {
    BuckEventBus eventBus = new DefaultBuckEventBus(new IncrementingFakeClock(), new BuildId());
    TargetGraph targetGraph = TargetGraphFactory.newInstance();

    assertThat(
        new TargetGraphHashing(eventBus, targetGraph, new NullFileHashCache(), ImmutableList.of())
            .hashTargetGraph()
            .entrySet(),
        empty());
  }

  @Test
  public void hashChangesWhenSrcContentChanges()
      throws IOException, InterruptedException, AcyclicDepthFirstPostOrderTraversal.CycleException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    BuckEventBus eventBus = new DefaultBuckEventBus(new IncrementingFakeClock(), new BuildId());

    TargetNode<?, ?> node =
        createJavaLibraryTargetNodeWithSrcs(
            BuildTargetFactory.newInstance("//foo:lib"),
            HashCode.fromLong(64738),
            ImmutableSet.of(Paths.get("foo/FooLib.java")));
    TargetGraph targetGraph = TargetGraphFactory.newInstance(node);

    FileHashCache baseCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                projectFilesystem.resolve("foo/FooLib.java"), HashCode.fromString("abcdef")));

    FileHashCache modifiedCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                projectFilesystem.resolve("foo/FooLib.java"), HashCode.fromString("abc1ef")));

    Map<BuildTarget, HashCode> baseResult =
        new TargetGraphHashing(eventBus, targetGraph, baseCache, ImmutableList.of(node))
            .hashTargetGraph();

    Map<BuildTarget, HashCode> modifiedResult =
        new TargetGraphHashing(eventBus, targetGraph, modifiedCache, ImmutableList.of(node))
            .hashTargetGraph();

    assertThat(baseResult, aMapWithSize(1));
    assertThat(baseResult, hasKey(node.getBuildTarget()));
    assertThat(modifiedResult, aMapWithSize(1));
    assertThat(modifiedResult, hasKey(node.getBuildTarget()));
    assertThat(
        modifiedResult.get(node.getBuildTarget()),
        not(equalTo(baseResult.get(node.getBuildTarget()))));
  }

  @Test
  public void twoNodeIndependentRootsTargetGraphHasExpectedHashes()
      throws IOException, InterruptedException, AcyclicDepthFirstPostOrderTraversal.CycleException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    BuckEventBus eventBus = new DefaultBuckEventBus(new IncrementingFakeClock(), new BuildId());

    TargetNode<?, ?> nodeA =
        createJavaLibraryTargetNodeWithSrcs(
            BuildTargetFactory.newInstance("//foo:lib"),
            HashCode.fromLong(64738),
            ImmutableSet.of(Paths.get("foo/FooLib.java")));
    TargetNode<?, ?> nodeB =
        createJavaLibraryTargetNodeWithSrcs(
            BuildTargetFactory.newInstance("//bar:lib"),
            HashCode.fromLong(49152),
            ImmutableSet.of(Paths.get("bar/BarLib.java")));
    TargetGraph targetGraphA = TargetGraphFactory.newInstance(nodeA);
    TargetGraph targetGraphB = TargetGraphFactory.newInstance(nodeB);
    TargetGraph commonTargetGraph = TargetGraphFactory.newInstance(nodeA, nodeB);

    FileHashCache fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                projectFilesystem.resolve("foo/FooLib.java"), HashCode.fromString("abcdef"),
                projectFilesystem.resolve("bar/BarLib.java"), HashCode.fromString("123456")));

    Map<BuildTarget, HashCode> resultsA =
        new TargetGraphHashing(eventBus, targetGraphA, fileHashCache, ImmutableList.of(nodeA))
            .hashTargetGraph();

    Map<BuildTarget, HashCode> resultsB =
        new TargetGraphHashing(eventBus, targetGraphB, fileHashCache, ImmutableList.of(nodeB))
            .hashTargetGraph();

    Map<BuildTarget, HashCode> commonResults =
        new TargetGraphHashing(
                eventBus, commonTargetGraph, fileHashCache, ImmutableList.of(nodeA, nodeB))
            .hashTargetGraph();

    assertThat(resultsA, aMapWithSize(1));
    assertThat(resultsA, hasKey(nodeA.getBuildTarget()));
    assertThat(resultsB, aMapWithSize(1));
    assertThat(resultsB, hasKey(nodeB.getBuildTarget()));
    assertThat(commonResults, aMapWithSize(2));
    assertThat(commonResults, hasKey(nodeA.getBuildTarget()));
    assertThat(commonResults, hasKey(nodeB.getBuildTarget()));
    assertThat(
        resultsA.get(nodeA.getBuildTarget()), equalTo(commonResults.get(nodeA.getBuildTarget())));
    assertThat(
        resultsB.get(nodeB.getBuildTarget()), equalTo(commonResults.get(nodeB.getBuildTarget())));
  }

  private TargetGraph createGraphWithANodeAndADep(
      BuildTarget nodeTarget, HashCode nodeHash, BuildTarget depTarget, HashCode depHash) {
    TargetNode<?, ?> dep =
        createJavaLibraryTargetNodeWithSrcs(
            depTarget, depHash, ImmutableSet.of(Paths.get("dep/DepLib.java")));
    TargetNode<?, ?> node =
        createJavaLibraryTargetNodeWithSrcs(
            nodeTarget, nodeHash, ImmutableSet.of(Paths.get("foo/FooLib.java")), dep);
    return TargetGraphFactory.newInstance(node, dep);
  }

  @Test
  public void hashChangesForDependentNodeWhenDepsChange()
      throws IOException, InterruptedException, AcyclicDepthFirstPostOrderTraversal.CycleException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    BuckEventBus eventBus = new DefaultBuckEventBus(new IncrementingFakeClock(), new BuildId());

    BuildTarget nodeTarget = BuildTargetFactory.newInstance("//foo:lib");
    BuildTarget depTarget = BuildTargetFactory.newInstance("//dep:lib");

    TargetGraph targetGraphA =
        createGraphWithANodeAndADep(
            nodeTarget, HashCode.fromLong(12345), depTarget, HashCode.fromLong(64738));

    TargetGraph targetGraphB =
        createGraphWithANodeAndADep(
            nodeTarget, HashCode.fromLong(12345), depTarget, HashCode.fromLong(84552));

    FileHashCache fileHashCache =
        new FakeFileHashCache(
            ImmutableMap.of(
                projectFilesystem.resolve("foo/FooLib.java"), HashCode.fromString("abcdef"),
                projectFilesystem.resolve("dep/DepLib.java"), HashCode.fromString("123456")));

    Map<BuildTarget, HashCode> resultA =
        new TargetGraphHashing(
                eventBus,
                targetGraphA,
                fileHashCache,
                ImmutableList.of(targetGraphA.get(nodeTarget)))
            .hashTargetGraph();

    Map<BuildTarget, HashCode> resultB =
        new TargetGraphHashing(
                eventBus,
                targetGraphB,
                fileHashCache,
                ImmutableList.of(targetGraphB.get(nodeTarget)))
            .hashTargetGraph();

    assertThat(resultA, aMapWithSize(2));
    assertThat(resultA, hasKey(nodeTarget));
    assertThat(resultA, hasKey(depTarget));
    assertThat(resultB, aMapWithSize(2));
    assertThat(resultB, hasKey(nodeTarget));
    assertThat(resultB, hasKey(depTarget));
    assertThat(resultA.get(nodeTarget), not(equalTo(resultB.get(nodeTarget))));
    assertThat(resultA.get(depTarget), not(equalTo(resultB.get(depTarget))));
  }

  private static TargetNode<?, ?> createJavaLibraryTargetNodeWithSrcs(
      BuildTarget buildTarget,
      HashCode hashCode,
      ImmutableSet<Path> srcs,
      TargetNode<?, ?>... deps) {
    JavaLibraryBuilder targetNodeBuilder = JavaLibraryBuilder.createBuilder(buildTarget, hashCode);
    for (TargetNode<?, ?> dep : deps) {
      targetNodeBuilder.addDep(dep.getBuildTarget());
    }
    for (Path src : srcs) {
      targetNodeBuilder.addSrc(src);
    }
    return targetNodeBuilder.build();
  }
}
