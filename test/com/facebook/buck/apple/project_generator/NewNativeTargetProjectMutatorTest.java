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

package com.facebook.buck.apple.project_generator;

import static com.facebook.buck.apple.project_generator.ProjectGeneratorTestUtils.assertHasSingletonFrameworksPhaseWithFrameworkEntries;
import static com.facebook.buck.apple.project_generator.ProjectGeneratorTestUtils.assertHasSingletonPhaseWithEntries;
import static com.facebook.buck.apple.project_generator.ProjectGeneratorTestUtils.assertTargetExistsAndReturnTarget;
import static com.facebook.buck.apple.project_generator.ProjectGeneratorTestUtils.getSingletonPhaseByType;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyIterable.emptyIterable;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.apple.AppleAssetCatalogDescriptionArg;
import com.facebook.buck.apple.AppleResourceDescriptionArg;
import com.facebook.buck.apple.XcodePostbuildScriptBuilder;
import com.facebook.buck.apple.XcodePrebuildScriptBuilder;
import com.facebook.buck.apple.xcode.xcodeproj.CopyFilePhaseDestinationSpec;
import com.facebook.buck.apple.xcode.xcodeproj.PBXBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXCopyFilesBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXFileReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXGroup;
import com.facebook.buck.apple.xcode.xcodeproj.PBXNativeTarget;
import com.facebook.buck.apple.xcode.xcodeproj.PBXProject;
import com.facebook.buck.apple.xcode.xcodeproj.PBXReference;
import com.facebook.buck.apple.xcode.xcodeproj.PBXResourcesBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.PBXShellScriptBuildPhase;
import com.facebook.buck.apple.xcode.xcodeproj.ProductType;
import com.facebook.buck.apple.xcode.xcodeproj.SourceTreePath;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.js.IosReactNativeLibraryBuilder;
import com.facebook.buck.js.ReactNativeBuckConfig;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.testutil.AllExistingProjectFilesystem;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class NewNativeTargetProjectMutatorTest {
  private PBXProject generatedProject;
  private PathRelativizer pathRelativizer;
  private SourcePathResolver sourcePathResolver;

  @Before
  public void setUp() {
    assumeTrue(Platform.detect() == Platform.MACOS || Platform.detect() == Platform.LINUX);
    generatedProject = new PBXProject("TestProject");
    sourcePathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    pathRelativizer =
        new PathRelativizer(Paths.get("_output"), sourcePathResolver::getRelativePath);
  }

  @Test
  public void shouldCreateTargetAndTargetGroup() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator =
        new NewNativeTargetProjectMutator(pathRelativizer, sourcePathResolver::getRelativePath);
    mutator
        .setTargetName("TestTarget")
        .setProduct(ProductType.BUNDLE, "TestTargetProduct", Paths.get("TestTargetProduct.bundle"))
        .buildTargetAndAddToProject(generatedProject, true);

    assertTargetExistsAndReturnTarget(generatedProject, "TestTarget");
    assertHasTargetGroupWithName(generatedProject, "TestTarget");
  }

  @Test
  public void shouldCreateTargetAndCustomTargetGroup() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator =
        new NewNativeTargetProjectMutator(pathRelativizer, sourcePathResolver::getRelativePath);
    mutator
        .setTargetName("TestTarget")
        .setTargetGroupPath(ImmutableList.of("Grandparent", "Parent"))
        .setProduct(ProductType.BUNDLE, "TestTargetProduct", Paths.get("TestTargetProduct.bundle"))
        .buildTargetAndAddToProject(generatedProject, true);

    assertTargetExistsAndReturnTarget(generatedProject, "TestTarget");
    PBXGroup grandparentGroup =
        assertHasSubgroupAndReturnIt(generatedProject.getMainGroup(), "Grandparent");
    assertHasSubgroupAndReturnIt(grandparentGroup, "Parent");
  }

  @Test
  public void shouldCreateTargetAndNoGroup() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator =
        new NewNativeTargetProjectMutator(pathRelativizer, sourcePathResolver::getRelativePath);
    NewNativeTargetProjectMutator.Result result =
        mutator
            .setTargetName("TestTarget")
            .setTargetGroupPath(ImmutableList.of("Grandparent", "Parent"))
            .setProduct(
                ProductType.BUNDLE, "TestTargetProduct", Paths.get("TestTargetProduct.bundle"))
            .buildTargetAndAddToProject(generatedProject, false);

    assertFalse(result.targetGroup.isPresent());
  }

  @Test
  public void testSourceGroups() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();

    SourcePath foo = new FakeSourcePath("Group1/foo.m");
    SourcePath bar = new FakeSourcePath("Group1/bar.m");
    SourcePath baz = new FakeSourcePath("Group2/baz.m");
    mutator.setSourcesWithFlags(
        ImmutableSet.of(
            SourceWithFlags.of(foo),
            SourceWithFlags.of(bar, ImmutableList.of("-Wall")),
            SourceWithFlags.of(baz)));
    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    PBXGroup sourcesGroup = result.targetGroup.get().getOrCreateChildGroupByName("Sources");

    PBXGroup group1 = (PBXGroup) Iterables.get(sourcesGroup.getChildren(), 0);
    assertEquals("Group1", group1.getName());
    assertThat(group1.getChildren(), hasSize(2));
    PBXFileReference fileRefBar = (PBXFileReference) Iterables.get(group1.getChildren(), 0);
    assertEquals("bar.m", fileRefBar.getName());
    PBXFileReference fileRefFoo = (PBXFileReference) Iterables.get(group1.getChildren(), 1);
    assertEquals("foo.m", fileRefFoo.getName());

    PBXGroup group2 = (PBXGroup) Iterables.get(sourcesGroup.getChildren(), 1);
    assertEquals("Group2", group2.getName());
    assertThat(group2.getChildren(), hasSize(1));
    PBXFileReference fileRefBaz = (PBXFileReference) Iterables.get(group2.getChildren(), 0);
    assertEquals("baz.m", fileRefBaz.getName());
  }

  @Test
  public void testLibraryHeaderGroups() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();

    SourcePath foo = new FakeSourcePath("HeaderGroup1/foo.h");
    SourcePath bar = new FakeSourcePath("HeaderGroup1/bar.h");
    SourcePath baz = new FakeSourcePath("HeaderGroup2/baz.h");
    mutator.setPublicHeaders(ImmutableSet.of(bar, baz));
    mutator.setPrivateHeaders(ImmutableSet.of(foo));
    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    PBXGroup sourcesGroup = result.targetGroup.get().getOrCreateChildGroupByName("Sources");

    assertThat(sourcesGroup.getChildren(), hasSize(2));

    PBXGroup group1 = (PBXGroup) Iterables.get(sourcesGroup.getChildren(), 0);
    assertEquals("HeaderGroup1", group1.getName());
    assertThat(group1.getChildren(), hasSize(2));
    PBXFileReference fileRefBar = (PBXFileReference) Iterables.get(group1.getChildren(), 0);
    assertEquals("bar.h", fileRefBar.getName());
    PBXFileReference fileRefFoo = (PBXFileReference) Iterables.get(group1.getChildren(), 1);
    assertEquals("foo.h", fileRefFoo.getName());

    PBXGroup group2 = (PBXGroup) Iterables.get(sourcesGroup.getChildren(), 1);
    assertEquals("HeaderGroup2", group2.getName());
    assertThat(group2.getChildren(), hasSize(1));
    PBXFileReference fileRefBaz = (PBXFileReference) Iterables.get(group2.getChildren(), 0);
    assertEquals("baz.h", fileRefBaz.getName());
  }

  @Test
  public void testPrefixHeaderInSourceGroup() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();
    SourcePath prefixHeader = new FakeSourcePath("Group1/prefix.pch");
    mutator.setPrefixHeader(Optional.of(prefixHeader));

    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    // No matter where the prefixHeader file is it should always be directly inside Sources
    PBXGroup sourcesGroup = result.targetGroup.get().getOrCreateChildGroupByName("Sources");

    assertThat(sourcesGroup.getChildren(), hasSize(1));
    PBXFileReference fileRef = (PBXFileReference) Iterables.get(sourcesGroup.getChildren(), 0);
    assertEquals("prefix.pch", fileRef.getName());
  }

  @Test
  public void testFrameworkBuildPhase() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();
    mutator.setFrameworks(
        ImmutableSet.of(
            FrameworkPath.ofSourceTreePath(
                new SourceTreePath(
                    PBXReference.SourceTree.SDKROOT,
                    Paths.get("Foo.framework"),
                    Optional.empty()))));
    mutator.setArchives(
        ImmutableSet.of(
            new PBXFileReference(
                "libdep.a",
                "libdep.a",
                PBXReference.SourceTree.BUILT_PRODUCTS_DIR,
                Optional.empty())));
    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);
    assertHasSingletonFrameworksPhaseWithFrameworkEntries(
        result.target, ImmutableList.of("$SDKROOT/Foo.framework", "$BUILT_PRODUCTS_DIR/libdep.a"));
  }

  @Test
  public void testResourcesBuildPhase() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();

    AppleResourceDescriptionArg arg =
        AppleResourceDescriptionArg.builder()
            .setName("resources")
            .setFiles(ImmutableSet.of(new FakeSourcePath("foo.png")))
            .build();

    mutator.setRecursiveResources(ImmutableSet.of(arg));
    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    assertHasSingletonPhaseWithEntries(
        result.target, PBXResourcesBuildPhase.class, ImmutableList.of("$SOURCE_ROOT/../foo.png"));
  }

  @Test
  public void testCopyFilesBuildPhase() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();

    CopyFilePhaseDestinationSpec.Builder specBuilder = CopyFilePhaseDestinationSpec.builder();
    specBuilder.setDestination(PBXCopyFilesBuildPhase.Destination.FRAMEWORKS);
    specBuilder.setPath("foo.png");

    PBXBuildPhase copyPhase = new PBXCopyFilesBuildPhase(specBuilder.build());
    mutator.setCopyFilesPhases(ImmutableList.of(copyPhase));

    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    PBXBuildPhase buildPhaseToTest =
        getSingletonPhaseByType(result.target, PBXCopyFilesBuildPhase.class);
    assertThat(copyPhase, equalTo(buildPhaseToTest));
  }

  @Test
  public void testCopyFilesBuildPhaseIsBeforePostBuildScriptBuildPhase()
      throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();

    CopyFilePhaseDestinationSpec.Builder specBuilder = CopyFilePhaseDestinationSpec.builder();
    specBuilder.setDestination(PBXCopyFilesBuildPhase.Destination.FRAMEWORKS);
    specBuilder.setPath("script/input.png");

    PBXBuildPhase copyFilesPhase = new PBXCopyFilesBuildPhase(specBuilder.build());
    mutator.setCopyFilesPhases(ImmutableList.of(copyFilesPhase));

    TargetNode<?, ?> postbuildNode =
        XcodePostbuildScriptBuilder.createBuilder(BuildTargetFactory.newInstance("//foo:script"))
            .setCmd("echo \"hello world!\"")
            .build();
    mutator.setPostBuildRunScriptPhasesFromTargetNodes(ImmutableList.of(postbuildNode));

    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    PBXNativeTarget target = result.target;

    List<PBXBuildPhase> buildPhases = target.getBuildPhases();

    PBXBuildPhase copyBuildPhaseToTest =
        getSingletonPhaseByType(target, PBXCopyFilesBuildPhase.class);
    PBXBuildPhase postBuildScriptPhase =
        getSingletonPhaseByType(target, PBXShellScriptBuildPhase.class);

    assertThat(
        buildPhases.indexOf(copyBuildPhaseToTest),
        lessThan(buildPhases.indexOf(postBuildScriptPhase)));
  }

  @Test
  public void assetCatalogsBuildPhaseBuildsAssetCatalogs() throws NoSuchBuildTargetException {
    AppleAssetCatalogDescriptionArg arg =
        AppleAssetCatalogDescriptionArg.builder()
            .setName("some_rule")
            .setDirs(ImmutableSortedSet.of(new FakeSourcePath("AssetCatalog1.xcassets")))
            .build();

    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();
    mutator.setRecursiveAssetCatalogs(ImmutableSet.of(arg));
    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);
    assertHasSingletonPhaseWithEntries(
        result.target,
        PBXResourcesBuildPhase.class,
        ImmutableList.of("$SOURCE_ROOT/../AssetCatalog1.xcassets"));
  }

  @Test
  public void testScriptBuildPhase() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();

    TargetNode<?, ?> prebuildNode =
        XcodePrebuildScriptBuilder.createBuilder(BuildTargetFactory.newInstance("//foo:script"))
            .setSrcs(ImmutableSortedSet.of(new FakeSourcePath("script/input.png")))
            .setOutputs(ImmutableSortedSet.of("helloworld.txt"))
            .setCmd("echo \"hello world!\"")
            .build();

    mutator.setPostBuildRunScriptPhasesFromTargetNodes(ImmutableList.of(prebuildNode));
    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    PBXShellScriptBuildPhase phase =
        getSingletonPhaseByType(result.target, PBXShellScriptBuildPhase.class);
    assertThat(
        "Should set input paths correctly",
        "../script/input.png",
        is(equalTo(Iterables.getOnlyElement(phase.getInputPaths()))));
    assertThat(
        "Should set output paths correctly",
        "helloworld.txt",
        is(equalTo(Iterables.getOnlyElement(phase.getOutputPaths()))));
    assertEquals("should set script correctly", "echo \"hello world!\"", phase.getShellScript());
  }

  @Test
  public void testScriptBuildPhaseWithReactNative() throws NoSuchBuildTargetException {
    NewNativeTargetProjectMutator mutator = mutatorWithCommonDefaults();

    BuildTarget depBuildTarget = BuildTargetFactory.newInstance("//foo:dep");
    ProjectFilesystem filesystem = new AllExistingProjectFilesystem();
    ReactNativeBuckConfig buckConfig =
        new ReactNativeBuckConfig(
            FakeBuckConfig.builder()
                .setSections(
                    ImmutableMap.of(
                        "react-native",
                        ImmutableMap.of("packager_worker", "react-native/packager.sh")))
                .setFilesystem(filesystem)
                .build());
    TargetNode<?, ?> reactNativeNode =
        IosReactNativeLibraryBuilder.builder(depBuildTarget, buckConfig)
            .setBundleName("Apps/Foo/FooBundle.js")
            .setEntryPath(new PathSourcePath(filesystem, Paths.get("js/FooApp.js")))
            .build();

    mutator.setPostBuildRunScriptPhasesFromTargetNodes(ImmutableList.of(reactNativeNode));
    NewNativeTargetProjectMutator.Result result =
        mutator.buildTargetAndAddToProject(generatedProject, true);

    PBXShellScriptBuildPhase phase =
        getSingletonPhaseByType(result.target, PBXShellScriptBuildPhase.class);
    String shellScript = phase.getShellScript();
    assertThat(
        shellScript,
        startsWith(
            "BASE_DIR=${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}\n"
                + "JS_OUT=${BASE_DIR}/Apps/Foo/FooBundle.js\n"
                + "SOURCE_MAP=${TEMP_DIR}/rn_source_map/Apps/Foo/FooBundle.js.map\n"));
  }

  private NewNativeTargetProjectMutator mutatorWithCommonDefaults() {
    NewNativeTargetProjectMutator mutator =
        new NewNativeTargetProjectMutator(pathRelativizer, sourcePathResolver::getRelativePath);
    mutator
        .setTargetName("TestTarget")
        .setProduct(ProductType.BUNDLE, "TestTargetProduct", Paths.get("TestTargetProduct.bundle"));
    return mutator;
  }

  private static void assertHasTargetGroupWithName(PBXProject project, final String name) {
    assertThat(
        "Should contain a target group named: " + name,
        Iterables.filter(
            project.getMainGroup().getChildren(), input -> input.getName().equals(name)),
        not(emptyIterable()));
  }

  private static PBXGroup assertHasSubgroupAndReturnIt(PBXGroup group, final String subgroupName) {
    ImmutableList<PBXGroup> candidates =
        FluentIterable.from(group.getChildren())
            .filter(input -> input.getName().equals(subgroupName))
            .filter(PBXGroup.class)
            .toList();
    if (candidates.size() != 1) {
      fail("Could not find a unique subgroup by its name");
    }
    return candidates.get(0);
  }
}
