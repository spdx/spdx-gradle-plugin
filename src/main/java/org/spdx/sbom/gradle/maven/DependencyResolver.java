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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;

/**
 * DependencyResolver identifies and collects the component identifiers for dependencies in a project's dependency
 * graph, focusing specifically on Maven artifacts. It filters out non-serializable Gradle API components
 * to concentrate on Maven dependencies.
 *
 * <p>Process:</p>
 * <ol>
 *     <li><b>Identifying Component Identifiers:</b>
 *         The process starts by identifying the component identifiers of all dependencies in a project's
 *         dependency graph. This is achieved by iterating over the root components (instances of
 *         {@link org.gradle.api.artifacts.result.ResolvedComponentResult}) of the dependency graph. Each
 *         root component is analyzed to extract its dependencies and their respective component identifiers.</li>
 *
 *     <li><b>Resolving Dependencies:</b>
 *         With the component identifiers collected, the class then proceeds to resolve these dependencies.
 *         This is done using the {@link org.gradle.api.artifacts.dsl.DependencyHandler} to create an
 *         artifact resolution query. The query targets the collected component identifiers and specifies
 *         the types of artifacts to resolve, in this case, Maven POM artifacts (represented by
 *         {@link org.gradle.maven.MavenPomArtifact}).</li>
 *
 *     <li><b>Filtering and Collecting Artifacts:</b>
 *         The resolved artifacts are then processed to filter out non-serializable Gradle components
 *         (identified by {@link org.gradle.internal.component.local.model.OpaqueComponentIdentifier}).
 *         This step ensures the focus remains on Maven artifacts. The final output is a list of
 *         {@link org.gradle.api.artifacts.result.ResolvedArtifactResult} instances, representing the
 *         resolved Maven POM artifacts. Each ResolvedArtifactResult provides access to the artifact file
 *         and metadata.</li>
 * </ol>
 *
 * <p>Result:</p>
 * The outcome is a list of {@link org.gradle.api.artifacts.result.ResolvedArtifactResult}, which provides
 * access to the files of resolved Maven POM artifacts, giving detailed insight into the project's Maven dependencies.
 *
 * @see org.gradle.api.artifacts.dsl.DependencyHandler
 * @see org.gradle.api.artifacts.result.ResolvedArtifactResult
 */
public class DependencyResolver {
  private final DependencyHandler dependencies;

  public static DependencyResolver newDependencyResolver(DependencyHandler dependencies) {
    return new DependencyResolver(dependencies);
  }

  DependencyResolver(DependencyHandler dependencies) {
    this.dependencies = dependencies;
  }

  @SuppressWarnings("unchecked")
  public List<ResolvedArtifactResult> resolveDependencies(
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
        // ignore gradle API components as they cannot be serialized
        .filter(x -> !(x.getId().getComponentIdentifier() instanceof OpaqueComponentIdentifier))
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
}
