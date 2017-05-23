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

package com.facebook.buck.apple.xcode.xcodeproj;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** Unit tests for {@link FileTypes}. */
public class FileTypesTest {

  @Test
  public void testFileExtensionsContainNoDots() {
    for (String fileExtension : FileTypes.FILE_EXTENSION_TO_IDENTIFIER.keySet()) {
      assertFalse(fileExtension.contains("."));
    }
  }

  @Test
  public void testFileExtensionMapsToIdentifier() {
    assertEquals(FileTypes.FILE_EXTENSION_TO_IDENTIFIER.get("cpp"), "sourcecode.cpp.cpp");
  }
}
