/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.step;

import com.facebook.buck.event.AbstractBuckEvent;
import com.facebook.buck.event.EventKey;
import com.facebook.buck.event.LeafEvent;
import com.facebook.buck.event.WorkAdvanceEvent;
import com.facebook.buck.event.external.events.StepEventExternalInterface;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;
import java.util.UUID;

/** Base class for events about steps. */
public abstract class StepEvent extends AbstractBuckEvent
    implements LeafEvent, StepEventExternalInterface, WorkAdvanceEvent {

  private final String shortName;
  private final String description;

  @JsonIgnore private final UUID uuid;

  protected StepEvent(String shortName, String description, UUID uuid) {
    super(EventKey.slowValueKey("StepEvent", uuid));
    this.shortName = shortName;
    this.description = description;
    this.uuid = uuid;
  }

  @Override
  public String getShortStepName() {
    return shortName;
  }

  @Override
  public String getDescription() {
    return description;
  }

  protected UUID getUuid() {
    return uuid;
  }

  @Override
  public String getCategory() {
    return getShortStepName();
  }

  @Override
  protected String getValueString() {
    return getShortStepName();
  }

  public static Started started(String shortName, String description, UUID uuid) {
    return new Started(shortName, description, uuid);
  }

  public static Finished finished(Started started, int exitCode) {
    return new Finished(started, exitCode);
  }

  public static class Started extends StepEvent {
    protected Started(String shortName, String description, UUID uuid) {
      super(shortName, description, uuid);
    }

    @Override
    public String getEventName() {
      return STEP_STARTED;
    }
  }

  public static class Finished extends StepEvent {
    private final int exitCode;

    protected Finished(Started started, int exitCode) {
      super(started.getShortStepName(), started.getDescription(), started.getUuid());
      this.exitCode = exitCode;
    }

    public int getExitCode() {
      return exitCode;
    }

    @Override
    public String getEventName() {
      return STEP_FINISHED;
    }

    @Override
    public boolean equals(Object o) {
      if (!super.equals(o)) {
        return false;
      }
      // Because super.equals compares the EventKey, getting here means that we've somehow managed
      // to create 2 Finished events for the same Started event.
      throw new UnsupportedOperationException("Multiple conflicting Finished events detected.");
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(super.hashCode(), exitCode);
    }
  }
}
