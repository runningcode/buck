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

package com.facebook.buck.rules;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Represents hints given when deal with the value of a type returned by {@link
 * com.facebook.buck.rules.Description#getConstructorArgType()}.
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface Hint {
  public static final boolean DEFAULT_IS_DEP = true;
  public static final boolean DEFAULT_IS_INPUT = true;

  /** @return Whether to search the field's value for dependencies */
  boolean isDep() default DEFAULT_IS_DEP;

  /** @return Whether to use the field's value as an input */
  boolean isInput() default DEFAULT_IS_INPUT;
}
