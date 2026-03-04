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
package org.spdx.sbom.gradle.project;

import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.immutables.serial.Serial;
import org.immutables.value.Value.Immutable;
import org.spdx.sbom.gradle.internal.AndroidVersionLoader;

@Immutable
@Serial.Version(1)
public interface ProjectInfo {

  String VERSION_UNKNOWN = "unknown";
  String VERSION_UNSPECIFIED = "unspecified";

  String getName();

  // is not required and is not available in project isolation mode
  Optional<String> getDescription();

  // is required but is not available in project isolation mode
  String getVersion();

  File getProjectDirectory();

  String getPath();

  // is not required and is not available in project isolation mode
  Optional<String> getGroup();

  static ProjectInfo from(Project project) {
    return ImmutableProjectInfo.builder()
        .name(project.getName())
        .description(Optional.ofNullable(project.getDescription()))
        .version(version(project))
        .projectDirectory(project.getProjectDir())
        .path(project.getPath())
        .group(project.getGroup().toString())
        .build();
  }

  static ProjectInfo from(Project project, Provider<Boolean> isProjectIsolated) {
    if (isProjectIsolated.getOrElse(false)) {
      var isolated = project.getIsolated();
      return ImmutableProjectInfo.builder()
          .name(isolated.getName())
          .version(VERSION_UNKNOWN)
          .projectDirectory(isolated.getProjectDirectory().getAsFile())
          .path(isolated.getPath())
          .build();
    }

    return from(project);
  }

  static String version(Project project) {
    String version = project.getVersion().toString();

    if (project.getPlugins().hasPlugin("com.android.application")) {
      String androidVersion = new AndroidVersionLoader().getApplicationVersion(project);
      if (androidVersion != null) {
        version = androidVersion;
      }
    }

    return version;
  }

  static Set<ProjectInfo> from(Set<Project> projects, Provider<Boolean> isProjectIsolated) {
    return projects.stream()
        .map(p -> ProjectInfo.from(p, isProjectIsolated))
        .collect(Collectors.toSet());
  }
}
