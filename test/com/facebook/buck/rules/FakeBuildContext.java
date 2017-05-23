/*
 * Copyright 2013-present Facebook, Inc.
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

import static org.easymock.EasyMock.createMock;

import com.facebook.buck.event.BuckEventBusFactory;
import com.facebook.buck.jvm.java.FakeJavaPackageFinder;
import com.google.common.collect.ImmutableList;

/** Facilitates creating a fake {@link BuildContext} for unit tests. */
public class FakeBuildContext {

  /** Utility class: do not instantiate. */
  private FakeBuildContext() {}

  /** A BuildContext which doesn't touch the host filesystem or actually execute steps. */
  public static final BuildContext NOOP_CONTEXT =
      withSourcePathResolver(createMock(SourcePathResolver.class));

  public static BuildContext withSourcePathResolver(SourcePathResolver pathResolver) {
    return BuildContext.builder()
        .setActionGraph(new ActionGraph(ImmutableList.of()))
        .setSourcePathResolver(pathResolver)
        .setJavaPackageFinder(new FakeJavaPackageFinder())
        .setEventBus(BuckEventBusFactory.newInstance())
        .build();
  }
}
