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

package com.facebook.buck.step.fs;

import com.facebook.buck.io.MoreFiles;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.util.Escaper;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class WriteFileStep implements Step {

  private static final Logger LOG = Logger.get(WriteFileStep.class);

  private final ByteSource source;
  private final ProjectFilesystem filesystem;
  private final Path outputPath;
  private final boolean executable;

  public WriteFileStep(
      ProjectFilesystem filesystem, ByteSource content, Path outputPath, boolean executable) {
    Preconditions.checkArgument(
        !outputPath.isAbsolute(), "Output path must not be absolute: %s", outputPath);

    this.source = content;
    this.filesystem = filesystem;
    this.outputPath = outputPath;
    this.executable = executable;
  }

  public WriteFileStep(
      ProjectFilesystem filesystem, String content, Path outputPath, boolean executable) {
    this(filesystem, Suppliers.ofInstance(content), outputPath, executable);
  }

  public WriteFileStep(
      ProjectFilesystem filesystem,
      final Supplier<String> content,
      Path outputPath,
      boolean executable) {
    this(
        filesystem,
        new ByteSource() {
          @Override
          public InputStream openStream() throws IOException {
            // echo by default writes a trailing new line and so should we.
            return new ByteArrayInputStream((content.get() + "\n").getBytes(Charsets.UTF_8));
          }
        },
        outputPath,
        executable);
  }

  @Override
  public StepExecutionResult execute(ExecutionContext context) {
    try (InputStream sourceStream = source.openStream()) {
      filesystem.copyToPath(sourceStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
      if (executable) {
        Path resolvedPath = filesystem.resolve(outputPath);
        MoreFiles.makeExecutable(resolvedPath);
      }
      return StepExecutionResult.SUCCESS;
    } catch (IOException e) {
      LOG.error(e, "Couldn't copy bytes to %s", outputPath);
      e.printStackTrace(context.getStdErr());
      return StepExecutionResult.ERROR;
    }
  }

  @Override
  public String getShortName() {
    return "write_file";
  }

  @Override
  public String getDescription(ExecutionContext context) {
    return String.format("echo ... > %s", Escaper.escapeAsBashString(outputPath));
  }
}
