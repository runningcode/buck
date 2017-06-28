/*
 * Copyright 2017-present Facebook, Inc.
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

import static com.facebook.buck.jvm.java.BaseCompileToJarStepFactory.EMPTY_EXTRA_CLASSPATH;

import com.facebook.buck.jvm.java.CompileToJarStepFactory;
import com.facebook.buck.jvm.java.DefaultJavaLibraryBuilder;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.jvm.java.JavaLibraryDescription;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class KotlinLibraryBuilder extends DefaultJavaLibraryBuilder {
  private final KotlinBuckConfig kotlinBuckConfig;
  private ImmutableList<String> extraKotlincArguments = ImmutableList.of();

  public KotlinLibraryBuilder(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver buildRuleResolver,
      CellPathResolver cellRoots,
      KotlinBuckConfig kotlinBuckConfig,
      JavaBuckConfig javaBuckConfig) {
    super(targetGraph, params, buildRuleResolver, cellRoots, javaBuckConfig);
    this.kotlinBuckConfig = kotlinBuckConfig;
    setCompileAgainstAbis(false);
  }

  @Override
  public KotlinLibraryBuilder setArgs(JavaLibraryDescription.CoreArg args) {
    super.setArgs(args);

    KotlinLibraryDescription.CoreArg kotlinArgs = (KotlinLibraryDescription.CoreArg) args;
    extraKotlincArguments = kotlinArgs.getExtraKotlincArguments();
    return this;
  }

  @Override
  public KotlinLibraryBuilder setJavacOptions(JavacOptions javacOptions) {
    this.javacOptions = javacOptions;
    return this;
  }

  @Override
  protected BuilderHelper newHelper() {
    return new BuilderHelper();
  }

  protected class BuilderHelper extends DefaultJavaLibraryBuilder.BuilderHelper {
    @Override
    protected CompileToJarStepFactory buildCompileStepFactory() {
      return new KotlincToJarStepFactory(
          Preconditions.checkNotNull(kotlinBuckConfig).getKotlinc(),
          extraKotlincArguments,
          EMPTY_EXTRA_CLASSPATH,
          getJavac(),
          Preconditions.checkNotNull(javacOptions),
          javacOptionsAmender);
    }
  }
}
