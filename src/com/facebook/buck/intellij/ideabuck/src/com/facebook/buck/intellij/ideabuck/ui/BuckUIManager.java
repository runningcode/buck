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

package com.facebook.buck.intellij.ideabuck.ui;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import javax.swing.tree.DefaultTreeModel;

public class BuckUIManager {

  private ConsoleView outputConsole;
  private RunnerLayoutUi runnerLayoutUi;

  public static synchronized BuckUIManager getInstance(Project project) {
    return ServiceManager.getService(project, BuckUIManager.class);
  }

  public ConsoleView getConsoleWindow(Project project) {
    if (outputConsole == null) {
      outputConsole = new ConsoleViewImpl(project, false);
    }
    return outputConsole;
  }

  public RunnerLayoutUi getLayoutUi(Project project) {
    if (runnerLayoutUi == null) {
      runnerLayoutUi =
          RunnerLayoutUi.Factory.getInstance(project).create("buck", "buck", "buck", project);
    }
    return runnerLayoutUi;
  }

  private DefaultTreeModel mTreeModel;

  public DefaultTreeModel getTreeModel() {
    if (mTreeModel == null) {
      mTreeModel = new DefaultTreeModel(null);
    }
    return mTreeModel;
  }
}
