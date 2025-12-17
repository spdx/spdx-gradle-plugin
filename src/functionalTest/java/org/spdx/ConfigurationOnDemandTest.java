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

public class ConfigurationOnDemandTest {

  @TempDir(cleanup = CleanupMode.ON_SUCCESS)
  Path projectDir;

  /**
   * This method verifies that the `tasks` task works with configure-on-demand This is a regression
   * test for <a href="https://github.com/spdx/spdx-gradle-plugin/issues/62">issues#62<a>
   */
  @Test
  public void tasksConfigurationOnDemand() throws IOException {
    var test =
        FunctionalTest.newTest(projectDir)
            .newGroovySettings("spdx-functional-test-project", "subproject1", "subproject2")
            .newFile(
                "subproject1/build.gradle",
                """
                afterEvaluate {
                  println('afterEvaluate')
                }
                """)
            .newFile(
                "subproject2/build.gradle",
                """
                plugins {
                  id("org.spdx.sbom")
                }
                configurations.create("bundleInside")
                dependencies {
                  bundleInside(project(":subproject1"))
                }
                spdxSbom.targets.create("release") { target ->
                  configurations = ["bundleInside"]
                }
                """);

    test.newGradleRunner()
        .withArguments(":subproject2:tasks", "--stacktrace", "--configure-on-demand")
        .build();
  }
}
