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

package com.facebook.buck.cxx;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultBuildTargetSourcePath;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.Test;

public class CxxDescriptionEnhancerTest {

  @Test
  public void libraryTestIncludesPrivateHeadersOfLibraryUnderTest() throws Exception {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));

    BuildTarget libTarget = BuildTargetFactory.newInstance("//:lib");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//:test");

    BuildRuleParams libParams = new FakeBuildRuleParamsBuilder(libTarget).build();
    FakeCxxLibrary libRule =
        new FakeCxxLibrary(
            libParams,
            BuildTargetFactory.newInstance("//:header"),
            BuildTargetFactory.newInstance("//:symlink"),
            BuildTargetFactory.newInstance("//:privateheader"),
            BuildTargetFactory.newInstance("//:privatesymlink"),
            new FakeBuildRule("//:archive", pathResolver),
            new FakeBuildRule("//:shared", pathResolver),
            Paths.get("output/path/lib.so"),
            "lib.so",
            // Ensure the test is listed as a dep of the lib.
            ImmutableSortedSet.of(testTarget));

    BuildRuleParams testParams =
        new FakeBuildRuleParamsBuilder(testTarget)
            .setDeclaredDeps(ImmutableSortedSet.of(libRule))
            .build();

    ImmutableList<CxxPreprocessorInput> combinedInput =
        CxxDescriptionEnhancer.collectCxxPreprocessorInput(
            testParams,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            testParams.getBuildDeps(),
            ImmutableMultimap.of(),
            ImmutableList.of(),
            ImmutableSet.of(),
            CxxPreprocessables.getTransitiveCxxPreprocessorInput(
                CxxPlatformUtils.DEFAULT_PLATFORM,
                FluentIterable.from(testParams.getBuildDeps())
                    .filter(CxxPreprocessorDep.class::isInstance)),
            ImmutableList.of(),
            Optional.empty());

    Set<SourcePath> roots = new HashSet<>();
    for (CxxHeaders headers : CxxPreprocessorInput.concat(combinedInput).getIncludes()) {
      roots.add(headers.getRoot());
    }
    assertThat(
        "Test of library should include both public and private headers",
        roots,
        Matchers.hasItems(
            new DefaultBuildTargetSourcePath(BuildTargetFactory.newInstance("//:symlink")),
            new DefaultBuildTargetSourcePath(BuildTargetFactory.newInstance("//:privatesymlink"))));
  }

  @Test
  public void libraryTestIncludesPublicHeadersOfDependenciesOfLibraryUnderTest() throws Exception {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));

    BuildTarget libTarget = BuildTargetFactory.newInstance("//:lib");
    BuildTarget otherlibTarget = BuildTargetFactory.newInstance("//:otherlib");
    BuildTarget testTarget = BuildTargetFactory.newInstance("//:test");

    BuildRuleParams otherlibParams = new FakeBuildRuleParamsBuilder(otherlibTarget).build();
    FakeCxxLibrary otherlibRule =
        new FakeCxxLibrary(
            otherlibParams,
            BuildTargetFactory.newInstance("//:otherheader"),
            BuildTargetFactory.newInstance("//:othersymlink"),
            BuildTargetFactory.newInstance("//:otherprivateheader"),
            BuildTargetFactory.newInstance("//:otherprivatesymlink"),
            new FakeBuildRule("//:archive", pathResolver),
            new FakeBuildRule("//:shared", pathResolver),
            Paths.get("output/path/lib.so"),
            "lib.so",
            // This library has no tests.
            ImmutableSortedSet.of());

    BuildRuleParams libParams =
        new FakeBuildRuleParamsBuilder(libTarget)
            .setDeclaredDeps(ImmutableSortedSet.of(otherlibRule))
            .build();
    FakeCxxLibrary libRule =
        new FakeCxxLibrary(
            libParams,
            BuildTargetFactory.newInstance("//:header"),
            BuildTargetFactory.newInstance("//:symlink"),
            BuildTargetFactory.newInstance("//:privateheader"),
            BuildTargetFactory.newInstance("//:privatesymlink"),
            new FakeBuildRule("//:archive", pathResolver),
            new FakeBuildRule("//:shared", pathResolver),
            Paths.get("output/path/lib.so"),
            "lib.so",
            // Ensure the test is listed as a dep of the lib.
            ImmutableSortedSet.of(testTarget));

    BuildRuleParams testParams =
        new FakeBuildRuleParamsBuilder(testTarget)
            .setDeclaredDeps(ImmutableSortedSet.of(libRule))
            .build();

    ImmutableList<CxxPreprocessorInput> combinedInput =
        CxxDescriptionEnhancer.collectCxxPreprocessorInput(
            testParams,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            testParams.getBuildDeps(),
            ImmutableMultimap.of(),
            ImmutableList.of(),
            ImmutableSet.of(),
            CxxPreprocessables.getTransitiveCxxPreprocessorInput(
                CxxPlatformUtils.DEFAULT_PLATFORM,
                FluentIterable.from(testParams.getBuildDeps())
                    .filter(CxxPreprocessorDep.class::isInstance)),
            ImmutableList.of(),
            Optional.empty());

    Set<SourcePath> roots = new HashSet<>();
    for (CxxHeaders headers : CxxPreprocessorInput.concat(combinedInput).getIncludes()) {
      roots.add(headers.getRoot());
    }
    assertThat(
        "Test of library should include public dependency headers",
        Iterables.transform(
            CxxPreprocessorInput.concat(combinedInput).getIncludes(), CxxHeaders::getRoot),
        allOf(
            hasItem(
                new DefaultBuildTargetSourcePath(
                    BuildTargetFactory.newInstance("//:othersymlink"))),
            not(
                hasItem(
                    new DefaultBuildTargetSourcePath(
                        BuildTargetFactory.newInstance("//:otherprivatesymlink"))))));
  }

  @Test
  public void nonTestLibraryDepDoesNotIncludePrivateHeadersOfLibrary() throws Exception {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));

    BuildTarget libTarget = BuildTargetFactory.newInstance("//:lib");

    BuildRuleParams libParams = new FakeBuildRuleParamsBuilder(libTarget).build();
    FakeCxxLibrary libRule =
        new FakeCxxLibrary(
            libParams,
            BuildTargetFactory.newInstance("//:header"),
            BuildTargetFactory.newInstance("//:symlink"),
            BuildTargetFactory.newInstance("//:privateheader"),
            BuildTargetFactory.newInstance("//:privatesymlink"),
            new FakeBuildRule("//:archive", pathResolver),
            new FakeBuildRule("//:shared", pathResolver),
            Paths.get("output/path/lib.so"),
            "lib.so",
            // This library has no tests.
            ImmutableSortedSet.of());

    BuildTarget otherLibDepTarget = BuildTargetFactory.newInstance("//:other");
    BuildRuleParams otherLibDepParams =
        new FakeBuildRuleParamsBuilder(otherLibDepTarget)
            .setDeclaredDeps(ImmutableSortedSet.of(libRule))
            .build();

    ImmutableList<CxxPreprocessorInput> otherInput =
        CxxDescriptionEnhancer.collectCxxPreprocessorInput(
            otherLibDepParams,
            CxxPlatformUtils.DEFAULT_PLATFORM,
            otherLibDepParams.getBuildDeps(),
            ImmutableMultimap.of(),
            ImmutableList.of(),
            ImmutableSet.of(),
            CxxPreprocessables.getTransitiveCxxPreprocessorInput(
                CxxPlatformUtils.DEFAULT_PLATFORM,
                FluentIterable.from(otherLibDepParams.getBuildDeps())
                    .filter(CxxPreprocessorDep.class::isInstance)),
            ImmutableList.of(),
            Optional.empty());

    Set<SourcePath> roots = new HashSet<>();
    for (CxxHeaders headers : CxxPreprocessorInput.concat(otherInput).getIncludes()) {
      roots.add(headers.getRoot());
    }
    assertThat(
        "Non-test rule with library dep should include public and not private headers",
        roots,
        allOf(
            hasItem(new DefaultBuildTargetSourcePath(BuildTargetFactory.newInstance("//:symlink"))),
            not(
                hasItem(
                    new DefaultBuildTargetSourcePath(
                        BuildTargetFactory.newInstance("//:privatesymlink"))))));
  }

  @Test
  public void testSonameExpansion() {
    assertThat(soname("libfoo.so", "dylib", "%s.dylib"), equalTo("libfoo.so"));
    assertThat(soname("libfoo.$(ext)", "good", "%s.bad"), equalTo("libfoo.good"));
    assertThat(soname("libfoo.$(ext 2.3)", "bad", "%s.good"), equalTo("libfoo.2.3.good"));
    assertThat(soname("libfoo.$(ext 2.3)", "bad", "good.%s"), equalTo("libfoo.good.2.3"));
    assertThat(soname("libfoo.$(ext 2.3)", "bad", "windows"), equalTo("libfoo.windows"));
  }

  /** Just a helper to make this shorter to write. */
  private static String soname(String declared, String extension, String versionedFormat) {
    return CxxDescriptionEnhancer.getNonDefaultSharedLibrarySoname(
        declared, extension, versionedFormat);
  }
}
