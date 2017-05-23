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

package com.facebook.buck.apple;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.google.common.collect.ImmutableSortedSet;

public abstract class AbstractXcodeScriptBuilder<
        T extends AbstractXcodeScriptBuilder<T, U>,
        U extends Description<XcodeScriptDescriptionArg>>
    extends AbstractNodeBuilder<
        XcodeScriptDescriptionArg.Builder, XcodeScriptDescriptionArg, U, BuildRule> {

  public AbstractXcodeScriptBuilder(U description, BuildTarget target) {
    super(description, target);
  }

  public T setSrcs(ImmutableSortedSet<SourcePath> srcs) {
    getArgForPopulating().setSrcs(srcs);
    return getThis();
  }

  public T setOutputs(ImmutableSortedSet<String> outputs) {
    getArgForPopulating().setOutputs(outputs);
    return getThis();
  }

  public T setCmd(String cmd) {
    getArgForPopulating().setCmd(cmd);
    return getThis();
  }

  protected abstract T getThis();
}
