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

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.SourcePath;

public class KeystoreBuilder
    extends AbstractNodeBuilder<
        KeystoreDescriptionArg.Builder, KeystoreDescriptionArg, KeystoreDescription, Keystore> {

  private KeystoreBuilder(BuildTarget target) {
    super(new KeystoreDescription(), target);
  }

  public static KeystoreBuilder createBuilder(BuildTarget target) {
    return new KeystoreBuilder(target);
  }

  public KeystoreBuilder setStore(SourcePath store) {
    getArgForPopulating().setStore(store);
    return this;
  }

  public KeystoreBuilder setProperties(SourcePath properties) {
    getArgForPopulating().setProperties(properties);
    return this;
  }

  public KeystoreBuilder addDep(BuildTarget dep) {
    getArgForPopulating().addDeps(dep);
    return this;
  }
}
