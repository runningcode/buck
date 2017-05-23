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
package com.facebook.buck.event;

import com.facebook.buck.event.external.events.ProgressEventInterface;

public abstract class ProgressEvent extends AbstractBuckEvent implements ProgressEventInterface {

  protected final double progressValue;

  protected ProgressEvent(double progressValue) {
    super(EventKey.unique());
    this.progressValue = progressValue;
  }

  public static ParsingProgressUpdated parsingProgressUpdated(double v) {
    return new ParsingProgressUpdated(v);
  }

  public static ProjectGenerationProgressUpdated projectGenerationProgressUpdated(double v) {
    return new ProjectGenerationProgressUpdated(v);
  }

  public static BuildProgressUpdated buildProgressUpdated(double v) {
    return new BuildProgressUpdated(v);
  }

  @Override
  protected String getValueString() {
    return "progress=" + String.valueOf(progressValue);
  }

  @Override
  public double getProgressValue() {
    return progressValue;
  }

  public static class ParsingProgressUpdated extends ProgressEvent {
    public ParsingProgressUpdated(double progress) {
      super(progress);
    }

    @Override
    public String getEventName() {
      return PARSING_PROGRESS_UPDATED;
    }
  }

  public static class ProjectGenerationProgressUpdated extends ProgressEvent {
    public ProjectGenerationProgressUpdated(double progress) {
      super(progress);
    }

    @Override
    public String getEventName() {
      return PROJECT_GENERATION_PROGRESS_UPDATED;
    }
  }

  public static class BuildProgressUpdated extends ProgressEvent {
    public BuildProgressUpdated(double progress) {
      super(progress);
    }

    @Override
    public String getEventName() {
      return BUILD_PROGRESS_UPDATED;
    }
  }
}
