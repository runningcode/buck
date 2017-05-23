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

package com.facebook.buck.apple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

public class AppleDescriptionsTest {

  @Before
  public void setUp() {
    assumeTrue(Platform.detect() == Platform.MACOS || Platform.detect() == Platform.LINUX);
  }

  @Test
  public void parseAppleHeadersForUseFromOtherTargetsFromSet() {
    SourcePathResolver resolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    assertEquals(
        ImmutableMap.<String, SourcePath>of(
            "prefix/some_file.h", new FakeSourcePath("path/to/some_file.h"),
            "prefix/another_file.h", new FakeSourcePath("path/to/another_file.h"),
            "prefix/a_file.h", new FakeSourcePath("different/path/to/a_file.h"),
            "prefix/file.h", new FakeSourcePath("file.h")),
        AppleDescriptions.parseAppleHeadersForUseFromOtherTargets(
            resolver::getRelativePath,
            Paths.get("prefix"),
            SourceList.ofUnnamedSources(
                ImmutableSortedSet.of(
                    new FakeSourcePath("path/to/some_file.h"),
                    new FakeSourcePath("path/to/another_file.h"),
                    new FakeSourcePath("different/path/to/a_file.h"),
                    new FakeSourcePath("file.h")))));
  }

  @Test
  public void parseAppleHeadersForUseFromTheSameFromSet() {
    SourcePathResolver resolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    assertEquals(
        ImmutableMap.<String, SourcePath>of(
            "some_file.h", new FakeSourcePath("path/to/some_file.h"),
            "another_file.h", new FakeSourcePath("path/to/another_file.h"),
            "a_file.h", new FakeSourcePath("different/path/to/a_file.h"),
            "file.h", new FakeSourcePath("file.h")),
        AppleDescriptions.parseAppleHeadersForUseFromTheSameTarget(
            resolver::getRelativePath,
            SourceList.ofUnnamedSources(
                ImmutableSortedSet.of(
                    new FakeSourcePath("path/to/some_file.h"),
                    new FakeSourcePath("path/to/another_file.h"),
                    new FakeSourcePath("different/path/to/a_file.h"),
                    new FakeSourcePath("file.h")))));
  }

  @Test
  public void parseAppleHeadersForUseFromOtherTargetsFromMap() {
    ImmutableSortedMap<String, SourcePath> headerMap =
        ImmutableSortedMap.of(
            "virtual/path.h", new FakeSourcePath("path/to/some_file.h"),
            "another/path.h", new FakeSourcePath("path/to/another_file.h"),
            "another/file.h", new FakeSourcePath("different/path/to/a_file.h"),
            "file.h", new FakeSourcePath("file.h"));
    SourcePathResolver resolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    assertEquals(
        headerMap,
        AppleDescriptions.parseAppleHeadersForUseFromOtherTargets(
            resolver::getRelativePath, Paths.get("prefix"), SourceList.ofNamedSources(headerMap)));
  }

  @Test
  public void parseAppleHeadersForUseFromTheSameTargetFromMap() {
    ImmutableSortedMap<String, SourcePath> headerMap =
        ImmutableSortedMap.of(
            "virtual/path.h", new FakeSourcePath("path/to/some_file.h"),
            "another/path.h", new FakeSourcePath("path/to/another_file.h"),
            "another/file.h", new FakeSourcePath("different/path/to/a_file.h"),
            "file.h", new FakeSourcePath("file.h"));
    SourcePathResolver resolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    assertEquals(
        ImmutableMap.of(),
        AppleDescriptions.parseAppleHeadersForUseFromTheSameTarget(
            resolver::getRelativePath, SourceList.ofNamedSources(headerMap)));
  }

  @Test
  public void convertToFlatCxxHeadersWithPrefix() {
    SourcePathResolver resolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    assertEquals(
        ImmutableMap.<String, SourcePath>of(
            "prefix/some_file.h", new FakeSourcePath("path/to/some_file.h"),
            "prefix/another_file.h", new FakeSourcePath("path/to/another_file.h"),
            "prefix/a_file.h", new FakeSourcePath("different/path/to/a_file.h"),
            "prefix/file.h", new FakeSourcePath("file.h")),
        AppleDescriptions.convertToFlatCxxHeaders(
            Paths.get("prefix"),
            resolver::getRelativePath,
            ImmutableSet.of(
                new FakeSourcePath("path/to/some_file.h"),
                new FakeSourcePath("path/to/another_file.h"),
                new FakeSourcePath("different/path/to/a_file.h"),
                new FakeSourcePath("file.h"))));
  }

  @Test
  public void convertToFlatCxxHeadersWithoutPrefix() {
    SourcePathResolver resolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    assertEquals(
        ImmutableMap.<String, SourcePath>of(
            "some_file.h", new FakeSourcePath("path/to/some_file.h"),
            "another_file.h", new FakeSourcePath("path/to/another_file.h"),
            "a_file.h", new FakeSourcePath("different/path/to/a_file.h"),
            "file.h", new FakeSourcePath("file.h")),
        AppleDescriptions.convertToFlatCxxHeaders(
            Paths.get(""),
            resolver::getRelativePath,
            ImmutableSet.of(
                new FakeSourcePath("path/to/some_file.h"),
                new FakeSourcePath("path/to/another_file.h"),
                new FakeSourcePath("different/path/to/a_file.h"),
                new FakeSourcePath("file.h"))));
  }
}
