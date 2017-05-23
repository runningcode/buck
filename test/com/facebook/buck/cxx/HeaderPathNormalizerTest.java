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

import static org.junit.Assert.assertThat;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import java.nio.file.Path;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Test;

public class HeaderPathNormalizerTest {
  private final ProjectFilesystem filesystem = new FakeProjectFilesystem();

  @Test
  public void unmanagedHeader() {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    Path header = filesystem.getPath("foo/bar.h");
    HeaderPathNormalizer normalizer = new HeaderPathNormalizer.Builder(pathResolver).build();
    assertThat(
        normalizer.getAbsolutePathForUnnormalizedPath(filesystem.resolve(header)),
        Matchers.equalTo(Optional.empty()));
  }

  @Test
  public void managedHeader() {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    Path header = filesystem.getPath("foo/bar.h");
    SourcePath headerPath = new PathSourcePath(filesystem, header);
    HeaderPathNormalizer normalizer =
        new HeaderPathNormalizer.Builder(pathResolver).addHeader(headerPath).build();
    assertThat(
        normalizer.getAbsolutePathForUnnormalizedPath(pathResolver.getAbsolutePath(headerPath)),
        Matchers.equalTo(Optional.of(pathResolver.getAbsolutePath(headerPath))));
    assertThat(
        normalizer.getSourcePathForAbsolutePath(pathResolver.getAbsolutePath(headerPath)),
        Matchers.equalTo(headerPath));
  }

  @Test
  public void managedHeaderWithRelativePath() {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    Path header = filesystem.getPath("foo/bar.h");
    SourcePath headerPath = new PathSourcePath(filesystem, header);
    HeaderPathNormalizer normalizer =
        new HeaderPathNormalizer.Builder(pathResolver).addHeader(headerPath).build();
    assertThat(
        normalizer.getAbsolutePathForUnnormalizedPath(filesystem.resolve(header)),
        Matchers.equalTo(Optional.of(pathResolver.getAbsolutePath(headerPath))));
    assertThat(
        normalizer.getAbsolutePathForUnnormalizedPath(pathResolver.getAbsolutePath(headerPath)),
        Matchers.equalTo(Optional.of(pathResolver.getAbsolutePath(headerPath))));
    assertThat(
        normalizer.getSourcePathForAbsolutePath(pathResolver.getAbsolutePath(headerPath)),
        Matchers.equalTo(headerPath));
  }

  @Test
  public void managedHeaderDir() {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    Path header = filesystem.getPath("foo/bar.h");
    SourcePath headerDirPath = new PathSourcePath(filesystem, header.getParent());
    HeaderPathNormalizer normalizer =
        new HeaderPathNormalizer.Builder(pathResolver).addHeaderDir(headerDirPath).build();
    assertThat(
        normalizer.getAbsolutePathForUnnormalizedPath(pathResolver.getAbsolutePath(headerDirPath)),
        Matchers.equalTo(Optional.of(pathResolver.getAbsolutePath(headerDirPath))));
    assertThat(
        normalizer.getAbsolutePathForUnnormalizedPath(filesystem.resolve(header)),
        Matchers.equalTo(Optional.of(filesystem.resolve(header))));
    assertThat(
        normalizer.getSourcePathForAbsolutePath(pathResolver.getAbsolutePath(headerDirPath)),
        Matchers.equalTo(headerDirPath));
    assertThat(
        normalizer.getSourcePathForAbsolutePath(filesystem.resolve(header)),
        Matchers.equalTo(headerDirPath));
  }

  @Test
  public void managedPrefixHeaderDir() {
    SourcePathResolver pathResolver =
        new SourcePathResolver(
            new SourcePathRuleFinder(
                new BuildRuleResolver(
                    TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer())));
    Path header = filesystem.getPath("foo/bar.pch");
    SourcePath headerPath = new PathSourcePath(filesystem, header);
    HeaderPathNormalizer normalizer =
        new HeaderPathNormalizer.Builder(pathResolver).addPrefixHeader(headerPath).build();
    assertThat(
        normalizer.getAbsolutePathForUnnormalizedPath(pathResolver.getAbsolutePath(headerPath)),
        Matchers.equalTo(Optional.of(pathResolver.getAbsolutePath(headerPath))));
    assertThat(
        normalizer.getSourcePathForAbsolutePath(pathResolver.getAbsolutePath(headerPath)),
        Matchers.equalTo(headerPath));
  }
}
