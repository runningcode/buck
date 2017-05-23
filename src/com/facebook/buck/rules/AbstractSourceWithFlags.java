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

package com.facebook.buck.rules;

import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.util.List;
import org.immutables.value.Value;

/** Simple type representing a {@link SourcePath} and a list of file-specific flags. */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractSourceWithFlags implements Comparable<AbstractSourceWithFlags> {

  @Value.Parameter
  public abstract SourcePath getSourcePath();

  @Value.Parameter
  public abstract List<String> getFlags();

  @Override
  public int compareTo(AbstractSourceWithFlags that) {
    if (this == that) {
      return 0;
    }

    return ComparisonChain.start()
        .compare(this.getSourcePath(), that.getSourcePath())
        .compare(this.getFlags(), that.getFlags(), Ordering.<String>natural().lexicographical())
        .result();
  }

  public static SourceWithFlags of(SourcePath sourcePath) {
    return SourceWithFlags.builder().setSourcePath(sourcePath).build();
  }
}
