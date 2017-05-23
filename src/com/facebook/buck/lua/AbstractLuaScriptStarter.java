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

package com.facebook.buck.lua;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.file.WriteFile;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.model.InternalFlavor;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.WriteStringTemplateRule;
import com.facebook.buck.util.Escaper;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import com.google.common.base.Charsets;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.immutables.value.Value;

/** {@link Starter} implementation which builds a starter as a Lua script. */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractLuaScriptStarter implements Starter {

  private static final String STARTER = "com/facebook/buck/lua/starter.lua.in";

  abstract BuildRuleParams getBaseParams();

  abstract BuildRuleResolver getRuleResolver();

  abstract SourcePathResolver getPathResolver();

  abstract SourcePathRuleFinder getRuleFinder();

  abstract LuaConfig getLuaConfig();

  abstract CxxPlatform getCxxPlatform();

  abstract BuildTarget getTarget();

  abstract Path getOutput();

  abstract String getMainModule();

  abstract Optional<Path> getRelativeModulesDir();

  abstract Optional<Path> getRelativePythonModulesDir();

  private String getPureStarterTemplate() {
    try {
      return Resources.toString(Resources.getResource(STARTER), Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public SourcePath build() {
    BuildTarget templateTarget =
        BuildTarget.builder(getBaseParams().getBuildTarget())
            .addFlavors(InternalFlavor.of("starter-template"))
            .build();
    WriteFile templateRule =
        getRuleResolver()
            .addToIndex(
                new WriteFile(
                    getBaseParams()
                        .withBuildTarget(templateTarget)
                        .copyReplacingDeclaredAndExtraDeps(
                            Suppliers.ofInstance(ImmutableSortedSet.of()),
                            Suppliers.ofInstance(ImmutableSortedSet.of())),
                    getPureStarterTemplate(),
                    BuildTargets.getGenPath(
                        getBaseParams().getProjectFilesystem(),
                        templateTarget,
                        "%s/starter.lua.in"),
                    /* executable */ false));

    final Tool lua = getLuaConfig().getLua(getRuleResolver());
    WriteStringTemplateRule writeStringTemplateRule =
        getRuleResolver()
            .addToIndex(
                WriteStringTemplateRule.from(
                    getBaseParams(),
                    getRuleFinder(),
                    getTarget(),
                    getOutput(),
                    templateRule.getSourcePathToOutput(),
                    ImmutableMap.of(
                        "SHEBANG",
                        lua.getCommandPrefix(getPathResolver()).get(0),
                        "MAIN_MODULE",
                        Escaper.escapeAsPythonString(getMainModule()),
                        "MODULES_DIR",
                        getRelativeModulesDir().isPresent()
                            ? Escaper.escapeAsPythonString(getRelativeModulesDir().get().toString())
                            : "nil",
                        "PY_MODULES_DIR",
                        getRelativePythonModulesDir().isPresent()
                            ? Escaper.escapeAsPythonString(
                                getRelativePythonModulesDir().get().toString())
                            : "nil",
                        "EXT_SUFFIX",
                        Escaper.escapeAsPythonString(getCxxPlatform().getSharedLibraryExtension())),
                    /* executable */ true));

    return writeStringTemplateRule.getSourcePathToOutput();
  }
}
