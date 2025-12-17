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

public class BuildExtensionTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  public void canRunOnPluginProject() throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("spdx-functional-test-project")
            .newFile(
                "build.gradle.kts",
                """
                import java.net.URI
                import org.spdx.sbom.gradle.project.ProjectInfo
                import org.spdx.sbom.gradle.project.ScmInfo
                plugins {
                  id("org.spdx.sbom")
                  `java`
                }
                tasks.withType<org.spdx.sbom.gradle.SpdxSbomTask> {
                    taskExtension.set(object : org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension() {
                        override fun mapRepoUri(input: URI?, moduleId: ModuleVersionIdentifier): URI {
                            if (moduleId.name == "sigstore-java") {
                               return URI.create("https://truck.com")
                            }
                            // ignore input and return duck
                            return URI.create("https://duck.com")
                        }
                        override fun mapScmForProject(original: ScmInfo, projectInfo: ProjectInfo): ScmInfo {
                            return ScmInfo.from("git", "https://git.duck.com", "asdf")
                        }
                    })
                }
                version = "1"
                repositories {
                  mavenCentral()
                }
                dependencies {
                  implementation("dev.sigstore:sigstore-java:0.3.0")
                }
                spdxSbom {
                  targets {
                    create("sbom") {
                    }
                  }
                }
                """);

    test.newGradleRunner().withArguments("spdxSbom", "--stacktrace").build();

    var sbom = test.verifyBasic(test.getFile("build/spdx/sbom.spdx.json"));

    // Verify the result
    sbom.lines()
        .filter(line -> line.contains("downloadLocation"))
        .filter(line -> !line.contains("NOASSERTION"))
        .filter(line -> !line.contains("/sigstore-java/"))
        .forEach(
            line -> MatcherAssert.assertThat(line, Matchers.containsString("https://duck.com")));
    sbom.lines()
        .filter(line -> line.contains("downloadLocation"))
        .filter(line -> !line.contains("NOASSERTION"))
        .filter(line -> line.contains("/sigstore-java/"))
        .forEach(
            line -> MatcherAssert.assertThat(line, Matchers.containsString("https://truck.com")));
    sbom.lines()
        .filter(line -> line.contains("sourceInfo"))
        .forEach(
            line ->
                MatcherAssert.assertThat(
                    line, Matchers.containsString("https://git.duck.com@asdf")));
  }
}
