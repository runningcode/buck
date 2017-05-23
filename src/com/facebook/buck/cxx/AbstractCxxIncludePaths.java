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

package com.facebook.buck.cxx;

import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.util.MoreIterables;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@BuckStyleTuple
abstract class AbstractCxxIncludePaths {

  /** Paths added with {@code -I} */
  public abstract ImmutableSet<CxxHeaders> getIPaths();

  /** Framework paths added with {@code -F} */
  public abstract ImmutableSet<FrameworkPath> getFPaths();

  /**
   * Merge all the given {@link CxxIncludePaths}.
   *
   * <p>Combinines their path lists, deduping them (keeping the earlier of the repeated instance).
   */
  public static CxxIncludePaths concat(Iterator<CxxIncludePaths> itemIter) {
    ImmutableSet.Builder<CxxHeaders> ipathBuilder = ImmutableSet.<CxxHeaders>builder();
    ImmutableSet.Builder<FrameworkPath> fpathBuilder = ImmutableSet.<FrameworkPath>builder();

    while (itemIter.hasNext()) {
      CxxIncludePaths item = itemIter.next();
      ipathBuilder.addAll(item.getIPaths());
      fpathBuilder.addAll(item.getFPaths());
    }

    return CxxIncludePaths.of(ipathBuilder.build(), fpathBuilder.build());
  }

  public static CxxIncludePaths empty() {
    return concat(Collections.emptyIterator());
  }

  /**
   * Build a list of compiler flag strings representing the contained paths.
   *
   * <p>This method's parameters allow the caller to do some massaging and cleaning-up of paths.
   *
   * @param pathResolver
   * @param pathShortener used to shorten the {@code -I} and {@code -isystem} paths
   * @param frameworkPathTransformer used to shorten/convert/transmogrify framework {@code -F} paths
   */
  public ImmutableList<String> getFlags(
      SourcePathResolver pathResolver,
      PathShortener pathShortener,
      Function<FrameworkPath, Path> frameworkPathTransformer,
      Preprocessor preprocessor) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    builder.addAll(
        CxxHeaders.getArgs(getIPaths(), pathResolver, Optional.of(pathShortener), preprocessor));

    builder.addAll(
        MoreIterables.zipAndConcat(
            Iterables.cycle("-F"),
            FluentIterable.from(getFPaths())
                .transform(frameworkPathTransformer)
                .transform(Object::toString)
                .toSortedSet(Ordering.natural())));

    return builder.build();
  }

  public CxxToolFlags toToolFlags(
      SourcePathResolver resolver,
      PathShortener pathShortener,
      Function<FrameworkPath, Path> frameworkPathTransformer,
      Preprocessor preprocessor) {
    return CxxToolFlags.explicitBuilder()
        .addAllRuleFlags(getFlags(resolver, pathShortener, frameworkPathTransformer, preprocessor))
        .build();
  }

  /**
   * Build a list of compiler flag strings representing the contained paths.
   *
   * <p>Paths are inserted into the compiler flag list as-is, without transformation or shortening.
   */
  public ImmutableList<String> getFlags(
      SourcePathResolver pathResolver, Preprocessor preprocessor) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.addAll(CxxHeaders.getArgs(getIPaths(), pathResolver, Optional.empty(), preprocessor));
    // TODO(steveo) gotta handle framework paths!
    return builder.build();
  }

  public CxxToolFlags toToolFlags(SourcePathResolver resolver, Preprocessor preprocessor) {
    return CxxToolFlags.explicitBuilder().addAllRuleFlags(getFlags(resolver, preprocessor)).build();
  }
}
