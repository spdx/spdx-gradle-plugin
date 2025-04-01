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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.GradleRunner;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.tools.SpdxToolsHelper.SerFileType;
import org.spdx.tools.SpdxVerificationException;
import org.spdx.tools.Verify;

/** A simple functional test for the 'org.spdx.greeting' plugin. */
class SpdxSbomPluginFunctionalTest {
  @TempDir File projectDir;

  private File getBuildFile() {
    return new File(projectDir, "build.gradle");
  }

  private File getKotlinBuildFile() {
    return new File(projectDir, "build.gradle.kts");
  }

  private File getSettingsFile() {
    return new File(projectDir, "settings.gradle");
  }

  private File getKotlinSettingsFile() {
    return new File(projectDir, "settings.gradle.kts");
  }

  @Test
  void canRunTask() throws IOException, SpdxVerificationException {
    writeString(
        getSettingsFile(),
        "rootProject.name = 'spdx-functional-test-project'\n" + "include 'sub-project'");
    writeString(
        getBuildFile(),
        "plugins {\n"
            + "  id('org.spdx.sbom')\n"
            + "  id('java')\n"
            + "}\n"
            + "version = 1\n"
            + "repositories {\n"
            + "  google()\n"
            + "  mavenCentral()\n"
            + "}\n"
            + "dependencies {\n"
            + "  implementation 'android.arch.persistence:db:1.1.1'\n"
            + "  implementation 'dev.sigstore:sigstore-java:0.3.0'\n"
            + "  implementation project(':sub-project')\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    release {\n"
            + "      configurations = ['testCompileClasspath']\n"
            + "    }\n"
            + "  }\n"
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
    runner.withArguments("spdxSbomForRelease", "--stacktrace");
    runner.withProjectDir(projectDir);
    var result = runner.build();

    Path outputFile = projectDir.toPath().resolve(Paths.get("build/spdx/release.spdx.json"));
    Verify.verify(outputFile.toFile().getAbsolutePath(), SerFileType.JSON);

    MatcherAssert.assertThat(
        result.getOutput(),
        Matchers.containsString(
            "spdx sboms require a version but project: sub-project has no specified version"));

    // Verify the result
    assertTrue(Files.isRegularFile(outputFile));

    System.out.println(Files.readString(outputFile));
  }

  @Test
  void useMultipleConfigurations() throws IOException, SpdxVerificationException {
    writeString(getSettingsFile(), "rootProject.name = 'spdx-functional-test-project'");
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
            + "version = '1.2.3'\n"
            + "configurations {\n"
            + "  custom\n"
            + "}\n"
            + "dependencies {\n"
            + "  implementation 'android.arch.persistence:db:1.1.1'\n"
            + "  implementation 'dev.sigstore:sigstore-java:0.3.0'\n"
            + "  custom 'dev.sigstore:sigstore-java:0.2.0'\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    release {\n"
            + "      configurations = ['runtimeClasspath', 'custom']\n"
            + "    }\n"
            + "  }\n"
            + "}\n");

    // Run the build
    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments("spdxSbomForRelease", "--stacktrace");
    runner.withProjectDir(projectDir);
    runner.build();

    Path outputFile = projectDir.toPath().resolve(Paths.get("build/spdx/release.spdx.json"));
    Verify.verify(outputFile.toFile().getAbsolutePath(), SerFileType.JSON);

    // Verify the result
    assertTrue(Files.isRegularFile(outputFile));

    // should contain both versions from both library references
    var sbom = Files.readString(outputFile);
    MatcherAssert.assertThat(sbom, Matchers.containsString("dev.sigstore:sigstore-java\","));
    MatcherAssert.assertThat(sbom, Matchers.containsString("sigstore-java@0.3.0"));
    MatcherAssert.assertThat(sbom, Matchers.containsString("sigstore-java@0.2.0"));
    MatcherAssert.assertThat(sbom, Matchers.containsString("\"versionInfo\" : \"1.2.3\""));

    System.out.println(Files.readString(outputFile));
  }

  @Test
  public void canRunOnPluginProject() throws IOException, SpdxVerificationException {
    writeString(getKotlinSettingsFile(), "rootProject.name = \"spdx-functional-test-project\"");
    writeString(
        getKotlinBuildFile(),
        "plugins {\n"
            + "  id(\"org.spdx.sbom\")\n"
            + "  `java-gradle-plugin`\n"
            + "}\n"
            + "repositories {\n"
            + "  google()\n"
            + "  mavenCentral()\n"
            + "}\n"
            + "version = \"1\"\n"
            + "dependencies {\n"
            + "  implementation(\"dev.sigstore:sigstore-java:0.3.0\")\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    create(\"sbom\") {\n"
            + "    }\n"
            + "    create(\"test\") {\n"
            + "      configurations.set(listOf(\"testRuntimeClasspath\"))\n"
            + "    }\n"
            + "  }\n"
            + "}\n");

    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments("spdxSbom", "--stacktrace");
    runner.withProjectDir(projectDir);
    runner.build();

    Path outputFile = projectDir.toPath().resolve(Paths.get("build/spdx/sbom.spdx.json"));
    Verify.verify(outputFile.toFile().getAbsolutePath(), SerFileType.JSON);
    Path outputFile2 = projectDir.toPath().resolve(Paths.get("build/spdx/test.spdx.json"));
    Verify.verify(outputFile2.toFile().getAbsolutePath(), SerFileType.JSON);

    // Verify the result
    assertTrue(Files.isRegularFile(outputFile));
    assertTrue(Files.isRegularFile(outputFile2));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @EnabledIfEnvironmentVariable(named = "CI", matches = "true")
  @Test
  public void canResolveVariantDependencies() throws IOException, SpdxVerificationException {
    File app = new File(projectDir, "app");
    app.mkdir();
    File appKotlinBuildFile = new File(app, "build.gradle.kts");

    File library = new File(projectDir, "library");
    library.mkdir();
    File libraryKotlinBuildFile = new File(library, "build.gradle.kts");

    writeString(
        getKotlinSettingsFile(),
        "pluginManagement {\n"
            + "  repositories {\n"
            + "    google()\n"
            + "    mavenCentral()\n"
            + "  }\n"
            + "}\n"
            + "rootProject.name = \"spdx-functional-test-project\"\n"
            + "include(\":app\")\n"
            + "include(\":library\")");

    writeString(
        appKotlinBuildFile,
        "plugins {\n"
            + " kotlin(\"android\") version \"1.9.21\"\n"
            + "  id(\"org.spdx.sbom\")\n"
            + "  id(\"com.android.application\") version \"8.2.0\"\n"
            + "}\n"
            + "repositories {\n"
            + "  google()\n"
            + "  mavenCentral()\n"
            + "}\n"
            + "version = \"1\"\n"
            + "android {\n"
            + "  namespace=\"org.spdx.app\"\n"
            + "  compileSdk = 34\n"
            + "  defaultConfig {\n"
            + "    targetSdk = 24\n"
            + "    minSdk = 24\n"
            + "    applicationId = \"org.spdx.sbom.app\"\n"
            + "    versionCode = 1\n"
            + "    versionName = \"1.0.0\"\n"
            + "  }\n"
            + "  buildTypes {\n"
            + "    named(\"release\") {\n"
            + "      isMinifyEnabled = true\n"
            + "      isShrinkResources = true\n"
            + "    }\n"
            + "    named(\"debug\") {\n"
            + "      applicationIdSuffix = \".debug\"\n"
            + "      versionNameSuffix = \"-DEBUG\"\n"
            + "      isMinifyEnabled = false\n"
            + "    }\n"
            + "  }\n"
            + "  dependencies {\n"
            + "    implementation(project(\":library\"))\n"
            + "    implementation(\"dev.sigstore:sigstore-java:0.3.0\")\n"
            + "  }\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    create(\"sbom\") {\n"
            + "      configurations.set(listOf(\"debugRuntimeClasspath\"))\n"
            + "    }\n"
            + "  }\n"
            + "}\n");

    writeString(
        libraryKotlinBuildFile,
        "plugins {\n"
            + " kotlin(\"android\") version \"1.9.21\"\n"
            + "  id(\"com.android.library\") version \"8.2.0\"\n"
            + "}\n"
            + "repositories {\n"
            + "  google()\n"
            + "  mavenCentral()\n"
            + "}\n"
            + "android {\n"
            + "  namespace=\"org.spdx.library\"\n"
            + "  compileSdk = 34\n"
            + "  defaultConfig {\n"
            + "    minSdk = 24\n"
            + "  }\n"
            + "  buildTypes {\n"
            + "    named(\"release\") {\n"
            + "      isMinifyEnabled = false\n"
            + "    }\n"
            + "    named(\"debug\") {\n"
            + "      isMinifyEnabled = false\n"
            + "    }\n"
            + "  }\n"
            + "  dependencies {\n"
            + "    implementation(\"dev.sigstore:sigstore-java:0.3.0\")\n"
            + "  }\n"
            + "}\n");

    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments(":app:spdxSbom", "--stacktrace");
    runner.withProjectDir(projectDir);
    var result = runner.build();

    assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"));

    Path outputFile = projectDir.toPath().resolve(Paths.get("app/build/spdx/sbom.spdx.json"));
    Verify.verify(outputFile.toFile().getAbsolutePath(), SerFileType.JSON);

    // Verify the result
    assertTrue(Files.isRegularFile(outputFile));
    var sbom = Files.readAllLines(outputFile);
    var actualVersion =
        sbom.stream()
            .filter(line -> line.contains("\"versionInfo\" : \"1.0.0\""))
            .collect(Collectors.joining());
    MatcherAssert.assertThat(
        "versionInfo is not correct (expected 1.0.0 but was " + actualVersion + ")",
        sbom.stream().filter(line -> line.contains("\"versionInfo\" : \"1.0.0\"")).count() == 1);
  }

  @Test
  public void canUseBuildExtension() throws IOException, SpdxVerificationException {
    writeString(getKotlinSettingsFile(), "rootProject.name = \"spdx-functional-test-project\"");
    writeString(
        getKotlinBuildFile(),
        "import java.net.URI\n"
            + "import org.spdx.sbom.gradle.project.ProjectInfo\n"
            + "import org.spdx.sbom.gradle.project.ScmInfo\n"
            + "plugins {\n"
            + "  id(\"org.spdx.sbom\")\n"
            + "  `java`\n"
            + "}\n"
            + "tasks.withType<org.spdx.sbom.gradle.SpdxSbomTask> {\n"
            + "    taskExtension.set(object : org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension() {\n"
            + "        override fun mapRepoUri(input: URI?, moduleId: ModuleVersionIdentifier): URI {\n"
            + "            if (moduleId.name == \"sigstore-java\") {\n"
            + "               return URI.create(\"https://truck.com\")\n"
            + "            }\n"
            + "            // ignore input and return duck\n"
            + "            return URI.create(\"https://duck.com\")\n"
            + "        }\n"
            + "        override fun mapScmForProject(original: ScmInfo, projectInfo: ProjectInfo): ScmInfo {\n"
            + "            return ScmInfo.from(\"git\", \"https://git.duck.com\", \"asdf\")\n"
            + "        }\n"
            + "    })\n"
            + "}\n"
            + "version = \"1\"\n"
            + "repositories {\n"
            + "  mavenCentral()\n"
            + "}\n"
            + "dependencies {\n"
            + "  implementation(\"dev.sigstore:sigstore-java:0.3.0\")\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    create(\"sbom\") {\n"
            + "    }\n"
            + "  }\n"
            + "}\n");

    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments("spdxSbom", "--stacktrace");
    runner.withProjectDir(projectDir);
    runner.build();

    Path outputFile = projectDir.toPath().resolve(Paths.get("build/spdx/sbom.spdx.json"));
    Verify.verify(outputFile.toFile().getAbsolutePath(), SerFileType.JSON);

    // Verify the result
    assertTrue(Files.isRegularFile(outputFile));
    var sbom = Files.readAllLines(outputFile);
    sbom.stream()
        .filter(line -> line.contains("downloadLocation"))
        .filter(line -> !line.contains("NOASSERTION"))
        .filter(line -> !line.contains("/sigstore-java/"))
        .forEach(
            line -> MatcherAssert.assertThat(line, Matchers.containsString("https://duck.com")));
    sbom.stream()
        .filter(line -> line.contains("downloadLocation"))
        .filter(line -> !line.contains("NOASSERTION"))
        .filter(line -> line.contains("/sigstore-java/"))
        .forEach(
            line -> MatcherAssert.assertThat(line, Matchers.containsString("https://truck.com")));
    sbom.stream()
        .filter(line -> line.contains("sourceInfo"))
        .forEach(
            line ->
                MatcherAssert.assertThat(
                    line, Matchers.containsString("https://git.duck.com@asdf")));
  }

  @Test
  public void rootProjectIsValid() throws IOException, SpdxVerificationException {
    writeString(getKotlinSettingsFile(), "rootProject.name = \"spdx-functional-test-project\"");
    writeString(
        getKotlinBuildFile(),
        "plugins {\n"
            + "  id(\"org.spdx.sbom\")\n"
            + "  `java`\n"
            + "}\n"
            + "version = \"1\"\n"
            + "repositories {\n"
            + "  mavenCentral()\n"
            + "}\n"
            + "dependencies {\n"
            + "  implementation(\"dev.sigstore:sigstore-java:0.3.0\")\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    create(\"sbom\") {\n"
            + "      document {\n"
            + "        uberPackage {\n"
            + "          name.set(\"abc\")\n"
            + "          version.set(\"1.2.3\")\n"
            + "          supplier.set(\"Organization:def\")\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n");

    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments("spdxSbom", "--stacktrace");
    runner.withProjectDir(projectDir);
    runner.build();

    Path outputFile = projectDir.toPath().resolve(Paths.get("build/spdx/sbom.spdx.json"));
    Verify.verify(outputFile.toFile().getAbsolutePath(), SerFileType.JSON);
  }

  /**
   * This method verifies that the `tasks` task works with configure-on-demand This is a regression
   * test for https://github.com/spdx/spdx-gradle-plugin/issues/62
   */
  @Test
  public void tasksConfigurationOnDemand() throws IOException {
    writeString(
        getSettingsFile(),
        "rootProject.name = 'spdx-functional-test-project'\n"
            + "include 'subproject1'\n"
            + "include 'subproject2'\n");

    Path sub1 = projectDir.toPath().resolve("subproject1");
    Files.createDirectories(sub1);
    writeString(
        sub1.resolve("build.gradle").toFile(),
        "afterEvaluate {\n" + "  println('afterEvaluate')\n" + "}\n");

    Path sub2 = projectDir.toPath().resolve("subproject2");
    Files.createDirectories(sub2);
    writeString(
        sub2.resolve("build.gradle").toFile(),
        "plugins {\n"
            + "  id(\"org.spdx.sbom\")\n"
            + "}\n"
            + "configurations.create(\"bundleInside\")\n"
            + "dependencies {\n"
            + "  bundleInside(project(\":subproject1\"))\n"
            + "}\n"
            + "spdxSbom.targets.create(\"release\") { target ->\n"
            + "  configurations = [\"bundleInside\"]"
            + "}\n");

    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments(":subproject2:tasks", "--stacktrace", "--configure-on-demand");
    runner.withProjectDir(projectDir);
    runner.build();
  }

  @Test
  void cannotGenerateReportWithIvyDependenciesWhenMissingDependencies()
      throws IOException, SpdxVerificationException, URISyntaxException {
    writeString(getSettingsFile(), "rootProject.name = 'spdx-functional-test-project'\n");
    writeString(
        getBuildFile(),
        "plugins {\n"
            + "  id('org.spdx.sbom')\n"
            + "  id('java')\n"
            + "}\n"
            + "version = 1\n"
            + "repositories {\n"
            + "  ivy {\n"
            + "    name = \"ivyRepository\"\n"
            + "    url = \""
            + projectDir.getAbsolutePath().replaceAll("\\\\", "/")
            + "/ivy-repository\"\n"
            + "    patternLayout {\n"
            + "      artifact('[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])')\n"
            + "      ivy('[organisation]/[module]/[revision]/ivy-[revision].xml')\n"
            + "      setM2compatible(true)\n"
            + "    }\n"
            + "  }\n"
            + "}\n"
            + "dependencies {\n"
            + "  implementation 'org.example:module1:1.0.0'\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    release {\n"
            + "    }\n"
            + "  }\n"
            + "}\n");

    Path main = projectDir.toPath().resolve(Paths.get("src/main/java/main/Main.java"));
    Files.createDirectories(main.getParent());
    writeString(
        Files.createFile(main).toFile(),
        "package main;\n"
            + "public class Main {\n"
            + "  public static void main(String[] args) {  }\n"
            + "}");

    URL ivyRepositoryFolder = this.getClass().getResource("/ivy-repository");
    FileUtils.copyDirectory(new File(ivyRepositoryFolder.toURI()).getParentFile(), projectDir);

    // Run the build
    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments("spdxSbomForRelease", "--stacktrace");
    runner.withProjectDir(projectDir);
    var result = runner.buildAndFail();

    // Verify
    MatcherAssert.assertThat(
        result.getOutput(),
        Matchers.containsString("No POM file found for dependency org.example:module1:1.0.0"));
  }

  @Test
  void canGenerateReportWithIvyDependenciesWhenIgnoringMissingDependencies()
      throws IOException, SpdxVerificationException, URISyntaxException {
    writeString(getSettingsFile(), "rootProject.name = 'spdx-functional-test-project'\n");
    writeString(
        getBuildFile(),
        "plugins {\n"
            + "  id('org.spdx.sbom')\n"
            + "  id('java')\n"
            + "}\n"
            + "version = 1\n"
            + "repositories {\n"
            + "  ivy {\n"
            + "    name = \"ivyRepository\"\n"
            + "    url = \""
            + projectDir.getAbsolutePath().replaceAll("\\\\", "/")
            + "/ivy-repository\"\n"
            + "    patternLayout {\n"
            + "      artifact('[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])')\n"
            + "      ivy('[organisation]/[module]/[revision]/ivy-[revision].xml')\n"
            + "      setM2compatible(true)\n"
            + "    }\n"
            + "  }\n"
            + "}\n"
            + "dependencies {\n"
            + "  implementation 'org.example:module1:1.0.0'\n"
            + "}\n"
            + "spdxSbom {\n"
            + "  targets {\n"
            + "    release {\n"
            + "      ignoreNonMavenDependencies = true\n"
            + "    }\n"
            + "  }\n"
            + "}\n");

    Path main = projectDir.toPath().resolve(Paths.get("src/main/java/main/Main.java"));
    Files.createDirectories(main.getParent());
    writeString(
        Files.createFile(main).toFile(),
        "package main;\n"
            + "public class Main {\n"
            + "  public static void main(String[] args) {  }\n"
            + "}");

    URL ivyRepositoryFolder = this.getClass().getResource("/ivy-repository");
    FileUtils.copyDirectory(new File(ivyRepositoryFolder.toURI()).getParentFile(), projectDir);

    // Run the build
    GradleRunner runner = GradleRunner.create();
    runner.forwardOutput();
    runner.withPluginClasspath();
    runner.withDebug(true);
    runner.withArguments("spdxSbomForRelease", "--stacktrace");
    runner.withProjectDir(projectDir);
    var result = runner.build();

    Path outputFile = projectDir.toPath().resolve(Paths.get("build/spdx/release.spdx.json"));
    Verify.verify(outputFile.toFile().getAbsolutePath(), SerFileType.JSON);

    MatcherAssert.assertThat(
        result.getOutput(),
        Matchers.containsString("Ignoring dependency without POM file: org.example:module1:1.0.0"));

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
