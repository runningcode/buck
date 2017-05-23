/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.cli;

import com.facebook.buck.step.TargetDevice;
import com.facebook.buck.step.TargetDeviceOptions;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import javax.annotation.Nullable;
import org.kohsuke.args4j.Option;

public class TargetDeviceCommandLineOptions {

  @VisibleForTesting public static final String EMULATOR_MODE_SHORT_ARG = "-e";
  @VisibleForTesting static final String EMULATOR_MODE_LONG_ARG = "--emulator";

  @Option(
    name = EMULATOR_MODE_LONG_ARG,
    aliases = {EMULATOR_MODE_SHORT_ARG},
    usage = "Use this option to use emulators only."
  )
  private boolean useEmulatorsOnlyMode;

  @VisibleForTesting static final String DEVICE_MODE_SHORT_ARG = "-d";
  @VisibleForTesting static final String DEVICE_MODE_LONG_ARG = "--device";

  @Option(
    name = DEVICE_MODE_LONG_ARG,
    aliases = {DEVICE_MODE_SHORT_ARG},
    usage = "Use this option to use real devices only."
  )
  private boolean useRealDevicesOnlyMode;

  @VisibleForTesting static final String SERIAL_NUMBER_SHORT_ARG = "-s";
  @VisibleForTesting static final String SERIAL_NUMBER_LONG_ARG = "--serial";
  static final String UDID_ARG = "--udid";

  @Option(
    name = SERIAL_NUMBER_LONG_ARG,
    aliases = {SERIAL_NUMBER_SHORT_ARG, UDID_ARG},
    forbids = SIMULATOR_NAME_LONG_ARG,
    metaVar = "<serial-number>",
    usage = "Use device or emulator with specific serial or UDID number."
  )
  @Nullable
  private String serialNumber;

  static final String SIMULATOR_NAME_SHORT_ARG = "-n";
  static final String SIMULATOR_NAME_LONG_ARG = "--simulator-name";

  @Option(
    name = SIMULATOR_NAME_LONG_ARG,
    aliases = {SIMULATOR_NAME_SHORT_ARG},
    forbids = SERIAL_NUMBER_LONG_ARG,
    metaVar = "<name>",
    usage = "Use simulator with specific name (Apple only)."
  )
  @Nullable
  private String simulatorName;

  public TargetDeviceCommandLineOptions() {}

  @VisibleForTesting
  TargetDeviceCommandLineOptions(String serial) {
    this.serialNumber = serial;
  }

  public boolean isEmulatorsOnlyModeEnabled() {
    return useEmulatorsOnlyMode;
  }

  public boolean isRealDevicesOnlyModeEnabled() {
    return useRealDevicesOnlyMode;
  }

  public Optional<String> getSerialNumber() {
    return Optional.ofNullable(serialNumber);
  }

  public Optional<String> getSimulatorName() {
    return Optional.ofNullable(simulatorName);
  }

  public Optional<TargetDevice> getTargetDeviceOptional() {
    if (!getSerialNumber().isPresent()
        && !isEmulatorsOnlyModeEnabled()
        && !isRealDevicesOnlyModeEnabled()) {
      return Optional.empty();
    }

    TargetDevice device =
        new TargetDevice(
            isEmulatorsOnlyModeEnabled()
                ? TargetDevice.Type.EMULATOR
                : TargetDevice.Type.REAL_DEVICE,
            getSerialNumber());
    return Optional.of(device);
  }

  public TargetDeviceOptions getTargetDeviceOptions() {
    return new TargetDeviceOptions(useEmulatorsOnlyMode, useRealDevicesOnlyMode, getSerialNumber());
  }
}
