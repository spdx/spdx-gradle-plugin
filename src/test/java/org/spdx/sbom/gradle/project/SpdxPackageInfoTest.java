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

import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.spdx.sbom.gradle.SpdxPackageExtension;

class SpdxPackageInfoTest {

  @Test
  void from_null() {
    Assertions.assertThrows(NullPointerException.class, () -> SpdxPackageInfo.from(null));
  }

  @Test
  void from_valid() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply("org.spdx.sbom-package");
    project
        .getExtensions()
        .configure(
            SpdxPackageExtension.class,
            ext -> {
              ext.getName().set("test-name");
              ext.getVersion().set("test-version");
              ext.getSupplier().set("test-supplier");
              ext.getCreatePackage().set(false);
              ext.getScm().getUri().set("git.test.com");
              ext.getScm().getRevision().set("test-revision");
              ext.getScm().getTool().set("cvs");
            });

    var ext = SpdxPackageInfo.from(project.getExtensions().getByType(SpdxPackageExtension.class));
    Assertions.assertEquals("test-name", ext.getName());
    Assertions.assertEquals("test-version", ext.getVersion());
    Assertions.assertFalse(ext.getCreatePackage());
    Assertions.assertEquals(Optional.of("test-supplier"), ext.getSupplier());
    Assertions.assertEquals(
        Optional.of(ScmInfo.from("cvs", "git.test.com", "test-revision")), ext.getScmInfo());
  }

  @Test
  void from_defaults() {
    Project project = ProjectBuilder.builder().withName("test-name").build();
    project.setVersion("test-version");
    project.getPlugins().apply("org.spdx.sbom-package");

    var ext = SpdxPackageInfo.from(project.getExtensions().getByType(SpdxPackageExtension.class));
    Assertions.assertEquals("test-name", ext.getName());
    Assertions.assertEquals("test-version", ext.getVersion());
    Assertions.assertTrue(ext.getCreatePackage());
    Assertions.assertEquals(Optional.empty(), ext.getSupplier());
    Assertions.assertEquals(Optional.empty(), ext.getScmInfo());
  }

  @Test
  void from_scmErrorNoRevision() {
    Project project = ProjectBuilder.builder().withName("test-name").build();
    project.setVersion("test-version");
    project.getPlugins().apply("org.spdx.sbom-package");

    project
        .getExtensions()
        .configure(
            SpdxPackageExtension.class,
            ext -> {
              ext.getScm().getUri().set("git.test.com");
            });

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SpdxPackageInfo.from(project.getExtensions().getByType(SpdxPackageExtension.class)));
  }

  @Test
  void from_scmErrorNoUri() {
    Project project = ProjectBuilder.builder().withName("test-name").build();
    project.setVersion("test-version");
    project.getPlugins().apply("org.spdx.sbom-package");

    project
        .getExtensions()
        .configure(
            SpdxPackageExtension.class,
            ext -> {
              ext.getScm().getRevision().set("test-revision");
            });

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> SpdxPackageInfo.from(project.getExtensions().getByType(SpdxPackageExtension.class)));
  }
}
