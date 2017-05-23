/*
 * Copyright 2013-present Facebook, Inc.
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

import static com.facebook.buck.jvm.java.JavaCompilationConstants.ANDROID_JAVAC_OPTIONS;
import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVAC;
import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVA_CONFIG;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.android.AndroidBinary.ExopackageMode;
import com.facebook.buck.android.aapt.RDotTxtEntry.RType;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.jvm.core.HasJavaClassHashes;
import com.facebook.buck.jvm.java.JavaLibrary;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.jvm.java.Keystore;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.FakeCellPathResolver;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.rules.coercer.BuildConfigFields;
import com.facebook.buck.rules.coercer.ManifestEntries;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Test;

public class AndroidBinaryGraphEnhancerTest {

  @Test
  public void testCreateDepsForPreDexing() throws Exception {
    // Create three Java rules, :dep1, :dep2, and :lib. :lib depends on :dep1 and :dep2.
    BuildTarget javaDep1BuildTarget = BuildTargetFactory.newInstance("//java/com/example:dep1");
    TargetNode<?, ?> javaDep1Node =
        JavaLibraryBuilder.createBuilder(javaDep1BuildTarget)
            .addSrc(Paths.get("java/com/example/Dep1.java"))
            .build();

    BuildTarget javaDep2BuildTarget = BuildTargetFactory.newInstance("//java/com/example:dep2");
    TargetNode<?, ?> javaDep2Node =
        JavaLibraryBuilder.createBuilder(javaDep2BuildTarget)
            .addSrc(Paths.get("java/com/example/Dep2.java"))
            .build();

    BuildTarget javaLibBuildTarget = BuildTargetFactory.newInstance("//java/com/example:lib");
    TargetNode<?, ?> javaLibNode =
        JavaLibraryBuilder.createBuilder(javaLibBuildTarget)
            .addSrc(Paths.get("java/com/example/Lib.java"))
            .addDep(javaDep1Node.getBuildTarget())
            .addDep(javaDep2Node.getBuildTarget())
            .build();

    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(javaDep1Node, javaDep2Node, javaLibNode);
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);

    BuildRule javaDep1 = ruleResolver.requireRule(javaDep1BuildTarget);
    BuildRule javaDep2 = ruleResolver.requireRule(javaDep2BuildTarget);
    BuildRule javaLib = ruleResolver.requireRule(javaLibBuildTarget);

    // Assume we are enhancing an android_binary rule whose only dep
    // is //java/com/example:lib, and that //java/com/example:dep2 is in its no_dx list.
    ImmutableSortedSet<BuildRule> originalDeps = ImmutableSortedSet.of(javaLib);
    ImmutableSet<BuildTarget> buildRulesToExcludeFromDex = ImmutableSet.of(javaDep2BuildTarget);
    BuildTarget apkTarget = BuildTargetFactory.newInstance("//java/com/example:apk");
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleParams originalParams =
        new BuildRuleParams(
            apkTarget,
            Suppliers.ofInstance(originalDeps),
            Suppliers.ofInstance(originalDeps),
            ImmutableSortedSet.of(),
            filesystem);
    AndroidBinaryGraphEnhancer graphEnhancer =
        new AndroidBinaryGraphEnhancer(
            originalParams,
            targetGraph,
            ruleResolver,
            new FakeCellPathResolver(filesystem),
            AndroidBinary.AaptMode.AAPT1,
            ResourcesFilter.ResourceCompressionMode.DISABLED,
            FilterResourcesStep.ResourceFilter.EMPTY_FILTER,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            Optional.empty(),
            /* locales */ ImmutableSet.of(),
            createStrictMock(PathSourcePath.class),
            AndroidBinary.PackageType.DEBUG,
            /* cpuFilters */ ImmutableSet.of(),
            /* shouldBuildStringSourceMap */ false,
            /* shouldPreDex */ true,
            BuildTargets.getScratchPath(
                originalParams.getProjectFilesystem(), apkTarget, "%s/classes.dex"),
            DexSplitMode.NO_SPLIT,
            buildRulesToExcludeFromDex,
            /* resourcesToExclude */ ImmutableSet.of(),
            /* skipCrunchPngs */ false,
            /* includesVectorDrawables */ false,
            DEFAULT_JAVA_CONFIG,
            DEFAULT_JAVAC,
            ANDROID_JAVAC_OPTIONS,
            EnumSet.noneOf(ExopackageMode.class),
            /* buildConfigValues */ BuildConfigFields.empty(),
            /* buildConfigValuesFile */ Optional.empty(),
            /* xzCompressionLevel */ Optional.empty(),
            /* trimResourceIds */ false,
            /* keepResourcePattern */ Optional.empty(),
            /* nativePlatforms */ ImmutableMap.of(),
            /* nativeLibraryMergeMap */ Optional.empty(),
            /* nativeLibraryMergeGlue */ Optional.empty(),
            /* nativeLibraryMergeCodeGenerator */ Optional.empty(),
            /* nativeLibraryProguardConfigGenerator */ Optional.empty(),
            Optional.empty(),
            AndroidBinary.RelinkerMode.DISABLED,
            MoreExecutors.newDirectExecutorService(),
            /* manifestEntries */ ManifestEntries.empty(),
            CxxPlatformUtils.DEFAULT_CONFIG,
            new APKModuleGraph(
                TargetGraph.EMPTY, originalParams.getBuildTarget(), Optional.empty()),
            new DxConfig(FakeBuckConfig.builder().build()),
            Optional.empty());

    BuildTarget aaptPackageResourcesTarget =
        BuildTargetFactory.newInstance("//java/com/example:apk#aapt_package");
    BuildRuleParams aaptPackageResourcesParams =
        new FakeBuildRuleParamsBuilder(aaptPackageResourcesTarget).build();
    AaptPackageResources aaptPackageResources =
        new AaptPackageResources(
            aaptPackageResourcesParams,
            ruleFinder,
            ruleResolver,
            /* manifest */ new FakeSourcePath("java/src/com/facebook/base/AndroidManifest.xml"),
            new IdentityResourcesProvider(ImmutableList.of()),
            ImmutableList.of(),
            /* skipCrunchPngs */ false,
            /* includesVectorDrawables */ false,
            /* manifestEntries */ ManifestEntries.empty());
    ruleResolver.addToIndex(aaptPackageResources);

    AndroidPackageableCollection collection =
        new AndroidPackageableCollector(
                /* collectionRoot */ apkTarget,
                ImmutableSet.of(javaDep2BuildTarget),
                /* resourcesToExclude */ ImmutableSet.of(),
                new APKModuleGraph(TargetGraph.EMPTY, apkTarget, Optional.empty()))
            .addClasspathEntry(((HasJavaClassHashes) javaDep1), new FakeSourcePath("ignored"))
            .addClasspathEntry(((HasJavaClassHashes) javaDep2), new FakeSourcePath("ignored"))
            .addClasspathEntry(((HasJavaClassHashes) javaLib), new FakeSourcePath("ignored"))
            .build();

    ImmutableMultimap<APKModule, DexProducedFromJavaLibrary> preDexedLibraries =
        graphEnhancer.createPreDexRulesForLibraries(
            /* additionalJavaLibrariesToDex */
            ImmutableList.of(), collection);

    BuildTarget fakeUberRDotJavaCompileTarget =
        BuildTargetFactory.newInstance("//fake:uber_r_dot_java#compile");
    JavaLibrary fakeUberRDotJavaCompile =
        JavaLibraryBuilder.createBuilder(fakeUberRDotJavaCompileTarget).build(ruleResolver);
    BuildTarget fakeUberRDotJavaDexTarget =
        BuildTargetFactory.newInstance("//fake:uber_r_dot_java#dex");
    DexProducedFromJavaLibrary fakeUberRDotJavaDex =
        new DexProducedFromJavaLibrary(
            new FakeBuildRuleParamsBuilder(fakeUberRDotJavaDexTarget).build(),
            fakeUberRDotJavaCompile);
    ruleResolver.addToIndex(fakeUberRDotJavaDex);

    BuildRule preDexMergeRule =
        graphEnhancer.createPreDexMergeRule(preDexedLibraries, fakeUberRDotJavaDex);
    BuildTarget dexMergeTarget = BuildTargetFactory.newInstance("//java/com/example:apk#dex_merge");
    BuildRule dexMergeRule = ruleResolver.getRule(dexMergeTarget);

    assertEquals(dexMergeRule, preDexMergeRule);

    BuildTarget javaDep1DexBuildTarget =
        BuildTarget.builder(javaDep1BuildTarget)
            .addFlavors(AndroidBinaryGraphEnhancer.DEX_FLAVOR)
            .build();
    BuildTarget javaDep2DexBuildTarget =
        BuildTarget.builder(javaDep2BuildTarget)
            .addFlavors(AndroidBinaryGraphEnhancer.DEX_FLAVOR)
            .build();
    BuildTarget javaLibDexBuildTarget =
        BuildTarget.builder(javaLibBuildTarget)
            .addFlavors(AndroidBinaryGraphEnhancer.DEX_FLAVOR)
            .build();
    assertThat(
        "There should be a #dex rule for dep1 and lib, but not dep2 because it is in the no_dx "
            + "list.  And we should depend on uber_r_dot_java",
        Iterables.transform(dexMergeRule.getBuildDeps(), BuildRule::getBuildTarget),
        Matchers.allOf(
            Matchers.not(Matchers.hasItem(javaDep1BuildTarget)),
            Matchers.hasItem(javaDep1DexBuildTarget),
            Matchers.not(Matchers.hasItem(javaDep2BuildTarget)),
            Matchers.not(Matchers.hasItem(javaDep2DexBuildTarget)),
            Matchers.hasItem(javaLibDexBuildTarget),
            Matchers.hasItem(fakeUberRDotJavaDex.getBuildTarget())));
  }

  @Test
  public void testAllBuildablesExceptPreDexRule() throws Exception {
    // Create an android_build_config() as a dependency of the android_binary().
    BuildTarget buildConfigBuildTarget = BuildTargetFactory.newInstance("//java/com/example:cfg");
    BuildRuleParams buildConfigParams =
        new FakeBuildRuleParamsBuilder(buildConfigBuildTarget).build();
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    AndroidBuildConfigJavaLibrary buildConfigJavaLibrary =
        AndroidBuildConfigDescription.createBuildRule(
            buildConfigParams,
            "com.example.buck",
            /* values */ BuildConfigFields.empty(),
            /* valuesFile */ Optional.empty(),
            /* useConstantExpressions */ false,
            DEFAULT_JAVAC,
            ANDROID_JAVAC_OPTIONS,
            ruleResolver);

    BuildTarget apkTarget = BuildTargetFactory.newInstance("//java/com/example:apk");
    BuildRuleParams originalParams =
        new FakeBuildRuleParamsBuilder(apkTarget)
            .setDeclaredDeps(ImmutableSortedSet.of(buildConfigJavaLibrary))
            .build();

    // set it up.
    Keystore keystore = createStrictMock(Keystore.class);
    AndroidBinaryGraphEnhancer graphEnhancer =
        new AndroidBinaryGraphEnhancer(
            originalParams,
            TargetGraph.EMPTY,
            ruleResolver,
            new FakeCellPathResolver(new FakeProjectFilesystem()),
            AndroidBinary.AaptMode.AAPT1,
            ResourcesFilter.ResourceCompressionMode.ENABLED_WITH_STRINGS_AS_ASSETS,
            FilterResourcesStep.ResourceFilter.EMPTY_FILTER,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            Optional.empty(),
            /* locales */ ImmutableSet.of(),
            new FakeSourcePath("AndroidManifest.xml"),
            AndroidBinary.PackageType.DEBUG,
            /* cpuFilters */ ImmutableSet.of(),
            /* shouldBuildStringSourceMap */ false,
            /* shouldPreDex */ false,
            BuildTargets.getScratchPath(
                originalParams.getProjectFilesystem(), apkTarget, "%s/classes.dex"),
            DexSplitMode.NO_SPLIT,
            /* buildRulesToExcludeFromDex */ ImmutableSet.of(),
            /* resourcesToExclude */ ImmutableSet.of(),
            /* skipCrunchPngs */ false,
            /* includesVectorDrawables */ false,
            DEFAULT_JAVA_CONFIG,
            DEFAULT_JAVAC,
            ANDROID_JAVAC_OPTIONS,
            EnumSet.of(ExopackageMode.SECONDARY_DEX),
            /* buildConfigValues */ BuildConfigFields.empty(),
            /* buildConfigValuesFiles */ Optional.empty(),
            /* xzCompressionLevel */ Optional.empty(),
            /* trimResourceIds */ false,
            /* keepResourcePattern */ Optional.empty(),
            /* nativePlatforms */ ImmutableMap.of(),
            /* nativeLibraryMergeMap */ Optional.empty(),
            /* nativeLibraryMergeGlue */ Optional.empty(),
            /* nativeLibraryMergeCodeGenerator */ Optional.empty(),
            /* nativeLibraryProguardConfigGenerator */ Optional.empty(),
            Optional.empty(),
            AndroidBinary.RelinkerMode.DISABLED,
            MoreExecutors.newDirectExecutorService(),
            /* manifestEntries */ ManifestEntries.empty(),
            CxxPlatformUtils.DEFAULT_CONFIG,
            new APKModuleGraph(
                TargetGraph.EMPTY, originalParams.getBuildTarget(), Optional.empty()),
            new DxConfig(FakeBuckConfig.builder().build()),
            Optional.empty());
    replay(keystore);
    AndroidGraphEnhancementResult result = graphEnhancer.createAdditionalBuildables();

    // Verify that android_build_config() was processed correctly.
    Flavor flavor = InternalFlavor.of("buildconfig_com_example_buck");
    final SourcePathResolver pathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(ruleResolver));
    BuildTarget enhancedBuildConfigTarget =
        BuildTarget.builder(apkTarget).addFlavors(flavor).build();
    assertEquals(
        "The only classpath entry to dex should be the one from the AndroidBuildConfigJavaLibrary"
            + " created via graph enhancement.",
        ImmutableSet.of(
            BuildTargets.getGenPath(
                    originalParams.getProjectFilesystem(),
                    enhancedBuildConfigTarget,
                    "lib__%s__output")
                .resolve(enhancedBuildConfigTarget.getShortNameAndFlavorPostfix() + ".jar")),
        result
            .getClasspathEntriesToDex()
            .stream()
            .map(pathResolver::getRelativePath)
            .collect(MoreCollectors.toImmutableSet()));
    BuildRule enhancedBuildConfigRule = ruleResolver.getRule(enhancedBuildConfigTarget);
    assertTrue(enhancedBuildConfigRule instanceof AndroidBuildConfigJavaLibrary);
    AndroidBuildConfigJavaLibrary enhancedBuildConfigJavaLibrary =
        (AndroidBuildConfigJavaLibrary) enhancedBuildConfigRule;
    AndroidBuildConfig androidBuildConfig = enhancedBuildConfigJavaLibrary.getAndroidBuildConfig();
    assertEquals("com.example.buck", androidBuildConfig.getJavaPackage());
    assertTrue(androidBuildConfig.isUseConstantExpressions());
    assertEquals(
        "IS_EXOPACKAGE defaults to false, but should now be true. DEBUG should still be true.",
        BuildConfigFields.fromFields(
            ImmutableList.of(
                BuildConfigFields.Field.of("boolean", "DEBUG", "true"),
                BuildConfigFields.Field.of("boolean", "IS_EXOPACKAGE", "true"),
                BuildConfigFields.Field.of("int", "EXOPACKAGE_FLAGS", "1"))),
        androidBuildConfig.getBuildConfigFields());

    ImmutableSortedSet<BuildRule> finalDeps = result.getFinalDeps();
    BuildRule computeExopackageDepsAbiRule =
        findRuleOfType(ruleResolver, ComputeExopackageDepsAbi.class);
    assertThat(finalDeps, Matchers.hasItem(computeExopackageDepsAbiRule));

    BuildRule resourcesFilterRule = findRuleOfType(ruleResolver, ResourcesFilter.class);

    BuildRule aaptPackageResourcesRule = findRuleOfType(ruleResolver, AaptPackageResources.class);
    MoreAsserts.assertDepends(
        "AaptPackageResources must depend on ResourcesFilter",
        aaptPackageResourcesRule,
        resourcesFilterRule);

    BuildRule packageStringAssetsRule = findRuleOfType(ruleResolver, PackageStringAssets.class);
    MoreAsserts.assertDepends(
        "PackageStringAssets must depend on ResourcesFilter",
        packageStringAssetsRule,
        aaptPackageResourcesRule);

    assertFalse(result.getPreDexMerge().isPresent());

    MoreAsserts.assertDepends(
        "ComputeExopackageDepsAbi must depend on ResourcesFilter",
        computeExopackageDepsAbiRule,
        resourcesFilterRule);
    MoreAsserts.assertDepends(
        "ComputeExopackageDepsAbi must depend on PackageStringAssets",
        computeExopackageDepsAbiRule,
        packageStringAssetsRule);
    MoreAsserts.assertDepends(
        "ComputeExopackageDepsAbi must depend on AaptPackageResources",
        computeExopackageDepsAbiRule,
        aaptPackageResourcesRule);

    assertTrue(result.getPackageStringAssets().isPresent());
    assertTrue(result.getComputeExopackageDepsAbi().isPresent());

    verify(keystore);
  }

  @Test
  public void testResourceRulesBecomeDepsOfAaptPackageResources() throws Exception {
    TargetNode<?, ?> resourceNode =
        AndroidResourceBuilder.createBuilder(BuildTargetFactory.newInstance("//:resource"))
            .setRDotJavaPackage("package")
            .setRes(Paths.get("res"))
            .build();

    TargetGraph targetGraph = TargetGraphFactory.newInstance(resourceNode);
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());

    AndroidResource resource =
        (AndroidResource) ruleResolver.requireRule(resourceNode.getBuildTarget());

    // set it up.
    BuildTarget target = BuildTargetFactory.newInstance("//:target");
    BuildRuleParams originalParams =
        new FakeBuildRuleParamsBuilder(target)
            .setDeclaredDeps(ImmutableSortedSet.of(resource))
            .build();
    AndroidBinaryGraphEnhancer graphEnhancer =
        new AndroidBinaryGraphEnhancer(
            originalParams,
            targetGraph,
            ruleResolver,
            new FakeCellPathResolver(new FakeProjectFilesystem()),
            AndroidBinary.AaptMode.AAPT1,
            ResourcesFilter.ResourceCompressionMode.ENABLED_WITH_STRINGS_AS_ASSETS,
            FilterResourcesStep.ResourceFilter.EMPTY_FILTER,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            Optional.empty(),
            /* locales */ ImmutableSet.of(),
            new FakeSourcePath("AndroidManifest.xml"),
            AndroidBinary.PackageType.DEBUG,
            /* cpuFilters */ ImmutableSet.of(),
            /* shouldBuildStringSourceMap */ false,
            /* shouldPreDex */ false,
            BuildTargets.getScratchPath(
                originalParams.getProjectFilesystem(), target, "%s/classes.dex"),
            DexSplitMode.NO_SPLIT,
            /* buildRulesToExcludeFromDex */ ImmutableSet.of(),
            /* resourcesToExclude */ ImmutableSet.of(),
            /* skipCrunchPngs */ false,
            /* includesVectorDrawables */ false,
            DEFAULT_JAVA_CONFIG,
            DEFAULT_JAVAC,
            ANDROID_JAVAC_OPTIONS,
            EnumSet.of(ExopackageMode.SECONDARY_DEX),
            /* buildConfigValues */ BuildConfigFields.empty(),
            /* buildConfigValuesFiles */ Optional.empty(),
            /* xzCompressionLevel */ Optional.empty(),
            /* trimResourceIds */ false,
            /* keepResourcePattern */ Optional.empty(),
            /* nativePlatforms */ ImmutableMap.of(),
            /* nativeLibraryMergeMap */ Optional.empty(),
            /* nativeLibraryMergeGlue */ Optional.empty(),
            /* nativeLibraryMergeCodeGenerator */ Optional.empty(),
            /* nativeLibraryProguardConfigGenerator */ Optional.empty(),
            Optional.empty(),
            AndroidBinary.RelinkerMode.DISABLED,
            MoreExecutors.newDirectExecutorService(),
            /* manifestEntries */ ManifestEntries.empty(),
            CxxPlatformUtils.DEFAULT_CONFIG,
            new APKModuleGraph(
                TargetGraph.EMPTY, originalParams.getBuildTarget(), Optional.empty()),
            new DxConfig(FakeBuckConfig.builder().build()),
            Optional.empty());
    graphEnhancer.createAdditionalBuildables();

    BuildRule aaptPackageResourcesRule = findRuleOfType(ruleResolver, AaptPackageResources.class);
    MoreAsserts.assertDepends(
        "AaptPackageResources must depend on resource rules", aaptPackageResourcesRule, resource);
  }

  @Test
  public void testPackageStringsDependsOnResourcesFilter() throws Exception {
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());

    // set it up.
    BuildTarget target = BuildTargetFactory.newInstance("//:target");
    BuildRuleParams originalParams = new FakeBuildRuleParamsBuilder(target).build();
    AndroidBinaryGraphEnhancer graphEnhancer =
        new AndroidBinaryGraphEnhancer(
            originalParams,
            TargetGraph.EMPTY,
            ruleResolver,
            new FakeCellPathResolver(new FakeProjectFilesystem()),
            AndroidBinary.AaptMode.AAPT1,
            ResourcesFilter.ResourceCompressionMode.ENABLED_WITH_STRINGS_AS_ASSETS,
            FilterResourcesStep.ResourceFilter.EMPTY_FILTER,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            Optional.empty(),
            /* locales */ ImmutableSet.of(),
            new FakeSourcePath("AndroidManifest.xml"),
            AndroidBinary.PackageType.DEBUG,
            /* cpuFilters */ ImmutableSet.of(),
            /* shouldBuildStringSourceMap */ false,
            /* shouldPreDex */ false,
            BuildTargets.getScratchPath(
                originalParams.getProjectFilesystem(), target, "%s/classes.dex"),
            DexSplitMode.NO_SPLIT,
            /* buildRulesToExcludeFromDex */ ImmutableSet.of(),
            /* resourcesToExclude */ ImmutableSet.of(),
            /* skipCrunchPngs */ false,
            /* includesVectorDrawables */ false,
            DEFAULT_JAVA_CONFIG,
            DEFAULT_JAVAC,
            ANDROID_JAVAC_OPTIONS,
            EnumSet.of(ExopackageMode.SECONDARY_DEX),
            /* buildConfigValues */ BuildConfigFields.empty(),
            /* buildConfigValuesFiles */ Optional.empty(),
            /* xzCompressionLevel */ Optional.empty(),
            /* trimResourceIds */ false,
            /* keepResourcePattern */ Optional.empty(),
            /* nativePlatforms */ ImmutableMap.of(),
            /* nativeLibraryMergeMap */ Optional.empty(),
            /* nativeLibraryMergeGlue */ Optional.empty(),
            /* nativeLibraryMergeCodeGenerator */ Optional.empty(),
            /* nativeLibraryProguardConfigGenerator */ Optional.empty(),
            Optional.empty(),
            AndroidBinary.RelinkerMode.DISABLED,
            MoreExecutors.newDirectExecutorService(),
            /* manifestEntries */ ManifestEntries.empty(),
            CxxPlatformUtils.DEFAULT_CONFIG,
            new APKModuleGraph(
                TargetGraph.EMPTY, originalParams.getBuildTarget(), Optional.empty()),
            new DxConfig(FakeBuckConfig.builder().build()),
            Optional.empty());
    graphEnhancer.createAdditionalBuildables();

    ResourcesFilter resourcesFilter = findRuleOfType(ruleResolver, ResourcesFilter.class);
    PackageStringAssets packageStringAssetsRule =
        findRuleOfType(ruleResolver, PackageStringAssets.class);
    MoreAsserts.assertDepends(
        "PackageStringAssets must depend on AaptPackageResources",
        packageStringAssetsRule,
        resourcesFilter);
  }

  @Test
  public void testResourceRulesDependOnRulesBehindResourceSourcePaths() throws Exception {
    BuildRuleResolver ruleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);

    FakeBuildRule resourcesDep =
        ruleResolver.addToIndex(
            new FakeBuildRule(BuildTargetFactory.newInstance("//:resource_dep"), pathResolver));
    resourcesDep.setOutputFile("foo");

    AndroidResource resource =
        ruleResolver.addToIndex(
            new AndroidResource(
                new FakeBuildRuleParamsBuilder("//:resources")
                    .build()
                    .copyAppendingExtraDeps(ImmutableSortedSet.of(resourcesDep)),
                ruleFinder,
                ImmutableSortedSet.of(),
                resourcesDep.getSourcePathToOutput(),
                ImmutableSortedMap.of(),
                null,
                null,
                ImmutableSortedMap.of(),
                new FakeSourcePath("manifest"),
                false));

    // set it up.
    BuildTarget target = BuildTargetFactory.newInstance("//:target");
    BuildRuleParams originalParams =
        new FakeBuildRuleParamsBuilder(target)
            .setDeclaredDeps(ImmutableSortedSet.of(resource))
            .build();
    AndroidBinaryGraphEnhancer graphEnhancer =
        new AndroidBinaryGraphEnhancer(
            originalParams,
            TargetGraph.EMPTY,
            ruleResolver,
            new FakeCellPathResolver(new FakeProjectFilesystem()),
            AndroidBinary.AaptMode.AAPT1,
            ResourcesFilter.ResourceCompressionMode.ENABLED_WITH_STRINGS_AS_ASSETS,
            FilterResourcesStep.ResourceFilter.EMPTY_FILTER,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            Optional.empty(),
            /* locales */ ImmutableSet.of(),
            new FakeSourcePath("AndroidManifest.xml"),
            AndroidBinary.PackageType.DEBUG,
            /* cpuFilters */ ImmutableSet.of(),
            /* shouldBuildStringSourceMap */ false,
            /* shouldPreDex */ false,
            BuildTargets.getScratchPath(
                originalParams.getProjectFilesystem(), target, "%s/classes.dex"),
            DexSplitMode.NO_SPLIT,
            /* buildRulesToExcludeFromDex */ ImmutableSet.of(),
            /* resourcesToExclude */ ImmutableSet.of(),
            /* skipCrunchPngs */ false,
            /* includesVectorDrawables */ false,
            DEFAULT_JAVA_CONFIG,
            DEFAULT_JAVAC,
            ANDROID_JAVAC_OPTIONS,
            EnumSet.of(ExopackageMode.SECONDARY_DEX),
            /* buildConfigValues */ BuildConfigFields.empty(),
            /* buildConfigValuesFiles */ Optional.empty(),
            /* xzCompressionLevel */ Optional.empty(),
            /* trimResourceIds */ false,
            /* keepResourcePattern */ Optional.empty(),
            /* nativePlatforms */ ImmutableMap.of(),
            /* nativeLibraryMergeMap */ Optional.empty(),
            /* nativeLibraryMergeGlue */ Optional.empty(),
            /* nativeLibraryMergeCodeGenerator */ Optional.empty(),
            /* nativeLibraryProguardConfigGenerator */ Optional.empty(),
            Optional.empty(),
            AndroidBinary.RelinkerMode.DISABLED,
            MoreExecutors.newDirectExecutorService(),
            /* manifestEntries */ ManifestEntries.empty(),
            CxxPlatformUtils.DEFAULT_CONFIG,
            new APKModuleGraph(
                TargetGraph.EMPTY, originalParams.getBuildTarget(), Optional.empty()),
            new DxConfig(FakeBuckConfig.builder().build()),
            Optional.empty());
    graphEnhancer.createAdditionalBuildables();

    ResourcesFilter resourcesFilter = findRuleOfType(ruleResolver, ResourcesFilter.class);
    MoreAsserts.assertDepends(
        "ResourcesFilter must depend on rules behind resources source paths",
        resourcesFilter,
        resourcesDep);
  }

  private <T extends BuildRule> T findRuleOfType(
      BuildRuleResolver ruleResolver, Class<T> ruleClass) {
    for (BuildRule rule : ruleResolver.getBuildRules()) {
      if (ruleClass.isAssignableFrom(rule.getClass())) {
        return ruleClass.cast(rule);
      }
    }
    fail("Could not find build rule of type " + ruleClass.getCanonicalName());
    return null;
  }
}
