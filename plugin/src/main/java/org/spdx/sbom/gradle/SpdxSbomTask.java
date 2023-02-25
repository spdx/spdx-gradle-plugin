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

import java.io.FileOutputStream;
import java.io.IOException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.SpdxDocument;
import org.spdx.sbom.gradle.utils.SpdxDocumentBuilder;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

public abstract class SpdxSbomTask extends DefaultTask {

  @Input
  public abstract ListProperty<String> getSourceSets();

  // @Input
  // public abstract Property<String> getComponent();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @TaskAction
  public void generateSbom()
      throws InvalidSPDXAnalysisException, IOException, XmlPullParserException,
          InterruptedException {
    if (!getSourceSets().isPresent()) {
      throw new GradleException("No sourceSets were configured for sbom generation");
    }
    ISerializableModelStore modelStore =
        new MultiFormatStore(new InMemSpdxStore(), Format.JSON_PRETTY);
    SpdxDocumentBuilder documentBuilder =
        new SpdxDocumentBuilder(getProject(), modelStore, getProject().getName());

    for (String sourceSetName : getSourceSets().get()) {
      SourceSet sourceSet =
          getProject()
              .getExtensions()
              .getByType(JavaPluginExtension.class)
              .getSourceSets()
              .getByName(sourceSetName);
      documentBuilder.addSourceSet(sourceSet);
    }
    FileOutputStream out =
        new FileOutputStream(getOutputDirectory().file("spdx.sbom.json").get().getAsFile());
    SpdxDocument doc = documentBuilder.asSpdxDocument();

    // shows verification errors in the final doc
    System.out.println(doc.verify());

    modelStore.serialize(doc.getDocumentUri(), out);
  }
}
