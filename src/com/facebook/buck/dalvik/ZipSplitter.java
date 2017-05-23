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

package com.facebook.buck.dalvik;

import com.facebook.buck.android.APKModule;
import com.google.common.collect.ImmutableMultimap;
import java.io.IOException;
import java.nio.file.Path;

public interface ZipSplitter {

  enum DexSplitStrategy {
    MAXIMIZE_PRIMARY_DEX_SIZE,
    MINIMIZE_PRIMARY_DEX_SIZE,
    ;
  }

  enum CanaryStrategy {
    INCLUDE_CANARIES,
    DONT_INCLUDE_CANARIES,
    ;
  }

  /**
   * Writes the primary zip file and if necessary, the secondary zip files.
   *
   * @return output map of dex store to zip files.
   */
  ImmutableMultimap<APKModule, Path> execute() throws IOException;
}
