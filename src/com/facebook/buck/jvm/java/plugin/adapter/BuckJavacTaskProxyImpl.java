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

package com.facebook.buck.jvm.java.plugin.adapter;

import com.facebook.buck.jvm.java.plugin.api.BuckJavacTaskListener;
import com.facebook.buck.jvm.java.plugin.api.BuckJavacTaskProxy;
import com.facebook.buck.jvm.java.plugin.api.CompilationUnitTreeProxy;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;

public class BuckJavacTaskProxyImpl implements BuckJavacTaskProxy {
  private final BuckJavacTask javacTask;

  public BuckJavacTaskProxyImpl(JavaCompiler.CompilationTask javacTask) {
    this.javacTask = new BuckJavacTask((JavacTask) javacTask);
  }

  public BuckJavacTaskProxyImpl(BuckJavacTask javacTask) {
    this.javacTask = javacTask;
  }

  public BuckJavacTask getInner() {
    return javacTask;
  }

  @Override
  public Iterable<CompilationUnitTreeProxy> parse() throws IOException {
    return StreamSupport.stream(javacTask.parse().spliterator(), false)
        .map(CompilationUnitTreeProxyImpl::new)
        .collect(Collectors.toList());
  }

  @Override
  public Iterable<? extends TypeElement> enter() throws IOException {
    try {
      @SuppressWarnings("unchecked")
      Iterable<? extends TypeElement> result =
          (Iterable<? extends TypeElement>)
              javacTask.getClass().getMethod("enter").invoke(javacTask);
      return result;
    } catch (IllegalAccessException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }

      throw new RuntimeException(e);
    }
  }

  @Override
  public Iterable<? extends Element> analyze() throws IOException {
    return javacTask.analyze();
  }

  @Override
  public Iterable<? extends JavaFileObject> generate() throws IOException {
    return javacTask.generate();
  }

  @Override
  public void setTaskListener(BuckJavacTaskListener buckTaskListener) {
    javacTask.setTaskListener(getTaskListener(buckTaskListener));
  }

  @Override
  public void addTaskListener(BuckJavacTaskListener buckTaskListener) {
    javacTask.addTaskListener(getTaskListener(buckTaskListener));
  }

  @Override
  public void removeTaskListener(BuckJavacTaskListener buckTaskListener) {
    javacTask.removeTaskListener(getTaskListener(buckTaskListener));
  }

  private TaskListener getTaskListener(BuckJavacTaskListener taskListener) {
    if (taskListener instanceof TaskListenerProxy) {
      return ((TaskListenerProxy) taskListener).getInner();
    }

    return new BuckJavacTaskListenerProxy(taskListener);
  }

  @Override
  public void addPostEnterCallback(Consumer<Set<TypeElement>> callback) {
    javacTask.addPostEnterCallback(callback);
  }

  @Override
  public Elements getElements() {
    return javacTask.getElements();
  }

  @Override
  public Types getTypes() {
    return javacTask.getTypes();
  }

  @Override
  public void setProcessors(Iterable<? extends Processor> processors) {
    javacTask.setProcessors(processors);
  }

  @Override
  public void setLocale(Locale locale) {
    javacTask.setLocale(locale);
  }

  @Override
  public Boolean call() {
    return javacTask.call();
  }
}
