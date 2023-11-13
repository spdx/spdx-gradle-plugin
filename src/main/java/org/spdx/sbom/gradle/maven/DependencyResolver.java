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
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;

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
