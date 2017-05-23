/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.apple.simulator;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.FakeProcess;
import com.facebook.buck.util.FakeProcessExecutor;
import com.facebook.buck.util.ProcessExecutorParams;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link AppleSimulatorDiscovery}. */
public class AppleSimulatorDiscoveryTest {
  @Before
  public void setUp() {
    assumeTrue(Platform.detect() == Platform.MACOS || Platform.detect() == Platform.LINUX);
  }

  @Test
  public void appleSimulatorsDiscoveredFromSimctlList() throws IOException, InterruptedException {
    ImmutableSet<AppleSimulator> simulators;
    try (OutputStream stdin = new ByteArrayOutputStream();
        InputStream stdout = getClass().getResourceAsStream("testdata/simctl-list.txt");
        InputStream stderr = new ByteArrayInputStream(new byte[0])) {
      FakeProcess fakeXcrunSimctlList = new FakeProcess(0, stdin, stdout, stderr);
      ProcessExecutorParams processExecutorParams =
          ProcessExecutorParams.builder()
              .setCommand(ImmutableList.of("path/to/simctl", "list"))
              .build();
      FakeProcessExecutor fakeProcessExecutor =
          new FakeProcessExecutor(ImmutableMap.of(processExecutorParams, fakeXcrunSimctlList));
      simulators =
          AppleSimulatorDiscovery.discoverAppleSimulators(
              fakeProcessExecutor, Paths.get("path/to/simctl"));
    }

    ImmutableSet<AppleSimulator> expected =
        ImmutableSet.<AppleSimulator>builder()
            .add(
                AppleSimulator.builder()
                    .setName("iPhone 4s")
                    .setUdid("F7C1CC9A-945E-4258-BA84-DEEBE683798B")
                    .setSimulatorState(AppleSimulatorState.SHUTDOWN)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("iPhone 5")
                    .setUdid("45BD7164-686C-474F-8C68-3730432BC5F2")
                    .setSimulatorState(AppleSimulatorState.SHUTDOWN)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("iPhone 5s")
                    .setUdid("70200ED8-EEF1-4BDB-BCCF-3595B137D67D")
                    .setSimulatorState(AppleSimulatorState.BOOTED)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("iPhone 6 Plus")
                    .setUdid("92340ACF-2C44-455F-BACD-573B133FB20E")
                    .setSimulatorState(AppleSimulatorState.SHUTDOWN)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("iPhone 6")
                    .setUdid("A75FF972-FE12-4656-A8CC-99572879D4A3")
                    .setSimulatorState(AppleSimulatorState.SHUTDOWN)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("iPad 2")
                    .setUdid("CC1B0BAD-BAE6-4A53-92CF-F79850654057")
                    .setSimulatorState(AppleSimulatorState.SHUTTING_DOWN)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("iPad Retina")
                    .setUdid("137AAA25-54A1-42E8-8202-84DEADD668E1")
                    .setSimulatorState(AppleSimulatorState.SHUTDOWN)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("iPad Air")
                    .setUdid("554B2E0F-63F3-4400-8319-5C5062CF4C95")
                    .setSimulatorState(AppleSimulatorState.SHUTDOWN)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("Resizable iPhone")
                    .setUdid("58E3748F-F7E6-4A45-B52C-A136B59F7A42")
                    .setSimulatorState(AppleSimulatorState.CREATING)
                    .build())
            .add(
                AppleSimulator.builder()
                    .setName("Resizable iPad")
                    .setUdid("56FE1CBC-61FF-443D-8E23-19D05864C6DB")
                    .setSimulatorState(AppleSimulatorState.SHUTDOWN)
                    .build())
            .build();

    assertThat(simulators, is(equalTo(expected)));
  }

  @Test
  public void appleSimulatorProfileDiscoveredFromPlist() throws Exception {
    AppleSimulator simulator =
        AppleSimulator.builder()
            .setName("iPhone 5s")
            .setUdid("70200ED8-EEF1-4BDB-BCCF-3595B137D67D")
            .setSimulatorState(AppleSimulatorState.BOOTED)
            .build();
    Optional<AppleSimulatorProfile> simulatorProfile =
        AppleSimulatorDiscovery.discoverAppleSimulatorProfile(
            simulator, TestDataHelper.getTestDataDirectory(this));

    Optional<AppleSimulatorProfile> expected =
        Optional.of(
            AppleSimulatorProfile.builder()
                .addSupportedProductFamilyIDs(1)
                .addSupportedArchitectures("i386", "x86_64")
                .build());

    assertThat(simulatorProfile, is(equalTo(expected)));
  }
}
