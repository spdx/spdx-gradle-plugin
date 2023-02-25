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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A simple functional test for the 'org.spdx.greeting' plugin. */
class SpdxSbomPluginFunctionalTest {
  @TempDir File projectDir;

  private File getBuildFile() {
    return new File(projectDir, "build.gradle");
  }

  private File getSettingsFile() {
    return new File(projectDir, "settings.gradle");
  }

  @Test
  void canRunTask() throws IOException {
    writeString(
        getSettingsFile(),
        "rootProject.name = 'spdx-functional-test-project'\n" + "include 'sub-project'");
    writeString(
        getBuildFile(),
        "plugins {\n"
            + "  id('org.spdx.sbom')\n"
            + "  id('java')\n"
            + "}\n"
            + "repositories {\n"
            + "  google()\n"
            + "  mavenCentral()\n"
            + "}\n"
            + "dependencies {\n"
            + "  implementation 'android.arch.persistence:db:1.1.1'\n"
            + "  implementation 'dev.sigstore:sigstore-java:0.3.0'\n"
            + "  implementation project(':sub-project')\n"
            + ""
            + "}\n");

    Path main = projectDir.toPath().resolve(Paths.get("src/main/java/main/Main.java"));
    Files.createDirectories(main.getParent());
    writeString(
        Files.createFile(main).toFile(),
        "package main;\n"
            + "import lib.Lib;\n"
            + "public class Main {\n"
            + "  public static void main(String[] args) { Lib.doSomething(); }\n"
            + "}");

    Path resource = projectDir.toPath().resolve(Paths.get("src/main/resources/res.txt"));
    Files.createDirectories(resource.getParent());
    writeString(Files.createFile(resource).toFile(), "duck duck duck, goose");

    Path sub = projectDir.toPath().resolve("sub-project");
    Files.createDirectories(sub);
    writeString(sub.resolve("build.gradle").toFile(), "plugins {\n" + "  id('java')\n" + "}\n");

    Path lib = sub.resolve(Paths.get("src/main/java/lib/Lib.java"));
    Files.createDirectories(lib.getParent());
    writeString(
        Files.createFile(lib).toFile(),
        "package lib;\n" + "public class Lib { public static int doSomething() { return 5; } }\n");

    // Run the build
    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments("spdxSbom", "--stacktrace");
    runner.withProjectDir(projectDir);
    runner.build();

    Path outputFile = projectDir.toPath().resolve(Paths.get("build/spdx/spdx.sbom.json"));

    // Verify the result
    assertTrue(Files.isRegularFile(outputFile));

    System.out.println(Files.readString(outputFile));
  }

  private void writeString(File file, String string) throws IOException {
    try (Writer writer = new FileWriter(file)) {
      writer.write(string);
    }
  }
}
