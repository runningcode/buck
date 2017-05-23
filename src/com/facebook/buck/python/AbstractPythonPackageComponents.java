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

package com.facebook.buck.python;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable(builder = false)
@BuckStyleImmutable
abstract class AbstractPythonPackageComponents implements RuleKeyAppendable {

  private static final PythonPackageComponents EMPTY =
      PythonPackageComponents.of(
          /* modules */ ImmutableMap.of(),
          /* resources */ ImmutableMap.of(),
          /* nativeLibraries */ ImmutableMap.of(),
          /* prebuiltLibraries */ ImmutableSet.of(),
          /* zipSafe */ Optional.empty());

  // Python modules as map of their module name to location of the source.
  @Value.Parameter
  public abstract Map<Path, SourcePath> getModules();

  // Resources to include in the package.
  @Value.Parameter
  public abstract Map<Path, SourcePath> getResources();

  // Native libraries to include in the package.
  @Value.Parameter
  public abstract Map<Path, SourcePath> getNativeLibraries();

  // Pre-built python libraries (eggs, wheels). Note that source distributions
  // will not work!
  @Value.Parameter
  public abstract Set<SourcePath> getPrebuiltLibraries();

  @Value.Parameter
  public abstract Optional<Boolean> isZipSafe();

  @Override
  public final void appendToRuleKey(RuleKeyObjectSink sink) {
    // Hash all the input components here so we can detect changes in both input file content
    // and module name mappings.
    // TODO(agallagher): Change the types of these fields from Map to SortedMap so that we don't
    // have to do all this weird stuff to ensure the key is stable. Please update
    // getInputsToCompareToOutput() as well once this is fixed.
    for (ImmutableMap.Entry<String, Map<Path, SourcePath>> part :
        ImmutableMap.of(
                "module", getModules(),
                "resource", getResources(),
                "nativeLibraries", getNativeLibraries())
            .entrySet()) {
      for (Path name : ImmutableSortedSet.copyOf(part.getValue().keySet())) {
        sink.setReflectively(part.getKey() + ":" + name, part.getValue().get(name));
      }
    }
  }

  /** @return whether there are any native libraries included in these components. */
  public boolean hasNativeCode(CxxPlatform cxxPlatform) {
    for (Path module : getModules().keySet()) {
      if (module.toString().endsWith(cxxPlatform.getSharedLibraryExtension())) {
        return true;
      }
    }
    return false;
  }

  public static PythonPackageComponents of() {
    return EMPTY;
  }

  public ImmutableCollection<BuildRule> getDeps(SourcePathRuleFinder ruleFinder) {
    ImmutableList.Builder<BuildRule> deps = ImmutableList.builder();
    deps.addAll(ruleFinder.filterBuildRuleInputs(getModules().values()));
    deps.addAll(ruleFinder.filterBuildRuleInputs(getResources().values()));
    deps.addAll(ruleFinder.filterBuildRuleInputs(getNativeLibraries().values()));
    deps.addAll(ruleFinder.filterBuildRuleInputs(getPrebuiltLibraries()));
    return deps.build();
  }

  /**
   * A helper class to construct a PythonPackageComponents instance which throws human readable
   * error messages on duplicates.
   */
  public static class Builder {

    // A description of the entity that is building this PythonPackageComponents instance.
    private final BuildTarget owner;

    // The actual maps holding the components.
    private final Map<Path, SourcePath> modules = new HashMap<>();
    private final Map<Path, SourcePath> resources = new HashMap<>();
    private final Map<Path, SourcePath> nativeLibraries = new HashMap<>();
    private final Set<SourcePath> prebuiltLibraries = new LinkedHashSet<>();
    private Optional<Boolean> zipSafe = Optional.empty();

    // Bookkeeping used to for error handling in the presence of duplicate
    // entries.  These data structures map the components named above to the
    // entities that provided them.
    private final Map<Path, BuildTarget> moduleSources = new HashMap<>();
    private final Map<Path, BuildTarget> resourceSources = new HashMap<>();
    private final Map<Path, BuildTarget> nativeLibrarySources = new HashMap<>();

    public Builder(BuildTarget owner) {
      this.owner = owner;
    }

    private HumanReadableException createDuplicateError(
        String type, Path destination, BuildTarget sourceA, BuildTarget sourceB) {
      return new HumanReadableException(
          "%s: found duplicate entries for %s %s when creating python package (%s and %s)",
          owner, type, destination, sourceA, sourceB);
    }

    private Builder add(
        String type,
        Map<Path, SourcePath> builder,
        Map<Path, BuildTarget> sourceDescs,
        Path destination,
        SourcePath source,
        BuildTarget sourceDesc) {
      SourcePath existing = builder.put(destination, source);
      if (existing != null && !existing.equals(source)) {
        throw createDuplicateError(
            type,
            destination,
            sourceDesc,
            Preconditions.checkNotNull(sourceDescs.get(destination)));
      }
      sourceDescs.put(destination, sourceDesc);
      return this;
    }

    private Builder add(
        String type,
        Map<Path, SourcePath> builder,
        Map<Path, BuildTarget> sourceDescs,
        Map<Path, SourcePath> toAdd,
        BuildTarget sourceDesc) {
      for (Map.Entry<Path, SourcePath> ent : toAdd.entrySet()) {
        add(type, builder, sourceDescs, ent.getKey(), ent.getValue(), sourceDesc);
      }
      return this;
    }

    public Builder addModule(Path destination, SourcePath source, BuildTarget from) {
      return add("module", modules, moduleSources, destination, source, from);
    }

    public Builder addModules(Map<Path, SourcePath> sources, BuildTarget from) {
      return add("module", modules, moduleSources, sources, from);
    }

    public Builder addResources(Map<Path, SourcePath> sources, BuildTarget from) {
      return add("resource", resources, resourceSources, sources, from);
    }

    public Builder addNativeLibraries(Path destination, SourcePath source, BuildTarget from) {
      return add(
          "native library", nativeLibraries, nativeLibrarySources, destination, source, from);
    }

    public Builder addNativeLibraries(Map<Path, SourcePath> sources, BuildTarget from) {
      return add("native library", nativeLibraries, nativeLibrarySources, sources, from);
    }

    public Builder addPrebuiltLibraries(Set<SourcePath> sources) {
      prebuiltLibraries.addAll(sources);
      return this;
    }

    public Builder addComponent(PythonPackageComponents other, BuildTarget from) {
      addModules(other.getModules(), from);
      addResources(other.getResources(), from);
      addNativeLibraries(other.getNativeLibraries(), from);
      addPrebuiltLibraries(other.getPrebuiltLibraries());
      addZipSafe(other.isZipSafe());
      return this;
    }

    public Builder addZipSafe(Optional<Boolean> zipSafe) {
      if (!this.zipSafe.isPresent() && !zipSafe.isPresent()) {
        return this;
      }

      this.zipSafe = Optional.of(this.zipSafe.orElse(true) && zipSafe.orElse(true));
      return this;
    }

    public PythonPackageComponents build() {
      return PythonPackageComponents.of(
          ImmutableMap.copyOf(modules),
          ImmutableMap.copyOf(resources),
          ImmutableMap.copyOf(nativeLibraries),
          ImmutableSet.copyOf(prebuiltLibraries),
          zipSafe);
    }
  }
}
