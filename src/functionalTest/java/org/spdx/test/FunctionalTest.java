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
package org.spdx.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.GradleRunner;
import org.spdx.tools.SpdxToolsHelper;
import org.spdx.tools.SpdxVerificationException;
import org.spdx.tools.Verify;

public class FunctionalTest {

  private final Path projectDir;

  private FunctionalTest(Path tmpDir) {
    projectDir = tmpDir;
  }

  public static FunctionalTest newTest(Path tmpDir) {
    return new FunctionalTest(tmpDir);
  }

  /** Create a new build.gradle immediately. */
  public FunctionalTest newGroovySettings(String rootProject, String... subProjects)
      throws IOException {
    String content =
        "rootProject.name = '"
            + rootProject
            + "'\n"
            + Arrays.stream(subProjects)
                .map(subProject -> "include '" + subProject + "'\n")
                .collect(Collectors.joining());
    Files.writeString(projectDir.resolve("settings.gradle"), content);
    return this;
  }

  /** Create a new build.gradle.kts immediately. */
  public FunctionalTest newKotlinSettings(String rootProject, String... subProjects)
      throws IOException {
    String content =
        "rootProject.name = \""
            + rootProject
            + "\"\n"
            + Arrays.stream(subProjects)
                .map(subProject -> "include(\"" + subProject + "\")\n")
                .collect(Collectors.joining());
    Files.writeString(projectDir.resolve("settings.gradle.kts"), content);
    return this;
  }

  /** Create a new file immediately. */
  public FunctionalTest newFile(String relativePath, String content) throws IOException {
    Path targetFile = projectDir.resolve(relativePath);
    Files.createDirectories(targetFile.getParent());
    Files.writeString(targetFile, content);
    return this;
  }

  /**
   * Creates a new GradleRunner with default configuration, you should still set "debug",
   * "arguments" and run either "build" or "buildAndFail"
   */
  public GradleRunner newGradleRunner() {
    return GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile());
  }

  /** Get a file from the project directory. */
  public Path getFile(String relativePath) {
    return projectDir.resolve(relativePath);
  }

  /**
   * Do some basic verification on a generated sbom and return the contents of the sbom for further
   * processing.
   */
  public String verifyBasic(Path outputFile) throws IOException, SpdxVerificationException {
    assertTrue(Files.isRegularFile(outputFile));
    var sbom = Files.readString(outputFile);
    System.out.println(sbom);
    Verify.verify(outputFile.toFile().getAbsolutePath(), SpdxToolsHelper.SerFileType.JSON);
    return sbom;
  }
}
