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

package com.facebook.buck.log;

import com.facebook.buck.log.views.JsonViews;
import com.facebook.buck.util.immutables.BuckStyleImmutable;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.concurrent.atomic.AtomicInteger;
import org.immutables.value.Value;

/**
 * Utility class to help outputting the information to the machine-readable log. It helps in the
 * serialization & deserialization process.
 */
@Value.Immutable
@BuckStyleImmutable
@JsonDeserialize(as = CacheUploadInfo.class)
abstract class AbstractCacheUploadInfo {

  @Value.Parameter
  @JsonView(JsonViews.MachineReadableLog.class)
  public abstract AtomicInteger getSuccessUploadCount();

  @Value.Parameter
  @JsonView(JsonViews.MachineReadableLog.class)
  public abstract AtomicInteger getFailureUploadCount();
}
