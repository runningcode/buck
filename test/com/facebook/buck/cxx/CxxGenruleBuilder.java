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
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;

public class CxxGenruleBuilder
    extends AbstractNodeBuilder<
        CxxGenruleDescriptionArg.Builder, CxxGenruleDescriptionArg, CxxGenruleDescription,
        BuildRule> {

  public CxxGenruleBuilder(BuildTarget target, FlavorDomain<CxxPlatform> cxxPlatforms) {
    super(new CxxGenruleDescription(cxxPlatforms), target);
  }

  public CxxGenruleBuilder(BuildTarget target) {
    this(target, CxxPlatformUtils.DEFAULT_PLATFORMS);
  }

  public CxxGenruleBuilder setOut(String out) {
    getArgForPopulating().setOut(out);
    return this;
  }

  public CxxGenruleBuilder setCmd(String cmd) {
    getArgForPopulating().setCmd(cmd);
    return this;
  }
}
