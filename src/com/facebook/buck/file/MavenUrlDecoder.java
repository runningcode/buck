/*
 * Copyright 2014-present Facebook, Inc.
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

import com.facebook.buck.util.HumanReadableException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsible for converting a maven URL to an HTTP or HTTPS url. The format of a maven URL is:
 *
 * <pre>
 *   mvn:optionalServer:group:id:type:classifier:version
 * </pre>
 *
 * If the {@code optionalServer} is omitted, the default one configured in "download -&gt;
 * maven_repo" in the project's {@code .buckconfig} is used, or an exception is thrown. The
 * optionalServer URL is expected to be a valid http or https {@link java.net.URL}.
 *
 * <p>Examples of valid mvn URLs:
 *
 * <pre>
 *   mvn:org.seleniumhq.selenium:selenium-java:jar:2.42.2
 *   mvn:http://repo1.maven.org/maven2:org.seleniumhq.selenium:selenium-java:jar:2.42.2
 * </pre>
 */
public class MavenUrlDecoder {
  @VisibleForTesting
  private static final Pattern URL_PATTERN =
      Pattern.compile(
          "((?<host>^https?://.+?):)?"
              + "(?<group>[^:]+)"
              + ":(?<id>[^:]+)"
              + ":(?<type>[^:]+)"
              + "(:(?<classifier>[^:]+))?"
              + ":(?<version>[^:]+)$");

  private MavenUrlDecoder() {
    // Utility class
  }

  public static URI toHttpUrl(Optional<String> mavenRepo, URI uri) {
    Preconditions.checkArgument("mvn".equals(uri.getScheme()), "URI must start with mvn: " + uri);
    Preconditions.checkArgument(
        mavenRepo.isPresent(),
        "You must specify the maven repo in the \"download->maven_repo\" section of your "
            + ".buckconfig");

    String repo = mavenRepo.get();

    if (!repo.endsWith("/")) {
      repo += "/";
    }

    Matcher matcher = URL_PATTERN.matcher(uri.getSchemeSpecificPart());

    if (!matcher.matches()) {
      throw new HumanReadableException("Unable to parse: " + uri);
    }

    String host = matcher.group("host");
    if (Strings.isNullOrEmpty(host)) {
      host = repo;
    }
    String group = matcher.group("group").replace('.', '/');
    String artifactId = matcher.group("id");
    String type = matcher.group("type");
    String version = matcher.group("version");
    Optional<String> classifier = Optional.ofNullable(matcher.group("classifier"));

    if (!host.endsWith("/")) {
      host += "/";
    }

    try {
      String plainUri =
          String.format(
              "%s%s/%s/%s/%s",
              host, group, artifactId, version, fileNameFor(artifactId, version, type, classifier));
      URI generated = new URI(plainUri);
      if ("https".equals(generated.getScheme()) || "http".equals(generated.getScheme())) {
        return generated;
      }
      throw new HumanReadableException(
          "Can only download maven artifacts over HTTP or HTTPS: %s", generated);
    } catch (URISyntaxException e) {
      throw new HumanReadableException("Unable to parse URL: " + uri);
    }
  }

  private static String fileNameFor(
      String artifactId, String version, String type, Optional<String> classifier) {
    StringBuilder sb = new StringBuilder();
    sb.append(artifactId);
    sb.append('-');
    sb.append(version);
    if (classifier.isPresent()) {
      sb.append('-');
      sb.append(classifier.get());
    }
    sb.append(fileExtensionFor(type));
    return sb.toString();
  }

  private static String fileExtensionFor(String type) {
    switch (type) {
      case "jar":
        return ".jar";

      case "aar":
        return ".aar";

      case "src":
        return "-sources.jar";

      default:
        return String.format("-%s.jar", type);
    }
  }
}
