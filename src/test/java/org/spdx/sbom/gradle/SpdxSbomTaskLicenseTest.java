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
package org.spdx.sbom.gradle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spdx.sbom.gradle.SpdxSbomExtension.Target;

class SpdxSbomTaskLicenseTest {

  @TempDir File projectDir;

  private Project newProjectWithTarget(Action<? super Target> configureTarget) {
    Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
    project.setVersion("1");
    project.getPlugins().apply("java");
    project.getPlugins().apply("org.spdx.sbom");
    SpdxSbomExtension ext = project.getExtensions().getByType(SpdxSbomExtension.class);
    ext.getOnlyUseLocalLicenses().set(true);
    ext.getTargets().create("release", configureTarget);
    return project;
  }

  private static File prepareForDirectExecution(SpdxSbomTask task) throws IOException {
    task.getMavenRepositories().set(Map.of());
    File outputFile = task.getOutputFile().get().getAsFile();
    Files.createDirectories(outputFile.toPath().getParent());
    return outputFile;
  }

  private static Map<String, String> generateDeclaredLicenses(Project project) throws Exception {
    SpdxSbomTask task = (SpdxSbomTask) project.getTasks().getByName("spdxSbomForRelease");
    File outputFile = prepareForDirectExecution(task);
    task.generateSbom();
    assertTrue(outputFile.isFile());
    Map<String, String> result = new HashMap<>();
    JsonObject root =
        JsonParser.parseString(Files.readString(outputFile.toPath())).getAsJsonObject();
    for (JsonElement element : root.getAsJsonArray("packages")) {
      JsonObject pkg = element.getAsJsonObject();
      result.put(pkg.get("name").getAsString(), pkg.get("licenseDeclared").getAsString());
    }
    return result;
  }

  @Test
  void projectWithInvalidLicenseFails() throws Exception {
    Project project = newProjectWithTarget(target -> target.getLicense().set("apache-2.0"));
    SpdxSbomTask task = (SpdxSbomTask) project.getTasks().getByName("spdxSbomForRelease");
    prepareForDirectExecution(task);
    GradleException ex = assertThrows(GradleException.class, task::generateSbom);
    assertThat(ex.getMessage(), containsString("(did you mean 'Apache-2.0'?)"));
  }

  @Test
  void uberPackageAndCompoundLicenseExpressions() throws Exception {
    Project project =
        newProjectWithTarget(
            target -> {
              target.document(
                  doc ->
                      doc.uberPackage(
                          pkg -> {
                            pkg.getName().set("my-uber-package");
                            pkg.getVersion().set("1.0.0");
                            pkg.getSupplier().set("Organization: MyOrg");
                          }));
              target
                  .getLicense()
                  .set("(Apache-2.0 OR MIT) AND GPL-2.0-only WITH Classpath-exception-2.0");
            });
    String expected = "(GPL-2.0-only WITH Classpath-exception-2.0 AND (MIT OR Apache-2.0))";
    assertEquals(
        Map.of("my-uber-package", expected, project.getName(), expected),
        generateDeclaredLicenses(project));
  }
}
