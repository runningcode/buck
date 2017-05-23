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

package com.facebook.buck.httpserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.template.soy.data.SoyMapData;
import java.io.IOException;
import org.easymock.EasyMock;
import org.eclipse.jetty.server.Request;
import org.junit.Test;

public class IndexHandlerDelegateTest {

  @Test
  public void testIndexHandlerReturnsCorrectTemplateAndData() throws IOException {
    Request baseRequest = EasyMock.createMock(Request.class);
    EasyMock.replay(baseRequest);

    IndexHandlerDelegate indexHandlerDelegate = new IndexHandlerDelegate();
    assertEquals("buck.index", indexHandlerDelegate.getTemplateForRequest(baseRequest));
    SoyMapData templateData = indexHandlerDelegate.getDataForRequest(baseRequest);
    assertTrue(templateData.getKeys().isEmpty());

    EasyMock.verify(baseRequest);
  }
}
