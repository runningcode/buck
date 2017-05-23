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

package com.facebook.buck.intellij.ideabuck.config;

import com.facebook.buck.intellij.ideabuck.autodeps.BuckAutoDepsContributor;
import com.facebook.buck.intellij.ideabuck.debugger.AndroidDebugger;
import com.facebook.buck.intellij.ideabuck.file.BuckFileUtil;
import com.facebook.buck.intellij.ideabuck.ui.BuckEventsConsumer;
import com.facebook.buck.intellij.ideabuck.ui.BuckToolWindowFactory;
import com.facebook.buck.intellij.ideabuck.ui.BuckUIManager;
import com.facebook.buck.intellij.ideabuck.ui.utils.BuckPluginNotifications;
import com.facebook.buck.intellij.ideabuck.ws.BuckClientManager;
import com.facebook.buck.intellij.ideabuck.ws.buckevents.BuckEventsHandler;
import com.facebook.buck.intellij.ideabuck.ws.buckevents.consumers.BuckEventsConsumerFactory;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BuckModule implements ProjectComponent {

  private Project mProject;
  private BuckEventsHandler mEventHandler;
  private BuckEventsConsumer mBuckEventsConsumer;
  private AtomicBoolean projectClosed;

  public BuckModule(final Project project) {
    mProject = project;
    mEventHandler =
        new BuckEventsHandler(
            new BuckEventsConsumerFactory(mProject),
            new ExecuteOnBuckPluginConnect(),
            new ExecuteOnBuckPluginDisconnect());
  }

  @Override
  public String getComponentName() {
    return "ideabuck - " + mProject.getName();
  }

  @Override
  public void initComponent() {}

  @Override
  public void disposeComponent() {}

  @Override
  public void projectOpened() {
    projectClosed = new AtomicBoolean(false);
    BuckFileUtil.setBuckFileType();
    // connect to the Buck client
    BuckClientManager.getOrCreateClient(mProject, mEventHandler).connect();

    if (!UISettings.getInstance().SHOW_MAIN_TOOLBAR) {
      BuckPluginNotifications.notifyActionToolbar(mProject);
    }

    mBuckEventsConsumer = new BuckEventsConsumer(mProject);

    PsiDocumentManager manager = PsiDocumentManager.getInstance(mProject);
    manager.addListener(new BuckAutoDepsContributor(mProject));
  }

  @Override
  public void projectClosed() {
    projectClosed.set(true);
    BuckClientManager.getOrCreateClient(mProject, mEventHandler).disconnectWithoutRetry();
    AndroidDebugger.disconnect();
    if (mBuckEventsConsumer != null) {
      mBuckEventsConsumer.detach();
    }
  }

  public boolean isConnected() {
    return BuckClientManager.getOrCreateClient(mProject, mEventHandler).isConnected();
  }

  public void attachIfDetached() {
    attachIfDetached("");
  }

  public void attachIfDetached(String target) {
    if (!mBuckEventsConsumer.isAttached()) {
      attach(target);
    }
  }

  public void attach(String target) {
    mBuckEventsConsumer.detach();

    mBuckEventsConsumer.attach(target, BuckUIManager.getInstance(mProject).getTreeModel());
  }

  public BuckEventsConsumer getBuckEventsConsumer() {
    return mBuckEventsConsumer;
  }

  private class ExecuteOnBuckPluginDisconnect implements Runnable {
    @Override
    public void run() {
      ApplicationManager.getApplication()
          .invokeLater(
              new Runnable() {
                @Override
                public void run() {
                  // If we haven't closed the project, then we show the message
                  if (!mProject.isDisposed()) {
                    BuckToolWindowFactory.outputConsoleMessage(
                        mProject,
                        "Disconnected from buck!\n",
                        ConsoleViewContentType.SYSTEM_OUTPUT);
                  }
                }
              });
      if (!projectClosed.get()) {
        // Tell the client that we got disconnected, but we can retry
        BuckClientManager.getOrCreateClient(mProject, mEventHandler).disconnectWithRetry();
      }
    }
  }

  private class ExecuteOnBuckPluginConnect implements Runnable {
    @Override
    public void run() {
      ApplicationManager.getApplication()
          .invokeLater(
              new Runnable() {
                @Override
                public void run() {
                  // If we connected to Buck and then closed the project, before getting
                  // the success message
                  if (!mProject.isDisposed()) {
                    BuckToolWindowFactory.outputConsoleMessage(
                        mProject, "Connected to buck!\n", ConsoleViewContentType.SYSTEM_OUTPUT);
                  }
                }
              });
    }
  }
}
