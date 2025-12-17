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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.test.FunctionalTest;
import org.spdx.tools.SpdxVerificationException;

public class UberPackageTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  public void rootProjectIsValid() throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newKotlinSettings("spdx-functional-test-project")
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  id("org.spdx.sbom")
                  `java`
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
                      document {
                        uberPackage {
                          name.set("abc")
                          version.set("1.2.3")
                          supplier.set("Organization:def")
                        }
                      }
                    }
                  }
                }
                """);

    test.newGradleRunner().withArguments("spdxSbom", "--stacktrace").build();
    Path outputFile = test.getFile("build/spdx/sbom.spdx.json");
    test.verifyBasic(outputFile);
  }
}
