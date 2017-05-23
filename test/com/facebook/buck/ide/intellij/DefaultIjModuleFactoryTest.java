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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.android.AndroidBinaryBuilder;
import com.facebook.buck.android.AndroidBinaryDescriptionArg;
import com.facebook.buck.android.AndroidLibraryBuilder;
import com.facebook.buck.android.AndroidLibraryDescription;
import com.facebook.buck.android.AndroidPrebuiltAarBuilder;
import com.facebook.buck.android.AndroidResourceDescriptionArg;
import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxLibraryBuilder;
import com.facebook.buck.ide.intellij.aggregation.AggregationMode;
import com.facebook.buck.ide.intellij.model.DependencyType;
import com.facebook.buck.ide.intellij.model.IjLibrary;
import com.facebook.buck.ide.intellij.model.IjLibraryFactoryResolver;
import com.facebook.buck.ide.intellij.model.IjModule;
import com.facebook.buck.ide.intellij.model.IjModuleFactory;
import com.facebook.buck.ide.intellij.model.IjModuleFactoryResolver;
import com.facebook.buck.ide.intellij.model.IjModuleType;
import com.facebook.buck.ide.intellij.model.IjProjectConfig;
import com.facebook.buck.ide.intellij.model.folders.IjFolder;
import com.facebook.buck.ide.intellij.model.folders.SourceFolder;
import com.facebook.buck.ide.intellij.model.folders.TestFolder;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.groovy.GroovyLibraryBuilder;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.jvm.java.JavaTestBuilder;
import com.facebook.buck.jvm.java.JvmLibraryArg;
import com.facebook.buck.jvm.kotlin.KotlinLibraryBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Test;

public class DefaultIjModuleFactoryTest {

  @Test
  public void testModuleDep() {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("java/com/example/base");
    BuildTarget buildTargetGuava = BuildTargetFactory.newInstance("//third-party/guava:guava");

    TargetNode<?, ?> javaLibBase =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addSrc(moduleBasePath.resolve("File.java"))
            .addDep(buildTargetGuava)
            .build();

    IjModule module =
        factory.createModule(moduleBasePath, ImmutableSet.of(javaLibBase), Collections.emptySet());

    assertEquals(ImmutableMap.of(buildTargetGuava, DependencyType.PROD), module.getDependencies());
  }

  @Test
  public void testModuleDepMerge() {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("test/com/example/base");
    BuildTarget buildTargetGuava = BuildTargetFactory.newInstance("//third-party/guava:guava");
    BuildTarget buildTargetJunit = BuildTargetFactory.newInstance("//third-party/junit:junit");

    TargetNode<?, ?> javaLibBase =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//test/com/example/base:base"))
            .addSrc(moduleBasePath.resolve("File.java"))
            .addDep(buildTargetGuava)
            .build();

    TargetNode<?, ?> javaLibExtra =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//test/com/example/base:extra"))
            .addSrc(moduleBasePath.resolve("File2.java"))
            .addDep(javaLibBase.getBuildTarget())
            .addDep(buildTargetJunit)
            .build();

    IjModule module =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(javaLibBase, javaLibExtra), Collections.emptySet());

    assertEquals(
        ImmutableMap.of(
            buildTargetGuava, DependencyType.PROD,
            buildTargetJunit, DependencyType.PROD),
        module.getDependencies());
  }

  @Test
  public void testModuleTestDep() {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("test/com/example/base");
    BuildTarget buildTargetJunit = BuildTargetFactory.newInstance("//third-party/junit:junit");

    TargetNode<?, ?> javaTestExtra =
        JavaTestBuilder.createBuilder(
                BuildTargetFactory.newInstance("//test/com/example/base:extra"))
            .addSrc(moduleBasePath.resolve("base/File.java"))
            .addDep(buildTargetJunit)
            .build();

    IjModule module =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(javaTestExtra), Collections.emptySet());

    assertEquals(ImmutableMap.of(buildTargetJunit, DependencyType.TEST), module.getDependencies());
  }

  @Test
  public void testModuleDepTypeResolution() {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("java/com/example");
    BuildTarget buildTargetGuava = BuildTargetFactory.newInstance("//third-party:guava");
    BuildTarget buildTargetJunit = BuildTargetFactory.newInstance("//third-party:junit");

    TargetNode<?, ?> javaLibBase =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addSrc(moduleBasePath.resolve("base/File.java"))
            .addDep(buildTargetGuava)
            .build();

    TargetNode<?, ?> javaTestBase =
        JavaTestBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/test:test"))
            .addSrc(moduleBasePath.resolve("test/TestFile.java"))
            .addDep(buildTargetJunit)
            .addDep(buildTargetGuava)
            .build();

    IjModule module =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(javaLibBase, javaTestBase), Collections.emptySet());

    // Guava is a dependency of both a prod and test target therefore it should be kept as a
    // prod dependency.
    assertEquals(
        ImmutableMap.of(
            buildTargetGuava, DependencyType.PROD,
            buildTargetJunit, DependencyType.TEST),
        module.getDependencies());
  }

  @Test
  public void testModuleDepTypePromotion() {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("java/com/example");
    BuildTarget buildTargetGuava = BuildTargetFactory.newInstance("//third-party:guava");
    BuildTarget buildTargetHamcrest = BuildTargetFactory.newInstance("//third-party:hamcrest");
    BuildTarget buildTargetJunit = BuildTargetFactory.newInstance("//third-party:junit");

    TargetNode<?, ?> javaLibBase =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addSrc(moduleBasePath.resolve("base/File.java"))
            .addDep(buildTargetGuava)
            .build();

    TargetNode<?, ?> javaTestBase =
        JavaTestBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:test"))
            .addSrc(moduleBasePath.resolve("base/TestFile.java"))
            .addSrc(moduleBasePath.resolve("test/TestFile.java"))
            .addDep(buildTargetJunit)
            .addDep(buildTargetGuava)
            .build();

    TargetNode<?, ?> javaTest =
        JavaTestBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/test:test"))
            .addSrc(moduleBasePath.resolve("test/TestFile2.java"))
            .addDep(javaLibBase.getBuildTarget())
            .addDep(buildTargetJunit)
            .addDep(buildTargetHamcrest)
            .build();

    IjModule module =
        factory.createModule(
            moduleBasePath,
            ImmutableSet.of(javaLibBase, javaTestBase, javaTest),
            Collections.emptySet());

    // Because both javaLibBase and javaTestBase have source files in the same folder and IntelliJ
    // operates at folder level granularity it is impossible to split the files into separate test
    // and prod sets. Therefore it is necessary to "promote" the test dependencies to prod so that
    // it's possible to compile all of the code in the folder.
    assertEquals(
        ImmutableMap.of(
            buildTargetGuava, DependencyType.PROD,
            buildTargetJunit, DependencyType.PROD,
            buildTargetHamcrest, DependencyType.TEST),
        module.getDependencies());
  }

  @Test
  public void testDepWhenNoSources() {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("java/com/example");
    BuildTarget buildTargetGuava = BuildTargetFactory.newInstance("//third-party:guava");

    TargetNode<?, ?> javaLibBase =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addDep(buildTargetGuava)
            .build();

    BuildTarget keystoreTarget = BuildTargetFactory.newInstance("//java/com/example/test:keystore");
    TargetNode<?, ?> androidBinary =
        AndroidBinaryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/test:test"))
            .setManifest(new FakeSourcePath("java/com/example/test/AndroidManifest.xml"))
            .setOriginalDeps(ImmutableSortedSet.of(javaLibBase.getBuildTarget()))
            .setKeystore(keystoreTarget)
            .build();

    IjModule moduleJavaLib =
        factory.createModule(moduleBasePath, ImmutableSet.of(javaLibBase), Collections.emptySet());

    IjModule moduleFromBinary =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(androidBinary), Collections.emptySet());

    assertEquals(
        ImmutableMap.of(buildTargetGuava, DependencyType.PROD), moduleJavaLib.getDependencies());

    assertEquals(
        ImmutableMap.of(
            javaLibBase.getBuildTarget(), DependencyType.PROD, keystoreTarget, DependencyType.PROD),
        moduleFromBinary.getDependencies());
  }

  @Test
  public void testCompiledShadowDep() {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("java/com/example");
    BuildTarget genruleBuildTarget =
        BuildTargetFactory.newInstance("//java/com/example/base:genrule");

    TargetNode<?, ?> javaLibWithGenrule =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base_genrule"))
            .addSrcTarget(genruleBuildTarget)
            .build();

    TargetNode<?, ?> javaLibWithAnnotationProcessor =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base_annotation"))
            .addSrc(Paths.get("java/com/example/base/Base.java"))
            .setAnnotationProcessors(ImmutableSet.of(":annotation_processor"))
            .build();

    IjModule moduleJavaLibWithGenrule =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(javaLibWithGenrule), Collections.emptySet());

    IjModule moduleJavaLibWithAnnotationProcessor =
        factory.createModule(
            moduleBasePath,
            ImmutableSet.of(javaLibWithAnnotationProcessor),
            Collections.emptySet());

    assertEquals(
        ImmutableMap.of(
            genruleBuildTarget,
            DependencyType.PROD,
            javaLibWithGenrule.getBuildTarget(),
            DependencyType.COMPILED_SHADOW),
        moduleJavaLibWithGenrule.getDependencies());

    assertEquals(
        ImmutableMap.of(
            javaLibWithAnnotationProcessor.getBuildTarget(), DependencyType.COMPILED_SHADOW),
        moduleJavaLibWithAnnotationProcessor.getDependencies());
  }

  @Test
  public void testJavaLibrary() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> javaLib =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addSrc(Paths.get("java/com/example/base/File.java"))
            .build();

    Path moduleBasePath = Paths.get("java/com/example/base");
    IjModule module =
        factory.createModule(moduleBasePath, ImmutableSet.of(javaLib), Collections.emptySet());

    assertEquals(moduleBasePath, module.getModuleBasePath());
    assertFalse(module.getAndroidFacet().isPresent());
    assertEquals(1, module.getFolders().size());
    assertEquals(ImmutableSet.of(javaLib.getBuildTarget()), module.getTargets());

    IjFolder folder = module.getFolders().iterator().next();
    assertEquals(Paths.get("java/com/example/base"), folder.getPath());
    assertFalse(folder instanceof TestFolder);
    assertTrue(folder.getWantsPackagePrefix());
  }

  @Test
  public void testGroovyLibrary() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> groovyLib =
        GroovyLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//groovy/com/example/base:base"))
            .addSrc(Paths.get("groovy/com/example/base/File.groovy"))
            .build();

    Path moduleBasePath = Paths.get("groovy/com/example/base");
    IjModule module =
        factory.createModule(moduleBasePath, ImmutableSet.of(groovyLib), Collections.emptySet());

    assertEquals(moduleBasePath, module.getModuleBasePath());
    assertFalse(module.getAndroidFacet().isPresent());
    assertEquals(1, module.getFolders().size());
    assertEquals(ImmutableSet.of(groovyLib.getBuildTarget()), module.getTargets());

    IjFolder folder = module.getFolders().iterator().next();
    assertEquals(Paths.get("groovy/com/example/base"), folder.getPath());
    assertFalse(folder instanceof TestFolder);
    assertFalse(folder.getWantsPackagePrefix());
  }

  @Test
  public void testKotlinLibrary() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> kotlinLib =
        KotlinLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//kotlin/com/example/base:base"))
            .addSrc(Paths.get("kotlin/com/example/base/File.kt"))
            .build();

    Path moduleBasePath = Paths.get("kotlin/com/example/base");
    IjModule module =
        factory.createModule(moduleBasePath, ImmutableSet.of(kotlinLib), Collections.emptySet());

    assertEquals(moduleBasePath, module.getModuleBasePath());
    assertFalse(module.getAndroidFacet().isPresent());
    assertEquals(1, module.getFolders().size());
    assertEquals(ImmutableSet.of(kotlinLib.getBuildTarget()), module.getTargets());

    IjFolder folder = module.getFolders().iterator().next();
    assertEquals(Paths.get("kotlin/com/example/base"), folder.getPath());
    assertFalse(folder instanceof TestFolder);
    assertFalse(folder.getWantsPackagePrefix());
  }

  @Test
  public void testJavaLibraryInRoot() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> javaLib =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:base"))
            .addSrc(Paths.get("File.java"))
            .build();

    Path moduleBasePath = Paths.get("");
    IjModule module =
        factory.createModule(moduleBasePath, ImmutableSet.of(javaLib), Collections.emptySet());

    assertEquals(moduleBasePath, module.getModuleBasePath());

    assertEquals(1, module.getFolders().size());
    IjFolder folder = module.getFolders().iterator().next();
    assertEquals(Paths.get(""), folder.getPath());
  }

  private ImmutableSet<Path> getFolderPaths(ImmutableSet<IjFolder> folders) {
    return folders.stream().map(IjFolder::getPath).collect(MoreCollectors.toImmutableSet());
  }

  @Test
  public void testJavaLibrariesWithParentBasePath() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> javaLib1 =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:core"))
            .addSrc(Paths.get("java/com/example/base/src1/File.java"))
            .build();

    TargetNode<?, ?> javaLib2 =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:more"))
            .addSrc(Paths.get("java/com/example/base/src2/File.java"))
            .build();

    IjModule module =
        factory.createModule(
            Paths.get("java/com/example"),
            ImmutableSet.of(javaLib1, javaLib2),
            Collections.emptySet());

    ImmutableSet<Path> folderPaths = getFolderPaths(module.getFolders());
    assertEquals(
        ImmutableSet.of(
            Paths.get("java/com/example/base/src1"), Paths.get("java/com/example/base/src2")),
        folderPaths);
  }

  @Test
  public void testJavaLibraryAndTestLibraryResultInOnlyOneFolder() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> javaLib =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//third-party/example:core"))
            .addSrc(Paths.get("third-party/example/File.java"))
            .build();

    TargetNode<?, ?> javaTest =
        JavaTestBuilder.createBuilder(BuildTargetFactory.newInstance("//third-party/example:test"))
            .addSrc(Paths.get("third-party/example/TestFile.java"))
            .addDep(javaLib.getBuildTarget())
            .build();

    IjModule module =
        factory.createModule(
            Paths.get("third-party/example"),
            ImmutableSet.of(javaLib, javaTest),
            Collections.emptySet());

    assertEquals(
        ImmutableSet.of(Paths.get("third-party/example")), getFolderPaths(module.getFolders()));
  }

  @Test
  public void testAndroidLibrary() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> androidLib =
        AndroidLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addSrc(Paths.get("java/com/example/base/File.java"))
            .build();

    Path moduleBasePath = Paths.get("java/com/example/base");
    IjModule module =
        factory.createModule(moduleBasePath, ImmutableSet.of(androidLib), Collections.emptySet());

    assertTrue(module.getAndroidFacet().isPresent());
    assertEquals(ImmutableSet.of(moduleBasePath), getFolderPaths(module.getFolders()));
  }

  @Test
  public void testAndroidLibraries() {
    IjModuleFactory factory = createIjModuleFactory();

    TargetNode<?, ?> javaLib =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .build();

    TargetNode<?, ?> androidLib =
        AndroidLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:more"))
            .build();

    Path moduleBasePath = Paths.get("java/com/example/base");
    IjModule module =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(androidLib, javaLib), Collections.emptySet());

    assertTrue(module.getAndroidFacet().isPresent());
  }

  @Test
  public void testAndroidBinary() {
    IjModuleFactory factory = createIjModuleFactory();

    String manifestName = "Manifest.xml";
    SourcePath manifestPath = new FakeSourcePath(manifestName);
    TargetNode<?, ?> androidBinary =
        AndroidBinaryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example:droid"))
            .setManifest(manifestPath)
            .setKeystore(BuildTargetFactory.newInstance("//java/com/example:keystore"))
            .build();

    Path moduleBasePath = Paths.get("java/com/example");
    IjModule module =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(androidBinary), Collections.emptySet());

    assertTrue(module.getAndroidFacet().isPresent());
    assertEquals(Paths.get(manifestName), module.getAndroidFacet().get().getManifestPath().get());
  }

  @Test
  public void testOverrideSdk() throws Exception {
    IjModuleFactory factory = createIjModuleFactory();

    Path moduleBasePath = Paths.get("java/com/example");
    TargetNode<?, ?> defaultJavaNode =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addSrc(Paths.get("java/com/example/base/Base.java"))
            .build();

    TargetNode<?, ?> java8Node =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base2:base2"))
            .addSrc(Paths.get("java/com/example/base2/Base2.java"))
            .setSourceLevel("1.8")
            .build();

    IjModule moduleWithDefault =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(defaultJavaNode), Collections.emptySet());
    IjModule moduleWithJava8 =
        factory.createModule(moduleBasePath, ImmutableSet.of(java8Node), Collections.emptySet());

    assertThat(moduleWithDefault.getModuleType(), equalTo(IjModuleType.JAVA_MODULE));
    assertThat(moduleWithJava8.getModuleType(), equalTo(IjModuleType.JAVA_MODULE));
    assertThat(moduleWithJava8.getLanguageLevel(), equalTo(Optional.of("1.8")));
  }

  @Test
  public void testOverrideSdkFromBuckConfig() throws Exception {
    IjModuleFactory factory =
        createIjModuleFactory(
            FakeBuckConfig.builder()
                .setSections("[intellij]", "java_library_sdk_names = 1.8 => TestSDK")
                .build());

    Path moduleBasePath = Paths.get("java/com/example");
    TargetNode<?, ?> defaultJavaNode =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base:base"))
            .addSrc(Paths.get("java/com/example/base/Base.java"))
            .build();

    TargetNode<?, ?> java8Node =
        JavaLibraryBuilder.createBuilder(
                BuildTargetFactory.newInstance("//java/com/example/base2:base2"))
            .addSrc(Paths.get("java/com/example/base2/Base2.java"))
            .setSourceLevel("1.8")
            .build();

    IjModule moduleWithDefault =
        factory.createModule(
            moduleBasePath, ImmutableSet.of(defaultJavaNode), Collections.emptySet());
    IjModule moduleWithJava8 =
        factory.createModule(moduleBasePath, ImmutableSet.of(java8Node), Collections.emptySet());

    assertThat(moduleWithDefault.getModuleType(), equalTo(IjModuleType.JAVA_MODULE));
    assertThat(moduleWithJava8.getModuleType(), equalTo(IjModuleType.JAVA_MODULE));
    assertThat(moduleWithJava8.getLanguageLevel(), equalTo(Optional.of("1.8")));
  }

  @Test
  public void testAndroidPrebuiltAar() {
    final SourcePath androidSupportBinaryPath =
        new FakeSourcePath("third_party/java/support/support.aar");
    final Path androidSupportSourcesPath =
        Paths.get("third_party/java/support/support-sources.jar");
    final String androidSupportJavadocUrl = "file:///support/docs";
    final TargetNode<?, ?> androidPrebuiltAar =
        AndroidPrebuiltAarBuilder.createBuilder(
                BuildTargetFactory.newInstance("//third_party/java/support:support"))
            .setBinaryAar(androidSupportBinaryPath)
            .setSourcesJar(androidSupportSourcesPath)
            .setJavadocUrl(androidSupportJavadocUrl)
            .build();

    final BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    final SourcePathResolver sourcePathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(buildRuleResolver));
    IjLibraryFactoryResolver ijLibraryFactoryResolver =
        new IjLibraryFactoryResolver() {
          @Override
          public Path getPath(SourcePath path) {
            return sourcePathResolver.getRelativePath(path);
          }

          @Override
          public Optional<SourcePath> getPathIfJavaLibrary(TargetNode<?, ?> targetNode) {
            if (targetNode.equals(androidPrebuiltAar)) {
              return Optional.of(androidSupportBinaryPath);
            }
            return Optional.empty();
          }
        };

    Optional<IjLibrary> library =
        new DefaultIjLibraryFactory(ijLibraryFactoryResolver).getLibrary(androidPrebuiltAar);
    assertTrue(library.isPresent());
    assertEquals(
        library.get().getBinaryJars(),
        ImmutableSet.of(sourcePathResolver.getRelativePath(androidSupportBinaryPath)));
    assertEquals(library.get().getSourceJars(), ImmutableSet.of(androidSupportSourcesPath));
    assertEquals(library.get().getJavadocUrls(), ImmutableSet.of(androidSupportJavadocUrl));
  }

  private IjModuleFactory createIjModuleFactory() {
    return createIjModuleFactory(null);
  }

  private IjModuleFactory createIjModuleFactory(BuckConfig buckConfig) {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    IjProjectConfig projectConfig =
        buckConfig == null
            ? IjProjectBuckConfig.create(
                FakeBuckConfig.builder().build(),
                AggregationMode.AUTO,
                null,
                false,
                false,
                false,
                true)
            : IjProjectBuckConfig.create(
                buckConfig, AggregationMode.AUTO, null, false, false, false, true);
    SupportedTargetTypeRegistry typeRegistry =
        new SupportedTargetTypeRegistry(
            projectFilesystem,
            new IjModuleFactoryResolver() {
              @Override
              public Optional<Path> getDummyRDotJavaPath(TargetNode<?, ?> targetNode) {
                return Optional.empty();
              }

              @Override
              public Path getAndroidManifestPath(
                  TargetNode<AndroidBinaryDescriptionArg, ?> targetNode) {
                return ((FakeSourcePath) targetNode.getConstructorArg().getManifest())
                    .getRelativePath();
              }

              @Override
              public Optional<Path> getLibraryAndroidManifestPath(
                  TargetNode<AndroidLibraryDescription.CoreArg, ?> targetNode) {
                return Optional.empty();
              }

              @Override
              public Optional<Path> getProguardConfigPath(
                  TargetNode<AndroidBinaryDescriptionArg, ?> targetNode) {
                return Optional.empty();
              }

              @Override
              public Optional<Path> getAndroidResourcePath(
                  TargetNode<AndroidResourceDescriptionArg, ?> targetNode) {
                return Optional.empty();
              }

              @Override
              public Optional<Path> getAssetsPath(
                  TargetNode<AndroidResourceDescriptionArg, ?> targetNode) {
                return Optional.empty();
              }

              @Override
              public Optional<Path> getAnnotationOutputPath(
                  TargetNode<? extends JvmLibraryArg, ?> targetNode) {
                return Optional.empty();
              }
            },
            projectConfig);
    return new DefaultIjModuleFactory(projectFilesystem, typeRegistry);
  }

  @Test
  public void testCxxLibrary() {
    IjModuleFactory factory = createIjModuleFactory();

    String sourceName = "cpp/lib/foo.cpp";
    TargetNode<?, ?> cxxLibrary =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//cpp/lib:foo"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath(sourceName))))
            .build();

    Path moduleBasePath = Paths.get("cpp/lib");
    IjModule module =
        factory.createModule(moduleBasePath, ImmutableSet.of(cxxLibrary), Collections.emptySet());

    IjFolder cxxLibraryModel =
        new SourceFolder(
            Paths.get("cpp/lib"), false, ImmutableSortedSet.of(Paths.get("cpp/lib/foo.cpp")));
    assertThat(module.getFolders(), Matchers.contains(cxxLibraryModel));
  }
}
