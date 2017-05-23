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

package com.facebook.buck.jvm.java;

import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.facebook.buck.log.Logger;
import com.facebook.buck.util.PatternsMatcher;
import com.facebook.buck.zip.CustomZipEntry;
import com.facebook.buck.zip.JarBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

/**
 * A {@link StandardJavaFileManager} that creates and writes the content of files directly into a
 * Jar output stream instead of writing the files to disk.
 */
public class JavaInMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager>
    implements StandardJavaFileManager {
  private static final Logger LOG = Logger.get(JavaInMemoryFileManager.class);

  private Path jarPath;
  private StandardJavaFileManager delegate;
  private Set<String> directoryPaths;
  private Map<String, JarFileObject> fileForOutputPaths;
  private PatternsMatcher classesToRemoveFromJar;

  public JavaInMemoryFileManager(
      StandardJavaFileManager standardManager,
      Path jarPath,
      ImmutableSet<Pattern> classesToRemoveFromJar) {
    super(standardManager);
    this.delegate = standardManager;
    this.jarPath = jarPath;
    this.directoryPaths = new HashSet<>();
    this.fileForOutputPaths = new HashMap<>();
    this.classesToRemoveFromJar = new PatternsMatcher(classesToRemoveFromJar);
  }

  /**
   * Creates a ZipEntry for placing in the jar output stream. Sets the modification time to 0 for a
   * deterministic jar.
   *
   * @param name the name of the entry
   * @return the zip entry for the file specified
   */
  public static ZipEntry createEntry(String name) {
    CustomZipEntry entry = new CustomZipEntry(name);
    // We want deterministic JARs, so avoid mtimes.
    entry.setFakeTime();
    return entry;
  }

  private static String getPath(String className) {
    return className.replace('.', '/');
  }

  private static String getPath(String className, JavaFileObject.Kind kind) {
    return className.replace('.', '/') + kind.extension;
  }

  private static String getPath(String packageName, String relativeName) {
    return !packageName.isEmpty()
        ? packageName.replace('.', '/') + '/' + relativeName
        : relativeName;
  }

  @Override
  public JavaFileObject getJavaFileForOutput(
      Location location, String className, JavaFileObject.Kind kind, FileObject sibling)
      throws IOException {
    // Use the normal FileObject that writes to the disk for source files.
    if (shouldDelegate(location)) {
      return delegate.getJavaFileForOutput(location, className, kind, sibling);
    }
    String path = getPath(className, kind);

    // If the class is to be removed from the Jar create a NoOp FileObject.
    if (classesToRemoveFromJar.hasPatterns()
        && classesToRemoveFromJar.matches(className.toString())) {
      LOG.info(
          "%s was excluded from the Jar because it matched a remove_classes pattern.",
          className.toString());
      return getJavaNoOpFileObject(path, kind);
    }

    return getJavaMemoryFileObject(kind, path);
  }

  @Override
  public FileObject getFileForOutput(
      Location location, String packageName, String relativeName, FileObject sibling)
      throws IOException {
    if (shouldDelegate(location)) {
      return delegate.getFileForOutput(location, packageName, relativeName, sibling);
    }

    String path = getPath(packageName, relativeName);
    return getJavaMemoryFileObject(JavaFileObject.Kind.OTHER, path);
  }

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    boolean aInMemoryJavaFileInstance = a instanceof JavaInMemoryFileObject;
    boolean bInMemoryJavaFileInstance = b instanceof JavaInMemoryFileObject;
    if (aInMemoryJavaFileInstance || bInMemoryJavaFileInstance) {
      return aInMemoryJavaFileInstance
          && bInMemoryJavaFileInstance
          && a.getName().equals(b.getName());
    }
    return super.isSameFile(a, b);
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
      Iterable<? extends File> files) {
    return delegate.getJavaFileObjectsFromFiles(files);
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
    return delegate.getJavaFileObjects(files);
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
    return delegate.getJavaFileObjectsFromStrings(names);
  }

  @Override
  public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
    return delegate.getJavaFileObjects(names);
  }

  @Override
  public void setLocation(Location location, Iterable<? extends File> path) throws IOException {
    delegate.setLocation(location, path);
  }

  @Override
  public Iterable<? extends File> getLocation(Location location) {
    return delegate.getLocation(location);
  }

  @Override
  public Iterable<JavaFileObject> list(
      Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse)
      throws IOException {
    if (shouldDelegate(location)) {
      return delegate.list(location, packageName, kinds, recurse);
    }

    ArrayList<JavaFileObject> results = new ArrayList<>();
    for (JavaFileObject fromSuper : delegate.list(location, packageName, kinds, recurse)) {
      results.add(fromSuper);
    }

    String packageDirPath = getPath(packageName) + '/';
    for (String filepath : fileForOutputPaths.keySet()) {
      if (recurse && filepath.startsWith(packageDirPath)) {
        results.add(fileForOutputPaths.get(filepath));
      } else if (!recurse
          && filepath.startsWith(packageDirPath)
          && filepath.substring(packageDirPath.length()).indexOf('/') < 0) {
        results.add(fileForOutputPaths.get(filepath));
      }
    }

    return results;
  }

  public ImmutableSet<String> writeToJar(JarBuilder jarBuilder) throws IOException {
    for (JarFileObject fileObject : fileForOutputPaths.values()) {
      fileObject.writeToJar(jarBuilder, jarPath.toString());
    }

    return ImmutableSet.copyOf(Sets.union(directoryPaths, fileForOutputPaths.keySet()));
  }

  private boolean shouldDelegate(Location location) {
    return location != CLASS_OUTPUT;
  }

  private JavaFileObject getJavaMemoryFileObject(JavaFileObject.Kind kind, String path)
      throws IOException {
    return fileForOutputPaths.computeIfAbsent(
        path, p -> new JavaInMemoryFileObject(getUriPath(p), p, kind));
  }

  private JavaFileObject getJavaNoOpFileObject(String path, JavaFileObject.Kind kind) {
    return fileForOutputPaths.computeIfAbsent(
        path, p -> new JavaNoOpFileObject(getUriPath(p), p, kind));
  }

  private String encodeURL(String path) {
    try {
      return URLEncoder.encode(path, "UTF-8").replace("%2F", "/");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private URI getUriPath(String relativePath) {
    return URI.create("jar:file:" + encodeURL(jarPath.toString()) + "!/" + encodeURL(relativePath));
  }
}
