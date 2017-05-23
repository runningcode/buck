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
package com.facebook.buck.apple;

import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.immutables.value.Value;

/** Resources to be bundled into a bundle. */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractAppleBundleResources implements RuleKeyAppendable {
  /**
   * Directories that should be copied into the bundle as directories of files with the same name.
   */
  public abstract ImmutableSet<SourcePath> getResourceDirs();

  /**
   * Directories whose contents should be copied into the root of the resources subdirectory.
   *
   * <p>This is useful when the directory contents are not known beforehand, such as when a rule
   * generates a directory of files.
   */
  public abstract ImmutableSet<SourcePath> getDirsContainingResourceDirs();

  /** Files that are copied to the root of the resources subdirectory. */
  public abstract ImmutableSet<SourcePath> getResourceFiles();

  /** Resource files with localization variants. */
  public abstract ImmutableSet<SourcePath> getResourceVariantFiles();

  /** Returns all the SourcePaths from the different types of resources. */
  public Iterable<SourcePath> getAll() {
    return Iterables.concat(
        getResourceDirs(),
        getDirsContainingResourceDirs(),
        getResourceFiles(),
        getResourceVariantFiles());
  }

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    sink.setReflectively("resourceDirs", getResourceDirs())
        .setReflectively("dirContainingResourceDirs", getDirsContainingResourceDirs())
        .setReflectively("resourceFiles", getResourceFiles())
        .setReflectively("resourceVariantFiles", getResourceVariantFiles());
  }
}
