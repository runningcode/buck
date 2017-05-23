/*
 * Copyright 2012-present Facebook, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.jvm.java.KeystoreBuilder;
import com.facebook.buck.jvm.java.PrebuiltJarBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultBuildTargetSourcePath;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.Matchers;
import org.junit.Test;

public class AndroidPackageableCollectorTest {

  /**
   * This is a regression test to ensure that an additional 1 second startup cost is not
   * re-introduced to fb4a.
   */
  @Test
  public void testFindTransitiveDependencies() throws Exception {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    Path prebuiltNativeLibraryPath = Paths.get("java/com/facebook/prebuilt_native_library/libs");
    projectFilesystem.mkdirs(prebuiltNativeLibraryPath);

    // Create an AndroidBinaryRule that transitively depends on two prebuilt JARs. One of the two
    // prebuilt JARs will be listed in the AndroidBinaryRule's no_dx list.
    BuildTarget guavaTarget = BuildTargetFactory.newInstance("//third_party/guava:guava");
    TargetNode<?, ?> guava =
        PrebuiltJarBuilder.createBuilder(guavaTarget)
            .setBinaryJar(Paths.get("third_party/guava/guava-10.0.1.jar"))
            .build();

    BuildTarget jsr305Target = BuildTargetFactory.newInstance("//third_party/jsr-305:jsr-305");
    TargetNode<?, ?> jsr =
        PrebuiltJarBuilder.createBuilder(jsr305Target)
            .setBinaryJar(Paths.get("third_party/jsr-305/jsr305.jar"))
            .build();

    TargetNode<?, ?> ndkLibrary =
        new NdkLibraryBuilder(
                BuildTargetFactory.newInstance("//java/com/facebook/native_library:library"),
                projectFilesystem)
            .build();

    BuildTarget prebuiltNativeLibraryTarget =
        BuildTargetFactory.newInstance("//java/com/facebook/prebuilt_native_library:library");
    TargetNode<?, ?> prebuiltNativeLibraryBuild =
        PrebuiltNativeLibraryBuilder.newBuilder(prebuiltNativeLibraryTarget, projectFilesystem)
            .setNativeLibs(prebuiltNativeLibraryPath)
            .setIsAsset(true)
            .build();

    BuildTarget libraryRuleTarget =
        BuildTargetFactory.newInstance("//java/src/com/facebook:example");
    TargetNode<?, ?> library =
        JavaLibraryBuilder.createBuilder(libraryRuleTarget)
            .setProguardConfig(new FakeSourcePath("debug.pro"))
            .addSrc(Paths.get("Example.java"))
            .addDep(guavaTarget)
            .addDep(jsr305Target)
            .addDep(prebuiltNativeLibraryBuild.getBuildTarget())
            .addDep(ndkLibrary.getBuildTarget())
            .build();

    BuildTarget manifestTarget = BuildTargetFactory.newInstance("//java/src/com/facebook:res");
    TargetNode<?, ?> manifest =
        AndroidResourceBuilder.createBuilder(manifestTarget)
            .setManifest(
                new PathSourcePath(
                    projectFilesystem,
                    Paths.get("java/src/com/facebook/module/AndroidManifest.xml")))
            .setAssets(new FakeSourcePath("assets"))
            .build();

    BuildTarget keystoreTarget = BuildTargetFactory.newInstance("//keystore:debug");
    TargetNode<?, ?> keystore =
        KeystoreBuilder.createBuilder(keystoreTarget)
            .setStore(new FakeSourcePath(projectFilesystem, "keystore/debug.keystore"))
            .setProperties(
                new FakeSourcePath(projectFilesystem, "keystore/debug.keystore.properties"))
            .build();

    ImmutableSortedSet<BuildTarget> originalDepsTargets =
        ImmutableSortedSet.of(libraryRuleTarget, manifestTarget);
    BuildTarget binaryTarget = BuildTargetFactory.newInstance("//java/src/com/facebook:app");
    TargetNode<?, ?> binary =
        AndroidBinaryBuilder.createBuilder(binaryTarget)
            .setOriginalDeps(originalDepsTargets)
            .setBuildTargetsToExcludeFromDex(
                ImmutableSet.of(BuildTargetFactory.newInstance("//third_party/guava:guava")))
            .setManifest(new FakeSourcePath("java/src/com/facebook/AndroidManifest.xml"))
            .setKeystore(keystoreTarget)
            .build();

    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            binary,
            library,
            manifest,
            keystore,
            ndkLibrary,
            prebuiltNativeLibraryBuild,
            guava,
            jsr);
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(ruleResolver));

    AndroidBinary binaryRule = (AndroidBinary) ruleResolver.requireRule(binaryTarget);
    NdkLibrary ndkLibraryRule = (NdkLibrary) ruleResolver.requireRule(ndkLibrary.getBuildTarget());
    NativeLibraryBuildRule prebuildNativeLibraryRule =
        (NativeLibraryBuildRule) ruleResolver.requireRule(prebuiltNativeLibraryTarget);

    // Verify that the correct transitive dependencies are found.
    AndroidPackageableCollection packageableCollection =
        binaryRule.getAndroidPackageableCollection();

    assertResolvedEquals(
        "Because guava was passed to no_dx, it should not be in the classpathEntriesToDex list",
        pathResolver,
        ImmutableSet.of(
            ruleResolver.getRule(jsr305Target).getSourcePathToOutput(),
            ruleResolver.getRule(libraryRuleTarget).getSourcePathToOutput()),
        packageableCollection.getClasspathEntriesToDex());
    assertResolvedEquals(
        "Because guava was passed to no_dx, it should not be treated as a third-party JAR whose "
            + "resources need to be extracted and repacked in the APK. If this is done, then code "
            + "in the guava-10.0.1.dex.1.jar in the APK's assets/ tmp may try to load the resource "
            + "from the APK as a ZipFileEntry rather than as a resource within "
            + "guava-10.0.1.dex.1.jar. Loading a resource in this way could take substantially "
            + "longer. Specifically, this was observed to take over one second longer to load "
            + "the resource in fb4a. Because the resource was loaded on startup, this introduced a "
            + "substantial regression in the startup time for the fb4a app.",
        pathResolver,
        ImmutableSet.of(ruleResolver.getRule(jsr305Target).getSourcePathToOutput()),
        packageableCollection.getPathsToThirdPartyJars());
    assertResolvedEquals(
        "Because assets directory was passed an AndroidResourceRule it should be added to the "
            + "transitive dependencies",
        pathResolver,
        ImmutableSet.of(
            new DefaultBuildTargetSourcePath(
                manifestTarget.withAppendedFlavors(
                    AndroidResourceDescription.ASSETS_SYMLINK_TREE_FLAVOR))),
        packageableCollection.getAssetsDirectories());
    assertResolvedEquals(
        "Because a native library was declared as a dependency, it should be added to the "
            + "transitive dependencies.",
        pathResolver,
        ImmutableSet.<SourcePath>of(
            new PathSourcePath(new FakeProjectFilesystem(), ndkLibraryRule.getLibraryPath())),
        ImmutableSet.copyOf(packageableCollection.getNativeLibsDirectories().values()));
    assertResolvedEquals(
        "Because a prebuilt native library  was declared as a dependency (and asset), it should "
            + "be added to the transitive dependecies.",
        pathResolver,
        ImmutableSet.<SourcePath>of(
            new PathSourcePath(
                new FakeProjectFilesystem(), prebuildNativeLibraryRule.getLibraryPath())),
        ImmutableSet.copyOf(packageableCollection.getNativeLibAssetsDirectories().values()));
    assertEquals(
        ImmutableSet.of(new FakeSourcePath("debug.pro")),
        packageableCollection.getProguardConfigs());
  }

  /**
   * Create the following dependency graph of {@link AndroidResource}s:
   *
   * <pre>
   *    A
   *  / | \
   * B  |  D
   *  \ | /
   *    C
   * </pre>
   *
   * Note that an ordinary breadth-first traversal would yield either {@code A B C D} or {@code A D
   * C B}. However, either of these would be <em>wrong</em> in this case because we need to be sure
   * that we perform a topological sort, the resulting traversal of which is either {@code A B D C}
   * or {@code A D B C}.
   *
   * <p>The reason for the correct result being reversed is because we want the resources with the
   * most dependencies listed first on the path, so that they're used in preference to the ones that
   * they depend on (presumably, the reason for extending the initial set of resources was to
   * override values).
   */
  @Test
  public void testGetAndroidResourceDeps() throws Exception {
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    BuildRule c =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:c"))
                .setRes(new FakeSourcePath("res_c"))
                .setRDotJavaPackage("com.facebook")
                .build());

    BuildRule b =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:b"))
                .setRes(new FakeSourcePath("res_b"))
                .setRDotJavaPackage("com.facebook")
                .setDeps(ImmutableSortedSet.of(c))
                .build());

    BuildRule d =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:d"))
                .setRes(new FakeSourcePath("res_d"))
                .setRDotJavaPackage("com.facebook")
                .setDeps(ImmutableSortedSet.of(c))
                .build());

    AndroidResource a =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:a"))
                .setRes(new FakeSourcePath("res_a"))
                .setRDotJavaPackage("com.facebook")
                .setDeps(ImmutableSortedSet.of(b, c, d))
                .build());

    AndroidPackageableCollector collector = new AndroidPackageableCollector(a.getBuildTarget());
    collector.addPackageables(ImmutableList.of(a));

    // Note that a topological sort for a DAG is not guaranteed to be unique, but we order nodes
    // within the same depth of the search.
    ImmutableList<BuildTarget> result =
        ImmutableList.of(a, d, b, c)
            .stream()
            .map(BuildRule::getBuildTarget)
            .collect(MoreCollectors.toImmutableList());

    assertEquals(
        "Android resources should be topologically sorted.",
        result,
        collector.build().getResourceDetails().getResourcesWithNonEmptyResDir());

    // Introduce an AndroidBinaryRule that depends on A and C and verify that the same topological
    // sort results. This verifies that both AndroidResourceRule.getAndroidResourceDeps does the
    // right thing when it gets a non-AndroidResourceRule as well as an AndroidResourceRule.
    BuildTarget keystoreTarget = BuildTargetFactory.newInstance("//keystore:debug");
    KeystoreBuilder.createBuilder(keystoreTarget)
        .setStore(new FakeSourcePath("keystore/debug.keystore"))
        .setProperties(new FakeSourcePath("keystore/debug.keystore.properties"))
        .build(ruleResolver);

    ImmutableSortedSet<BuildTarget> declaredDepsTargets =
        ImmutableSortedSet.of(a.getBuildTarget(), c.getBuildTarget());
    AndroidBinary androidBinary =
        AndroidBinaryBuilder.createBuilder(BuildTargetFactory.newInstance("//:e"))
            .setManifest(new FakeSourcePath("AndroidManfiest.xml"))
            .setKeystore(keystoreTarget)
            .setOriginalDeps(declaredDepsTargets)
            .build(ruleResolver);

    assertEquals(
        "Android resources should be topologically sorted.",
        result,
        androidBinary
            .getAndroidPackageableCollection()
            .getResourceDetails()
            .getResourcesWithNonEmptyResDir());
  }

  @Test
  public void testGetAndroidResourceDepsWithDuplicateResourcePaths() throws Exception {
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    FakeSourcePath resPath = new FakeSourcePath("res");
    AndroidResource res1 =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:res1"))
                .setRes(resPath)
                .setRDotJavaPackage("com.facebook")
                .build());

    AndroidResource res2 =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:res2"))
                .setRes(resPath)
                .setRDotJavaPackage("com.facebook")
                .build());

    FakeSourcePath resBPath = new FakeSourcePath("res_b");
    BuildRule b =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:b"))
                .setRes(resBPath)
                .setRDotJavaPackage("com.facebook")
                .build());

    FakeSourcePath resAPath = new FakeSourcePath("res_a");
    AndroidResource a =
        ruleResolver.addToIndex(
            AndroidResourceRuleBuilder.newBuilder()
                .setRuleFinder(ruleFinder)
                .setBuildTarget(BuildTargetFactory.newInstance("//:a"))
                .setRes(resAPath)
                .setRDotJavaPackage("com.facebook")
                .setDeps(ImmutableSortedSet.of(res1, res2, b))
                .build());

    AndroidPackageableCollector collector = new AndroidPackageableCollector(a.getBuildTarget());
    collector.addPackageables(ImmutableList.of(a));

    AndroidPackageableCollection androidPackageableCollection = collector.build();
    AndroidPackageableCollection.ResourceDetails resourceDetails =
        androidPackageableCollection.getResourceDetails();
    assertThat(
        resourceDetails.getResourceDirectories(), Matchers.contains(resAPath, resPath, resBPath));
  }

  /**
   * If the keystore rule depends on an android_library, and an android_binary uses that keystore,
   * the keystore's android_library should not contribute to the classpath of the android_binary.
   */
  @Test
  public void testGraphForAndroidBinaryExcludesKeystoreDeps() throws Exception {
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(ruleResolver));

    BuildTarget androidLibraryKeystoreTarget =
        BuildTargetFactory.newInstance("//java/com/keystore/base:base");
    BuildRule androidLibraryKeystore =
        AndroidLibraryBuilder.createBuilder(androidLibraryKeystoreTarget)
            .addSrc(Paths.get("java/com/facebook/keystore/Base.java"))
            .build(ruleResolver);

    BuildTarget keystoreTarget = BuildTargetFactory.newInstance("//keystore:debug");
    KeystoreBuilder.createBuilder(keystoreTarget)
        .setStore(new FakeSourcePath("keystore/debug.keystore"))
        .setProperties(new FakeSourcePath("keystore/debug.keystore.properties"))
        .addDep(androidLibraryKeystore.getBuildTarget())
        .build(ruleResolver);

    BuildTarget androidLibraryTarget =
        BuildTargetFactory.newInstance("//java/com/facebook/base:base");
    BuildRule androidLibrary =
        AndroidLibraryBuilder.createBuilder(androidLibraryTarget)
            .addSrc(Paths.get("java/com/facebook/base/Base.java"))
            .build(ruleResolver);

    ImmutableSortedSet<BuildTarget> originalDepsTargets =
        ImmutableSortedSet.of(androidLibrary.getBuildTarget());
    AndroidBinary androidBinary =
        AndroidBinaryBuilder.createBuilder(BuildTargetFactory.newInstance("//apps/sample:app"))
            .setManifest(new FakeSourcePath("apps/sample/AndroidManifest.xml"))
            .setOriginalDeps(originalDepsTargets)
            .setKeystore(keystoreTarget)
            .build(ruleResolver);

    AndroidPackageableCollection packageableCollection =
        androidBinary.getAndroidPackageableCollection();
    assertEquals(
        "Classpath entries should include facebook/base but not keystore/base.",
        ImmutableSet.of(
            BuildTargets.getGenPath(
                    androidBinary.getProjectFilesystem(), androidLibraryTarget, "lib__%s__output/")
                .resolve(androidLibraryTarget.getShortNameAndFlavorPostfix() + ".jar")),
        packageableCollection
            .getClasspathEntriesToDex()
            .stream()
            .map(pathResolver::getRelativePath)
            .collect(MoreCollectors.toImmutableSet()));
  }

  private void assertResolvedEquals(
      String message,
      SourcePathResolver pathResolver,
      ImmutableSet<SourcePath> expected,
      ImmutableSet<SourcePath> actual) {
    assertEquals(
        message,
        expected
            .stream()
            .map(pathResolver::getRelativePath)
            .collect(MoreCollectors.toImmutableSet()),
        actual
            .stream()
            .map(pathResolver::getRelativePath)
            .collect(MoreCollectors.toImmutableSet()));
  }
}
