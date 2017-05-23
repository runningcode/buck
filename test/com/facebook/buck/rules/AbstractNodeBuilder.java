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

package com.facebook.buck.rules;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.versions.Version;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Support class for writing builders for nodes of a {@link TargetGraph} and {@link ActionGraph}
 * ({@link TargetNode} and {@link BuildRule} respectively) mirroring the behavior seen when running
 * the actual parser as closely as possible.
 */
public abstract class AbstractNodeBuilder<
    TArgBuilder, TArg, TDescription extends Description<TArg>, TBuildRule extends BuildRule> {
  protected static final TypeCoercerFactory TYPE_COERCER_FACTORY = new DefaultTypeCoercerFactory();
  private static final VisibilityPatternParser VISIBILITY_PATTERN_PARSER =
      new VisibilityPatternParser();

  protected final TDescription description;
  protected final ProjectFilesystem filesystem;
  protected final BuildTarget target;
  protected final TArgBuilder argBuilder;
  protected final CellPathResolver cellRoots;
  @Nullable private final HashCode rawHashCode;
  private Optional<ImmutableMap<BuildTarget, Version>> selectedVersions = Optional.empty();

  protected AbstractNodeBuilder(TDescription description, BuildTarget target) {
    this(description, target, new FakeProjectFilesystem(), null);
  }

  protected AbstractNodeBuilder(
      TDescription description, BuildTarget target, ProjectFilesystem projectFilesystem) {
    this(description, target, projectFilesystem, null);
  }

  protected AbstractNodeBuilder(
      TDescription description,
      BuildTarget target,
      ProjectFilesystem projectFilesystem,
      HashCode hashCode) {
    this.description = description;
    this.filesystem = projectFilesystem;
    this.target = target;
    this.argBuilder = makeArgBuilder(description);
    this.rawHashCode = hashCode;

    this.cellRoots = new FakeCellPathResolver(projectFilesystem);
  }

  @SuppressWarnings("unchecked")
  private TArgBuilder makeArgBuilder(TDescription description) {
    Class<? extends TArg> constructorArgType = description.getConstructorArgType();
    TArgBuilder builder;
    try {
      builder = (TArgBuilder) constructorArgType.getMethod("builder").invoke(null);
      // Set a default value for name from the target. The real coercer stack implicitly sets name,
      // but we're not going through that stack so we emulate it instead.
      // If setName is explicitly called, its value with override this one.
      builder.getClass().getMethod("setName", String.class).invoke(builder, target.getShortName());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return builder;
  }

  public final TBuildRule build(BuildRuleResolver resolver) throws NoSuchBuildTargetException {
    return build(resolver, filesystem, TargetGraph.EMPTY);
  }

  public final TBuildRule build(BuildRuleResolver resolver, TargetGraph targetGraph)
      throws NoSuchBuildTargetException {
    return build(resolver, filesystem, targetGraph);
  }

  public final TBuildRule build(BuildRuleResolver resolver, ProjectFilesystem filesystem)
      throws NoSuchBuildTargetException {
    return build(resolver, filesystem, TargetGraph.EMPTY);
  }

  public final TBuildRule build(
      BuildRuleResolver resolver, ProjectFilesystem filesystem, TargetGraph targetGraph)
      throws NoSuchBuildTargetException {

    // The BuildRule determines its deps by extracting them from the rule parameters.
    BuildRuleParams params = createBuildRuleParams(resolver, filesystem);

    TArg builtArg = getPopulatedArg();
    @SuppressWarnings("unchecked")
    TBuildRule rule =
        (TBuildRule)
            description.createBuildRule(targetGraph, params, resolver, cellRoots, builtArg);
    resolver.addToIndex(rule);
    return rule;
  }

  public TargetNode<TArg, TDescription> build() {
    try {
      HashCode hash =
          rawHashCode == null
              ? Hashing.sha1().hashString(target.getFullyQualifiedName(), UTF_8)
              : rawHashCode;
      TargetNodeFactory factory = new TargetNodeFactory(TYPE_COERCER_FACTORY);
      TArg populatedArg = getPopulatedArg();
      TargetNode<TArg, TDescription> node =
          factory.create(
              // This hash will do in a pinch.
              hash,
              description,
              populatedArg,
              filesystem,
              target,
              getDepsFromArg(populatedArg),
              ImmutableSet.of(
                  VISIBILITY_PATTERN_PARSER.parse(null, VisibilityPatternParser.VISIBILITY_PUBLIC)),
              ImmutableSet.of(),
              cellRoots);
      if (selectedVersions.isPresent()) {
        node =
            node.withTargetConstructorArgDepsAndSelectedVerisons(
                node.getBuildTarget(),
                node.getConstructorArg(),
                node.getDeclaredDeps(),
                node.getExtraDeps(),
                node.getTargetGraphOnlyDeps(),
                selectedVersions);
      }
      return node;
    } catch (NoSuchBuildTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public BuildRuleParams createBuildRuleParams(
      BuildRuleResolver resolver, ProjectFilesystem filesystem) {
    TargetNode<?, ?> node = build();
    return new FakeBuildRuleParamsBuilder(target)
        .setProjectFilesystem(filesystem)
        .setDeclaredDeps(resolver.getAllRules(node.getDeclaredDeps()))
        .setExtraDeps(resolver.getAllRules(node.getExtraDeps()))
        .build();
  }

  @SuppressWarnings("unchecked")
  public ImmutableSortedSet<BuildTarget> findImplicitDeps() {
    ImplicitDepsInferringDescription<TArg> desc =
        (ImplicitDepsInferringDescription<TArg>) description;
    ImmutableSortedSet.Builder<BuildTarget> builder = ImmutableSortedSet.naturalOrder();
    desc.findDepsForTargetFromConstructorArgs(
        target, cellRoots, getPopulatedArg(), builder, ImmutableSortedSet.naturalOrder());
    return builder.build();
  }

  public BuildTarget getTarget() {
    return target;
  }

  public AbstractNodeBuilder<TArgBuilder, TArg, TDescription, TBuildRule> setSelectedVersions(
      ImmutableMap<BuildTarget, Version> selectedVersions) {
    this.selectedVersions = Optional.of(selectedVersions);
    return this;
  }

  protected TArgBuilder getArgForPopulating() {
    return argBuilder;
  }

  @SuppressWarnings("unchecked")
  protected TArg getPopulatedArg() {
    try {
      return (TArg) argBuilder.getClass().getMethod("build").invoke(argBuilder);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected final ImmutableSortedSet<BuildTarget> getDepsFromArg(TArg arg) {
    if (!(arg instanceof HasDeclaredDeps)) {
      return ImmutableSortedSet.of();
    }
    return ((HasDeclaredDeps) arg).getDeps();
  }
}
