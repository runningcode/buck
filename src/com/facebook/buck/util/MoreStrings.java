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

package com.facebook.buck.util;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.Optional;

public final class MoreStrings {

  /** Utility class: do not instantiate. */
  private MoreStrings() {}

  public static boolean isEmpty(CharSequence sequence) {
    return sequence.length() == 0;
  }

  public static String withoutSuffix(String str, String suffix) {
    Preconditions.checkArgument(str.endsWith(suffix), "%s must end with %s", str, suffix);
    return str.substring(0, str.length() - suffix.length());
  }

  public static String capitalize(String str) {
    if (!str.isEmpty()) {
      return str.substring(0, 1).toUpperCase() + str.substring(1);
    } else {
      return "";
    }
  }

  public static int getLevenshteinDistance(String str1, String str2) {

    char[] arr1 = str1.toCharArray();
    char[] arr2 = str2.toCharArray();
    int[][] levenshteinDist = new int[arr1.length + 1][arr2.length + 1];

    for (int i = 0; i <= arr1.length; i++) {
      levenshteinDist[i][0] = i;
    }

    for (int j = 1; j <= arr2.length; j++) {
      levenshteinDist[0][j] = j;
    }

    for (int i = 1; i <= arr1.length; i++) {
      for (int j = 1; j <= arr2.length; j++) {
        if (arr1[i - 1] == arr2[j - 1]) {
          levenshteinDist[i][j] = levenshteinDist[i - 1][j - 1];
        } else {
          levenshteinDist[i][j] =
              Math.min(
                  levenshteinDist[i - 1][j] + 1,
                  Math.min(levenshteinDist[i][j - 1] + 1, levenshteinDist[i - 1][j - 1] + 1));
        }
      }
    }

    return levenshteinDist[arr1.length][arr2.length];
  }

  public static String regexPatternForAny(String... values) {
    return regexPatternForAny(Arrays.asList(values));
  }

  public static String regexPatternForAny(Iterable<String> values) {
    return "((?:" + Joiner.on(")|(?:").join(values) + "))";
  }

  public static boolean endsWithIgnoreCase(String str, String suffix) {
    if (str.length() < suffix.length()) {
      return false;
    }

    return str.substring(str.length() - suffix.length()).equalsIgnoreCase(suffix);
  }

  public static Optional<String> stripPrefix(String s, String prefix) {
    return s.startsWith(prefix)
        ? Optional.of(s.substring(prefix.length(), s.length()))
        : Optional.empty();
  }

  public static Optional<String> stripSuffix(String s, String suffix) {
    return s.endsWith(suffix)
        ? Optional.of(s.substring(0, s.length() - suffix.length()))
        : Optional.empty();
  }

  public static String truncatePretty(String data) {
    final int keepFirstChars = 10000;
    final int keepLastChars = 10000;
    final String truncateMessage = "...\n<truncated>\n...";
    return truncateMiddle(data, keepFirstChars, keepLastChars, truncateMessage);
  }

  public static String truncateMiddle(
      String data, int keepFirstChars, int keepLastChars, String truncateMessage) {
    if (data.length() <= keepFirstChars + keepLastChars + truncateMessage.length()) {
      return data;
    }
    return data.substring(0, keepFirstChars)
        + truncateMessage
        + data.substring(data.length() - keepLastChars);
  }
}
