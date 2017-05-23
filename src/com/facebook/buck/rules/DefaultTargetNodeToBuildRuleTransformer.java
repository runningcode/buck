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

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;

/** Takes in an {@link TargetNode} from the target graph and builds a {@link BuildRule}. */
public class DefaultTargetNodeToBuildRuleTransformer implements TargetNodeToBuildRuleTransformer {

  @Override
  public <T, U extends Description<T>> BuildRule transform(
      TargetGraph targetGraph, BuildRuleResolver ruleResolver, TargetNode<T, U> targetNode)
      throws NoSuchBuildTargetException {
    U description = targetNode.getDescription();
    T arg = targetNode.getConstructorArg();

    ImmutableSet.Builder<BuildTarget> extraDepsBuilder = new ImmutableSet.Builder<>();
    ImmutableSet.Builder<BuildTarget> targetGraphOnlyDepsBuilder = new ImmutableSet.Builder<>();
    if (description instanceof ImplicitDepsInferringDescription) {
      @SuppressWarnings("unchecked")
      ImplicitDepsInferringDescription<T> castedDescription =
          (ImplicitDepsInferringDescription<T>) description;
      castedDescription.findDepsForTargetFromConstructorArgs(
          targetNode.getBuildTarget(),
          targetNode.getCellNames(),
          targetGraph,
          ruleResolver,
          new SourcePathRuleFinder(ruleResolver),
          targetNode.getFilesystem(),
          arg,
          extraDepsBuilder,
          targetGraphOnlyDepsBuilder);
    }
    Set<BuildTarget> extraDeps = Sets.union(targetNode.getExtraDeps(), extraDepsBuilder.build());
    Set<BuildTarget> targetGraphOnlyDeps =
        Sets.union(targetNode.getTargetGraphOnlyDeps(), targetGraphOnlyDepsBuilder.build());

    // The params used for the Buildable only contain the declared parameters. However, the deps of
    // the rule include not only those, but also any that were picked up through the deps declared
    // via a SourcePath.
    BuildRuleParams params =
        new BuildRuleParams(
            targetNode.getBuildTarget(),
            Suppliers.ofInstance(ruleResolver.requireAllRules(targetNode.getDeclaredDeps())),
            Suppliers.ofInstance(ruleResolver.requireAllRules(extraDeps)),
            ruleResolver.requireAllRules(targetGraphOnlyDeps),
            targetNode.getFilesystem());

    return description.createBuildRule(
        targetGraph, params, ruleResolver, targetNode.getCellNames(), arg);
  }
}
