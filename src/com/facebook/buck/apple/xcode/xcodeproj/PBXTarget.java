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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Information for building a specific artifact (a library, binary, or test). */
public abstract class PBXTarget extends PBXProjectItem {
  private final String name;
  private final List<PBXTargetDependency> dependencies;
  private final List<PBXBuildPhase> buildPhases;
  private XCConfigurationList buildConfigurationList;
  @Nullable private String productName;
  @Nullable private PBXFileReference productReference;
  @Nullable private ProductType productType;

  public PBXTarget(String name) {
    this.name = name;
    this.dependencies = new ArrayList<>();
    this.buildPhases = new ArrayList<>();
    this.buildConfigurationList = new XCConfigurationList();
  }

  public String getName() {
    return name;
  }

  public List<PBXTargetDependency> getDependencies() {
    return dependencies;
  }

  public List<PBXBuildPhase> getBuildPhases() {
    return buildPhases;
  }

  public XCConfigurationList getBuildConfigurationList() {
    return buildConfigurationList;
  }

  public void setBuildConfigurationList(XCConfigurationList buildConfigurationList) {
    this.buildConfigurationList = buildConfigurationList;
  }

  @Nullable
  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
  }

  @Nullable
  public PBXFileReference getProductReference() {
    return productReference;
  }

  public void setProductReference(PBXFileReference v) {
    productReference = v;
  }

  @Nullable
  public ProductType getProductType() {
    return productType;
  }

  public void setProductType(@Nullable ProductType productType) {
    this.productType = productType;
  }

  @Override
  public String isa() {
    return "PBXTarget";
  }

  @Override
  public int stableHash() {
    return name.hashCode();
  }

  @Override
  public void serializeInto(XcodeprojSerializer s) {
    super.serializeInto(s);

    s.addField("name", name);
    if (productName != null) {
      s.addField("productName", productName);
    }
    if (productReference != null) {
      s.addField("productReference", productReference);
    }
    if (productType != null) {
      s.addField("productType", productType.toString());
    }
    s.addField("dependencies", dependencies);
    s.addField("buildPhases", buildPhases);
    s.addField("buildConfigurationList", buildConfigurationList);
  }
}
