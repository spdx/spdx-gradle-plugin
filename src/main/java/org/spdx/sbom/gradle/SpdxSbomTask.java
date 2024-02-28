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
package org.spdx.sbom.gradle;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.model.SpdxDocument;
import org.spdx.sbom.gradle.extensions.SpdxSbomTaskExtension;
import org.spdx.sbom.gradle.maven.PomInfo;
import org.spdx.sbom.gradle.project.DocumentInfo;
import org.spdx.sbom.gradle.project.ProjectInfoService;
import org.spdx.sbom.gradle.project.ScmInfo;
import org.spdx.sbom.gradle.utils.SpdxDocumentBuilder;
import org.spdx.sbom.gradle.utils.SpdxKnownLicensesService;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

public abstract class SpdxSbomTask extends DefaultTask {

  @Internal
  abstract Property<SpdxKnownLicensesService> getSpdxKnownLicensesService();

  @Input
  abstract ListProperty<ResolvedComponentResult> getRootComponents();

  @Input
  abstract MapProperty<ComponentArtifactIdentifier, File> getResolvedArtifacts();

  @OutputFile
  public abstract RegularFileProperty getOutputFile();

  @Internal
  abstract Property<ProjectInfoService> getProjectInfoService();

  @Input
  abstract MapProperty<String, URI> getMavenRepositories();

  @Input
  abstract MapProperty<String, PomInfo> getPoms();

  @Input
  abstract Property<DocumentInfo> getDocumentInfo();

  @Input
  abstract Property<ScmInfo> getScmInfo();

  @Input
  abstract Property<String> getProjectPath();

  @Internal
  public abstract Property<SpdxSbomTaskExtension> getTaskExtension();

  @TaskAction
  public void generateSbom() throws Exception {
    ISerializableModelStore modelStore =
        new MultiFormatStore(new InMemSpdxStore(), Format.JSON_PRETTY);
    SpdxDocumentBuilder documentBuilder =
        new SpdxDocumentBuilder(
            getProjectPath().get(),
            getProjectInfoService().get().getAllProjectInfo(),
            getLogger(),
            modelStore,
            getResolvedArtifacts().get(),
            getMavenRepositories().get(),
            getPoms().get(),
            getTaskExtension().getOrNull(),
            getDocumentInfo().get(),
            getScmInfo().get(),
            getSpdxKnownLicensesService().get().getKnownLicenses());

    for (var rootComponent : getRootComponents().get()) {
      documentBuilder.add(rootComponent);
    }

    SpdxDocument doc = documentBuilder.getSpdxDocument();

    // shows verification errors in the final doc
    List<String> verificationErrors = doc.verify();
    verificationErrors.forEach(errors -> getLogger().warn(errors));

    FileOutputStream out = new FileOutputStream(getOutputFile().get().getAsFile());
    modelStore.serialize(doc.getDocumentUri(), out);
  }
}
