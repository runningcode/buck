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

package com.facebook.buck.jvm.java.abi.source;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.jvm.java.testutil.compiler.CompilerTreeApiParameterized;
import com.google.common.base.Joiner;
import java.io.IOException;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CompilerTreeApiParameterized.class)
public class StandaloneDeclaredTypeTest extends CompilerTreeApiParameterizedTest {
  @Test
  public void testToStringNoGenerics() throws IOException {
    compile(Joiner.on('\n').join("package com.facebook.foo;", "class Foo { }"));

    DeclaredType fooType = (DeclaredType) elements.getTypeElement("com.facebook.foo.Foo").asType();

    assertEquals("com.facebook.foo.Foo", fooType.toString());
  }

  @Test
  public void testToStringWithGenerics() throws IOException {
    initCompiler();

    TypeElement mapElement = elements.getTypeElement("java.util.Map");
    TypeMirror stringType = elements.getTypeElement("java.lang.String").asType();
    TypeMirror integerType = elements.getTypeElement("java.lang.Integer").asType();
    DeclaredType mapStringIntType = types.getDeclaredType(mapElement, stringType, integerType);

    assertEquals("java.util.Map<java.lang.String,java.lang.Integer>", mapStringIntType.toString());
  }
}
