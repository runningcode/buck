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

package com.facebook.buck.lua;

import static org.junit.Assert.assertThat;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLibraryBuilder;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.cxx.CxxTestUtils;
import com.facebook.buck.cxx.NativeLinkStrategy;
import com.facebook.buck.cxx.PrebuiltCxxLibraryBuilder;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.python.CxxPythonExtensionBuilder;
import com.facebook.buck.python.PythonBinaryDescription;
import com.facebook.buck.python.PythonEnvironment;
import com.facebook.buck.python.PythonLibraryBuilder;
import com.facebook.buck.python.PythonPlatform;
import com.facebook.buck.python.PythonVersion;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CommandTool;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.SymlinkTree;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.MoreCollectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LuaBinaryDescriptionTest {

  private static final BuildTarget PYTHON2_DEP_TARGET =
      BuildTargetFactory.newInstance("//:python2_dep");
  private static final PythonPlatform PY2 =
      PythonPlatform.of(
          InternalFlavor.of("py2"),
          new PythonEnvironment(Paths.get("python2"), PythonVersion.of("CPython", "2.6")),
          Optional.of(PYTHON2_DEP_TARGET));

  private static final BuildTarget PYTHON3_DEP_TARGET =
      BuildTargetFactory.newInstance("//:python3_dep");
  private static final PythonPlatform PY3 =
      PythonPlatform.of(
          InternalFlavor.of("py3"),
          new PythonEnvironment(Paths.get("python3"), PythonVersion.of("CPython", "3.5")),
          Optional.of(PYTHON3_DEP_TARGET));

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void mainModule() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaBinary binary =
        new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setMainModule("hello.world")
            .build(resolver);
    assertThat(binary.getMainModule(), Matchers.equalTo("hello.world"));
  }

  @Test
  public void extensionOverride() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathResolver pathResolver = new SourcePathResolver(new SourcePathRuleFinder(resolver));
    LuaBinary binary =
        new LuaBinaryBuilder(
                BuildTargetFactory.newInstance("//:rule"),
                FakeLuaConfig.DEFAULT.withExtension(".override"))
            .setMainModule("main")
            .build(resolver);
    assertThat(
        pathResolver.getRelativePath(binary.getSourcePathToOutput()).toString(),
        Matchers.endsWith(".override"));
  }

  @Test
  public void toolOverride() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    Tool override = new CommandTool.Builder().addArg("override").build();
    LuaBinary binary =
        new LuaBinaryBuilder(
                BuildTargetFactory.newInstance("//:rule"),
                FakeLuaConfig.DEFAULT.withLua(override).withExtension(".override"))
            .setMainModule("main")
            .build(resolver);
    assertThat(binary.getLua(), Matchers.is(override));
  }

  @Test
  public void versionLessNativeLibraryExtension() throws Exception {
    CxxLibraryBuilder cxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:lib"))
            .setSoname("libfoo.so.1.0")
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("hello.c"))));
    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(
                BuildTargetFactory.newInstance("//:rule"),
                FakeLuaConfig.DEFAULT.withPackageStyle(LuaConfig.PackageStyle.INPLACE))
            .setMainModule("main")
            .setDeps(ImmutableSortedSet.of(cxxLibraryBuilder.getTarget()));
    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(cxxLibraryBuilder.build(), binaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    cxxLibraryBuilder.build(resolver);
    binaryBuilder.build(resolver);
    SymlinkTree tree =
        resolver.getRuleWithType(
            LuaBinaryDescription.getNativeLibsSymlinkTreeTarget(binaryBuilder.getTarget()),
            SymlinkTree.class);
    assertThat(
        tree.getLinks().keySet(),
        Matchers.hasItem(tree.getProjectFilesystem().getPath("libfoo.so")));
  }

  @Test
  public void duplicateIdenticalModules() throws Exception {
    LuaLibraryBuilder libraryABuilder =
        new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:a"))
            .setSrcs(ImmutableSortedMap.of("foo.lua", new FakeSourcePath("test")));
    LuaLibraryBuilder libraryBBuilder =
        new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:b"))
            .setSrcs(ImmutableSortedMap.of("foo.lua", new FakeSourcePath("test")));
    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setMainModule("hello.world")
            .setDeps(
                ImmutableSortedSet.of(libraryABuilder.getTarget(), libraryBBuilder.getTarget()));
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            libraryABuilder.build(), libraryBBuilder.build(), binaryBuilder.build());
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    libraryABuilder.build(resolver, filesystem, targetGraph);
    libraryBBuilder.build(resolver, filesystem, targetGraph);
    binaryBuilder.build(resolver, filesystem, targetGraph);
  }

  @Test
  public void duplicateConflictingModules() throws Exception {
    LuaLibraryBuilder libraryABuilder =
        new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:a"))
            .setSrcs(ImmutableSortedMap.of("foo.lua", new FakeSourcePath("foo")));
    LuaLibraryBuilder libraryBBuilder =
        new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:b"))
            .setSrcs(ImmutableSortedMap.of("foo.lua", new FakeSourcePath("bar")));
    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setMainModule("hello.world")
            .setDeps(
                ImmutableSortedSet.of(libraryABuilder.getTarget(), libraryBBuilder.getTarget()));
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(
            libraryABuilder.build(), libraryBBuilder.build(), binaryBuilder.build());
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    libraryABuilder.build(resolver, filesystem, targetGraph);
    libraryBBuilder.build(resolver, filesystem, targetGraph);
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(Matchers.containsString("conflicting modules for foo.lua"));
    binaryBuilder.build(resolver, filesystem, targetGraph);
  }

  @Test
  public void pythonDeps() throws Exception {
    PythonLibraryBuilder pythonLibraryBuilder =
        new PythonLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setSrcs(
                SourceList.ofUnnamedSources(ImmutableSortedSet.of(new FakeSourcePath("foo.py"))));
    LuaBinaryBuilder luaBinaryBuilder =
        new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setMainModule("hello.world")
            .setDeps(ImmutableSortedSet.of(pythonLibraryBuilder.getTarget()));
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(pythonLibraryBuilder.build(), luaBinaryBuilder.build());
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    pythonLibraryBuilder.build(resolver, filesystem, targetGraph);
    LuaBinary luaBinary = luaBinaryBuilder.build(resolver, filesystem, targetGraph);
    assertThat(luaBinary.getComponents().getPythonModules().keySet(), Matchers.hasItem("foo.py"));
  }

  @Test
  public void platformDeps() throws Exception {
    FlavorDomain<PythonPlatform> pythonPlatforms = FlavorDomain.of("Python Platform", PY2, PY3);
    CxxBuckConfig cxxBuckConfig = new CxxBuckConfig(FakeBuckConfig.builder().build());

    CxxLibraryBuilder py2LibBuilder = new CxxLibraryBuilder(PYTHON2_DEP_TARGET);
    CxxLibraryBuilder py3LibBuilder = new CxxLibraryBuilder(PYTHON3_DEP_TARGET);
    CxxLibraryBuilder py2CxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:py2_library"))
            .setSoname("libpy2.so")
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("hello.c"))));
    CxxLibraryBuilder py3CxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:py3_library"))
            .setSoname("libpy3.so")
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("hello.c"))));
    CxxPythonExtensionBuilder cxxPythonExtensionBuilder =
        new CxxPythonExtensionBuilder(
                BuildTargetFactory.newInstance("//:extension"),
                pythonPlatforms,
                cxxBuckConfig,
                CxxTestUtils.createDefaultPlatforms())
            .setPlatformDeps(
                PatternMatchedCollection.<ImmutableSortedSet<BuildTarget>>builder()
                    .add(
                        Pattern.compile(PY2.getFlavor().toString()),
                        ImmutableSortedSet.of(py2CxxLibraryBuilder.getTarget()))
                    .add(
                        Pattern.compile(PY3.getFlavor().toString()),
                        ImmutableSortedSet.of(py3CxxLibraryBuilder.getTarget()))
                    .build());
    LuaBinaryBuilder luaBinaryBuilder =
        new LuaBinaryBuilder(
                new LuaBinaryDescription(
                    FakeLuaConfig.DEFAULT,
                    cxxBuckConfig,
                    CxxPlatformUtils.DEFAULT_PLATFORM,
                    CxxPlatformUtils.DEFAULT_PLATFORMS,
                    pythonPlatforms),
                BuildTargetFactory.newInstance("//:binary"))
            .setMainModule("main")
            .setDeps(ImmutableSortedSet.of(cxxPythonExtensionBuilder.getTarget()));

    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                py2LibBuilder.build(),
                py3LibBuilder.build(),
                py2CxxLibraryBuilder.build(),
                py3CxxLibraryBuilder.build(),
                cxxPythonExtensionBuilder.build(),
                luaBinaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());

    py2LibBuilder.build(resolver);
    py3LibBuilder.build(resolver);
    py2CxxLibraryBuilder.build(resolver);
    py3CxxLibraryBuilder.build(resolver);
    cxxPythonExtensionBuilder.build(resolver);
    LuaBinary luaBinary = luaBinaryBuilder.build(resolver);

    LuaPackageComponents components = luaBinary.getComponents();
    assertThat(components.getNativeLibraries().keySet(), Matchers.hasItem("libpy2.so"));
    assertThat(
        components.getNativeLibraries().keySet(), Matchers.not(Matchers.hasItem("libpy3.so")));
  }

  @Test
  public void pythonInitIsRuntimeDepForInPlaceBinary() throws Exception {
    PythonLibraryBuilder pythonLibraryBuilder =
        new PythonLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setSrcs(
                SourceList.ofUnnamedSources(ImmutableSortedSet.of(new FakeSourcePath("foo.py"))));
    LuaBinaryBuilder luaBinaryBuilder =
        new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setMainModule("hello.world")
            .setPackageStyle(LuaConfig.PackageStyle.INPLACE)
            .setDeps(ImmutableSortedSet.of(pythonLibraryBuilder.getTarget()));
    TargetGraph targetGraph =
        TargetGraphFactory.newInstance(pythonLibraryBuilder.build(), luaBinaryBuilder.build());
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildRuleResolver resolver =
        new BuildRuleResolver(targetGraph, new DefaultTargetNodeToBuildRuleTransformer());
    pythonLibraryBuilder.build(resolver, filesystem, targetGraph);
    LuaBinary luaBinary = luaBinaryBuilder.build(resolver, filesystem, targetGraph);
    assertThat(
        luaBinary.getRuntimeDeps().collect(MoreCollectors.toImmutableSet()),
        Matchers.hasItem(PythonBinaryDescription.getEmptyInitTarget(luaBinary.getBuildTarget())));
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

    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            FakeLuaConfig.DEFAULT.withNativeLinkStrategy(NativeLinkStrategy.MERGED));
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
    LuaBinary binary = binaryBuilder.build(resolver);
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

    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            FakeLuaConfig.DEFAULT.withNativeLinkStrategy(NativeLinkStrategy.SEPARATE));
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
    LuaBinary binary = binaryBuilder.build(resolver);
    assertThat(
        Iterables.transform(binary.getComponents().getNativeLibraries().keySet(), Object::toString),
        Matchers.containsInAnyOrder("libtransitive_dep.so", "libdep.so", "libcxx.so"));
  }

  @Test
  public void transitiveDepsOfNativeStarterDepsAreIncludedInMergedNativeLinkStrategy()
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
    CxxLibraryBuilder nativeStarterCxxBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:native_starter"))
            .setSrcs(
                ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("native_starter.c"))))
            .setDeps(ImmutableSortedSet.of(transitiveCxxDepBuilder.getTarget()));

    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            FakeLuaConfig.DEFAULT.withNativeLinkStrategy(NativeLinkStrategy.MERGED));
    binaryBuilder.setMainModule("main");
    binaryBuilder.setDeps(ImmutableSortedSet.of(cxxBuilder.getTarget()));
    binaryBuilder.setNativeStarterLibrary(nativeStarterCxxBuilder.getTarget());

    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                transitiveCxxDepBuilder.build(),
                cxxDepBuilder.build(),
                cxxBuilder.build(),
                nativeStarterCxxBuilder.build(),
                binaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    transitiveCxxDepBuilder.build(resolver);
    cxxDepBuilder.build(resolver);
    cxxBuilder.build(resolver);
    nativeStarterCxxBuilder.build(resolver);
    LuaBinary binary = binaryBuilder.build(resolver);
    assertThat(
        Iterables.transform(binary.getComponents().getNativeLibraries().keySet(), Object::toString),
        Matchers.containsInAnyOrder("libomnibus.so", "libcxx.so"));
  }

  @Test
  public void pythonExtensionDepUsingMergedNativeLinkStrategy() throws Exception {
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

    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(
            BuildTargetFactory.newInstance("//:bin"),
            FakeLuaConfig.DEFAULT.withNativeLinkStrategy(NativeLinkStrategy.MERGED),
            CxxPlatformUtils.DEFAULT_CONFIG,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            CxxPlatformUtils.DEFAULT_PLATFORMS,
            pythonPlatforms);
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
    LuaBinary binary = binaryBuilder.build(resolver, filesystem, targetGraph);
    assertThat(binary.getComponents().getNativeLibraries().entrySet(), Matchers.empty());
    assertThat(
        Iterables.transform(binary.getComponents().getPythonModules().keySet(), Object::toString),
        Matchers.hasItem(MorePaths.pathWithPlatformSeparators("hello/extension.so")));
  }
}
