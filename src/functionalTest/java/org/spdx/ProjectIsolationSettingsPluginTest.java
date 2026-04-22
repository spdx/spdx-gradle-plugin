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
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.test.FunctionalTest;
import org.spdx.tools.SpdxVerificationException;

public class ProjectIsolationSettingsPluginTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  void canUseProjectIsolationSettingsPlugin() throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newFile(
                "settings.gradle.kts",
                """
                rootProject.name = "spdx-functional-test-project"
                include(":sub-project")
                plugins {
                  id("org.spdx.sbom.settings")
                }
                """)
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  id("org.spdx.sbom")
                  java
                }
                version = "root-1.2.3"
                repositories {
                  mavenCentral()
                }
                dependencies {
                  implementation(project(":sub-project"))
                }

                spdxSbom {
                  targets {
                    create ("release") {
                      configurations.set(listOf("runtimeClasspath"))
                    }
                  }
                }
                """)
            .newFile(
                "sub-project/build.gradle.kts",
                """
                plugins {
                  java
                }
                version = "sub-4.5.6"
                """)
            .newFile(
                "src/main/java/main/Main.java",
                """
                package main;
                public class Main {
                  public static void main(String[] args) { }
                }
                """);

    test.newGradleRunner()
        .withArguments(
            "spdxSbomForRelease", "--stacktrace", "-Dorg.gradle.unsafe.isolated-projects=true")
        .build();

    var outputFile = test.getFile("build/spdx/release.spdx.json");
    var sbom = test.verifyBasic(outputFile);

    // Verify root project version
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"root-1.2.3\""));
    // Verify sub-project version was automatically aggregated
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"sub-4.5.6\""));
  }

  @Test
  void projectIsolationWithoutSettingsPluginGeneratesIncompleteSbom()
      throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newFile(
                "settings.gradle.kts",
                """
                rootProject.name = "spdx-isolation-failure"
                include(":sub-project")
                """)
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  id("org.spdx.sbom")
                  java
                }
                version = "root-1.2.3"
                repositories {
                  mavenCentral()
                }
                dependencies {
                  implementation(project(":sub-project"))
                }
                spdxSbom {
                  targets {
                    create ("release") {
                      configurations.set(listOf("runtimeClasspath"))
                    }
                  }
                }
                """)
            .newFile(
                "sub-project/build.gradle.kts",
                """
                plugins {
                  java
                }
                version = "sub-4.5.6"
                """);

    var result =
        test.newGradleRunner()
            .withArguments(
                "spdxSbomForRelease", "--stacktrace", "-Dorg.gradle.unsafe.isolated-projects=true")
            .build();

    MatcherAssert.assertThat(
        result.getOutput(),
        Matchers.containsString(
            "has no known version due to project isolation, use org.spdx.sbom.settings settings plugin to fix"));
    var outputFile = test.getFile("build/spdx/release.spdx.json");
    var sbom = test.verifyBasic(outputFile);

    // Verify root project version (still works because it's the current project)
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"root-1.2.3\""));
    // Verify sub-project version is NOASSERTION because it was "unknown" due to project isolation
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"name\" : \"sub-project\""));
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"NOASSERTION\""));
  }

  @Test
  void isCompatibleWithConfigurationCacheAndConfigurationOnDemand()
      throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newFile(
                "settings.gradle.kts",
                """
                rootProject.name = "spdx-config-cache"
                include(":sub-project")
                plugins {
                  id("org.spdx.sbom.settings")
                }
                """)
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  id("org.spdx.sbom")
                  java
                }
                version = "1.2.3"
                repositories {
                  mavenCentral()
                }
                dependencies {
                  implementation(project(":sub-project"))
                }
                spdxSbom {
                  targets {
                    create ("release") {
                      configurations.set(listOf("runtimeClasspath"))
                    }
                  }
                }
                """)
            .newFile("sub-project/build.gradle.kts", "plugins { java }\nversion=\"4.5.6\"")
            .newFile("ignored-sub-project/build.gradle.kts", "println(\"do-not-print\")");

    // First run to store configuration cache and check if ignored-sub-project is not configured
    var firstRun =
        test.newGradleRunner()
            .withArguments(
                "spdxSbomForRelease",
                "-Dorg.gradle.unsafe.isolated-projects=true",
                "--configuration-cache")
            .build();
    MatcherAssert.assertThat(
        firstRun.getOutput(), Matchers.not(Matchers.containsString("do-not-print")));

    // Second run to reuse configuration cache even if unrelated project is changed
    test.newFile("ignored-sub-project/build.gradle.kts", "println(\"do-not-print-again\")");
    var secondRun =
        test.newGradleRunner()
            .withArguments(
                "spdxSbomForRelease",
                "-Dorg.gradle.unsafe.isolated-projects=true",
                "--configuration-cache")
            .build();

    MatcherAssert.assertThat(
        secondRun.getOutput(), Matchers.containsString("Reusing configuration cache"));
    var outputFile = test.getFile("build/spdx/release.spdx.json");
    var sbom = test.verifyBasic(outputFile);
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"1.2.3\""));
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"4.5.6\""));
    MatcherAssert.assertThat(
        secondRun.getOutput(), Matchers.not(Matchers.containsString("do-not-print")));

    // Third run to ensure changes trigger cache invalidation
    test.newFile("sub-project/build.gradle.kts", "plugins { java }\nversion=\"7.8.9\"");
    var thirdRun =
        test.newGradleRunner()
            .withArguments(
                "spdxSbomForRelease",
                "-Dorg.gradle.unsafe.isolated-projects=true",
                "--configuration-cache")
            .build();

    MatcherAssert.assertThat(
        thirdRun.getOutput(), Matchers.containsString("configuration cache cannot be reused"));
    MatcherAssert.assertThat(
        thirdRun.getOutput(),
        Matchers.containsString("'sub-project/build.gradle.kts' has changed"));
    outputFile = test.getFile("build/spdx/release.spdx.json");
    sbom = test.verifyBasic(outputFile);
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"1.2.3\""));
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"7.8.9\""));
    MatcherAssert.assertThat(
        thirdRun.getOutput(), Matchers.not(Matchers.containsString("do-not-print")));
  }
}
