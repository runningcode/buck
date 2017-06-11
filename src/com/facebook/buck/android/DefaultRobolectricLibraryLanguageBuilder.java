package com.facebook.buck.android;

import com.facebook.buck.android.AndroidLibraryDescription.JvmLanguage;
import com.facebook.buck.jvm.java.DefaultJavaLibrary;
import com.facebook.buck.jvm.java.DefaultJavaLibraryBuilder;
import com.facebook.buck.jvm.java.JavaBuckConfig;
import com.facebook.buck.jvm.kotlin.KotlinBuckConfig;
import com.facebook.buck.jvm.kotlin.KotlinLibraryBuilder;
import com.facebook.buck.jvm.scala.ScalaBuckConfig;
import com.facebook.buck.jvm.scala.ScalaLibraryBuilder;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.util.HumanReadableException;

public class DefaultRobolectricLibraryLanguageBuilder implements RobolectricLibraryLanguageBuilder {

  private final JavaBuckConfig javaBuckConfig;
  private final KotlinBuckConfig kotlinBuckConfig;
  private final ScalaBuckConfig scalaBuckConfig;

  public DefaultRobolectricLibraryLanguageBuilder(
      JavaBuckConfig javaBuckConfig,
      KotlinBuckConfig kotlinBuckConfig, ScalaBuckConfig scalaBuckConfig) {
    this.javaBuckConfig = javaBuckConfig;
    this.kotlinBuckConfig = kotlinBuckConfig;
    this.scalaBuckConfig = scalaBuckConfig;
  }

  @Override
  public DefaultJavaLibraryBuilder getJavaLibraryBuilder(
      RobolectricTestDescriptionArg arg,
      TargetGraph targetGraph,
      BuildRuleParams testsLibraryParams,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots) {
    JvmLanguage language = arg.getLanguage().orElse(JvmLanguage.JAVA);
    switch (language) {
      case JAVA:
        return DefaultJavaLibrary.builder(
            targetGraph,
            testsLibraryParams,
            resolver,
            cellRoots,
            javaBuckConfig);
      case KOTLIN:
        return new KotlinLibraryBuilder(
            targetGraph,
            testsLibraryParams,
            resolver,
            cellRoots,
            kotlinBuckConfig,
            javaBuckConfig);
      case SCALA:
        return new ScalaLibraryBuilder(
            targetGraph,
            testsLibraryParams,
            resolver,
            cellRoots,
            scalaBuckConfig);
    }
    throw new HumanReadableException("Unsupported `language` parameter value: %s", language);
  }
}
