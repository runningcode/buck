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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.macros.LocationMacro;

public class LocationMacroTypeCoercer extends BuildTargetMacroTypeCoercer<LocationMacro> {

  public LocationMacroTypeCoercer(TypeCoercer<BuildTarget> buildTargetTypeCoercer) {
    super(buildTargetTypeCoercer);
  }

  @Override
  public Class<LocationMacro> getOutputClass() {
    return LocationMacro.class;
  }

  @Override
  LocationMacro create(BuildTarget target) {
    return LocationMacro.of(target);
  }
}
