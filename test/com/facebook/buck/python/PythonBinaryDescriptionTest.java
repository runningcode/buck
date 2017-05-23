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

package com.facebook.buck.python;

import static org.junit.Assert.assertThat;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBinaryBuilder;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLibraryBuilder;
import com.facebook.buck.cxx.CxxLink;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.cxx.NativeLinkStrategy;
import com.facebook.buck.cxx.PrebuiltCxxLibraryBuilder;
import com.facebook.buck.io.AlwaysFoundExecutableFinder;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRules;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.CommandTool;
import com.facebook.buck.rules.DefaultBuildTargetSourcePath;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildContext;
import com.facebook.buck.rules.FakeBuildableContext;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.HashedFileTool;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.rules.keys.RuleKeyFieldLoader;
import com.facebook.buck.shell.Genrule;
import com.facebook.buck.shell.GenruleBuilder;
import com.facebook.buck.shell.ShBinary;
import com.facebook.buck.shell.ShBinaryBuilder;
import com.facebook.buck.step.Step;
import com.facebook.buck.testutil.AllExistingProjectFilesystem;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.cache.DefaultFileHashCache;
import com.facebook.buck.util.cache.StackedFileHashCache;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.Test;

public class PythonBinaryDescriptionTest {

  private static final BuildTarget PYTHON2_DEP_TARGET =
      BuildTargetFactory.newInstance("//:python2_dep");
  private static final PythonPlatform PY2 =
      PythonPlatform.of(
          InternalFlavor.of("py2"),
          new PythonEnvironment(Paths.get("python2"), PythonVersion.of("CPython", "2.6")),
          Optional.of(PYTHON2_DEP_TARGET));

  @Test
  public void thatComponentSourcePathDepsPropagateProperly() throws Exception {
    GenruleBuilder genruleBuilder =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:gen"))
            .setOut("blah.py");
    PythonLibraryBuilder libBuilder =
        new PythonLibraryBuilder(BuildTargetFactory.newInstance("//:lib"))
            .setSrcs(
                SourceList.ofUnnamedSources(
                    ImmutableSortedSet.of(
                        new DefaultBuildTargetSourcePath(genruleBuilder.getTarget()))));
    PythonBinaryBuilder binaryBuilder =
        PythonBinaryBuilder.create(BuildTargetFactory.newInstance("//:bin"))
            .setMainModule("main")
            .setDeps(ImmutableSortedSet.of(libBuilder.getTarget()));

    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            genruleBuilder.build(), libBuilder.build(), binaryBuilder.build());
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());

    Genrule genrule = genruleBuilder.build(resolver, filesystem, targetGraph);
    libBuilder.build(resolver, filesystem, targetGraph);
    PythonBinary binary = binaryBuilder.build(resolver, filesystem, targetGraph);
    assertThat(binary.getBuildDeps(), Matchers.hasItem(genrule));
  }

  @Test
  public void thatMainSourcePathPropagatesToDeps() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    Genrule genrule =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:gen"))
            .setOut("blah.py")
            .build(resolver);
    PythonBinary binary =
        PythonBinaryBuilder.create(BuildTargetFactory.newInstance("//:bin"))
            .setMain(genrule.getSourcePathToOutput())
            .build(resolver);
    assertThat(binary.getBuildDeps(), Matchers.hasItem(genrule));
  }

  @Test
  public void baseModule() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    String sourceName = "main.py";
    SourcePath source = new FakeSourcePath("foo/" + sourceName);

    // Run without a base module set and verify it defaults to using the build target
    // base name.
    PythonBinary normal =
        PythonBinaryBuilder.create(target)
            .setMain(source)
            .build(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
    assertThat(
        normal.getComponents().getModules().keySet(),
        Matchers.hasItem(target.getBasePath().resolve(sourceName)));

    // Run *with* a base module set and verify it gets used to build the main module path.
    String baseModule = "blah";
    PythonBinary withBaseModule =
        PythonBinaryBuilder.create(target)
            .setMain(source)
            .setBaseModule(baseModule)
            .build(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
    assertThat(
        withBaseModule.getComponents().getModules().keySet(),
        Matchers.hasItem(Paths.get(baseModule).resolve(sourceName)));
  }

  @Test
  public void mainModule() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    String mainModule = "foo.main";
    PythonBinary binary =
        PythonBinaryBuilder.create(target).setMainModule(mainModule).build(resolver);
    assertThat(mainModule, Matchers.equalTo(binary.getMainModule()));
  }

  @Test
  public void extensionConfig() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    PythonBuckConfig config =
        new PythonBuckConfig(
            FakeBuckConfig.builder()
                .setSections(
                    ImmutableMap.of(
                        "python", ImmutableMap.of("pex_extension", ".different_extension")))
                .build(),
            new AlwaysFoundExecutableFinder());
    PythonBinaryBuilder builder =
        new PythonBinaryBuilder(
            target,
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    PythonBinary binary = builder.setMainModule("main").build(resolver);
    assertThat(
        pathResolver
            .getRelativePath(Preconditions.checkNotNull(binary.getSourcePathToOutput()))
            .toString(),
        Matchers.endsWith(".different_extension"));
  }

  @Test
  public void extensionParameter() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    PythonBinaryBuilder builder = PythonBinaryBuilder.create(target);
    PythonBinary binary =
        builder.setMainModule("main").setExtension(".different_extension").build(resolver);
    assertThat(
        pathResolver
            .getRelativePath(Preconditions.checkNotNull(binary.getSourcePathToOutput()))
            .toString(),
        Matchers.endsWith(".different_extension"));
  }

  @Test
  public void buildArgs() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    ImmutableList<String> buildArgs = ImmutableList.of("--some", "--args");
    PythonBinary binary =
        PythonBinaryBuilder.create(target)
            .setMainModule("main")
            .setBuildArgs(buildArgs)
            .build(resolver);
    ImmutableList<? extends Step> buildSteps =
        binary.getBuildSteps(
            FakeBuildContext.withSourcePathResolver(pathResolver), new FakeBuildableContext());
    PexStep pexStep = FluentIterable.from(buildSteps).filter(PexStep.class).get(0);
    assertThat(
        pexStep.getCommandPrefix(),
        Matchers.hasItems(buildArgs.toArray(new String[buildArgs.size()])));
  }

  @Test
  public void explicitPythonHome() throws Exception {
    PythonPlatform platform1 =
        PythonPlatform.of(
            InternalFlavor.of("pyPlat1"),
            new PythonEnvironment(Paths.get("python2.6"), PythonVersion.of("CPython", "2.6.9")),
            Optional.empty());
    PythonPlatform platform2 =
        PythonPlatform.of(
            InternalFlavor.of("pyPlat2"),
            new PythonEnvironment(Paths.get("python2.7"), PythonVersion.of("CPython", "2.7.11")),
            Optional.empty());
    PythonBinaryBuilder builder =
        PythonBinaryBuilder.create(
            BuildTargetFactory.newInstance("//:bin"),
            FlavorDomain.of("Python Platform", platform1, platform2));
    builder.setMainModule("main");
    PythonBinary binary1 =
        builder
            .setPlatform(platform1.getFlavor().toString())
            .build(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
    assertThat(binary1.getPythonPlatform(), Matchers.equalTo(platform1));
    PythonBinary binary2 =
        builder
            .setPlatform(platform2.getFlavor().toString())
            .build(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
    assertThat(binary2.getPythonPlatform(), Matchers.equalTo(platform2));
  }

  @Test
  public void runtimeDepOnDeps() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    for (PythonBuckConfig.PackageStyle packageStyle : PythonBuckConfig.PackageStyle.values()) {
      CxxBinaryBuilder cxxBinaryBuilder =
          new CxxBinaryBuilder(BuildTargetFactory.newInstance("//:dep"));
      PythonLibraryBuilder pythonLibraryBuilder =
          new PythonLibraryBuilder(BuildTargetFactory.newInstance("//:lib"))
              .setSrcs(
                  SourceList.ofUnnamedSources(
                      ImmutableSortedSet.of(new FakeSourcePath("something.py"))))
              .setDeps(ImmutableSortedSet.of(cxxBinaryBuilder.getTarget()));
      PythonBinaryBuilder pythonBinaryBuilder =
          PythonBinaryBuilder.create(BuildTargetFactory.newInstance("//:bin"))
              .setMainModule("main")
              .setDeps(ImmutableSortedSet.of(pythonLibraryBuilder.getTarget()))
              .setPackageStyle(packageStyle);
      TargetGraph targetGraph =
          TargetGraphFactory.newInstance(
              cxxBinaryBuilder.build(), pythonLibraryBuilder.build(), pythonBinaryBuilder.build());
      BuildRuleResolver resolver =
          new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
      BuildRule cxxBinary = cxxBinaryBuilder.build(resolver, filesystem, targetGraph);
      pythonLibraryBuilder.build(resolver, filesystem, targetGraph);
      PythonBinary pythonBinary = pythonBinaryBuilder.build(resolver, filesystem, targetGraph);
      assertThat(
          String.format(
              "Transitive runtime deps of %s [%s]", pythonBinary, packageStyle.toString()),
          BuildRules.getTransitiveRuntimeDeps(pythonBinary, resolver),
          Matchers.hasItem(cxxBinary.getBuildTarget()));
    }
  }

  @Test
  public void executableCommandWithPathToPexExecutor() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    final Path executor = Paths.get("executor");
    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public Optional<Tool> getPexExecutor(BuildRuleResolver resolver) {
            return Optional.of(new HashedFileTool(executor));
          }
        };
    PythonBinaryBuilder builder =
        new PythonBinaryBuilder(
            target,
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    PythonPackagedBinary binary =
        (PythonPackagedBinary) builder.setMainModule("main").build(resolver);
    assertThat(
        binary.getExecutableCommand().getCommandPrefix(pathResolver),
        Matchers.contains(
            executor.toString(),
            pathResolver.getAbsolutePath(binary.getSourcePathToOutput()).toString()));
  }

  @Test
  public void executableCommandWithNoPathToPexExecutor() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    PythonPackagedBinary binary =
        (PythonPackagedBinary)
            PythonBinaryBuilder.create(target).setMainModule("main").build(resolver);
    assertThat(
        binary.getExecutableCommand().getCommandPrefix(pathResolver),
        Matchers.contains(
            PythonTestUtils.PYTHON_PLATFORM.getEnvironment().getPythonPath().toString(),
            pathResolver.getAbsolutePath(binary.getSourcePathToOutput()).toString()));
  }

  @Test
  public void packagedBinaryAttachedPexToolDeps() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bin");
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    final Genrule pexTool =
        GenruleBuilder.newGenruleBuilder(BuildTargetFactory.newInstance("//:pex_tool"))
            .setOut("pex-tool")
            .build(resolver);
    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public PackageStyle getPackageStyle() {
            return PackageStyle.STANDALONE;
          }

          @Override
          public Tool getPexTool(BuildRuleResolver resolver) {
            return new CommandTool.Builder()
                .addArg(SourcePathArg.of(pexTool.getSourcePathToOutput()))
                .build();
          }
        };
    PythonBinaryBuilder builder =
        new PythonBinaryBuilder(
            target,
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    PythonPackagedBinary binary =
        (PythonPackagedBinary) builder.setMainModule("main").build(resolver);
    assertThat(binary.getBuildDeps(), Matchers.hasItem(pexTool));
  }

  @Test
  public void transitiveNativeDepsUsingMergedNativeLinkStrategy() throws Exception {
    CxxLibraryBuilder transitiveCxxDepBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:transitive_dep"))
            .setSrcs(
                ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("transitive_dep.c"))));
    CxxLibraryBuilder cxxDepBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("dep.c"))))
            .setDeps(ImmutableSortedSet.of(transitiveCxxDepBuilder.getTarget()));
    CxxLibraryBuilder cxxBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:cxx"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("cxx.c"))))
            .setDeps(ImmutableSortedSet.of(cxxDepBuilder.getTarget()));

    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public NativeLinkStrategy getNativeLinkStrategy() {
            return NativeLinkStrategy.MERGED;
          }
        };
    PythonBinaryBuilder binaryBuilder =
        new PythonBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    binaryBuilder.setMainModule("main");
    binaryBuilder.setDeps(ImmutableSortedSet.of(cxxBuilder.getTarget()));

    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                transitiveCxxDepBuilder.build(),
                cxxDepBuilder.build(),
                cxxBuilder.build(),
                binaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    transitiveCxxDepBuilder.build(resolver);
    cxxDepBuilder.build(resolver);
    cxxBuilder.build(resolver);
    PythonBinary binary = binaryBuilder.build(resolver);
    assertThat(
        Iterables.transform(binary.getComponents().getNativeLibraries().keySet(), Object::toString),
        Matchers.containsInAnyOrder("libomnibus.so", "libcxx.so"));
  }

  @Test
  public void transitiveNativeDepsUsingSeparateNativeLinkStrategy() throws Exception {
    CxxLibraryBuilder transitiveCxxDepBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:transitive_dep"))
            .setSrcs(
                ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("transitive_dep.c"))));
    CxxLibraryBuilder cxxDepBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("dep.c"))))
            .setDeps(ImmutableSortedSet.of(transitiveCxxDepBuilder.getTarget()));
    CxxLibraryBuilder cxxBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:cxx"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("cxx.c"))))
            .setDeps(ImmutableSortedSet.of(cxxDepBuilder.getTarget()));

    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public NativeLinkStrategy getNativeLinkStrategy() {
            return NativeLinkStrategy.SEPARATE;
          }
        };
    PythonBinaryBuilder binaryBuilder =
        new PythonBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    binaryBuilder.setMainModule("main");
    binaryBuilder.setDeps(ImmutableSortedSet.of(cxxBuilder.getTarget()));

    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                transitiveCxxDepBuilder.build(),
                cxxDepBuilder.build(),
                cxxBuilder.build(),
                binaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    transitiveCxxDepBuilder.build(resolver);
    cxxDepBuilder.build(resolver);
    cxxBuilder.build(resolver);
    PythonBinary binary = binaryBuilder.build(resolver);
    assertThat(
        Iterables.transform(binary.getComponents().getNativeLibraries().keySet(), Object::toString),
        Matchers.containsInAnyOrder("libtransitive_dep.so", "libdep.so", "libcxx.so"));
  }

  @Test
  public void extensionDepUsingMergedNativeLinkStrategy() throws Exception {
    FlavorDomain<PythonPlatform> pythonPlatforms = FlavorDomain.of("Python Platform", PY2);

    PrebuiltCxxLibraryBuilder python2Builder =
        new PrebuiltCxxLibraryBuilder(PYTHON2_DEP_TARGET)
            .setProvided(true)
            .setExportedLinkerFlags(ImmutableList.of("-lpython2"));

    CxxPythonExtensionBuilder extensionBuilder =
        new CxxPythonExtensionBuilder(
            BuildTargetFactory.newInstance("//:extension"),
            pythonPlatforms,
            new CxxBuckConfig(FakeBuckConfig.builder().build()),
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    extensionBuilder.setBaseModule("hello");

    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public NativeLinkStrategy getNativeLinkStrategy() {
            return NativeLinkStrategy.MERGED;
          }
        };
    PythonBinaryBuilder binaryBuilder =
        new PythonBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            config,
            pythonPlatforms,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    binaryBuilder.setMainModule("main");
    binaryBuilder.setDeps(ImmutableSortedSet.of(extensionBuilder.getTarget()));

    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            python2Builder.build(), extensionBuilder.build(), binaryBuilder.build());
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());

    python2Builder.build(resolver, filesystem, targetGraph);
    extensionBuilder.build(resolver, filesystem, targetGraph);
    PythonBinary binary = binaryBuilder.build(resolver, filesystem, targetGraph);

    assertThat(binary.getComponents().getNativeLibraries().entrySet(), Matchers.empty());
    assertThat(
        Iterables.transform(binary.getComponents().getModules().keySet(), Object::toString),
        Matchers.containsInAnyOrder(MorePaths.pathWithPlatformSeparators("hello/extension.so")));
  }

  @Test
  public void transitiveDepsOfLibsWithPrebuiltNativeLibsAreNotIncludedInMergedLink()
      throws Exception {
    CxxLibraryBuilder transitiveCxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:transitive_cxx"))
            .setSrcs(
                ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("transitive_cxx.c"))));
    CxxLibraryBuilder cxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:cxx"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("cxx.c"))))
            .setDeps(ImmutableSortedSet.of(transitiveCxxLibraryBuilder.getTarget()));
    PythonLibraryBuilder pythonLibraryBuilder =
        new PythonLibraryBuilder(BuildTargetFactory.newInstance("//:lib"))
            .setSrcs(
                SourceList.ofUnnamedSources(
                    ImmutableSortedSet.of(new FakeSourcePath("prebuilt.so"))))
            .setDeps(ImmutableSortedSet.of(cxxLibraryBuilder.getTarget()));
    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public NativeLinkStrategy getNativeLinkStrategy() {
            return NativeLinkStrategy.MERGED;
          }
        };
    PythonBinaryBuilder pythonBinaryBuilder =
        new PythonBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    pythonBinaryBuilder.setMainModule("main");
    pythonBinaryBuilder.setDeps(ImmutableSortedSet.of(pythonLibraryBuilder.getTarget()));
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            transitiveCxxLibraryBuilder.build(),
            cxxLibraryBuilder.build(),
            pythonLibraryBuilder.build(),
            pythonBinaryBuilder.build());
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    transitiveCxxLibraryBuilder.build(resolver, filesystem, targetGraph);
    cxxLibraryBuilder.build(resolver, filesystem, targetGraph);
    pythonLibraryBuilder.build(resolver, filesystem, targetGraph);
    PythonBinary binary = pythonBinaryBuilder.build(resolver, filesystem, targetGraph);
    assertThat(
        Iterables.transform(binary.getComponents().getNativeLibraries().keySet(), Object::toString),
        Matchers.hasItem("libtransitive_cxx.so"));
  }

  @Test
  public void packageStyleParam() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    PythonBinary pythonBinary =
        PythonBinaryBuilder.create(BuildTargetFactory.newInstance("//:bin"))
            .setMainModule("main")
            .setPackageStyle(PythonBuckConfig.PackageStyle.INPLACE)
            .build(resolver);
    assertThat(pythonBinary, Matchers.instanceOf(PythonInPlaceBinary.class));
    resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    pythonBinary =
        PythonBinaryBuilder.create(BuildTargetFactory.newInstance("//:bin"))
            .setMainModule("main")
            .setPackageStyle(PythonBuckConfig.PackageStyle.STANDALONE)
            .build(resolver);
    assertThat(pythonBinary, Matchers.instanceOf(PythonPackagedBinary.class));
  }

  @Test
  public void preloadLibraries() throws Exception {
    for (final NativeLinkStrategy strategy : NativeLinkStrategy.values()) {
      CxxLibraryBuilder cxxLibraryBuilder =
          new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
              .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("test.c"))));
      PythonBuckConfig config =
          new PythonBuckConfig(
              FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
            @Override
            public NativeLinkStrategy getNativeLinkStrategy() {
              return strategy;
            }
          };
      PythonBinaryBuilder binaryBuilder =
          new PythonBinaryBuilder(
              BuildTargetFactory.newInstance("//:bin"),
              config,
              PythonTestUtils.PYTHON_PLATFORMS,
              CxxPlatformUtils.DEFAULT_PLATFORM,
              CxxPlatformUtils.DEFAULT_PLATFORMS);
      binaryBuilder.setMainModule("main");
      binaryBuilder.setPreloadDeps(ImmutableSortedSet.of(cxxLibraryBuilder.getTarget()));
      BuildRuleResolver resolver =
          new BuildRuleResolver(
              TargetGraphFactory.newInstance(cxxLibraryBuilder.build(), binaryBuilder.build()),
              new DefaultTargetNodeToBuildRuleTransformer());
      cxxLibraryBuilder.build(resolver);
      PythonBinary binary = binaryBuilder.build(resolver);
      assertThat("Using " + strategy, binary.getPreloadLibraries(), Matchers.hasItems("libdep.so"));
      assertThat(
          "Using " + strategy,
          binary.getComponents().getNativeLibraries().keySet(),
          Matchers.hasItems(Paths.get("libdep.so")));
    }
  }

  @Test
  public void pexExecutorRuleIsAddedToParseTimeDeps() throws Exception {
    ShBinaryBuilder pexExecutorBuilder =
        new ShBinaryBuilder(BuildTargetFactory.newInstance("//:pex_executor"))
            .setMain(new FakeSourcePath("run.sh"));
    PythonBinaryBuilder builder =
        new PythonBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            new PythonBuckConfig(
                FakeBuckConfig.builder()
                    .setSections(
                        ImmutableMap.of(
                            "python",
                            ImmutableMap.of(
                                "path_to_pex_executer", pexExecutorBuilder.getTarget().toString())))
                    .build(),
                new AlwaysFoundExecutableFinder()),
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    builder.setMainModule("main").setPackageStyle(PythonBuckConfig.PackageStyle.STANDALONE);
    assertThat(builder.build().getExtraDeps(), Matchers.hasItem(pexExecutorBuilder.getTarget()));
  }

  @Test
  public void linkerFlagsUsingMergedNativeLinkStrategy() throws Exception {
    CxxLibraryBuilder cxxDepBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("dep.c"))));
    CxxLibraryBuilder cxxBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:cxx"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("cxx.c"))))
            .setDeps(ImmutableSortedSet.of(cxxDepBuilder.getTarget()));

    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public NativeLinkStrategy getNativeLinkStrategy() {
            return NativeLinkStrategy.MERGED;
          }
        };
    PythonBinaryBuilder binaryBuilder =
        new PythonBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    binaryBuilder.setLinkerFlags(ImmutableList.of("-flag"));
    binaryBuilder.setMainModule("main");
    binaryBuilder.setDeps(ImmutableSortedSet.of(cxxBuilder.getTarget()));

    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                cxxDepBuilder.build(), cxxBuilder.build(), binaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    cxxDepBuilder.build(resolver);
    cxxBuilder.build(resolver);
    PythonBinary binary = binaryBuilder.build(resolver);
    for (SourcePath path : binary.getComponents().getNativeLibraries().values()) {
      CxxLink link =
          resolver
              .getRuleOptionalWithType(((BuildTargetSourcePath) path).getTarget(), CxxLink.class)
              .get();
      assertThat(Arg.stringify(link.getArgs(), pathResolver), Matchers.hasItem("-flag"));
    }
  }

  @Test
  public void explicitDepOnlinkWholeLibPullsInSharedLibrary() throws Exception {
    for (final NativeLinkStrategy strategy : NativeLinkStrategy.values()) {
      ProjectFilesystem filesystem = new AllExistingProjectFilesystem();
      CxxLibraryBuilder cxxLibraryBuilder =
          new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep1"))
              .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("test.c"))))
              .setForceStatic(true);
      PrebuiltCxxLibraryBuilder prebuiltCxxLibraryBuilder =
          new PrebuiltCxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep2"))
              .setForceStatic(true);
      PythonBuckConfig config =
          new PythonBuckConfig(
              FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
            @Override
            public NativeLinkStrategy getNativeLinkStrategy() {
              return strategy;
            }
          };
      PythonBinaryBuilder binaryBuilder =
          new PythonBinaryBuilder(
              BuildTargetFactory.newInstance("//:bin"),
              config,
              PythonTestUtils.PYTHON_PLATFORMS,
              CxxPlatformUtils.DEFAULT_PLATFORM,
              CxxPlatformUtils.DEFAULT_PLATFORMS);
      binaryBuilder.setMainModule("main");
      binaryBuilder.setDeps(
          ImmutableSortedSet.of(
              cxxLibraryBuilder.getTarget(), prebuiltCxxLibraryBuilder.getTarget()));
      TargetGraph targetGraph =
          TargetGraphFactory.newInstance(
              cxxLibraryBuilder.build(), prebuiltCxxLibraryBuilder.build(), binaryBuilder.build());
      BuildRuleResolver resolver =
          new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
      cxxLibraryBuilder.build(resolver, filesystem, targetGraph);
      prebuiltCxxLibraryBuilder.build(resolver, filesystem, targetGraph);
      PythonBinary binary = binaryBuilder.build(resolver, filesystem, targetGraph);
      assertThat(
          "Using " + strategy,
          binary.getComponents().getNativeLibraries().keySet(),
          Matchers.hasItems(Paths.get("libdep1.so"), Paths.get("libdep2.so")));
    }
  }

  @Test
  public void transitiveDepsOfPreloadDepsAreExcludedFromMergedNativeLinkStrategy()
      throws Exception {
    CxxLibraryBuilder transitiveCxxDepBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:transitive_dep"))
            .setSrcs(
                ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("transitive_dep.c"))));
    CxxLibraryBuilder cxxDepBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("dep.c"))))
            .setDeps(ImmutableSortedSet.of(transitiveCxxDepBuilder.getTarget()));
    CxxLibraryBuilder cxxBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:cxx"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("cxx.c"))))
            .setDeps(ImmutableSortedSet.of(cxxDepBuilder.getTarget()));
    CxxLibraryBuilder preloadCxxBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:preload_cxx"))
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("preload_cxx.c"))))
            .setDeps(ImmutableSortedSet.of(transitiveCxxDepBuilder.getTarget()));

    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public NativeLinkStrategy getNativeLinkStrategy() {
            return NativeLinkStrategy.MERGED;
          }
        };
    PythonBinaryBuilder binaryBuilder =
        new PythonBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            config,
            PythonTestUtils.PYTHON_PLATFORMS,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS);
    binaryBuilder.setMainModule("main");
    binaryBuilder.setDeps(ImmutableSortedSet.of(cxxBuilder.getTarget()));
    binaryBuilder.setPreloadDeps(ImmutableSet.of(preloadCxxBuilder.getTarget()));

    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                transitiveCxxDepBuilder.build(),
                cxxDepBuilder.build(),
                cxxBuilder.build(),
                preloadCxxBuilder.build(),
                binaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    transitiveCxxDepBuilder.build(resolver);
    cxxDepBuilder.build(resolver);
    cxxBuilder.build(resolver);
    preloadCxxBuilder.build(resolver);
    PythonBinary binary = binaryBuilder.build(resolver);
    assertThat(
        Iterables.transform(binary.getComponents().getNativeLibraries().keySet(), Object::toString),
        Matchers.containsInAnyOrder(
            "libomnibus.so", "libcxx.so", "libpreload_cxx.so", "libtransitive_dep.so"));
  }

  @Test
  public void pexBuilderAddedToParseTimeDeps() {
    final BuildTarget pexBuilder = BuildTargetFactory.newInstance("//:pex_builder");
    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public Optional<BuildTarget> getPexExecutorTarget() {
            return Optional.of(pexBuilder);
          }
        };

    PythonBinaryBuilder inplaceBinary =
        new PythonBinaryBuilder(
                BuildTargetFactory.newInstance("//:bin"),
                config,
                PythonTestUtils.PYTHON_PLATFORMS,
                CxxPlatformUtils.DEFAULT_PLATFORM,
                CxxPlatformUtils.DEFAULT_PLATFORMS)
            .setPackageStyle(PythonBuckConfig.PackageStyle.INPLACE);
    assertThat(inplaceBinary.findImplicitDeps(), Matchers.not(Matchers.hasItem(pexBuilder)));

    PythonBinaryBuilder standaloneBinary =
        new PythonBinaryBuilder(
                BuildTargetFactory.newInstance("//:bin"),
                config,
                PythonTestUtils.PYTHON_PLATFORMS,
                CxxPlatformUtils.DEFAULT_PLATFORM,
                CxxPlatformUtils.DEFAULT_PLATFORMS)
            .setPackageStyle(PythonBuckConfig.PackageStyle.STANDALONE);
    assertThat(standaloneBinary.findImplicitDeps(), Matchers.hasItem(pexBuilder));
  }

  @Test
  public void pexToolBuilderAddedToRuntimeDeps() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(), new DefaultTargetNodeToBuildRuleTransformer());

    ShBinary pyTool =
        new ShBinaryBuilder(BuildTargetFactory.newInstance("//:py_tool"))
            .setMain(new FakeSourcePath("run.sh"))
            .build(resolver);

    PythonBuckConfig config =
        new PythonBuckConfig(FakeBuckConfig.builder().build(), new AlwaysFoundExecutableFinder()) {
          @Override
          public Optional<Tool> getPexExecutor(BuildRuleResolver resolver) {
            return Optional.of(pyTool.getExecutableCommand());
          }
        };

    PythonBinary standaloneBinary =
        new PythonBinaryBuilder(
                BuildTargetFactory.newInstance("//:bin"),
                config,
                PythonTestUtils.PYTHON_PLATFORMS,
                CxxPlatformUtils.DEFAULT_PLATFORM,
                CxxPlatformUtils.DEFAULT_PLATFORMS)
            .setMainModule("hello")
            .setPackageStyle(PythonBuckConfig.PackageStyle.STANDALONE)
            .build(resolver);
    assertThat(
        standaloneBinary.getRuntimeDeps().collect(MoreCollectors.toImmutableSet()),
        Matchers.hasItem(pyTool.getBuildTarget()));
  }

  @Test
  public void targetGraphOnlyDepsDoNotAffectRuleKey() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    for (PythonBuckConfig.PackageStyle packageStyle : PythonBuckConfig.PackageStyle.values()) {

      // First, calculate the rule key of a python binary with no deps.
      PythonBinaryBuilder pythonBinaryBuilder =
          PythonBinaryBuilder.create(BuildTargetFactory.newInstance("//:bin"))
              .setMainModule("main")
              .setPackageStyle(packageStyle);
      TargetGraph targetGraph = TargetGraphFactory.newInstance(pythonBinaryBuilder.build());
      BuildRuleResolver resolver =
          new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
      PythonBinary pythonBinaryWithoutDep =
          pythonBinaryBuilder.build(resolver, filesystem, targetGraph);
      RuleKey ruleKeyWithoutDep = calculateRuleKey(resolver, pythonBinaryWithoutDep);

      // Next, calculate the rule key of a python binary with a deps on another binary.
      CxxBinaryBuilder cxxBinaryBuilder =
          new CxxBinaryBuilder(BuildTargetFactory.newInstance("//:dep"));
      pythonBinaryBuilder.setDeps(ImmutableSortedSet.of(cxxBinaryBuilder.getTarget()));
      targetGraph =
          TargetGraphFactory.newInstance(cxxBinaryBuilder.build(), pythonBinaryBuilder.build());
      resolver = new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
      cxxBinaryBuilder.build(resolver, filesystem, targetGraph);
      PythonBinary pythonBinaryWithDep =
          pythonBinaryBuilder.build(resolver, filesystem, targetGraph);
      RuleKey ruleKeyWithDep = calculateRuleKey(resolver, pythonBinaryWithDep);

      // Verify that the rule keys are identical.
      assertThat(ruleKeyWithoutDep, Matchers.equalTo(ruleKeyWithDep));
    }
  }

  @Test
  public void platformDeps() throws Exception {
    SourcePath libASrc = new FakeSourcePath("libA.py");
    PythonLibraryBuilder libraryABuilder =
        PythonLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:libA"))
            .setSrcs(SourceList.ofUnnamedSources(ImmutableSortedSet.of(libASrc)));
    SourcePath libBSrc = new FakeSourcePath("libB.py");
    PythonLibraryBuilder libraryBBuilder =
        PythonLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:libB"))
            .setSrcs(SourceList.ofUnnamedSources(ImmutableSortedSet.of(libBSrc)));
    PythonBinaryBuilder binaryBuilder =
        PythonBinaryBuilder.create(BuildTargetFactory.newInstance("//:bin"))
            .setMainModule("main")
            .setPlatformDeps(
                PatternMatchedCollection.<ImmutableSortedSet<BuildTarget>>builder()
                    .add(
                        Pattern.compile(
                            CxxPlatformUtils.DEFAULT_PLATFORM.getFlavor().toString(),
                            Pattern.LITERAL),
                        ImmutableSortedSet.of(libraryABuilder.getTarget()))
                    .add(
                        Pattern.compile("matches nothing", Pattern.LITERAL),
                        ImmutableSortedSet.of(libraryBBuilder.getTarget()))
                    .build());
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            libraryABuilder.build(), libraryBBuilder.build(), binaryBuilder.build());
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    PythonBinary binary = (PythonBinary) resolver.requireRule(binaryBuilder.getTarget());
    assertThat(
        binary.getComponents().getModules().values(),
        Matchers.allOf(Matchers.hasItem(libASrc), Matchers.not(Matchers.hasItem(libBSrc))));
  }

  @Test
  public void cxxPlatform() throws Exception {
    CxxPlatform platformA =
        CxxPlatformUtils.DEFAULT_PLATFORM.withFlavor(InternalFlavor.of("platA"));
    CxxPlatform platformB =
        CxxPlatformUtils.DEFAULT_PLATFORM.withFlavor(InternalFlavor.of("platB"));
    FlavorDomain<CxxPlatform> cxxPlatforms =
        FlavorDomain.from("C/C++ platform", ImmutableList.of(platformA, platformB));
    SourcePath libASrc = new FakeSourcePath("libA.py");
    PythonLibraryBuilder libraryABuilder =
        new PythonLibraryBuilder(
                BuildTargetFactory.newInstance("//:libA"),
                PythonTestUtils.PYTHON_PLATFORMS,
                cxxPlatforms)
            .setSrcs(SourceList.ofUnnamedSources(ImmutableSortedSet.of(libASrc)));
    SourcePath libBSrc = new FakeSourcePath("libB.py");
    PythonLibraryBuilder libraryBBuilder =
        new PythonLibraryBuilder(
                BuildTargetFactory.newInstance("//:libB"),
                PythonTestUtils.PYTHON_PLATFORMS,
                cxxPlatforms)
            .setSrcs(SourceList.ofUnnamedSources(ImmutableSortedSet.of(libBSrc)));
    PythonBinaryBuilder binaryBuilder =
        new PythonBinaryBuilder(
                BuildTargetFactory.newInstance("//:bin"),
                PythonTestUtils.PYTHON_CONFIG,
                PythonTestUtils.PYTHON_PLATFORMS,
                CxxPlatformUtils.DEFAULT_PLATFORM,
                cxxPlatforms)
            .setMainModule("main")
            .setCxxPlatform(platformA.getFlavor())
            .setPlatformDeps(
                PatternMatchedCollection.<ImmutableSortedSet<BuildTarget>>builder()
                    .add(
                        Pattern.compile(platformA.getFlavor().toString(), Pattern.LITERAL),
                        ImmutableSortedSet.of(libraryABuilder.getTarget()))
                    .add(
                        Pattern.compile(platformB.getFlavor().toString(), Pattern.LITERAL),
                        ImmutableSortedSet.of(libraryBBuilder.getTarget()))
                    .build());
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            libraryABuilder.build(), libraryBBuilder.build(), binaryBuilder.build());
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    PythonBinary binary = (PythonBinary) resolver.requireRule(binaryBuilder.getTarget());
    assertThat(
        binary.getComponents().getModules().values(),
        Matchers.allOf(Matchers.hasItem(libASrc), Matchers.not(Matchers.hasItem(libBSrc))));
  }

  private RuleKey calculateRuleKey(BuildRuleResolver ruleResolver, BuildRule rule) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(ruleResolver);
    DefaultRuleKeyFactory ruleKeyFactory =
        new DefaultRuleKeyFactory(
            new RuleKeyFieldLoader(0),
            new StackedFileHashCache(
                ImmutableList.of(
                    DefaultFileHashCache.createDefaultFileHashCache(rule.getProjectFilesystem()))),
            new SourcePathResolver(ruleFinder),
            ruleFinder);
    return ruleKeyFactory.build(rule);
  }
}
