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
package com.facebook.buck.json;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.facebook.buck.io.MoreFiles;
import com.facebook.buck.io.MorePaths;
import com.facebook.buck.log.Logger;
import com.facebook.buck.rules.BuckPyFunction;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.coercer.CoercedTypeCache;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.util.Escaper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.Writer;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a serialized copy of the buck python program used to read BUCK files.
 *
 * <p>Layout of the directory:
 *
 * <pre>
 *  root/
 *    __main__.py
 *    generated_rules.py
 * </pre>
 */
class BuckPythonProgram implements AutoCloseable {

  private static final Path PATH_TO_PATHLIB_PY =
      Paths.get(System.getProperty("buck.path_to_pathlib_py", "third-party/py/pathlib/pathlib.py"));

  private static final Path PATH_TO_PYWATCHMAN =
      Paths.get(System.getProperty("buck.path_to_pywatchman", "third-party/py/pywatchman"));

  private static final Path PATH_TO_TYPING =
      Paths.get(System.getProperty("buck.path_to_typing", "third-party/py/typing/python2"));

  private static final Logger LOG = Logger.get(BuckPythonProgram.class);

  private final Path rootDirectory;

  /** Create a new instance by layout the files in a temporary directory. */
  public static BuckPythonProgram newInstance(
      TypeCoercerFactory typeCoercerFactory, ImmutableSet<Description<?>> descriptions)
      throws IOException {

    Path pythonPath;

    try {
      URL url = Resources.getResource("buck_parser");

      if ("jar".equals(url.getProtocol())) {
        // Buck is being executed from a JAR file. Extract the jar file from the resource path, and
        // verify it is correct.
        // When python attempts to import `buck_parser`, it will see the jar file, and load it via
        // zipimport, and look into the `buck_parser` directory in the root of the jar.
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        Preconditions.checkState(
            connection.getEntryName().equals("buck_parser"),
            "buck_parser directory should be at the root of the jar file.");
        URI jarFileURI = connection.getJarFileURL().toURI();
        pythonPath = Paths.get(jarFileURI);
      } else if ("file".equals(url.getProtocol())) {
        // Buck is being executed from classpath on disk. Set the parent directory as the python
        // path.
        // When python attempts to import `buck_parser`, it will look for a `buck_parser` child
        // directory in the given path.
        pythonPath = Paths.get(url.toURI()).getParent();
      } else {
        throw new IllegalStateException(
            "buck_python resource directory should reside in a local directory or in a jar file. "
                + "Got: "
                + url);
      }
    } catch (URISyntaxException e) {
      throw new IllegalStateException(
          "Failed to determine location of buck_parser python package", e);
    }

    Path generatedRoot = Files.createTempDirectory("buck_python_program");
    LOG.debug("Writing python rules stub to %s.", generatedRoot);
    try (Writer out = Files.newBufferedWriter(generatedRoot.resolve("generated_rules.py"), UTF_8)) {
      out.write("from buck_parser.buck import *\n\n");
      BuckPyFunction function = new BuckPyFunction(typeCoercerFactory, CoercedTypeCache.INSTANCE);
      for (Description<?> description : descriptions) {
        out.write(
            function.toPythonFunction(
                Description.getBuildRuleType(description), description.getConstructorArgType()));
        out.write('\n');
      }
    }

    String pathlibDir = PATH_TO_PATHLIB_PY.getParent().toString();
    String watchmanDir = PATH_TO_PYWATCHMAN.toString();
    String typingDir = PATH_TO_TYPING.toString();
    try (Writer out = Files.newBufferedWriter(generatedRoot.resolve("__main__.py"), UTF_8)) {
      out.write(
          Joiner.on("\n")
              .join(
                  "from __future__ import absolute_import",
                  "import sys",
                  "sys.path.insert(0, \""
                      + Escaper.escapeAsBashString(MorePaths.pathWithUnixSeparators(pathlibDir))
                      + "\")",
                  "sys.path.insert(0, \""
                      + Escaper.escapeAsBashString(MorePaths.pathWithUnixSeparators(watchmanDir))
                      + "\")",
                  "sys.path.insert(0, \""
                      + Escaper.escapeAsBashString(MorePaths.pathWithUnixSeparators(typingDir))
                      + "\")",
                  // Path to the bundled python code.
                  "sys.path.insert(0, \""
                      + Escaper.escapeAsBashString(MorePaths.pathWithUnixSeparators(pythonPath))
                      + "\")",
                  // Path to the generated rules stub.
                  "sys.path.insert(0, \""
                      + Escaper.escapeAsBashString(MorePaths.pathWithUnixSeparators(generatedRoot))
                      + "\")",
                  "if __name__ == '__main__':",
                  "    try:",
                  "        from buck_parser import buck",
                  "        buck.main()",
                  "    except KeyboardInterrupt:",
                  "        print >> sys.stderr, 'Killed by User'",
                  ""));
    }

    LOG.debug("Created temporary buck.py instance at %s.", generatedRoot);
    return new BuckPythonProgram(generatedRoot);
  }

  public Path getExecutablePath() {
    return this.rootDirectory.resolve("__main__.py");
  }

  @Override
  public void close() throws IOException {
    MoreFiles.deleteRecursively(this.rootDirectory);
  }

  private BuckPythonProgram(Path rootDirectory) {
    this.rootDirectory = rootDirectory;
  }
}
