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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public class XCVersionGroup extends PBXReference {
  private Optional<PBXFileReference> currentVersion;

  private final List<PBXFileReference> children;

  private final LoadingCache<SourceTreePath, PBXFileReference> fileReferencesBySourceTreePath;

  public XCVersionGroup(String name, @Nullable String path, SourceTree sourceTree) {
    super(name, path, sourceTree);
    children = new ArrayList<>();

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

    currentVersion = Optional.empty();
  }

  public Optional<String> getVersionGroupType() {
    if (currentVersion.isPresent()) {
      return currentVersion.get().getExplicitFileType();
    }
    return Optional.empty();
  }

  public Optional<PBXFileReference> getCurrentVersion() {
    return currentVersion;
  }

  public void setCurrentVersion(Optional<PBXFileReference> v) {
    currentVersion = v;
  }

  public List<PBXFileReference> getChildren() {
    return children;
  }

  public PBXFileReference getOrCreateFileReferenceBySourceTreePath(SourceTreePath sourceTreePath) {
    return fileReferencesBySourceTreePath.getUnchecked(sourceTreePath);
  }

  @Override
  public String isa() {
    return "XCVersionGroup";
  }

  @Override
  public void serializeInto(XcodeprojSerializer s) {
    super.serializeInto(s);

    Collections.sort(children, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    s.addField("children", children);

    if (currentVersion.isPresent()) {
      s.addField("currentVersion", currentVersion.get());
    }

    Optional<String> versionGroupType = getVersionGroupType();
    if (versionGroupType.isPresent()) {
      s.addField("versionGroupType", versionGroupType.get());
    }
  }
}
