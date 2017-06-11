package com.facebook.buck.android;

import com.facebook.buck.android.AndroidLibraryDescription.JvmLanguage;
import com.facebook.buck.jvm.java.DefaultJavaLibraryBuilder;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CellPathResolver;
import com.facebook.buck.rules.TargetGraph;

/**
 * Factory providing implementations of {@link DefaultJavaLibraryBuilder} for the specified {@code
 * language}. {@link RobolectricTestDescription} uses this factory to handle multiple JVM languages.
 * Implementations should provide a compiler implementation for every {@link JvmLanguage}. See
 * {@link DefaultRobolectricLibraryLanguageBuilder}
 */
public interface RobolectricLibraryLanguageBuilder {
  DefaultJavaLibraryBuilder getJavaLibraryBuilder(
      RobolectricTestDescriptionArg arg,
      TargetGraph targetGraph,
      BuildRuleParams testsLibraryParams,
      BuildRuleResolver resolver,
      CellPathResolver cellRoots);
}
