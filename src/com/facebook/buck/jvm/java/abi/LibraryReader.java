/*
 * Copyright 2017-present Facebook, Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import org.objectweb.asm.ClassVisitor;

/** An interface for reading and listing resources and classes in a library. */
interface LibraryReader extends AutoCloseable {
  static LibraryReader of(Path path) {
    if (Files.isDirectory(path)) {
      return new DirectoryReader(path);
    } else {
      return new JarReader(path);
    }
  }

  static LibraryReader of(
      SourceVersion targetVersion, Elements elements, Iterable<TypeElement> topLevelTypes) {
    return new TypeElementsReader(targetVersion, elements, topLevelTypes);
  }

  List<Path> getRelativePaths() throws IOException;

  InputStream openResourceFile(Path relativePath) throws IOException;

  void visitClass(Path relativePath, ClassVisitor cv) throws IOException;

  @Override
  void close() throws IOException;

  default boolean isResource(Path path) {
    return !isClass(path);
  }

  default boolean isClass(Path path) {
    return path.toString().endsWith(".class");
  }
}
