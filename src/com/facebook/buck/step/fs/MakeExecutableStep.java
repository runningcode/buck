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

package com.facebook.buck.step.fs;

import com.facebook.buck.io.MoreFiles;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import java.io.IOException;
import java.nio.file.Path;

public class MakeExecutableStep implements Step {
  private final ProjectFilesystem filesystem;
  private final Path file;

  public MakeExecutableStep(ProjectFilesystem filesystem, Path file) {
    this.filesystem = filesystem;
    this.file = file;
  }

  @Override
  public StepExecutionResult execute(ExecutionContext context)
      throws IOException, InterruptedException {
    MoreFiles.makeExecutable(filesystem.resolve(file));
    return StepExecutionResult.SUCCESS;
  }

  @Override
  public String getDescription(ExecutionContext context) {
    return "chmod +x " + file.toString();
  }

  @Override
  public String getShortName() {
    return "chmod";
  }
}
