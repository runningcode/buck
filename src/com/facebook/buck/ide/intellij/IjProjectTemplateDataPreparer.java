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

import com.facebook.buck.ide.intellij.lang.android.AndroidResourceFolder;
import com.facebook.buck.ide.intellij.model.ContentRoot;
import com.facebook.buck.ide.intellij.model.DependencyType;
import com.facebook.buck.ide.intellij.model.IjLibrary;
import com.facebook.buck.ide.intellij.model.IjModule;
import com.facebook.buck.ide.intellij.model.IjModuleAndroidFacet;
import com.facebook.buck.ide.intellij.model.IjModuleType;
import com.facebook.buck.ide.intellij.model.IjProjectElement;
import com.facebook.buck.ide.intellij.model.ModuleIndexEntry;
import com.facebook.buck.ide.intellij.model.folders.ExcludeFolder;
import com.facebook.buck.ide.intellij.model.folders.IjFolder;
import com.facebook.buck.ide.intellij.model.folders.IjSourceFolder;
import com.facebook.buck.ide.intellij.model.folders.TestFolder;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.core.JavaPackageFinder;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Does the converting of abstract data structures to a format immediately consumable by the
 * StringTemplate-based templates employed by {@link IjProjectWriter}. This is a separate class
 * mainly for testing convenience.
 */
@VisibleForTesting
public class IjProjectTemplateDataPreparer {
  private static final String ANDROID_MANIFEST_TEMPLATE_PARAMETER = "android_manifest";
  private static final String APK_PATH_TEMPLATE_PARAMETER = "apk_path";
  private static final String ASSETS_FOLDER_TEMPLATE_PARAMETER = "asset_folder";
  private static final String PROGUARD_CONFIG_TEMPLATE_PARAMETER = "proguard_config";
  private static final String RESOURCES_RELATIVE_PATH_TEMPLATE_PARAMETER = "res";

  private static final String EMPTY_STRING = "";

  private JavaPackageFinder javaPackageFinder;
  private IjModuleGraph moduleGraph;
  private ProjectFilesystem projectFilesystem;
  private IjSourceRootSimplifier sourceRootSimplifier;
  private ImmutableSet<Path> referencedFolderPaths;
  private ImmutableSet<Path> filesystemTraversalBoundaryPaths;
  private ImmutableSet<IjModule> modulesToBeWritten;
  private ImmutableSet<IjLibrary> librariesToBeWritten;

  public IjProjectTemplateDataPreparer(
      JavaPackageFinder javaPackageFinder,
      IjModuleGraph moduleGraph,
      ProjectFilesystem projectFilesystem) {
    this.javaPackageFinder = javaPackageFinder;
    this.moduleGraph = moduleGraph;
    this.projectFilesystem = projectFilesystem;
    this.sourceRootSimplifier = new IjSourceRootSimplifier(javaPackageFinder);
    this.modulesToBeWritten = createModulesToBeWritten(moduleGraph);
    this.librariesToBeWritten = moduleGraph.getLibraries();
    this.filesystemTraversalBoundaryPaths =
        createFilesystemTraversalBoundaryPathSet(modulesToBeWritten);
    this.referencedFolderPaths = createReferencedFolderPathsSet(modulesToBeWritten);
  }

  private static void addPathAndParents(Set<Path> pathSet, Path path) {
    do {
      pathSet.add(path);
      path = path.getParent();
    } while (path != null && !pathSet.contains(path));
  }

  public static ImmutableSet<Path> createReferencedFolderPathsSet(ImmutableSet<IjModule> modules) {
    Set<Path> pathSet = new HashSet<>();
    for (IjModule module : modules) {
      addPathAndParents(pathSet, module.getModuleBasePath());
      for (IjFolder folder : module.getFolders()) {
        addPathAndParents(pathSet, folder.getPath());
      }
    }
    return ImmutableSet.copyOf(pathSet);
  }

  public static ImmutableSet<Path> createFilesystemTraversalBoundaryPathSet(
      ImmutableSet<IjModule> modules) {
    return Stream.concat(
            modules.stream().map(IjModule::getModuleBasePath),
            Stream.of(IjProjectPaths.IDEA_CONFIG_DIR))
        .collect(MoreCollectors.toImmutableSet());
  }

  public static ImmutableSet<Path> createPackageLookupPathSet(IjModuleGraph moduleGraph) {
    ImmutableSet.Builder<Path> builder = ImmutableSet.builder();

    for (IjModule module : moduleGraph.getModules()) {
      for (IjFolder folder : module.getFolders()) {
        if (!folder.getWantsPackagePrefix()) {
          continue;
        }
        Optional<Path> firstJavaFile =
            folder
                .getInputs()
                .stream()
                .filter(input -> input.getFileName().toString().endsWith(".java"))
                .findFirst();
        if (firstJavaFile.isPresent()) {
          builder.add(firstJavaFile.get());
        }
      }
    }

    return builder.build();
  }

  private static ImmutableSet<IjModule> createModulesToBeWritten(IjModuleGraph graph) {
    Path rootModuleBasePath = Paths.get("");
    boolean hasRootModule =
        graph
            .getModules()
            .stream()
            .anyMatch(module -> rootModuleBasePath.equals(module.getModuleBasePath()));

    ImmutableSet<IjModule> supplementalModules = ImmutableSet.of();
    if (!hasRootModule) {
      supplementalModules =
          ImmutableSet.of(
              IjModule.builder()
                  .setModuleBasePath(rootModuleBasePath)
                  .setTargets(ImmutableSet.of())
                  .setModuleType(IjModuleType.UNKNOWN_MODULE)
                  .build());
    }

    return Stream.concat(graph.getModules().stream(), supplementalModules.stream())
        .collect(MoreCollectors.toImmutableSet());
  }

  public ImmutableSet<IjModule> getModulesToBeWritten() {
    return modulesToBeWritten;
  }

  public ImmutableSet<IjLibrary> getLibrariesToBeWritten() {
    return librariesToBeWritten;
  }

  private ContentRoot createContentRoot(
      final IjModule module,
      Path contentRootPath,
      ImmutableSet<IjFolder> folders,
      final Path moduleLocationBasePath) {
    String url = IjProjectPaths.toModuleDirRelativeString(contentRootPath, moduleLocationBasePath);
    ImmutableSet<IjFolder> simplifiedFolders =
        sourceRootSimplifier.simplify(contentRootPath.getNameCount(), folders);
    IjFolderToIjSourceFolderTransform transformToFolder =
        new IjFolderToIjSourceFolderTransform(module);
    ImmutableSortedSet<IjSourceFolder> sourceFolders =
        simplifiedFolders
            .stream()
            .map(transformToFolder::apply)
            .filter(folder -> !(folder.getType().equals("excludeFolder")
              && (folder.getUrl().endsWith("/res") || folder.getUrl().endsWith("/assets"))))
        .collect(MoreCollectors.toImmutableSortedSet(Ordering.natural()));
    return ContentRoot.builder().setUrl(url).setFolders(sourceFolders).build();
  }

  public ImmutableSet<IjFolder> createExcludes(final IjModule module) throws IOException {
    final ImmutableSet.Builder<IjFolder> excludesBuilder = ImmutableSet.builder();
    final Path moduleBasePath = module.getModuleBasePath();
    projectFilesystem.walkRelativeFileTree(
        moduleBasePath,
        new FileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            // This is another module that's nested in this one. The entire subtree will be handled
            // When we create excludes for that module.
            if (filesystemTraversalBoundaryPaths.contains(dir) && !moduleBasePath.equals(dir)) {
              return FileVisitResult.SKIP_SUBTREE;
            }

            if (isRootAndroidResourceDirectory(module, dir)) {
              return FileVisitResult.SKIP_SUBTREE;
            }

            if (!referencedFolderPaths.contains(dir)) {
              excludesBuilder.add(new ExcludeFolder(dir));
              return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
          }
        });
    return excludesBuilder.build();
  }

  private boolean isRootAndroidResourceDirectory(IjModule module, Path dir) {
    if (!module.getAndroidFacet().isPresent()) {
      return false;
    }

    for (Path resourcePath : module.getAndroidFacet().get().getResourcePaths()) {
      if (dir.equals(resourcePath)) {
        return true;
      }
    }

    return false;
  }

  public ContentRoot getContentRoot(IjModule module) throws IOException {
    Path moduleBasePath = module.getModuleBasePath();
    Path moduleLocation = module.getModuleImlFilePath();
    final Path moduleLocationBasePath =
        (moduleLocation.getParent() == null) ? Paths.get("") : moduleLocation.getParent();
    ImmutableSet<IjFolder> sourcesAndExcludes =
        Stream.concat(module.getFolders().stream(), createExcludes(module).stream())
            .collect(MoreCollectors.toImmutableSortedSet());
    return createContentRoot(module, moduleBasePath, sourcesAndExcludes, moduleLocationBasePath);
  }

  public ImmutableSet<IjSourceFolder> getGeneratedSourceFolders(final IjModule module) {
    return module
        .getGeneratedSourceCodeFolders()
        .stream()
        .map(new IjFolderToIjSourceFolderTransform(module)::apply)
        .collect(MoreCollectors.toImmutableSortedSet());
  }

  public ImmutableSet<DependencyEntry> getDependencies(IjModule module) {
    ImmutableMap<IjProjectElement, DependencyType> deps = moduleGraph.getDepsFor(module);
    IjDependencyListBuilder dependencyListBuilder = new IjDependencyListBuilder();

    for (Map.Entry<IjProjectElement, DependencyType> entry : deps.entrySet()) {
      IjProjectElement element = entry.getKey();
      DependencyType dependencyType = entry.getValue();
      element.addAsDependency(dependencyType, dependencyListBuilder);
    }
    return dependencyListBuilder.build();
  }

  public Optional<String> getFirstResourcePackageFromDependencies(IjModule module) {
    ImmutableMap<IjModule, DependencyType> deps = moduleGraph.getDependentModulesFor(module);
    for (IjModule dep : deps.keySet()) {
      Optional<IjModuleAndroidFacet> facet = dep.getAndroidFacet();
      if (facet.isPresent()) {
        Optional<String> packageName = facet.get().getPackageName();
        if (packageName.isPresent()) {
          return packageName;
        }
      }
    }
    return Optional.empty();
  }

  public ImmutableSortedSet<ModuleIndexEntry> getModuleIndexEntries() {
    return modulesToBeWritten
        .stream()
        .map(
            module -> {
              Path moduleOutputFilePath = module.getModuleImlFilePath();
              String fileUrl = IjProjectPaths.toProjectDirRelativeString(moduleOutputFilePath);
              // The root project module cannot belong to any group.
              String group = (module.getModuleBasePath().toString().isEmpty()) ? null : "modules";
              return ModuleIndexEntry.builder()
                  .setFileUrl(fileUrl)
                  .setFilePath(moduleOutputFilePath)
                  .setGroup(group)
                  .build();
            })
        .collect(MoreCollectors.toImmutableSortedSet(Ordering.natural()));
  }

  public Map<String, Object> getAndroidProperties(IjModule module) {
    Map<String, Object> androidProperties = new HashMap<>();
    Optional<IjModuleAndroidFacet> androidFacetOptional = module.getAndroidFacet();

    boolean isAndroidFacetPresent = androidFacetOptional.isPresent();
    androidProperties.put("enabled", isAndroidFacetPresent);
    if (!isAndroidFacetPresent) {
      return androidProperties;
    }

    IjModuleAndroidFacet androidFacet = androidFacetOptional.get();

    androidProperties.put("is_android_library_project", androidFacet.isAndroidLibrary());
    androidProperties.put("autogenerate_sources", androidFacet.autogenerateSources());

    Path basePath = module.getModuleBasePath();

    addAndroidApkPaths(androidProperties, module, basePath, androidFacet);
    addAndroidAssetPaths(androidProperties, module, androidFacet);
    addAndroidGenPath(androidProperties, androidFacet, basePath);
    addAndroidManifestPath(androidProperties, basePath, androidFacet);
    addAndroidProguardPath(androidProperties, androidFacet);
    addAndroidResourcePaths(androidProperties, module, androidFacet);

    return androidProperties;
  }

  private void addAndroidApkPaths(
      Map<String, Object> androidProperties,
      IjModule module,
      Path moduleBasePath,
      IjModuleAndroidFacet androidFacet) {
    if (androidFacet.isAndroidLibrary()) {
      return;
    }

    Path apkPath =
        moduleBasePath
            .relativize(Paths.get(""))
            .resolve(IjAndroidHelper.getAndroidApkDir(projectFilesystem))
            .resolve(Paths.get("").relativize(moduleBasePath))
            .resolve(module.getName() + ".apk");
    androidProperties.put(APK_PATH_TEMPLATE_PARAMETER, apkPath);
  }

  private void addAndroidAssetPaths(
      Map<String, Object> androidProperties, IjModule module, IjModuleAndroidFacet androidFacet) {
    if (androidFacet.isAndroidLibrary()) {
      return;
    }
    ImmutableSet<Path> assetPaths = androidFacet.getAssetPaths();
    if (assetPaths.isEmpty()) {
      return;
    }
    Set<Path> relativeAssetPaths = new HashSet<>(assetPaths.size());
    Path moduleBase = module.getModuleBasePath();
    for (Path assetPath : assetPaths) {
      relativeAssetPaths.add(moduleBase.relativize(assetPath));
    }
    androidProperties.put(
        ASSETS_FOLDER_TEMPLATE_PARAMETER, "/" + Joiner.on(";/").join(relativeAssetPaths));
  }

  private void addAndroidGenPath(
      Map<String, Object> androidProperties,
      IjModuleAndroidFacet androidFacet,
      Path moduleBasePath) {
    Path genPath = moduleBasePath.relativize(androidFacet.getGeneratedSourcePath());
    androidProperties.put("module_gen_path", "/" + MorePaths.pathWithUnixSeparators(genPath));
  }

  private void addAndroidManifestPath(
      Map<String, Object> androidProperties,
      Path moduleBasePath,
      IjModuleAndroidFacet androidFacet) {
    Optional<Path> androidManifestPath = androidFacet.getManifestPath();
    Path manifestPath;
    if (androidManifestPath.isPresent()) {
      manifestPath =
          projectFilesystem.resolve(moduleBasePath).relativize(androidManifestPath.get());
    } else {
      manifestPath =
          moduleBasePath.relativize(Paths.get("").resolve("android_res/AndroidManifest.xml"));
    }
    if (!"AndroidManifest.xml".equals(manifestPath.toString())) {
      androidProperties.put(ANDROID_MANIFEST_TEMPLATE_PARAMETER, "/" + manifestPath);
    }
  }

  private void addAndroidProguardPath(
      Map<String, Object> androidProperties, IjModuleAndroidFacet androidFacet) {
    androidFacet
        .getProguardConfigPath()
        .ifPresent(
            proguardPath ->
                androidProperties.put(PROGUARD_CONFIG_TEMPLATE_PARAMETER, proguardPath));
  }

  private void addAndroidResourcePaths(
      Map<String, Object> androidProperties, IjModule module, IjModuleAndroidFacet androidFacet) {
    ImmutableSet<Path> resourcePaths = androidFacet.getResourcePaths();
    if (resourcePaths.isEmpty()) {
      androidProperties.put(RESOURCES_RELATIVE_PATH_TEMPLATE_PARAMETER, EMPTY_STRING);
    } else {
      Set<Path> relativeResourcePaths = new HashSet<>(resourcePaths.size());
      Path moduleBase = module.getModuleBasePath();
      for (Path resourcePath : resourcePaths) {
        relativeResourcePaths.add(moduleBase.relativize(resourcePath));
      }

      androidProperties.put(
          RESOURCES_RELATIVE_PATH_TEMPLATE_PARAMETER,
          "/" + Joiner.on(";/").join(relativeResourcePaths));
    }
  }

  private class IjFolderToIjSourceFolderTransform implements Function<IjFolder, IjSourceFolder> {
    private Path moduleBasePath;
    private Optional<IjModuleAndroidFacet> androidFacet;

    IjFolderToIjSourceFolderTransform(IjModule module) {
      moduleBasePath = module.getModuleBasePath();
      androidFacet = module.getAndroidFacet();
    }

    @Override
    public IjSourceFolder apply(IjFolder input) {
      String packagePrefix;
      if (input instanceof AndroidResourceFolder
          && androidFacet.isPresent()
          && androidFacet.get().getPackageName().isPresent()) {
        packagePrefix = androidFacet.get().getPackageName().get();
      } else {
        packagePrefix = getPackagePrefix(input);
      }
      return createSourceFolder(input, moduleBasePath, packagePrefix);
    }

    private IjSourceFolder createSourceFolder(
        IjFolder folder, Path moduleLocationBasePath, @Nullable String packagePrefix) {
      return IjSourceFolder.builder()
          .setType(folder.getIjName())
          .setUrl(
              IjProjectPaths.toModuleDirRelativeString(folder.getPath(), moduleLocationBasePath))
          .setIsTestSource(folder instanceof TestFolder)
          .setIsAndroidResources(folder instanceof AndroidResourceFolder)
          .setPackagePrefix(packagePrefix)
          .build();
    }

    @Nullable
    private String getPackagePrefix(IjFolder folder) {
      if (!folder.getWantsPackagePrefix()) {
        return null;
      }
      Path fileToLookupPackageIn;
      if (!folder.getInputs().isEmpty()
          && folder.getInputs().first().getParent().equals(folder.getPath())) {
        fileToLookupPackageIn = folder.getInputs().first();
      } else {
        fileToLookupPackageIn = folder.getPath().resolve("notfound");
      }
      String packagePrefix = javaPackageFinder.findJavaPackage(fileToLookupPackageIn);
      if (packagePrefix.isEmpty()) {
        // It doesn't matter either way, but an empty prefix looks confusing.
        return null;
      }
      return packagePrefix;
    }
  }
}
