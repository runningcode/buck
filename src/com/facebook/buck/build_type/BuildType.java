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

package com.facebook.buck.build_type;

import com.facebook.buck.log.Logger;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.Files;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public enum BuildType {
  LOCAL_ANT,
  LOCAL_PEX,
  RELEASE_PEX,
  ;

  private static final Logger LOG = Logger.get(BuildType.class);

  /**
   * Check in runtime to see what current build type is.
   *
   * <p>To run buck in different modes you can invoke: buck run buck --config build.type=enum_value
   */
  public static final Supplier<BuildType> CURRENT_BUILD_TYPE =
      Suppliers.memoize(
          new Supplier<BuildType>() {
            @Override
            public BuildType get() {
              String buildTypeFilename = System.getProperty("buck.buck_build_type_info");
              if (buildTypeFilename == null) {
                return LOCAL_ANT;
              }
              try {
                String contents =
                    Files.readFirstLine(
                        Paths.get(buildTypeFilename).toFile(), StandardCharsets.UTF_8);
                return BuildType.valueOf(contents);
              } catch (IOException e) {
                LOG.error(e, "Failed to read build type, using LOCAL_ANT type.");
                return LOCAL_ANT;
              }
            }
          });
}
