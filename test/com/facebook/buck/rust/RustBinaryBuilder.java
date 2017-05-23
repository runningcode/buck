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

package com.facebook.buck.rust;

import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.SourcePath;
import com.google.common.collect.ImmutableSortedSet;

public class RustBinaryBuilder
    extends AbstractNodeBuilder<
        RustBinaryDescriptionArg.Builder, RustBinaryDescriptionArg, RustBinaryDescription,
        BuildRule> {

  private RustBinaryBuilder(RustBinaryDescription description, BuildTarget target) {
    super(description, target);
  }

  public static RustBinaryBuilder from(String target) {
    return new RustBinaryBuilder(
        new RustBinaryDescription(
            FakeRustConfig.FAKE_RUST_CONFIG,
            CxxPlatformUtils.DEFAULT_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM),
        BuildTargetFactory.newInstance(target));
  }

  public RustBinaryBuilder setSrcs(ImmutableSortedSet<SourcePath> srcs) {
    getArgForPopulating().setSrcs(srcs);
    return this;
  }

  public RustBinaryBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    getArgForPopulating().setDeps(deps);
    return this;
  }
}
