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

import java.nio.file.Path;

/** Specifies how secondary .dex files should be stored in the .apk. */
enum DexStore {
  /** Secondary dexes should be raw dex files for inclusion in the APK. */
  RAW {
    @Override
    public String fileNameForSecondary(int index) {
      return fileNameForSecondary("classes", index);
    }

    @Override
    public String fileNameForSecondary(String prefix, int index) {
      // Google expects secondary dex files to start at 2.
      // I guess classes.dex is number 1.
      return String.format("%s%d.dex", prefix, index + 2);
    }

    @Override
    public boolean matchesPath(Path path) {
      return path.getFileName().toString().matches(".*\\d+\\.dex");
    }
  },

  /** Secondary dexes should be compressed using JAR's deflate. */
  JAR {
    @Override
    public String fileNameForSecondary(int index) {
      return fileNameForSecondary("secondary", index);
    }

    @Override
    public String fileNameForSecondary(String prefix, int index) {
      // Start at one for easier comprehension by humans.
      return String.format("%s-%s.dex.jar", prefix, index + 1);
    }

    @Override
    public boolean matchesPath(Path path) {
      return path.getFileName().toString().endsWith(".dex.jar");
    }
  },

  /** Secondary dexes should be stored uncompressed in jars that will be XZ-compressed. */
  XZ {
    @Override
    public String fileNameForSecondary(int index) {
      return fileNameForSecondary("secondary", index);
    }

    @Override
    public String fileNameForSecondary(String prefix, int index) {
      // Start at one for easier comprehension by humans.
      return String.format("%s-%s.dex.jar.xz", prefix, index + 1);
    }

    @Override
    public boolean matchesPath(Path path) {
      return path.getFileName().toString().endsWith(".dex.jar.xz");
    }
  },

  /** Secondary dexes will be solid compressed into a single XZS file. */
  XZS {
    // Since secondary dexes are created in parallel, we add a .tmp~
    // extension to indicate that this process is not yet complete.
    // A .dex.jar.xzs.tmp~ is a dex file that is waiting to be concatenated
    // and then XZ compressed. The ~ character is a hack to ensure that
    // the external apkbuilder tool does not copy this file to the final apk.
    // The alternative would require either rewriting apkbuilder or writing substantial code
    // to get around the current limitations of its API.

    @Override
    public String fileNameForSecondary(int index) {
      return fileNameForSecondary("secondary", index);
    }

    @Override
    public String fileNameForSecondary(String prefix, int index) {
      return String.format("%s-%s.dex.jar.xzs.tmp~", prefix, index + 1);
    }

    @Override
    public boolean matchesPath(Path path) {
      return path.getFileName().toString().endsWith(".dex.jar.xzs.tmp~");
    }
  },
  ;

  /**
   * @param index The index of a given secondary dex file, starting from 0.
   * @return The appropriate name for the secondary dex file at {@code index}.
   */
  public abstract String fileNameForSecondary(int index);

  /**
   * @param prefix The prefix to use to name the file according to what store it is in
   * @param index The index of a given secondary dex file, starting from 0.
   * @return The appropriate name for the dex file at {@code index}.
   */
  public abstract String fileNameForSecondary(String prefix, int index);

  /**
   * @param path The path where a secondary dex file will be written.
   * @return Whether that file is of this DexStore type.
   */
  public abstract boolean matchesPath(Path path);
}
