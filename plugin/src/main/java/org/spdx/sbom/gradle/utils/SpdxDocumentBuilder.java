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
package org.spdx.sbom.gradle.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxFile;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.SpdxPackageVerificationCode;
import org.spdx.library.model.enumerations.RelationshipType;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;

public class SpdxDocumentBuilder {
  private final SpdxDocument doc;
  private final Project project;

  private final Map<String, Project> knownProjects;

  private final Set<Path> sourceFiles = new HashSet<>();
  private final Set<Path> resourceFiles = new HashSet<>();

  private final HashMap<ResolvedDependency, ArrayList<ResolvedDependency>> tree = new HashMap<>();
  private final ArrayList<ResolvedDependency> directDependencies = new ArrayList<>();
  private final ArtifactInfoFactory artifactInfoFactory;

  public SpdxDocumentBuilder(Project project, IModelStore modelStore, String documentUri)
      throws InvalidSPDXAnalysisException {
    this.project = project;
    doc = SpdxModelFactory.createSpdxDocument(modelStore, documentUri, new ModelCopyManager());
    doc.setName(project.getName());
    doc.setCreationInfo(
        doc.createCreationInfo(
            Collections.singletonList("Tool:spdx-gradle-plugin"),
            ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)));
    this.artifactInfoFactory = ArtifactInfoFactory.newFactory(project);

    this.knownProjects =
        project
            .getRootProject()
            .getSubprojects()
            .stream()
            .collect(
                Collectors.toMap(
                    p -> p.getGroup() + ":" + p.getName() + ":" + p.getVersion(),
                    Function.identity()));
  }

  public void addSourceSet(SourceSet sourceSet) {
    add(sourceSet.getAllSource(), sourceFiles);
    add(sourceSet.getResources(), resourceFiles);
    Configuration configuration =
        project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
    add(configuration);
  }

  private void add(SourceDirectorySet sourceDirectorySet, Set<Path> target) {
    sourceDirectorySet
        .getSourceDirectories()
        .getAsFileTree()
        .getFiles()
        .stream()
        .filter(File::isFile)
        .map(File::toPath)
        .forEachOrdered(target::add);
  }

  private void add(Configuration configuration) {
    for (ResolvedDependency dep :
        configuration.getResolvedConfiguration().getFirstLevelModuleDependencies()) {
      add(null, dep);
    }
  }

  private void add(ResolvedDependency parent, ResolvedDependency resolvedDependency) {
    if (resolvedDependency.getModuleArtifacts().size() == 0) {
      // we're not dealing with objects that aren't resolved to files
      // this might mean we don't see boms in the sbom
      return;
    }
    if (tree.containsKey(resolvedDependency)) {
      // item already processed, return
      return;
    }
    if (parent == null) {
      directDependencies.add(resolvedDependency);
    } else {
      tree.get(parent).add(resolvedDependency);
    }
    tree.put(resolvedDependency, new ArrayList<>());
    for (ResolvedDependency child : resolvedDependency.getChildren()) {
      add(resolvedDependency, child);
    }
  }

  public SpdxDocument asSpdxDocument()
      throws InvalidSPDXAnalysisException, XmlPullParserException, IOException,
          InterruptedException {
    SpdxLicenses licenses = SpdxLicenses.newSpdxLicenes(project, doc);

    String sourceInfo = GitInfo.extractGitInfo(project).asSourceInfo();

    // create project under analysis object
    SpdxPackage projectPackage =
        doc.createPackage(
                doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
                project.getName(),
                new SpdxNoAssertionLicense(),
                "",
                new SpdxNoAssertionLicense())
            .setFilesAnalyzed(false)
            .setDescription("" + project.getDescription())
            .setVersionInfo("" + project.getVersion())
            .setSourceInfo(sourceInfo)
            .setDownloadLocation("NOASSERTION")
            .build();
    doc.setDocumentDescribes(Collections.singletonList(projectPackage));

    SpdxFileFactory fileFactory = new SpdxFileFactory(doc, project.getProjectDir());

    for (Path src : sourceFiles) {
      SpdxFile spdxFile = fileFactory.newFile(src);
      spdxFile.addRelationship(
          doc.createRelationship(projectPackage, RelationshipType.GENERATES, null));
      projectPackage.addFile(spdxFile);
    }

    for (Path res : resourceFiles) {
      SpdxFile spdxFile = fileFactory.newFile(res);
      spdxFile.addRelationship(
          doc.createRelationship(projectPackage, RelationshipType.CONTAINED_BY, null));
      projectPackage.addFile(spdxFile);
    }

    // create all dependencies
    Map<ResolvedDependency, SpdxPackage> spdxrefs = new HashMap<>();
    for (ResolvedDependency pkg : tree.keySet()) {
      String moduleReference = getModuleReference(pkg);
      // handle project dependencies
      if (knownProjects.containsKey(moduleReference)) {
        Project subProject = knownProjects.get(moduleReference);
        SpdxPackage subProjectPkg =
            doc.createPackage(
                    doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
                    subProject.getName(),
                    new SpdxNoAssertionLicense(),
                    "",
                    new SpdxNoAssertionLicense())
                .setFilesAnalyzed(false)
                .setDescription("" + subProject.getDescription())
                .setVersionInfo("" + subProject.getVersion())
                .setSourceInfo(subProject.getPath() + " in " + sourceInfo)
                .setDownloadLocation("NOASSERTION")
                .build();
        spdxrefs.put(pkg, subProjectPkg);
      }
      // external dependencies
      else {
        ArtifactInfo info = artifactInfoFactory.getInfo(pkg);
        SpdxPackageVerificationCode vc =
            doc.createPackageVerificationCode(info.getSha1(), new ArrayList<>());
        AnyLicenseInfo licenseInfo = licenses.asSpdxLicense(info.getLicenses());
        SpdxPackage spdxPkg =
            doc.createPackage(
                    doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
                    pkg.getName(),
                    licenseInfo,
                    "NOASSERTION",
                    licenseInfo)
                .setPackageVerificationCode(vc)
                .build();
        spdxrefs.put(pkg, spdxPkg);
      }
    }
    // create relationships between dependencies
    for (ResolvedDependency pkg : tree.keySet()) {
      for (ResolvedDependency child : tree.get(pkg)) {
        var rel = doc.createRelationship(spdxrefs.get(child), RelationshipType.DEPENDS_ON, null);
        spdxrefs.get(pkg).addRelationship(rel);
      }
    }
    // special handling for direct dependencies of the project being analyzed
    for (ResolvedDependency dep : directDependencies) {
      var rel = doc.createRelationship(spdxrefs.get(dep), RelationshipType.DEPENDS_ON, null);
      projectPackage.addRelationship(rel);
    }
    return doc;
  }

  private String getModuleReference(ResolvedDependency dep) {
    return dep.getModuleGroup() + ":" + dep.getModuleName() + ":" + dep.getModuleVersion();
  }
}
