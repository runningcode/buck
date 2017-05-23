/*
 * Copyright 2014-present Facebook, Inc.
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

import com.facebook.buck.jvm.java.DefaultJavaLibrary;
import com.facebook.buck.jvm.java.HasJavaAbi;
import com.facebook.buck.jvm.java.JavaLibrary;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacOptionsAmender;
import com.facebook.buck.jvm.java.JavacToJarStepFactory;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;

/**
 * {@link JavaLibrary} that wraps the output of an {@link AndroidBuildConfig}.
 *
 * <p>This is a custom subclass of {@link DefaultJavaLibrary} so that it can have special behavior
 * when being traversed by an {@link AndroidPackageableCollector}.
 */
class AndroidBuildConfigJavaLibrary extends DefaultJavaLibrary implements AndroidPackageable {

  private final AndroidBuildConfig androidBuildConfig;

  AndroidBuildConfigJavaLibrary(
      BuildRuleParams params,
      SourcePathResolver resolver,
      SourcePathRuleFinder ruleFinder,
      Javac javac,
      JavacOptions javacOptions,
      ImmutableSortedSet<SourcePath> abiInputs,
      AndroidBuildConfig androidBuildConfig) {
    super(
        params.copyAppendingExtraDeps(ruleFinder.filterBuildRuleInputs(abiInputs)),
        resolver,
        ruleFinder,
        /* srcs */ ImmutableSortedSet.of(androidBuildConfig.getSourcePathToOutput()),
        /* resources */ ImmutableSortedSet.of(),
        javacOptions.getGeneratedSourceFolderName(),
        /* proguardConfig */ Optional.empty(),
        /* postprocessClassesCommands */ ImmutableList.of(),
        /* declaredDeps */ params.getDeclaredDeps().get(),
        /* exportedDeps */ ImmutableSortedSet.of(),
        /* providedDeps */ ImmutableSortedSet.of(),
        /* compileTimeClasspathDeps */ ImmutableSortedSet.of(
            androidBuildConfig.getSourcePathToOutput()),
        abiInputs,
        HasJavaAbi.getClassAbiJar(params.getBuildTarget()),
        /* trackClassUsage */ javacOptions.trackClassUsage(),
        new JavacToJarStepFactory(javac, javacOptions, JavacOptionsAmender.IDENTITY),
        /* resourcesRoot */ Optional.empty(),
        /* manifest file */ Optional.empty(),
        /* mavenCoords */ Optional.empty(),
        /* tests */ ImmutableSortedSet.of(),
        /* classesToRemoveFromJar */ ImmutableSet.of());
    this.androidBuildConfig = androidBuildConfig;
    Preconditions.checkState(
        params.getBuildDeps().contains(androidBuildConfig),
        "%s must depend on the AndroidBuildConfig whose output is in this rule's srcs.",
        params.getBuildTarget());
  }

  /**
   * If an {@link AndroidPackageableCollector} is traversing this rule for an {@link AndroidBinary},
   * then it should flag itself as a class that should not be dexed and insert a new classpath entry
   * for a {@code BuildConfig} with the final values for the APK.
   */
  @Override
  public void addToCollector(AndroidPackageableCollector collector) {
    collector.addBuildConfig(
        androidBuildConfig.getJavaPackage(), androidBuildConfig.getBuildConfigFields());
  }

  public AndroidBuildConfig getAndroidBuildConfig() {
    return androidBuildConfig;
  }
}
