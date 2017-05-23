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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.google.common.collect.ImmutableMap;

/**
 * An interface that represents a {@link com.facebook.buck.rules.BuildRule} which can contribute
 * components (e.g. header files, preprocessor macros) to the preprocessing of some top-level file
 * (e.g. a C++ source from a C++ library rule).
 */
public interface CxxPreprocessorDep {

  BuildTarget getBuildTarget();

  Iterable<? extends CxxPreprocessorDep> getCxxPreprocessorDeps(CxxPlatform cxxPlatform);

  CxxPreprocessorInput getCxxPreprocessorInput(
      CxxPlatform cxxPlatform, HeaderVisibility headerVisibility) throws NoSuchBuildTargetException;

  ImmutableMap<BuildTarget, CxxPreprocessorInput> getTransitiveCxxPreprocessorInput(
      CxxPlatform cxxPlatform, HeaderVisibility headerVisibility) throws NoSuchBuildTargetException;
}
