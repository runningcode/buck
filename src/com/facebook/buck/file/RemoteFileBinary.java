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

package com.facebook.buck.file;

import com.facebook.buck.rules.BinaryBuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.CommandTool;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.args.SourcePathArg;
import com.google.common.hash.HashCode;
import java.net.URI;

/**
 * Represents an executable {@link RemoteFile}, which means that it can be invoked using {@code buck
 * run}.
 */
public class RemoteFileBinary extends RemoteFile implements BinaryBuildRule {
  public RemoteFileBinary(
      BuildRuleParams params,
      Downloader downloader,
      URI uri,
      HashCode sha1,
      String out,
      Type type) {
    super(params, downloader, uri, sha1, out, type);
  }

  @Override
  public Tool getExecutableCommand() {
    return new CommandTool.Builder().addArg(SourcePathArg.of(getSourcePathToOutput())).build();
  }
}
