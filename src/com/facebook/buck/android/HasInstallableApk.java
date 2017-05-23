/*
 * Copyright 2012-present Facebook, Inc.
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

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.ExopackageInfo;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Build rule that generates an APK that can be installed with the install command.
 *
 * @see com.facebook.buck.cli.InstallCommand
 */
public interface HasInstallableApk extends BuildRule {

  ApkInfo getApkInfo();

  @Override
  SourcePath getSourcePathToOutput();

  @Value.Immutable
  @BuckStyleImmutable
  abstract class AbstractApkInfo {
    /**
     * @return the path to the AndroidManifest.xml. Note that this file might be a symlink, and
     *     might not exist at all before this rule has been built.
     */
    public abstract SourcePath getManifestPath();

    /**
     * @return The APK at this path is the final one that points to an APK that a user should
     *     install.
     */
    public abstract SourcePath getApkPath();

    public abstract Optional<ExopackageInfo> getExopackageInfo();
  }
}
