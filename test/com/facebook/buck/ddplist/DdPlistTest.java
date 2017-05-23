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

package com.facebook.buck.ddplist;

import static org.junit.Assert.assertEquals;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;

public class DdPlistTest {

  private File outputFile;

  @Before
  public void setUp() throws IOException {
    outputFile = File.createTempFile("out", ".plist");
  }

  @Test
  public void testXMLWriting() throws Exception {
    InputStream in = getClass().getResourceAsStream("test-files/test1.plist");
    NSDictionary x = (NSDictionary) PropertyListParser.parse(in);
    PropertyListParser.saveAsXML(x, outputFile);
    NSDictionary y = (NSDictionary) PropertyListParser.parse(outputFile);
    assertEquals(x, y);
  }
}
