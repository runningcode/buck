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

package com.facebook.buck.android;

import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVAC;
import static com.facebook.buck.jvm.java.JavaCompilationConstants.DEFAULT_JAVAC_OPTIONS;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.BuildConfigFields;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

public class AndroidBuildConfigJavaLibraryTest {

  @Test
  public void testAddToCollector() throws NoSuchBuildTargetException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo:bar");
    BuildRuleParams params = new FakeBuildRuleParamsBuilder(buildTarget).build();
    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    AndroidBuildConfigJavaLibrary buildConfigJavaLibrary =
        AndroidBuildConfigDescription.createBuildRule(
            params,
            "com.example.buck",
            /* values */ BuildConfigFields.fromFieldDeclarations(
                Collections.singleton("String foo = \"bar\"")),
            /* valuesFile */ Optional.empty(),
            /* useConstantExpressions */ false,
            DEFAULT_JAVAC,
            DEFAULT_JAVAC_OPTIONS,
            buildRuleResolver);

    AndroidPackageableCollector collector = new AndroidPackageableCollector(buildTarget);
    buildConfigJavaLibrary.addToCollector(collector);
    AndroidPackageableCollection collection = collector.build();
    assertEquals(
        ImmutableMap.of(
            "com.example.buck",
            BuildConfigFields.fromFields(
                ImmutableList.of(BuildConfigFields.Field.of("String", "foo", "\"bar\"")))),
        collection.getBuildConfigs());
  }

  @Test
  public void testBuildConfigHasCorrectProperties() throws NoSuchBuildTargetException {
    BuildRuleParams params = new FakeBuildRuleParamsBuilder("//foo:bar").build();
    BuildConfigFields fields =
        BuildConfigFields.fromFieldDeclarations(Collections.singleton("String KEY = \"value\""));
    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    AndroidBuildConfigJavaLibrary buildConfigJavaLibrary =
        AndroidBuildConfigDescription.createBuildRule(
            params,
            "com.example.buck",
            /* values */ fields,
            /* valuesFile */ Optional.empty(),
            /* useConstantExpressions */ false,
            DEFAULT_JAVAC,
            DEFAULT_JAVAC_OPTIONS,
            buildRuleResolver);
    AndroidBuildConfig buildConfig = buildConfigJavaLibrary.getAndroidBuildConfig();
    assertEquals("com.example.buck", buildConfig.getJavaPackage());
    assertEquals(fields, buildConfig.getBuildConfigFields());
  }
}
