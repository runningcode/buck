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
package com.facebook.buck.macho;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class FatArchTest {
  @Test
  public void testCreatingFromBytesBigEndian() throws Exception {
    FatArch arch =
        FatArchUtils.createFromBuffer(
            ByteBuffer.wrap(FatArchTestData.getBigEndian()).order(ByteOrder.BIG_ENDIAN));
    FatArchTestData.checkValues(arch);
  }

  @Test
  public void testCreatingFromBytesLittleEndian() throws Exception {
    FatArch arch =
        FatArchUtils.createFromBuffer(
            ByteBuffer.wrap(FatArchTestData.getLittleEndian()).order(ByteOrder.LITTLE_ENDIAN));
    FatArchTestData.checkValues(arch);
  }
}
