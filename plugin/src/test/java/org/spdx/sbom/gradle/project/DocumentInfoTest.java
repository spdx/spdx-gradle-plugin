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
import java.util.stream.Stream;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.spdx.sbom.gradle.SpdxSbomExtension;
import org.spdx.sbom.gradle.SpdxSbomExtension.RootPackage;
import org.spdx.sbom.gradle.project.DocumentInfo.RootPackageInfo;

class DocumentInfoTest {
  @Test
  void from_allSet() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply("org.spdx.sbom");
    project
        .getExtensions()
        .getByType(SpdxSbomExtension.class)
        .getTargets()
        .create(
            "test",
            target ->
                target.document(
                    d -> {
                      d.getName().set("test-name");
                      d.getCreator().set("test-creator");
                      d.getNamespace().set("test-namespace");
                      d.rootPackage(
                          rp -> {
                            rp.getVersion().set("test-version");
                            rp.getName().set("test-name");
                            rp.getSupplier().set("test-supplier");
                          });
                    }));
    DocumentInfo di =
        DocumentInfo.from(
            project
                .getExtensions()
                .getByType(SpdxSbomExtension.class)
                .getTargets()
                .getByName("test"));
    Assertions.assertEquals("test-name", di.getName());
    Assertions.assertEquals("test-namespace", di.getNamespace());
    Assertions.assertEquals(Optional.of("test-creator"), di.getCreator());
    Optional<RootPackageInfo> rp = di.getRootPackageInfo();
    Assertions.assertTrue(rp.isPresent());
  }

  @Test
  void from_requiredSet() {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply("org.spdx.sbom");
    project
        .getExtensions()
        .getByType(SpdxSbomExtension.class)
        .getTargets()
        .create(
            "test",
            target ->
                target.document(
                    d -> {
                      d.getName().set("test-name");
                      d.getNamespace().set("test-namespace");
                    }));
    DocumentInfo di =
        DocumentInfo.from(
            project
                .getExtensions()
                .getByType(SpdxSbomExtension.class)
                .getTargets()
                .getByName("test"));
    Assertions.assertEquals("test-name", di.getName());
    Assertions.assertEquals("test-namespace", di.getNamespace());
    Assertions.assertTrue(di.getCreator().isEmpty());
    Assertions.assertTrue(di.getRootPackageInfo().isEmpty());
  }

  @ParameterizedTest
  @MethodSource("badRootPackageConfigs")
  void from_RootPackageNotSet(Action<? super RootPackage> configureRootPackage) {
    Project project = ProjectBuilder.builder().build();
    project.getPlugins().apply("org.spdx.sbom");
    project
        .getExtensions()
        .getByType(SpdxSbomExtension.class)
        .getTargets()
        .create("test", target -> target.document(d -> d.rootPackage(configureRootPackage)));
    Assertions.assertThrows(
        GradleException.class,
        () ->
            DocumentInfo.from(
                project
                    .getExtensions()
                    .getByType(SpdxSbomExtension.class)
                    .getTargets()
                    .getByName("test")));
  }

  private static Stream<Action<? super RootPackage>> badRootPackageConfigs() {
    return Stream.of(
        rp -> rp.getVersion().set("test-version"),
        rp -> rp.getName().set("test-name"),
        rp -> rp.getSupplier().set("test-supplier"),
        rp -> {
          rp.getVersion().set("test-version");
          rp.getName().set("test-name");
        },
        rp -> {
          rp.getVersion().set("test-version");
          rp.getSupplier().set("test-supplier");
        },
        rp -> {
          rp.getName().set("test-name");
          rp.getSupplier().set("test-supplier");
        });
  }
}
