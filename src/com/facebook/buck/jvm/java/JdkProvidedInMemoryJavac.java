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

package com.facebook.buck.jvm.java;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSortedSet;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class JdkProvidedInMemoryJavac extends Jsr199Javac {

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    sink.setReflectively("javac", "jsr199").setReflectively("javac.version", "in-memory");
  }

  @Override
  public ImmutableCollection<BuildRule> getDeps(SourcePathRuleFinder ruleFinder) {
    return ImmutableSortedSet.of();
  }

  @Override
  public ImmutableCollection<SourcePath> getInputs() {
    return ImmutableSortedSet.of();
  }

  @Override
  protected JavaCompiler createCompiler(JavacExecutionContext context) {
    JavaCompiler compiler;
    synchronized (ToolProvider.class) {
      // ToolProvider has no synchronization internally, so if we don't synchronize from the
      // outside we could wind up loading the compiler classes multiple times from different
      // class loaders.
      compiler = ToolProvider.getSystemJavaCompiler();
    }

    if (compiler == null) {
      throw new HumanReadableException(
          "No system compiler found. Did you install the JRE instead of the JDK?");
    }

    return compiler;
  }
}
