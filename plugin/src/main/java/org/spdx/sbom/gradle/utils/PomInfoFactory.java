/*
 * Copyright 2023 The Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spdx.sbom.gradle.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.logging.Logger;

public class PomInfoFactory {

  private final Map<ComponentIdentifier, File> poms;
  private final Logger logger;

  public PomInfoFactory(Logger logger, Map<ComponentIdentifier, File> poms) {
    this.poms = poms;
    this.logger = logger;
  }

  public static PomInfoFactory newFactory(
      Logger logger, Map<ComponentArtifactIdentifier, File> poms) {
    return new PomInfoFactory(
        logger,
        poms.entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().getComponentIdentifier(), Entry::getValue)));
  }

  public PomInfo getInfo(ComponentIdentifier componentIdentifier)
      throws XmlPullParserException, IOException {

    File pomFile = poms.get(componentIdentifier);
    MavenProject mavenProject =
        new MavenProject(new MavenXpp3Reader().read(Files.newInputStream(pomFile.toPath())));
    return ImmutablePomInfo.builder()
        .addAllLicenses(mavenProject.getLicenses())
        .homepage(extractHomepage(mavenProject, componentIdentifier))
        .build();
  }

  private URI extractHomepage(MavenProject mavenProject, ComponentIdentifier componentIdentifier) {
    String url = mavenProject.getUrl();
    if (url == null) {
      return URI.create("");
    }
    try {
      return new URI(mavenProject.getUrl());
    } catch (URISyntaxException error) {
      logger.warn(
          "Ignoring invalid url detected in project '"
              + componentIdentifier.getDisplayName()
              + "': "
              + url);
      return URI.create("");
    }
  }
}
