/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.util.versioncontrol;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.log.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

public class VersionControlStatsGenerator {

  private static final Logger LOG = Logger.get(VersionControlStatsGenerator.class);

  /**
   * Modes the generator can get stats in, in order from least comprehensive to most comprehensive.
   * Each mode should include all the information present in the previous one.
   */
  public enum Mode {
    /** Do not generate new information, but return whatever is already generated */
    PREGENERATED,
    /** Generate a set of stats that is fast to generate but incomplete */
    FAST,
    /** Generate the full set of stats */
    FULL,
    ;
  }

  private static final String REMOTE_MASTER = "remote/master";
  private static final ImmutableSet<String> TRACKED_BOOKMARKS =
      ImmutableSet.of(
          REMOTE_MASTER);

  private final VersionControlCmdLineInterface versionControlCmdLineInterface;

  private final Optional<FastVersionControlStats> pregeneratedVersionControlStats;

  @GuardedBy("this")
  @Nullable
  private FastVersionControlStats fastStats;

  @GuardedBy("this")
  @Nullable
  private ImmutableSet<String> changedFiles;

  @GuardedBy("this")
  @Nullable
  private Optional<String> diff;

  public VersionControlStatsGenerator(
      VersionControlCmdLineInterface versionControlCmdLineInterface,
      Optional<FastVersionControlStats> pregeneratedVersionControlStats) {
    this.versionControlCmdLineInterface = versionControlCmdLineInterface;
    this.pregeneratedVersionControlStats = pregeneratedVersionControlStats;
    pregeneratedVersionControlStats.ifPresent(
        x ->
            this.fastStats =
                FastVersionControlStats.of(
                    x.getCurrentRevisionId(),
                    x.getBaseBookmarks(),
                    x.getBranchedFromMasterRevisionId(),
                    x.getBranchedFromMasterTS()));
  }

  public void generateStatsAsync(
      Mode mode, ExecutorService executorService, BuckEventBus buckEventBus) {
    executorService.submit(
        () -> {
          try {
            Optional<FullVersionControlStats> versionControlStats;
            try (SimplePerfEvent.Scope ignored =
                SimplePerfEvent.scope(buckEventBus, "gen_source_control_info")) {
              versionControlStats = generateStats(mode);
            }
            versionControlStats.ifPresent(x -> buckEventBus.post(new VersionControlStatsEvent(x)));
          } catch (InterruptedException e) {
            LOG.warn(e, "Failed to generate VC stats due to being interrupted. Skipping..");
            Thread.currentThread().interrupt(); // Re-set interrupt flag
          }
        });
  }

  public synchronized Optional<FullVersionControlStats> generateStats(Mode mode)
      throws InterruptedException {
    if (mode == Mode.PREGENERATED) {
      return pregeneratedVersionControlStats.map(
          x -> FullVersionControlStats.builder().from(x).build());
    }

    FullVersionControlStats versionControlStats = null;
    LOG.info("Starting generation of version control stats.");
    if (!versionControlCmdLineInterface.isSupportedVersionControlSystem()) {
      LOG.warn("Skipping generation of version control stats as unsupported repository type.");
    } else {
      FullVersionControlStats.Builder versionControlStatsBuilder =
          FullVersionControlStats.builder();
      try {
        if (fastStats == null) {
          fastStats = versionControlCmdLineInterface.fastVersionControlStats();
        }
        versionControlStatsBuilder.setCurrentRevisionId(fastStats.getCurrentRevisionId());
        versionControlStatsBuilder.setBaseBookmarks(
            Sets.intersection(fastStats.getBaseBookmarks(), TRACKED_BOOKMARKS));
        versionControlStatsBuilder.setBranchedFromMasterRevisionId(
            fastStats.getBranchedFromMasterRevisionId());
        versionControlStatsBuilder.setBranchedFromMasterTS(fastStats.getBranchedFromMasterTS());
        if (mode == Mode.FULL) {
          // Prepopulate as much as possible before trying to query the VCS: this way if it fails
          // we still have this information.
          if (changedFiles != null) {
            versionControlStatsBuilder.setPathsChangedInWorkingDirectory(changedFiles);
          }
          if (diff != null) {
            versionControlStatsBuilder.setDiff(diff);
          }
          if (changedFiles == null) {
            changedFiles =
                versionControlCmdLineInterface.changedFiles(
                    fastStats.getBranchedFromMasterRevisionId());
            versionControlStatsBuilder.setPathsChangedInWorkingDirectory(changedFiles);
          }
          if (diff == null) {
            diff =
                versionControlCmdLineInterface.diffBetweenRevisionsOrAbsent(
                    fastStats.getBranchedFromMasterRevisionId(), fastStats.getCurrentRevisionId());
            versionControlStatsBuilder.setDiff(diff);
          }
        }
      } catch (VersionControlCommandFailedException e) {
        LOG.warn("Failed to gather some source control stats.");
      }
      versionControlStats = versionControlStatsBuilder.build();
      LOG.info("Stats generated successfully. \n%s", versionControlStats);
    }
    return Optional.ofNullable(versionControlStats);
  }
}
