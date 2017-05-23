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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.dalvik.ZipSplitter;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.FakeSourcePath;
import com.google.common.base.Predicate;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.easymock.EasyMock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SplitZipStepTest {
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  @Test
  public void testMetaList() throws IOException {
    Path outJar = tempDir.newFile("test.jar").toPath();
    ZipOutputStream zipOut =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outJar)));
    Map<String, String> fileToClassName =
        ImmutableMap.of(
            "com/facebook/foo.class", "com.facebook.foo",
            "bar.class", "bar");
    try {
      for (String entry : fileToClassName.keySet()) {
        zipOut.putNextEntry(new ZipEntry(entry));
        zipOut.write(new byte[] {0});
      }
    } finally {
      zipOut.close();
    }

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);
    ImmutableSet<APKModule> requires = ImmutableSet.of();
    try {
      SplitZipStep.writeMetaList(
          writer, SplitZipStep.SECONDARY_DEX_ID, requires, ImmutableList.of(outJar), DexStore.JAR);
    } finally {
      writer.close();
    }
    List<String> lines = CharStreams.readLines(new StringReader(stringWriter.toString()));
    assertEquals(1, lines.size());

    String line = Iterables.getLast(lines, null);
    String[] data = line.split(" ");
    assertEquals(3, data.length);

    // Note that we cannot test data[1] (the hash) because zip files change their hash each
    // time they are written due to timestamps written into the file.
    assertEquals("secondary-1.dex.jar", data[0]);
    assertTrue(
        String.format("Unexpected class: %s", data[2]), fileToClassName.values().contains(data[2]));
  }

  @Test
  public void testMetaListApkModuule() throws IOException {
    Path outJar = tempDir.newFile("test.jar").toPath();
    ZipOutputStream zipOut =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outJar)));
    Map<String, String> fileToClassName =
        ImmutableMap.of(
            "com/facebook/foo.class", "com.facebook.foo",
            "bar.class", "bar");
    try {
      for (String entry : fileToClassName.keySet()) {
        zipOut.putNextEntry(new ZipEntry(entry));
        zipOut.write(new byte[] {0});
      }
    } finally {
      zipOut.close();
    }

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);
    ImmutableSet<APKModule> requires =
        ImmutableSet.of(APKModule.builder().setName("dependency").build());
    try {
      SplitZipStep.writeMetaList(
          writer, "module", requires, ImmutableList.of(outJar), DexStore.JAR);
    } finally {
      writer.close();
    }
    List<String> lines = CharStreams.readLines(new StringReader(stringWriter.toString()));
    assertEquals(3, lines.size());

    assertEquals(lines.get(0), ".id module");
    assertEquals(lines.get(1), ".requires dependency");

    String line = Iterables.getLast(lines, null);
    String[] data = line.split(" ");
    assertEquals(3, data.length);

    // Note that we cannot test data[1] (the hash) because zip files change their hash each
    // time they are written due to timestamps written into the file.
    assertEquals("module-1.dex.jar", data[0]);
    assertTrue(
        String.format("Unexpected class: %s", data[2]), fileToClassName.values().contains(data[2]));
  }

  @Test
  public void testRequiredInPrimaryZipPredicate() throws IOException {
    Path primaryDexClassesFile = Paths.get("the/manifest.txt");
    List<String> linesInManifestFile =
        ImmutableList.of(
            "com/google/common/collect/ImmutableSortedSet",
            "  com/google/common/collect/ImmutableSet",
            "# com/google/common/collect/ImmutableMap");
    ProjectFilesystem projectFilesystem = EasyMock.createMock(ProjectFilesystem.class);
    EasyMock.expect(projectFilesystem.readLines(primaryDexClassesFile))
        .andReturn(linesInManifestFile);
    EasyMock.replay(projectFilesystem);

    SplitZipStep splitZipStep =
        new SplitZipStep(
            projectFilesystem,
            /* inputPathsToSplit */ ImmutableSet.of(),
            /* secondaryJarMetaPath */ Paths.get(""),
            /* primaryJarPath */ Paths.get(""),
            /* secondaryJarDir */ Paths.get(""),
            /* secondaryJarPattern */ "",
            /* additionalDexStoreJarMetaPath */ Paths.get(""),
            /* additionalDexStoreJarDir */ Paths.get(""),
            /* proguardFullConfigFile */ Optional.empty(),
            /* proguardMappingFile */ Optional.empty(),
            false,
            new DexSplitMode(
                /* shouldSplitDex */ true,
                ZipSplitter.DexSplitStrategy.MAXIMIZE_PRIMARY_DEX_SIZE,
                DexStore.JAR,
                /* linearAllocHardLimit */ 4 * 1024 * 1024,
                /* primaryDexPatterns */ ImmutableSet.of("List"),
                Optional.of(new FakeSourcePath("the/manifest.txt")),
                /* primaryDexScenarioFile */ Optional.empty(),
                /* isPrimaryDexScenarioOverflowAllowed */ false,
                /* secondaryDexHeadClassesFile */ Optional.empty(),
                /* secondaryDexTailClassesFile */ Optional.empty()),
            Optional.empty(),
            Optional.of(Paths.get("the/manifest.txt")),
            Optional.empty(),
            Optional.empty(),
            /* additionalDexStoreToJarPathMap */ ImmutableMultimap.of(),
            new APKModuleGraph(null, (BuildTarget) null, null),
            /* pathToReportDir */ Paths.get(""));

    Predicate<String> requiredInPrimaryZipPredicate =
        splitZipStep.createRequiredInPrimaryZipPredicate(
            ProguardTranslatorFactory.createForTest(Optional.empty()),
            Suppliers.ofInstance(ImmutableList.of()));
    assertTrue(
        "com/google/common/collect/ImmutableSortedSet.class is listed in the manifest verbatim.",
        requiredInPrimaryZipPredicate.apply("com/google/common/collect/ImmutableSortedSet.class"));
    assertTrue(
        "com/google/common/collect/ImmutableSet.class is in the manifest with whitespace.",
        requiredInPrimaryZipPredicate.apply("com/google/common/collect/ImmutableSet.class"));
    assertFalse(
        "com/google/common/collect/ImmutableSet.class cannot have whitespace as param.",
        requiredInPrimaryZipPredicate.apply("  com/google/common/collect/ImmutableSet.class"));
    assertFalse(
        "com/google/common/collect/ImmutableMap.class is commented out.",
        requiredInPrimaryZipPredicate.apply("com/google/common/collect/ImmutableMap.class"));
    assertFalse(
        "com/google/common/collect/Iterables.class is not even mentioned.",
        requiredInPrimaryZipPredicate.apply("com/google/common/collect/Iterables.class"));
    assertTrue(
        "java/awt/List.class matches the substring 'List'.",
        requiredInPrimaryZipPredicate.apply("java/awt/List.class"));
    assertFalse(
        "Substring matching is case-sensitive.",
        requiredInPrimaryZipPredicate.apply("shiny/Glistener.class"));

    EasyMock.verify(projectFilesystem);
  }

  @Test
  public void testRequiredInPrimaryZipPredicateWithProguard() throws IOException {
    List<String> linesInMappingFile =
        ImmutableList.of(
            "foo.bar.MappedPrimary -> foo.bar.a:",
            "foo.bar.MappedSecondary -> foo.bar.b:",
            "foo.bar.UnmappedPrimary -> foo.bar.UnmappedPrimary:",
            "foo.bar.UnmappedSecondary -> foo.bar.UnmappedSecondary:",
            "foo.primary.MappedPackage -> x.a:",
            "foo.secondary.MappedPackage -> x.b:",
            "foo.primary.UnmappedPackage -> foo.primary.UnmappedPackage:");
    List<String> linesInManifestFile =
        ImmutableList.of(
            // Actual primary dex classes.
            "foo/bar/MappedPrimary",
            "foo/bar/UnmappedPrimary",
            // Red herrings!
            "foo/bar/b",
            "x/b");

    Path proguardConfigFile = Paths.get("the/configuration.txt");
    Path proguardMappingFile = Paths.get("the/mapping.txt");
    Path primaryDexClassesFile = Paths.get("the/manifest.txt");

    ProjectFilesystem projectFilesystem = EasyMock.createMock(ProjectFilesystem.class);
    EasyMock.expect(projectFilesystem.readLines(primaryDexClassesFile))
        .andReturn(linesInManifestFile);
    EasyMock.expect(projectFilesystem.readLines(proguardConfigFile)).andReturn(ImmutableList.of());
    EasyMock.expect(projectFilesystem.readLines(proguardMappingFile)).andReturn(linesInMappingFile);
    EasyMock.replay(projectFilesystem);

    SplitZipStep splitZipStep =
        new SplitZipStep(
            projectFilesystem,
            /* inputPathsToSplit */ ImmutableSet.of(),
            /* secondaryJarMetaPath */ Paths.get(""),
            /* primaryJarPath */ Paths.get(""),
            /* secondaryJarDir */ Paths.get(""),
            /* secondaryJarPattern */ "",
            /* additionalDexStoreJarMetaPath */ Paths.get(""),
            /* additionalDexStoreJarDir */ Paths.get(""),
            /* proguardFullConfigFile */ Optional.of(proguardConfigFile),
            /* proguardMappingFile */ Optional.of(proguardMappingFile),
            false,
            new DexSplitMode(
                /* shouldSplitDex */ true,
                ZipSplitter.DexSplitStrategy.MAXIMIZE_PRIMARY_DEX_SIZE,
                DexStore.JAR,
                /* linearAllocHardLimit */ 4 * 1024 * 1024,
                /* primaryDexPatterns */ ImmutableSet.of("/primary/", "x/"),
                Optional.of(new FakeSourcePath("the/manifest.txt")),
                /* primaryDexScenarioFile */ Optional.empty(),
                /* isPrimaryDexScenarioOverflowAllowed */ false,
                /* secondaryDexHeadClassesFile */ Optional.empty(),
                /* secondaryDexTailClassesFile */ Optional.empty()),
            Optional.empty(),
            Optional.of(Paths.get("the/manifest.txt")),
            Optional.empty(),
            Optional.empty(),
            /* additionalDexStoreToJarPathMap */ ImmutableMultimap.of(),
            new APKModuleGraph(null, (BuildTarget) null, null),
            /* pathToReportDir */ Paths.get(""));

    ProguardTranslatorFactory translatorFactory =
        ProguardTranslatorFactory.create(
            projectFilesystem,
            Optional.of(proguardConfigFile),
            Optional.of(proguardMappingFile),
            false);

    Predicate<String> requiredInPrimaryZipPredicate =
        splitZipStep.createRequiredInPrimaryZipPredicate(
            translatorFactory, Suppliers.ofInstance(ImmutableList.of()));
    assertTrue(
        "Mapped class from primary list should be in primary.",
        requiredInPrimaryZipPredicate.apply("foo/bar/a.class"));
    assertTrue(
        "Unmapped class from primary list should be in primary.",
        requiredInPrimaryZipPredicate.apply("foo/bar/UnmappedPrimary.class"));
    assertTrue(
        "Mapped class from substring should be in primary.",
        requiredInPrimaryZipPredicate.apply("x/a.class"));
    assertTrue(
        "Unmapped class from substring should be in primary.",
        requiredInPrimaryZipPredicate.apply("foo/primary/UnmappedPackage.class"));
    assertFalse(
        "Mapped class with obfuscated name match should not be in primary.",
        requiredInPrimaryZipPredicate.apply("foo/bar/b.class"));
    assertFalse(
        "Unmapped class name should not randomly be in primary.",
        requiredInPrimaryZipPredicate.apply("foo/bar/UnmappedSecondary.class"));
    assertFalse(
        "Map class with obfuscated name substring should not be in primary.",
        requiredInPrimaryZipPredicate.apply("x/b.class"));

    EasyMock.verify(projectFilesystem);
  }

  @Test
  public void testNonObfuscatedBuild() throws IOException {
    Path proguardConfigFile = Paths.get("the/configuration.txt");
    Path proguardMappingFile = Paths.get("the/mapping.txt");

    ProjectFilesystem projectFilesystem = EasyMock.createMock(ProjectFilesystem.class);
    EasyMock.expect(projectFilesystem.readLines(proguardConfigFile))
        .andReturn(ImmutableList.of("-dontobfuscate"));
    EasyMock.replay(projectFilesystem);

    SplitZipStep splitZipStep =
        new SplitZipStep(
            projectFilesystem,
            /* inputPathsToSplit */ ImmutableSet.of(),
            /* secondaryJarMetaPath */ Paths.get(""),
            /* primaryJarPath */ Paths.get(""),
            /* secondaryJarDir */ Paths.get(""),
            /* secondaryJarPattern */ "",
            /* additionalDexStoreJarMetaPath */ Paths.get(""),
            /* additionalDexStoreJarDir */ Paths.get(""),
            /* proguardFullConfigFile */ Optional.of(proguardConfigFile),
            /* proguardMappingFile */ Optional.of(proguardMappingFile),
            false,
            new DexSplitMode(
                /* shouldSplitDex */ true,
                ZipSplitter.DexSplitStrategy.MAXIMIZE_PRIMARY_DEX_SIZE,
                DexStore.JAR,
                /* linearAllocHardLimit */ 4 * 1024 * 1024,
                /* primaryDexPatterns */ ImmutableSet.of("primary"),
                /* primaryDexClassesFile */ Optional.empty(),
                /* primaryDexScenarioFile */ Optional.empty(),
                /* isPrimaryDexScenarioOverflowAllowed */ false,
                /* secondaryDexHeadClassesFile */ Optional.empty(),
                /* secondaryDexTailClassesFile */ Optional.empty()),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            /* additionalDexStoreToJarPathMap */ ImmutableMultimap.of(),
            new APKModuleGraph(null, (BuildTarget) null, null),
            /* pathToReportDir */ Paths.get(""));

    ProguardTranslatorFactory translatorFactory =
        ProguardTranslatorFactory.create(
            projectFilesystem,
            Optional.of(proguardConfigFile),
            Optional.of(proguardMappingFile),
            false);

    Predicate<String> requiredInPrimaryZipPredicate =
        splitZipStep.createRequiredInPrimaryZipPredicate(
            translatorFactory, Suppliers.ofInstance(ImmutableList.of()));
    assertTrue(
        "Primary class should be in primary.",
        requiredInPrimaryZipPredicate.apply("primary.class"));
    assertFalse(
        "Secondary class should be in secondary.",
        requiredInPrimaryZipPredicate.apply("secondary.class"));

    EasyMock.verify(projectFilesystem);
  }

  @Test
  public void testClassFilePattern() {
    assertTrue(
        SplitZipStep.CLASS_FILE_PATTERN
            .matcher("com/facebook/orca/threads/ParticipantInfo.class")
            .matches());
    assertTrue(
        SplitZipStep.CLASS_FILE_PATTERN
            .matcher("com/facebook/orca/threads/ParticipantInfo$1.class")
            .matches());
  }
}
