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

package com.facebook.buck.ide.intellij;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.stringtemplate.v4.AutoIndentWriter;
import org.stringtemplate.v4.ST;

enum StringTemplateFile {
  MODULE_TEMPLATE("ij-module.st"),
  MODULE_INDEX_TEMPLATE("ij-module-index.st"),
  MISC_TEMPLATE("ij-misc.st"),
  LIBRARY_TEMPLATE("ij-library.st"),
  GENERATED_BY_IDEA_CLASS("GeneratedByIdeaClass.st");

  private static final char DELIMITER = '%';

  private final String fileName;

  StringTemplateFile(String fileName) {
    this.fileName = fileName;
  }

  public String getFileName() {
    return fileName;
  }

  public ST getST() throws IOException {
    URL templateUrl = Resources.getResource(StringTemplateFile.class, fileName);
    String template = Resources.toString(templateUrl, StandardCharsets.UTF_8);
    return new ST(template, DELIMITER, DELIMITER);
  }

  public static void writeToFile(ProjectFilesystem projectFilesystem, ST contents, Path path)
      throws IOException {
    StringWriter stringWriter = new StringWriter();
    AutoIndentWriter noIndentWriter = new AutoIndentWriter(stringWriter);
    contents.write(noIndentWriter);
    byte[] renderedContentsBytes = noIndentWriter.toString().getBytes(StandardCharsets.UTF_8);
    if (projectFilesystem.exists(path)) {
      Sha1HashCode fileSha1 = projectFilesystem.computeSha1(path);
      Sha1HashCode contentsSha1 =
          Sha1HashCode.fromHashCode(Hashing.sha1().hashBytes(renderedContentsBytes));
      if (fileSha1.equals(contentsSha1)) {
        return;
      }
    }

    boolean danglingTempFile = false;
    Path tempFile =
        projectFilesystem.createTempFile(
            IjProjectPaths.IDEA_CONFIG_DIR, path.getFileName().toString(), ".tmp");
    try {
      danglingTempFile = true;
      try (OutputStream outputStream = projectFilesystem.newFileOutputStream(tempFile)) {
        outputStream.write(contents.render().getBytes());
      }
      projectFilesystem.createParentDirs(path);
      projectFilesystem.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
      danglingTempFile = false;
    } finally {
      if (danglingTempFile) {
        projectFilesystem.deleteFileAtPath(tempFile);
      }
    }
  }
}
