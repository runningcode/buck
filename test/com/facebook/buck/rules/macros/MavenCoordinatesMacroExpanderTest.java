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

package com.facebook.buck.rules.macros;

import static com.facebook.buck.rules.TestCellBuilder.createCellRoots;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.HasMavenCoordinates;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.MacroException;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

public class MavenCoordinatesMacroExpanderTest {

  private BuildRuleResolver resolver;
  private MavenCoordinatesMacroExpander expander;

  @Before
  public void setUp() {
    resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    expander = new MavenCoordinatesMacroExpander();
  }

  @Test
  public void testHasMavenCoordinatesBuildRule() throws Exception {

    String mavenCoords = "org.foo:bar:1.0";

    BuildRule rule =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//test:java"))
            .setMavenCoords(mavenCoords)
            .build(resolver);
    try {
      String actualCoords = expander.getMavenCoordinates(rule);
      assertEquals(
          "Return maven coordinates do not match provides ones", mavenCoords, actualCoords);
    } catch (MacroException e) {
      fail(String.format("Unexpected MacroException: %s", e.getMessage()));
    }
  }

  @Test
  public void testNonHasMavenCoordinatesBuildRule() throws Exception {
    assumeFalse(
        "Assuming that FakeBuildRule does not have maven coordinates",
        FakeBuildRule.class.isAssignableFrom(HasMavenCoordinates.class));

    SourcePathResolver sourcePathResolver =
        new SourcePathResolver(new SourcePathRuleFinder(resolver));
    BuildRule rule = new FakeBuildRule("//test:foo", sourcePathResolver);

    try {
      expander.getMavenCoordinates(rule);
      fail("Expected MacroException; Rule does not contain maven coordinates");
    } catch (MacroException e) {
      assertTrue(
          "Expected MacroException that indicates target does not have maven coordinates",
          e.getMessage().contains("does not correspond to a rule with maven coordinates"));
    }
  }

  @Test
  public void testHasMavenCoordinatesBuildRuleMissingCoordinates() throws Exception {
    BuildRule rule =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//test:no-mvn"))
            .build(resolver);
    try {
      expander.getMavenCoordinates(rule);
      fail("Expected MacroException; Rule does not contain maven coordinates");
    } catch (MacroException e) {
      assertTrue(
          "Expected MacroException that indicates target does not have maven coordinates",
          e.getMessage().contains("does not have maven coordinates"));
    }
  }

  @Test
  public void testExpansionOfMavenCoordinates() throws NoSuchBuildTargetException {
    String mavenCoords = "org.foo:bar:1.0";
    BuildTarget target = BuildTargetFactory.newInstance("//:java");

    JavaLibraryBuilder.createBuilder(target).setMavenCoords(mavenCoords).build(resolver);

    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    MacroHandler macroHandler = new MacroHandler(ImmutableMap.of("maven_coords", expander));
    try {
      String expansion =
          macroHandler.expand(
              target, createCellRoots(filesystem), resolver, "$(maven_coords //:java)");
      assertEquals("Return maven coordinates do not match provides ones", mavenCoords, expansion);
    } catch (MacroException e) {
      fail(String.format("Unexpected MacroException: %s", e.getMessage()));
    }
  }

  @Test
  public void testMissingBuildRule() throws NoSuchBuildTargetException {
    BuildTarget target = BuildTargetFactory.newInstance("//:java");

    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    MacroHandler macroHandler = new MacroHandler(ImmutableMap.of("maven_coords", expander));
    try {
      macroHandler.expand(target, createCellRoots(filesystem), resolver, "$(maven_coords //:foo)");
      fail("Expected MacroException; Rule does not exist");
    } catch (MacroException e) {
      assertTrue(
          "Expected MacroException that indicates target does not exist",
          e.getMessage().contains("no rule //:foo"));
    }
  }
}
