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

package com.facebook.buck.rules.coercer;

import static com.facebook.buck.rules.TestCellBuilder.createCellRoots;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PathTypeCoercerTest {

  private ProjectFilesystem filesystem;
  private final Path pathRelativeToProjectRoot = Paths.get("");
  private final PathTypeCoercer pathTypeCoercer =
      new PathTypeCoercer(PathTypeCoercer.PathExistenceVerificationMode.VERIFY);

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() {
    filesystem = new FakeProjectFilesystem().setIgnoreValidityOfPaths(false);
  }

  @Test
  public void coercingInvalidPathThrowsException() {
    String invalidPath = "";
    try {
      pathTypeCoercer.coerce(
          createCellRoots(filesystem), filesystem, pathRelativeToProjectRoot, invalidPath);
      fail("expected to throw when");
    } catch (CoerceFailedException e) {
      assertEquals("invalid path", e.getMessage());
    }
  }

  @Test
  public void coercingMissingFileThrowsExceptionWhenVerificationEnabled() throws Exception {
    String missingPath = "hello";
    expectedException.expect(CoerceFailedException.class);
    expectedException.expectMessage(String.format("no such file or directory '%s'", missingPath));

    pathTypeCoercer.coerce(
        createCellRoots(filesystem), filesystem, pathRelativeToProjectRoot, missingPath);
  }

  @Test
  public void coercingMissingFileDoesNotThrowWhenVerificationDisabled() throws Exception {
    String missingPath = "hello";
    new PathTypeCoercer(PathTypeCoercer.PathExistenceVerificationMode.DO_NOT_VERIFY)
        .coerce(createCellRoots(filesystem), filesystem, pathRelativeToProjectRoot, missingPath);
  }
}
