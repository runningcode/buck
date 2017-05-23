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

package com.facebook.buck.python;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.NoopBuildRule;
import java.util.stream.Stream;

public class PythonLibrary extends NoopBuildRule implements PythonPackagable, HasRuntimeDeps {

  private final BuildRuleResolver resolver;

  PythonLibrary(BuildRuleParams params, BuildRuleResolver resolver) {
    super(params);
    this.resolver = resolver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<BuildRule> getPythonPackageDeps(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform) throws NoSuchBuildTargetException {
    return resolver
        .requireMetadata(
            getBuildTarget()
                .withAppendedFlavors(
                    PythonLibraryDescription.MetadataType.PACKAGE_DEPS.getFlavor(),
                    pythonPlatform.getFlavor(),
                    cxxPlatform.getFlavor()),
            Iterable.class)
        .orElseThrow(IllegalStateException::new);
  }

  @Override
  public PythonPackageComponents getPythonPackageComponents(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform) throws NoSuchBuildTargetException {
    return resolver
        .requireMetadata(
            getBuildTarget()
                .withAppendedFlavors(
                    PythonLibraryDescription.MetadataType.PACKAGE_COMPONENTS.getFlavor(),
                    pythonPlatform.getFlavor(),
                    cxxPlatform.getFlavor()),
            PythonPackageComponents.class)
        .orElseThrow(IllegalStateException::new);
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps() {
    return getDeclaredDeps().stream().map(BuildRule::getBuildTarget);
  }
}
