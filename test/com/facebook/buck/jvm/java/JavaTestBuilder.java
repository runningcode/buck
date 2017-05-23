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

package com.facebook.buck.jvm.java;

import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVAC_OPTIONS;
import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVA_CONFIG;
import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVA_OPTIONS;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

public class JavaTestBuilder
    extends AbstractNodeBuilder<
        JavaTestDescriptionArg.Builder, JavaTestDescriptionArg, JavaTestDescription, JavaTest> {
  private JavaTestBuilder(BuildTarget target, JavaBuckConfig javaBuckConfig) {
    super(
        new JavaTestDescription(
            javaBuckConfig,
            DEFAULT_JAVA_OPTIONS,
            DEFAULT_JAVAC_OPTIONS,
            /* testRuleTimeoutMs */ Optional.empty(),
            null),
        target);
  }

  public static JavaTestBuilder createBuilder(BuildTarget target) {
    return new JavaTestBuilder(target, DEFAULT_JAVA_CONFIG);
  }

  public static JavaTestBuilder createBuilder(BuildTarget target, JavaBuckConfig javaBuckConfig) {
    return new JavaTestBuilder(target, javaBuckConfig);
  }

  public JavaTestBuilder addDep(BuildTarget rule) {
    getArgForPopulating().addDeps(rule);
    return this;
  }

  public JavaTestBuilder addProvidedDep(BuildTarget rule) {
    getArgForPopulating().addProvidedDeps(rule);
    return this;
  }

  public JavaTestBuilder addSrc(Path path) {
    getArgForPopulating().addSrcs(new PathSourcePath(new FakeProjectFilesystem(), path));
    return this;
  }

  public JavaTestBuilder setVmArgs(@Nullable ImmutableList<String> vmArgs) {
    getArgForPopulating().setVmArgs(Optional.ofNullable(vmArgs).orElse(ImmutableList.of()));
    return this;
  }
}
