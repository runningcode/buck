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

package com.facebook.buck.js;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.SourcePath;

public class IosReactNativeLibraryBuilder
    extends AbstractNodeBuilder<
        ReactNativeLibraryArg.Builder, ReactNativeLibraryArg, IosReactNativeLibraryDescription,
        BuildRule> {

  protected IosReactNativeLibraryBuilder(
      IosReactNativeLibraryDescription description, BuildTarget target) {
    super(description, target);
  }

  public static IosReactNativeLibraryBuilder builder(
      BuildTarget buildTarget, ReactNativeBuckConfig buckConfig) {
    return new IosReactNativeLibraryBuilder(
        new IosReactNativeLibraryDescription(buckConfig), buildTarget);
  }

  public IosReactNativeLibraryBuilder setBundleName(String bundleName) {
    getArgForPopulating().setBundleName(bundleName);
    return this;
  }

  public IosReactNativeLibraryBuilder setEntryPath(SourcePath path) {
    getArgForPopulating().setEntryPath(path);
    return this;
  }
}
