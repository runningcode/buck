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

package com.facebook.buck.apple.xcode.xcodeproj;

import com.facebook.buck.apple.xcode.XcodeprojSerializer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/** A collection of files in Xcode's virtual filesystem hierarchy. */
public class PBXGroup extends PBXReference {
  /** Method by which group contents will be sorted. */
  public enum SortPolicy {
    /** By name, in default Java sort order. */
    BY_NAME,

    /** Group contents will not be sorted, and will remain in the order they were added. */
    UNSORTED;
  }

  // Unfortunately, we can't determine this at constructor time, because CacheBuilder
  // calls our constructor and it's not easy to pass arguments to it.
  private SortPolicy sortPolicy;

  private final List<PBXReference> children;

  private final LoadingCache<String, PBXGroup> childGroupsByName;
  private final LoadingCache<String, PBXVariantGroup> childVariantGroupsByName;
  private final LoadingCache<SourceTreePath, PBXFileReference> fileReferencesBySourceTreePath;
  private final LoadingCache<SourceTreePath, XCVersionGroup> childVersionGroupsBySourceTreePath;

  public PBXGroup(String name, @Nullable String path, SourceTree sourceTree) {
    super(name, path, sourceTree);

    sortPolicy = SortPolicy.BY_NAME;
    children = new ArrayList<>();

    childGroupsByName =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, PBXGroup>() {
                  @Override
                  public PBXGroup load(String key) throws Exception {
                    PBXGroup group = new PBXGroup(key, null, SourceTree.GROUP);
                    children.add(group);
                    return group;
                  }
                });

    childVariantGroupsByName =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, PBXVariantGroup>() {
                  @Override
                  public PBXVariantGroup load(String key) throws Exception {
                    PBXVariantGroup group = new PBXVariantGroup(key, null, SourceTree.GROUP);
                    children.add(group);
                    return group;
                  }
                });

    fileReferencesBySourceTreePath =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<SourceTreePath, PBXFileReference>() {
                  @Override
                  public PBXFileReference load(SourceTreePath key) throws Exception {
                    PBXFileReference ref = key.createFileReference();
                    children.add(ref);
                    return ref;
                  }
                });

    childVersionGroupsBySourceTreePath =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<SourceTreePath, XCVersionGroup>() {
                  @Override
                  public XCVersionGroup load(SourceTreePath key) throws Exception {
                    XCVersionGroup ref = key.createVersionGroup();
                    children.add(ref);
                    return ref;
                  }
                });
  }

  public PBXGroup getOrCreateChildGroupByName(String name) {
    return childGroupsByName.getUnchecked(name);
  }

  public PBXGroup getOrCreateDescendantGroupByPath(ImmutableList<String> path) {
    PBXGroup targetGroup = this;
    for (String part : path) {
      targetGroup = targetGroup.getOrCreateChildGroupByName(part);
    }
    return targetGroup;
  }

  public PBXVariantGroup getOrCreateChildVariantGroupByName(String name) {
    return childVariantGroupsByName.getUnchecked(name);
  }

  public PBXFileReference getOrCreateFileReferenceBySourceTreePath(SourceTreePath sourceTreePath) {
    return fileReferencesBySourceTreePath.getUnchecked(sourceTreePath);
  }

  public XCVersionGroup getOrCreateChildVersionGroupsBySourceTreePath(
      SourceTreePath sourceTreePath) {
    return childVersionGroupsBySourceTreePath.getUnchecked(sourceTreePath);
  }

  public List<PBXReference> getChildren() {
    return children;
  }

  public void setSortPolicy(SortPolicy sortPolicy) {
    this.sortPolicy = sortPolicy;
  }

  @Override
  public String isa() {
    return "PBXGroup";
  }

  @Override
  public void serializeInto(XcodeprojSerializer s) {
    super.serializeInto(s);

    if (sortPolicy == SortPolicy.BY_NAME) {
      Collections.sort(children, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    }

    s.addField("children", children);
  }
}
