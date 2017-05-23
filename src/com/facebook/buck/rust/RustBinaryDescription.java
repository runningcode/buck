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

package com.facebook.buck.rust;

import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatforms;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorConvertible;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.Flavored;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.HasDeclaredDeps;
import com.facebook.buck.rules.HasSrcs;
import com.facebook.buck.rules.HasTests;
import com.facebook.buck.rules.ImplicitDepsInferringDescription;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.ToolProvider;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.versions.VersionRoot;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.immutables.value.Value;

public class RustBinaryDescription
    implements Description<RustBinaryDescriptionArg>,
        ImplicitDepsInferringDescription<RustBinaryDescription.AbstractRustBinaryDescriptionArg>,
        Flavored,
        VersionRoot<RustBinaryDescriptionArg> {

  public static final FlavorDomain<Type> BINARY_TYPE =
      FlavorDomain.from("Rust Binary Type", Type.class);

  private final RustBuckConfig rustBuckConfig;
  private final FlavorDomain<CxxPlatform> cxxPlatforms;
  private final CxxPlatform defaultCxxPlatform;

  public RustBinaryDescription(
      RustBuckConfig rustBuckConfig,
      FlavorDomain<CxxPlatform> cxxPlatforms,
      CxxPlatform defaultCxxPlatform) {
    this.rustBuckConfig = rustBuckConfig;
    this.cxxPlatforms = cxxPlatforms;
    this.defaultCxxPlatform = defaultCxxPlatform;
  }

  @Override
  public Class<RustBinaryDescriptionArg> getConstructorArgType() {
    return RustBinaryDescriptionArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      RustBinaryDescriptionArg args)
      throws NoSuchBuildTargetException {
    final BuildTarget buildTarget = params.getBuildTarget();

    Linker.LinkableDepType linkStyle =
        RustCompileUtils.getLinkStyle(params.getBuildTarget(), args.getLinkStyle());

    Optional<Map.Entry<Flavor, RustBinaryDescription.Type>> type =
        BINARY_TYPE.getFlavorAndValue(buildTarget);

    boolean isCheck = type.map(t -> t.getValue().isCheck()).orElse(false);

    return RustCompileUtils.createBinaryBuildRule(
        params,
        resolver,
        rustBuckConfig,
        cxxPlatforms,
        defaultCxxPlatform,
        args.getCrate(),
        args.getFeatures(),
        Stream.of(rustBuckConfig.getRustBinaryFlags().stream(), args.getRustcFlags().stream())
            .flatMap(x -> x)
            .iterator(),
        args.getLinkerFlags().iterator(),
        linkStyle,
        args.isRpath(),
        args.getSrcs(),
        args.getCrateRoot(),
        ImmutableSet.of("main.rs"),
        isCheck);
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      AbstractRustBinaryDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    ToolProvider compiler = rustBuckConfig.getRustCompiler();
    extraDepsBuilder.addAll(compiler.getParseTimeDeps());

    extraDepsBuilder.addAll(CxxPlatforms.getParseTimeDeps(cxxPlatforms.getValues()));
    extraDepsBuilder.addAll(
        rustBuckConfig.getLinker().map(ToolProvider::getParseTimeDeps).orElse(ImmutableList.of()));
  }

  @Override
  public boolean isVersionRoot(ImmutableSet<Flavor> flavors) {
    return true;
  }

  protected enum Type implements FlavorConvertible {
    CHECK(RustDescriptionEnhancer.RFCHECK, Linker.LinkableDepType.STATIC_PIC),
    SHARED(CxxDescriptionEnhancer.SHARED_FLAVOR, Linker.LinkableDepType.SHARED),
    STATIC_PIC(CxxDescriptionEnhancer.STATIC_PIC_FLAVOR, Linker.LinkableDepType.STATIC_PIC),
    STATIC(CxxDescriptionEnhancer.STATIC_FLAVOR, Linker.LinkableDepType.STATIC),
    ;

    private final Flavor flavor;
    private final Linker.LinkableDepType linkStyle;

    Type(Flavor flavor, Linker.LinkableDepType linkStyle) {
      this.flavor = flavor;
      this.linkStyle = linkStyle;
    }

    @Override
    public Flavor getFlavor() {
      return flavor;
    }

    public Linker.LinkableDepType getLinkStyle() {
      return linkStyle;
    }

    public boolean isCheck() {
      return flavor == RustDescriptionEnhancer.RFCHECK;
    }
  }

  @Override
  public boolean hasFlavors(ImmutableSet<Flavor> flavors) {
    if (cxxPlatforms.containsAnyOf(flavors)) {
      return true;
    }

    for (Type type : Type.values()) {
      if (flavors.contains(type.getFlavor())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains() {
    return Optional.of(ImmutableSet.of(cxxPlatforms, BINARY_TYPE));
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractRustBinaryDescriptionArg
      extends CommonDescriptionArg, HasDeclaredDeps, HasSrcs, HasTests {
    @Value.NaturalOrder
    ImmutableSortedSet<String> getFeatures();

    ImmutableList<String> getRustcFlags();

    ImmutableList<String> getLinkerFlags();

    Optional<Linker.LinkableDepType> getLinkStyle();

    Optional<String> getCrate();

    Optional<SourcePath> getCrateRoot();

    @Value.Default
    default boolean isRpath() {
      return true;
    }
  }
}
