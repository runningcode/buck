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

package com.facebook.buck.io;

import com.facebook.buck.config.Config;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.autosparse.AutoSparseConfig;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.facebook.buck.zip.CustomZipEntry;
import com.facebook.buck.zip.CustomZipOutputStream;
import com.facebook.buck.zip.ZipOutputStreams;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** An injectable service for interacting with the filesystem relative to the project root. */
public class ProjectFilesystem {

  /** Controls the behavior of how the source should be treated when copying. */
  public enum CopySourceMode {
    /** Copy the single source file into the destination path. */
    FILE,

    /**
     * Treat the source as a directory and copy each file inside it to the destination path, which
     * must be a directory.
     */
    DIRECTORY_CONTENTS_ONLY,

    /**
     * Treat the source as a directory. Copy the directory and its contents to the destination path,
     * which must be a directory.
     */
    DIRECTORY_AND_CONTENTS,
  }

  // A non-exhaustive list of characters that might indicate that we're about to deal with a glob.
  private static final Pattern GLOB_CHARS = Pattern.compile("[\\*\\?\\{\\[]");

  @VisibleForTesting static final String BUCK_BUCKD_DIR_KEY = "buck.buckd_dir";

  private final Path projectRoot;
  private final BuckPaths buckPaths;

  private final ImmutableSet<PathOrGlobMatcher> blackListedPaths;
  private final ImmutableSet<PathOrGlobMatcher> blackListedDirectories;

  /** Supplier that returns an absolute path that is guaranteed to exist. */
  private final Supplier<Path> tmpDir;

  private final ProjectFilesystemDelegate delegate;

  // Defaults to false, and so paths should be valid.
  @VisibleForTesting protected boolean ignoreValidityOfPaths;

  public ProjectFilesystem(Path root) throws InterruptedException {
    this(root, new Config());
  }

  public static ProjectFilesystem createNewOrThrowHumanReadableException(Path path)
      throws InterruptedException {
    try {
      // toRealPath() is necessary to resolve symlinks, allowing us to later
      // check whether files are inside or outside of the project without issue.
      return new ProjectFilesystem(path.toRealPath().normalize());
    } catch (IOException e) {
      throw new HumanReadableException(
          String.format(
              ("Failed to resolve project root [%s]."
                  + "Check if it exists and has the right permissions."),
              path.toAbsolutePath()),
          e);
    }
  }

  /**
   * This constructor is restricted to {@code protected} because it is generally best to let {@link
   * ProjectFilesystemDelegateFactory#newInstance(Path, String, AutoSparseConfig)} create an
   * appropriate delegate. Currently, the only case in which we need to override this behavior is in
   * unit tests.
   */
  protected ProjectFilesystem(Path root, ProjectFilesystemDelegate delegate) {
    this(root.getFileSystem(), root, ImmutableSet.of(), getDefaultBuckPaths(root), delegate);
  }

  public ProjectFilesystem(Path root, Config config) throws InterruptedException {
    this(
        root.getFileSystem(),
        root,
        extractIgnorePaths(root, config, getConfiguredBuckPaths(root, config)),
        getConfiguredBuckPaths(root, config),
        ProjectFilesystemDelegateFactory.newInstance(
            root,
            config.getValue("version_control", "hg_cmd").orElse("hg"),
            AutoSparseConfig.of(config)));
  }

  /**
   * For testing purposes, subclasses might want to skip some of the verification done by the
   * constructor on its arguments.
   */
  protected boolean shouldVerifyConstructorArguments() {
    return true;
  }

  private ProjectFilesystem(
      FileSystem vfs,
      final Path root,
      ImmutableSet<PathOrGlobMatcher> blackListedPaths,
      BuckPaths buckPaths,
      ProjectFilesystemDelegate delegate) {
    if (shouldVerifyConstructorArguments()) {
      Preconditions.checkArgument(Files.isDirectory(root), "%s must be a directory", root);
      Preconditions.checkState(vfs.equals(root.getFileSystem()));
      Preconditions.checkArgument(root.isAbsolute());
    }
    this.projectRoot = MorePaths.normalize(root);
    this.delegate = delegate;
    this.ignoreValidityOfPaths = false;
    this.blackListedPaths =
        FluentIterable.from(blackListedPaths)
            .append(
                FluentIterable.from(
                        // "Path" is Iterable, so avoid adding each segment.
                        // We use the default value here because that's what we've always done.
                        ImmutableSet.of(
                            getCacheDir(
                                root, Optional.of(buckPaths.getCacheDir().toString()), buckPaths)))
                    .append(ImmutableSet.of(buckPaths.getTrashDir()))
                    .transform(PathOrGlobMatcher::new))
            .toSet();
    this.buckPaths = buckPaths;

    this.blackListedDirectories =
        FluentIterable.from(this.blackListedPaths)
            .filter(matcher -> matcher.getType() == PathOrGlobMatcher.Type.PATH)
            .transform(
                matcher -> {
                  Path path = matcher.getPath();
                  ImmutableSet<Path> filtered =
                      MorePaths.filterForSubpaths(ImmutableSet.of(path), root);
                  if (filtered.isEmpty()) {
                    return path;
                  }
                  return Iterables.getOnlyElement(filtered);
                })
            // TODO(#10068334) So we claim to ignore this path to preserve existing behaviour, but we
            // really don't end up ignoring it in reality (see extractIgnorePaths).
            .append(ImmutableSet.of(buckPaths.getBuckOut()))
            .transform(PathOrGlobMatcher::new)
            .append(
                Iterables.filter(
                    this.blackListedPaths, input -> input.getType() == PathOrGlobMatcher.Type.GLOB))
            .toSet();
    this.tmpDir =
        Suppliers.memoize(
            () -> {
              Path relativeTmpDir = ProjectFilesystem.this.buckPaths.getTmpDir();
              try {
                mkdirs(relativeTmpDir);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
              return relativeTmpDir;
            });
  }

  private static BuckPaths getDefaultBuckPaths(Path rootPath) {
    return BuckPaths.of(
        rootPath.getFileSystem().getPath(BuckConstant.getBuckOutputPath().toString()));
  }

  private static BuckPaths getConfiguredBuckPaths(Path rootPath, Config config) {
    BuckPaths buckPaths = getDefaultBuckPaths(rootPath);
    Optional<String> configuredBuckOut = config.getValue("project", "buck_out");
    if (configuredBuckOut.isPresent()) {
      buckPaths =
          buckPaths.withConfiguredBuckOut(
              rootPath.getFileSystem().getPath(configuredBuckOut.get()));
    }
    return buckPaths;
  }

  private static Path getCacheDir(Path root, Optional<String> value, BuckPaths buckPaths) {
    String cacheDir = value.orElse(root.resolve(buckPaths.getCacheDir()).toString());
    Path toReturn = root.getFileSystem().getPath(cacheDir);
    toReturn = MorePaths.expandHomeDir(toReturn);
    if (toReturn.isAbsolute()) {
      return toReturn;
    }
    ImmutableSet<Path> filtered = MorePaths.filterForSubpaths(ImmutableSet.of(toReturn), root);
    if (filtered.isEmpty()) {
      // OK. For some reason the relative path managed to be out of our directory.
      return toReturn;
    }
    return Iterables.getOnlyElement(filtered);
  }

  private static ImmutableSet<PathOrGlobMatcher> extractIgnorePaths(
      final Path root, Config config, final BuckPaths buckPaths) {
    ImmutableSet.Builder<PathOrGlobMatcher> builder = ImmutableSet.builder();

    builder.add(new PathOrGlobMatcher(root, ".idea"));

    final String projectKey = "project";
    final String ignoreKey = "ignore";

    String buckdDirProperty = System.getProperty(BUCK_BUCKD_DIR_KEY, ".buckd");
    if (!Strings.isNullOrEmpty(buckdDirProperty)) {
      builder.add(new PathOrGlobMatcher(root, buckdDirProperty));
    }

    Path cacheDir = getCacheDir(root, config.getValue("cache", "dir"), buckPaths);
    builder.add(new PathOrGlobMatcher(cacheDir));

    builder.addAll(
        FluentIterable.from(config.getListWithoutComments(projectKey, ignoreKey))
            .transform(
                new Function<String, PathOrGlobMatcher>() {
                  @Nullable
                  @Override
                  public PathOrGlobMatcher apply(String input) {
                    // We don't really want to ignore the output directory when doing things like filesystem
                    // walks, so return null
                    if (buckPaths.getBuckOut().toString().equals(input)) {
                      return null; //root.getFileSystem().getPathMatcher("glob:**");
                    }

                    if (GLOB_CHARS.matcher(input).find()) {
                      return new PathOrGlobMatcher(
                          root.getFileSystem().getPathMatcher("glob:" + input), input);
                    }
                    return new PathOrGlobMatcher(root, input);
                  }
                })
            // And now remove any null patterns
            .filter(Objects::nonNull)
            .toList());

    return builder.build();
  }

  public final Path getRootPath() {
    return projectRoot;
  }

  public ProjectFilesystem replaceBlacklistedPaths(
      ImmutableSet<PathOrGlobMatcher> blackListedPaths) {
    return new ProjectFilesystem(
        projectRoot.getFileSystem(), projectRoot, blackListedPaths, buckPaths, delegate);
  }

  /**
   * Hook for virtual filesystems to materialise virtual files as Buck will need to be able to read
   * them past this point.
   */
  public void ensureConcreteFilesExist(BuckEventBus eventBus) {
    delegate.ensureConcreteFilesExist(eventBus);
  }

  /**
   * @return the specified {@code path} resolved against {@link #getRootPath()} to an absolute path.
   */
  public Path resolve(Path path) {
    return MorePaths.normalize(getPathForRelativePath(path).toAbsolutePath());
  }

  public Path resolve(String path) {
    return MorePaths.normalize(getRootPath().resolve(path).toAbsolutePath());
  }

  /** Construct a relative path between the project root and a given path. */
  public Path relativize(Path path) {
    return projectRoot.relativize(path);
  }

  /** @return A {@link ImmutableSet} of {@link PathOrGlobMatcher} objects to have buck ignore. */
  public ImmutableSet<PathOrGlobMatcher> getIgnorePaths() {
    return blackListedDirectories;
  }

  public Path getPathForRelativePath(Path pathRelativeToProjectRoot) {
    return delegate.getPathForRelativePath(pathRelativeToProjectRoot);
  }

  public Path getPathForRelativePath(String pathRelativeToProjectRoot) {
    return projectRoot.resolve(pathRelativeToProjectRoot);
  }

  /**
   * @param path Absolute path or path relative to the project root.
   * @return If {@code path} is relative, it is returned. If it is absolute and is inside the
   *     project root, it is relativized to the project root and returned. Otherwise an absent value
   *     is returned.
   */
  public Optional<Path> getPathRelativeToProjectRoot(Path path) {
    path = MorePaths.normalize(path);
    if (path.isAbsolute()) {
      if (path.startsWith(projectRoot)) {
        return Optional.of(MorePaths.relativize(projectRoot, path));
      } else {
        return Optional.empty();
      }
    } else {
      return Optional.of(path);
    }
  }

  /**
   * As {@link #getPathForRelativePath(java.nio.file.Path)}, but with the added twist that the
   * existence of the path is checked before returning.
   */
  public Path getPathForRelativeExistingPath(Path pathRelativeToProjectRoot) {
    Path file = getPathForRelativePath(pathRelativeToProjectRoot);

    if (ignoreValidityOfPaths) {
      return file;
    }

    if (exists(file)) {
      return file;
    }

    // TODO(mbolin): Eliminate this temporary exemption for symbolic links.
    if (isSymLink(file)) {
      return file;
    }

    throw new RuntimeException(
        String.format("Not an ordinary file: '%s'.", pathRelativeToProjectRoot));
  }

  public boolean exists(Path pathRelativeToProjectRoot, LinkOption... options) {
    return delegate.exists(pathRelativeToProjectRoot, options);
  }

  public long getFileSize(Path pathRelativeToProjectRoot) throws IOException {
    Path path = getPathForRelativePath(pathRelativeToProjectRoot);
    if (!Files.isRegularFile(path)) {
      throw new IOException("Cannot get size of " + path + " because it is not an ordinary file.");
    }
    return Files.size(path);
  }

  /**
   * Deletes a file specified by its path relative to the project root.
   *
   * <p>Ignores the failure if the file does not exist.
   *
   * @param pathRelativeToProjectRoot path to the file
   * @return {@code true} if the file was deleted, {@code false} if it did not exist
   */
  public boolean deleteFileAtPathIfExists(Path pathRelativeToProjectRoot) throws IOException {
    return Files.deleteIfExists(getPathForRelativePath(pathRelativeToProjectRoot));
  }

  /**
   * Deletes a file specified by its path relative to the project root.
   *
   * @param pathRelativeToProjectRoot path to the file
   */
  public void deleteFileAtPath(Path pathRelativeToProjectRoot) throws IOException {
    Files.delete(getPathForRelativePath(pathRelativeToProjectRoot));
  }

  public Properties readPropertiesFile(Path propertiesFile) throws IOException {
    Properties properties = new Properties();
    if (exists(propertiesFile)) {
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(newFileInputStream(propertiesFile), Charsets.UTF_8))) {
        properties.load(reader);
      }
      return properties;
    } else {
      throw new FileNotFoundException(propertiesFile.toString());
    }
  }

  /** Checks whether there is a normal file at the specified path. */
  public boolean isFile(Path pathRelativeToProjectRoot, LinkOption... options) {
    return Files.isRegularFile(getPathForRelativePath(pathRelativeToProjectRoot), options);
  }

  public boolean isHidden(Path pathRelativeToProjectRoot) throws IOException {
    return Files.isHidden(getPathForRelativePath(pathRelativeToProjectRoot));
  }

  /**
   * Similar to {@link #walkFileTree(Path, FileVisitor)} except this takes in a path relative to the
   * project root.
   */
  public void walkRelativeFileTree(
      Path pathRelativeToProjectRoot, final FileVisitor<Path> fileVisitor) throws IOException {
    walkRelativeFileTree(
        pathRelativeToProjectRoot, EnumSet.of(FileVisitOption.FOLLOW_LINKS), fileVisitor);
  }

  /** Walks a project-root relative file tree with a visitor and visit options. */
  public void walkRelativeFileTree(
      Path pathRelativeToProjectRoot,
      EnumSet<FileVisitOption> visitOptions,
      final FileVisitor<Path> fileVisitor)
      throws IOException {

    FileVisitor<Path> relativizingVisitor =
        new FileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            return fileVisitor.preVisitDirectory(relativize(dir), attrs);
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            return fileVisitor.visitFile(relativize(file), attrs);
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return fileVisitor.visitFileFailed(relativize(file), exc);
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return fileVisitor.postVisitDirectory(relativize(dir), exc);
          }
        };
    Path rootPath = getPathForRelativePath(pathRelativeToProjectRoot);
    walkFileTree(rootPath, visitOptions, relativizingVisitor);
  }

  /** Allows {@link Files#walkFileTree} to be faked in tests. */
  public void walkFileTree(Path root, FileVisitor<Path> fileVisitor) throws IOException {
    root = getPathForRelativePath(root);
    walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), fileVisitor);
  }

  public void walkFileTree(Path root, Set<FileVisitOption> options, FileVisitor<Path> fileVisitor)
      throws IOException {
    new FileTreeWalker(root, options, fileVisitor).walk();
  }

  public ImmutableSet<Path> getFilesUnderPath(Path pathRelativeToProjectRoot) throws IOException {
    return getFilesUnderPath(pathRelativeToProjectRoot, x -> true);
  }

  public ImmutableSet<Path> getFilesUnderPath(
      Path pathRelativeToProjectRoot, Predicate<Path> predicate) throws IOException {
    return getFilesUnderPath(
        pathRelativeToProjectRoot, predicate, EnumSet.of(FileVisitOption.FOLLOW_LINKS));
  }

  public ImmutableSet<Path> getFilesUnderPath(
      Path pathRelativeToProjectRoot,
      final Predicate<Path> predicate,
      EnumSet<FileVisitOption> visitOptions)
      throws IOException {
    final ImmutableSet.Builder<Path> paths = ImmutableSet.builder();
    walkRelativeFileTree(
        pathRelativeToProjectRoot,
        visitOptions,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
            if (predicate.apply(path)) {
              paths.add(path);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return paths.build();
  }

  /** Allows {@link Files#isDirectory} to be faked in tests. */
  public boolean isDirectory(Path child, LinkOption... linkOptions) {
    return Files.isDirectory(resolve(child), linkOptions);
  }

  /** Allows {@link Files#isExecutable} to be faked in tests. */
  public boolean isExecutable(Path child) {
    return delegate.isExecutable(child);
  }

  /**
   * Allows {@link java.io.File#listFiles} to be faked in tests.
   *
   * <p>// @deprecated Replaced by {@link #getDirectoryContents}
   */
  public File[] listFiles(Path pathRelativeToProjectRoot) throws IOException {
    Collection<Path> paths = getDirectoryContents(pathRelativeToProjectRoot);

    File[] result = new File[paths.size()];
    return Collections2.transform(paths, Path::toFile).toArray(result);
  }

  public ImmutableCollection<Path> getDirectoryContents(Path pathToUse) throws IOException {
    Path path = getPathForRelativePath(pathToUse);
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
      return FluentIterable.from(stream)
          .filter(input -> !isIgnored(relativize(input)))
          .transform(absolutePath -> MorePaths.relativize(projectRoot, absolutePath))
          .toSortedList(Comparator.naturalOrder());
    }
  }

  @VisibleForTesting
  protected PathListing.PathModifiedTimeFetcher getLastModifiedTimeFetcher() {
    return path -> ProjectFilesystem.this.getLastModifiedTime(path);
  }

  /**
   * Returns the files inside {@code pathRelativeToProjectRoot} which match {@code globPattern},
   * ordered in descending last modified time order. This will not obey the results of {@link
   * #isIgnored(Path)}.
   */
  public ImmutableSortedSet<Path> getMtimeSortedMatchingDirectoryContents(
      Path pathRelativeToProjectRoot, String globPattern) throws IOException {
    Path path = getPathForRelativePath(pathRelativeToProjectRoot);
    return PathListing.listMatchingPaths(path, globPattern, getLastModifiedTimeFetcher());
  }

  public FileTime getLastModifiedTime(Path pathRelativeToProjectRoot) throws IOException {
    Path path = getPathForRelativePath(pathRelativeToProjectRoot);
    return Files.getLastModifiedTime(path);
  }

  /** Sets the last modified time for the given path. */
  public Path setLastModifiedTime(Path pathRelativeToProjectRoot, FileTime time)
      throws IOException {
    Path path = getPathForRelativePath(pathRelativeToProjectRoot);
    return Files.setLastModifiedTime(path, time);
  }

  /**
   * Recursively delete everything under the specified path. Ignore the failure if the file at the
   * specified path does not exist.
   */
  public void deleteRecursivelyIfExists(Path pathRelativeToProjectRoot) throws IOException {
    MoreFiles.deleteRecursivelyIfExists(resolve(pathRelativeToProjectRoot));
  }

  /**
   * Resolves the relative path against the project root and then calls {@link
   * Files#createDirectories(java.nio.file.Path, java.nio.file.attribute.FileAttribute[])}
   */
  public void mkdirs(Path pathRelativeToProjectRoot) throws IOException {
    Files.createDirectories(resolve(pathRelativeToProjectRoot));
  }

  /** Creates a new file relative to the project root. */
  public Path createNewFile(Path pathRelativeToProjectRoot) throws IOException {
    Path path = getPathForRelativePath(pathRelativeToProjectRoot);
    return Files.createFile(path);
  }

  /**
   * // @deprecated Prefer operating on {@code Path}s directly, replaced by {@link
   * #createParentDirs(java.nio.file.Path)}.
   */
  public void createParentDirs(String pathRelativeToProjectRoot) throws IOException {
    Path file = getPathForRelativePath(pathRelativeToProjectRoot);
    mkdirs(file.getParent());
  }

  /**
   * @param pathRelativeToProjectRoot Must identify a file, not a directory. (Unfortunately, we have
   *     no way to assert this because the path is not expected to exist yet.)
   */
  public void createParentDirs(Path pathRelativeToProjectRoot) throws IOException {
    Path file = resolve(pathRelativeToProjectRoot);
    Path directory = file.getParent();
    mkdirs(directory);
  }

  /**
   * Writes each line in {@code lines} with a trailing newline to a file at the specified path.
   *
   * <p>The parent path of {@code pathRelativeToProjectRoot} must exist.
   */
  public void writeLinesToPath(
      Iterable<String> lines, Path pathRelativeToProjectRoot, FileAttribute<?>... attrs)
      throws IOException {
    try (Writer writer =
        new BufferedWriter(
            new OutputStreamWriter(
                newFileOutputStream(pathRelativeToProjectRoot, attrs), Charsets.UTF_8))) {
      for (String line : lines) {
        writer.write(line);
        writer.write('\n');
      }
    }
  }

  public void writeContentsToPath(
      String contents, Path pathRelativeToProjectRoot, FileAttribute<?>... attrs)
      throws IOException {
    writeBytesToPath(contents.getBytes(Charsets.UTF_8), pathRelativeToProjectRoot, attrs);
  }

  public void writeBytesToPath(
      byte[] bytes, Path pathRelativeToProjectRoot, FileAttribute<?>... attrs) throws IOException {
    // No need to buffer writes when writing a single piece of data.
    try (OutputStream outputStream =
        newUnbufferedFileOutputStream(pathRelativeToProjectRoot, /* append */ false, attrs)) {
      outputStream.write(bytes);
    }
  }

  public OutputStream newFileOutputStream(Path pathRelativeToProjectRoot, FileAttribute<?>... attrs)
      throws IOException {
    return newFileOutputStream(pathRelativeToProjectRoot, /* append */ false, attrs);
  }

  public OutputStream newFileOutputStream(
      Path pathRelativeToProjectRoot, boolean append, FileAttribute<?>... attrs)
      throws IOException {
    return new BufferedOutputStream(
        newUnbufferedFileOutputStream(pathRelativeToProjectRoot, append, attrs));
  }

  public OutputStream newUnbufferedFileOutputStream(
      Path pathRelativeToProjectRoot, boolean append, FileAttribute<?>... attrs)
      throws IOException {
    return Channels.newOutputStream(
        Files.newByteChannel(
            getPathForRelativePath(pathRelativeToProjectRoot),
            append
                ? ImmutableSet.of(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                : ImmutableSet.of(
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE),
            attrs));
  }

  public <A extends BasicFileAttributes> A readAttributes(
      Path pathRelativeToProjectRoot, Class<A> type, LinkOption... options) throws IOException {
    return Files.readAttributes(getPathForRelativePath(pathRelativeToProjectRoot), type, options);
  }

  public InputStream newFileInputStream(Path pathRelativeToProjectRoot) throws IOException {
    return new BufferedInputStream(
        Files.newInputStream(getPathForRelativePath(pathRelativeToProjectRoot)));
  }

  /** @param inputStream Source of the bytes. This method does not close this stream. */
  public void copyToPath(
      InputStream inputStream, Path pathRelativeToProjectRoot, CopyOption... options)
      throws IOException {
    Files.copy(inputStream, getPathForRelativePath(pathRelativeToProjectRoot), options);
  }

  /** Copies a file to an output stream. */
  public void copyToOutputStream(Path pathRelativeToProjectRoot, OutputStream out)
      throws IOException {
    Files.copy(getPathForRelativePath(pathRelativeToProjectRoot), out);
  }

  public Optional<String> readFileIfItExists(Path pathRelativeToProjectRoot) {
    Path fileToRead = getPathForRelativePath(pathRelativeToProjectRoot);
    return readFileIfItExists(fileToRead, pathRelativeToProjectRoot.toString());
  }

  private Optional<String> readFileIfItExists(Path fileToRead, String pathRelativeToProjectRoot) {
    if (Files.isRegularFile(fileToRead)) {
      String contents;
      try {
        contents = new String(Files.readAllBytes(fileToRead), Charsets.UTF_8);
      } catch (IOException e) {
        // Alternatively, we could return Optional.empty(), though something seems suspicious if we
        // have already verified that fileToRead is a file and then we cannot read it.
        throw new RuntimeException("Error reading " + pathRelativeToProjectRoot, e);
      }
      return Optional.of(contents);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Attempts to read the first line of the file specified by the relative path. If the file does
   * not exist, is empty, or encounters an error while being read, {@link Optional#empty()} is
   * returned. Otherwise, an {@link Optional} with the first line of the file will be returned.
   *
   * <p>// @deprecated PRefero operation on {@code Path}s directly, replaced by {@link
   * #readFirstLine(java.nio.file.Path)}
   */
  public Optional<String> readFirstLine(String pathRelativeToProjectRoot) {
    return readFirstLine(projectRoot.getFileSystem().getPath(pathRelativeToProjectRoot));
  }

  /**
   * Attempts to read the first line of the file specified by the relative path. If the file does
   * not exist, is empty, or encounters an error while being read, {@link Optional#empty()} is
   * returned. Otherwise, an {@link Optional} with the first line of the file will be returned.
   */
  public Optional<String> readFirstLine(Path pathRelativeToProjectRoot) {
    Path file = getPathForRelativePath(pathRelativeToProjectRoot);
    return readFirstLineFromFile(file);
  }

  /**
   * Attempts to read the first line of the specified file. If the file does not exist, is empty, or
   * encounters an error while being read, {@link Optional#empty()} is returned. Otherwise, an
   * {@link Optional} with the first line of the file will be returned.
   */
  public Optional<String> readFirstLineFromFile(Path file) {
    try {
      try (BufferedReader reader = Files.newBufferedReader(file, Charsets.UTF_8)) {
        return Optional.ofNullable(reader.readLine());
      }
    } catch (IOException e) {
      // Because the file is not even guaranteed to exist, swallow the IOException.
      return Optional.empty();
    }
  }

  public List<String> readLines(Path pathRelativeToProjectRoot) throws IOException {
    Path file = getPathForRelativePath(pathRelativeToProjectRoot);
    return Files.readAllLines(file, Charsets.UTF_8);
  }

  /**
   * // @deprecated Prefer operation on {@code Path}s directly, replaced by {@link
   * Files#newInputStream(java.nio.file.Path, java.nio.file.OpenOption...)}.
   */
  public InputStream getInputStreamForRelativePath(Path path) throws IOException {
    Path file = getPathForRelativePath(path);
    return Files.newInputStream(file);
  }

  public Sha1HashCode computeSha1(Path pathRelativeToProjectRootOrJustAbsolute) throws IOException {
    return delegate.computeSha1(pathRelativeToProjectRootOrJustAbsolute);
  }

  public String computeSha256(Path pathRelativeToProjectRoot) throws IOException {
    Path fileToHash = getPathForRelativePath(pathRelativeToProjectRoot);
    return Hashing.sha256().hashBytes(Files.readAllBytes(fileToHash)).toString();
  }

  public void copy(Path source, Path target, CopySourceMode sourceMode) throws IOException {
    source = getPathForRelativePath(source);
    switch (sourceMode) {
      case FILE:
        Files.copy(resolve(source), resolve(target), StandardCopyOption.REPLACE_EXISTING);
        break;
      case DIRECTORY_CONTENTS_ONLY:
        MoreFiles.copyRecursively(resolve(source), resolve(target));
        break;
      case DIRECTORY_AND_CONTENTS:
        MoreFiles.copyRecursively(resolve(source), resolve(target.resolve(source.getFileName())));
        break;
    }
  }

  public void move(Path source, Path target, CopyOption... options) throws IOException {
    Files.move(resolve(source), resolve(target), options);
  }

  public void copyFolder(Path source, Path target) throws IOException {
    copy(source, target, CopySourceMode.DIRECTORY_CONTENTS_ONLY);
  }

  public void copyFile(Path source, Path target) throws IOException {
    copy(source, target, CopySourceMode.FILE);
  }

  public void createSymLink(Path symLink, Path realFile, boolean force) throws IOException {
    symLink = resolve(symLink);
    if (force) {
      Files.deleteIfExists(symLink);
    }
    if (Platform.detect() == Platform.WINDOWS) {
      if (isDirectory(realFile)) {
        // Creating symlinks to directories on Windows requires escalated privileges. We're just
        // going to have to copy things recursively.
        MoreFiles.copyRecursively(realFile, symLink);
      } else {
        // When sourcePath is relative, resolve it from the targetPath. We're creating a hard link
        // anyway.
        realFile = MorePaths.normalize(symLink.getParent().resolve(realFile));
        Files.createLink(symLink, realFile);
      }
    } else {
      Files.createSymbolicLink(symLink, realFile);
    }
  }

  /**
   * Returns the set of POSIX file permissions, or the empty set if the underlying file system does
   * not support POSIX file attributes.
   */
  public Set<PosixFilePermission> getPosixFilePermissions(Path path) throws IOException {
    Path resolvedPath = getPathForRelativePath(path);
    if (Files.getFileAttributeView(resolvedPath, PosixFileAttributeView.class) != null) {
      return Files.getPosixFilePermissions(resolvedPath);
    } else {
      return ImmutableSet.of();
    }
  }

  /** Returns true if the file under {@code path} exists and is a symbolic link, false otherwise. */
  public boolean isSymLink(Path path) {
    return delegate.isSymlink(path);
  }

  /** Returns the target of the specified symbolic link. */
  public Path readSymLink(Path path) throws IOException {
    return Files.readSymbolicLink(getPathForRelativePath(path));
  }

  /**
   * Takes a sequence of paths relative to the project root and writes a zip file to {@code out}
   * with the contents and structure that matches that of the specified paths.
   */
  public void createZip(Collection<Path> pathsToIncludeInZip, Path out) throws IOException {
    try (CustomZipOutputStream zip = ZipOutputStreams.newOutputStream(out)) {
      for (Path path : pathsToIncludeInZip) {
        boolean isDirectory = isDirectory(path);
        CustomZipEntry entry = new CustomZipEntry(path, isDirectory);

        // We want deterministic ZIPs, so avoid mtimes.
        entry.setFakeTime();

        entry.setExternalAttributes(getFileAttributesForZipEntry(path));

        zip.putNextEntry(entry);
        if (!isDirectory) {
          try (InputStream input = newFileInputStream(path)) {
            ByteStreams.copy(input, zip);
          }
        }
        zip.closeEntry();
      }
    }
  }

  public Manifest getJarManifest(Path path) throws IOException {
    Path absolutePath = getPathForRelativePath(path);
    try (JarFile jarFile = new JarFile(absolutePath.toFile())) {
      return jarFile.getManifest();
    }
  }

  public long getFileAttributesForZipEntry(Path path) throws IOException {
    long mode = 0;
    // Support executable files.  If we detect this file is executable, store this
    // information as 0100 in the field typically used in zip implementations for
    // POSIX file permissions.  We'll use this information when unzipping.
    if (isExecutable(path)) {
      mode |= MorePosixFilePermissions.toMode(EnumSet.of(PosixFilePermission.OWNER_EXECUTE));
    }

    if (isDirectory(path)) {
      mode |= MoreFiles.S_IFDIR;
    } else if (isFile(path)) {
      mode |= MoreFiles.S_IFREG;
    }

    // Propagate any additional permissions
    mode |= MorePosixFilePermissions.toMode(getPosixFilePermissions(path));

    return mode << 16;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof ProjectFilesystem)) {
      return false;
    }

    ProjectFilesystem that = (ProjectFilesystem) other;

    if (!Objects.equals(projectRoot, that.projectRoot)) {
      return false;
    }

    if (!Objects.equals(blackListedPaths, that.blackListedPaths)) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return String.format(
        "%s (projectRoot=%s, hash(blackListedPaths)=%s)",
        super.toString(), projectRoot, blackListedPaths.hashCode());
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectRoot, blackListedPaths);
  }

  public BuckPaths getBuckPaths() {
    return buckPaths;
  }

  /**
   * @param path the path to check.
   * @return whether ignoredPaths contains path or any of its ancestors.
   */
  public boolean isIgnored(Path path) {
    Preconditions.checkArgument(!path.isAbsolute());
    for (PathOrGlobMatcher blackListedPath : blackListedPaths) {
      if (blackListedPath.matches(path)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a relative path whose parent directory is guaranteed to exist. The path will be under
   * {@code buck-out}, so it is safe to write to.
   */
  public Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs)
      throws IOException {
    return createTempFile(tmpDir.get(), prefix, suffix, attrs);
  }

  /**
   * Prefer {@link #createTempFile(String, String, FileAttribute[])} so that temporary files are
   * guaranteed to be created under {@code buck-out}. This method will be deprecated once t12079608
   * is resolved.
   */
  public Path createTempFile(
      Path directory, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
    Path tmp = Files.createTempFile(resolve(directory), prefix, suffix, attrs);
    return getPathRelativeToProjectRoot(tmp).orElse(tmp);
  }

  public void touch(Path fileToTouch) throws IOException {
    if (exists(fileToTouch)) {
      setLastModifiedTime(fileToTouch, FileTime.fromMillis(System.currentTimeMillis()));
    } else {
      createNewFile(fileToTouch);
    }
  }

  /**
   * Converts a path string (or sequence of strings) to a Path with the same VFS as this instance.
   *
   * @see FileSystem#getPath(String, String...)
   */
  public Path getPath(String first, String... rest) {
    return getRootPath().getFileSystem().getPath(first, rest);
  }

  /**
   * FileTreeWalker is used to walk files similar to Files.walkFileTree.
   *
   * <p>It has two major differences from walkFileTree. 1. It ignores files and directories ignored
   * by this ProjectFilesystem. 2. The walk is in a deterministic order.
   *
   * <p>And it has two minor differences. 1. It doesn't accept a depth limit. 2. It doesn't handle
   * the presence of a security manager the same way.
   */
  private class FileTreeWalker {
    private final FileVisitor<Path> visitor;
    private final Path root;
    private final boolean followLinks;
    private ArrayDeque<DirWalkState> state;

    FileTreeWalker(Path root, Set<FileVisitOption> options, FileVisitor<Path> pathFileVisitor) {
      this.followLinks = options.contains(FileVisitOption.FOLLOW_LINKS);
      this.visitor = pathFileVisitor;
      this.root = root;
      this.state = new ArrayDeque<>();
    }

    private ImmutableList<Path> getContents(Path root) throws IOException {
      try (DirectoryStream<Path> stream =
          Files.newDirectoryStream(root, input -> !isIgnored(relativize(input)))) {
        return FluentIterable.from(stream).toSortedList(Comparator.naturalOrder());
      }
    }

    private class DirWalkState {
      final Path dir;
      final BasicFileAttributes attrs;
      final boolean isRootSentinel;
      UnmodifiableIterator<Path> iter;
      @Nullable IOException ioe = null;

      DirWalkState(Path directory, BasicFileAttributes attributes, boolean isRootSentinel) {
        this.dir = directory;
        this.attrs = attributes;
        if (isRootSentinel) {
          this.iter = ImmutableList.of(root).iterator();
        } else {
          try {
            this.iter = getContents(directory).iterator();
          } catch (IOException e) {
            this.iter = ImmutableList.<Path>of().iterator();
            this.ioe = e;
          }
        }
        this.isRootSentinel = isRootSentinel;
      }
    }

    private void walk() throws IOException {
      state.add(new DirWalkState(root, getAttributes(root), true));

      while (true) {
        FileVisitResult result;
        if (state.getLast().iter.hasNext()) {
          result = visitPath(state.getLast().iter.next());
        } else {
          DirWalkState dirState = state.removeLast();
          if (dirState.isRootSentinel) {
            return;
          }
          result = visitor.postVisitDirectory(dirState.dir, dirState.ioe);
        }
        Objects.requireNonNull(result, "FileVisitor returned a null FileVisitResult.");
        if (result == FileVisitResult.SKIP_SIBLINGS) {
          state.getLast().iter = ImmutableList.<Path>of().iterator();
        } else if (result == FileVisitResult.TERMINATE) {
          return;
        }
      }
    }

    private FileVisitResult visitPath(Path p) throws IOException {
      BasicFileAttributes attrs;
      try {
        attrs = getAttributes(p);
        ensureNoLoops(p, attrs);
      } catch (IOException ioe) {
        return visitor.visitFileFailed(p, ioe);
      }

      if (attrs.isDirectory()) {
        FileVisitResult result = visitor.preVisitDirectory(p, attrs);
        if (result == FileVisitResult.CONTINUE) {
          state.add(new DirWalkState(p, attrs, false));
        }
        return result;
      } else {
        return visitor.visitFile(p, attrs);
      }
    }

    private void ensureNoLoops(Path p, BasicFileAttributes attrs) throws FileSystemLoopException {
      if (!followLinks) {
        return;
      }
      if (!attrs.isDirectory()) {
        return;
      }
      if (willLoop(p, attrs)) {
        throw new FileSystemLoopException(p.toString());
      }
    }

    private boolean willLoop(Path p, BasicFileAttributes attrs) {
      try {
        Object thisKey = attrs.fileKey();
        for (DirWalkState s : state) {
          if (s.isRootSentinel) {
            continue;
          }
          Object thatKey = s.attrs.fileKey();
          if (thisKey != null && thatKey != null) {
            if (thisKey.equals(thatKey)) {
              return true;
            }
          } else if (Files.isSameFile(p, s.dir)) {
            return true;
          }
        }
      } catch (IOException e) {
        return true;
      }
      return false;
    }

    private BasicFileAttributes getAttributes(Path root) throws IOException {
      if (!followLinks) {
        return Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      }
      try {
        return Files.readAttributes(root, BasicFileAttributes.class);
      } catch (IOException e) {
        return Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      }
    }
  }
}
