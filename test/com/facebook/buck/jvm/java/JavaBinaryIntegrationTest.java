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

package com.facebook.buck.jvm.java;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import com.facebook.buck.jvm.java.testutil.AbiCompilationModeTest;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JavaBinaryIntegrationTest extends AbiCompilationModeTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  private ProjectWorkspace workspace;

  @Before
  public void checkPlatform() {
    assumeThat(Platform.detect(), not(Platform.WINDOWS));
  }

  @Test
  public void fatJarLoadingNativeLibraries() throws IOException {
    setUpProjectWorkspaceForScenario("fat_jar");
    workspace.runBuckCommand("run", "//:bin-fat").assertSuccess();
  }

  @Test
  public void fatJarOutputIsRecorded() throws IOException, InterruptedException {
    setUpProjectWorkspaceForScenario("fat_jar");
    workspace.enableDirCache();
    workspace.runBuckCommand("build", "//:bin-fat").assertSuccess();
    workspace.runBuckCommand("clean");
    Path path = workspace.buildAndReturnOutput("//:bin-fat");
    workspace.getBuildLog().assertTargetWasFetchedFromCache("//:bin-fat");
    assertTrue(workspace.asCell().getFilesystem().exists(path));
  }

  @Test
  public void fatJarWithOutput() throws IOException, InterruptedException {
    setUpProjectWorkspaceForScenario("fat_jar");
    Path jar = workspace.buildAndReturnOutput("//:bin-output");
    ProcessExecutor.Result result = workspace.runJar(jar);
    assertEquals("output", result.getStdout().get().trim());
    assertEquals("error", result.getStderr().get().trim());
  }

  @Test
  public void disableCachingForBinaries() throws IOException {
    setUpProjectWorkspaceForScenario("java_binary_with_blacklist");
    workspace.enableDirCache();
    workspace
        .runBuckBuild("-c", "java.cache_binaries=false", "//:bin-no-blacklist")
        .assertSuccess();
    workspace.runBuckCommand("clean").assertSuccess();
    workspace
        .runBuckBuild("-c", "java.cache_binaries=false", "//:bin-no-blacklist")
        .assertSuccess();
    workspace.getBuildLog().assertTargetBuiltLocally("//:bin-no-blacklist");
  }

  @Test
  public void fatJarWithExitCode() throws IOException {
    setUpProjectWorkspaceForScenario("fat_jar");

    workspace.runBuckCommand("run", "//:bin-exit-code").assertSpecialExitCode("error", 5);
  }

  @Test
  public void fatJarWithVmArguments() throws IOException, InterruptedException {
    setUpProjectWorkspaceForScenario("fat_jar");
    ImmutableList<String> args = ImmutableList.of("-ea", "-Dfoo.bar.baz=1234", "-Xms64m");
    String expected = Joiner.on("\n").join(args);
    Path jar = workspace.buildAndReturnOutput("//:bin-jvm-args");
    ProcessExecutor.Result result = workspace.runJar(jar, args);
    assertEquals(expected, result.getStdout().get().trim());
  }

  @Test
  public void fatJarWithAlternateJavaBin() throws IOException, InterruptedException {
    setUpProjectWorkspaceForScenario("fat_jar");
    Path jar = workspace.buildAndReturnOutput("//:bin-alternate-java");
    String javaHomeArg = "-Dbuck.fatjar.java.home=" + tmp.getRoot().toString();
    ProcessExecutor.Result result = workspace.runJar(jar, ImmutableList.of(javaHomeArg));
    assertEquals("Running java wrapper\nRunning inner jar", result.getStdout().get().trim());
  }

  @Test
  public void jarWithMetaInfo() throws IOException, InterruptedException {
    setUpProjectWorkspaceForScenario("java_binary_with_meta_inf");
    Path jar = workspace.buildAndReturnOutput("//:bin-meta-inf");
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      assertNotNull(jarFile.getEntry("META-INF/test.txt"));
    }
  }

  @Test
  public void fatJarWithBlacklist() throws IOException {
    setUpProjectWorkspaceForScenario("java_binary_with_blacklist");
    Path binaryJarWithBlacklist = workspace.buildAndReturnOutput("//:bin-blacklist");
    Path binaryJarWithoutBlacklist = workspace.buildAndReturnOutput("//:bin-no-blacklist");

    ImmutableSet<String> commonEntries =
        ImmutableSet.of(
            "META-INF/", "META-INF/MANIFEST.MF", "com/", "com/example/", "com/example/B.class");
    ImmutableSet<String> blacklistedEntries =
        ImmutableSet.of(
            "com/example/A.class", "com/example/A$C.class", "com/example/Alligator.class");
    assertEquals(
        "com.example.Alligator, com.example.A and any inner classes should be removed.",
        commonEntries,
        new ZipInspector(binaryJarWithBlacklist).getZipFileEntries());
    assertEquals(
        ImmutableSet.builder().addAll(commonEntries).addAll(blacklistedEntries).build(),
        new ZipInspector(binaryJarWithoutBlacklist).getZipFileEntries());
  }

  @Test
  public void testJarWithCorruptInput() throws IOException {
    setUpProjectWorkspaceForScenario("corruption");
    workspace.runBuckBuild("//:simple-lib").assertSuccess();
    String libJar =
        workspace
            .runBuckCommand("targets", "--show_output", "//:simple-lib")
            .assertSuccess()
            .getStdout()
            .split(" ")[1]
            .trim();

    // Now corrupt the output jar.
    Path jarPath = workspace.getPath(libJar);
    byte[] bytes = Files.readAllBytes(jarPath);
    for (int backOffset = 7; backOffset <= 10; backOffset++) {
      bytes[bytes.length - backOffset] = 0x77;
    }
    Files.write(jarPath, bytes);

    ProjectWorkspace.ProcessResult result = workspace.runBuckBuild("//:wrapper_01").assertFailure();
    // Should show the rule that failed.
    assertThat(result.getStderr(), containsString("//:simple-lib"));
    // Should show the jar we were operating on.
    assertThat(result.getStderr(), containsString(libJar));
    // Should show the original exception.
    assertThat(result.getStderr(), containsString("ZipError"));
  }

  private ProjectWorkspace setUpProjectWorkspaceForScenario(String scenario) throws IOException {
    workspace = TestDataHelper.createProjectWorkspaceForScenario(this, scenario, tmp);
    workspace.setUp();
    setWorkspaceCompilationMode(workspace);
    return workspace;
  }
}
