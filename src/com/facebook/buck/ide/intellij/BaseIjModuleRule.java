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
package com.facebook.buck.ide.intellij;

import com.facebook.buck.ide.intellij.aggregation.AggregationContext;
import com.facebook.buck.ide.intellij.model.DependencyType;
import com.facebook.buck.ide.intellij.model.IjModule;
import com.facebook.buck.ide.intellij.model.IjModuleFactoryResolver;
import com.facebook.buck.ide.intellij.model.IjModuleRule;
import com.facebook.buck.ide.intellij.model.IjProjectConfig;
import com.facebook.buck.ide.intellij.model.folders.IJFolderFactory;
import com.facebook.buck.ide.intellij.model.folders.SourceFolder;
import com.facebook.buck.ide.intellij.model.folders.TestFolder;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.JvmLibraryArg;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public abstract class BaseIjModuleRule<T extends CommonDescriptionArg> implements IjModuleRule<T> {

  protected final ProjectFilesystem projectFilesystem;
  protected final IjModuleFactoryResolver moduleFactoryResolver;
  protected final IjProjectConfig projectConfig;

  protected BaseIjModuleRule(
      ProjectFilesystem projectFilesystem,
      IjModuleFactoryResolver moduleFactoryResolver,
      IjProjectConfig projectConfig) {
    this.projectFilesystem = projectFilesystem;
    this.moduleFactoryResolver = moduleFactoryResolver;
    this.projectConfig = projectConfig;
  }

  /**
   * Calculate the set of directories containing inputs to the target.
   *
   * @param paths inputs to a given target.
   * @return index of path to set of inputs in that path
   */
  protected static ImmutableMultimap<Path, Path> getSourceFoldersToInputsIndex(
      ImmutableSet<Path> paths) {
    Path defaultParent = Paths.get("");
    return paths
        .stream()
        .collect(
            MoreCollectors.toImmutableMultimap(
                path -> {
                  Path parent = path.getParent();
                  return parent == null ? defaultParent : parent;
                },
                path -> path));
  }

  /**
   * Add the set of input paths to the {@link IjModule.Builder} as source folders.
   *
   * @param foldersToInputsIndex mapping of source folders to their inputs.
   * @param wantsPackagePrefix whether folders should be annotated with a package prefix. This only
   *     makes sense when the source folder is Java source code.
   * @param context the module to add the folders to.
   */
  protected void addSourceFolders(
      IJFolderFactory factory,
      ImmutableMultimap<Path, Path> foldersToInputsIndex,
      boolean wantsPackagePrefix,
      ModuleBuildContext context) {
    for (Map.Entry<Path, Collection<Path>> entry : foldersToInputsIndex.asMap().entrySet()) {
      context.addSourceFolder(
          factory.create(
              entry.getKey(),
              wantsPackagePrefix,
              ImmutableSortedSet.copyOf(Ordering.natural(), entry.getValue())));
    }
  }

  private void addDepsAndFolder(
      IJFolderFactory folderFactory,
      DependencyType dependencyType,
      TargetNode<T, ?> targetNode,
      boolean wantsPackagePrefix,
      ModuleBuildContext context) {
    ImmutableMultimap<Path, Path> foldersToInputsIndex =
        getSourceFoldersToInputsIndex(targetNode.getInputs());
    addSourceFolders(folderFactory, foldersToInputsIndex, wantsPackagePrefix, context);
    addDeps(foldersToInputsIndex, targetNode, dependencyType, context);

    addGeneratedOutputIfNeeded(folderFactory, targetNode, context);

    if (targetNode.getConstructorArg() instanceof JvmLibraryArg) {
      addAnnotationOutputIfNeeded(folderFactory, targetNode, context);
    }
  }

  protected void addDepsAndSources(
      TargetNode<T, ?> targetNode, boolean wantsPackagePrefix, ModuleBuildContext context) {
    addDepsAndFolder(
        SourceFolder.FACTORY, DependencyType.PROD, targetNode, wantsPackagePrefix, context);
  }

  protected void addDepsAndTestSources(
      TargetNode<T, ?> targetNode, boolean wantsPackagePrefix, ModuleBuildContext context) {
    addDepsAndFolder(
        TestFolder.FACTORY, DependencyType.TEST, targetNode, wantsPackagePrefix, context);
  }

  private void addDeps(
      ImmutableMultimap<Path, Path> foldersToInputsIndex,
      TargetNode<T, ?> targetNode,
      DependencyType dependencyType,
      ModuleBuildContext context) {
    context.addDeps(foldersToInputsIndex.keySet(), targetNode.getBuildDeps(), dependencyType);
  }

  @SuppressWarnings("unchecked")
  private void addAnnotationOutputIfNeeded(
      IJFolderFactory folderFactory, TargetNode<T, ?> targetNode, ModuleBuildContext context) {
    TargetNode<? extends JvmLibraryArg, ?> jvmLibraryTargetNode =
        (TargetNode<? extends JvmLibraryArg, ?>) targetNode;

    Optional<Path> annotationOutput =
        moduleFactoryResolver.getAnnotationOutputPath(jvmLibraryTargetNode);
    if (!annotationOutput.isPresent()) {
      return;
    }

    Path annotationOutputPath = annotationOutput.get();
    context.addGeneratedSourceCodeFolder(
        folderFactory.create(
            annotationOutputPath, false, ImmutableSortedSet.of(annotationOutputPath)));
  }

  private void addGeneratedOutputIfNeeded(
      IJFolderFactory folderFactory, TargetNode<T, ?> targetNode, ModuleBuildContext context) {

    ImmutableSet<Path> generatedSourcePaths = findConfiguredGeneratedSourcePaths(targetNode);

    for (Path generatedSourcePath : generatedSourcePaths) {
      context.addGeneratedSourceCodeFolder(
          folderFactory.create(
              generatedSourcePath, false, ImmutableSortedSet.of(generatedSourcePath)));
    }
  }

  private ImmutableSet<Path> findConfiguredGeneratedSourcePaths(TargetNode<T, ?> targetNode) {
    ImmutableSet.Builder<Path> generatedSourcePaths = ImmutableSet.builder();

    generatedSourcePaths.addAll(findConfiguredGeneratedSourcePathsUsingDeps(targetNode));
    generatedSourcePaths.addAll(findConfiguredGeneratedSourcePathsUsingLabels(targetNode));

    return generatedSourcePaths.build();
  }

  private Set<Path> findConfiguredGeneratedSourcePathsUsingDeps(TargetNode<T, ?> targetNode) {
    ImmutableMap<String, String> depToGeneratedSourcesMap =
        projectConfig.getDepToGeneratedSourcesMap();
    BuildTarget buildTarget = targetNode.getBuildTarget();

    Set<Path> generatedSourcePaths = new HashSet<>();

    for (BuildTarget dependencyTarget : targetNode.getBuildDeps()) {
      String buildTargetName = dependencyTarget.toString();
      String generatedSourceWithPattern = depToGeneratedSourcesMap.get(buildTargetName);
      if (generatedSourceWithPattern != null) {
        String generatedSource =
            generatedSourceWithPattern.replaceAll(
                "%name%", buildTarget.getShortNameAndFlavorPostfix());
        Path generatedSourcePath =
            BuildTargets.getGenPath(projectFilesystem, buildTarget, generatedSource);

        generatedSourcePaths.add(generatedSourcePath);
      }
    }

    return generatedSourcePaths;
  }

  private ImmutableSet<Path> findConfiguredGeneratedSourcePathsUsingLabels(
      TargetNode<T, ?> targetNode) {
    BuildTarget buildTarget = targetNode.getBuildTarget();
    ImmutableMap<String, String> labelToGeneratedSourcesMap =
        projectConfig.getLabelToGeneratedSourcesMap();

    return targetNode
        .getConstructorArg()
        .getLabels()
        .stream()
        .map(labelToGeneratedSourcesMap::get)
        .filter(Objects::nonNull)
        .map(pattern -> pattern.replaceAll("%name%", buildTarget.getShortNameAndFlavorPostfix()))
        .map(path -> BuildTargets.getGenPath(projectFilesystem, buildTarget, path))
        .collect(MoreCollectors.toImmutableSet());
  }

  @Override
  public void applyDuringAggregation(AggregationContext context, TargetNode<T, ?> targetNode) {
    context.setModuleType(detectModuleType(targetNode));
  }
}
