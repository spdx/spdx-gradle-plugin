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

public class SmokeTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  void smokeTest() throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("spdx-functional-test-project", "sub-project")
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  id("org.spdx.sbom")
                  java
                }
                version = 1
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
                }
                """)
            .newFile(
                "src/main/java/lib/Lib.java",
                """
                package lib;
                public class Lib { public static int doSomething() { return 5; } }
                """);

    var result = test.newGradleRunner().withArguments("spdxSbomForRelease", "--stacktrace").build();

    Path outputFile = test.getFile("build/spdx/release.spdx.json");
    test.verifyBasic(outputFile);

    MatcherAssert.assertThat(
        result.getOutput(),
        Matchers.containsString(
            "spdx sboms require a version but project: sub-project has no specified version"));
  }
}
