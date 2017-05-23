/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

public class Types {

  private static final LoadingCache<Field, Type> FIRST_NON_OPTIONAL_TYPE_CACHE =
      CacheBuilder.newBuilder()
          .weakValues()
          .build(
              new CacheLoader<Field, Type>() {
                @Override
                public Type load(Field field) throws Exception {
                  boolean isOptional = Optional.class.isAssignableFrom(field.getType());
                  if (isOptional) {
                    Type type = field.getGenericType();

                    if (type instanceof ParameterizedType) {
                      return ((ParameterizedType) type).getActualTypeArguments()[0];
                    } else {
                      throw new RuntimeException("Unexpected type parameter for Optional: " + type);
                    }
                  } else {
                    return field.getGenericType();
                  }
                }
              });

  private Types() {
    // Utility class.
  }

  /**
   * Determine the "base type" of a field. That is, the following will be returned:
   *
   * <ul>
   *   <li>{@code String} -&gt; {@code String.class}
   *   <li>{@code Optional&lt;String&gt;} -&gt; {@code String.class}
   *   <li>{@code Set&lt;String&gt;} -&gt; {@code String.class}
   *   <li>{@code Collection&lt;? extends Comparable&gt;} -&gt; {@code Comparable.class}
   *   <li>{@code Collection&lt;? super Comparable} -&gt; {@code Object.class}
   * </ul>
   */
  public static Type getBaseType(Field field) {
    Type type = getFirstNonOptionalType(field);

    if (type instanceof ParameterizedType) {
      type = ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    if (type instanceof WildcardType) {
      type = ((WildcardType) type).getUpperBounds()[0];
    }

    return Primitives.wrap((Class<?>) type);
  }

  /**
   * @return The raw type of the {@link Collection} a field represents, even if contained in an
   *     {@link Optional}, but without the ParameterizedType information.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public static Class<? extends Collection<?>> getContainerClass(Field field) {
    Type type = getFirstNonOptionalType(field);

    if (!(type instanceof ParameterizedType)) {
      return null;
    }

    Type rawType = ((ParameterizedType) type).getRawType();
    if (!(rawType instanceof Class)) {
      return null;
    }

    Class<?> clazz = (Class<?>) rawType;
    if (!(Collection.class.isAssignableFrom(clazz))) {
      return null;
    }

    return (Class<? extends Collection<?>>) clazz;
  }

  /**
   * Get the first complete {@link Type} in a signature that's non-optional, complete with the
   * information from the {@link ParameterizedType}.
   *
   * <ul>
   *   <li>String -&gt; String
   *   <li>Optional&lt;String$gt; -&gt; String
   *   <li>ImmutableSet&lt;String&gt; -&gt; ImmutableSet&lt;String&gt;
   *   <li>Optional&lt;ImmutableSet&lt;String&gt;&gt; -&gt; ImmutableSet&lt;String&gt;
   * </ul>
   */
  public static Type getFirstNonOptionalType(Field field) {
    try {
      return FIRST_NON_OPTIONAL_TYPE_CACHE.get(field);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a Set of classes and interfaces inherited or implemented by clazz.
   *
   * <p>Result includes clazz itself. Result is ordered closest to furthest, i.e. first entry will
   * always be clazz and last entry will always be {@link java.lang.Object}.
   */
  public static ImmutableSet<Class<?>> getSupertypes(Class<?> clazz) {
    LinkedHashSet<Class<?>> ret = new LinkedHashSet<>();

    Queue<Class<?>> toExpand = new LinkedList<>();
    toExpand.add(clazz);
    while (!toExpand.isEmpty()) {
      Class<?> current = toExpand.remove();
      if (!ret.add(current)) {
        continue;
      }
      for (Class<?> i : current.getInterfaces()) {
        toExpand.add(i);
      }
      if (current.getSuperclass() != null) {
        toExpand.add(current.getSuperclass());
      }
    }
    return ImmutableSet.copyOf(ret);
  }
}
