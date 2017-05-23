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
package com.facebook.buck.cxx;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/** Encapsulates all the logic to sanitize debug paths in native code. */
public abstract class DebugPathSanitizer {
  protected final int pathSize;
  protected final char separator;
  protected final Path compilationDirectory;

  public DebugPathSanitizer(char separator, int pathSize, Path compilationDirectory) {
    this.separator = separator;
    this.pathSize = pathSize;
    this.compilationDirectory = compilationDirectory;
  }

  /**
   * @return the given path as a string, expanded using {@code separator} to fulfill the required
   *     {@code pathSize}.
   */
  protected String getExpandedPath(Path path) {
    Preconditions.checkArgument(
        path.toString().length() <= pathSize,
        String.format(
            "Path is too long to sanitize:\n'%s' is %d characters long, limit is %d.",
            path, path.toString().length(), pathSize));
    return Strings.padEnd(path.toString(), pathSize, separator);
  }

  abstract ImmutableMap<String, String> getCompilationEnvironment(
      Path workingDir, boolean shouldSanitize);

  abstract ImmutableList<String> getCompilationFlags();

  protected abstract ImmutableBiMap<Path, Path> getAllPaths(Optional<Path> workingDir);

  public String getCompilationDirectory() {
    return getExpandedPath(compilationDirectory);
  }

  public Function<String, String> sanitize(final Optional<Path> workingDir) {
    return input -> DebugPathSanitizer.this.sanitize(workingDir, input);
  }

  public ImmutableList<String> sanitizeFlags(Iterable<String> flags) {
    return StreamSupport.stream(flags.spliterator(), false)
        .map(sanitize(Optional.empty())::apply)
        .collect(MoreCollectors.toImmutableList());
  }

  /**
   * @param workingDir the current working directory, if applicable.
   * @param contents the string to sanitize.
   * @return a string with all matching paths replaced with their sanitized versions.
   */
  public String sanitize(Optional<Path> workingDir, String contents) {
    for (Map.Entry<Path, Path> entry : getAllPaths(workingDir).entrySet()) {
      String replacement = entry.getValue().toString();
      String pathToReplace = entry.getKey().toString();
      if (contents.contains(pathToReplace)) {
        // String.replace creates a number of objects, and creates a fair
        // amount of object churn at this level, so we avoid doing it if
        // it's essentially going to be a no-op.
        contents = contents.replace(pathToReplace, replacement);
      }
    }
    return contents;
  }

  public String restore(Optional<Path> workingDir, String contents) {
    for (Map.Entry<Path, Path> entry : getAllPaths(workingDir).entrySet()) {
      contents = contents.replace(getExpandedPath(entry.getValue()), entry.getKey().toString());
    }
    return contents;
  }

  // Construct the replacer, giving the expanded current directory and the desired directory.
  // We use ASCII, since all the relevant debug standards we care about (e.g. DWARF) use it.
  abstract void restoreCompilationDirectory(Path path, Path workingDir) throws IOException;

  /**
   * Offensive check for cross-cell builds: return a new sanitizer with the provided
   * ProjectFilesystem
   */
  @SuppressWarnings("unused")
  public DebugPathSanitizer withProjectFilesystem(ProjectFilesystem projectFilesystem) {
    // Do nothing by default.  Only one subclass uses this right now.
    return this;
  }
}
