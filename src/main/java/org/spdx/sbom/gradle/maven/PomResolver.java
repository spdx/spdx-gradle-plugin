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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.logging.Logger;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;

/** This needs to be run *before* while configuring the task, so use it in the Plugin. */
public class PomResolver {
  private final DefaultModelBuilderFactory defaultModelBuilderFactory;
  private final DependencyHandler dependencies;
  private final GradleMavenResolver gradleMavenResolver;
  private final Logger logger;

  public static PomResolver newPomResolver(
      DependencyHandler dependencies, ConfigurationContainer configurations, Logger logger) {
    return new PomResolver(
        dependencies,
        new GradleMavenResolver(dependencies, configurations),
        new DefaultModelBuilderFactory(),
        logger);
  }

  PomResolver(
      DependencyHandler dependencies,
      GradleMavenResolver gradleMavenResolver,
      DefaultModelBuilderFactory defaultModelBuilderFactory,
      Logger logger) {
    this.dependencies = dependencies;
    this.defaultModelBuilderFactory = defaultModelBuilderFactory;
    this.gradleMavenResolver = gradleMavenResolver;
    this.logger = logger;
  }

  /**
   * resolvePomArtifacts identifies and collects the component identifiers for dependencies in a
   * project's dependency graph, focusing specifically on Maven POM artifacts.
   *
   * <p>Process:
   *
   * <ol>
   *   <li><b>Identifying Component Identifiers:</b> The process starts by identifying the component
   *       identifiers of all dependencies in a project's dependency graph. This is achieved by
   *       iterating over the root components (instances of {@link
   *       org.gradle.api.artifacts.result.ResolvedComponentResult}) of the dependency graph. Each
   *       root component is analyzed to extract its dependencies and their respective component
   *       identifiers.
   *   <li><b>Resolving Dependencies:</b> With the component identifiers collected, the class then
   *       proceeds to resolve these dependencies. This is done using the {@link
   *       org.gradle.api.artifacts.dsl.DependencyHandler} to create an artifact resolution query.
   *       The query targets the collected component identifiers and specifies the types of
   *       artifacts to resolve, in this case, Maven POM artifacts (represented by {@link
   *       org.gradle.maven.MavenPomArtifact}).
   *   <li><b>Collecting Artifacts:</b> The final output is a list of {@link
   *       org.gradle.api.artifacts.result.ResolvedArtifactResult} instances, representing the
   *       resolved Maven POM artifacts. Each ResolvedArtifactResult provides access to the artifact
   *       file and metadata.
   * </ol>
   *
   * <p>Result: The outcome is a list of {@link
   * org.gradle.api.artifacts.result.ResolvedArtifactResult}, which provides access to the files of
   * resolved Maven POM artifacts, giving detailed insight into the project's Maven dependencies.
   *
   * @see org.gradle.api.artifacts.dsl.DependencyHandler
   * @see org.gradle.api.artifacts.result.ResolvedArtifactResult
   */
  @SuppressWarnings("unchecked")
  public List<ResolvedArtifactResult> resolvePomArtifacts(
      List<ResolvedComponentResult> rootComponents) {
    Set<ComponentIdentifier> componentIds = gatherSelectedDependencies(rootComponents);

    return dependencies
        .createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(MavenModule.class, MavenPomArtifact.class)
        .execute()
        .getResolvedComponents()
        .stream()
        .flatMap(
            componentArtifactsResult ->
                componentArtifactsResult.getArtifacts(MavenPomArtifact.class).stream())
        .filter(ResolvedArtifactResult.class::isInstance)
        .map(ResolvedArtifactResult.class::cast)
        .collect(Collectors.toList());
  }

  private Set<ComponentIdentifier> gatherSelectedDependencies(
      List<ResolvedComponentResult> rootComponents) {
    Set<ComponentIdentifier> componentIds = new HashSet<>();
    for (var rootComponent : rootComponents) {
      componentIds.addAll(
          gatherSelectedDependencies(rootComponent, new HashSet<>(), new HashSet<>()));
    }
    return componentIds;
  }

  private Set<ComponentIdentifier> gatherSelectedDependencies(
      ResolvedComponentResult component,
      Set<ResolvedComponentResult> seenComponents,
      Set<ComponentIdentifier> componentIds) {
    if (seenComponents.add(component)) {
      for (DependencyResult dep : component.getDependencies()) {
        if (dep instanceof ResolvedDependencyResult) {
          ResolvedDependencyResult resolvedDep = (ResolvedDependencyResult) dep;
          componentIds.add(resolvedDep.getSelected().getId());
          gatherSelectedDependencies(resolvedDep.getSelected(), seenComponents, componentIds);
        }
      }
    }

    return componentIds;
  }

  public Map<String, PomInfo> effectivePoms(List<ResolvedArtifactResult> resolvedPomArtifacts) {
    Map<String, PomInfo> effectivePoms = new TreeMap<>();
    for (var ra : resolvedPomArtifacts) {
      var pomFile = ra.getFile();
      Model model = resolveEffectivePom(pomFile);
      effectivePoms.put(
          ra.getId().getComponentIdentifier().getDisplayName(),
          ImmutablePomInfo.builder()
              .addAllLicenses(
                  model.getLicenses().stream()
                      .map(
                          l ->
                              ImmutableLicenseInfo.builder()
                                  .name(OptionalOfNonEmpty(l.getName()).orElse("NOASSERTION"))
                                  .url(OptionalOfNonEmpty(l.getUrl()).orElse("NOASSERTION"))
                                  .build())
                      .collect(Collectors.toList()))
              .homepage(extractHomepage(model, ra.getId().getComponentIdentifier()))
              .organization(Optional.ofNullable(model.getOrganization()))
              .addAllDevelopers(
                  model.getDevelopers().stream()
                      .map(
                          d ->
                              ImmutableDeveloperInfo.builder()
                                  .name(OptionalOfNonEmpty(d.getName()))
                                  .email(OptionalOfNonEmpty(d.getEmail()))
                                  .organization(OptionalOfNonEmpty(d.getOrganization()))
                                  .build())
                      .collect(Collectors.toList()))
              .build());
    }
    return new LinkedHashMap<>(effectivePoms);
  }

  private Optional<String> OptionalOfNonEmpty(String s) {
    if (s == null) return Optional.empty();

    String trimmed = s.trim();
    if (trimmed.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(s);
    }
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
