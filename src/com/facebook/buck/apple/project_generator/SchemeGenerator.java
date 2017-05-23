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

package com.facebook.buck.apple.project_generator;

import com.facebook.buck.apple.SchemeActionType;
import com.facebook.buck.apple.xcode.XCScheme;
import com.facebook.buck.apple.xcode.xcodeproj.PBXTarget;
import com.facebook.buck.io.MoreProjectFilesystems;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.log.Logger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Collects target references and generates an xcscheme.
 *
 * <p>To register entries in the scheme, clients must add:
 *
 * <ul>
 *   <li>associations between buck targets and Xcode targets
 *   <li>associations between Xcode targets and the projects that contain them
 * </ul>
 *
 * <p>Both of these values can be pulled out of {@link ProjectGenerator}.
 */
class SchemeGenerator {
  private static final Logger LOG = Logger.get(SchemeGenerator.class);

  private final ProjectFilesystem projectFilesystem;
  private final Optional<PBXTarget> primaryTarget;
  private final ImmutableSet<PBXTarget> orderedBuildTargets;
  private final ImmutableSet<PBXTarget> orderedBuildTestTargets;
  private final ImmutableSet<PBXTarget> orderedRunTestTargets;
  private final String schemeName;
  private final Path outputDirectory;
  private final boolean parallelizeBuild;
  private final Optional<String> runnablePath;
  private final Optional<String> remoteRunnablePath;
  private final ImmutableMap<SchemeActionType, String> actionConfigNames;
  private final ImmutableMap<PBXTarget, Path> targetToProjectPathMap;
  private Optional<XCScheme> outputScheme = Optional.empty();
  private final XCScheme.LaunchAction.LaunchStyle launchStyle;

  public SchemeGenerator(
      ProjectFilesystem projectFilesystem,
      Optional<PBXTarget> primaryTarget,
      ImmutableSet<PBXTarget> orderedBuildTargets,
      ImmutableSet<PBXTarget> orderedBuildTestTargets,
      ImmutableSet<PBXTarget> orderedRunTestTargets,
      String schemeName,
      Path outputDirectory,
      boolean parallelizeBuild,
      Optional<String> runnablePath,
      Optional<String> remoteRunnablePath,
      ImmutableMap<SchemeActionType, String> actionConfigNames,
      ImmutableMap<PBXTarget, Path> targetToProjectPathMap,
      XCScheme.LaunchAction.LaunchStyle launchStyle) {
    this.projectFilesystem = projectFilesystem;
    this.primaryTarget = primaryTarget;
    this.launchStyle = launchStyle;
    this.orderedBuildTargets = orderedBuildTargets;
    this.orderedBuildTestTargets = orderedBuildTestTargets;
    this.orderedRunTestTargets = orderedRunTestTargets;
    this.schemeName = schemeName;
    this.outputDirectory = outputDirectory;
    this.parallelizeBuild = parallelizeBuild;
    this.runnablePath = runnablePath;
    this.remoteRunnablePath = remoteRunnablePath;
    this.actionConfigNames = actionConfigNames;
    this.targetToProjectPathMap = targetToProjectPathMap;

    LOG.debug(
        "Generating scheme with build targets %s, test build targets %s, test bundle targets %s",
        orderedBuildTargets, orderedBuildTestTargets, orderedRunTestTargets);
  }

  @VisibleForTesting
  Optional<XCScheme> getOutputScheme() {
    return outputScheme;
  }

  public Path writeScheme() throws IOException {
    Map<PBXTarget, XCScheme.BuildableReference> buildTargetToBuildableReferenceMap =
        new HashMap<>();

    for (PBXTarget target : Iterables.concat(orderedBuildTargets, orderedBuildTestTargets)) {
      String blueprintName = target.getProductName();
      if (blueprintName == null) {
        blueprintName = target.getName();
      }
      Path outputPath = outputDirectory.getParent();
      String buildableReferencePath;
      Path projectPath = Preconditions.checkNotNull(targetToProjectPathMap.get(target));
      if (outputPath == null) {
        //Root directory project
        buildableReferencePath = projectPath.toString();
      } else {
        buildableReferencePath = outputPath.relativize(projectPath).toString();
      }

      XCScheme.BuildableReference buildableReference =
          new XCScheme.BuildableReference(
              buildableReferencePath,
              Preconditions.checkNotNull(target.getGlobalID()),
              target.getProductReference() != null
                  ? target.getProductReference().getName()
                  : Preconditions.checkNotNull(target.getProductName()),
              blueprintName);
      buildTargetToBuildableReferenceMap.put(target, buildableReference);
    }

    XCScheme.BuildAction buildAction = new XCScheme.BuildAction(parallelizeBuild);

    // For aesthetic reasons put all non-test build actions before all test build actions.
    for (PBXTarget target : orderedBuildTargets) {
      addBuildActionForBuildTarget(
          buildTargetToBuildableReferenceMap.get(target),
          XCScheme.BuildActionEntry.BuildFor.DEFAULT,
          buildAction);
    }

    for (PBXTarget target : orderedBuildTestTargets) {
      addBuildActionForBuildTarget(
          buildTargetToBuildableReferenceMap.get(target),
          XCScheme.BuildActionEntry.BuildFor.TEST_ONLY,
          buildAction);
    }

    XCScheme.TestAction testAction =
        new XCScheme.TestAction(
            Preconditions.checkNotNull(actionConfigNames.get(SchemeActionType.TEST)));
    for (PBXTarget target : orderedRunTestTargets) {
      XCScheme.BuildableReference buildableReference =
          buildTargetToBuildableReferenceMap.get(target);
      XCScheme.TestableReference testableReference =
          new XCScheme.TestableReference(buildableReference);
      testAction.addTestableReference(testableReference);
    }

    Optional<XCScheme.LaunchAction> launchAction = Optional.empty();
    Optional<XCScheme.ProfileAction> profileAction = Optional.empty();

    if (primaryTarget.isPresent()) {
      XCScheme.BuildableReference primaryBuildableReference =
          buildTargetToBuildableReferenceMap.get(primaryTarget.get());
      if (primaryBuildableReference != null) {
        launchAction =
            Optional.of(
                new XCScheme.LaunchAction(
                    primaryBuildableReference,
                    Preconditions.checkNotNull(actionConfigNames.get(SchemeActionType.LAUNCH)),
                    runnablePath,
                    remoteRunnablePath,
                    launchStyle));
        profileAction =
            Optional.of(
                new XCScheme.ProfileAction(
                    primaryBuildableReference,
                    Preconditions.checkNotNull(actionConfigNames.get(SchemeActionType.PROFILE))));
      }
    }
    XCScheme.AnalyzeAction analyzeAction =
        new XCScheme.AnalyzeAction(
            Preconditions.checkNotNull(actionConfigNames.get(SchemeActionType.ANALYZE)));
    XCScheme.ArchiveAction archiveAction =
        new XCScheme.ArchiveAction(
            Preconditions.checkNotNull(actionConfigNames.get(SchemeActionType.ARCHIVE)));

    XCScheme scheme =
        new XCScheme(
            schemeName,
            Optional.of(buildAction),
            Optional.of(testAction),
            launchAction,
            profileAction,
            Optional.of(analyzeAction),
            Optional.of(archiveAction));
    outputScheme = Optional.of(scheme);

    Path schemeDirectory = outputDirectory.resolve("xcshareddata/xcschemes");
    projectFilesystem.mkdirs(schemeDirectory);
    Path schemePath = schemeDirectory.resolve(schemeName + ".xcscheme");
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      serializeScheme(scheme, outputStream);
      String contentsToWrite = outputStream.toString();
      if (MoreProjectFilesystems.fileContentsDiffer(
          new ByteArrayInputStream(contentsToWrite.getBytes(Charsets.UTF_8)),
          schemePath,
          projectFilesystem)) {
        projectFilesystem.writeContentsToPath(outputStream.toString(), schemePath);
      }
    }
    return schemePath;
  }

  private static void addBuildActionForBuildTarget(
      XCScheme.BuildableReference buildableReference,
      EnumSet<XCScheme.BuildActionEntry.BuildFor> buildFor,
      XCScheme.BuildAction buildAction) {
    XCScheme.BuildActionEntry entry = new XCScheme.BuildActionEntry(buildableReference, buildFor);
    buildAction.addBuildAction(entry);
  }

  public static Element serializeBuildableReference(
      Document doc, XCScheme.BuildableReference buildableReference) {
    Element refElem = doc.createElement("BuildableReference");
    refElem.setAttribute("BuildableIdentifier", "primary");
    refElem.setAttribute("BlueprintIdentifier", buildableReference.getBlueprintIdentifier());
    refElem.setAttribute("BuildableName", buildableReference.getBuildableName());
    refElem.setAttribute("BlueprintName", buildableReference.getBlueprintName());
    String referencedContainer = "container:" + buildableReference.getContainerRelativePath();
    refElem.setAttribute("ReferencedContainer", referencedContainer);
    return refElem;
  }

  public static Element serializeBuildAction(Document doc, XCScheme.BuildAction buildAction) {
    Element buildActionElem = doc.createElement("BuildAction");
    buildActionElem.setAttribute(
        "parallelizeBuildables", buildAction.getParallelizeBuild() ? "YES" : "NO");
    buildActionElem.setAttribute(
        "buildImplicitDependencies", buildAction.getParallelizeBuild() ? "YES" : "NO");

    Element buildActionEntriesElem = doc.createElement("BuildActionEntries");
    buildActionElem.appendChild(buildActionEntriesElem);

    for (XCScheme.BuildActionEntry entry : buildAction.getBuildActionEntries()) {
      Element entryElem = doc.createElement("BuildActionEntry");
      buildActionEntriesElem.appendChild(entryElem);

      EnumSet<XCScheme.BuildActionEntry.BuildFor> buildFor = entry.getBuildFor();
      boolean buildForRunning = buildFor.contains(XCScheme.BuildActionEntry.BuildFor.RUNNING);
      entryElem.setAttribute("buildForRunning", buildForRunning ? "YES" : "NO");
      boolean buildForTesting = buildFor.contains(XCScheme.BuildActionEntry.BuildFor.TESTING);
      entryElem.setAttribute("buildForTesting", buildForTesting ? "YES" : "NO");
      boolean buildForProfiling = buildFor.contains(XCScheme.BuildActionEntry.BuildFor.PROFILING);
      entryElem.setAttribute("buildForProfiling", buildForProfiling ? "YES" : "NO");
      boolean buildForArchiving = buildFor.contains(XCScheme.BuildActionEntry.BuildFor.ARCHIVING);
      entryElem.setAttribute("buildForArchiving", buildForArchiving ? "YES" : "NO");
      boolean buildForAnalyzing = buildFor.contains(XCScheme.BuildActionEntry.BuildFor.ANALYZING);
      entryElem.setAttribute("buildForAnalyzing", buildForAnalyzing ? "YES" : "NO");

      Element refElem = serializeBuildableReference(doc, entry.getBuildableReference());
      entryElem.appendChild(refElem);
    }

    return buildActionElem;
  }

  public static Element serializeTestAction(Document doc, XCScheme.TestAction testAction) {
    Element testActionElem = doc.createElement("TestAction");
    testActionElem.setAttribute("shouldUseLaunchSchemeArgsEnv", "YES");

    Element testablesElem = doc.createElement("Testables");
    testActionElem.appendChild(testablesElem);

    for (XCScheme.TestableReference testable : testAction.getTestables()) {
      Element testableElem = doc.createElement("TestableReference");
      testablesElem.appendChild(testableElem);
      testableElem.setAttribute("skipped", "NO");

      Element refElem = serializeBuildableReference(doc, testable.getBuildableReference());
      testableElem.appendChild(refElem);
    }

    return testActionElem;
  }

  public static Element serializeLaunchAction(Document doc, XCScheme.LaunchAction launchAction) {
    Element launchActionElem = doc.createElement("LaunchAction");

    Optional<String> runnablePath = launchAction.getRunnablePath();
    Optional<String> remoteRunnablePath = launchAction.getRemoteRunnablePath();
    if (remoteRunnablePath.isPresent()) {
      Element remoteRunnableElem = doc.createElement("RemoteRunnable");
      remoteRunnableElem.setAttribute("runnableDebuggingMode", "2");
      remoteRunnableElem.setAttribute("BundleIdentifier", "com.apple.springboard");
      remoteRunnableElem.setAttribute("RemotePath", remoteRunnablePath.get());
      launchActionElem.appendChild(remoteRunnableElem);
      Element refElem = serializeBuildableReference(doc, launchAction.getBuildableReference());
      remoteRunnableElem.appendChild(refElem);

      // Yes, this appears to be duplicated in Xcode as well..
      Element refElem2 = serializeBuildableReference(doc, launchAction.getBuildableReference());
      launchActionElem.appendChild(refElem2);
    } else if (runnablePath.isPresent()) {
      Element pathRunnableElem = doc.createElement("PathRunnable");
      launchActionElem.appendChild(pathRunnableElem);
      pathRunnableElem.setAttribute("FilePath", runnablePath.get());
    } else {
      Element productRunnableElem = doc.createElement("BuildableProductRunnable");
      launchActionElem.appendChild(productRunnableElem);
      Element refElem = serializeBuildableReference(doc, launchAction.getBuildableReference());
      productRunnableElem.appendChild(refElem);
    }

    XCScheme.LaunchAction.LaunchStyle launchStyle = launchAction.getLaunchStyle();
    launchActionElem.setAttribute(
        "launchStyle", launchStyle == XCScheme.LaunchAction.LaunchStyle.AUTO ? "0" : "1");

    return launchActionElem;
  }

  public static Element serializeProfileAction(Document doc, XCScheme.ProfileAction profileAction) {
    Element profileActionElem = doc.createElement("ProfileAction");

    Element productRunnableElem = doc.createElement("BuildableProductRunnable");
    profileActionElem.appendChild(productRunnableElem);

    Element refElem = serializeBuildableReference(doc, profileAction.getBuildableReference());
    productRunnableElem.appendChild(refElem);

    return profileActionElem;
  }

  private static void serializeScheme(XCScheme scheme, OutputStream stream) {
    DocumentBuilder docBuilder;
    Transformer transformer;
    try {
      docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      transformer = TransformerFactory.newInstance().newTransformer();
    } catch (ParserConfigurationException | TransformerConfigurationException e) {
      throw new RuntimeException(e);
    }

    DOMImplementation domImplementation = docBuilder.getDOMImplementation();
    Document doc = domImplementation.createDocument(null, "Scheme", null);
    doc.setXmlVersion("1.0");

    Element rootElem = doc.getDocumentElement();
    rootElem.setAttribute("LastUpgradeVersion", "9999");
    rootElem.setAttribute("version", "1.7");

    Optional<XCScheme.BuildAction> buildAction = scheme.getBuildAction();
    if (buildAction.isPresent()) {
      Element buildActionElem = serializeBuildAction(doc, buildAction.get());
      rootElem.appendChild(buildActionElem);
    }

    Optional<XCScheme.TestAction> testAction = scheme.getTestAction();
    if (testAction.isPresent()) {
      Element testActionElem = serializeTestAction(doc, testAction.get());
      testActionElem.setAttribute(
          "buildConfiguration", scheme.getTestAction().get().getBuildConfiguration());
      rootElem.appendChild(testActionElem);
    }

    Optional<XCScheme.LaunchAction> launchAction = scheme.getLaunchAction();
    if (launchAction.isPresent()) {
      Element launchActionElem = serializeLaunchAction(doc, launchAction.get());
      launchActionElem.setAttribute(
          "buildConfiguration", launchAction.get().getBuildConfiguration());
      rootElem.appendChild(launchActionElem);
    } else {
      Element launchActionElem = doc.createElement("LaunchAction");
      launchActionElem.setAttribute("buildConfiguration", "Debug");
      rootElem.appendChild(launchActionElem);
    }

    Optional<XCScheme.ProfileAction> profileAction = scheme.getProfileAction();
    if (profileAction.isPresent()) {
      Element profileActionElem = serializeProfileAction(doc, profileAction.get());
      profileActionElem.setAttribute(
          "buildConfiguration", profileAction.get().getBuildConfiguration());
      rootElem.appendChild(profileActionElem);
    } else {
      Element profileActionElem = doc.createElement("ProfileAction");
      profileActionElem.setAttribute("buildConfiguration", "Release");
      rootElem.appendChild(profileActionElem);
    }

    Optional<XCScheme.AnalyzeAction> analyzeAction = scheme.getAnalyzeAction();
    if (analyzeAction.isPresent()) {
      Element analyzeActionElem = doc.createElement("AnalyzeAction");
      analyzeActionElem.setAttribute(
          "buildConfiguration", analyzeAction.get().getBuildConfiguration());
      rootElem.appendChild(analyzeActionElem);
    } else {
      Element analyzeActionElem = doc.createElement("AnalyzeAction");
      analyzeActionElem.setAttribute("buildConfiguration", "Debug");
      rootElem.appendChild(analyzeActionElem);
    }

    Optional<XCScheme.ArchiveAction> archiveAction = scheme.getArchiveAction();
    if (archiveAction.isPresent()) {
      Element archiveActionElem = doc.createElement("ArchiveAction");
      archiveActionElem.setAttribute(
          "buildConfiguration", archiveAction.get().getBuildConfiguration());
      archiveActionElem.setAttribute("revealArchiveInOrganizer", "YES");
      rootElem.appendChild(archiveActionElem);
    } else {
      Element archiveActionElem = doc.createElement("ArchiveAction");
      archiveActionElem.setAttribute("buildConfiguration", "Release");
      rootElem.appendChild(archiveActionElem);
    }

    // write out

    DOMSource source = new DOMSource(doc);
    StreamResult result = new StreamResult(stream);

    try {
      transformer.transform(source, result);
    } catch (TransformerException e) {
      throw new RuntimeException(e);
    }
  }
}
