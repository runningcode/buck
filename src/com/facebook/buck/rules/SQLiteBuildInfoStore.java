/*
 * Copyright 2017-present Facebook, Inc.
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

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import org.sqlite.BusyHandler;

public class SQLiteBuildInfoStore implements BuildInfoStore {
  private final Connection connection;
  private final PreparedStatement selectStmt;
  private final PreparedStatement updateStmt;
  private final PreparedStatement deleteStmt;

  public SQLiteBuildInfoStore(ProjectFilesystem filesystem) throws IOException {
    String dbPath =
        filesystem
            .getRootPath()
            .resolve(filesystem.getBuckPaths().getScratchDir().resolve("metadata.db"))
            .toString();
    try {
      Class.forName("org.sqlite.JDBC");
      connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
      connection.createStatement().executeUpdate("PRAGMA SYNCHRONOUS = OFF");
      connection.createStatement().executeUpdate("PRAGMA JOURNAL_MODE = WAL");
      connection
          .createStatement()
          .executeUpdate(
              "CREATE TABLE IF NOT EXISTS metadata "
                  + "(target TEXT, key TEXT, value TEXT, "
                  + "PRIMARY KEY (target, key)) "
                  + "WITHOUT ROWID");
      selectStmt =
          connection.prepareStatement("SELECT value FROM metadata WHERE target = ? AND key = ?");
      updateStmt =
          connection.prepareStatement(
              "INSERT OR REPLACE INTO metadata (target, key, value) VALUES (?, ?, ?)");
      deleteStmt = connection.prepareStatement("DELETE FROM metadata WHERE target = ?");
      BusyHandler busyHandler =
          new BusyHandler() {
            @Override
            protected int callback(int retries) throws SQLException {
              try {
                Thread.sleep(retries);
              } catch (InterruptedException e) {
                throw new SQLException(e);
              }
              return 1;
            }
          };
      BusyHandler.setHandler(connection, busyHandler);
    } catch (ClassNotFoundException | SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void close() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized Optional<String> readMetadata(BuildTarget buildTarget, String key) {
    try {
      selectStmt.setString(1, buildTarget.getFullyQualifiedName());
      selectStmt.setString(2, key);
      ResultSet rs = selectStmt.executeQuery();
      if (!rs.next()) {
        return Optional.empty();
      }
      String value = rs.getString(1);
      return Optional.of(value);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void updateMetadata(BuildTarget buildTarget, Map<String, String> metadata)
      throws IOException {
    try {
      for (Map.Entry<String, String> e : metadata.entrySet()) {
        updateStmt.setString(1, buildTarget.getFullyQualifiedName());
        updateStmt.setString(2, e.getKey());
        updateStmt.setString(3, e.getValue());
        updateStmt.addBatch();
      }
      updateStmt.executeBatch();
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public synchronized void deleteMetadata(BuildTarget buildTarget) throws IOException {
    try {
      deleteStmt.setString(1, buildTarget.getFullyQualifiedName());
      deleteStmt.executeUpdate();
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }
}
