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
package org.spdx.sbom.gradle;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.provider.Provider;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.spdx.sbom.gradle.utils.ProjectInfo;

/** A plugin to generate spdx sboms. */
public class SpdxSbomPlugin implements Plugin<Project> {
  public void apply(Project project) {
    // Register the spdx sbom task with defaults
    project
        .getTasks()
        .register(
            "spdxSbom",
            SpdxSbomTask.class,
            t -> {
              t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("spdx"));
              t.getProjectInfo().set(ProjectInfo.from(project));
              t.getAllProjects().set(ProjectInfo.from(project.getRootProject().getAllprojects()));
              Provider<Set<ResolvedArtifactResult>> artifacts =
                  project
                      .getConfigurations()
                      .getByName("runtimeClasspath")
                      .getIncoming()
                      .getArtifacts()
                      .getResolvedArtifacts();
              t.getResolvedArtifacts().set(artifacts.map(new ArtifactTransformer()));
              Provider<ResolvedComponentResult> rootComponent =
                  project
                      .getConfigurations()
                      .getByName("runtimeClasspath")
                      .getIncoming()
                      .getResolutionResult()
                      .getRootComponent();

              Configuration pomsConfig =
                  project
                      .getConfigurations()
                      .detachedConfiguration(
                          project
                              .getConfigurations()
                              .getByName("runtimeClasspath")
                              .getIncoming()
                              .getResolutionResult()
                              .getAllComponents()
                              .stream()
                              .filter(rcr -> rcr.getId() instanceof ModuleComponentIdentifier)
                              .map(rcr -> rcr.getId().getDisplayName() + "@pom")
                              .map(pom -> project.getDependencies().create(pom))
                              .toArray(Dependency[]::new));
              t.getPoms()
                  .set(
                      pomsConfig
                          .getIncoming()
                          .getArtifacts()
                          .getResolvedArtifacts()
                          .map(new ArtifactTransformer()));

              t.getRootComponent().set(rootComponent);
              t.getMavenRepositories()
                  .set(
                      project.provider(
                          () ->
                              project
                                  .getRepositories()
                                  .getAsMap()
                                  .entrySet()
                                  .stream()
                                  .filter(e -> e.getValue() instanceof MavenArtifactRepository)
                                  .map(Entry::getKey)
                                  .collect(
                                      Collectors.toMap(
                                          e -> e,
                                          e ->
                                              ((MavenArtifactRepository)
                                                      project.getRepositories().getByName(e))
                                                  .getUrl()))));
            });
  }

  private static class ArtifactTransformer
      implements Transformer<
          Map<ComponentArtifactIdentifier, File>, Collection<ResolvedArtifactResult>> {

    @Override
    public Map<ComponentArtifactIdentifier, File> transform(
        Collection<ResolvedArtifactResult> resolvedArtifactResults) {
      return resolvedArtifactResults
          .stream()
          // ignore gradle API components as they cannot be serialized
          .filter(x -> !(x.getId().getComponentIdentifier() instanceof OpaqueComponentIdentifier))
          .collect(Collectors.toMap(ArtifactResult::getId, ResolvedArtifactResult::getFile));
    }
  }
}
