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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.test.FunctionalTest;
import org.spdx.tools.SpdxVerificationException;

public class LicenseTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  @Test
  public void multiProjectLicensesWithIsolatedProjectsAndTargetOverride()
      throws IOException, SpdxVerificationException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newFile(
                "settings.gradle.kts",
                """
                rootProject.name = "spdx-multipub-test"
                include(":sub-mit")
                include(":sub-bsd")
                include(":sub-unlicensed")
                plugins {
                  id("org.spdx.sbom.settings")
                }
                """)
            .newFile(
                "build.gradle.kts",
                """
                plugins {
                  id("org.spdx.sbom")
                  `java`
                  `maven-publish`
                }
                version = "1.0.0"
                repositories {
                  mavenCentral()
                }
                dependencies {
                  implementation(project(":sub-mit"))
                  implementation(project(":sub-bsd"))
                  implementation(project(":sub-unlicensed"))
                }
                spdxSbom {
                  onlyUseLocalLicenses.set(true)
                  targets {
                    create("release")
                    create("override") {
                      license.set("GPL-3.0-only")
                    }
                  }
                }
                publishing {
                  publications {
                    create<MavenPublication>("mavenJava") {
                      from(components["java"])
                      pom {
                        licenses {
                          license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                          }
                        }
                      }
                    }
                  }
                }
                """)
            .newFile(
                "sub-mit/build.gradle.kts",
                """
                plugins {
                  `java`
                  `maven-publish`
                }
                version = "2.0.0"
                publishing {
                  publications {
                    create<MavenPublication>("mavenJava") {
                      from(components["java"])
                      pom {
                        licenses {
                          license {
                            name.set("MIT License")
                          }
                        }
                      }
                    }
                  }
                }
                """)
            .newFile(
                "sub-bsd/build.gradle.kts",
                """
                plugins {
                  `java`
                  `maven-publish`
                }
                version = "4.0.0"
                afterEvaluate {
                  publishing {
                    publications {
                      create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        pom {
                          licenses {
                            license {
                              name.set("BSD-3-Clause")
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """)
            .newFile(
                "sub-unlicensed/build.gradle.kts",
                """
                plugins {
                  `java`
                }
                version = "3.0.0"
                """);

    var args =
        new String[] {
          "spdxSbomForRelease",
          "spdxSbomForOverride",
          "--stacktrace",
          "-Dorg.gradle.unsafe.isolated-projects=true",
          "--configuration-cache"
        };

    test.newGradleRunner().withArguments(args).build();

    Path releaseOutput = test.getFile("build/spdx/release.spdx.json");
    Path overrideOutput = test.getFile("build/spdx/override.spdx.json");
    Map<String, String> expectedRelease =
        Map.of(
            "spdx-multipub-test", "Apache-2.0",
            "sub-mit", "MIT",
            "sub-bsd", "BSD-3-Clause",
            "sub-unlicensed", "NOASSERTION");
    Map<String, String> expectedOverride =
        Map.of(
            "spdx-multipub-test", "GPL-3.0-only",
            "sub-mit", "MIT",
            "sub-bsd", "BSD-3-Clause",
            "sub-unlicensed", "NOASSERTION");

    assertEquals(expectedRelease, declaredLicensesByPackage(test.verifyBasic(releaseOutput)));
    assertEquals(expectedOverride, declaredLicensesByPackage(test.verifyBasic(overrideOutput)));

    // Delete outputs and re-run to verify configuration-cache reuse produces identical licenses
    Files.delete(releaseOutput);
    Files.delete(overrideOutput);
    var cachedRun = test.newGradleRunner().withArguments(args).build();
    assertThat(cachedRun.getOutput(), containsString("Reusing configuration cache"));
    assertEquals(expectedRelease, declaredLicensesByPackage(test.verifyBasic(releaseOutput)));
    assertEquals(expectedOverride, declaredLicensesByPackage(test.verifyBasic(overrideOutput)));
  }

  private static Map<String, String> declaredLicensesByPackage(String sbomJson) {
    Map<String, String> result = new HashMap<>();
    JsonObject root = JsonParser.parseString(sbomJson).getAsJsonObject();
    for (JsonElement element : root.getAsJsonArray("packages")) {
      JsonObject pkg = element.getAsJsonObject();
      result.put(pkg.get("name").getAsString(), pkg.get("licenseDeclared").getAsString());
    }
    return result;
  }
}
