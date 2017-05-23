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

package com.facebook.buck.android;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.SourcePath;
import javax.annotation.Nullable;

/** Indicates that this class may have android resources that should be packaged into an APK. */
public interface HasAndroidResourceDeps {

  BuildTarget getBuildTarget();

  /** @return the package name in which to generate the R.java representing these resources. */
  String getRDotJavaPackage();

  /** @return path to a temporary directory for storing text symbols. */
  SourcePath getPathToTextSymbolsFile();

  /** @return path to a file containing the package name for R.java. */
  SourcePath getPathToRDotJavaPackageFile();

  /** @return path to a directory containing Android resources. */
  @Nullable
  SourcePath getRes();

  /** @return path to a directory containing Android assets. */
  @Nullable
  SourcePath getAssets();
}
