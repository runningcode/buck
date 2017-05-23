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

package com.facebook.buck.util;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Helper class that keeps a list of compiled patterns and provides a method to check whether a
 * string matches at least one of them.
 *
 * <p>Note that a string is considered to be matched if there are no patterns.
 */
public class PatternsMatcher {

  private final Iterable<Pattern> patterns;
  private final boolean hasPatterns;

  public PatternsMatcher(Iterable<String> rawPatterns) {
    patterns = Iterables.transform(rawPatterns, Pattern::compile);
    hasPatterns = rawPatterns.iterator().hasNext();
  }

  public PatternsMatcher(Set<Pattern> compiledPatterns) {
    patterns = compiledPatterns;
    hasPatterns = !compiledPatterns.isEmpty();
  }

  /** @return true if the given string matches some of the patterns or there are no patterns */
  public boolean matches(final String string) {
    if (!hasPatterns) {
      return true;
    }
    for (Pattern pattern : patterns) {
      if (pattern.matcher(string).matches()) {
        return true;
      }
    }
    return false;
  }

  /** @return true if there is at least one pattern */
  public boolean hasPatterns() {
    return hasPatterns;
  }

  /** @return a view of the given map where all non-matching keys are removed. */
  public <V> Map<String, V> filterMatchingMapKeys(final Map<String, V> entries) {
    if (!hasPatterns) {
      return entries;
    }

    return Maps.filterEntries(entries, entry -> matches(entry.getKey()));
  }
}
