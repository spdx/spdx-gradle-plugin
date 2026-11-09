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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.immutables.serial.Serial;
import org.immutables.value.Value.Immutable;
import org.spdx.sbom.gradle.internal.AndroidVersionLoader;
import org.spdx.sbom.gradle.maven.PomInfo;

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

  // populated from maven-publish POM licenses when available
  List<PomInfo.LicenseInfo> getLicenses();

  static ProjectInfo from(Project project) {
    ImmutableProjectInfo.Builder builder =
        ImmutableProjectInfo.builder()
            .name(project.getName())
            .description(Optional.ofNullable(project.getDescription()))
            .version(version(project))
            .projectDirectory(project.getProjectDir())
            .path(project.getPath())
            .group(project.getGroup().toString());
    populateMavenPublishLicenses(project, builder);
    return builder.build();
  }

  private static void populateMavenPublishLicenses(
      Project project, ImmutableProjectInfo.Builder builder) {
    PublishingExtension publishing = project.getExtensions().findByType(PublishingExtension.class);
    if (publishing == null) {
      return;
    }
    Set<List<PomInfo.LicenseInfo>> distinct = new HashSet<>();
    for (MavenPublication pub : publishing.getPublications().withType(MavenPublication.class)) {
      if (pub.getPom() instanceof MavenPomInternal pom) {
        List<PomInfo.LicenseInfo> licenses =
            pom.getLicenses().stream()
                .map(l -> PomInfo.LicenseInfo.of(l.getName().getOrNull(), l.getUrl().getOrNull()))
                .filter(l -> l.getName() != null || l.getUrl() != null)
                .toList();
        if (!licenses.isEmpty()) {
          distinct.add(licenses);
        }
      }
    }
    if (distinct.size() == 1) {
      builder.addAllLicenses(distinct.iterator().next());
    } else if (distinct.size() > 1) {
      project
          .getLogger()
          .warn(
              "Multiple MavenPublications in {} declare different licenses; using NOASSERTION",
              project.getPath());
    }
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
