/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.maven;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.java.MavenPublishable;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.UnflavoredBuildTarget;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.util.artifact.SubArtifact;

public class Publisher {

  public static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2";
  private static final URL MAVEN_CENTRAL;

  static {
    try {
      MAVEN_CENTRAL = new URL(MAVEN_CENTRAL_URL);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Logger LOG = Logger.get(Publisher.class);

  private final ServiceLocator locator;
  private final LocalRepository localRepo;
  private final RemoteRepository remoteRepo;
  private final boolean dryRun;

  public Publisher(
      ProjectFilesystem repositoryFilesystem,
      Optional<URL> remoteRepoUrl,
      Optional<String> username,
      Optional<String> password,
      boolean dryRun) {
    this(repositoryFilesystem.getRootPath(), remoteRepoUrl, username, password, dryRun);
  }

  /**
   * @param localRepoPath Typically obtained as {@link
   *     com.facebook.buck.io.ProjectFilesystem#getRootPath}
   * @param remoteRepoUrl Canonically {@link #MAVEN_CENTRAL_URL}
   * @param dryRun if true, a dummy {@link DeployResult} will be returned, with the fully
   *     constructed {@link DeployRequest}. No actual publishing will happen
   */
  public Publisher(
      Path localRepoPath,
      Optional<URL> remoteRepoUrl,
      Optional<String> username,
      Optional<String> password,
      boolean dryRun) {
    this.localRepo = new LocalRepository(localRepoPath.toFile());
    this.remoteRepo =
        AetherUtil.toRemoteRepository(remoteRepoUrl.orElse(MAVEN_CENTRAL), username, password);
    this.locator = AetherUtil.initServiceLocator();
    this.dryRun = dryRun;
  }

  public ImmutableSet<DeployResult> publish(
      SourcePathResolver pathResolver, ImmutableSet<MavenPublishable> publishables)
      throws DeploymentException {
    ImmutableListMultimap<UnflavoredBuildTarget, UnflavoredBuildTarget> duplicateBuiltinBuileRules =
        checkForDuplicatePackagedDeps(publishables);
    if (duplicateBuiltinBuileRules.size() > 0) {
      StringBuilder sb = new StringBuilder();
      sb.append("Duplicate transitive dependencies for publishable libraries found!  This means");
      sb.append(StandardSystemProperty.LINE_SEPARATOR);
      sb.append("that the following libraries would have multiple copies if these libraries were");
      sb.append(StandardSystemProperty.LINE_SEPARATOR);
      sb.append("used together.  The can be resolved by adding a maven URL to each target listed");
      sb.append(StandardSystemProperty.LINE_SEPARATOR);
      sb.append("below:");
      for (UnflavoredBuildTarget unflavoredBuildTarget : duplicateBuiltinBuileRules.keySet()) {
        sb.append(StandardSystemProperty.LINE_SEPARATOR);
        sb.append(unflavoredBuildTarget.getFullyQualifiedName());
        sb.append(" (referenced by these build targets: ");
        Joiner.on(", ").appendTo(sb, duplicateBuiltinBuileRules.get(unflavoredBuildTarget));
        sb.append(")");
      }
      throw new DeploymentException(sb.toString());
    }

    ImmutableSet.Builder<DeployResult> deployResultBuilder = ImmutableSet.builder();
    for (MavenPublishable publishable : publishables) {
      DefaultArtifact coords =
          new DefaultArtifact(
              Preconditions.checkNotNull(
                  publishable.getMavenCoords().get(),
                  "No maven coordinates specified for published rule ",
                  publishable));
      Path relativePathToOutput =
          pathResolver.getRelativePath(
              Preconditions.checkNotNull(
                  publishable.getSourcePathToOutput(),
                  "No path to output present in ",
                  publishable));
      File mainItem = publishable.getProjectFilesystem().resolve(relativePathToOutput).toFile();

      if (!coords.getClassifier().isEmpty()) {
        deployResultBuilder.add(publish(coords, ImmutableList.of(mainItem)));
      }

      try {
        // If this is the "main" artifact (denoted by lack of classifier) generate and publish
        // pom alongside
        File pom = Pom.generatePomFile(pathResolver, publishable).toFile();
        deployResultBuilder.add(publish(coords, ImmutableList.of(mainItem, pom)));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return deployResultBuilder.build();
  }

  /**
   * Checks for any packaged dependencies that exist between more than one of the targets that we
   * are trying to publish.
   *
   * @return A multimap of dependency build targets and the publishable build targets that have them
   *     included in the final package that will be uploaded.
   */
  private ImmutableListMultimap<UnflavoredBuildTarget, UnflavoredBuildTarget>
      checkForDuplicatePackagedDeps(ImmutableSet<MavenPublishable> publishables) {
    // First build the multimap of the builtin dependencies and the publishable targets that use
    // them.
    Multimap<UnflavoredBuildTarget, UnflavoredBuildTarget> builtinDeps = HashMultimap.create();
    for (MavenPublishable publishable : publishables) {
      for (BuildRule buildRule : publishable.getPackagedDependencies()) {
        builtinDeps.put(
            buildRule.getBuildTarget().getUnflavoredBuildTarget(),
            publishable.getBuildTarget().getUnflavoredBuildTarget());
      }
    }
    // Now, check for any duplicate uses, and if found, return them.
    ImmutableListMultimap.Builder<UnflavoredBuildTarget, UnflavoredBuildTarget> builder =
        ImmutableListMultimap.builder();
    for (UnflavoredBuildTarget buildTarget : builtinDeps.keySet()) {
      Collection<UnflavoredBuildTarget> publishablesUsingBuildTarget = builtinDeps.get(buildTarget);
      if (publishablesUsingBuildTarget.size() > 1) {
        builder.putAll(buildTarget, publishablesUsingBuildTarget);
      }
    }
    return builder.build();
  }

  public DeployResult publish(
      String groupId, String artifactId, String version, List<File> toPublish)
      throws DeploymentException {
    return publish(new DefaultArtifact(groupId, artifactId, "", version), toPublish);
  }

  /**
   * @param descriptor an {@link Artifact}, holding the maven coordinates for the published files
   *     less the extension that is to be derived from the files. The {@code descriptor} itself will
   *     not be published as is, and the {@link File} attached to it (if any) will be ignored.
   * @param toPublish {@link File}(s) to be published using the given coordinates. The filename
   *     extension of each given file will be used as a maven "extension" coordinate
   */
  public DeployResult publish(Artifact descriptor, List<File> toPublish)
      throws DeploymentException {
    String providedExtension = descriptor.getExtension();
    if (!providedExtension.isEmpty()) {
      LOG.warn(
          "Provided extension %s of artifact %s to be published will be ignored. The extensions "
              + "of the provided file(s) will be used",
          providedExtension, descriptor);
    }
    List<Artifact> artifacts = new ArrayList<>(toPublish.size());
    for (File file : toPublish) {
      artifacts.add(
          new SubArtifact(
              descriptor,
              descriptor.getClassifier(),
              Files.getFileExtension(file.getAbsolutePath()),
              file));
    }
    return publish(artifacts);
  }

  /**
   * @param toPublish each {@link Artifact} must contain a file, that will be published under maven
   *     coordinates in the corresponding {@link Artifact}.
   * @see Artifact#setFile
   */
  public DeployResult publish(List<Artifact> toPublish) throws DeploymentException {
    RepositorySystem repoSys =
        Preconditions.checkNotNull(locator.getService(RepositorySystem.class));

    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    session.setLocalRepositoryManager(repoSys.newLocalRepositoryManager(session, localRepo));
    session.setReadOnly();

    DeployRequest deployRequest = createDeployRequest(toPublish);

    if (dryRun) {
      return new DeployResult(deployRequest)
          .setArtifacts(toPublish)
          .setMetadata(deployRequest.getMetadata());
    } else {
      return repoSys.deploy(session, deployRequest);
    }
  }

  private DeployRequest createDeployRequest(List<Artifact> toPublish) {
    DeployRequest deployRequest = new DeployRequest().setRepository(remoteRepo);
    for (Artifact artifact : toPublish) {
      File file = artifact.getFile();
      Preconditions.checkNotNull(file);
      Preconditions.checkArgument(file.exists(), "No such file: %s", file.getAbsolutePath());

      deployRequest.addArtifact(artifact);
    }
    return deployRequest;
  }
}
