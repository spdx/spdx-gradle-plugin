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
package org.spdx;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.test.FunctionalTest;
import org.spdx.tools.SpdxVerificationException;

public class ProjectPackagePurlTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  public void projectPackageUsesMavenPublicationCoordinatesForPurl()
      throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("javaagent")
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  `java-library`
                  `maven-publish`
                  id("org.spdx.sbom")
                }

                group = "io.opentelemetry.javaagent"
                version = "2.0.0"

                publishing {
                  publications {
                    create<MavenPublication>("maven") {
                      artifactId = "opentelemetry-javaagent"
                      from(components["java"])
                    }
                  }
                }

                spdxSbom {
                  targets {
                    create("release") {}
                  }
                }
                """);

    test.newGradleRunner().withArguments("spdxSbomForRelease", "--stacktrace").build();

    String sbom = test.verifyBasic(test.getFile("build/spdx/release.spdx.json"));
    Assertions.assertTrue(
        sbom.contains(
            "\"referenceLocator\" : \"pkg:maven/io.opentelemetry.javaagent/opentelemetry-javaagent@2.0.0\""),
        sbom);
    Assertions.assertTrue(sbom.contains("\"referenceType\" : \"purl\""), sbom);
  }

  @Test
  public void projectPackageWithoutMavenPublicationHasNoPurl()
      throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("no-publication")
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  `java-library`
                  id("org.spdx.sbom")
                }

                group = "com.example"
                version = "1.0.0"

                spdxSbom {
                  targets {
                    create("release") {}
                  }
                }
                """);

    test.newGradleRunner().withArguments("spdxSbomForRelease", "--stacktrace").build();

    String sbom = test.verifyBasic(test.getFile("build/spdx/release.spdx.json"));
    Assertions.assertFalse(sbom.contains("\"purl\""), sbom);
    Assertions.assertFalse(sbom.contains("pkg:maven/com.example"), sbom);
  }

  @Test
  public void projectPackageWithMultipleMavenPublicationsHasNoPurl()
      throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("multi-publication")
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  `java-library`
                  `maven-publish`
                  id("org.spdx.sbom")
                }

                group = "com.example"
                version = "1.0.0"

                publishing {
                  publications {
                    create<MavenPublication>("first") {
                      artifactId = "first-artifact"
                      from(components["java"])
                    }
                    create<MavenPublication>("second") {
                      artifactId = "second-artifact"
                      from(components["java"])
                    }
                  }
                }

                spdxSbom {
                  targets {
                    create("release") {}
                  }
                }
                """);

    test.newGradleRunner().withArguments("spdxSbomForRelease", "--stacktrace").build();

    String sbom = test.verifyBasic(test.getFile("build/spdx/release.spdx.json"));
    Assertions.assertFalse(sbom.contains("\"purl\""), sbom);
  }
}
