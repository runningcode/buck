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

package com.facebook.buck.util;

import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Optional;
import org.immutables.value.Value;

/** Value type passed to {@link ProcessExecutor} to launch a process. */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractProcessExecutorParams {

  public static ProcessExecutorParams ofCommand(String... args) {
    return ProcessExecutorParams.builder().addCommand(args).build();
  }

  /** The command and arguments to launch. */
  @Value.Parameter
  public abstract ImmutableList<String> getCommand();

  /** If present, the current working directory for the launched process. */
  @Value.Parameter
  public abstract Optional<Path> getDirectory();

  /**
   * If present, the map of environment variables used for the launched process. Otherwise, inherits
   * the current process's environment.
   */
  @Value.Parameter
  public abstract Optional<ImmutableMap<String, String>> getEnvironment();

  /**
   * If present, redirects stdout for the process to this location. Otherwise, opens a pipe for
   * stdout.
   */
  @Value.Parameter
  public abstract Optional<ProcessBuilder.Redirect> getRedirectInput();

  /**
   * If present, redirects stdin for the process to this location. Otherwise, opens a pipe for
   * stdin.
   */
  @Value.Parameter
  public abstract Optional<ProcessBuilder.Redirect> getRedirectOutput();

  /**
   * If present, redirects stderr for the process to this location. Otherwise, opens a pipe for
   * stderr.
   */
  @Value.Parameter
  public abstract Optional<ProcessBuilder.Redirect> getRedirectError();

  /*
   * If true, redirects stderr for the process to stdout.
   */
  @Value.Parameter
  public abstract Optional<Boolean> getRedirectErrorStream();
}
