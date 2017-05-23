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

package com.facebook.buck.parser;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.facebook.buck.json.ProjectBuildFileParser;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.util.concurrent.AssertScopeExclusiveAccess;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.hamcrest.Matchers;
import org.junit.Test;

public class ProjectBuildFileParserPoolTest {

  private void assertHowManyParserInstancesAreCreated(
      ListeningExecutorService executorService,
      final int maxParsers,
      int numRequests,
      int expectedCreateCount)
      throws Exception {
    final AtomicInteger createCount = new AtomicInteger(0);
    Cell cell = EasyMock.createMock(Cell.class);

    final CountDownLatch createParserLatch = new CountDownLatch(expectedCreateCount);
    try (ProjectBuildFileParserPool parserPool =
        new ProjectBuildFileParserPool(
            maxParsers,
            input -> {
              createCount.incrementAndGet();
              return createMockParser(
                  () -> {
                    createParserLatch.countDown();
                    boolean didntTimeout = false;
                    try {
                      didntTimeout = createParserLatch.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                      Throwables.throwIfUnchecked(e);
                      throw new RuntimeException(e);
                    }
                    assertThat(didntTimeout, Matchers.equalTo(true));
                    return ImmutableList.of();
                  });
            })) {

      Futures.allAsList(scheduleWork(cell, parserPool, executorService, numRequests)).get();
      assertThat(createCount.get(), Matchers.equalTo(expectedCreateCount));
    }
  }

  @Test
  public void createsConstrainedNumberOfParsers() throws Exception {
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2));
    assertHowManyParserInstancesAreCreated(
        /* executor */ executorService,
        /* maxParsers */ 2,
        /* requests */ 3,
        /* expectedCreateCount */ 2);
  }

  @Test
  public void doesntCreateParsersWhenNotNecessary() throws Exception {
    // The direct executor will do the "parsing" as the lease is obtained and therefore we should
    // never need more than one to be created.
    assertHowManyParserInstancesAreCreated(
        /* executor */ MoreExecutors.newDirectExecutorService(),
        /* maxParsers */ 2,
        /* requests */ 3,
        /* expectedCreateCount */ 1);
  }

  @Test
  public void closesCreatedParsers() throws Exception {
    final int parsersCount = 4;
    final AtomicInteger parserCount = new AtomicInteger(0);
    Cell cell = EasyMock.createMock(Cell.class);
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(parsersCount));

    final CountDownLatch createParserLatch = new CountDownLatch(parsersCount);
    try (ProjectBuildFileParserPool parserPool =
        new ProjectBuildFileParserPool(
            parsersCount,
            input -> {
              parserCount.incrementAndGet();

              final ProjectBuildFileParser parser =
                  EasyMock.createMock(ProjectBuildFileParser.class);
              try {
                EasyMock.expect(
                        parser.getAllRulesAndMetaRules(
                            EasyMock.anyObject(Path.class), EasyMock.anyObject(AtomicLong.class)))
                    .andAnswer(
                        () -> {
                          createParserLatch.countDown();
                          createParserLatch.await();

                          return ImmutableList.of();
                        })
                    .anyTimes();
                parser.close();
                EasyMock.expectLastCall()
                    .andAnswer(
                        new IAnswer<Void>() {
                          @Override
                          public Void answer() throws Throwable {
                            parserCount.decrementAndGet();
                            return null;
                          }
                        });
              } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
              }
              EasyMock.replay(parser);
              return parser;
            })) {

      Futures.allAsList(scheduleWork(cell, parserPool, executorService, parsersCount * 2)).get();
      assertThat(parserCount.get(), Matchers.is(4));
    } finally {
      executorService.shutdown();
    }

    // Parser shutdown is async.
    for (int i = 0; i < 10; ++i) {
      if (parserCount.get() == 0) {
        break;
      }
      Thread.sleep(100);
    }
    assertThat(parserCount.get(), Matchers.is(0));
  }

  @Test
  public void fuzzForConcurrentAccess() throws Exception {
    final int parsersCount = 3;
    Cell cell = EasyMock.createMock(Cell.class);
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4));

    try (ProjectBuildFileParserPool parserPool =
        new ProjectBuildFileParserPool(
            parsersCount,
            input -> {
              final AtomicInteger sleepCallCount = new AtomicInteger(0);
              return createMockParser(
                  () -> {
                    int numCalls = sleepCallCount.incrementAndGet();
                    Preconditions.checkState(numCalls == 1);
                    try {
                      Thread.sleep(10);
                    } finally {
                      sleepCallCount.decrementAndGet();
                    }
                    return ImmutableList.of();
                  });
            })) {

      Futures.allAsList(scheduleWork(cell, parserPool, executorService, 142)).get();
    } finally {
      executorService.shutdown();
    }
  }

  @Test
  public void ignoresCancellation() throws Exception {
    Cell cell = EasyMock.createMock(Cell.class);
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

    int numberOfJobs = 5;
    final CountDownLatch waitTillAllWorkIsDone = new CountDownLatch(numberOfJobs);
    final CountDownLatch waitTillCanceled = new CountDownLatch(1);
    try (ProjectBuildFileParserPool parserPool =
        new ProjectBuildFileParserPool(
            /* maxParsers */ 1,
            createMockParserFactory(
                () -> {
                  waitTillCanceled.await();
                  waitTillAllWorkIsDone.countDown();
                  return ImmutableList.of();
                }))) {

      ImmutableSet<ListenableFuture<?>> futures =
          scheduleWork(cell, parserPool, executorService, numberOfJobs);
      for (ListenableFuture<?> future : futures) {
        future.cancel(true);
      }
      waitTillCanceled.countDown();
      // We're making sure cancel is ignored by the pool by waiting for the supposedly canceled
      // work to go through.
      waitTillAllWorkIsDone.await(1, TimeUnit.SECONDS);

    } finally {
      executorService.shutdown();
    }
  }

  @Test
  public void closeWhenRunningJobs() throws Exception {
    Cell cell = EasyMock.createMock(Cell.class);
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

    final CountDownLatch waitTillClosed = new CountDownLatch(1);
    final CountDownLatch firstJobRunning = new CountDownLatch(1);
    final AtomicInteger postCloseWork = new AtomicInteger(0);
    ImmutableSet<ListenableFuture<?>> futures;

    try (ProjectBuildFileParserPool parserPool =
        new ProjectBuildFileParserPool(
            /* maxParsers */ 1,
            createMockParserFactory(
                () -> {
                  firstJobRunning.countDown();
                  waitTillClosed.await();
                  return ImmutableList.of();
                }))) {

      futures = scheduleWork(cell, parserPool, executorService, 5);
      for (ListenableFuture<?> future : futures) {
        Futures.addCallback(
            future,
            new FutureCallback<Object>() {
              @Override
              public void onSuccess(@Nullable Object result) {
                postCloseWork.incrementAndGet();
              }

              @Override
              public void onFailure(Throwable t) {}
            });
      }
      firstJobRunning.await(1, TimeUnit.SECONDS);
    }
    waitTillClosed.countDown();

    List<Object> futureResults = Futures.successfulAsList(futures).get(1, TimeUnit.SECONDS);

    // The threadpool is of size 1, so we had 1 job in the 'running' state. That one job completed
    // normally, the rest should have been cancelled.
    int expectedCompletedJobs = 1;
    int completedJobs = FluentIterable.from(futureResults).filter(Objects::nonNull).size();
    assertThat(completedJobs, Matchers.equalTo(expectedCompletedJobs));

    executorService.shutdown();
    assertThat(executorService.awaitTermination(1, TimeUnit.SECONDS), Matchers.is(true));
    assertThat(postCloseWork.get(), Matchers.equalTo(expectedCompletedJobs));
  }

  @Test
  public void workThatThrows() throws Exception {
    Cell cell = EasyMock.createMock(Cell.class);
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));

    final String exceptionMessage = "haha!";
    final AtomicBoolean throwWhileParsing = new AtomicBoolean(true);
    try (ProjectBuildFileParserPool parserPool =
        new ProjectBuildFileParserPool(
            /* maxParsers */ 2,
            createMockParserFactory(
                () -> {
                  if (throwWhileParsing.get()) {
                    throw new Exception(exceptionMessage);
                  }
                  return ImmutableList.of();
                }))) {

      ImmutableSet<ListenableFuture<?>> failedWork =
          scheduleWork(cell, parserPool, executorService, 5);
      for (ListenableFuture<?> failedFuture : failedWork) {
        try {
          failedFuture.get();
          fail("Expected ExecutionException to be thrown.");
        } catch (ExecutionException e) {
          assertThat(e.getCause().getMessage(), Matchers.equalTo(exceptionMessage));
        }
      }

      // Make sure it's still possible to do work.
      throwWhileParsing.set(false);
      Futures.allAsList(scheduleWork(cell, parserPool, executorService, 5)).get();
    } finally {
      executorService.shutdown();
    }
  }

  private static ImmutableSet<ListenableFuture<?>> scheduleWork(
      Cell cell,
      ProjectBuildFileParserPool pool,
      ListeningExecutorService executorService,
      int count) {
    ImmutableSet.Builder<ListenableFuture<?>> futures = ImmutableSet.builder();
    for (int i = 0; i < count; i++) {
      futures.add(
          pool.getAllRulesAndMetaRules(cell, Paths.get("BUCK"), new AtomicLong(), executorService));
    }
    return futures.build();
  }

  private ProjectBuildFileParser createMockParser(
      IAnswer<ImmutableList<Map<String, Object>>> parseFn) {
    ProjectBuildFileParser mock = EasyMock.createMock(ProjectBuildFileParser.class);
    try {
      EasyMock.expect(
              mock.getAllRulesAndMetaRules(
                  EasyMock.anyObject(Path.class), EasyMock.anyObject(AtomicLong.class)))
          .andAnswer(parseFn)
          .anyTimes();
      mock.close();
      EasyMock.expectLastCall().andVoid().once();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
    EasyMock.replay(mock);
    return mock;
  }

  private Function<Cell, ProjectBuildFileParser> createMockParserFactory(
      final IAnswer<ImmutableList<Map<String, Object>>> parseFn) {
    return input -> {
      final AssertScopeExclusiveAccess exclusiveAccess = new AssertScopeExclusiveAccess();
      return createMockParser(
          () -> {
            try (AssertScopeExclusiveAccess.Scope scope = exclusiveAccess.scope()) {
              return parseFn.answer();
            }
          });
    };
  }
}
