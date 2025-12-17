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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.test.FunctionalTest;
import org.spdx.tools.SpdxVerificationException;

public class IvyDependencyTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  void cannotGenerateReportWithIvyDependenciesWhenMissingDependencies()
      throws IOException, URISyntaxException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newGroovySettings("spdx-functional-test-project")
            .newFile(
                "build.gradle",
                """
                plugins {
                  id('org.spdx.sbom')
                  id('java')
                }
                version = 1
                repositories {
                  ivy {
                    name = "ivyRepository"
                    url = "%s/ivy-repository"
                    patternLayout {
                      artifact('[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])')
                      ivy('[organisation]/[module]/[revision]/ivy-[revision].xml')
                      setM2compatible(true)
                    }
                  }
                }
                dependencies {
                  implementation 'org.example:module1:1.0.0'
                }
                spdxSbom {
                  targets {
                    release {
                    }
                  }
                }
                """
                    .formatted(projectDir.toAbsolutePath().toString().replaceAll("\\\\", "/")))
            .newFile(
                "src/main/java/main/Main.java",
                """
                package main;
                public class Main {
                    public static void main(String[] args) {  }
                }
                """);

    URL ivyRepositoryFolder = this.getClass().getResource("/ivy-repository");
    FileUtils.copyDirectory(
        new File(ivyRepositoryFolder.toURI()).getParentFile(), projectDir.toFile());

    var result =
        test.newGradleRunner().withArguments("spdxSbomForRelease", "--stacktrace").buildAndFail();

    // Verify
    MatcherAssert.assertThat(
        result.getOutput(),
        Matchers.containsString("No POM file found for dependency org.example:module1:1.0.0"));
  }

  @Test
  void canGenerateReportWithIvyDependenciesWhenIgnoringMissingDependencies()
      throws IOException, SpdxVerificationException, URISyntaxException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newGroovySettings("spdx-functional-test-project")
            .newFile(
                "build.gradle",
                """
                plugins {
                  id('org.spdx.sbom')
                  id('java')
                }
                version = 1
                repositories {
                  ivy {
                    name = "ivyRepository"
                    url = "%s/ivy-repository"
                    patternLayout {
                      artifact('[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])')
                      ivy('[organisation]/[module]/[revision]/ivy-[revision].xml')
                      setM2compatible(true)
                    }
                  }
                }
                dependencies {
                  implementation 'org.example:module1:1.0.0'
                }
                spdxSbom {
                  targets {
                    release {
                      ignoreNonMavenDependencies = true
                    }
                  }
                }
                """
                    .formatted(projectDir.toAbsolutePath().toString().replaceAll("\\\\", "/")))
            .newFile(
                "src/main/java/main/Main.java",
                """
                package main;
                public class Main {
                  public static void main(String[] args) {}
                }
                """);

    URL ivyRepositoryFolder = this.getClass().getResource("/ivy-repository");
    FileUtils.copyDirectory(
        new File(ivyRepositoryFolder.toURI()).getParentFile(), projectDir.toFile());

    var result = test.newGradleRunner().withArguments("spdxSbomForRelease", "--stacktrace").build();

    Path outputFile = test.getFile("build/spdx/release.spdx.json");
    test.verifyBasic(outputFile);

    MatcherAssert.assertThat(
        result.getOutput(),
        Matchers.containsString("Ignoring dependency without POM file: org.example:module1:1.0.0"));
  }
}
