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

package com.facebook.buck.jvm.kotlin;

import static com.facebook.buck.jvm.java.JavacToJarStepFactory.addAnnotationGenFolderStep;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;

import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.PathOrGlobMatcher;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.AnnotationProcessingParams;
import com.facebook.buck.jvm.java.BaseCompileToJarStepFactory;
import com.facebook.buck.jvm.java.ClassUsageFileWriter;
import com.facebook.buck.jvm.java.ClasspathChecker;
import com.facebook.buck.jvm.java.Javac;
import com.facebook.buck.jvm.java.JavacOptions;
import com.facebook.buck.jvm.java.JavacOptionsAmender;
import com.facebook.buck.jvm.java.JavacPluginJsr199Fields;
import com.facebook.buck.jvm.java.JavacStep;
import com.facebook.buck.jvm.java.ResolvedJavacPluginProperties;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class KotlincToJarStepFactory extends BaseCompileToJarStepFactory {
  private static final Logger LOG = Logger.get(KotlincToJarStepFactory.class);

  private static final PathOrGlobMatcher JAVA_PATH_MATCHER = new PathOrGlobMatcher("**.java");

  private static final String KAPT3_PLUGIN = "plugin:org.jetbrains.kotlin.kapt3:";
  private static final String AP_CLASSPATH_ARG = KAPT3_PLUGIN + "apclasspath=";
  // output path for generated sources;
  private static final String SOURCES_ARG = KAPT3_PLUGIN + "sources=";
  private static final String CLASSES_ARG = KAPT3_PLUGIN + "classes=";
  // output path for java stubs;
  private static final String STUBS_ARG = KAPT3_PLUGIN + "stubs=";
  private static final String LIGHT_ANALYSIS = KAPT3_PLUGIN + "useLightAnalysis=";
  private static final String CORRECT_ERROR_TYPES = KAPT3_PLUGIN + "correctErrorTypes=";
  private static final String VERBOSE_ARG = KAPT3_PLUGIN + "verbose=";
  private static final String JAVAC_ARG = KAPT3_PLUGIN + "javacArguments=";
  private static final String AP_OPTIONS = KAPT3_PLUGIN + "apoptions=";
  private static final String KAPT_GENERATED = "kapt.kotlin.generated";

  private final Kotlinc kotlinc;
  private final ImmutableList<String> extraArguments;
  private final Function<BuildContext, Iterable<Path>> extraClassPath;
  private final Javac javac;
  private final JavacOptions javacOptions;
  private final JavacOptionsAmender amender;

  public KotlincToJarStepFactory(
      Kotlinc kotlinc,
      ImmutableList<String> extraArguments,
      Function<BuildContext, Iterable<Path>> extraClassPath,
      Javac javac,
      JavacOptions javacOptions,
      JavacOptionsAmender amender) {
    this.kotlinc = kotlinc;
    this.extraArguments = extraArguments;
    this.extraClassPath = extraClassPath;
    this.javac = javac;
    this.javacOptions = Preconditions.checkNotNull(javacOptions);
    this.amender = amender;
  }

  @Override
  public void createCompileStep(
      BuildContext buildContext,
      ImmutableSortedSet<Path> sourceFilePaths,
      BuildTarget invokingRule,
      SourcePathResolver resolver,
      SourcePathRuleFinder ruleFinder,
      ProjectFilesystem filesystem,
      ImmutableSortedSet<Path> declaredClasspathEntries,
      Path outputDirectory,
      Optional<Path> workingDirectory,
      Path pathToSrcsList,
      ClassUsageFileWriter usedClassesFileWriter,
      /* out params */
      ImmutableList.Builder<Step> steps,
      BuildableContext buildableContext) {


    final JavacOptions buildTimeOptions = amender.amend(javacOptions, buildContext);
    if (!javacOptions.getAnnotationProcessingParams().isEmpty()) {
      // Javac requires that the root directory for generated sources already exist.
      addAnnotationGenFolderStep(buildTimeOptions, filesystem, steps, buildableContext, buildContext);
    }


    Path stubsOutput = BuildTargets.getAnnotationPath(filesystem, invokingRule, "__%s_stubs__");
    Path classesOutput = BuildTargets.getAnnotationPath(filesystem, invokingRule, "__%s_classes__");
    Path kaptGenerated = BuildTargets.getAnnotationPath(filesystem, invokingRule, "__%s_kapt_generated");

    addCreateFolderStep(steps, filesystem, buildableContext, buildContext, stubsOutput);
    addCreateFolderStep(steps, filesystem, buildableContext, buildContext, classesOutput);
    addCreateFolderStep(steps, filesystem, buildableContext, buildContext, kaptGenerated);

    ImmutableList<String> pluginFields =
        ImmutableList.copyOf(
            javacOptions
                .getAnnotationProcessingParams()
                .getAnnotationProcessors(filesystem, resolver)
                .stream()
                .map(ResolvedJavacPluginProperties::getJavacPluginJsr199Fields)
                .map(JavacPluginJsr199Fields::getClasspath)
                .flatMap(List::stream)
                .map(url -> AP_CLASSPATH_ARG + url.getFile())
                .collect(Collectors.toList()));

    LOG.error("Plugin fields are " + pluginFields);
    ImmutableList<String> apClassPaths = ImmutableList.<String>builder()
        .add(AP_CLASSPATH_ARG + kotlinc.getAPPaths())
        .add(AP_CLASSPATH_ARG + kotlinc.getStdlibPath())
        .addAll(pluginFields)
        .add(SOURCES_ARG + javacOptions.getAnnotationProcessingParams().getGeneratedSourceFolderName())
        .add(CLASSES_ARG + classesOutput)
        .add(STUBS_ARG + stubsOutput)
        .add(AP_OPTIONS + encodeOptions(Collections.singletonMap(KAPT_GENERATED, kaptGenerated.toString())))
        .add(JAVAC_ARG + encodeOptions(Collections.emptyMap()))
        .add(LIGHT_ANALYSIS + "true")
        .add(CORRECT_ERROR_TYPES + "true")
        .add(VERBOSE_ARG + "true")
        .build();
    String join = Joiner.on(",").join(apClassPaths);
    LOG.error("joined are " + join);

    // First add generate stubs step.
    steps.add(new KotlincStep(
        invokingRule,
        outputDirectory,
        sourceFilePaths,
        pathToSrcsList,
        ImmutableSortedSet.<Path>naturalOrder()
            .add(kotlinc.getStdlibPath())
        .addAll(
            Optional.ofNullable(extraClassPath.apply(buildContext))
            .orElse(ImmutableList.of()))
            .addAll(declaredClasspathEntries)
            .build(),
        kotlinc,
        ImmutableList.of(
//            "-module-name", "foo",
            "-Xadd-compiler-builtins",
            "-Xload-builtins-from-dependencies",
            "-P", KAPT3_PLUGIN + "aptMode=stubs," + join,
            "-Xplugin=" + kotlinc.getAPPaths()),
        filesystem));

    // second, the kapt step.
    steps.add(new KotlincStep(
        invokingRule,
        outputDirectory,
        sourceFilePaths,
        pathToSrcsList,
        ImmutableSortedSet.<Path>naturalOrder()
            .add(kotlinc.getStdlibPath())
        .addAll(
            Optional.ofNullable(extraClassPath.apply(buildContext))
            .orElse(ImmutableList.of()))
            .addAll(declaredClasspathEntries)
            .build(),
        kotlinc,
        ImmutableList.of(
//            "-module-name", "foo",
            "-Xadd-compiler-builtins",
            "-Xload-builtins-from-dependencies",
            "-P", KAPT3_PLUGIN + "aptMode=apt," + join,
            "-Xplugin=" + kotlinc.getAPPaths()),
        filesystem));

    LOG.error("javac output=" + javacOptions.getAnnotationProcessingParams().getGeneratedSourceFolderName());
    ImmutableSortedSet<Path> sourcePaths = ImmutableSortedSet.<Path>naturalOrder()
        .add(outputDirectory)
//        .add(javacOptions.getAnnotationProcessingParams().getGeneratedSourceFolderName())
        .addAll(sourceFilePaths)
        .build();

    steps.add(
        new KotlincStep(
            invokingRule,
            outputDirectory,
            sourcePaths,
            pathToSrcsList,
            ImmutableSortedSet.<Path>naturalOrder()
                .addAll(
                    Optional.ofNullable(extraClassPath.apply(buildContext))
                        .orElse(ImmutableList.of()))
                .addAll(declaredClasspathEntries)
                .build(),
            kotlinc,
            extraArguments,
            filesystem));

    ImmutableSortedSet<Path> javaSourceFiles =
        ImmutableSortedSet.copyOf(
            sourceFilePaths
                .stream()
                .filter(JAVA_PATH_MATCHER::matches)
                .collect(Collectors.toSet()));

    // Compile java without annotation processors.
    // Don't invoke javac if we don't have any java files.
    if (!javaSourceFiles.isEmpty()) {
      JavacOptions noAnnotationProcessing = JavacOptions.builder(buildTimeOptions)
          .setAnnotationProcessingParams(AnnotationProcessingParams.EMPTY)
          .build();
      steps.add(
          new JavacStep(
              outputDirectory,
              usedClassesFileWriter,
              workingDirectory,
              sourceFilePaths,
              pathToSrcsList,
              declaredClasspathEntries,
              javac,
              noAnnotationProcessing,
              invokingRule,
              resolver,
              filesystem,
              new ClasspathChecker(),
              Optional.empty()));
    }
  }

  @Override
  protected Optional<String> getBootClasspath(BuildContext context) {
    JavacOptions buildTimeOptions = amender.amend(javacOptions, context);
    return buildTimeOptions.getBootclasspath();
  }

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    kotlinc.appendToRuleKey(sink);
    sink.setReflectively("extraArguments", extraArguments);
  }

  @Override
  protected Tool getCompiler() {
    return kotlinc;
  }

  private void addCreateFolderStep(
      ImmutableList.Builder<Step> steps,
      ProjectFilesystem filesystem,
      BuildableContext buildableContext,
      BuildContext buildContext, Path location) {
    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(), filesystem, location)));
    buildableContext.recordArtifact(location);

  }

  private String encodeOptions(Map<String, String> options) {
    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(os);

      oos.writeInt(options.size());
      for (Map.Entry<String, String> entry : options.entrySet()) {
        oos.writeUTF(entry.getKey());
        oos.writeUTF(entry.getValue());
      }

      oos.flush();
      return printBase64Binary(os.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
