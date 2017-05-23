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

package com.facebook.buck.android.redex;

import com.facebook.buck.android.KeystoreProperties;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Runs <a href="https://github.com/facebook/redex">ReDex</a> on an APK. */
public class ReDexStep extends ShellStep {
  private final ImmutableList<String> redexBinaryArgs;
  private final ImmutableMap<String, String> redexEnvironmentVariables;
  private final Path inputApkPath;
  private final Path outputApkPath;
  private final Supplier<KeystoreProperties> keystorePropertiesSupplier;
  private final Optional<Path> redexConfig;
  private final ImmutableList<Arg> redexExtraArgs;
  private final Path proguardMap;
  private final Path proguardCommandLine;
  private final Path seeds;
  private final SourcePathResolver pathResolver;

  @VisibleForTesting
  ReDexStep(
      Path workingDirectory,
      List<String> redexBinaryArgs,
      Map<String, String> redexEnvironmentVariables,
      Path inputApkPath,
      Path outputApkPath,
      Supplier<KeystoreProperties> keystorePropertiesSupplier,
      Optional<Path> redexConfig,
      ImmutableList<Arg> redexExtraArgs,
      Path proguardMap,
      Path proguardCommandLine,
      Path seeds,
      SourcePathResolver pathResolver) {
    super(workingDirectory);
    this.redexBinaryArgs = ImmutableList.copyOf(redexBinaryArgs);
    this.redexEnvironmentVariables = ImmutableMap.copyOf(redexEnvironmentVariables);
    this.inputApkPath = inputApkPath;
    this.outputApkPath = outputApkPath;
    this.keystorePropertiesSupplier = keystorePropertiesSupplier;
    this.redexConfig = redexConfig;
    this.redexExtraArgs = redexExtraArgs;
    this.proguardMap = proguardMap;
    this.proguardCommandLine = proguardCommandLine;
    this.seeds = seeds;
    this.pathResolver = pathResolver;
  }

  public static ImmutableList<Step> createSteps(
      final ProjectFilesystem filesystem,
      SourcePathResolver resolver,
      RedexOptions redexOptions,
      Path inputApkPath,
      Path outputApkPath,
      Supplier<KeystoreProperties> keystorePropertiesSupplier,
      Path proguardConfigDir,
      BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    Tool redexBinary = redexOptions.getRedex();
    ReDexStep redexStep =
        new ReDexStep(
            filesystem.getRootPath(),
            redexBinary.getCommandPrefix(resolver),
            redexBinary.getEnvironment(resolver),
            inputApkPath,
            outputApkPath,
            keystorePropertiesSupplier,
            redexOptions.getRedexConfig().map(resolver::getAbsolutePath),
            redexOptions.getRedexExtraArgs(),
            proguardConfigDir.resolve("mapping.txt"),
            proguardConfigDir.resolve("command-line.txt"),
            proguardConfigDir.resolve("seeds.txt"),
            resolver);
    steps.add(redexStep);

    Path outputDir = outputApkPath.getParent();
    buildableContext.recordArtifact(outputDir);

    return steps.build();
  }

  @Override
  public String getShortName() {
    return "redex";
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    ImmutableList.Builder<String> args = ImmutableList.<String>builder();

    // In practice, redexBinaryArgs is likely to be a single argument, which is the path to the
    // ReDex binary.
    args.addAll(redexBinaryArgs);

    // Config is optional.
    if (redexConfig.isPresent()) {
      args.add("--config", redexConfig.get().toString());
    }

    // Signing args.
    KeystoreProperties keystoreProperties = keystorePropertiesSupplier.get();
    args.add("--sign");
    args.add(
        "--keystore", keystoreProperties.getKeystore().toString(),
        "--keyalias", keystoreProperties.getAlias(),
        "--keypass", keystoreProperties.getKeypass());

    // Proguard args.
    args.add("--proguard-map", proguardMap.toString());
    args.add("-P", proguardCommandLine.toString());

    args.add("--keep", seeds.toString());

    args.add("--out", outputApkPath.toString());

    args.addAll(Arg.stringify(redexExtraArgs, pathResolver));

    args.add(inputApkPath.toString());
    return args.build();
  }

  @Override
  public ImmutableMap<String, String> getEnvironmentVariables(ExecutionContext context) {
    return ImmutableMap.<String, String>builder()
        .put("ANDROID_SDK", context.getAndroidPlatformTarget().checkSdkDirectory().toString())
        .putAll(redexEnvironmentVariables)
        .build();
  }
}
