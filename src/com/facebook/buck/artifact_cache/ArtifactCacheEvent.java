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

package com.facebook.buck.artifact_cache;

import com.facebook.buck.event.AbstractBuckEvent;
import com.facebook.buck.event.EventKey;
import com.facebook.buck.event.LeafEvent;
import com.facebook.buck.rules.RuleKey;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;

public abstract class ArtifactCacheEvent extends AbstractBuckEvent implements LeafEvent {
  private static final String TARGET_KEY = "TARGET";

  public enum Operation {
    FETCH,
    STORE,
  }

  public enum InvocationType {
    SYNCHRONOUS,
    ASYNCHRONOUS,
  }

  public enum CacheMode {
    dir,
    http
  }

  @JsonIgnore private final CacheMode cacheMode;

  @JsonProperty("operation")
  private final Operation operation;

  @JsonIgnore private final ArtifactCacheEvent.InvocationType invocationType;

  @JsonIgnore private final Optional<String> target;

  @JsonIgnore private final ImmutableSet<RuleKey> ruleKeys;

  protected ArtifactCacheEvent(
      EventKey eventKey,
      CacheMode cacheMode,
      Operation operation,
      Optional<String> target,
      ImmutableSet<RuleKey> ruleKeys,
      ArtifactCacheEvent.InvocationType invocationType) {
    super(eventKey);
    this.cacheMode = cacheMode;
    this.operation = operation;
    this.target = target;
    this.ruleKeys = ruleKeys;
    this.invocationType = invocationType;
  }

  @Override
  protected String getValueString() {
    return getEventName() + getEventKey().toString();
  }

  @Override
  public String getCategory() {
    return cacheMode.toString().toLowerCase() + "_artifact_" + operation.toString().toLowerCase();
  }

  public Operation getOperation() {
    return operation;
  }

  public ImmutableSet<RuleKey> getRuleKeys() {
    return ruleKeys;
  }

  public Optional<String> getTarget() {
    return target;
  }

  public ArtifactCacheEvent.InvocationType getInvocationType() {
    return invocationType;
  }

  @Override
  public abstract String getEventName();

  public static final Optional<String> getTarget(final ImmutableMap<String, String> metadata) {
    return metadata.containsKey(TARGET_KEY)
        ? Optional.of(metadata.get(TARGET_KEY))
        : Optional.empty();
  }

  public abstract static class Started extends ArtifactCacheEvent {
    protected Started(
        EventKey eventKey,
        CacheMode cacheMode,
        Operation operation,
        Optional<String> target,
        ImmutableSet<RuleKey> ruleKeys,
        ArtifactCacheEvent.InvocationType invocationType) {
      super(eventKey, cacheMode, operation, target, ruleKeys, invocationType);
    }
  }

  public abstract static class Finished extends ArtifactCacheEvent {
    /** Not present iff {@link #getOperation()} is not {@link Operation#FETCH}. */
    private final Optional<CacheResult> cacheResult;

    protected Finished(
        EventKey eventKey,
        CacheMode cacheMode,
        Operation operation,
        Optional<String> target,
        ImmutableSet<RuleKey> ruleKeys,
        ArtifactCacheEvent.InvocationType invocationType,
        Optional<CacheResult> cacheResult) {
      super(eventKey, cacheMode, operation, target, ruleKeys, invocationType);
      Preconditions.checkArgument(
          (!operation.equals(Operation.FETCH) || cacheResult.isPresent()),
          "For FETCH operations, cacheResult must be non-null. "
              + "For non-FETCH operations, cacheResult must be null.");
      this.cacheResult = cacheResult;
    }

    public Optional<CacheResult> getCacheResult() {
      return cacheResult;
    }

    public boolean isSuccess() {
      return !cacheResult.isPresent() || cacheResult.get().getType().isSuccess();
    }

    @Override
    public boolean equals(Object o) {
      if (!super.equals(o)) {
        return false;
      }
      // Because super.equals compares the EventKey, getting here means that we've somehow managed
      // to create 2 Finished events for the same Started event.
      throw new UnsupportedOperationException("Multiple conflicting Finished events detected.");
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(super.hashCode(), cacheResult);
    }
  }
}
