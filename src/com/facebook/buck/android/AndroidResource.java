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

package com.facebook.buck.android;

import static com.facebook.buck.rules.BuildableProperties.Kind.ANDROID;
import static com.facebook.buck.rules.BuildableProperties.Kind.LIBRARY;

import com.facebook.buck.android.aapt.MiniAapt;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildOutputInitializer;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.BuildableProperties;
import com.facebook.buck.rules.ExplicitBuildTargetSourcePath;
import com.facebook.buck.rules.InitializableFromDisk;
import com.facebook.buck.rules.OnDiskBuildInfo;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.keys.SupportsInputBasedRuleKey;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.TouchStep;
import com.facebook.buck.step.fs.WriteFileStep;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.MoreMaps;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

/**
 * An object that represents the resources of an android library.
 *
 * <p>Suppose this were a rule defined in <code>src/com/facebook/feed/BUCK</code>:
 *
 * <pre>
 * android_resources(
 *   name = 'res',
 *   res = 'res',
 *   assets = 'buck-assets',
 *   deps = [
 *     '//first-party/orca/lib-ui:lib-ui',
 *   ],
 * )
 * </pre>
 */
public class AndroidResource extends AbstractBuildRule
    implements AndroidPackageable,
        HasAndroidResourceDeps,
        InitializableFromDisk<String>,
        SupportsInputBasedRuleKey {

  private static final BuildableProperties PROPERTIES = new BuildableProperties(ANDROID, LIBRARY);

  @VisibleForTesting
  static final String METADATA_KEY_FOR_R_DOT_JAVA_PACKAGE = "METADATA_KEY_FOR_R_DOT_JAVA_PACKAGE";

  @AddToRuleKey @Nullable private final SourcePath res;

  @SuppressWarnings("PMD.UnusedPrivateField")
  @AddToRuleKey
  private final ImmutableSortedMap<String, SourcePath> resSrcs;

  @AddToRuleKey @Nullable private final SourcePath assets;

  @SuppressWarnings("PMD.UnusedPrivateField")
  @AddToRuleKey
  private final ImmutableSortedMap<String, SourcePath> assetsSrcs;

  private final Path pathToTextSymbolsDir;
  private final Path pathToTextSymbolsFile;
  private final Path pathToRDotJavaPackageFile;

  @AddToRuleKey @Nullable private final SourcePath manifestFile;

  @AddToRuleKey private final Supplier<ImmutableSortedSet<? extends SourcePath>> symbolsOfDeps;

  @AddToRuleKey private final boolean hasWhitelistedStrings;

  @AddToRuleKey private final boolean resourceUnion;

  private final boolean isGrayscaleImageProcessingEnabled;

  private final ImmutableSortedSet<BuildRule> deps;

  private final BuildOutputInitializer<String> buildOutputInitializer;

  /** This is the original {@code package} argument passed to this rule. */
  @AddToRuleKey @Nullable private final String rDotJavaPackageArgument;

  /**
   * Supplier that returns the package for the Java class generated for the resources in {@link
   * #res}, if any. The value for this supplier is determined, as follows:
   *
   * <ul>
   *   <li>If the user specified a {@code package} argument, the supplier will return that value.
   *   <li>Failing that, when the rule is built, it will parse the package from the file specified
   *       by the {@code manifest} so that it can be returned by this supplier. (Note this also
   *       needs to work correctly if the rule is initialized from disk.)
   *   <li>In all other cases (e.g., both {@code package} and {@code manifest} are unspecified), the
   *       behavior is undefined.
   * </ul>
   */
  private final Supplier<String> rDotJavaPackageSupplier;

  private final AtomicReference<String> rDotJavaPackage;

  public AndroidResource(
      BuildRuleParams buildRuleParams,
      SourcePathRuleFinder ruleFinder,
      final ImmutableSortedSet<BuildRule> deps,
      @Nullable SourcePath res,
      ImmutableSortedMap<Path, SourcePath> resSrcs,
      @Nullable String rDotJavaPackageArgument,
      @Nullable SourcePath assets,
      ImmutableSortedMap<Path, SourcePath> assetsSrcs,
      @Nullable SourcePath manifestFile,
      Supplier<ImmutableSortedSet<? extends SourcePath>> symbolFilesFromDeps,
      boolean hasWhitelistedStrings,
      boolean resourceUnion,
      boolean isGrayscaleImageProcessingEnabled) {
    super(
        buildRuleParams.copyAppendingExtraDeps(
            Suppliers.compose(ruleFinder::filterBuildRuleInputs, symbolFilesFromDeps)));
    if (res != null && rDotJavaPackageArgument == null && manifestFile == null) {
      throw new HumanReadableException(
          "When the 'res' is specified for android_resource() %s, at least one of 'package' or "
              + "'manifest' must be specified.",
          getBuildTarget());
    }

    this.res = res;
    this.resSrcs = MoreMaps.transformKeysAndSort(resSrcs, Path::toString);
    this.assets = assets;
    this.assetsSrcs = MoreMaps.transformKeysAndSort(assetsSrcs, Path::toString);
    this.manifestFile = manifestFile;
    this.symbolsOfDeps = symbolFilesFromDeps;
    this.hasWhitelistedStrings = hasWhitelistedStrings;
    this.resourceUnion = resourceUnion;

    BuildTarget buildTarget = buildRuleParams.getBuildTarget();
    this.pathToTextSymbolsDir =
        BuildTargets.getGenPath(getProjectFilesystem(), buildTarget, "__%s_text_symbols__");
    this.pathToTextSymbolsFile = pathToTextSymbolsDir.resolve("R.txt");
    this.pathToRDotJavaPackageFile = pathToTextSymbolsDir.resolve("RDotJavaPackage.txt");

    this.deps = deps;

    this.buildOutputInitializer = new BuildOutputInitializer<>(buildTarget, this);

    this.rDotJavaPackageArgument = rDotJavaPackageArgument;
    this.rDotJavaPackage = new AtomicReference<>(rDotJavaPackageArgument);

    this.rDotJavaPackageSupplier =
        () -> {
          String rDotJavaPackage1 = AndroidResource.this.rDotJavaPackage.get();
          if (rDotJavaPackage1 != null) {
            return rDotJavaPackage1;
          } else {
            throw new RuntimeException(
                "rDotJavaPackage for "
                    + AndroidResource.this.getBuildTarget().toString()
                    + " was requested before it was made available.");
          }
        };
    this.isGrayscaleImageProcessingEnabled = isGrayscaleImageProcessingEnabled;
  }

  public AndroidResource(
      final BuildRuleParams buildRuleParams,
      SourcePathRuleFinder ruleFinder,
      final ImmutableSortedSet<BuildRule> deps,
      @Nullable SourcePath res,
      ImmutableSortedMap<Path, SourcePath> resSrcs,
      @Nullable String rDotJavaPackageArgument,
      @Nullable SourcePath assets,
      ImmutableSortedMap<Path, SourcePath> assetsSrcs,
      @Nullable SourcePath manifestFile,
      boolean hasWhitelistedStrings) {
    this(
        buildRuleParams,
        ruleFinder,
        deps,
        res,
        resSrcs,
        rDotJavaPackageArgument,
        assets,
        assetsSrcs,
        manifestFile,
        hasWhitelistedStrings,
        /* resourceUnion */ false,
        /* isGrayscaleImageProcessingEnabled */ false);
  }

  public AndroidResource(
      final BuildRuleParams buildRuleParams,
      SourcePathRuleFinder ruleFinder,
      final ImmutableSortedSet<BuildRule> deps,
      @Nullable SourcePath res,
      ImmutableSortedMap<Path, SourcePath> resSrcs,
      @Nullable String rDotJavaPackageArgument,
      @Nullable SourcePath assets,
      ImmutableSortedMap<Path, SourcePath> assetsSrcs,
      @Nullable SourcePath manifestFile,
      boolean hasWhitelistedStrings,
      boolean resourceUnion,
      boolean isGrayscaleImageProcessingEnabled) {
    this(
        buildRuleParams,
        ruleFinder,
        deps,
        res,
        resSrcs,
        rDotJavaPackageArgument,
        assets,
        assetsSrcs,
        manifestFile,
        () ->
            FluentIterable.from(buildRuleParams.getBuildDeps())
                .filter(HasAndroidResourceDeps.class)
                .filter(input -> input.getRes() != null)
                .transform(HasAndroidResourceDeps::getPathToTextSymbolsFile)
                .toSortedSet(Ordering.natural()),
        hasWhitelistedStrings,
        resourceUnion,
        isGrayscaleImageProcessingEnabled);
  }

  @Override
  @Nullable
  public SourcePath getRes() {
    return res;
  }

  @Override
  @Nullable
  public SourcePath getAssets() {
    return assets;
  }

  @Nullable
  public SourcePath getManifestFile() {
    return manifestFile;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, final BuildableContext buildableContext) {
    buildableContext.recordArtifact(Preconditions.checkNotNull(pathToTextSymbolsFile));
    buildableContext.recordArtifact(Preconditions.checkNotNull(pathToRDotJavaPackageFile));

    ImmutableList.Builder<Step> steps = ImmutableList.builder();
    steps.addAll(
        MakeCleanDirectoryStep.of(
            getProjectFilesystem(), Preconditions.checkNotNull(pathToTextSymbolsDir)));
    if (getRes() == null) {
      return steps
          .add(new TouchStep(getProjectFilesystem(), pathToTextSymbolsFile))
          .add(
              new WriteFileStep(
                  getProjectFilesystem(),
                  rDotJavaPackageArgument == null ? "" : rDotJavaPackageArgument,
                  pathToRDotJavaPackageFile,
                  false /* executable */))
          .build();
    }

    // If the 'package' was not specified for this android_resource(), then attempt to parse it
    // from the AndroidManifest.xml.
    if (rDotJavaPackageArgument == null) {
      Preconditions.checkNotNull(
          manifestFile,
          "manifestFile cannot be null when res is non-null and rDotJavaPackageArgument is "
              + "null. This should already be enforced by the constructor.");
      steps.add(
          new ExtractFromAndroidManifestStep(
              context.getSourcePathResolver().getAbsolutePath(manifestFile),
              getProjectFilesystem(),
              buildableContext,
              METADATA_KEY_FOR_R_DOT_JAVA_PACKAGE,
              Preconditions.checkNotNull(pathToRDotJavaPackageFile)));
    } else {
      steps.add(
          new WriteFileStep(
              getProjectFilesystem(),
              rDotJavaPackageArgument,
              pathToRDotJavaPackageFile,
              false /* executable */));
    }

    ImmutableSet<Path> pathsToSymbolsOfDeps =
        symbolsOfDeps
            .get()
            .stream()
            .map(context.getSourcePathResolver()::getAbsolutePath)
            .collect(MoreCollectors.toImmutableSet());
    steps.add(
        new MiniAapt(
            context.getSourcePathResolver(),
            getProjectFilesystem(),
            Preconditions.checkNotNull(res),
            Preconditions.checkNotNull(pathToTextSymbolsFile),
            pathsToSymbolsOfDeps,
            resourceUnion,
            isGrayscaleImageProcessingEnabled));
    return steps.build();
  }

  @Override
  @Nullable
  public SourcePath getSourcePathToOutput() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), pathToTextSymbolsDir);
  }

  @Override
  public SourcePath getPathToTextSymbolsFile() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), pathToTextSymbolsFile);
  }

  @Override
  public SourcePath getPathToRDotJavaPackageFile() {
    return new ExplicitBuildTargetSourcePath(getBuildTarget(), pathToRDotJavaPackageFile);
  }

  @Override
  public String getRDotJavaPackage() {
    String rDotJavaPackage = rDotJavaPackageSupplier.get();
    if (rDotJavaPackage == null) {
      throw new RuntimeException("No package for " + getBuildTarget());
    }
    return rDotJavaPackage;
  }

  @Override
  public BuildableProperties getProperties() {
    return PROPERTIES;
  }

  @Override
  public String initializeFromDisk(OnDiskBuildInfo onDiskBuildInfo) {
    String rDotJavaPackageFromFile =
        getProjectFilesystem().readFirstLine(pathToRDotJavaPackageFile).get();
    if (rDotJavaPackageArgument != null
        && !rDotJavaPackageFromFile.equals(rDotJavaPackageArgument)) {
      throw new RuntimeException(
          String.format(
              "%s contains incorrect rDotJavaPackage (%s!=%s)",
              pathToRDotJavaPackageFile, rDotJavaPackageFromFile, rDotJavaPackageArgument));
    }
    rDotJavaPackage.set(rDotJavaPackageFromFile);
    return rDotJavaPackageFromFile;
  }

  @Override
  public BuildOutputInitializer<String> getBuildOutputInitializer() {
    return buildOutputInitializer;
  }

  @Override
  public Iterable<AndroidPackageable> getRequiredPackageables() {
    return AndroidPackageableCollector.getPackageableRules(deps);
  }

  @Override
  public void addToCollector(AndroidPackageableCollector collector) {
    if (res != null) {
      if (hasWhitelistedStrings) {
        collector.addStringWhitelistedResourceDirectory(getBuildTarget(), res);
      } else {
        collector.addResourceDirectory(getBuildTarget(), res);
      }
    }
    if (assets != null) {
      collector.addAssetsDirectory(getBuildTarget(), assets);
    }
  }
}
