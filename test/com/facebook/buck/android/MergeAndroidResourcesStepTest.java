/*
 * Copyright 2012-present Facebook, Inc.
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

package com.facebook.buck.android;

import static com.facebook.buck.android.aapt.RDotTxtEntry.IdType.INT;
import static com.facebook.buck.android.aapt.RDotTxtEntry.IdType.INT_ARRAY;
import static com.facebook.buck.android.aapt.RDotTxtEntry.RType.ATTR;
import static com.facebook.buck.android.aapt.RDotTxtEntry.RType.ID;
import static com.facebook.buck.android.aapt.RDotTxtEntry.RType.STYLEABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.facebook.buck.android.MergeAndroidResourcesStep.DuplicateResourceException;
import com.facebook.buck.android.aapt.FakeRDotTxtEntryWithID;
import com.facebook.buck.android.aapt.RDotTxtEntry;
import com.facebook.buck.android.aapt.RDotTxtEntry.RType;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.IntStream;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringContains;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MergeAndroidResourcesStepTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testGenerateRDotJavaForMultipleSymbolsFiles()
      throws IOException, DuplicateResourceException {
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();

    // Merge everything into the same package space.
    String sharedPackageName = "com.facebook.abc";
    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "a-R.txt",
            ImmutableList.of(
                "int id a1 0x7f010001", "int id a2 0x7f010002", "int string a1 0x7f020001")));

    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "b-R.txt",
            ImmutableList.of(
                "int id b1 0x7f010001", "int id b2 0x7f010002", "int string a1 0x7f020001")));

    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "c-R.txt",
            ImmutableList.of("int attr c1 0x7f010001", "int[] styleable c1 { 0x7f010001 }")));

    SortedSetMultimap<String, RDotTxtEntry> packageNameToResources =
        MergeAndroidResourcesStep.sortSymbols(
            entriesBuilder.buildFilePathToPackageNameSet(),
            Optional.empty(),
            ImmutableMap.of(),
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            entriesBuilder.getProjectFilesystem(),
            false);

    assertEquals(1, packageNameToResources.keySet().size());
    SortedSet<RDotTxtEntry> resources = packageNameToResources.get(sharedPackageName);
    assertEquals(7, resources.size());

    Set<String> uniqueEntries = Sets.newHashSet();
    for (RDotTxtEntry resource : resources) {
      if (!resource.type.equals(STYLEABLE)) {
        assertFalse(
            "Duplicate ids should be fixed by renumerate=true; duplicate was: " + resource.idValue,
            uniqueEntries.contains(resource.idValue));
        uniqueEntries.add(resource.idValue);
      }
    }

    assertEquals(6, uniqueEntries.size());

    // All good, no need to further test whether we can write the Java file correctly...
  }

  @Test
  public void testGenerateRDotJavaForWithStyleables()
      throws IOException, DuplicateResourceException {
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();

    // Merge everything into the same package space.
    String sharedPackageName = "com.facebook.abc";
    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "a-R.txt",
            ImmutableList.of(
                "int attr buttonPanelSideLayout 0x7f01003a",
                "int attr listLayout 0x7f01003b",
                "int[] styleable AlertDialog { 0x7f01003a, 0x7f01003b, 0x010100f2 }",
                "int styleable AlertDialog_android_layout 2",
                "int styleable AlertDialog_buttonPanelSideLayout 0",
                "int styleable AlertDialog_multiChoiceItemLayout 1")));
    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "b-R.txt",
            ImmutableList.of(
                "int id a1 0x7f010001",
                "int id a2 0x7f010002",
                "int attr android_layout_gravity 0x7f078008",
                "int attr background 0x7f078009",
                "int attr backgroundSplit 0x7f078008",
                "int attr backgroundStacked 0x7f078010",
                "int attr layout_heightPercent 0x7f078012",
                "int[] styleable ActionBar {  }",
                "int styleable ActionBar_background 10",
                "int styleable ActionBar_backgroundSplit 12",
                "int styleable ActionBar_backgroundStacked 11",
                "int[] styleable ActionBarLayout { 0x7f060008 }",
                "int styleable ActionBarLayout_android_layout 0",
                "int styleable ActionBarLayout_android_layout_gravity 1",
                "int[] styleable PercentLayout_Layout { }",
                "int styleable PercentLayout_Layout_layout_aspectRatio 9",
                "int styleable PercentLayout_Layout_layout_heightPercent 1")));

    SortedSetMultimap<String, RDotTxtEntry> packageNameToResources =
        MergeAndroidResourcesStep.sortSymbols(
            entriesBuilder.buildFilePathToPackageNameSet(),
            Optional.empty(),
            ImmutableMap.of(),
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            entriesBuilder.getProjectFilesystem(),
            false);

    assertEquals(23, packageNameToResources.size());

    ArrayList<RDotTxtEntry> resources =
        new ArrayList<>(packageNameToResources.get(sharedPackageName));
    assertEquals(23, resources.size());

    System.out.println(resources);

    ImmutableList<FakeRDotTxtEntryWithID> fakeRDotTxtEntryWithIDS =
        ImmutableList.of(
            new FakeRDotTxtEntryWithID(INT, ATTR, "android_layout_gravity", "0x07f01005"),
            new FakeRDotTxtEntryWithID(INT, ATTR, "background", "0x07f01006"),
            new FakeRDotTxtEntryWithID(INT, ATTR, "backgroundSplit", "0x07f01007"),
            new FakeRDotTxtEntryWithID(INT, ATTR, "backgroundStacked", "0x07f01008"),
            new FakeRDotTxtEntryWithID(INT, ATTR, "buttonPanelSideLayout", "0x07f01001"),
            new FakeRDotTxtEntryWithID(INT, ATTR, "layout_heightPercent", "0x07f01009"),
            new FakeRDotTxtEntryWithID(INT, ATTR, "listLayout", "0x07f01002"),
            new FakeRDotTxtEntryWithID(INT, ID, "a1", "0x07f01003"),
            new FakeRDotTxtEntryWithID(INT, ID, "a2", "0x07f01004"),
            new FakeRDotTxtEntryWithID(
                INT_ARRAY, STYLEABLE, "ActionBar", "{ 0x07f01006,0x07f01007,0x07f01008 }"),
            new FakeRDotTxtEntryWithID(INT, STYLEABLE, "ActionBar_background", "0"),
            new FakeRDotTxtEntryWithID(INT, STYLEABLE, "ActionBar_backgroundSplit", "1"),
            new FakeRDotTxtEntryWithID(INT, STYLEABLE, "ActionBar_backgroundStacked", "2"),
            new FakeRDotTxtEntryWithID(
                INT_ARRAY, STYLEABLE, "ActionBarLayout", "{ 0x010100f2,0x07f01005 }"),
            new FakeRDotTxtEntryWithID(INT, STYLEABLE, "ActionBarLayout_android_layout", "0"),
            new FakeRDotTxtEntryWithID(
                INT, STYLEABLE, "ActionBarLayout_android_layout_gravity", "1"),
            new FakeRDotTxtEntryWithID(
                INT_ARRAY, STYLEABLE, "AlertDialog", "{ 0x010100f2,0x07f01001,0x7f01003b }"),
            new FakeRDotTxtEntryWithID(INT, STYLEABLE, "AlertDialog_android_layout", "0"),
            new FakeRDotTxtEntryWithID(INT, STYLEABLE, "AlertDialog_buttonPanelSideLayout", "1"),
            new FakeRDotTxtEntryWithID(INT, STYLEABLE, "AlertDialog_multiChoiceItemLayout", "2"),
            new FakeRDotTxtEntryWithID(
                INT_ARRAY, STYLEABLE, "PercentLayout_Layout", "{ 0,0x07f01009 }"),
            new FakeRDotTxtEntryWithID(
                INT, STYLEABLE, "PercentLayout_Layout_layout_aspectRatio", "0"),
            new FakeRDotTxtEntryWithID(
                INT, STYLEABLE, "PercentLayout_Layout_layout_heightPercent", "1"));

    IntStream.range(0, resources.size())
        .forEach(
            action -> {
              assertEquals(fakeRDotTxtEntryWithIDS.get(action), resources.get(action));
            });
  }

  @Test
  public void testGenerateRDotJavaForMultipleSymbolsFilesWithDuplicates()
      throws IOException, DuplicateResourceException {
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();

    // Merge everything into the same package space.
    String sharedPackageName = "com.facebook.abc";
    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "a-R.txt",
            ImmutableList.of("int id a1 0x7f010001", "int string a1 0x7f020001")));

    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "b-R.txt",
            ImmutableList.of(
                "int id a1 0x7f010001", "int string a1 0x7f010002", "int string c1 0x7f010003")));

    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            "c-R.txt",
            ImmutableList.of(
                "int id a1 0x7f010001",
                "int string a1 0x7f010002",
                "int string b1 0x7f010003",
                "int string c1 0x7f010004")));

    thrown.expect(DuplicateResourceException.class);
    thrown.expectMessage("Resource 'a1' (string) is duplicated across: ");
    thrown.expectMessage("Resource 'c1' (string) is duplicated across: ");

    BuildTarget resTarget = BuildTargetFactory.newInstance("//:res1");
    SourcePathRuleFinder ruleFinder =
        new SourcePathRuleFinder(
            new BuildRuleResolver(
                TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
    MergeAndroidResourcesStep.sortSymbols(
        entriesBuilder.buildFilePathToPackageNameSet(),
        Optional.empty(),
        ImmutableMap.of(
            entriesBuilder.getProjectFilesystem().getPath("a-R.txt"),
            (HasAndroidResourceDeps)
                AndroidResourceRuleBuilder.newBuilder()
                    .setRuleFinder(ruleFinder)
                    .setBuildTarget(resTarget)
                    .setRes(new FakeSourcePath("a/res"))
                    .setRDotJavaPackage("com.res.a")
                    .build(),
            entriesBuilder.getProjectFilesystem().getPath("b-R.txt"),
            (HasAndroidResourceDeps)
                AndroidResourceRuleBuilder.newBuilder()
                    .setRuleFinder(ruleFinder)
                    .setBuildTarget(resTarget)
                    .setRes(new FakeSourcePath("b/res"))
                    .setRDotJavaPackage("com.res.b")
                    .build(),
            entriesBuilder.getProjectFilesystem().getPath("c-R.txt"),
            (HasAndroidResourceDeps)
                AndroidResourceRuleBuilder.newBuilder()
                    .setRuleFinder(ruleFinder)
                    .setBuildTarget(resTarget)
                    .setRes(new FakeSourcePath("c/res"))
                    .setRDotJavaPackage("com.res.c")
                    .build()),
        /* bannedDuplicateResourceTypes */ EnumSet.of(RType.STRING),
        entriesBuilder.getProjectFilesystem(),
        false);
  }

  @Test
  public void testGenerateRDotJavaForLibrary() throws IOException {
    BuildTarget resTarget = BuildTargetFactory.newInstance("//:res1");
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res1",
            BuildTargets.getGenPath(
                    entriesBuilder.getProjectFilesystem(), resTarget, "__%s_text_symbols__/R.txt")
                .toString(),
            ImmutableList.of("int id id1 0x7f020000")));

    FakeProjectFilesystem filesystem = entriesBuilder.getProjectFilesystem();

    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    AndroidResource res =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(resTarget)
            .setRes(new FakeSourcePath("res"))
            .setRDotJavaPackage("com.res1")
            .build();
    buildRuleResolver.addToIndex(res);

    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            filesystem,
            resolver,
            ImmutableList.of(res),
            Paths.get("output"),
            /* forceFinalResourceIds */ false,
            /* unionPackage */ Optional.empty(),
            /* rName */ Optional.empty(),
            /* useOldStyleableFormat */ false);

    ExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    // Verify that the correct Java code is generated.
    assertThat(
        filesystem.readFileIfItExists(Paths.get("output/com/res1/R.java")).get(),
        CoreMatchers.containsString("{\n    public static int id1=0x07f01001;"));
  }

  @Test
  public void testGenerateRDotJavaForOneSymbolsFile() throws IOException {
    BuildTarget target = BuildTargetFactory.newInstance("//android_res/com/facebook/http:res");
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();
    String symbolsFile =
        BuildTargets.getGenPath(
                entriesBuilder.getProjectFilesystem(), target, "__%s_text_symbols__/R.txt")
            .toString();
    String rDotJavaPackage = "com.facebook";
    final ImmutableList<String> outputTextSymbols =
        ImmutableList.<String>builder()
            .add("int id placeholder 0x7f020000")
            .add("int string debug_http_proxy_dialog_title 0x7f030004")
            .add("int string debug_http_proxy_hint 0x7f030005")
            .add("int string debug_http_proxy_summary 0x7f030003")
            .add("int string debug_http_proxy_title 0x7f030002")
            .add("int string debug_ssl_cert_check_summary 0x7f030001")
            .add("int string debug_ssl_cert_check_title 0x7f030000")
            .add("int styleable SherlockMenuItem_android_visible 4")
            .add(
                "int[] styleable SherlockMenuView { 0x7f010026, 0x7f010027, 0x7f010028, 0x7f010029, "
                    + "0x7f01002a, 0x7f01002b, 0x7f01002c, 0x7f01002d }")
            .build();
    entriesBuilder.add(new RDotTxtFile(rDotJavaPackage, symbolsFile, outputTextSymbols));

    FakeProjectFilesystem filesystem = entriesBuilder.getProjectFilesystem();

    Path uberRDotTxt = filesystem.resolve("R.txt").toAbsolutePath();
    filesystem.writeLinesToPath(outputTextSymbols, uberRDotTxt);

    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    AndroidResource resource =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(target)
            .setRes(new FakeSourcePath("res"))
            .setRDotJavaPackage("com.facebook")
            .build();
    buildRuleResolver.addToIndex(resource);

    MergeAndroidResourcesStep mergeStep =
        new MergeAndroidResourcesStep(
            filesystem,
            resolver,
            ImmutableList.of(resource),
            Optional.of(uberRDotTxt),
            Paths.get("output"),
            /* forceFinalResourceIds */ true,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            /* unionPackage */ Optional.empty(),
            /* rName */ Optional.empty(),
            /* useOldStyleableFormat */ false);

    ExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    // Verify that the correct Java code is generated.
    assertEquals(
        "package com.facebook;\n"
            + "\n"
            + "public class R {\n"
            + "  public static class id {\n"
            + "    public static final int placeholder=0x7f020000;\n"
            + "  }\n"
            + "\n"
            + "  public static class string {\n"
            + "    public static final int debug_http_proxy_dialog_title=0x7f030004;\n"
            + "    public static final int debug_http_proxy_hint=0x7f030005;\n"
            + "    public static final int debug_http_proxy_summary=0x7f030003;\n"
            + "    public static final int debug_http_proxy_title=0x7f030002;\n"
            + "    public static final int debug_ssl_cert_check_summary=0x7f030001;\n"
            + "    public static final int debug_ssl_cert_check_title=0x7f030000;\n"
            + "  }\n"
            + "\n"
            + "  public static class styleable {\n"
            + "    public static final int SherlockMenuItem_android_visible=4;\n"
            + "    public static final int[] SherlockMenuView={ 0x7f010026, 0x7f010027, 0x7f010028, "
            + "0x7f010029, 0x7f01002a, 0x7f01002b, 0x7f01002c, 0x7f01002d };\n"
            + "  }\n"
            + "\n"
            + "}\n",
        filesystem
            .readFileIfItExists(Paths.get("output/com/facebook/R.java"))
            .get()
            .replace("\r", ""));
  }

  @Test
  public void testGenerateRDotJavaForCustomDrawables() throws IOException {
    BuildTarget target = BuildTargetFactory.newInstance("//android_res/com/facebook/http:res");
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();
    String symbolsFile =
        BuildTargets.getGenPath(
                entriesBuilder.getProjectFilesystem(), target, "__%s_text_symbols__/R.txt")
            .toString();
    String rDotJavaPackage = "com.facebook";
    final ImmutableList<String> outputTextSymbols =
        ImmutableList.<String>builder()
            .add("int drawable android_drawable 0x7f010000")
            .add("int drawable fb_drawable 0x7f010001 #")
            .build();
    entriesBuilder.add(new RDotTxtFile(rDotJavaPackage, symbolsFile, outputTextSymbols));

    FakeProjectFilesystem filesystem = entriesBuilder.getProjectFilesystem();

    Path uberRDotTxt = filesystem.resolve("R.txt").toAbsolutePath();
    filesystem.writeLinesToPath(outputTextSymbols, uberRDotTxt);

    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    AndroidResource resource =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(target)
            .setRes(new FakeSourcePath("res"))
            .setRDotJavaPackage("com.facebook")
            .build();
    buildRuleResolver.addToIndex(resource);

    MergeAndroidResourcesStep mergeStep =
        new MergeAndroidResourcesStep(
            filesystem,
            resolver,
            ImmutableList.of(resource),
            Optional.of(uberRDotTxt),
            Paths.get("output"),
            /* forceFinalResourceIds */ true,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            /* unionPackage */ Optional.empty(),
            /* rName */ Optional.empty(),
            /* useOldStyleableFormat */ false);

    ExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    // Verify that the correct Java code is generated.
    assertEquals(
        "package com.facebook;\n"
            + "\n"
            + "public class R {\n"
            + "  public static class drawable {\n"
            + "    public static final int android_drawable=0x7f010000;\n"
            + "    public static final int fb_drawable=0x7f010001;\n"
            + "  }\n"
            + "\n"
            + "  public static final int[] custom_drawables = { 0x7f010001 };\n"
            + "\n"
            + "}\n",
        filesystem
            .readFileIfItExists(Paths.get("output/com/facebook/R.java"))
            .get()
            .replace("\r", ""));
  }

  @Test
  public void testGenerateRDotJavaWithResourceUnionPackage() throws IOException {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    BuildTarget res2Target = BuildTargetFactory.newInstance("//:res2");
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res1",
            BuildTargets.getGenPath(
                    entriesBuilder.getProjectFilesystem(), res1Target, "__%s_text_symbols__/R.txt")
                .toString(),
            ImmutableList.of("int id id1 0x7f020000")));
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res2",
            BuildTargets.getGenPath(
                    entriesBuilder.getProjectFilesystem(), res2Target, "__%s_text_symbols__/R.txt")
                .toString(),
            ImmutableList.of("int id id2 0x7f020000")));

    FakeProjectFilesystem filesystem = entriesBuilder.getProjectFilesystem();

    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(res1Target)
            .setRes(new FakeSourcePath("res1"))
            .setRDotJavaPackage("res1")
            .build();
    buildRuleResolver.addToIndex(res1);

    AndroidResource res2 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(res2Target)
            .setRes(new FakeSourcePath("res2"))
            .setRDotJavaPackage("res2")
            .build();
    buildRuleResolver.addToIndex(res2);

    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            filesystem,
            resolver,
            ImmutableList.of(res1, res2),
            Paths.get("output"),
            /* forceFinalResourceIds */ false,
            Optional.of("res1"),
            /* rName */ Optional.empty(),
            /* useOldStyleableFormat */ false);

    ExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    String res1java = filesystem.readFileIfItExists(Paths.get("output/res1/R.java")).get();
    String res2java = filesystem.readFileIfItExists(Paths.get("output/res2/R.java")).get();
    assertThat(res1java, StringContains.containsString("id1"));
    assertThat(res1java, StringContains.containsString("id2"));
    assertThat(res2java, CoreMatchers.not(StringContains.containsString("id1")));
    assertThat(res2java, StringContains.containsString("id2"));
  }

  @Test
  public void testGenerateRDotJavaWithPreviouslyEmptyResourceUnionPackage() throws IOException {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res1",
            BuildTargets.getGenPath(
                    entriesBuilder.getProjectFilesystem(), res1Target, "__%s_text_symbols__/R.txt")
                .toString(),
            ImmutableList.of("int id id1 0x7f020000")));
    FakeProjectFilesystem filesystem = entriesBuilder.getProjectFilesystem();
    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(res1Target)
            .setRes(new FakeSourcePath("res1"))
            .setRDotJavaPackage("res1")
            .build();
    buildRuleResolver.addToIndex(res1);

    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            filesystem,
            resolver,
            ImmutableList.of(res1),
            Paths.get("output"),
            /* forceFinalResourceIds */ false,
            Optional.of("resM"),
            /* rName */ Optional.empty(),
            /* useOldStyleableFormat */ false);

    ExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    String res1java = filesystem.readFileIfItExists(Paths.get("output/res1/R.java")).get();
    String resMjava = filesystem.readFileIfItExists(Paths.get("output/resM/R.java")).get();
    assertThat(res1java, StringContains.containsString("id1"));
    assertThat(resMjava, StringContains.containsString("id1"));
  }

  @Test
  public void testGenerateRDotJavaWithRName() throws IOException {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder();
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res1",
            BuildTargets.getGenPath(
                    entriesBuilder.getProjectFilesystem(), res1Target, "__%s_text_symbols__/R.txt")
                .toString(),
            ImmutableList.of("int id id1 0x7f020000", "int id id2 0x7f020002")));

    FakeProjectFilesystem filesystem = entriesBuilder.getProjectFilesystem();

    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(res1Target)
            .setRes(new FakeSourcePath("res1"))
            .setRDotJavaPackage("res1")
            .build();
    buildRuleResolver.addToIndex(res1);

    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            filesystem,
            resolver,
            ImmutableList.of(res1),
            Paths.get("output"),
            /* forceFinalResourceIds */ true,
            Optional.of("res1"),
            Optional.of("R2"),
            /* useOldStyleableFormat */ false);

    ExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    String resR2Java = filesystem.readFileIfItExists(Paths.get("output/res1/R2.java")).get();
    assertThat(resR2Java, StringContains.containsString("static final int id1=0x07f01001;"));
    assertThat(resR2Java, StringContains.containsString("static final int id2=0x07f01002;"));
  }

  @Test
  public void testDuplicateBanning() throws Exception {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    BuildTarget res2Target = BuildTargetFactory.newInstance("//:res2");

    BuildRuleResolver buildRuleResolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(buildRuleResolver);
    SourcePathResolver resolver = new SourcePathResolver(ruleFinder);

    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);
    entriesBuilder.add(
        new RDotTxtFile(
            "package",
            BuildTargets.getGenPath(filesystem, res1Target, "__%s_text_symbols__/R.txt").toString(),
            ImmutableList.of(
                "int string app_name 0x7f020000", "int drawable android_drawable 0x7f010000")));
    entriesBuilder.add(
        new RDotTxtFile(
            "package",
            BuildTargets.getGenPath(filesystem, res2Target, "__%s_text_symbols__/R.txt").toString(),
            ImmutableList.of(
                "int string app_name 0x7f020000", "int drawable android_drawable 0x7f010000")));

    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(res1Target)
            .setRes(new FakeSourcePath("res1"))
            .setRDotJavaPackage("package")
            .build();
    buildRuleResolver.addToIndex(res1);

    AndroidResource res2 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(ruleFinder)
            .setBuildTarget(res2Target)
            .setRes(new FakeSourcePath("res2"))
            .setRDotJavaPackage("package")
            .build();
    buildRuleResolver.addToIndex(res2);

    ImmutableList<HasAndroidResourceDeps> resourceDeps = ImmutableList.of(res1, res2);

    checkDuplicatesDetected(
        resolver,
        filesystem,
        resourceDeps,
        EnumSet.noneOf(RType.class),
        ImmutableList.of(),
        ImmutableList.of("app_name", "android_drawable"));

    checkDuplicatesDetected(
        resolver,
        filesystem,
        resourceDeps,
        EnumSet.of(RType.STRING),
        ImmutableList.of("app_name"),
        ImmutableList.of("android_drawable"));

    checkDuplicatesDetected(
        resolver,
        filesystem,
        resourceDeps,
        EnumSet.allOf(RType.class),
        ImmutableList.of("app_name", "android_drawable"),
        ImmutableList.of());
  }

  private void checkDuplicatesDetected(
      SourcePathResolver resolver,
      FakeProjectFilesystem filesystem,
      ImmutableList<HasAndroidResourceDeps> resourceDeps,
      EnumSet<RType> rtypes,
      ImmutableList<String> duplicateResources,
      ImmutableList<String> ignoredDuplicates) {
    MergeAndroidResourcesStep mergeStep =
        new MergeAndroidResourcesStep(
            filesystem,
            resolver,
            resourceDeps,
            /* uberRDotTxt */ Optional.empty(),
            Paths.get("output"),
            true,
            rtypes,
            Optional.empty(),
            Optional.empty(),
            /* useOldStyleableFormat */ false);

    StepExecutionResult result = mergeStep.execute(TestExecutionContext.newInstance());
    String message = result.getStderr().orElse("");
    if (duplicateResources.isEmpty()) {
      assertEquals(0, result.getExitCode());
    } else {
      assertNotEquals(0, result.getExitCode());
      assertThat(message, Matchers.containsString("duplicated"));
    }
    for (String duplicateResource : duplicateResources) {
      assertThat(message, Matchers.containsString(duplicateResource));
    }
    for (String ignoredDuplicate : ignoredDuplicates) {
      assertThat(message, Matchers.not(Matchers.containsString(ignoredDuplicate)));
    }
  }

  // sortSymbols has a goofy API.  This will help.
  private static class RDotTxtEntryBuilder {
    private final FakeProjectFilesystem filesystem;
    private final ImmutableMap.Builder<Path, String> filePathToPackageName = ImmutableMap.builder();

    RDotTxtEntryBuilder() {
      this(new FakeProjectFilesystem());
    }

    RDotTxtEntryBuilder(FakeProjectFilesystem filesystem) {
      this.filesystem = filesystem;
    }

    public void add(RDotTxtFile entry) throws IOException {
      filesystem.writeLinesToPath(entry.contents, entry.filePath);
      filePathToPackageName.put(entry.filePath, entry.packageName);
    }

    Map<Path, String> buildFilePathToPackageNameSet() {
      return filePathToPackageName.build();
    }

    public FakeProjectFilesystem getProjectFilesystem() {
      return filesystem;
    }
  }

  static class RDotTxtFile {
    public ImmutableList<String> contents;

    String packageName;
    Path filePath;

    RDotTxtFile(String packageName, String filePath, ImmutableList<String> contents) {
      this.packageName = packageName;
      this.filePath = Paths.get(filePath);
      this.contents = contents;
    }
  }
}
