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

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.google.common.collect.ImmutableCollection;

/**
 * While building up the target graph, we infer the implicit dependencies of a rule by parsing all
 * parameters with types {@link com.facebook.buck.rules.SourcePath} or {@link
 * com.facebook.buck.rules.BuildRule}. However, in some cases like {@link
 * com.facebook.buck.shell.GenruleDescription}, the {@link
 * com.facebook.buck.shell.AbstractGenruleDescription.Arg#cmd} argument contains build targets in a
 * specific format. Any {@link Description} that implements this interface can modify its implicit
 * deps by poking at the raw build rule params.
 */
public interface ImplicitDepsInferringDescription<T> {

  void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      T constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder);

  /**
   * Infers any dependencies which can only be detected with access to the whole {@link TargetGraph}
   */
  @SuppressWarnings("unused")
  default void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      TargetGraph targetGraph,
      BuildRuleResolver resolver,
      SourcePathRuleFinder ruleFinder,
      ProjectFilesystem projectFilesystem,
      T constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {}
}
