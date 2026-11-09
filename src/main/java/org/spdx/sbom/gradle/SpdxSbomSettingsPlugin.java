/*
 * Copyright 2026 The Project Authors.
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.ProviderFactory;
import org.spdx.sbom.gradle.project.ProjectInfo;
import org.spdx.sbom.gradle.project.ProjectInfoService;

public abstract class SpdxSbomSettingsPlugin implements Plugin<Settings> {
  private final ProviderFactory providerFactory;

  @Inject
  public SpdxSbomSettingsPlugin(ProviderFactory providerFactory) {
    this.providerFactory = providerFactory;
  }

  @Override
  public void apply(Settings settings) {
    Set<ProjectInfo> allProjects = ConcurrentHashMap.newKeySet();

    settings
        .getGradle()
        .addProjectEvaluationListener(
            new ProjectEvaluationListener() {
              @Override
              public void beforeEvaluate(Project project) {}

              @Override
              public void afterEvaluate(Project project, ProjectState state) {
                if (state.getFailure() == null) {
                  // Defer to pass 2 so build-script afterEvaluate runs first.
                  project.afterEvaluate(p -> allProjects.add(ProjectInfo.from(p)));
                }
              }
            });

    settings
        .getGradle()
        .getSharedServices()
        .registerIfAbsent(
            ProjectInfoService.SERVICE_NAME,
            ProjectInfoService.class,
            spec ->
                spec.getParameters()
                    .getAllProjects()
                    .set(providerFactory.provider(() -> Set.copyOf(allProjects))));
  }
}
