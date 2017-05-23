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

package com.facebook.buck.httpserver;

import com.facebook.buck.event.BuckEventListener;
import com.facebook.buck.event.CompilerErrorEvent;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.event.InstallEvent;
import com.facebook.buck.event.ProgressEvent;
import com.facebook.buck.event.ProjectGenerationEvent;
import com.facebook.buck.event.listener.CacheRateStatsKeeper;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.parser.ParseEvent;
import com.facebook.buck.rules.BuildEvent;
import com.facebook.buck.rules.IndividualTestEvent;
import com.facebook.buck.rules.TestRunEvent;
import com.google.common.eventbus.Subscribe;

/**
 * {@link BuckEventListener} that is responsible for reporting events of interest to the {@link
 * StreamingWebSocketServlet}. This class passes high-level objects to the servlet, and the servlet
 * takes responsibility for serializing the objects as JSON down to the client.
 */
public class WebServerBuckEventListener implements BuckEventListener {
  private final StreamingWebSocketServlet streamingWebSocketServlet;

  WebServerBuckEventListener(final WebServer webServer) {
    this.streamingWebSocketServlet = webServer.getStreamingWebSocketServlet();
  }

  @Override
  public void outputTrace(BuildId buildId) {}

  @Subscribe
  public void parseStarted(ParseEvent.Started started) {
    streamingWebSocketServlet.tellClients(started);
  }

  @Subscribe
  public void parseFinished(ParseEvent.Finished finished) {
    streamingWebSocketServlet.tellClients(finished);
  }

  @Subscribe
  public void buildStarted(BuildEvent.Started started) {
    streamingWebSocketServlet.tellClients(started);
  }

  @Subscribe
  public void cacheRateStatsUpdate(
      CacheRateStatsKeeper.CacheRateStatsUpdateEvent cacheRateStatsUpdate) {
    streamingWebSocketServlet.tellClients(cacheRateStatsUpdate);
  }

  @Subscribe
  public void buildFinished(BuildEvent.Finished finished) {
    streamingWebSocketServlet.tellClients(finished);
  }

  @Subscribe
  public void testRunStarted(TestRunEvent.Started event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void testRunCompleted(TestRunEvent.Finished event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void testAwaitingResults(IndividualTestEvent.Started event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void testResultsAvailable(IndividualTestEvent.Finished event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void installEventFinished(InstallEvent.Finished event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void compilerErrorEvent(CompilerErrorEvent event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void consoleEvent(ConsoleEvent event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void buildProgressUpdated(ProgressEvent.BuildProgressUpdated event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void parsingProgressUpdated(ProgressEvent.ParsingProgressUpdated event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void projectGenerationProgressUpdated(
      ProgressEvent.ProjectGenerationProgressUpdated event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void projectGenerationStarted(ProjectGenerationEvent.Started event) {
    streamingWebSocketServlet.tellClients(event);
  }

  @Subscribe
  public void projectGenerationFinished(ProjectGenerationEvent.Finished event) {
    streamingWebSocketServlet.tellClients(event);
  }
}
