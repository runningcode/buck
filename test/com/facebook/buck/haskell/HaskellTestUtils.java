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

import static org.junit.Assume.assumeTrue;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.io.ExecutableFinder;
import java.nio.file.Path;
import java.util.Optional;

public class HaskellTestUtils {

  private HaskellTestUtils() {}

  /** Assume that we can find a haskell compiler on the system. */
  public static void assumeSystemCompiler() {
    HaskellBuckConfig fakeConfig =
        new HaskellBuckConfig(FakeBuckConfig.builder().build(), new ExecutableFinder());
    Optional<Path> compilerOptional = fakeConfig.getSystemCompiler();
    assumeTrue(compilerOptional.isPresent());
  }
}
