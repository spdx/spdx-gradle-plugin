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

public class MergeVariantsTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  void testMergeDuplicates() throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("spdx-functional-test-project")
            .newFile(
                "build.gradle.kts",
                """
plugins {
  id("org.spdx.sbom")
  java
}
version = 1
repositories {
  mavenCentral()
}
dependencies {
  implementation("org.xmlresolver:xmlresolver:5.2.1")
  implementation("org.xmlresolver:xmlresolver:5.2.1:data")
}

spdxSbom {
  targets {
    create ("release") {
      configurations.set(listOf("runtimeClasspath"))
    }
  }
}
                """);

    var result =
        test.newGradleRunner()
            .withArguments("spdxSbomForRelease", "--stacktrace")
            .withDebug(true)
            .build();

    Path outputFile = test.getFile("build/spdx/release.spdx.json");
    String sbom = test.verifyBasic(outputFile);

    // Let's assert that the output contains the dependency
    MatcherAssert.assertThat(sbom, Matchers.containsString("org.xmlresolver:xmlresolver"));
  }
}
