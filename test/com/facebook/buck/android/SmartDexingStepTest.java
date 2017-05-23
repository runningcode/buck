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

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.android.SmartDexingStep.DxPseudoRule;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.MoreAsserts;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.easymock.EasyMockSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SmartDexingStepTest extends EasyMockSupport {
  @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

  /** Tests whether pseudo rule cache detection is working properly. */
  @Test
  public void testDxPseudoRuleCaching() throws InterruptedException, IOException {
    File testIn = new File(tmpDir.getRoot(), "testIn");
    try (ZipOutputStream zipOut =
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(testIn)))) {
      zipOut.putNextEntry(new ZipEntry("foobar"));
      zipOut.write(new byte[] {0});
    }

    File outputFile = tmpDir.newFile("out.dex");
    Path outputHashFile = new File(tmpDir.getRoot(), "out.dex.hash").toPath();
    Files.write("dummy", outputHashFile.toFile(), Charsets.UTF_8);

    ProjectFilesystem filesystem = new ProjectFilesystem(tmpDir.getRoot().toPath());

    Sha1HashCode actualHashCode = Sha1HashCode.of(Strings.repeat("a", 40));
    DxPseudoRule rule =
        new DxPseudoRule(
            filesystem,
            ImmutableMap.of(testIn.toPath(), actualHashCode),
            ImmutableSet.of(testIn.toPath()),
            outputFile.toPath(),
            outputHashFile,
            EnumSet.of(DxStep.Option.NO_OPTIMIZE),
            Optional.empty(),
            Optional.empty());
    assertFalse("'dummy' is not a matching input hash", rule.checkIsCached());

    // Write the real hash into the output hash file and ensure that checkIsCached now
    // yields true.
    String actualHash = rule.hashInputs();
    assertFalse(actualHash.isEmpty());
    Files.write(actualHash, outputHashFile.toFile(), Charsets.UTF_8);

    assertTrue("Matching input hash should be considered cached", rule.checkIsCached());
  }

  @Test
  public void testCreateDxStepForDxPseudoRuleWithXzOutput()
      throws InterruptedException, IOException {
    ProjectFilesystem filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();

    ImmutableList<Path> filesToDex =
        ImmutableList.of(Paths.get("foo.dex.jar"), Paths.get("bar.dex.jar"));
    Path outputPath = Paths.get("classes.dex.jar.xz");
    EnumSet<DxStep.Option> dxOptions = EnumSet.noneOf(DxStep.Option.class);

    ImmutableList.Builder<Step> steps = new ImmutableList.Builder<>();
    SmartDexingStep.createDxStepForDxPseudoRule(
        steps, filesystem, filesToDex, outputPath, dxOptions, Optional.empty(), Optional.empty());

    MoreAsserts.assertSteps(
        "Steps should repack zip entries and then compress using xz.",
        ImmutableList.of(
            Joiner.on(" ")
                .join(
                    "(cd",
                    filesystem.getRootPath(),
                    "&&",
                    Paths.get("/usr/bin/dx"),
                    "--dex --output",
                    filesystem.resolve("classes.dex.tmp.jar"),
                    filesystem.resolve("foo.dex.jar"),
                    filesystem.resolve("bar.dex.jar") + ")"),
            "repack classes.dex.tmp.jar in classes.dex.jar",
            "rm -f " + filesystem.resolve("classes.dex.tmp.jar"),
            "dex_meta dexPath:classes.dex.jar dexMetaPath:classes.dex.jar.meta",
            "xz -z -4 --check=crc32 classes.dex.jar"),
        steps.build(),
        createMockedExecutionContext());

    verifyAll();
  }

  @Test
  public void testCreateDxStepForDxPseudoRuleWithXzOutputNonDefaultCompression()
      throws InterruptedException, IOException {
    ProjectFilesystem filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();

    ImmutableList<Path> filesToDex =
        ImmutableList.of(Paths.get("foo.dex.jar"), Paths.get("bar.dex.jar"));
    Path outputPath = Paths.get("classes.dex.jar.xz");
    EnumSet<DxStep.Option> dxOptions = EnumSet.noneOf(DxStep.Option.class);
    ImmutableList.Builder<Step> steps = new ImmutableList.Builder<>();
    SmartDexingStep.createDxStepForDxPseudoRule(
        steps, filesystem, filesToDex, outputPath, dxOptions, Optional.of(9), Optional.empty());

    MoreAsserts.assertSteps(
        "Steps should repack zip entries and then compress using xz.",
        ImmutableList.of(
            Joiner.on(" ")
                .join(
                    "(cd",
                    filesystem.getRootPath(),
                    "&&",
                    Paths.get("/usr/bin/dx"),
                    "--dex --output",
                    filesystem.resolve("classes.dex.tmp.jar"),
                    filesystem.resolve("foo.dex.jar"),
                    filesystem.resolve("bar.dex.jar") + ")"),
            "repack classes.dex.tmp.jar in classes.dex.jar",
            "rm -f " + filesystem.resolve("classes.dex.tmp.jar"),
            "dex_meta dexPath:classes.dex.jar dexMetaPath:classes.dex.jar.meta",
            "xz -z -9 --check=crc32 classes.dex.jar"),
        steps.build(),
        createMockedExecutionContext());

    verifyAll();
  }

  @Test
  public void testCreateDxStepForDxPseudoRuleWithDexOutput()
      throws InterruptedException, IOException {
    ProjectFilesystem filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();

    ImmutableList<Path> filesToDex =
        ImmutableList.of(Paths.get("foo.dex.jar"), Paths.get("bar.dex.jar"));
    Path outputPath = Paths.get("classes.dex");
    EnumSet<DxStep.Option> dxOptions = EnumSet.noneOf(DxStep.Option.class);
    ImmutableList.Builder<Step> steps = new ImmutableList.Builder<>();
    SmartDexingStep.createDxStepForDxPseudoRule(
        steps, filesystem, filesToDex, outputPath, dxOptions, Optional.empty(), Optional.empty());

    assertEquals(
        Joiner.on(" ")
            .join(
                "(cd",
                filesystem.getRootPath(),
                "&&",
                Paths.get("/usr/bin/dx"),
                "--dex --output",
                filesystem.resolve("classes.dex"),
                filesystem.resolve("foo.dex.jar"),
                filesystem.resolve("bar.dex.jar") + ")"),
        Iterables.getOnlyElement(steps.build()).getDescription(createMockedExecutionContext()));
    verifyAll();
  }

  @Test
  public void testCreateDxStepForDxPseudoRuleWithDexJarOutput()
      throws InterruptedException, IOException {
    ProjectFilesystem filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();

    ImmutableList<Path> filesToDex =
        ImmutableList.of(Paths.get("foo.dex.jar"), Paths.get("bar.dex.jar"));
    Path outputPath = Paths.get("classes.dex.jar");
    EnumSet<DxStep.Option> dxOptions = EnumSet.noneOf(DxStep.Option.class);
    ImmutableList.Builder<Step> steps = new ImmutableList.Builder<>();
    SmartDexingStep.createDxStepForDxPseudoRule(
        steps, filesystem, filesToDex, outputPath, dxOptions, Optional.empty(), Optional.empty());

    MoreAsserts.assertSteps(
        "Wrong steps",
        ImmutableList.of(
            Joiner.on(" ")
                .join(
                    "(cd",
                    filesystem.getRootPath(),
                    "&&",
                    Paths.get("/usr/bin/dx"),
                    "--dex --output",
                    filesystem.resolve("classes.dex.jar"),
                    filesystem.resolve("foo.dex.jar"),
                    filesystem.resolve("bar.dex.jar") + ")"),
            "dex_meta dexPath:classes.dex.jar dexMetaPath:classes.dex.jar.meta"),
        steps.build(),
        createMockedExecutionContext());
    verifyAll();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateDxStepForDxPseudoRuleWithUnrecognizedOutput() throws InterruptedException {
    ProjectFilesystem filesystem = new ProjectFilesystem(tmpDir.getRoot().toPath());

    ImmutableList<Path> filesToDex =
        ImmutableList.of(Paths.get("foo.dex.jar"), Paths.get("bar.dex.jar"));
    Path outputPath = Paths.get("classes.flex");
    EnumSet<DxStep.Option> dxOptions = EnumSet.noneOf(DxStep.Option.class);
    SmartDexingStep.createDxStepForDxPseudoRule(
        new ImmutableList.Builder<>(),
        filesystem,
        filesToDex,
        outputPath,
        dxOptions,
        Optional.empty(),
        Optional.empty());
  }

  private ExecutionContext createMockedExecutionContext() throws IOException {
    AndroidPlatformTarget androidPlatformTarget = createMock(AndroidPlatformTarget.class);
    expect(androidPlatformTarget.getDxExecutable()).andStubReturn(Paths.get("/usr/bin/dx"));
    replayAll();

    return TestExecutionContext.newBuilder()
        .setAndroidPlatformTargetSupplier(Suppliers.ofInstance(androidPlatformTarget))
        .build();
  }
}
