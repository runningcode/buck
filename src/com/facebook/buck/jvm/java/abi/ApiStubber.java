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

package com.facebook.buck.jvm.java.abi;

import com.facebook.buck.io.ProjectFilesystem;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApiStubber {

  private ApiStubber() {
    // Command line utility.
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    Path source = Paths.get(args[0]);
    Path destination = Paths.get(args[1]);

    new StubJar(source).writeTo(new ProjectFilesystem(Paths.get("").toAbsolutePath()), destination);
  }
}
