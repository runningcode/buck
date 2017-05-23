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

import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.Verbosity;
import com.facebook.buck.util.concurrent.ConcurrencyLimit;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class NdkBuildStep extends ShellStep {

  private final ProjectFilesystem filesystem;
  private final Path root;
  private final Path makefile;
  private final Path buildArtifactsDirectory;
  private final Path binDirectory;
  private final ImmutableList<String> flags;
  private final Function<String, String> macroExpander;

  public NdkBuildStep(
      ProjectFilesystem filesystem,
      Path root,
      Path makefile,
      Path buildArtifactsDirectory,
      Path binDirectory,
      Iterable<String> flags,
      Function<String, String> macroExpander) {
    super(filesystem.getRootPath());

    this.filesystem = filesystem;
    this.root = root;
    this.makefile = makefile;
    this.buildArtifactsDirectory = buildArtifactsDirectory;
    this.binDirectory = binDirectory;
    this.flags = ImmutableList.copyOf(flags);
    this.macroExpander = macroExpander;
  }

  @Override
  public String getShortName() {
    return "ndk_build";
  }

  @Override
  protected boolean shouldPrintStderr(Verbosity verbosity) {
    return verbosity.shouldPrintStandardInformation();
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    Optional<Path> ndkBuild =
        new ExecutableFinder()
            .getOptionalExecutable(
                Paths.get("ndk-build"), context.getAndroidPlatformTarget().checkNdkDirectory());
    if (!ndkBuild.isPresent()) {
      throw new HumanReadableException("Unable to find ndk-build");
    }

    ConcurrencyLimit concurrencyLimit = context.getConcurrencyLimit();

    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.add(
        ndkBuild.get().toAbsolutePath().toString(),
        "-j",
        // TODO(dancol): using -j here is wrong.  It lets make run too many work when we do
        // other work in parallel.  Instead, implement the GNU Make job server so make and Buck can
        // coordinate job concurrency.
        Integer.toString(concurrencyLimit.threadLimit),
        "-C",
        this.root.toString());

    Iterable<String> flags = Iterables.transform(this.flags, macroExpander);
    builder.addAll(flags);

    // We want relative, not absolute, paths in the debug-info for binaries we build using
    // ndk_library.  Absolute paths are machine-specific, but relative ones should be the
    // same everywhere.

    Path relativePathToProject = filesystem.resolve(root).relativize(filesystem.getRootPath());
    builder.add(
        "APP_PROJECT_PATH=" + filesystem.resolve(buildArtifactsDirectory) + File.separatorChar,
        "APP_BUILD_SCRIPT=" + filesystem.resolve(makefile),
        "NDK_OUT=" + filesystem.resolve(buildArtifactsDirectory) + File.separatorChar,
        "NDK_LIBS_OUT=" + filesystem.resolve(binDirectory),
        "BUCK_PROJECT_DIR=" + relativePathToProject);

    // Suppress the custom build step messages (e.g. "Compile++ ...").
    if (Platform.detect() == Platform.WINDOWS) {
      builder.add("host-echo-build-step=@REM");
    } else {
      builder.add("host-echo-build-step=@#");
    }

    // If we're running verbosely, force all the subcommands from the ndk build to be printed out.
    if (context.getVerbosity().shouldPrintCommand()) {
      builder.add("V=1");
      // Otherwise, suppress everything, including the "make: entering directory..." messages.
    } else {
      builder.add("--silent");
    }

    return builder.build();
  }

  // The ndk-build command delegates to `make` to run a lot of subcommands, so print them as they
  // happen.
  @Override
  protected boolean shouldFlushStdOutErrAsProgressIsMade(Verbosity verbosity) {
    return verbosity.shouldPrintCommand();
  }
}
