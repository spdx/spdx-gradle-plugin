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

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecOperations;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.SpdxPackage.SpdxPackageBuilder;
import org.spdx.library.model.enumerations.RelationshipType;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;

public class SpdxDocumentBuilder {
  private final SpdxDocument doc;
  private final SpdxLicenses licenses;
  private final Map<String, ProjectInfo> knownProjects;
  private final HashMap<ComponentIdentifier, SpdxPackage> processedPackages = new HashMap<>();
  private final String sourceInfo;
  private final Map<ComponentIdentifier, File> resolvedArtifacts;
  private final Map<String, URI> mavenArtifactRepositories;
  private final PomInfoFactory pomInfoFactory;

  public SpdxDocumentBuilder(
      Set<ProjectInfo> allProjects,
      ProjectInfo projectInfo,
      ExecOperations execOperations,
      Logger logger,
      IModelStore modelStore,
      String documentUri,
      Map<ComponentArtifactIdentifier, File> resolvedArtifacts,
      Map<String, URI> mavenArtifactRepositories,
      Map<ComponentArtifactIdentifier, File> poms)
      throws InvalidSPDXAnalysisException, IOException, InterruptedException {
    GitInfoProvider gitInfoProvider = new GitInfoProvider(execOperations, logger);
    doc = SpdxModelFactory.createSpdxDocument(modelStore, documentUri, new ModelCopyManager());
    doc.setName(projectInfo.getName());
    doc.setCreationInfo(
        doc.createCreationInfo(
            Collections.singletonList("Tool:spdx-gradle-plugin"),
            ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)));
    this.licenses = SpdxLicenses.newSpdxLicenes(logger, doc);

    this.sourceInfo = gitInfoProvider.getGitInfo().asSourceInfo();
    this.knownProjects =
        allProjects.stream().collect(Collectors.toMap(ProjectInfo::getPath, Function.identity()));

    this.resolvedArtifacts =
        resolvedArtifacts
            .entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().getComponentIdentifier(), Entry::getValue));
    this.mavenArtifactRepositories = mavenArtifactRepositories;
    this.pomInfoFactory = PomInfoFactory.newFactory(poms);
  }

  public void add(@Nullable SpdxPackage parent, ResolvedComponentResult resolvedComponentResult)
      throws InvalidSPDXAnalysisException, IOException, XmlPullParserException,
          InterruptedException {
    if (!processedPackages.containsKey(resolvedComponentResult.getId())) {
      SpdxPackage pkg;
      if (resolvedComponentResult.getId() instanceof ProjectComponentIdentifier) {
        pkg = createProjectPackage(resolvedComponentResult);
      } else if (resolvedComponentResult.getId() instanceof ModuleComponentIdentifier) {
        var result = createMavenModulePackage(resolvedComponentResult);
        if (result.isEmpty()) {
          System.out.println("ignoring: " + resolvedComponentResult.getId());
          return; // ignore this package (maybe it's a bom?)
        }
        pkg = result.get();
      } else {
        throw new RuntimeException(
            "Unknown package type: "
                + resolvedComponentResult.getClass().getName()
                + " "
                + resolvedComponentResult.getId().getClass().getName()
                + " "
                + resolvedComponentResult.getId());
      }
      processedPackages.put(resolvedComponentResult.getId(), pkg);
    }

    SpdxPackage pkg = processedPackages.get(resolvedComponentResult.getId());
    if (parent == null) {
      doc.setDocumentDescribes(Collections.singletonList(pkg));
    } else {
      var rel = doc.createRelationship(parent, RelationshipType.DEPENDENCY_OF, null);
      pkg.addRelationship(rel);
    }

    for (DependencyResult dep : resolvedComponentResult.getDependencies()) {
      if (dep instanceof ResolvedDependencyResult) {
        add(pkg, ((ResolvedDependencyResult) dep).getSelected());
      }
    }
  }

  private SpdxPackage createProjectPackage(ResolvedComponentResult resolvedComponentResult)
      throws InvalidSPDXAnalysisException {
    var projectId = (ProjectComponentIdentifier) resolvedComponentResult.getId();

    ProjectInfo pi = knownProjects.get(projectId.getProjectPath());
    return doc.createPackage(
            doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
            pi.getName(),
            new SpdxNoAssertionLicense(),
            "",
            new SpdxNoAssertionLicense())
        .setFilesAnalyzed(false)
        .setDescription("" + pi.getDescription().orElse(""))
        .setVersionInfo("" + pi.getVersion())
        .setSourceInfo(pi.getPath() + " in " + sourceInfo)
        .setDownloadLocation("NOASSERTION")
        .build();
  }

  private Optional<SpdxPackage> createMavenModulePackage(
      ResolvedComponentResult resolvedComponentResult)
      throws InvalidSPDXAnalysisException, IOException, XmlPullParserException,
          InterruptedException {

    // if the project doesn't resolve to anything, ignore it
    File dependencyFile = resolvedArtifacts.get(resolvedComponentResult.getId());
    if (dependencyFile != null) {

      ModuleVersionIdentifier moduleId = resolvedComponentResult.getModuleVersion();

      PomInfo pomInfo = pomInfoFactory.getInfo(resolvedComponentResult.getId());

      SpdxPackageBuilder spdxPkgBuilder =
          doc.createPackage(
              doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
              moduleId.getGroup() + ":" + moduleId.getName() + ":" + moduleId.getVersion(),
              new SpdxNoAssertionLicense(),
              "NOASSERTION",
              licenses.asSpdxLicense(pomInfo.getLicenses()));

      String sourceRepo =
          ((ResolvedComponentResultInternal) resolvedComponentResult).getRepositoryName();
      if (sourceRepo == null) {
        throw new RuntimeException("Source repo was null?");
      }

      var baseURI = mavenArtifactRepositories.get(sourceRepo);
      String modulePath =
          moduleId.getGroup().replace(".", "/")
              + "/"
              + moduleId.getName()
              + "/"
              + moduleId.getVersion()
              + "/"
              + URLEncoder.encode(dependencyFile.getName(), StandardCharsets.UTF_8);
      spdxPkgBuilder.setDownloadLocation(baseURI.resolve(modulePath).toString());

      String sha1 =
          com.google.common.io.Files.asByteSource(dependencyFile).hash(Hashing.sha1()).toString();
      spdxPkgBuilder.setPackageVerificationCode(
          doc.createPackageVerificationCode(sha1, Collections.emptyList()));

      return Optional.of(spdxPkgBuilder.build());
    }
    return Optional.empty();
  }

  public SpdxDocument getSpdxDocument() {
    return doc;
  }
}
