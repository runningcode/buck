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

package com.facebook.buck.haskell;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;

/** An interface for rule which can provide interfaces files for a haskell compilation. */
public interface HaskellCompileDep {

  /** @return a list of deps needed to compile this rule. */
  Iterable<BuildRule> getCompileDeps(CxxPlatform cxxPlatform);

  /** @return the {@link HaskellCompileInput} object that contributes to compilation. */
  HaskellCompileInput getCompileInput(CxxPlatform cxxPlatform, Linker.LinkableDepType depType)
      throws NoSuchBuildTargetException;
}
