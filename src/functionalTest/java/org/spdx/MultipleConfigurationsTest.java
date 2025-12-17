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

public class MultipleConfigurationsTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  void useMultipleConfigurations() throws IOException, SpdxVerificationException {
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
                repositories {
                  google()
                  mavenCentral()
                }
                version = '1.2.3'
                configurations {
                  custom
                }
                dependencies {
                  implementation 'android.arch.persistence:db:1.1.1'
                  implementation 'dev.sigstore:sigstore-java:0.3.0'
                  custom 'dev.sigstore:sigstore-java:0.2.0'
                }
                spdxSbom {
                  targets {
                    release {
                      configurations = ['runtimeClasspath', 'custom']
                    }
                  }
                }
                """);

    // Run the build
    test.newGradleRunner().withArguments("spdxSbomForRelease", "--stacktrace").build();

    Path outputFile = test.getFile("build/spdx/release.spdx.json");
    String sbom = test.verifyBasic(outputFile);

    // Verify the result
    MatcherAssert.assertThat(sbom, Matchers.containsString("dev.sigstore:sigstore-java\","));
    MatcherAssert.assertThat(sbom, Matchers.containsString("sigstore-java@0.3.0"));
    MatcherAssert.assertThat(sbom, Matchers.containsString("sigstore-java@0.2.0"));
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"1.2.3\""));
  }
}
