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

package com.facebook.buck.apple;

import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
abstract class AbstractGroupedSource {
  /** The type of grouped source entry this object represents. */
  public enum Type {
    /** A single {@link SourceWithFlags}. */
    SOURCE_WITH_FLAGS,
    /** A single {@link SourcePath} that shouldn't be included in the build phase. */
    IGNORED_SOURCE,
    /** A single {@link SourcePath} representing a public header file. */
    PUBLIC_HEADER,
    /** A single {@link SourcePath} representing a private header file. */
    PRIVATE_HEADER,
    /** A source group (group name and one or more GroupedSource objects). */
    SOURCE_GROUP,
  }

  @Value.Parameter
  protected abstract Type getType();

  @Value.Parameter
  protected abstract Optional<SourceWithFlags> getSourceWithFlags();

  @Value.Parameter
  protected abstract Optional<SourcePath> getSourcePath();

  @Value.Parameter
  protected abstract Optional<String> getSourceGroupName();

  @Value.Parameter
  protected abstract Optional<Path> getSourceGroupPathRelativeToTarget();

  @Value.Parameter
  protected abstract Optional<List<GroupedSource>> getSourceGroup();

  @Value.Check
  protected void check() {
    switch (getType()) {
      case SOURCE_WITH_FLAGS:
        Preconditions.checkArgument(getSourceWithFlags().isPresent());
        Preconditions.checkArgument(!getSourcePath().isPresent());
        Preconditions.checkArgument(!getSourceGroupName().isPresent());
        Preconditions.checkArgument(!getSourceGroupPathRelativeToTarget().isPresent());
        Preconditions.checkArgument(!getSourceGroup().isPresent());
        break;
      case IGNORED_SOURCE:
      case PUBLIC_HEADER:
      case PRIVATE_HEADER:
        Preconditions.checkArgument(!getSourceWithFlags().isPresent());
        Preconditions.checkArgument(getSourcePath().isPresent());
        Preconditions.checkArgument(!getSourceGroupName().isPresent());
        Preconditions.checkArgument(!getSourceGroupPathRelativeToTarget().isPresent());
        Preconditions.checkArgument(!getSourceGroup().isPresent());
        break;
      case SOURCE_GROUP:
        Preconditions.checkArgument(!getSourceWithFlags().isPresent());
        Preconditions.checkArgument(!getSourcePath().isPresent());
        Preconditions.checkArgument(getSourceGroupName().isPresent());
        Preconditions.checkArgument(getSourceGroupPathRelativeToTarget().isPresent());
        Preconditions.checkArgument(getSourceGroup().isPresent());
        break;
      default:
        throw new RuntimeException("Unhandled type: " + getType());
    }
  }

  public String getName(Function<SourcePath, Path> pathResolver) {
    SourcePath sourcePath;
    switch (getType()) {
      case SOURCE_WITH_FLAGS:
        sourcePath = getSourceWithFlags().get().getSourcePath();
        return Preconditions.checkNotNull(pathResolver.apply(sourcePath)).getFileName().toString();
      case IGNORED_SOURCE:
        sourcePath = getSourcePath().get();
        return Preconditions.checkNotNull(pathResolver.apply(sourcePath)).getFileName().toString();
      case PUBLIC_HEADER:
      case PRIVATE_HEADER:
        sourcePath = getSourcePath().get();
        return Preconditions.checkNotNull(pathResolver.apply(sourcePath)).getFileName().toString();
      case SOURCE_GROUP:
        return getSourceGroupName().get();
      default:
        throw new RuntimeException("Unhandled type: " + getType());
    }
  }

  /** Creates a {@link GroupedSource} given a {@link SourceWithFlags}. */
  public static GroupedSource ofSourceWithFlags(SourceWithFlags sourceWithFlags) {
    return GroupedSource.of(
        Type.SOURCE_WITH_FLAGS,
        Optional.of(sourceWithFlags),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Creates a {@link GroupedSource} given a {@link SourcePath} representing a file that should not
   * be included in sources.
   */
  public static GroupedSource ofIgnoredSource(SourcePath sourcePath) {
    return GroupedSource.of(
        Type.IGNORED_SOURCE,
        Optional.empty(),
        Optional.of(sourcePath),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Creates a {@link GroupedSource} given a {@link SourcePath} representing a public header file.
   */
  public static GroupedSource ofPublicHeader(SourcePath headerPath) {
    return GroupedSource.of(
        Type.PUBLIC_HEADER,
        Optional.empty(),
        Optional.of(headerPath),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Creates a {@link GroupedSource} given a {@link SourcePath} representing a private header file.
   */
  public static GroupedSource ofPrivateHeader(SourcePath headerPath) {
    return GroupedSource.of(
        Type.PRIVATE_HEADER,
        Optional.empty(),
        Optional.of(headerPath),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /** Creates a {@link GroupedSource} given a source group name and a list of GroupedSources. */
  public static GroupedSource ofSourceGroup(
      String sourceGroupName,
      Path sourceGroupPathRelativeToTarget,
      Collection<GroupedSource> sourceGroup) {
    return GroupedSource.of(
        Type.SOURCE_GROUP,
        Optional.empty(),
        Optional.empty(),
        Optional.of(sourceGroupName),
        Optional.of(sourceGroupPathRelativeToTarget),
        Optional.of((List<GroupedSource>) ImmutableList.copyOf(sourceGroup)));
  }

  public interface Visitor {
    void visitSourceWithFlags(SourceWithFlags sourceWithFlags);

    void visitIgnoredSource(SourcePath source);

    void visitPublicHeader(SourcePath publicHeader);

    void visitPrivateHeader(SourcePath privateHeader);

    void visitSourceGroup(
        String sourceGroupName,
        Path sourceGroupPathRelativeToTarget,
        List<GroupedSource> sourceGroup);
  }

  public void visit(Visitor visitor) {
    switch (getType()) {
      case SOURCE_WITH_FLAGS:
        visitor.visitSourceWithFlags(getSourceWithFlags().get());
        break;
      case IGNORED_SOURCE:
        visitor.visitIgnoredSource(getSourcePath().get());
        break;
      case PUBLIC_HEADER:
        visitor.visitPublicHeader(getSourcePath().get());
        break;
      case PRIVATE_HEADER:
        visitor.visitPrivateHeader(getSourcePath().get());
        break;
      case SOURCE_GROUP:
        visitor.visitSourceGroup(
            getSourceGroupName().get(),
            getSourceGroupPathRelativeToTarget().get(),
            getSourceGroup().get());
    }
  }
}
