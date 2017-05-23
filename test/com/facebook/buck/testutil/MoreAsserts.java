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

package com.facebook.buck.testutil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.util.MoreCollectors;
import com.facebook.buck.util.RichStream;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Assert;

/** Additional assertions that delegate to JUnit assertions, but with better error messages. */
public final class MoreAsserts {

  private static final int BUFFER_SIZE = 8 * 1024;

  private MoreAsserts() {}

  /**
   * Asserts that two sets have the same contents. On failure, prints a readable diff of the two
   * sets for easy debugging.
   */
  public static <E> void assertSetEquals(Set<E> expected, Set<E> actual) {
    Set<E> missing = Sets.difference(expected, actual);
    Set<E> extra = Sets.difference(actual, expected);
    boolean setsEqual = missing.isEmpty() && extra.isEmpty();
    Assert.assertTrue(
        String.format("%nMissing elements:%n%s%nExtraneous elements:%n%s", missing, extra),
        setsEqual);
  }

  /** @see #assertIterablesEquals(Iterable, Iterable) */
  public static <T extends List<?>> void assertListEquals(List<?> expected, List<?> observed) {
    assertIterablesEquals(expected, observed);
  }

  /** @see #assertIterablesEquals(String, Iterable, Iterable) */
  public static <T extends List<?>> void assertListEquals(
      String userMessage, List<?> expected, List<?> observed) {
    assertIterablesEquals(userMessage, expected, observed);
  }

  /**
   * Equivalent to {@link org.junit.Assert#assertEquals(Object, Object)} except if the assertion
   * fails, the message includes information about where the iterables differ.
   */
  public static <T extends Iterable<?>> void assertIterablesEquals(
      Iterable<?> expected, Iterable<?> observed) {
    assertIterablesEquals("" /* userMessage */, expected, observed);
  }

  /**
   * Equivalent to {@link org.junit.Assert#assertEquals(String, Object, Object)} except if the
   * assertion fails, the message includes information about where the iterables differ.
   */
  public static <T extends Iterable<?>> void assertIterablesEquals(
      String userMessage, Iterable<?> expected, Iterable<?> observed) {
    // The traditional assertEquals() method should be fine if either List is null.
    if (expected == null || observed == null) {
      assertEquals(userMessage, expected, observed);
      return;
    }

    String errmsgPart =
        String.format(
            "expected:[%s] observed:[%s]",
            Joiner.on(", ").join(expected), Joiner.on(", ").join(observed));

    // Compare each item in the list, one at a time.
    Iterator<?> expectedIter = expected.iterator();
    Iterator<?> observedIter = observed.iterator();
    int index = 0;
    while (expectedIter.hasNext()) {
      if (!observedIter.hasNext()) {
        fail(
            prefixWithUserMessage(
                userMessage,
                "Item "
                    + index
                    + " does not exist in the "
                    + "observed list ("
                    + errmsgPart
                    + "): "
                    + expectedIter.next()));
      }
      Object expectedItem = expectedIter.next();
      Object observedItem = observedIter.next();
      assertEquals(
          prefixWithUserMessage(
              userMessage, "Item " + index + " in the lists should match (" + errmsgPart + ")."),
          expectedItem,
          observedItem);
      ++index;
    }
    if (observedIter.hasNext()) {
      fail(
          prefixWithUserMessage(
              userMessage,
              "Extraneous item %s in the observed list (" + errmsgPart + "): %s.",
              index,
              observedIter.next()));
    }
  }

  public static <Item, Container extends Iterable<Item>> void assertContainsOne(
      Container container, Item expectedItem) {
    assertContainsOne(/* userMessage */ Iterables.toString(container), container, expectedItem);
  }

  public static <Item, Container extends Iterable<Item>> void assertContainsOne(
      String userMessage, Container container, Item expectedItem) {
    int seen = 0;
    for (Item item : container) {
      if (expectedItem.equals(item)) {
        seen++;
      }
    }
    if (seen < 1) {
      failWith(
          userMessage,
          "Item '" + expectedItem + "' not found in container, " + "expected to find one.");
    }
    if (seen > 1) {
      failWith(
          userMessage,
          "Found "
              + Integer.valueOf(seen)
              + " occurrences of '"
              + expectedItem
              + "' in container, expected to find only one.");
    }
  }

  /**
   * Asserts that every {@link com.facebook.buck.step.Step} in the observed list is a {@link
   * com.facebook.buck.shell.ShellStep} whose shell command arguments match those of the
   * corresponding entry in the expected list.
   */
  public static void assertShellCommands(
      String userMessage, List<String> expected, List<Step> observed, ExecutionContext context) {
    Iterator<String> expectedIter = expected.iterator();
    Iterator<Step> observedIter = observed.iterator();
    Joiner joiner = Joiner.on(" ");
    while (expectedIter.hasNext() && observedIter.hasNext()) {
      String expectedShellCommand = expectedIter.next();
      Step observedStep = observedIter.next();
      if (!(observedStep instanceof ShellStep)) {
        failWith(userMessage, "Observed command must be a shell command: " + observedStep);
      }
      ShellStep shellCommand = (ShellStep) observedStep;
      String observedShellCommand = joiner.join(shellCommand.getShellCommand(context));
      assertEquals(userMessage, expectedShellCommand, observedShellCommand);
    }

    if (expectedIter.hasNext()) {
      failWith(userMessage, "Extra expected command: " + expectedIter.next());
    }

    if (observedIter.hasNext()) {
      failWith(userMessage, "Extra observed command: " + observedIter.next());
    }
  }

  /**
   * Invokes the {@link Step#getDescription(ExecutionContext)} method on each of the observed steps
   * to create a list of strings and compares it to the expected value.
   */
  public static void assertSteps(
      String userMessage,
      List<String> expectedStepDescriptions,
      List<Step> observedSteps,
      final ExecutionContext executionContext) {
    ImmutableList<String> commands =
        observedSteps
            .stream()
            .map(step -> step.getDescription(executionContext))
            .collect(MoreCollectors.toImmutableList());
    assertListEquals(userMessage, expectedStepDescriptions, commands);
  }

  public static void assertDepends(String userMessage, BuildRule rule, BuildRule dep) {
    assertDepends(userMessage, rule, dep.getBuildTarget());
  }

  public static void assertDepends(String userMessage, BuildRule rule, BuildTarget dep) {
    assertDepends(userMessage, rule.getBuildDeps(), dep);
  }

  public static void assertDepends(
      String userMessage, Collection<BuildRule> ruleDeps, BuildTarget dep) {
    for (BuildRule realDep : ruleDeps) {
      BuildTarget target = realDep.getBuildTarget();
      if (target.equals(dep)) {
        return;
      }
    }
    fail(userMessage);
  }

  public static <T> void assertOptionalValueEquals(
      String userMessage, T expectedValue, Optional<T> optionalValue) {
    if (!optionalValue.isPresent()) {
      failWith(userMessage, "Optional value is not present.");
    }

    assertEquals(userMessage, expectedValue, optionalValue.get());
  }

  public static void assertContentsEqual(Path one, Path two) throws IOException {
    Preconditions.checkNotNull(one);
    Preconditions.checkNotNull(two);

    if (one.equals(two)) {
      return;
    }

    if (Files.size(one) != Files.size(two)) {
      fail(
          String.format(
              "File sizes differ: %s (%d bytes), %s (%d bytes)",
              one, Files.size(one), two, Files.size(two)));
    }

    try (InputStream ois = Files.newInputStream(one);
        InputStream tis = Files.newInputStream(two)) {
      byte[] bo = new byte[BUFFER_SIZE];
      byte[] bt = new byte[BUFFER_SIZE];

      while (true) {
        int read1 = ByteStreams.read(ois, bo, 0, BUFFER_SIZE);
        int read2 = ByteStreams.read(tis, bt, 0, BUFFER_SIZE);
        if (read1 != read2 || !Arrays.equals(bo, bt)) {
          fail(String.format("Contents of files differ: %s, %s", one, two));
        } else if (read1 != BUFFER_SIZE) {
          return;
        }
      }
    }
  }

  /**
   * Asserts that two strings are equal, but compares them in chunks so that Intellij will show the
   * diffs when the assertion fails.
   */
  public static void assertLargeStringsEqual(String expected, String content) {
    List<String> expectedChunks = chunkify(expected);
    List<String> contentChunks = chunkify(content);

    for (int i = 0; i < Math.min(expectedChunks.size(), contentChunks.size()); i++) {
      assertEquals("Failed at index: " + i, expectedChunks.get(i), contentChunks.get(i));
    }
    // We could check this first, but it's usually more useful to see the first difference than to
    // just see that the two strings are different length.
    assertEquals(expectedChunks.size(), contentChunks.size());
  }

  private static List<String> chunkify(String data) {
    return RichStream.from(Iterables.partition(Arrays.asList(data.split("\\n")), 1000))
        .map((l) -> Joiner.on("\n").join(l))
        .toImmutableList();
  }

  private static String prefixWithUserMessage(
      @Nullable String userMessage, String message, Object... formatArgs) {
    return (userMessage == null ? "" : userMessage + " ") + String.format(message, formatArgs);
  }

  private static void failWith(@Nullable String userMessage, String message) {
    fail(prefixWithUserMessage(userMessage, message));
  }
}
