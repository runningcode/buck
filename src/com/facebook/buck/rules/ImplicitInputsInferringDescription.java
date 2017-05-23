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

import com.facebook.buck.model.UnflavoredBuildTarget;
import java.nio.file.Path;

/**
 * While building up the target graph, we infer implicit inputs of a rule if certain fields are
 * absent (e.g. src field). Any {@link Description} that implements this interface can modify its
 * implicit inputs by poking at the raw build rule params.
 */
public interface ImplicitInputsInferringDescription<T> {

  Iterable<Path> inferInputsFromConstructorArgs(
      UnflavoredBuildTarget buildTarget, T constructorArg);
}
