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
package com.facebook.buck.rules;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Optional;

public interface CellPathResolver {
  /**
   * @param cellName name of cell, Optional.empty() for root cell.
   * @return Absolute path to the physical location of the cell.
   */
  Path getCellPath(Optional<String> cellName);

  /** @return absolute paths to all cells this resolver knows about. */
  ImmutableMap<String, Path> getCellPaths();

  /**
   * Returns a cell name that can be used to refer to the cell at the given path.
   *
   * <p>Returns {@code Optional.empty()} if the path refers to the root cell. Returns the
   * lexicographically smallest name if the cell path has multiple names.
   *
   * <p>Note: this is not the inverse of {@link #getCellPath(Optional)}, which returns the current,
   * rather than the root, cell path if the cell name is empty.
   *
   * @throws IllegalArgumentException if cell path is not known to the cell path resolver.
   */
  Optional<String> getCanonicalCellName(Path cellPath);
}
