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
import java.io.IOException;
import java.net.URI;
import java.util.List;
import javax.inject.Inject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.SpdxDocument;
import org.spdx.sbom.gradle.extensions.SpdxSbomTaskExtension;
import org.spdx.sbom.gradle.utils.ProjectInfo;
import org.spdx.sbom.gradle.utils.SpdxDocumentBuilder;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

public abstract class SpdxSbomTask extends DefaultTask {

  @Inject
  protected abstract ExecOperations getExecOperations();

  @Input
  abstract Property<ResolvedComponentResult> getRootComponent();

  @Input
  abstract MapProperty<ComponentArtifactIdentifier, File> getResolvedArtifacts();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @Input
  abstract Property<ProjectInfo> getProjectInfo();

  @Input
  abstract SetProperty<ProjectInfo> getAllProjects();

  @Input
  abstract MapProperty<String, URI> getMavenRepositories();

  @Input
  abstract MapProperty<ComponentArtifactIdentifier, File> getPoms();

  @Input
  abstract Property<String> getFilename();

  @Internal
  public abstract Property<SpdxSbomTaskExtension> getTaskExtension();

  @TaskAction
  public void generateSbom()
      throws InvalidSPDXAnalysisException, IOException, XmlPullParserException,
          InterruptedException {
    ISerializableModelStore modelStore =
        new MultiFormatStore(new InMemSpdxStore(), Format.JSON_PRETTY);
    SpdxDocumentBuilder documentBuilder =
        new SpdxDocumentBuilder(
            getAllProjects().get(),
            getProjectInfo().get(),
            getExecOperations(),
            getLogger(),
            modelStore,
            getProjectInfo().get().getName(),
            getResolvedArtifacts().get(),
            getMavenRepositories().get(),
            getPoms().get(),
            getTaskExtension().getOrNull());

    documentBuilder.add(null, getRootComponent().get());

    SpdxDocument doc = documentBuilder.getSpdxDocument();

    // shows verification errors in the final doc
    List<String> verificationErrors = doc.verify();
    verificationErrors.forEach(errors -> getLogger().warn(errors));

    FileOutputStream out =
        new FileOutputStream(getOutputDirectory().file(getFilename()).get().getAsFile());
    modelStore.serialize(doc.getDocumentUri(), out);
  }
}
