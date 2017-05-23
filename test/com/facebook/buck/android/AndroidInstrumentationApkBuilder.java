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

import static com.facebook.buck.jvm.java.JavaCompilationConstants.ANDROID_JAVAC_OPTIONS;
import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVA_CONFIG;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.SourcePath;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;

public class AndroidInstrumentationApkBuilder
    extends AbstractNodeBuilder<
        AndroidInstrumentationApkDescriptionArg.Builder, AndroidInstrumentationApkDescriptionArg,
        AndroidInstrumentationApkDescription, AndroidInstrumentationApk> {

  private AndroidInstrumentationApkBuilder(BuildTarget target) {
    super(
        new AndroidInstrumentationApkDescription(
            DEFAULT_JAVA_CONFIG,
            new ProGuardConfig(FakeBuckConfig.builder().build()),
            ANDROID_JAVAC_OPTIONS,
            ImmutableMap.of(),
            MoreExecutors.newDirectExecutorService(),
            new CxxBuckConfig(new FakeBuckConfig.Builder().build()),
            new DxConfig(FakeBuckConfig.builder().build())),
        target);
  }

  public static AndroidInstrumentationApkBuilder createBuilder(BuildTarget buildTarget) {
    return new AndroidInstrumentationApkBuilder(buildTarget);
  }

  public AndroidInstrumentationApkBuilder setManifest(SourcePath manifest) {
    getArgForPopulating().setManifest(manifest);
    return this;
  }

  public AndroidInstrumentationApkBuilder setApk(BuildTarget apk) {
    getArgForPopulating().setApk(apk);
    return this;
  }
}
