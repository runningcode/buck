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

import com.facebook.buck.io.ProjectFilesystem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * If we end up creating both an obfuscator function and a deobfuscator function, it would be nice
 * to load the proguard mapping file once. This class enables sharing that work.
 */
class ProguardTranslatorFactory {

  private final Optional<ImmutableMap<String, String>> rawMap;

  private ProguardTranslatorFactory(Optional<ImmutableMap<String, String>> rawMap) {
    this.rawMap = rawMap;
  }

  static ProguardTranslatorFactory create(
      ProjectFilesystem filesystem,
      Optional<Path> proguardFullConfigFile,
      Optional<Path> proguardMappingFile,
      boolean skipProguard)
      throws IOException {
    return new ProguardTranslatorFactory(
        loadOptionalRawMap(filesystem, proguardFullConfigFile, proguardMappingFile, skipProguard));
  }

  @VisibleForTesting
  static ProguardTranslatorFactory createForTest(Optional<ImmutableMap<String, String>> rawMap) {
    return new ProguardTranslatorFactory(rawMap);
  }

  private static Optional<ImmutableMap<String, String>> loadOptionalRawMap(
      ProjectFilesystem filesystem,
      Optional<Path> proguardFullConfigFile,
      Optional<Path> proguardMappingFile,
      boolean skipProguard)
      throws IOException {
    if (skipProguard || !proguardFullConfigFile.isPresent()) {
      return Optional.empty();
    }

    Path pathToProguardConfig = proguardFullConfigFile.get();

    // Proguard doesn't print a mapping when obfuscation is disabled.
    boolean obfuscationSkipped =
        Iterables.any(filesystem.readLines(pathToProguardConfig), "-dontobfuscate"::equals);
    if (obfuscationSkipped) {
      return Optional.empty();
    }

    Path mappingFile = proguardMappingFile.get();
    List<String> lines = filesystem.readLines(mappingFile);
    return Optional.of(ProguardMapping.readClassMapping(lines));
  }

  public Function<String, String> createDeobfuscationFunction() {
    return createFunction(false);
  }

  public Function<String, String> createObfuscationFunction() {
    return createFunction(true);
  }

  private Function<String, String> createFunction(boolean isForObfuscation) {
    if (!rawMap.isPresent()) {
      return Functions.identity();
    }

    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Map.Entry<String, String> entry : rawMap.get().entrySet()) {
      String original = entry.getKey().replace('.', '/');
      String obfuscated = entry.getValue().replace('.', '/');
      builder.put(
          isForObfuscation ? original : obfuscated, isForObfuscation ? obfuscated : original);
    }
    final Map<String, String> map = builder.build();

    return input -> {
      String mapped = map.get(input);
      if (mapped != null) {
        return mapped;
      } else {
        return input;
      }
    };
  }
}
