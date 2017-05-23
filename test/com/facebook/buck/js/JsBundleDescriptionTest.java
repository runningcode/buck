/*
 * Copyright 2017-present Facebook, Inc.
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

package com.facebook.buck.js;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.util.RichStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class JsBundleDescriptionTest {

  private static final BuildTarget directDependencyTarget =
      BuildTargetFactory.newInstance("//libs:direct");
  private static final BuildTarget level2 = BuildTargetFactory.newInstance("//libs:level2");
  private static final BuildTarget level1_1 = BuildTargetFactory.newInstance("//libs:level1.1");
  private static final BuildTarget level1_2 = BuildTargetFactory.newInstance("//libs:level1.2");
  private final BuildTarget bundleTarget = BuildTargetFactory.newInstance("//bundle:target");

  private JsTestScenario.Builder scenarioBuilder;
  private JsTestScenario scenario;

  static Collection<BuildTarget> allLibaryTargets(Flavor... flavors) {
    return Stream.of(level2, level1_1, level1_2, directDependencyTarget)
        .map(t -> t.withAppendedFlavors(flavors))
        .collect(Collectors.toList());
  }

  @Before
  public void setUp() throws NoSuchBuildTargetException {
    scenarioBuilder = JsTestScenario.builder();
    scenarioBuilder
        .library(level2)
        .library(level1_1, level2)
        .library(level1_2, level2)
        .library(directDependencyTarget, level1_1, level1_2)
        .bundleWithDeps(bundleTarget, directDependencyTarget);
    scenario = scenarioBuilder.build();
  }

  @Test
  public void testTransitiveLibraryDependencies() throws NoSuchBuildTargetException {
    BuildRule jsBundle = scenario.resolver.requireRule(bundleTarget);
    assertThat(allLibaryTargets(), everyItem(in(dependencyTargets(jsBundle))));
  }

  @Test
  public void testTransitiveLibraryDependenciesWithFlavors() throws NoSuchBuildTargetException {
    final Flavor[] flavors = {JsFlavors.IOS, JsFlavors.RELEASE};
    BuildRule jsBundle = scenario.resolver.requireRule(bundleTarget.withFlavors(flavors));
    assertThat(allLibaryTargets(flavors), everyItem(in(dependencyTargets(jsBundle))));
  }

  @Test
  public void testFlavoredBundleDoesNotDependOnUnflavoredLibs() throws NoSuchBuildTargetException {
    BuildRule jsBundle =
        scenario.resolver.requireRule(bundleTarget.withFlavors(JsFlavors.IOS, JsFlavors.RELEASE));
    assertThat(allLibaryTargets(), everyItem(not(in(dependencyTargets(jsBundle)))));
  }

  @Test
  public void testFlavoredBundleWithoutReleaseFlavorDependOnFlavoredLibs()
      throws NoSuchBuildTargetException {
    Flavor[] flavors = {JsFlavors.IOS, JsFlavors.RAM_BUNDLE_INDEXED};
    BuildRule jsBundle = scenario.resolver.requireRule((bundleTarget.withFlavors(flavors)));
    assertThat(allLibaryTargets(JsFlavors.IOS), everyItem(in(dependencyTargets(jsBundle))));
    assertThat(allLibaryTargets(flavors), everyItem(not(in(dependencyTargets(jsBundle)))));
  }

  @Test
  public void testFlavoredReleaseBundleDoesNotPropagateRamBundleFlavors()
      throws NoSuchBuildTargetException {
    Flavor[] bundleFlavors = {JsFlavors.IOS, JsFlavors.RAM_BUNDLE_INDEXED, JsFlavors.RELEASE};
    Flavor[] flavorsToBePropagated = {JsFlavors.IOS, JsFlavors.RELEASE};
    BuildRule jsBundle = scenario.resolver.requireRule((bundleTarget.withFlavors(bundleFlavors)));
    assertThat(allLibaryTargets(flavorsToBePropagated), everyItem(in(dependencyTargets(jsBundle))));
    assertThat(allLibaryTargets(bundleFlavors), everyItem(not(in(dependencyTargets(jsBundle)))));
  }

  @Test
  public void testFlavoredReleaseBundleDoesNotPropagateRamBundleFlavorsAndroid()
      throws NoSuchBuildTargetException {
    Flavor[] bundleFlavors = {JsFlavors.ANDROID, JsFlavors.RAM_BUNDLE_INDEXED, JsFlavors.RELEASE};
    Flavor[] flavorsToBePropagated = {JsFlavors.ANDROID, JsFlavors.RELEASE};
    BuildRule jsBundle = scenario.resolver.requireRule((bundleTarget.withFlavors(bundleFlavors)));
    assertThat(allLibaryTargets(flavorsToBePropagated), everyItem(in(dependencyTargets(jsBundle))));
    assertThat(allLibaryTargets(bundleFlavors), everyItem(not(in(dependencyTargets(jsBundle)))));
  }

  @Test
  public void testTransitiveLibraryDependenciesWithFlavorsForAndroid()
      throws NoSuchBuildTargetException {
    final Flavor[] flavors = {JsFlavors.ANDROID, JsFlavors.RELEASE};
    BuildRule jsBundle = scenario.resolver.requireRule(bundleTarget.withFlavors(flavors));
    assertThat(allLibaryTargets(flavors), everyItem(in(dependencyTargets(jsBundle))));
  }

  @Test
  public void testSourceMapExport() throws NoSuchBuildTargetException {
    BuildRule map =
        scenario.resolver.requireRule(
            bundleTarget.withFlavors(JsFlavors.IOS, JsFlavors.SOURCE_MAP));
    JsBundleOutputs bundle =
        scenario.resolver.getRuleWithType(
            bundleTarget.withFlavors(JsFlavors.IOS), JsBundleOutputs.class);

    assertEquals(map.getSourcePathToOutput(), bundle.getSourcePathToSourceMap());
  }

  @Test
  public void testJsLibraryInDeps() throws NoSuchBuildTargetException {
    final BuildTarget bundleTarget = BuildTargetFactory.newInstance("//the:bundle");
    final JsTestScenario testScenario =
        JsTestScenario.builder(scenario)
            .bundleWithDeps(bundleTarget, directDependencyTarget)
            .build();

    BuildRule jsBundle = testScenario.resolver.requireRule(bundleTarget);
    assertThat(allLibaryTargets(), everyItem(in(dependencyTargets(jsBundle))));
  }

  @Test
  public void testTransitiveDependenciesAcrossSubGraph() throws NoSuchBuildTargetException {
    final BuildTarget firstLevelA = BuildTargetFactory.newInstance("//:firstA");
    final BuildTarget firstLevelB = BuildTargetFactory.newInstance("//:firstB");
    final BuildTarget secondLevelA = BuildTargetFactory.newInstance("//:secondA");
    final BuildTarget secondLevelB = BuildTargetFactory.newInstance("//:secondB");
    final BuildTarget bundleTarget = BuildTargetFactory.newInstance("//the:bundle");

    final JsTestScenario.Builder builder = JsTestScenario.builder(scenario);
    final JsTestScenario testScenario =
        builder
            .appleLibraryWithDeps(firstLevelA, level1_1)
            .library(secondLevelA)
            .appleLibraryWithDeps(secondLevelB, level1_2, secondLevelA)
            .appleLibraryWithDeps(firstLevelB, secondLevelB)
            .bundleWithDeps(bundleTarget, firstLevelA, firstLevelB)
            .build();

    final Flavor[] flavors = {JsFlavors.IOS, JsFlavors.RELEASE};
    final BuildRule jsBundle = testScenario.resolver.requireRule(bundleTarget.withFlavors(flavors));
    final List<BuildTarget> expectedLibDeps =
        Stream.of(level1_1, level1_2, level2, secondLevelA)
            .map(t -> t.withAppendedFlavors(flavors))
            .collect(Collectors.toList());

    assertThat(expectedLibDeps, everyItem(in(dependencyTargets(jsBundle))));
  }

  private static Collection<BuildTarget> dependencyTargets(BuildRule rule) {
    if (rule instanceof JsBundleAndroid) {
      final JsBundle jsBundle =
          RichStream.from(rule.getBuildDeps()).filter(JsBundle.class).findFirst().get();
      return dependencyTargets(jsBundle);
    } else {
      return rule.getBuildDeps()
          .stream()
          .map(BuildRule::getBuildTarget)
          .collect(Collectors.toSet());
    }
  }
}
