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

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.shell.Shell;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.fs.WriteFileStep;
import com.facebook.buck.util.MoreIterables;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** OCaml linking step. Dependencies and inputs should be topologically ordered */
public class OcamlDebugLauncherStep implements Step {

  private final ProjectFilesystem filesystem;
  private final SourcePathResolver resolver;
  private final Args args;

  public OcamlDebugLauncherStep(
      ProjectFilesystem filesystem, SourcePathResolver resolver, Args args) {
    this.filesystem = filesystem;
    this.resolver = resolver;
    this.args = args;
  }

  @Override
  public StepExecutionResult execute(ExecutionContext context)
      throws InterruptedException, IOException {
    String debugCmdStr = getDebugCmd();
    String debugLauncherScript = getDebugLauncherScript(debugCmdStr);

    WriteFileStep writeFile =
        new WriteFileStep(filesystem, debugLauncherScript, args.getOutput(), /* executable */ true);
    return writeFile.execute(context);
  }

  private String getDebugLauncherScript(String debugCmdStr) {
    ImmutableList.Builder<String> debugFile = ImmutableList.builder();
    debugFile.add("#!/bin/sh");
    debugFile.add(debugCmdStr);

    return Joiner.on("\n").join(debugFile.build());
  }

  private String getDebugCmd() {
    ImmutableList.Builder<String> debugCmd = ImmutableList.builder();
    debugCmd.add("rlwrap");
    debugCmd.addAll(args.ocamlDebug.getCommandPrefix(resolver));

    Iterable<String> includesBytecodeDirs =
        FluentIterable.from(args.ocamlInput)
            .transformAndConcat(
                new Function<OcamlLibrary, Iterable<String>>() {
                  @Override
                  public Iterable<String> apply(OcamlLibrary input) {
                    return input.getBytecodeIncludeDirs();
                  }
                });

    ImmutableList<String> includesBytecodeFlags =
        ImmutableList.copyOf(
            MoreIterables.zipAndConcat(
                Iterables.cycle(OcamlCompilables.OCAML_INCLUDE_FLAG), includesBytecodeDirs));

    debugCmd.addAll(includesBytecodeFlags);
    debugCmd.addAll(args.bytecodeIncludeFlags);

    debugCmd.add(args.bytecodeOutput.toString());
    return Shell.shellQuoteJoin(debugCmd.build(), " ") + " $@";
  }

  @Override
  public String getShortName() {
    return "OCaml debug launcher";
  }

  @Override
  public String getDescription(ExecutionContext context) {
    return getShortName();
  }

  public static class Args implements RuleKeyAppendable {
    public final Tool ocamlDebug;
    public final Path bytecodeOutput;
    public final ImmutableList<OcamlLibrary> ocamlInput;
    public final ImmutableList<String> bytecodeIncludeFlags;

    public Args(
        Tool ocamlDebug,
        Path bytecodeOutput,
        List<OcamlLibrary> ocamlInput,
        List<String> bytecodeIncludeFlags) {
      this.ocamlDebug = ocamlDebug;
      this.bytecodeOutput = bytecodeOutput;
      this.ocamlInput = ImmutableList.copyOf(ocamlInput);
      this.bytecodeIncludeFlags = ImmutableList.copyOf(bytecodeIncludeFlags);
    }

    public Path getOutput() {
      return Paths.get(bytecodeOutput.toString() + ".debug");
    }

    @Override
    public void appendToRuleKey(RuleKeyObjectSink sink) {
      sink.setReflectively("ocamlDebug", ocamlDebug)
          .setReflectively("bytecodeOutput", bytecodeOutput.toString())
          .setReflectively("flags", bytecodeIncludeFlags);
    }
  }
}
