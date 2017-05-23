/*
 * Copyright 2015-present Facebook, Inc.
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

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;

public class AndroidBuckConfig {

  private final BuckConfig delegate;
  private final Platform platform;

  public AndroidBuckConfig(BuckConfig delegate, Platform platform) {
    this.delegate = delegate;
    this.platform = platform;
  }

  public Optional<String> getAndroidTarget() {
    return delegate.getValue("android", "target");
  }

  public Optional<String> getBuildToolsVersion() {
    return delegate.getValue("android", "build_tools_version");
  }

  public Optional<String> getNdkVersion() {
    return delegate.getValue("ndk", "ndk_version");
  }

  public Optional<String> getNdkAppPlatform() {
    return delegate.getValue("ndk", "app_platform");
  }

  public Optional<Set<String>> getNdkCpuAbis() {
    return delegate.getOptionalListWithoutComments("ndk", "cpu_abis").map(ImmutableSet::copyOf);
  }

  public Optional<NdkCxxPlatformCompiler.Type> getNdkCompiler() {
    return delegate.getEnum("ndk", "compiler", NdkCxxPlatformCompiler.Type.class);
  }

  public Optional<String> getNdkGccVersion() {
    return delegate.getValue("ndk", "gcc_version");
  }

  public Optional<String> getNdkClangVersion() {
    return delegate.getValue("ndk", "clang_version");
  }

  public Optional<NdkCxxRuntime> getNdkCxxRuntime() {
    return delegate.getEnum("ndk", "cxx_runtime", NdkCxxRuntime.class);
  }

  public ImmutableList<String> getExtraNdkCFlags() {
    return delegate.getListWithoutComments("ndk", "extra_cflags", ' ');
  }

  public ImmutableList<String> getExtraNdkCxxFlags() {
    return delegate.getListWithoutComments("ndk", "extra_cxxflags", ' ');
  }

  public ImmutableList<String> getExtraNdkLdFlags() {
    return delegate.getListWithoutComments("ndk", "extra_ldflags", ' ');
  }

  /**
   * Returns the path to the platform specific aapt executable that is overridden by the current
   * project. If not specified, the Android platform aapt will be used.
   */
  public Optional<Path> getAaptOverride() {
    return getToolOverride("aapt");
  }

  /**
   * Returns the path to the platform specific aapt2 executable that is overridden by the current
   * project. If not specified, the Android platform aapt will be used.
   */
  public Optional<Path> getAapt2Override() {
    return getToolOverride("aapt2");
  }

  private Optional<Path> getToolOverride(String tool) {
    Optional<String> pathString = delegate.getValue("tools", tool);
    if (!pathString.isPresent()) {
      return Optional.empty();
    }

    String platformDir;
    if (platform == Platform.LINUX) {
      platformDir = "linux";
    } else if (platform == Platform.MACOS) {
      platformDir = "mac";
    } else if (platform == Platform.WINDOWS) {
      platformDir = "windows";
    } else {
      return Optional.empty();
    }

    Path pathToTool = Paths.get(pathString.get(), platformDir, tool);
    return delegate.checkPathExists(pathToTool.toString(), "Overridden path not found: ");
  }
}
