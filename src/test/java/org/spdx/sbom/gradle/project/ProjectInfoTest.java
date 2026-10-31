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
package org.spdx.sbom.gradle.project;

import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.spdx.sbom.gradle.project.ProjectInfo.MavenCoordinates;

class ProjectInfoTest {

  @Test
  void mavenCoordinatesFromSingleMavenPublication() {
    Project project = publishingProject();
    publishing(project)
        .getPublications()
        .create(
            "maven",
            MavenPublication.class,
            publication -> publication.setArtifactId("custom-artifact"));

    Optional<MavenCoordinates> coordinates = ProjectInfo.from(project).getMavenCoordinates();

    Assertions.assertTrue(coordinates.isPresent());
    Assertions.assertEquals("com.example", coordinates.get().getGroup());
    Assertions.assertEquals("custom-artifact", coordinates.get().getArtifact());
    Assertions.assertEquals("1.2.3", coordinates.get().getVersion());
  }

  @Test
  void noMavenCoordinatesWithoutMavenPublication() {
    Assertions.assertTrue(ProjectInfo.from(publishingProject()).getMavenCoordinates().isEmpty());
  }

  @Test
  void noMavenCoordinatesWithMultipleMavenPublications() {
    Project project = publishingProject();
    publishing(project)
        .getPublications()
        .create(
            "first",
            MavenPublication.class,
            publication -> publication.setArtifactId("first-artifact"));
    publishing(project)
        .getPublications()
        .create(
            "second",
            MavenPublication.class,
            publication -> publication.setArtifactId("second-artifact"));

    Assertions.assertTrue(ProjectInfo.from(project).getMavenCoordinates().isEmpty());
  }

  private static Project publishingProject() {
    Project project = ProjectBuilder.builder().build();
    project.setGroup("com.example");
    project.setVersion("1.2.3");
    project.getPluginManager().apply("maven-publish");
    return project;
  }

  private static PublishingExtension publishing(Project project) {
    return project.getExtensions().getByType(PublishingExtension.class);
  }
}
