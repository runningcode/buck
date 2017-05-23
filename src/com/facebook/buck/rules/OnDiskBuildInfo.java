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

package com.facebook.buck.rules;

import com.facebook.buck.util.sha1.Sha1HashCode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface OnDiskBuildInfo {

  /** @return the build rule metadata value associated with the specified key, if it exists. */
  Optional<String> getValue(String key);

  /** @return the build engine metadata value associated with the specified key, if it exists. */
  Optional<String> getBuildValue(String key);

  /** @return the sequence of values associated with the specified key, if it exists. */
  Optional<ImmutableList<String>> getValues(String key);

  /**
   * Tries to read the values and if it fails it logs the attributes of the file it tried to read.
   */
  ImmutableList<String> getValuesOrThrow(String key);

  /**
   * @return the map of strings associated with the specified key in the build engine metadata, if
   *     it exists.
   */
  Optional<ImmutableMap<String, String>> getBuildMap(String key);

  /**
   * @return Assuming the value associated with the specified key is a valid sha1 hash, returns it
   *     as a {@link Sha1HashCode}, if it exists.
   */
  Optional<Sha1HashCode> getHash(String key);

  /**
   * Returns the {@link RuleKey} for the rule whose output is currently stored on disk.
   *
   * <p>This value would have been written the last time the rule was built successfully.
   */
  Optional<RuleKey> getRuleKey(String key);

  List<String> getOutputFileContentsByLine(Path path) throws IOException;

  void deleteExistingMetadata() throws IOException;
}
