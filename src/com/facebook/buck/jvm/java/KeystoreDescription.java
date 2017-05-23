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

package com.facebook.buck.jvm.java;

import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.HasDeclaredDeps;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import org.immutables.value.Value;

public class KeystoreDescription implements Description<KeystoreDescriptionArg> {

  @Override
  public Class<KeystoreDescriptionArg> getConstructorArgType() {
    return KeystoreDescriptionArg.class;
  }

  @Override
  public Keystore createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      KeystoreDescriptionArg args) {
    return new Keystore(params, args.getStore(), args.getProperties());
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractKeystoreDescriptionArg extends CommonDescriptionArg, HasDeclaredDeps {
    SourcePath getStore();

    SourcePath getProperties();
  }
}
