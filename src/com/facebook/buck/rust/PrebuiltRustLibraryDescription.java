/*
 * Copyright 2016-present Facebook, Inc.
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

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.cxx.NativeLinkable;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.CommonDescriptionArg;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.HasDeclaredDeps;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.facebook.buck.versions.VersionPropagator;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.immutables.value.Value;

public class PrebuiltRustLibraryDescription
    implements Description<PrebuiltRustLibraryDescriptionArg>,
        VersionPropagator<PrebuiltRustLibraryDescriptionArg> {

  @Override
  public Class<PrebuiltRustLibraryDescriptionArg> getConstructorArgType() {
    return PrebuiltRustLibraryDescriptionArg.class;
  }

  @Override
  public PrebuiltRustLibrary createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots,
      PrebuiltRustLibraryDescriptionArg args)
      throws NoSuchBuildTargetException {
    final SourcePathResolver pathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(resolver));

    return new PrebuiltRustLibrary(params, pathResolver) {

      @Override
      protected SourcePath getRlib() {
        return args.getRlib();
      }

      @Override
      public com.facebook.buck.rules.args.Arg getLinkerArg(
          boolean direct,
          boolean isCheck,
          CxxPlatform cxxPlatform,
          Linker.LinkableDepType depType) {
        return new RustLibraryArg(
            getResolver(), args.getCrate(), args.getRlib(), direct, getBuildDeps());
      }

      @Override
      public NativeLinkable.Linkage getPreferredLinkage() {
        return NativeLinkable.Linkage.STATIC;
      }

      @Override
      public ImmutableMap<String, SourcePath> getRustSharedLibraries(CxxPlatform cxxPlatform) {
        return ImmutableMap.of();
      }
    };
  }

  @BuckStyleImmutable
  @Value.Immutable
  interface AbstractPrebuiltRustLibraryDescriptionArg
      extends CommonDescriptionArg, HasDeclaredDeps {
    SourcePath getRlib();

    @Value.Default
    default String getCrate() {
      return getName();
    }

    Optional<Linker.LinkableDepType> getLinkStyle();
  }
}
