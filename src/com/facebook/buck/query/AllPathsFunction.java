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

// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.facebook.buck.query;

import com.facebook.buck.query.QueryEnvironment.Argument;
import com.facebook.buck.query.QueryEnvironment.ArgumentType;
import com.facebook.buck.query.QueryEnvironment.QueryFunction;
import com.facebook.buck.util.MoreSets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * A allpaths(from, to) expression, which computes all paths between the build targets in the set
 * 'from' and the build targets in the set 'to', by following the dependencies between nodes in the
 * target graph.
 *
 * <pre>expr ::= ALLPATHS '(' expr ',' expr ')'</pre>
 */
public class AllPathsFunction implements QueryFunction {

  private static final ImmutableList<ArgumentType> ARGUMENT_TYPES =
      ImmutableList.of(ArgumentType.EXPRESSION, ArgumentType.EXPRESSION);

  public AllPathsFunction() {}

  @Override
  public String getName() {
    return "allpaths";
  }

  @Override
  public int getMandatoryArguments() {
    return 2;
  }

  @Override
  public ImmutableList<ArgumentType> getArgumentTypes() {
    return ARGUMENT_TYPES;
  }

  @Override
  public ImmutableSet<QueryTarget> eval(
      QueryEnvironment env, ImmutableList<Argument> args, ListeningExecutorService executor)
      throws QueryException, InterruptedException {
    QueryExpression from = args.get(0).getExpression();
    QueryExpression to = args.get(1).getExpression();

    Set<QueryTarget> fromSet = from.eval(env, executor);
    Set<QueryTarget> toSet = to.eval(env, executor);

    // Algorithm:
    // 1) compute "reachableFromX", the forward transitive closure of the "from" set;
    // 2) find the intersection of "reachableFromX" with the "to" set, and traverse the graph using
    //    the reverse dependencies. This will effectively compute the intersection between the nodes
    //    reachable from the "from" set and the reverse transitive closure of the "to" set.

    env.buildTransitiveClosure(fromSet, Integer.MAX_VALUE, executor);

    Set<QueryTarget> reachableFromX = env.getTransitiveClosure(fromSet);
    Set<QueryTarget> result = MoreSets.intersection(reachableFromX, toSet);
    Collection<QueryTarget> worklist = result;
    while (!worklist.isEmpty()) {
      Collection<QueryTarget> reverseDeps = env.getReverseDeps(worklist);
      worklist = new ArrayList<>();
      for (QueryTarget target : reverseDeps) {
        if (reachableFromX.contains(target) && result.add(target)) {
          worklist.add(target);
        }
      }
    }
    return ImmutableSet.copyOf(result);
  }
}
