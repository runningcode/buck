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

package com.facebook.buck.ocaml;

import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/** Runs ocamllex to generate .ml files from .mll */
public class OcamlLexStep extends ShellStep {

  public static class Args {
    public final Tool lexCompiler;
    public final Path output;
    public final Path input;

    public Args(Tool lexCompiler, Path output, Path input) {
      this.lexCompiler = lexCompiler;
      this.output = output;
      this.input = input;
    }
  }

  private final SourcePathResolver resolver;
  private final Args args;

  public OcamlLexStep(Path workingDirectory, SourcePathResolver resolver, Args args) {
    super(workingDirectory);
    this.resolver = resolver;
    this.args = args;
  }

  @Override
  public String getShortName() {
    return "OCaml lex";
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    return ImmutableList.<String>builder()
        .addAll(args.lexCompiler.getCommandPrefix(resolver))
        .add("-o", args.output.toString())
        .add(args.input.toString())
        .build();
  }
}
