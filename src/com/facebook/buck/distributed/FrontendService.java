/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.distributed;

import com.facebook.buck.distributed.thrift.FrontendRequest;
import com.facebook.buck.distributed.thrift.FrontendResponse;
import com.facebook.buck.slb.ThriftOverHttpService;
import com.facebook.buck.slb.ThriftOverHttpServiceConfig;
import java.io.IOException;

/**
 * Extension of ThriftOverHttpService to get rid of the template arguments and make it easymock-able
 */
public class FrontendService extends ThriftOverHttpService<FrontendRequest, FrontendResponse> {

  public FrontendService(ThriftOverHttpServiceConfig config) {
    super(config);
  }

  public FrontendResponse makeRequest(FrontendRequest request) throws IOException {
    FrontendResponse response = new FrontendResponse();
    makeRequest(request, response);
    return response;
  }
}
