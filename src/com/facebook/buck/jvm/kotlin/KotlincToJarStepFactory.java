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

package com.facebook.buck.jvm.kotlin;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.BaseCompileToJarStepFactory;
import com.facebook.buck.jvm.java.ClassUsageFileWriter;
import com.facebook.buck.jvm.java.ClasspathChecker;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacStep;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Optional;
import java.util.stream.Collectors;

public class KotlincToJarStepFactory extends BaseCompileToJarStepFactory {

  private static final PathMatcher JAVA_PATH_MATCHER = FileSystems.getDefault()
            .getPathMatcher("glob:**.java");

  private final Tool kotlinc;
  private final ImmutableList<String> extraArguments;
  private final JavacOptions javacOptions;
  private final Function<BuildContext, Iterable<Path>> extraClassPath;
  private final Javac javac;

  public KotlincToJarStepFactory(Tool kotlinc, ImmutableList<String> extraArguments, Javac javac, JavacOptions javacOptions) {
    this(kotlinc, extraArguments, javac, javacOptions, EMPTY_EXTRA_CLASSPATH);
  }

  public KotlincToJarStepFactory(
      Tool kotlinc,
      ImmutableList<String> extraArguments,
      Javac javac,
      JavacOptions javacOptions,
      Function<BuildContext, Iterable<Path>> extraClassPath) {
    this.kotlinc = kotlinc;
    this.extraArguments = extraArguments;
    this.javac = javac;
    this.javacOptions = javacOptions;
    this.extraClassPath = extraClassPath;
  }

  @Override
  public void createCompileStep(
      BuildContext context,
      ImmutableSortedSet<Path> sourceFilePaths,
      BuildTarget invokingRule,
      SourcePathResolver resolver,
      SourcePathRuleFinder ruleFinder,
      ProjectFilesystem filesystem,
      ImmutableSortedSet<Path> declaredClasspathEntries,
      Path outputDirectory,
      Optional<Path> workingDirectory,
      Path pathToSrcsList,
      ClassUsageFileWriter usedClassesFileWriter,
      /* out params */
      ImmutableList.Builder<Step> steps,
      BuildableContext buildableContext) {
    steps.add(
        new KotlincStep(
            kotlinc,
            extraArguments,
            resolver,
            outputDirectory,
            sourceFilePaths,
            ImmutableSortedSet.<Path>naturalOrder()
                .addAll(
                    Optional.ofNullable(extraClassPath.apply(context)).orElse(ImmutableList.of()))
                .addAll(declaredClasspathEntries)
                .build(),
            filesystem));

       ImmutableSortedSet<Path> javaSourceFiles = ImmutableSortedSet.copyOf(
        sourceFilePaths
            .stream()
            .filter(JAVA_PATH_MATCHER::matches)
            .collect(Collectors.toSet()));

    // Don't invoke javac if we don't have any java files.
    if (!javaSourceFiles.isEmpty()) {
      steps.add(new JavacStep(
          outputDirectory,
          usedClassesFileWriter,
          workingDirectory,
          javaSourceFiles,
          pathToSrcsList,
          ImmutableSortedSet.<Path>naturalOrder()
              .add(outputDirectory)
              .addAll(declaredClasspathEntries)
              .build(),
          javac,
          javacOptions,
          invokingRule,
          resolver,
          filesystem,
          new ClasspathChecker(),
          Optional.empty()
      ));
    }
  }

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    kotlinc.appendToRuleKey(sink);
    sink.setReflectively("extraArguments", extraArguments);
  }

  @Override
  protected Tool getCompiler() {
    return kotlinc;
  }
}
