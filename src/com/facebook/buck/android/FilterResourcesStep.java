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

import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.shell.BashStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.util.DefaultFilteredDirectoryCopier;
import com.facebook.buck.util.Escaper;
import com.facebook.buck.util.FilteredDirectoryCopier;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * This {@link com.facebook.buck.step.Step} copies {@code res} directories to a different location,
 * while filtering out certain resources.
 */
public class FilterResourcesStep implements Step {

  private static final Pattern DRAWABLE_PATH_PATTERN =
      Pattern.compile(".*drawable.*/.*(png|jpg|jpeg|gif|webp|xml)", Pattern.CASE_INSENSITIVE);
  // Android doesn't scale these, so we don't need to scale or filter them either.
  private static final Pattern DRAWABLE_EXCLUDE_PATTERN =
      Pattern.compile(".*-nodpi.*", Pattern.CASE_INSENSITIVE);

  private static final Logger LOG = Logger.get(FilterResourcesStep.class);

  @VisibleForTesting
  static final Pattern NON_ENGLISH_STRINGS_FILE_PATH =
      Pattern.compile("\\b|.*/res/values-([a-z]{2})(?:-r([A-Z]{2}))*/strings.xml");

  private final ProjectFilesystem filesystem;
  private final ImmutableBiMap<Path, Path> inResDirToOutResDirMap;
  private final boolean filterByDensity;
  private final boolean enableStringWhitelisting;
  private final ImmutableSet<Path> whitelistedStringDirs;
  private final ImmutableSet<String> locales;
  private final FilteredDirectoryCopier filteredDirectoryCopier;
  @Nullable private final Set<ResourceFilters.Density> targetDensities;
  @Nullable private final DrawableFinder drawableFinder;
  @Nullable private final ImageScaler imageScaler;

  /**
   * Creates a command that filters a specified set of directories.
   *
   * @param inResDirToOutResDirMap set of {@code res} directories to filter
   * @param filterByDensity whether to filter all resources by DPI
   * @param enableStringWhitelisting whether to filter strings based on a whitelist
   * @param whitelistedStringDirs set of directories containing string resource files that must not
   *     be filtered out.
   * @param locales set of locales that the localized strings.xml files within {@code values-*}
   *     directories should be filtered by. This is useful if there are multiple apps that support a
   *     different set of locales that share a module. If empty, no filtering is performed.
   * @param filteredDirectoryCopier refer {@link FilteredDirectoryCopier}
   * @param targetDensities densities we're interested in keeping (e.g. {@code mdpi}, {@code hdpi}
   *     etc.) Only applicable if filterByDensity is true
   * @param drawableFinder refer {@link DrawableFinder}. Only applicable if filterByDensity is true.
   * @param imageScaler if not null, use the {@link ImageScaler} to downscale higher-density
   *     drawables for which we weren't able to find an image file of the proper density (as opposed
   *     to allowing Android to do it at runtime). Only applicable if filterByDensity. is true.
   */
  @VisibleForTesting
  FilterResourcesStep(
      ProjectFilesystem filesystem,
      ImmutableBiMap<Path, Path> inResDirToOutResDirMap,
      boolean filterByDensity,
      boolean enableStringWhitelisting,
      ImmutableSet<Path> whitelistedStringDirs,
      ImmutableSet<String> locales,
      FilteredDirectoryCopier filteredDirectoryCopier,
      @Nullable Set<ResourceFilters.Density> targetDensities,
      @Nullable DrawableFinder drawableFinder,
      @Nullable ImageScaler imageScaler) {

    Preconditions.checkArgument(filterByDensity || enableStringWhitelisting || !locales.isEmpty());
    Preconditions.checkArgument(
        !filterByDensity || (targetDensities != null && drawableFinder != null));

    this.filesystem = filesystem;
    this.inResDirToOutResDirMap = inResDirToOutResDirMap;
    this.filterByDensity = filterByDensity;
    this.enableStringWhitelisting = enableStringWhitelisting;
    this.whitelistedStringDirs = whitelistedStringDirs;
    this.locales = locales;
    this.filteredDirectoryCopier = filteredDirectoryCopier;
    this.targetDensities = targetDensities;
    this.drawableFinder = drawableFinder;
    this.imageScaler = imageScaler;
  }

  @Override
  public StepExecutionResult execute(ExecutionContext context)
      throws IOException, InterruptedException {
    return StepExecutionResult.of(doExecute(context));
  }

  private int doExecute(ExecutionContext context) throws IOException, InterruptedException {
    boolean canDownscale = imageScaler != null && imageScaler.isAvailable(context);
    LOG.info(
        "FilterResourcesStep: canDownscale: %s. imageScalar non-null: %s.",
        canDownscale, imageScaler != null);

    // Create filtered copies of all resource directories. These will be passed to aapt instead.
    filteredDirectoryCopier.copyDirs(
        filesystem, inResDirToOutResDirMap, getFilteringPredicate(context));

    // If an ImageScaler was specified, but only if it is available, try to apply it.
    if (canDownscale && filterByDensity) {
      scaleUnmatchedDrawables(context);
    }

    return 0;
  }

  @VisibleForTesting
  Predicate<Path> getFilteringPredicate(ExecutionContext context) throws IOException {
    List<Predicate<Path>> pathPredicates = new ArrayList<>();

    if (filterByDensity) {
      Preconditions.checkNotNull(targetDensities);
      Set<Path> rootResourceDirs = inResDirToOutResDirMap.keySet();

      pathPredicates.add(ResourceFilters.createDensityFilter(filesystem, targetDensities));

      Preconditions.checkNotNull(drawableFinder);
      Set<Path> drawables = drawableFinder.findDrawables(rootResourceDirs, filesystem);
      pathPredicates.add(
          ResourceFilters.createImageDensityFilter(
              drawables,
              targetDensities,
              /* canDownscale */ imageScaler != null && imageScaler.isAvailable(context)));
    }

    final boolean localeFilterEnabled = !locales.isEmpty();
    if (localeFilterEnabled || enableStringWhitelisting) {
      pathPredicates.add(
          path -> {
            Matcher matcher =
                NON_ENGLISH_STRINGS_FILE_PATH.matcher(MorePaths.pathWithUnixSeparators(path));
            if (!matcher.matches()) {
              return true;
            }

            if (enableStringWhitelisting) {
              return isPathWhitelisted(path);
            } else {
              Preconditions.checkState(localeFilterEnabled);
              String locale = matcher.group(1);
              if (matcher.group(2) != null) {
                locale += "_" + matcher.group(2);
              }

              return locales.contains(locale);
            }
          });
    }
    return Predicates.and(pathPredicates);
  }

  private boolean isPathWhitelisted(Path path) {
    for (Path whitelistedStringDir : whitelistedStringDirs) {
      if (path.startsWith(whitelistedStringDir)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public String getShortName() {
    return "resource_filtering";
  }

  @Override
  public String getDescription(ExecutionContext context) {
    return "Filtering drawable and string resources.";
  }

  /**
   * Looks through filtered drawables for files not of the target density and replaces them with
   * scaled versions.
   *
   * <p>Any drawables found by this step didn't have equivalents in the target density. If they are
   * of a higher density, we can replicate what Android does and downscale them at compile-time.
   */
  private void scaleUnmatchedDrawables(ExecutionContext context)
      throws IOException, InterruptedException {
    ResourceFilters.Density targetDensity = ResourceFilters.Density.ORDERING.max(targetDensities);

    // Go over all the images that remain after filtering.
    Preconditions.checkNotNull(drawableFinder);
    Collection<Path> drawables =
        drawableFinder.findDrawables(inResDirToOutResDirMap.values(), filesystem);
    for (Path drawable : drawables) {
      if (drawable.toString().endsWith(".xml")) {
        // Skip SVG and network drawables.
        continue;
      }
      if (drawable.toString().endsWith(".9.png")) {
        // Skip nine-patch for now.
        continue;
      }
      if (drawable.toString().endsWith(".webp")) {
        // Skip webp for now.
        continue;
      }

      ResourceFilters.Qualifiers qualifiers = ResourceFilters.Qualifiers.from(drawable.getParent());
      ResourceFilters.Density density = qualifiers.density;

      // If the image has a qualifier but it's not the right one.
      Preconditions.checkNotNull(targetDensities);
      if (!targetDensities.contains(density)) {

        // Replace density qualifier with target density using regular expression to match
        // the qualifier in the context of a path to a drawable.
        String fromDensity =
            (density == ResourceFilters.Density.NO_QUALIFIER ? "" : "-") + density.toString();
        Path destination =
            Paths.get(
                MorePaths.pathWithUnixSeparators(drawable)
                    .replaceFirst(
                        "((?:^|/)drawable[^/]*)" + Pattern.quote(fromDensity) + "(-|$|/)",
                        "$1-" + targetDensity + "$2"));

        double factor = targetDensity.value() / density.value();
        if (factor >= 1.0) {
          // There is no point in up-scaling, or converting between drawable and drawable-mdpi.
          continue;
        }

        // Make sure destination folder exists and perform downscaling.
        filesystem.createParentDirs(destination);
        Preconditions.checkNotNull(imageScaler);
        imageScaler.scale(factor, drawable, destination, context);

        // Delete source file.
        filesystem.deleteFileAtPath(drawable);

        // Delete newly-empty directories to prevent missing resources errors in apkbuilder.
        Path parent = drawable.getParent();
        if (filesystem.listFiles(parent).length == 0) {
          filesystem.deleteFileAtPath(parent);
        }
      }
    }
  }

  public interface DrawableFinder {
    public ImmutableSet<Path> findDrawables(Collection<Path> dirs, ProjectFilesystem filesystem)
        throws IOException;
  }

  public static class DefaultDrawableFinder implements DrawableFinder {

    private static final DefaultDrawableFinder instance = new DefaultDrawableFinder();

    public static DefaultDrawableFinder getInstance() {
      return instance;
    }

    @Override
    public ImmutableSet<Path> findDrawables(Collection<Path> dirs, ProjectFilesystem filesystem)
        throws IOException {
      final ImmutableSet.Builder<Path> drawableBuilder = ImmutableSet.builder();
      for (Path dir : dirs) {
        filesystem.walkRelativeFileTree(
            dir,
            new SimpleFileVisitor<Path>() {
              @Override
              public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
                String unixPath = MorePaths.pathWithUnixSeparators(path);
                if (DRAWABLE_PATH_PATTERN.matcher(unixPath).matches()
                    && !DRAWABLE_EXCLUDE_PATTERN.matcher(unixPath).matches()) {
                  // The path is normalized so that the value can be matched against patterns.
                  drawableBuilder.add(path);
                }
                return FileVisitResult.CONTINUE;
              }
            });
      }
      return drawableBuilder.build();
    }
  }

  public interface ImageScaler {
    boolean isAvailable(ExecutionContext context);

    void scale(double factor, Path source, Path destination, ExecutionContext context)
        throws IOException, InterruptedException;
  }

  /**
   * Implementation of {@link ImageScaler} that uses ImageMagick's {@code convert} command.
   *
   * @see <a href="http://www.imagemagick.org/script/index.php">ImageMagick</a>
   */
  static class ImageMagickScaler implements ImageScaler {

    private final Path workingDirectory;

    public ImageMagickScaler(Path workingDirectory) {
      this.workingDirectory = workingDirectory;
    }

    @Override
    public boolean isAvailable(ExecutionContext context) {
      return new ExecutableFinder()
          .getOptionalExecutable(Paths.get("convert"), context.getEnvironment())
          .isPresent();
    }

    @Override
    public void scale(double factor, Path source, Path destination, ExecutionContext context)
        throws IOException, InterruptedException {
      Step convertStep =
          new BashStep(
              workingDirectory,
              "convert",
              "-adaptive-resize",
              (int) (factor * 100) + "%",
              Escaper.escapeAsBashString(source),
              Escaper.escapeAsBashString(destination));

      if (!convertStep.execute(context).isSuccess()) {
        throw new HumanReadableException("Cannot scale " + source + " to " + destination);
      }
    }
  }

  /** Helper class for interpreting the resource_filter argument to android_binary(). */
  public static class ResourceFilter implements RuleKeyAppendable {

    static final ResourceFilter EMPTY_FILTER = new ResourceFilter(ImmutableList.of());

    private final Set<String> filter;
    private final Set<ResourceFilters.Density> densities;
    private final boolean downscale;

    public ResourceFilter(List<String> resourceFilter) {
      this.filter = ImmutableSet.copyOf(resourceFilter);
      this.densities = Sets.newHashSet();

      boolean downscale = false;
      for (String component : filter) {
        if ("downscale".equals(component)) {
          downscale = true;
        } else {
          densities.add(ResourceFilters.Density.from(component));
        }
      }

      this.downscale = downscale;
    }

    public boolean shouldDownscale() {
      return isEnabled() && downscale;
    }

    @Nullable
    public Set<ResourceFilters.Density> getDensities() {
      return densities;
    }

    public boolean isEnabled() {
      return !densities.isEmpty();
    }

    public String getDescription() {
      return filter.toString();
    }

    @Override
    public void appendToRuleKey(RuleKeyObjectSink sink) {
      sink.setReflectively("filter", getDescription());
    }

    @VisibleForTesting
    Set<String> getFilter() {
      return filter;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    @Nullable private ProjectFilesystem filesystem;
    @Nullable private ImmutableBiMap<Path, Path> inResDirToOutResDirMap;
    @Nullable private ResourceFilter resourceFilter;
    private ImmutableSet<Path> whitelistedStringDirs = ImmutableSet.of();
    private ImmutableSet<String> locales = ImmutableSet.of();
    private boolean enableStringWhitelisting = false;

    private Builder() {}

    public Builder setProjectFilesystem(ProjectFilesystem filesystem) {
      this.filesystem = filesystem;
      return this;
    }

    public Builder setInResToOutResDirMap(ImmutableBiMap<Path, Path> inResDirToOutResDirMap) {
      this.inResDirToOutResDirMap = inResDirToOutResDirMap;
      return this;
    }

    public Builder setResourceFilter(ResourceFilter resourceFilter) {
      this.resourceFilter = resourceFilter;
      return this;
    }

    public Builder enableStringWhitelisting() {
      this.enableStringWhitelisting = true;
      return this;
    }

    public Builder setWhitelistedStringDirs(ImmutableSet<Path> whitelistedStringDirs) {
      this.whitelistedStringDirs = whitelistedStringDirs;
      return this;
    }

    public Builder setLocales(ImmutableSet<String> locales) {
      this.locales = locales;
      return this;
    }

    public FilterResourcesStep build() {
      Preconditions.checkNotNull(filesystem);
      Preconditions.checkNotNull(resourceFilter);
      LOG.info("FilterResourcesStep.Builder: resource filter: %s", resourceFilter);
      Preconditions.checkNotNull(inResDirToOutResDirMap);
      return new FilterResourcesStep(
          filesystem,
          inResDirToOutResDirMap,
          /* filterByDensity */ resourceFilter.isEnabled(),
          enableStringWhitelisting,
          whitelistedStringDirs,
          locales,
          DefaultFilteredDirectoryCopier.getInstance(),
          resourceFilter.getDensities(),
          DefaultDrawableFinder.getInstance(),
          resourceFilter.shouldDownscale()
              ? new ImageMagickScaler(filesystem.getRootPath())
              : null);
    }
  }
}
