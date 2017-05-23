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

package com.facebook.buck.log;

import com.facebook.buck.model.BuildId;
import com.facebook.buck.util.DirectoryCleaner;
import com.facebook.buck.util.Verbosity;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.Lists;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import javax.annotation.Nullable;

public class GlobalStateManager {
  private static final Logger LOG = Logger.get(GlobalStateManager.class);

  private static final GlobalStateManager SINGLETON = new GlobalStateManager();
  private static final String DEFAULT_LOG_FILE_WRITER_KEY = "DEFAULT";
  private static final DirectoryCleaner LOG_FILE_DIR_CLEANER = LogFileHandler.newCleaner();

  // Shared global state.
  private final ConcurrentMap<Long, String> threadIdToCommandId;

  // Global state required by the ConsoleHandler.
  private final ConcurrentMap<String, ConsoleHandlerState.Writer> commandIdToConsoleHandlerWriter;
  private final ConcurrentMap<String, Level> commandIdToConsoleHandlerLevel;

  // Global state required by the LogFileHandler.
  private final ConcurrentMap<String, java.io.Writer> commandIdToLogFileHandlerWriter;
  private final ConcurrentMap<String, Boolean> commandIdToIsSuperconsoleEnabled;
  private final ConcurrentMap<String, Boolean> commandIdToIsDaemon;

  public static GlobalStateManager singleton() {
    return SINGLETON;
  }

  public GlobalStateManager() {
    this.threadIdToCommandId = new ConcurrentHashMap<>();
    this.commandIdToConsoleHandlerWriter = new ConcurrentHashMap<>();
    this.commandIdToConsoleHandlerLevel = new ConcurrentHashMap<>();
    this.commandIdToLogFileHandlerWriter = new ConcurrentHashMap<>();
    this.commandIdToIsSuperconsoleEnabled = new ConcurrentHashMap<>();
    this.commandIdToIsDaemon = new ConcurrentHashMap<>();

    ReferenceCountedWriter defaultWriter =
        createReferenceCountedWriter(
            InvocationInfo.of(
                    new BuildId(),
                    false,
                    false,
                    "launch",
                    new String[0],
                    new String[0],
                    LogConfigSetup.DEFAULT_SETUP.getLogDir())
                .getLogFilePath());
    putReferenceCountedWriter(DEFAULT_LOG_FILE_WRITER_KEY, defaultWriter);
  }

  public LoggerIsMappedToThreadScope setupLoggers(
      final InvocationInfo info,
      OutputStream consoleHandlerStream,
      final OutputStream consoleHandlerOriginalStream,
      final Verbosity consoleHandlerVerbosity) {
    final long threadId = Thread.currentThread().getId();
    final String commandId = info.getCommandId();

    ReferenceCountedWriter defaultWriter = createReferenceCountedWriter(info.getLogFilePath());
    ReferenceCountedWriter newWriter = defaultWriter.newReference();
    // Put defaultWriter to map only after newWriter has been created. Otherwise defaultWriter may
    // get closed before newWriter was created due to concurrency.
    putReferenceCountedWriter(DEFAULT_LOG_FILE_WRITER_KEY, defaultWriter);
    putReferenceCountedWriter(commandId, newWriter);
    createUserFriendlySymLink(info);

    // Setup the shared state.
    threadIdToCommandId.putIfAbsent(threadId, commandId);

    // Setup the ConsoleHandler state.
    commandIdToConsoleHandlerWriter.put(
        commandId, ConsoleHandler.utf8OutputStreamWriter(consoleHandlerStream));
    if (Verbosity.ALL.equals(consoleHandlerVerbosity)) {
      commandIdToConsoleHandlerLevel.put(commandId, Level.ALL);
    }
    commandIdToIsSuperconsoleEnabled.put(commandId, info.getSuperConsoleEnabled());
    commandIdToIsDaemon.put(commandId, info.getIsDaemon());

    return new LoggerIsMappedToThreadScope() {
      @Override
      public Closeable setWriter(ConsoleHandlerState.Writer writer) {
        final ConsoleHandlerState.Writer previousWriter =
            commandIdToConsoleHandlerWriter.get(commandId);
        commandIdToConsoleHandlerWriter.put(commandId, writer);
        return () -> commandIdToConsoleHandlerWriter.put(commandId, previousWriter);
      }

      @Override
      public void close() {
        // Tear down the LogFileHandler state.
        removeReferenceCountedWriter(commandId);

        // Tear down the ConsoleHandler state.
        commandIdToConsoleHandlerWriter.put(
            commandId, ConsoleHandler.utf8OutputStreamWriter(consoleHandlerOriginalStream));
        commandIdToConsoleHandlerLevel.remove(commandId);
        commandIdToIsSuperconsoleEnabled.remove(commandId);
        commandIdToIsDaemon.remove(commandId);

        // Tear down the shared state.
        // NOTE: Avoid iterator in case there's a concurrent change to this map.
        List<Long> allKeys = Lists.newArrayList(threadIdToCommandId.keySet());
        for (Long threadId1 : allKeys) {
          if (commandId.equals(threadIdToCommandId.get(threadId1))) {
            threadIdToCommandId.remove(threadId1);
          }
        }

        try {
          LOG_FILE_DIR_CLEANER.clean(info.getLogDirectoryPath().getParent());
        } catch (IOException e) {
          LOG.info(e, "It's possible another concurrent buck command removed the file.");
        }
      }
    };
  }

  private void createUserFriendlySymLink(final InvocationInfo info) {
    try {
      String symlinkName = "last_" + info.getSubCommand();
      Path symlinkPath = info.getBuckLogDir().resolve(symlinkName);
      Files.deleteIfExists(symlinkPath);
      if (Platform.detect() != Platform.WINDOWS) {
        Files.createSymbolicLink(symlinkPath, info.getLogDirectoryPath().toAbsolutePath());
      }
    } catch (IOException e) {
      LOG.info(e, "Failed to create a user friendly symlink to logs dir for the last command.");
    }
  }

  private void removeReferenceCountedWriter(String commandId) {
    putReferenceCountedWriter(commandId, null);
  }

  private void putReferenceCountedWriter(
      String commandId, @Nullable ReferenceCountedWriter newWriter) {
    try {
      java.io.Writer oldWriter;
      if (newWriter == null) {
        oldWriter = commandIdToLogFileHandlerWriter.remove(commandId);
      } else {
        oldWriter = commandIdToLogFileHandlerWriter.put(commandId, newWriter);
      }
      if (oldWriter != null) {
        oldWriter.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(String.format("Exception closing writer [%s].", commandId), e);
    }
  }

  private ReferenceCountedWriter createReferenceCountedWriter(Path logFilePath) {
    try {
      Files.createDirectories(logFilePath.getParent());
      return new ReferenceCountedWriter(
          new OutputStreamWriter(new FileOutputStream(logFilePath.toString()), "UTF-8"));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(String.format("Could not create file [%s].", logFilePath), e);
    } catch (IOException e) {
      throw new RuntimeException(String.format("Exception wrapping file [%s].", logFilePath), e);
    }
  }

  public CommonThreadFactoryState getThreadToCommandRegister() {
    return new CommonThreadFactoryState() {
      @Override
      public String threadIdToCommandId(long threadId) {
        return threadIdToCommandId.get(threadId);
      }

      @Override
      public void register(long threadId, String commandId) {
        threadIdToCommandId.put(threadId, commandId);
      }
    };
  }

  public ConsoleHandlerState getConsoleHandlerState() {
    return new ConsoleHandlerState() {
      @Override
      public Writer getWriter(String commandId) {
        return commandIdToConsoleHandlerWriter.get(commandId);
      }

      @Override
      public Iterable<Writer> getAllAvailableWriters() {
        return commandIdToConsoleHandlerWriter.values();
      }

      @Override
      public Level getLogLevel(String commandId) {
        return commandIdToConsoleHandlerLevel.get(commandId);
      }

      @Override
      public String threadIdToCommandId(long threadId) {
        return threadIdToCommandId.get(threadId);
      }
    };
  }

  public ThreadIdToCommandIdMapper getThreadIdToCommandIdMapper() {
    return threadIdToCommandId::get;
  }

  public CommandIdToIsDaemonMapper getCommandIdToIsDaemonMapper() {
    return commandIdToIsDaemon::get;
  }

  public CommandIdToIsSuperConsoleEnabledMapper getCommandIdToIsSuperConsoleEnabledMapper() {
    return commandIdToIsSuperconsoleEnabled::get;
  }

  /**
   * Writers obtained by {@link LogFileHandlerState#getWriters} must not be closed! This class
   * manages their lifetime.
   */
  public LogFileHandlerState getLogFileHandlerState() {
    return new LogFileHandlerState() {
      @Override
      public Iterable<java.io.Writer> getWriters(@Nullable String commandId) {
        if (commandId == null) {
          return commandIdToLogFileHandlerWriter.values();
        }

        java.io.Writer writer = commandIdToLogFileHandlerWriter.get(commandId);
        if (writer != null) {
          return Collections.singleton(writer);
        } else {
          return commandIdToLogFileHandlerWriter.values();
        }
      }

      @Override
      public String threadIdToCommandId(long threadId) {
        return threadIdToCommandId.get(threadId);
      }
    };
  }

  /**
   * Since this is a Singleton class, make sure it cleans after itself once it's GC'ed.
   *
   * @exception IOException if an I/O error occurs.
   */
  @Override
  protected void finalize() throws IOException {
    // Close off any log file writers that may still be hanging about.
    List<String> allKeys = Lists.newArrayList(commandIdToLogFileHandlerWriter.keySet());
    for (String commandId : allKeys) {
      removeReferenceCountedWriter(commandId);
    }
    // Close off any console writers that may still be hanging about.
    for (ConsoleHandlerState.Writer writer : commandIdToConsoleHandlerWriter.values()) {
      try {
        writer.close();
      } catch (IOException e) {
        // Keep going through all the writers even if one fails.
        LOG.error(e, "Failed to cleanly close() and OutputStreamWriter.");
      }
    }
  }

  public interface LoggerIsMappedToThreadScope extends AutoCloseable {
    Closeable setWriter(ConsoleHandlerState.Writer writer);

    @Override
    void close();
  }
}
