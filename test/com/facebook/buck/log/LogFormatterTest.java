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

package com.facebook.buck.log;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link LogFormatter}. */
public class LogFormatterTest {
  private ConcurrentHashMap<Long, String> threadIdToCommandId;
  private ThreadIdToCommandIdMapper mapper;

  @Before
  public void setUp() {
    threadIdToCommandId = new ConcurrentHashMap<>();
    mapper = threadId -> threadIdToCommandId.get(threadId);
  }

  @Test
  public void logFormatIncludesMessageAndTimestamp() {
    LogFormatter logFormatter =
        new LogFormatter(mapper, Locale.US, TimeZone.getTimeZone("America/Los_Angeles"));
    threadIdToCommandId.put(64738L, "testCommandId");
    LogRecord record = logRecord(Level.INFO, "Test", "testLogger", 64738, 1409072580000L);
    assertEquals(
        logFormatter.format(record),
        "[2014-08-26 10:03:00.000][info ][command:testCommandId][tid:64738][testLogger] Test\n");
  }

  private static LogRecord logRecord(
      Level level, String message, String loggerName, int tid, long millis) {
    LogRecord result = new LogRecord(level, message);
    result.setLoggerName(loggerName);
    result.setMillis(millis);
    result.setThreadID(tid);
    return result;
  }
}
