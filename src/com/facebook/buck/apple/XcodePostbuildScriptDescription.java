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

package com.facebook.buck.apple;

import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.TargetGraph;

/**
 * Description for an xcode_postbuild_script rule which runs a shell script after the 'copy
 * resources' phase has run.
 *
 * <p>Example rule:
 *
 * <pre>
 * xcode_postbuild_script(
 *   name = 'pngcrush',
 *   cmd = '../Tools/pngcrush.sh',
 * )
 * </pre>
 *
 * <p>This rule is a hack and in the long-term should be replaced with a rule which operates
 * similarly to apk_genrule, or should be removed entirely if possible. Those rules do nothing when
 * building with Buck.
 */
public class XcodePostbuildScriptDescription implements Description<XcodeScriptDescriptionArg> {

  @Override
  public Class<XcodeScriptDescriptionArg> getConstructorArgType() {
    return XcodeScriptDescriptionArg.class;
  }

  @Override
  public NoopBuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      XcodeScriptDescriptionArg args) {
    return new NoopBuildRule(params);
  }
}
