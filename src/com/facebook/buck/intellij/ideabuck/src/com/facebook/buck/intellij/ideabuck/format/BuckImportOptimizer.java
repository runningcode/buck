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

package com.facebook.buck.intellij.ideabuck.format;

import com.facebook.buck.intellij.ideabuck.external.IntellijBuckAction;
import com.facebook.buck.intellij.ideabuck.lang.BuckFile;
import com.intellij.lang.ImportOptimizer;
import com.intellij.psi.PsiFile;

/**
 * Import optimizer is used for sort and remove unused java/python imports. Buck has no imports, but
 * we use it to sort dependencies.
 */
public class BuckImportOptimizer implements ImportOptimizer {
  @Override
  public boolean supports(PsiFile psiFile) {
    return psiFile instanceof BuckFile;
  }

  @Override
  public Runnable processFile(final PsiFile psiFile) {
    psiFile
        .getProject()
        .getMessageBus()
        .syncPublisher(IntellijBuckAction.EVENT)
        .consume(this.getClass().toString());
    return new Runnable() {
      @Override
      public void run() {
        DependenciesOptimizer.optimzeDeps(psiFile);
      }
    };
  }
}
