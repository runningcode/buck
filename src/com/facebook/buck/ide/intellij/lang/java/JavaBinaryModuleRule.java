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
package com.facebook.buck.ide.intellij.lang.java;

import com.facebook.buck.ide.intellij.BaseIjModuleRule;
import com.facebook.buck.ide.intellij.ModuleBuildContext;
import com.facebook.buck.ide.intellij.model.DependencyType;
import com.facebook.buck.ide.intellij.model.IjModuleFactoryResolver;
import com.facebook.buck.ide.intellij.model.IjModuleType;
import com.facebook.buck.ide.intellij.model.IjProjectConfig;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaBinaryDescription;
import com.facebook.buck.jvm.java.JavaBinaryDescriptionArg;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.TargetNode;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;

public class JavaBinaryModuleRule extends BaseIjModuleRule<JavaBinaryDescriptionArg> {

  public JavaBinaryModuleRule(
      ProjectFilesystem projectFilesystem,
      IjModuleFactoryResolver moduleFactoryResolver,
      IjProjectConfig projectConfig) {
    super(projectFilesystem, moduleFactoryResolver, projectConfig);
  }

  @Override
  public Class<? extends Description<?>> getDescriptionClass() {
    return JavaBinaryDescription.class;
  }

  @Override
  public void apply(TargetNode<JavaBinaryDescriptionArg, ?> target, ModuleBuildContext context) {
    context.addDeps(target.getBuildDeps(), DependencyType.PROD);
    saveMetaInfDirectoryForIntellijPlugin(target, context);
  }

  private void saveMetaInfDirectoryForIntellijPlugin(
      TargetNode<JavaBinaryDescriptionArg, ?> target, ModuleBuildContext context) {
    ImmutableSet<String> intellijPluginLabels = projectConfig.getIntellijPluginLabels();
    if (intellijPluginLabels.isEmpty()) {
      return;
    }
    Optional<Path> metaInfDirectory = target.getConstructorArg().getMetaInfDirectory();
    if (metaInfDirectory.isPresent()
        && target.getConstructorArg().labelsContainsAnyOf(intellijPluginLabels)) {
      context.setMetaInfDirectory(metaInfDirectory.get());
    }
  }

  @Override
  public IjModuleType detectModuleType(TargetNode<JavaBinaryDescriptionArg, ?> target) {
    ImmutableSet<String> intellijPluginLabels = projectConfig.getIntellijPluginLabels();
    if (intellijPluginLabels.isEmpty()) {
      return IjModuleType.JAVA_MODULE;
    }
    Optional<Path> metaInfDirectory = target.getConstructorArg().getMetaInfDirectory();
    if (metaInfDirectory.isPresent()
        && target.getConstructorArg().labelsContainsAnyOf(intellijPluginLabels)) {
      return IjModuleType.INTELLIJ_PLUGIN_MODULE;
    }
    return IjModuleType.JAVA_MODULE;
  }
}
