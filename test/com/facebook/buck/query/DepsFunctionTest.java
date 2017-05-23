/*
 * Copyright 2017-present Facebook, Inc.
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

package com.facebook.buck.query;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertThat;

import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.RichStream;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.hamcrest.Matchers;
import org.junit.Test;

public class DepsFunctionTest {

  private static final ListeningExecutorService EXECUTOR = MoreExecutors.newDirectExecutorService();
  private static final DepsFunction DEPS_FUNCTION = new DepsFunction();
  private static final QueryEnvironment.Argument FIRST_ORDER_DEPS =
      QueryEnvironment.Argument.of(
          new FunctionExpression(new DepsFunction.FirstOrderDepsFunction(), ImmutableList.of()));
  private static final QueryEnvironment.Argument DEPTH = QueryEnvironment.Argument.of(10);

  @Test
  public void testFilterArgumentDoesNotLimitDeps() throws Exception {
    TargetNode<?, ?> b =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:b")).build();
    TargetNode<?, ?> a =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:a"))
            .addDep(b.getBuildTarget())
            .build();
    TargetGraph targetGraph = TargetGraphFactory.newInstance(a, b);
    QueryEnvironment queryEnvironment = makeFakeQueryEnvironment(targetGraph);
    assertThat(
        DEPS_FUNCTION.eval(
            queryEnvironment,
            ImmutableList.of(
                QueryEnvironment.Argument.of(
                    new TargetLiteral(a.getBuildTarget().getFullyQualifiedName())),
                DEPTH,
                FIRST_ORDER_DEPS),
            EXECUTOR),
        Matchers.containsInAnyOrder(
            QueryBuildTarget.of(a.getBuildTarget()), QueryBuildTarget.of(b.getBuildTarget())));
  }

  @Test
  public void testFilterArgumentLimitsDeps() throws Exception {
    TargetNode<?, ?> c =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//foo:c")).build();
    TargetNode<?, ?> b =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//bar:b"))
            .addDep(c.getBuildTarget())
            .build();
    TargetNode<?, ?> a =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//foo:a"))
            .addDep(b.getBuildTarget())
            .build();
    TargetGraph targetGraph = TargetGraphFactory.newInstance(a, b, c);
    QueryEnvironment queryEnvironment = makeFakeQueryEnvironment(targetGraph);
    assertThat(
        DEPS_FUNCTION.eval(
            queryEnvironment,
            ImmutableList.of(
                QueryEnvironment.Argument.of(
                    new TargetLiteral(a.getBuildTarget().getFullyQualifiedName())),
                DEPTH,
                QueryEnvironment.Argument.of(
                    new FunctionExpression(
                        new FilterFunction(),
                        ImmutableList.of(
                            QueryEnvironment.Argument.of("//foo.*"), FIRST_ORDER_DEPS)))),
            EXECUTOR),
        Matchers.contains(QueryBuildTarget.of(a.getBuildTarget())));
  }

  private QueryEnvironment makeFakeQueryEnvironment(TargetGraph targetGraph) throws Exception {
    QueryEnvironment env = createNiceMock(QueryEnvironment.class);

    final Capture<String> stringCapture = Capture.newInstance();
    expect(env.getTargetsMatchingPattern(EasyMock.capture(stringCapture), anyObject()))
        .andStubAnswer(
            () ->
                ImmutableSet.of(
                    QueryBuildTarget.of(BuildTargetFactory.newInstance(stringCapture.getValue()))));

    final Capture<Iterable<QueryTarget>> targetsCapture = Capture.newInstance();
    expect(env.getFwdDeps(EasyMock.capture(targetsCapture)))
        .andStubAnswer(
            () ->
                RichStream.from(targetsCapture.getValue())
                    .map(QueryBuildTarget.class::cast)
                    .map(QueryBuildTarget::getBuildTarget)
                    .map(targetGraph::get)
                    .flatMap(n -> targetGraph.getOutgoingNodesFor(n).stream())
                    .map(TargetNode::getBuildTarget)
                    .map(QueryBuildTarget::of)
                    .map(QueryTarget.class::cast)
                    .toImmutableSet());

    replay(env);

    return env;
  }
}
