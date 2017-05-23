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

package com.facebook.buck.jvm.java.tracing;

import java.util.List;
import javax.annotation.Nullable;

/**
 * An interface for tracing the phases of compilation in Oracle's javac. This interface is loaded in
 * the system class loader so that it is common to both Buck and compiler plugins.
 */
public interface JavacPhaseTracer {
  void beginParse(@Nullable String filename);

  void endParse();

  void beginEnter();

  void endEnter(List<String> filenames);

  void beginAnnotationProcessingRound();

  void endAnnotationProcessingRound();

  void beginAnalyze(@Nullable String filename, @Nullable String typename);

  void endAnalyze();

  void beginGenerate(@Nullable String filename, @Nullable String typename);

  void endGenerate();
}
