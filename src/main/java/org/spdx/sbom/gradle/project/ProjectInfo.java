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
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.immutables.serial.Serial;
import org.immutables.value.Value.Immutable;
import org.spdx.sbom.gradle.internal.AndroidVersionLoader;

@Immutable
@Serial.Version(2)
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

  // only set when the project has exactly one resolvable Maven publication
  Optional<MavenCoordinates> getMavenCoordinates();

  @Immutable
  @Serial.Version(1)
  interface MavenCoordinates {
    String getGroup();

    String getArtifact();

    String getVersion();
  }

  static ProjectInfo from(Project project) {
    return ImmutableProjectInfo.builder()
        .name(project.getName())
        .description(Optional.ofNullable(project.getDescription()))
        .version(version(project))
        .projectDirectory(project.getProjectDir())
        .path(project.getPath())
        .group(project.getGroup().toString())
        .mavenCoordinates(mavenCoordinates(project))
        .build();
  }

  static Optional<MavenCoordinates> mavenCoordinates(Project project) {
    var publishing = project.getExtensions().findByType(PublishingExtension.class);
    if (publishing == null) {
      return Optional.empty();
    }
    var publications = publishing.getPublications().withType(MavenPublication.class);
    if (publications.size() != 1) {
      return Optional.empty();
    }
    var publication = publications.iterator().next();
    String group = publication.getGroupId();
    String artifact = publication.getArtifactId();
    String version = publication.getVersion();
    if (group == null
        || group.isBlank()
        || artifact == null
        || artifact.isBlank()
        || version == null
        || version.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        ImmutableMavenCoordinates.builder()
            .group(group)
            .artifact(artifact)
            .version(version)
            .build());
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
