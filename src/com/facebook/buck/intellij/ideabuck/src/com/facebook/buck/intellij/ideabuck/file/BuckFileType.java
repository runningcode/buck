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

package com.facebook.buck.intellij.ideabuck.file;

import com.facebook.buck.intellij.ideabuck.icons.BuckIcons;
import com.facebook.buck.intellij.ideabuck.lang.BuckLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;

/** Buck language type */
public class BuckFileType extends LanguageFileType {

  public static final BuckFileType INSTANCE = new BuckFileType();
  // TODO(#11415116): Get the default extension from Buck or .buckconfig
  private static final String DEFAULT_EXTENSION = "BUCK";

  private BuckFileType() {
    super(BuckLanguage.INSTANCE);
  }

  public String getName() {
    return "Buck";
  }

  public String getDescription() {
    return "Buck file";
  }

  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  public Icon getIcon() {
    return BuckIcons.FILE_TYPE;
  }
}
