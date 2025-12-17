/*
 * Copyright 2025 The Project Authors.
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.test.FunctionalTest;
import org.spdx.tools.SpdxVerificationException;

/** An android only test that requires the android SDK, currently configured to only run on CI */
public class VariantDependenciesTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @EnabledIfEnvironmentVariable(named = "CI", matches = "true")
  @Test
  public void canResolveVariantDependencies() throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newFile(
                "settings.gradle.kts",
                """
                pluginManagement {
                  repositories {
                    google()
                    mavenCentral()
                  }
                }
                rootProject.name = "spdx-functional-test-project"
                include(":app")
                include(":library")
                """)
            .newFile(
                "app/build.gradle.kts",
                """
                plugins {
                 kotlin("android") version "1.9.21"
                  id("org.spdx.sbom")
                  id("com.android.application") version "8.2.0"
                }
                repositories {
                  google()
                  mavenCentral()
                }
                version = "1"
                android {
                  namespace="org.spdx.app"
                  compileSdk = 34
                  defaultConfig {
                    targetSdk = 24
                    minSdk = 24
                    applicationId = "org.spdx.sbom.app"
                    versionCode = 1
                    versionName = "1.0.0"
                  }
                  buildTypes {
                    named("release") {
                      isMinifyEnabled = true
                      isShrinkResources = true
                    }
                    named("debug") {
                      applicationIdSuffix = ".debug"
                      versionNameSuffix = "-DEBUG"
                      isMinifyEnabled = false
                    }
                  }
                  dependencies {
                    implementation(project(":library"))
                    implementation("dev.sigstore:sigstore-java:0.3.0")
                  }
                }
                spdxSbom {
                  targets {
                    create("sbom") {
                      configurations.set(listOf("debugRuntimeClasspath"))
                    }
                  }
                }
                """)
            .newFile(
                "library/build.gradle.kts",
                """
                plugins {
                 kotlin("android") version "1.9.21"
                  id("com.android.library") version "8.2.0"
                }
                repositories {
                  google()
                  mavenCentral()
                }
                android {
                  namespace="org.spdx.library"
                  compileSdk = 34
                  defaultConfig {
                    minSdk = 24
                  }
                  buildTypes {
                    named("release") {
                      isMinifyEnabled = false
                    }
                    named("debug") {
                      isMinifyEnabled = false
                    }
                  }
                  dependencies {
                    implementation("dev.sigstore:sigstore-java:0.3.0")
                  }
                }
                """);

    var result = test.newGradleRunner().withArguments(":app:spdxSbom", "--stacktrace").build();

    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));
    Path outputFile = test.getFile("app/build/spdx/sbom.spdx.json");

    var sbom = test.verifyBasic(outputFile);

    var actualVersion =
        sbom.lines()
            .filter(line -> line.contains("\"versionInfo\" : \"1.0.0\""))
            .collect(Collectors.joining());
    MatcherAssert.assertThat(
        "versionInfo is not correct (expected 1.0.0 but was " + actualVersion + ")",
        sbom.lines().filter(line -> line.contains("\"versionInfo\" : \"1.0.0\"")).count() == 1);
  }
}
