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
package com.facebook.buck.model;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;

/**
 * When applied to a {@link com.facebook.buck.rules.Description} this indicates that it supports
 * flavours.
 *
 * <p>This is only required to be implemented by Descriptions where the flavored BuildTarget should
 * be buildable in a top-level {@link com.facebook.buck.command.Build}. i.e. this should be
 * implemented by {@link com.facebook.buck.rules.Description}s where the
 * {@com.facebook.buck.rules.TargetNode} may have a flavored {@link
 * com.facebook.buck.model.BuildTarget} - it is not required where only the {@link
 * com.facebook.buck.rules.BuildRule}s are flavored.
 */
public interface Flavored {

  /**
   * @param flavors The set of {@link Flavor}s to consider. All must match.
   * @return Whether a {@link com.facebook.buck.rules.BuildRule} of the given {@link Flavor} can be
   *     created.
   */
  boolean hasFlavors(ImmutableSet<Flavor> flavors);

  default Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    return Optional.empty();
  }
}
