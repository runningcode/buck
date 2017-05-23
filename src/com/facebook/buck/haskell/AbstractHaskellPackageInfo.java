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

import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import org.immutables.value.Value;

/** Identifying information for a {@link HaskellPackage}. */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractHaskellPackageInfo implements RuleKeyAppendable {

  public abstract String getName();

  public abstract String getVersion();

  public abstract String getIdentifier();

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    sink.setReflectively("name", getName());
    sink.setReflectively("version", getVersion());
    sink.setReflectively("identifier", getIdentifier());
  }
}
