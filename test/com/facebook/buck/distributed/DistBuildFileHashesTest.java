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

package com.facebook.buck.distributed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.distributed.thrift.BuildJobStateFileHashEntry;
import com.facebook.buck.distributed.thrift.BuildJobStateFileHashes;
import com.facebook.buck.io.ArchiveMemberPath;
import com.facebook.buck.io.MoreFiles;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.ActionGraph;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.ArchiveMemberSourcePath;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeBuildRuleParamsBuilder;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.PathSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.TestCellBuilder;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.slb.ThriftUtil;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.cache.DefaultFileHashCache;
import com.facebook.buck.util.cache.ProjectFileHashCache;
import com.facebook.buck.util.cache.StackedFileHashCache;
import com.facebook.buck.zip.CustomJarOutputStream;
import com.facebook.buck.zip.ZipOutputStreams;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.easymock.EasyMock;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DistBuildFileHashesTest {
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  @Rule public TemporaryFolder archiveTempDir = new TemporaryFolder();

  private static class SingleFileFixture extends Fixture {
    protected Path javaSrcPath;
    protected HashCode writtenHashCode;
    protected String writtenContents;

    public SingleFileFixture(TemporaryFolder tempDir)
        throws InterruptedException, IOException, NoSuchBuildTargetException {
      super(tempDir);
    }

    @Override
    protected void setUpRules(BuildRuleResolver resolver, SourcePathResolver sourcePathResolver)
        throws IOException, NoSuchBuildTargetException {
      javaSrcPath = getPath("src", "A.java");

      projectFilesystem.createParentDirs(javaSrcPath);
      writtenContents = "public class A {}";
      projectFilesystem.writeContentsToPath(writtenContents, javaSrcPath);
      writtenHashCode = Hashing.sha1().hashString(writtenContents, Charsets.UTF_8);

      JavaLibraryBuilder.createBuilder(
              BuildTargetFactory.newInstance(projectFilesystem.getRootPath(), "//:java_lib"),
              projectFilesystem)
          .addSrc(javaSrcPath)
          .build(resolver, projectFilesystem);
    }
  }

  @Test
  public void recordsFileHashes() throws Exception {
    SingleFileFixture f = new SingleFileFixture(tempDir);

    List<BuildJobStateFileHashes> recordedHashes = f.distributedBuildFileHashes.getFileHashes();

    assertThat(toDebugStringForAssert(recordedHashes), recordedHashes, Matchers.hasSize(1));
    BuildJobStateFileHashes rootCellHashes = getRootCellHashes(recordedHashes);
    assertThat(rootCellHashes.entries, Matchers.hasSize(1));
    BuildJobStateFileHashEntry fileHashEntry = rootCellHashes.entries.get(0);
    // It's intentional that we hardcode the path as a string here as we expect the thrift data
    // to contain unix-formated paths.
    assertThat(fileHashEntry.getPath().getPath(), Matchers.equalTo("src/A.java"));
    assertFalse(fileHashEntry.isPathIsAbsolute());
    assertFalse(fileHashEntry.isIsDirectory());
  }

  @Test
  public void cacheReadsHashesForFiles() throws Exception {
    SingleFileFixture f = new SingleFileFixture(tempDir);

    List<BuildJobStateFileHashes> fileHashes = f.distributedBuildFileHashes.getFileHashes();

    ProjectFilesystem readProjectFilesystem =
        new ProjectFilesystem(tempDir.newFolder("read_hashes").toPath().toRealPath());
    ProjectFileHashCache mockCache = EasyMock.createMock(ProjectFileHashCache.class);
    EasyMock.expect(mockCache.getFilesystem()).andReturn(readProjectFilesystem).anyTimes();
    EasyMock.replay(mockCache);
    ProjectFileHashCache fileHashCache =
        DistBuildFileHashes.createFileHashCache(mockCache, fileHashes.get(0));

    assertThat(
        fileHashCache.willGet(readProjectFilesystem.resolve(f.javaSrcPath)),
        Matchers.equalTo(true));

    assertThat(
        fileHashCache.get(readProjectFilesystem.resolve(f.javaSrcPath)),
        Matchers.equalTo(f.writtenHashCode));
  }

  @Test
  public void materializerWritesContents() throws Exception {
    SingleFileFixture f = new SingleFileFixture(tempDir);

    List<BuildJobStateFileHashes> fileHashes = f.distributedBuildFileHashes.getFileHashes();

    ProjectFilesystem materializeProjectFilesystem =
        new ProjectFilesystem(tempDir.newFolder("read_hashes").getCanonicalFile().toPath());

    ProjectFileHashCache mockCache = EasyMock.createMock(ProjectFileHashCache.class);
    EasyMock.expect(mockCache.getFilesystem())
        .andReturn(materializeProjectFilesystem)
        .atLeastOnce();
    EasyMock.expect(mockCache.get(EasyMock.<Path>notNull())).andReturn(HashCode.fromInt(42)).once();
    EasyMock.replay(mockCache);
    MaterializerProjectFileHashCache materializer =
        new MaterializerProjectFileHashCache(
            mockCache, fileHashes.get(0), new InlineContentsProvider());

    materializer.get(materializeProjectFilesystem.resolve(f.javaSrcPath));
    assertThat(
        materializeProjectFilesystem.readFileIfItExists(f.javaSrcPath),
        Matchers.equalTo(Optional.of(f.writtenContents)));
  }

  @Test
  public void cacheMaterializes() throws Exception {
    SingleFileFixture f = new SingleFileFixture(tempDir);

    List<BuildJobStateFileHashes> fileHashes = f.distributedBuildFileHashes.getFileHashes();

    ProjectFilesystem readProjectFilesystem =
        new ProjectFilesystem(tempDir.newFolder("read_hashes").toPath().toRealPath());
    ProjectFileHashCache mockCache = EasyMock.createMock(ProjectFileHashCache.class);
    EasyMock.expect(mockCache.getFilesystem()).andReturn(readProjectFilesystem).anyTimes();
    EasyMock.replay(mockCache);
    ProjectFileHashCache fileHashCache =
        DistBuildFileHashes.createFileHashCache(mockCache, fileHashes.get(0));

    assertThat(
        fileHashCache.willGet(readProjectFilesystem.resolve("src/A.java")), Matchers.equalTo(true));

    assertThat(
        fileHashCache.get(readProjectFilesystem.resolve("src/A.java")),
        Matchers.equalTo(f.writtenHashCode));
  }

  private static class ArchiveFilesFixture extends Fixture implements AutoCloseable {
    private final Path firstFolder;
    private final Path secondFolder;
    protected Path archivePath;
    protected Path archiveMemberPath;
    protected HashCode archiveMemberHash;

    private ArchiveFilesFixture(Path firstFolder, Path secondFolder)
        throws InterruptedException, IOException, NoSuchBuildTargetException {
      super(new ProjectFilesystem(firstFolder), new ProjectFilesystem(secondFolder));
      this.firstFolder = firstFolder;
      this.secondFolder = secondFolder;
    }

    public static ArchiveFilesFixture create(TemporaryFolder archiveTempDir)
        throws InterruptedException, IOException, NoSuchBuildTargetException {
      return new ArchiveFilesFixture(
          archiveTempDir.newFolder("first").toPath().toRealPath(),
          archiveTempDir.newFolder("second").toPath().toRealPath());
    }

    @Override
    protected void setUpRules(BuildRuleResolver resolver, SourcePathResolver sourcePathResolver)
        throws IOException, NoSuchBuildTargetException {
      archivePath = getPath("src", "archive.jar");
      archiveMemberPath = getPath("Archive.class");

      projectFilesystem.createParentDirs(archivePath);
      try (CustomJarOutputStream jarWriter =
          ZipOutputStreams.newJarOutputStream(projectFilesystem.newFileOutputStream(archivePath))) {
        jarWriter.setEntryHashingEnabled(true);
        byte[] archiveMemberData = "data".getBytes(Charsets.UTF_8);
        archiveMemberHash = Hashing.murmur3_128().hashBytes(archiveMemberData);
        jarWriter.writeEntry("Archive.class", new ByteArrayInputStream(archiveMemberData));
      }

      resolver.addToIndex(
          new BuildRuleWithToolAndPath(
              new FakeBuildRuleParamsBuilder("//:with_tool")
                  .setProjectFilesystem(projectFilesystem)
                  .build(),
              null,
              ArchiveMemberSourcePath.of(
                  new PathSourcePath(projectFilesystem, archivePath), archiveMemberPath)));
    }

    @Override
    public void close() throws IOException {
      MoreFiles.deleteRecursively(firstFolder);
      MoreFiles.deleteRecursively(secondFolder);
    }
  }

  @Test
  public void recordsArchiveHashes() throws Exception {
    try (ArchiveFilesFixture f = ArchiveFilesFixture.create(archiveTempDir)) {
      List<BuildJobStateFileHashes> recordedHashes = f.distributedBuildFileHashes.getFileHashes();

      assertThat(toDebugStringForAssert(recordedHashes), recordedHashes, Matchers.hasSize(1));
      BuildJobStateFileHashes hashes = getRootCellHashes(recordedHashes);
      assertThat(hashes.entries, Matchers.hasSize(1));
      BuildJobStateFileHashEntry fileHashEntry = hashes.entries.get(0);
      assertThat(fileHashEntry.getPath().getPath(), Matchers.equalTo("src/archive.jar"));
      assertTrue(fileHashEntry.isSetArchiveMemberPath());
      assertThat(fileHashEntry.getArchiveMemberPath(), Matchers.equalTo("Archive.class"));
      assertFalse(fileHashEntry.isPathIsAbsolute());
      assertFalse(fileHashEntry.isIsDirectory());
    }
  }

  @Test
  public void readsHashesForArchiveMembers() throws Exception {
    try (ArchiveFilesFixture f = ArchiveFilesFixture.create(archiveTempDir)) {
      List<BuildJobStateFileHashes> recordedHashes = f.distributedBuildFileHashes.getFileHashes();

      ProjectFilesystem readProjectFilesystem =
          new ProjectFilesystem(tempDir.newFolder("read_hashes").toPath().toRealPath());
      ProjectFileHashCache mockCache = EasyMock.createMock(ProjectFileHashCache.class);
      EasyMock.expect(mockCache.getFilesystem()).andReturn(readProjectFilesystem).anyTimes();
      EasyMock.replay(mockCache);
      ProjectFileHashCache fileHashCache =
          DistBuildFileHashes.createFileHashCache(mockCache, recordedHashes.get(0));

      ArchiveMemberPath archiveMemberPath =
          ArchiveMemberPath.of(readProjectFilesystem.resolve(f.archivePath), f.archiveMemberPath);
      assertThat(fileHashCache.willGet(archiveMemberPath), Matchers.is(true));

      assertThat(fileHashCache.get(archiveMemberPath), Matchers.is(f.archiveMemberHash));
    }
  }

  @Test
  public void worksCrossCell() throws Exception {
    final Fixture f =
        new Fixture(tempDir) {

          @Override
          protected void setUpRules(
              BuildRuleResolver resolver, SourcePathResolver sourcePathResolver)
              throws IOException, NoSuchBuildTargetException {
            Path firstPath = javaFs.getPath("src", "A.java");

            projectFilesystem.createParentDirs(firstPath);
            projectFilesystem.writeContentsToPath("public class A {}", firstPath);

            Path secondPath = secondJavaFs.getPath("B.java");
            secondProjectFilesystem.writeContentsToPath("public class B {}", secondPath);

            JavaLibraryBuilder.createBuilder(
                    BuildTargetFactory.newInstance(
                        projectFilesystem.getRootPath(), "//:java_lib_at_root"),
                    projectFilesystem)
                .addSrc(firstPath)
                .build(resolver, projectFilesystem);

            JavaLibraryBuilder.createBuilder(
                    BuildTargetFactory.newInstance(
                        secondProjectFilesystem.getRootPath(), "//:java_lib_at_secondary"),
                    secondProjectFilesystem)
                .addSrc(secondPath)
                .build(resolver, secondProjectFilesystem);
          }

          @Override
          protected BuckConfig createBuckConfig() {
            return FakeBuckConfig.builder()
                .setSections(
                    "[repositories]",
                    "second_repo = " + secondProjectFilesystem.getRootPath().toAbsolutePath())
                .build();
          }
        };

    List<BuildJobStateFileHashes> recordedHashes = f.distributedBuildFileHashes.getFileHashes();
    assertThat(toDebugStringForAssert(recordedHashes), recordedHashes, Matchers.hasSize(2));

    BuildJobStateFileHashes rootCellHash = getRootCellHashes(recordedHashes);
    Assert.assertEquals(1, rootCellHash.getEntriesSize());
    Assert.assertEquals("src/A.java", rootCellHash.getEntries().get(0).getPath().getPath());

    BuildJobStateFileHashes secondaryCellHashes = getCellHashesByIndex(recordedHashes, 1);
    Assert.assertEquals(1, secondaryCellHashes.getEntriesSize());
    Assert.assertEquals("B.java", secondaryCellHashes.getEntries().get(0).getPath().getPath());
  }

  private abstract static class Fixture {

    protected final ProjectFilesystem projectFilesystem;
    protected final FileSystem javaFs;

    protected final ProjectFilesystem secondProjectFilesystem;
    protected final FileSystem secondJavaFs;

    protected final ActionGraph actionGraph;
    protected final BuildRuleResolver buildRuleResolver;
    protected final SourcePathRuleFinder ruleFinder;
    protected final SourcePathResolver sourcePathResolver;
    protected final DistBuildFileHashes distributedBuildFileHashes;

    public Fixture(ProjectFilesystem first, ProjectFilesystem second)
        throws InterruptedException, IOException, NoSuchBuildTargetException {
      projectFilesystem = first;
      javaFs = projectFilesystem.getRootPath().getFileSystem();

      secondProjectFilesystem = second;
      secondJavaFs = secondProjectFilesystem.getRootPath().getFileSystem();

      buildRuleResolver =
          new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
      ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
      sourcePathResolver = new SourcePathResolver(ruleFinder);
      setUpRules(buildRuleResolver, sourcePathResolver);
      actionGraph = new ActionGraph(buildRuleResolver.getBuildRules());
      BuckConfig buckConfig = createBuckConfig();
      Cell rootCell =
          new TestCellBuilder().setFilesystem(projectFilesystem).setBuckConfig(buckConfig).build();

      distributedBuildFileHashes =
          new DistBuildFileHashes(
              actionGraph,
              sourcePathResolver,
              ruleFinder,
              createFileHashCache(),
              new DistBuildCellIndexer(rootCell),
              MoreExecutors.newDirectExecutorService(),
              /* keySeed */ 0,
              rootCell);
    }

    public Fixture(TemporaryFolder tempDir)
        throws InterruptedException, IOException, NoSuchBuildTargetException {
      this(
          new ProjectFilesystem(tempDir.newFolder("first").toPath().toRealPath()),
          new ProjectFilesystem(tempDir.newFolder("second").toPath().toRealPath()));
    }

    protected BuckConfig createBuckConfig() {
      return FakeBuckConfig.builder().build();
    }

    protected abstract void setUpRules(
        BuildRuleResolver resolver, SourcePathResolver sourcePathResolver)
        throws IOException, NoSuchBuildTargetException;

    private StackedFileHashCache createFileHashCache() throws InterruptedException {
      ImmutableList.Builder<ProjectFileHashCache> cacheList = ImmutableList.builder();
      cacheList.add(DefaultFileHashCache.createDefaultFileHashCache(projectFilesystem));
      cacheList.add(DefaultFileHashCache.createDefaultFileHashCache(secondProjectFilesystem));
      for (Path path : javaFs.getRootDirectories()) {
        if (Files.isDirectory(path)) {
          cacheList.add(
              DefaultFileHashCache.createDefaultFileHashCache(new ProjectFilesystem(path)));
        }
      }
      return new StackedFileHashCache(cacheList.build());
    }

    public Path getPath(String first, String... more) {
      return javaFs.getPath(first, more);
    }
  }

  private static class BuildRuleWithToolAndPath extends NoopBuildRule {

    @AddToRuleKey Tool tool;

    @AddToRuleKey SourcePath sourcePath;

    public BuildRuleWithToolAndPath(BuildRuleParams params, Tool tool, SourcePath sourcePath) {
      super(params);
      this.tool = tool;
      this.sourcePath = sourcePath;
    }
  }

  private static BuildJobStateFileHashes getCellHashesByIndex(
      List<BuildJobStateFileHashes> recordedHashes, int index) {
    Preconditions.checkArgument(index >= 0);
    Preconditions.checkArgument(index < recordedHashes.size());
    return recordedHashes
        .stream()
        .filter(hashes -> hashes.getCellIndex() == index)
        .findFirst()
        .get();
  }

  private static BuildJobStateFileHashes getRootCellHashes(
      List<BuildJobStateFileHashes> recordedHashes) {
    return getCellHashesByIndex(recordedHashes, DistBuildCellIndexer.ROOT_CELL_INDEX);
  }

  private static String toDebugStringForAssert(List<BuildJobStateFileHashes> recordedHashes) {
    return Joiner.on("\n")
        .join(
            recordedHashes
                .stream()
                .map(ThriftUtil::thriftToDebugJson)
                .collect(MoreCollectors.toImmutableList()));
  }
}
