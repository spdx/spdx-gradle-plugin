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

import java.io.IOException;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.test.FunctionalTest;
import org.spdx.tools.SpdxVerificationException;

public class ProjectIsolationPluginTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  void canUseProjectIsolationPlugin() throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("spdx-functional-test-project", "sub-project")
            .newFile(
                "build.gradle.kts",
                """
                import org.spdx.sbom.gradle.project.IsolatedProjectInfo

                plugins {
                  id("org.spdx.sbom")
                  java
                }
                version = "dont-override"
                repositories {
                  google()
                  mavenCentral()
                }
                dependencies {
                  implementation("android.arch.persistence:db:1.1.1")
                  implementation("dev.sigstore:sigstore-java:0.3.0")
                  implementation(project(":sub-project"))
                }


                spdxSbom {
                  targets {
                    create ("release") {
                      configurations.set(listOf("testCompileClasspath"))
                      isolatedProjects {
                        isolatedProjectInfo.set(project.provider {
                          mapOf<String, IsolatedProjectInfo>(
                            ":" to IsolatedProjectInfo.of(":", "test-1.2.3"),
                            ":sub-project" to IsolatedProjectInfo.of(":sub-project", "test-2.3.4")
                          )
                        })
                      }
                    }
                  }
                }
                """)
            .newFile(
                "src/main/java/main/Main.java",
                """
                package main;
                import lib.Lib;
                public class Main {
                  public static void main(String[] args) { Lib.doSomething(); }
                }
                """)
            .newFile("src/main/resources/res.txt", "duck duck duck, goose")
            .newFile(
                "sub-project/build.gradle",
                """
                plugins {
                  id('java')
                }""")
            .newFile(
                "sub-project/src/main/java/lib/Lib.java",
                """
                package lib;
                public class Lib { public static int doSomething() { return 5; } }
                """);

    // you cannot debug with runner.withDebug(true) in project isolation
    test.newGradleRunner()
        .withArguments(
            "spdxSbomForRelease", "--stacktrace", "-Dorg.gradle.unsafe.isolated-projects=true")
        .build();

    var outputFile = test.getFile("build/spdx/release.spdx.json");
    var sbom = test.verifyBasic(outputFile);

    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"dont-override\""));
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"test-2.3.4\""));
  }
}
