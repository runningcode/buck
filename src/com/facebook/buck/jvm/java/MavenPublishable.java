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

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.SourcePath;
import java.util.Optional;

/**
 * A {@link BuildRule} that can have its output({@link #getSourcePathToOutput}) published to a maven
 * repository under the maven coordinates provided by {@link #getMavenCoords}
 */
public interface MavenPublishable extends HasMavenCoordinates {

  /** When published, these will be listed in pom.xml as dependencies */
  Iterable<HasMavenCoordinates> getMavenDeps();

  /**
   * When published, these will be included in the artifact. This, {@link #getMavenDeps()}, and the
   * transitive dependencies of those maven deps would form the complete set of transitive
   * dependencies for the artifact.
   */
  Iterable<BuildRule> getPackagedDependencies();

  /** @return A template for the pom.xml to be generated when publishing this artifact. */
  Optional<SourcePath> getPomTemplate();
}
