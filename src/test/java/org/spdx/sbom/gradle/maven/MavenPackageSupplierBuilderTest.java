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

import java.net.URI;
import org.apache.maven.model.Organization;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MavenPackageSupplierBuilderTest {
  private static final Organization pomOrganization = new Organization();

  static {
    pomOrganization.setName("Example");
  }

  @Test
  void useThePomOrganizationIfPresent() {
    PomInfo pomInfo =
        ImmutablePomInfo.builder()
            .homepage(URI.create("https://example.com"))
            .organization(pomOrganization)
            .addDevelopers(ImmutableDeveloperInfo.builder().name("Eli Graber").build())
            .build();

    Assertions.assertEquals(
        "Organization: Example", MavenPackageSupplierBuilder.buildPackageSupplier(pomInfo));
  }

  @Test
  void useTheDevelopersNameIfPresent() {
    PomInfo pomInfo =
        ImmutablePomInfo.builder()
            .homepage(URI.create("https://example.com"))
            .addDevelopers(ImmutableDeveloperInfo.builder().name("Eli Graber").build())
            .build();

    Assertions.assertEquals(
        "Person: Eli Graber", MavenPackageSupplierBuilder.buildPackageSupplier(pomInfo));
  }

  @Test
  void useTheDevelopersNameAndEmailIfPresent() {
    PomInfo pomInfo =
        ImmutablePomInfo.builder()
            .homepage(URI.create("https://example.com"))
            .addDevelopers(
                ImmutableDeveloperInfo.builder()
                    .name("Eli Graber")
                    .email("eli@example.com")
                    .build())
            .build();

    Assertions.assertEquals(
        "Person: Eli Graber (eli@example.com)",
        MavenPackageSupplierBuilder.buildPackageSupplier(pomInfo));
  }

  @Test
  void useTheDevelopersOrganizationIfPresentAndNameIsNotPresent() {
    PomInfo pomInfo =
        ImmutablePomInfo.builder()
            .homepage(URI.create("https://example.com"))
            .addDevelopers(
                ImmutableDeveloperInfo.builder()
                    .organization("ACME")
                    .email("eli@example.com")
                    .build())
            .build();

    Assertions.assertEquals(
        "Organization: ACME", MavenPackageSupplierBuilder.buildPackageSupplier(pomInfo));
  }

  @Test
  void otherwiseUseOrganizationNoAssertion() {
    PomInfo pomInfo =
        ImmutablePomInfo.builder()
            .homepage(URI.create("https://example.com"))
            .addDevelopers(ImmutableDeveloperInfo.builder().email("eli@example.com").build())
            .build();

    Assertions.assertEquals(
        "Organization: NOASSERTION", MavenPackageSupplierBuilder.buildPackageSupplier(pomInfo));
  }
}
