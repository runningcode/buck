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

package com.facebook.buck.jvm.java.abi;

import static org.junit.Assert.assertTrue;

import com.facebook.buck.jvm.java.testutil.compiler.CompilerTreeApiTestRunner;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CompilerTreeApiTestRunner.class)
public class DescriptorFactoryTest extends DescriptorAndSignatureFactoryTestBase {

  private DescriptorFactory descriptorFactory;

  @Override
  public void setUp() throws IOException {
    super.setUp();
    descriptorFactory = new DescriptorFactory(elements);
  }

  @Test
  public void testAllTheThings() throws IOException {
    List<String> errors =
        getTestErrors(
            field -> field.desc,
            method -> method.desc,
            (t) -> null,
            descriptorFactory::getDescriptor);

    assertTrue("Descriptor mismatch!\n\n" + Joiner.on('\n').join(errors), errors.isEmpty());
  }
}
