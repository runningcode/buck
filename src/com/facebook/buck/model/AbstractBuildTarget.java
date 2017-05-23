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

package com.facebook.buck.model;

import com.facebook.buck.log.views.JsonViews;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.immutables.value.Value;

@JsonAutoDetect(
  fieldVisibility = JsonAutoDetect.Visibility.NONE,
  getterVisibility = JsonAutoDetect.Visibility.NONE,
  setterVisibility = JsonAutoDetect.Visibility.NONE
)
@BuckStyleImmutable
@Value.Immutable(prehash = true)
abstract class AbstractBuildTarget implements Comparable<AbstractBuildTarget> {

  private static final Ordering<Iterable<Flavor>> LEXICOGRAPHICAL_ORDERING =
      Ordering.<Flavor>natural().lexicographical();

  @Value.Parameter
  public abstract UnflavoredBuildTarget getUnflavoredBuildTarget();

  @Value.NaturalOrder
  @Value.Parameter
  public abstract SortedSet<Flavor> getFlavors();

  @Value.Check
  protected void check() {
    Preconditions.checkArgument(
        getFlavors().comparator() == Ordering.natural(),
        "Flavors must be ordered using natural ordering.");
  }

  @JsonProperty("cell")
  public Optional<String> getCell() {
    return getUnflavoredBuildTarget().getCell();
  }

  public Path getCellPath() {
    return getUnflavoredBuildTarget().getCellPath();
  }

  @JsonProperty("baseName")
  @JsonView(JsonViews.MachineReadableLog.class)
  public String getBaseName() {
    return getUnflavoredBuildTarget().getBaseName();
  }

  public Path getBasePath() {
    return getUnflavoredBuildTarget().getBasePath();
  }

  @JsonIgnore
  public boolean isInCellRoot() {
    return getUnflavoredBuildTarget().isInCellRoot();
  }

  @JsonProperty("shortName")
  @JsonView(JsonViews.MachineReadableLog.class)
  public String getShortName() {
    return getUnflavoredBuildTarget().getShortName();
  }

  /**
   * If this build target were //third_party/java/guava:guava-latest, then this would return
   * "guava-latest". Note that the flavor of the target is included here.
   */
  public String getShortNameAndFlavorPostfix() {
    return getShortName() + getFlavorPostfix();
  }

  public String getFlavorPostfix() {
    if (getFlavors().isEmpty()) {
      return "";
    }
    return "#" + getFlavorsAsString();
  }

  @JsonProperty("flavor")
  @JsonView(JsonViews.MachineReadableLog.class)
  private String getFlavorsAsString() {
    return Joiner.on(",").join(getFlavors());
  }

  /**
   * If this build target is //third_party/java/guava:guava-latest, then this would return
   * "//third_party/java/guava:guava-latest".
   */
  @Value.Auxiliary
  @Value.Derived
  public String getFullyQualifiedName() {
    return getUnflavoredBuildTarget().getFullyQualifiedName() + getFlavorPostfix();
  }

  @JsonIgnore
  public boolean isFlavored() {
    return !(getFlavors().isEmpty());
  }

  public UnflavoredBuildTarget checkUnflavored() {
    Preconditions.checkState(!isFlavored(), "%s is flavored.", this);
    return getUnflavoredBuildTarget();
  }

  public static BuildTarget of(UnflavoredBuildTarget unflavoredBuildTarget) {
    return BuildTarget.of(unflavoredBuildTarget, ImmutableSortedSet.of());
  }

  public static BuildTarget.Builder builder(BuildTarget buildTarget) {
    return BuildTarget.builder()
        .setUnflavoredBuildTarget(buildTarget.getUnflavoredBuildTarget())
        .addAllFlavors(buildTarget.getFlavors());
  }

  public static BuildTarget.Builder builder(UnflavoredBuildTarget buildTarget) {
    return BuildTarget.builder().setUnflavoredBuildTarget(buildTarget);
  }

  public static BuildTarget.Builder builder(Path cellPath, String baseName, String shortName) {
    return BuildTarget.builder()
        .setUnflavoredBuildTarget(
            UnflavoredBuildTarget.of(cellPath, Optional.empty(), baseName, shortName));
  }

  /** @return {@link #getFullyQualifiedName()} */
  @Override
  public String toString() {
    return getFullyQualifiedName();
  }

  @Override
  public int compareTo(AbstractBuildTarget o) {
    if (this == o) {
      return 0;
    }

    return ComparisonChain.start()
        .compare(getUnflavoredBuildTarget(), o.getUnflavoredBuildTarget())
        .compare(getFlavors(), o.getFlavors(), LEXICOGRAPHICAL_ORDERING)
        .result();
  }

  public BuildTarget withoutFlavors(Set<Flavor> flavors) {
    BuildTarget.Builder builder = BuildTarget.builder();
    builder.setUnflavoredBuildTarget(getUnflavoredBuildTarget());
    for (Flavor flavor : getFlavors()) {
      if (!flavors.contains(flavor)) {
        builder.addFlavors(flavor);
      }
    }
    return builder.build();
  }

  public BuildTarget withoutFlavors(Flavor... flavors) {
    return withoutFlavors(ImmutableSet.copyOf(flavors));
  }

  public BuildTarget withAppendedFlavors(Set<Flavor> flavorsToAppend) {
    BuildTarget.Builder builder = BuildTarget.builder(BuildTarget.copyOf(this));
    builder.addAllFlavors(flavorsToAppend);
    return builder.build();
  }

  public BuildTarget withAppendedFlavors(Flavor... flavors) {
    BuildTarget.Builder builder = BuildTarget.builder(BuildTarget.copyOf(this));
    builder.addFlavors(flavors);
    return builder.build();
  }

  public BuildTarget withoutCell() {
    return BuildTarget.builder(
            getUnflavoredBuildTarget().getCellPath(), getBaseName(), getShortName())
        .addAllFlavors(getFlavors())
        .build();
  }
}
