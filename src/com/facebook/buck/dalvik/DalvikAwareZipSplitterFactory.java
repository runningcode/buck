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
import com.facebook.buck.android.APKModuleGraph;
import com.facebook.buck.io.ProjectFilesystem;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Set;

public class DalvikAwareZipSplitterFactory implements ZipSplitterFactory {

  private final long linearAllocLimit;
  private final Set<String> wantedInPrimaryZip;

  public DalvikAwareZipSplitterFactory(long linearAllocLimit, Set<String> wantedInPrimaryZip) {
    this.linearAllocLimit = linearAllocLimit;
    this.wantedInPrimaryZip = wantedInPrimaryZip;
  }

  @Override
  public ZipSplitter newInstance(
      ProjectFilesystem filesystem,
      Set<Path> inFiles,
      Path outPrimary,
      Path outSecondaryDir,
      String secondaryPattern,
      Path outDexStoresDir,
      Predicate<String> requiredInPrimaryZip,
      ImmutableSet<String> secondaryHeadSet,
      ImmutableSet<String> secondaryTailSet,
      ImmutableMultimap<APKModule, String> additionalDexStoreSets,
      APKModuleGraph apkModuleGraph,
      ZipSplitter.DexSplitStrategy dexSplitStrategy,
      ZipSplitter.CanaryStrategy canaryStrategy,
      Path reportDir) {
    return DalvikAwareZipSplitter.splitZip(
        filesystem,
        inFiles,
        outPrimary,
        outSecondaryDir,
        secondaryPattern,
        outDexStoresDir,
        linearAllocLimit,
        requiredInPrimaryZip,
        wantedInPrimaryZip,
        secondaryHeadSet,
        secondaryTailSet,
        additionalDexStoreSets,
        apkModuleGraph,
        dexSplitStrategy,
        canaryStrategy,
        reportDir);
  }
}
