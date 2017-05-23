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

import static org.easymock.EasyMock.expect;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.facebook.buck.util.trace.BuildTraces;
import com.facebook.buck.util.trace.BuildTraces.TraceAttributes;
import java.io.IOException;
import java.io.PrintWriter; // NOPMD required by API
import java.io.StringWriter;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.easymock.EasyMockSupport;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.junit.Test;

public class TraceHandlerDelegateTest extends EasyMockSupport {

  @Test
  public void testHandleGet() throws IOException, ServletException {
    Request baseRequest = createMock(Request.class);
    expect(baseRequest.getPathInfo()).andReturn("/abcdef");
    baseRequest.setHandled(true);
    HttpServletRequest request = createMock(HttpServletRequest.class);

    HttpServletResponse response = createMock(HttpServletResponse.class);
    response.setStatus(200);
    response.setContentType("text/html; charset=utf-8");
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter); // NOPMD required by API
    expect(response.getWriter()).andReturn(printWriter);
    response.flushBuffer();

    BuildTraces buildTraces = createMock(BuildTraces.class);
    expect(buildTraces.getTraceAttributesFor("abcdef"))
        .andReturn(new TraceAttributes(Optional.of("buck build buck"), FileTime.fromMillis(2000L)));
    Handler traceHandler = new TemplateHandler(new TraceHandlerDelegate(buildTraces));

    replayAll();
    traceHandler.handle("/trace/abcdef", baseRequest, request, response);
    verifyAll();

    String expectedScriptTag = "<script src=\"/tracedata/abcdef?callback=onTracesLoaded\">";
    String html = stringWriter.toString();
    assertThat(html, containsString(expectedScriptTag));
    assertThat(html, containsString("buck build buck"));
  }

  @Test
  public void testMalformedPathInfoReturnsError() throws IOException {
    Request baseRequest = createMock(Request.class);
    expect(baseRequest.getPathInfo()).andReturn("/..upADirectory");

    // Nothing on the helper should be invoked because the handler will error-out first.
    BuildTraces buildTraces = createMock(BuildTraces.class);

    replayAll();

    TraceHandlerDelegate traceHandler = new TraceHandlerDelegate(buildTraces);
    assertNull(traceHandler.getDataForRequest(baseRequest));

    verifyAll();
  }
}
