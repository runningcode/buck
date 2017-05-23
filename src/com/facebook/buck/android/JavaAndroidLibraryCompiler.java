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

package com.facebook.buck.android;

import com.facebook.buck.jvm.java.CompileToJarStepFactory;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.jvm.java.JavacFactory;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacToJarStepFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;

public class JavaAndroidLibraryCompiler extends AndroidLibraryCompiler {
  private final JavaBuckConfig javaBuckConfig;

  public JavaAndroidLibraryCompiler(JavaBuckConfig javaBuckConfig) {
    this.javaBuckConfig = javaBuckConfig;
  }

  @Override
  public CompileToJarStepFactory compileToJar(
      AndroidLibraryDescription.CoreArg arg,
      JavacOptions javacOptions,
      BuildRuleResolver resolver) {

    return new JavacToJarStepFactory(
        getJavac(resolver, arg), javacOptions, new BootClasspathAppender());
  }

  private Javac getJavac(BuildRuleResolver resolver, AndroidLibraryDescription.CoreArg arg) {
    return JavacFactory.create(new SourcePathRuleFinder(resolver), javaBuckConfig, arg);
  }
}
