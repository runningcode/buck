/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.parser;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetException;
import com.facebook.buck.model.BuildTargetPattern;

@SuppressWarnings("serial")
public class NoSuchBuildTargetException extends BuildTargetException {

  public NoSuchBuildTargetException(BuildTarget target) {
    this(String.format("No such target: '%s'", target));
  }

  private NoSuchBuildTargetException(String message) {
    super(message);
  }

  /** @param buildTarget the failing {@link com.facebook.buck.model.BuildTarget} */
  static NoSuchBuildTargetException createForMissingBuildRule(
      BuildTarget buildTarget,
      BuildTargetPatternParser<BuildTargetPattern> buildTargetPatternParser,
      String buildFileName,
      String buckFilepath) {
    String message =
        String.format(
            "No rule found when resolving target %s\n%s",
            buildTargetPatternParser.makeTargetDescription(
                buildTarget.getFullyQualifiedName(), buildFileName),
            buckFilepath);

    return new NoSuchBuildTargetException(message);
  }

  @Override
  public String getHumanReadableErrorMessage() {
    return getMessage();
  }
}
