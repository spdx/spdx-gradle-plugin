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
package org.spdx.sbom.gradle.maven;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.logging.Logger;

/** This needs to be run *before* while configuring the task, so use it in the Plugin. */
public class PomResolver {
  private final DefaultModelBuilderFactory defaultModelBuilderFactory;
  private final GradleMavenResolver gradleMavenResolver;
  private final Logger logger;

  public static PomResolver newPomResolver(Project project) {
    return new PomResolver(
        new GradleMavenResolver(project), new DefaultModelBuilderFactory(), project.getLogger());
  }

  PomResolver(
      GradleMavenResolver gradleMavenResolver,
      DefaultModelBuilderFactory defaultModelBuilderFactory,
      Logger logger) {
    this.defaultModelBuilderFactory = defaultModelBuilderFactory;
    this.gradleMavenResolver = gradleMavenResolver;
    this.logger = logger;
  }

  public Map<String, PomInfo> effectivePoms(Configuration pomsConfig) {
    Map<String, PomInfo> effectivePoms = new HashMap<>();
    for (var ra : pomsConfig.getIncoming().getArtifacts().getResolvedArtifacts().get()) {
      var pomFile = ra.getFile();
      Model model = resolveEffectivePom(pomFile);
      effectivePoms.put(
          ra.getId().getComponentIdentifier().getDisplayName(),
          ImmutablePomInfo.builder()
              .addAllLicenses(
                  model
                      .getLicenses()
                      .stream()
                      .map(
                          l ->
                              ImmutableLicenseInfo.builder()
                                  .name(Optional.ofNullable(l.getName()).orElse("NOASSERTION"))
                                  .url(Optional.ofNullable(l.getUrl()).orElse("NOASSERTION"))
                                  .build())
                      .collect(Collectors.toList()))
              .homepage(extractHomepage(model, ra.getId().getComponentIdentifier()))
              .build());
    }
    return effectivePoms;
  }

  private Model resolveEffectivePom(File pomFile) {
    ModelBuildingRequest request = new DefaultModelBuildingRequest();
    request.setPomFile(pomFile);
    request.setModelResolver(gradleMavenResolver);
    // projects appears to read system properties in their pom(?), I dunno why
    request.getSystemProperties().putAll(System.getProperties());
    request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

    DefaultModelBuilder builder = defaultModelBuilderFactory.newInstance();
    try {
      return builder.build(request).getEffectiveModel();
    } catch (ModelBuildingException e) {
      throw new GradleException("Could not determine effective POM", e);
    }
  }

  private URI extractHomepage(Model mavenModel, ComponentIdentifier componentIdentifier) {
    String url = mavenModel.getUrl();
    if (url == null) {
      return URI.create("");
    }
    try {
      return new URI(mavenModel.getUrl());
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
