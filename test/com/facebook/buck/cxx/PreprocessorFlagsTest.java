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

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
@RunWith(Enclosed.class)
public class PreprocessorFlagsTest {

  @RunWith(Parameterized.class)
  public static class FieldsAffectingRuleKeys {
    private static final PreprocessorFlags defaultFlags = PreprocessorFlags.builder().build();

    @Parameterized.Parameters(name = "field: {0} shouldAffectRuleKey: {2}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {
              "otherFlags (platform)",
              defaultFlags.withOtherFlags(
                  CxxToolFlags.explicitBuilder().addPlatformFlags("-DFOO").build()),
              true,
            },
            {
              "otherFlags (rule)",
              defaultFlags.withOtherFlags(
                  CxxToolFlags.explicitBuilder().addRuleFlags("-DFOO").build()),
              true,
            },
            {
              "frameworkPaths",
              defaultFlags.withFrameworkPaths(
                  FrameworkPath.ofSourcePath(new FakeSourcePath("different"))),
              true,
            },
            {
              "prefixHeader", defaultFlags.withPrefixHeader(new FakeSourcePath("different")), true,
            }
          });
    }

    @Parameterized.Parameter(0)
    public String caseName;

    @Parameterized.Parameter(1)
    public PreprocessorFlags alteredFlags;

    @Parameterized.Parameter(2)
    public boolean shouldDiffer;

    @Test
    public void shouldAffectRuleKey() {
      SourcePathRuleFinder ruleFinder =
          new SourcePathRuleFinder(
              new BuildRuleResolver(
                  TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
      SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
      BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
      FakeFileHashCache hashCache =
          FakeFileHashCache.createFromStrings(
              ImmutableMap.of("different", Strings.repeat("d", 40)));
      BuildRule fakeBuildRule = new FakeBuildRule(target, pathResolver);

      DefaultRuleKeyFactory.Builder<HashCode> builder;
      builder =
          new DefaultRuleKeyFactory(0, hashCache, pathResolver, ruleFinder)
              .newBuilderForTesting(fakeBuildRule);
      defaultFlags.appendToRuleKey(builder, CxxPlatformUtils.DEFAULT_COMPILER_DEBUG_PATH_SANITIZER);
      RuleKey defaultRuleKey = builder.build(RuleKey::new);

      builder =
          new DefaultRuleKeyFactory(0, hashCache, pathResolver, ruleFinder)
              .newBuilderForTesting(fakeBuildRule);
      alteredFlags.appendToRuleKey(builder, CxxPlatformUtils.DEFAULT_COMPILER_DEBUG_PATH_SANITIZER);
      RuleKey alteredRuleKey = builder.build(RuleKey::new);

      if (shouldDiffer) {
        Assert.assertNotEquals(defaultRuleKey, alteredRuleKey);
      } else {
        Assert.assertEquals(defaultRuleKey, alteredRuleKey);
      }
    }
  }

  public static class OtherTests {
    @Test
    public void flagsAreSanitized() {
      SourcePathRuleFinder ruleFinder =
          new SourcePathRuleFinder(
              new BuildRuleResolver(
                  TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
      final SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
      BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
      final FakeFileHashCache hashCache = FakeFileHashCache.createFromStrings(ImmutableMap.of());
      final BuildRule fakeBuildRule = new FakeBuildRule(target, pathResolver);

      class TestData {
        public RuleKey generate(String prefix) {
          DebugPathSanitizer sanitizer =
              new MungingDebugPathSanitizer(
                  10,
                  File.separatorChar,
                  Paths.get("PWD"),
                  ImmutableBiMap.of(Paths.get(prefix), Paths.get("A")));

          CxxToolFlags flags =
              CxxToolFlags.explicitBuilder()
                  .addPlatformFlags("-I" + prefix + "/foo")
                  .addRuleFlags("-I" + prefix + "/bar")
                  .build();

          DefaultRuleKeyFactory.Builder<HashCode> builder =
              new DefaultRuleKeyFactory(0, hashCache, pathResolver, ruleFinder)
                  .newBuilderForTesting(fakeBuildRule);
          PreprocessorFlags.builder()
              .setOtherFlags(flags)
              .build()
              .appendToRuleKey(builder, sanitizer);
          return builder.build(RuleKey::new);
        }
      }

      TestData testData = new TestData();
      Assert.assertEquals(testData.generate("something"), testData.generate("different"));
    }
  }
}
