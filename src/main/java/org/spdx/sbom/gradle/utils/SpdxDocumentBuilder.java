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

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxConstants;
import org.spdx.library.model.ReferenceType;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.SpdxPackage.SpdxPackageBuilder;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.enumerations.ReferenceCategory;
import org.spdx.library.model.enumerations.RelationshipType;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.sbom.gradle.extensions.SpdxSbomTaskExtension;
import org.spdx.sbom.gradle.maven.PomInfo;
import org.spdx.sbom.gradle.project.DocumentInfo;
import org.spdx.sbom.gradle.project.ProjectInfo;
import org.spdx.sbom.gradle.project.ScmInfo;
import org.spdx.sbom.gradle.uri.URIs;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;

public class SpdxDocumentBuilder {
  private final SpdxDocument doc;
  private final SpdxPackage rootPackage;
  private final RootPackageIdentifier rootPackageId;
  private final SpdxLicenses licenses;
  private final Map<String, ProjectInfo> knownProjects;
  private final HashMap<ComponentIdentifier, SpdxPackage> spdxPackages = new HashMap<>();

  private final HashMap<ComponentIdentifier, LinkedHashSet<ComponentIdentifier>> tree =
      new LinkedHashMap<>();
  private final Map<ComponentIdentifier, File> resolvedArtifacts;
  private final Map<String, URI> mavenArtifactRepositories;
  private final Map<String, PomInfo> poms;
  private final Logger logger;
  private final DocumentInfo documentInfo;
  private final ProjectInfo describesProject;

  @Nullable private final SpdxSbomTaskExtension taskExtension;
  private final ScmInfo scmInfo;

  private static class RootPackageIdentifier implements ComponentIdentifier {
    @Override
    public @NotNull String getDisplayName() {
      return "rootProject";
    }
  }

  public SpdxDocumentBuilder(
      String projectPath,
      Set<ProjectInfo> allProjects,
      Logger logger,
      IModelStore modelStore,
      Map<ComponentArtifactIdentifier, File> resolvedArtifacts,
      Map<String, URI> mavenArtifactRepositories,
      Map<String, PomInfo> poms,
      SpdxSbomTaskExtension spdxSbomTaskExtension,
      DocumentInfo documentInfo,
      ScmInfo scmInfo,
      SpdxKnownLicenses knownLicenses)
      throws InvalidSPDXAnalysisException {
    this.documentInfo = documentInfo;
    doc =
        SpdxModelFactory.createSpdxDocument(
            modelStore, documentInfo.getNamespace(), new ModelCopyManager());
    doc.setName(documentInfo.getName());

    ImmutableList.Builder<String> creators = ImmutableList.builder();
    creators.add("Tool: spdx-gradle-plugin");
    documentInfo.getCreator().ifPresent(creators::add);

    doc.setCreationInfo(
        doc.createCreationInfo(
            creators.build(),
            ZonedDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_DATE_TIME)));
    if (documentInfo.getUberPackageInfo().isPresent()) {
      var uberPackageInfo = documentInfo.getUberPackageInfo().get();
      this.rootPackage =
          doc.createPackage(
                  doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
                  uberPackageInfo.getName(),
                  new SpdxNoAssertionLicense(),
                  "NOASSERTION",
                  new SpdxNoAssertionLicense())
              .setSupplier(uberPackageInfo.getSupplier())
              .setVersionInfo(uberPackageInfo.getVersion())
              .setDownloadLocation("NOASSERTION")
              .setFilesAnalyzed(false)
              .build();
      this.rootPackageId = new RootPackageIdentifier();
      doc.setDocumentDescribes(Collections.singletonList(this.rootPackage));
      this.spdxPackages.put(rootPackageId, rootPackage);
      this.tree.putIfAbsent(rootPackageId, new LinkedHashSet<>());
    } else {
      this.rootPackage = null;
      this.rootPackageId = null;
    }

    this.licenses = SpdxLicenses.newSpdxLicenes(logger, doc, knownLicenses);

    this.logger = logger;
    this.scmInfo = scmInfo;
    this.knownProjects =
        allProjects.stream().collect(Collectors.toMap(ProjectInfo::getPath, Function.identity()));
    this.describesProject = knownProjects.get(projectPath);

    this.resolvedArtifacts =
        resolvedArtifacts
            .entrySet()
            .stream()
            .collect(Collectors.toMap(e -> e.getKey().getComponentIdentifier(), Entry::getValue));
    this.mavenArtifactRepositories = mavenArtifactRepositories;
    this.poms = poms;

    this.taskExtension = spdxSbomTaskExtension;
  }

  public void add(ResolvedComponentResult root) throws InvalidSPDXAnalysisException, IOException {
    add(rootPackageId, root, new HashSet<>());
    doc.setDocumentDescribes(
        Collections.singletonList(
            rootPackage == null ? spdxPackages.get(root.getId()) : rootPackage));

    for (var pkg : tree.keySet()) {
      for (var child : tree.get(pkg)) {
        var rel =
            doc.createRelationship(spdxPackages.get(child), RelationshipType.DEPENDS_ON, null);
        spdxPackages.get(pkg).addRelationship(rel);
      }
    }
  }

  private void add(
      ComponentIdentifier parent,
      ResolvedComponentResult component,
      Set<ComponentIdentifier> visited)
      throws InvalidSPDXAnalysisException, IOException {
    if (visited.contains(component.getId())) {
      return;
    }
    visited.add(component.getId());

    ComponentIdentifier effectiveParent;
    if (maybeAddPackage(parent, component)) {
      effectiveParent = component.getId();
    } else {
      effectiveParent = parent;
    }

    for (var child : component.getDependencies()) {
      if (child instanceof ResolvedDependencyResult) {
        add(effectiveParent, ((ResolvedDependencyResult) child).getSelected(), visited);
      }
    }
  }

  private boolean maybeAddPackage(ComponentIdentifier parent, ResolvedComponentResult component)
      throws InvalidSPDXAnalysisException, IOException {
    if (spdxPackages.containsKey(component.getId())) {
      return true;
    }

    Optional<SpdxPackage> maybePackage = createPackageIfNeeded(component);
    if (maybePackage.isEmpty()) {
      logger.info("ignoring: " + component.getId());
      return false;
    }

    spdxPackages.put(component.getId(), maybePackage.get());
    tree.putIfAbsent(component.getId(), new LinkedHashSet<>());
    if (parent != null) {
      tree.get(parent).add(component.getId());
    }

    return true;
  }

  private Optional<SpdxPackage> createPackageIfNeeded(ResolvedComponentResult component)
      throws InvalidSPDXAnalysisException, IOException {
    if (component.getId() instanceof ProjectComponentIdentifier) {
      return shouldCreatePackageForProject(component)
          ? Optional.of(createProjectPackage(component))
          : Optional.empty();
    } else if (component.getId() instanceof ModuleComponentIdentifier) {
      return createMavenModulePackage(component);
    } else {
      throw new RuntimeException(
          "Unknown package type: "
              + component.getClass().getName()
              + " "
              + component.getId().getClass().getName()
              + " "
              + component.getId());
    }
  }

  private boolean shouldCreatePackageForProject(ResolvedComponentResult resolvedComponentResult) {
    if (taskExtension == null) {
      return true;
    }
    var projectId = (ProjectComponentIdentifier) resolvedComponentResult.getId();
    ProjectInfo pi = knownProjects.get(projectId.getProjectPath());
    return taskExtension.shouldCreatePackageForProject(pi);
  }

  private SpdxPackage createProjectPackage(ResolvedComponentResult resolvedComponentResult)
      throws InvalidSPDXAnalysisException {
    var projectId = (ProjectComponentIdentifier) resolvedComponentResult.getId();

    resolvedComponentResult.getVariants();
    ProjectInfo pi = knownProjects.get(projectId.getProjectPath());
    var version = pi.getVersion();
    if (version.equals("unspecified")) {
      logger.warn(
          "spdx sboms require a version but project: "
              + pi.getName()
              + " has no specified version");
      version = "NOASSERTION";
    }
    var supplier = "NOASSERTION";
    if (pi == describesProject) {
      supplier = documentInfo.getPackageSupplier().orElse("NOASSERTION");
      if (supplier.equals("NOASSERTION")) {
        logger.warn("supplier not set for project " + pi.getName());
      }
    }
    SpdxPackageBuilder builder =
        doc.createPackage(
                doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
                pi.getName(),
                new SpdxNoAssertionLicense(),
                "NOASSERTION",
                new SpdxNoAssertionLicense())
            .setFilesAnalyzed(false)
            .setDescription(pi.getDescription().orElse(""))
            .setDownloadLocation("NOASSERTION")
            .setVersionInfo(version)
            .setSupplier(supplier);

    // we want to eventually use downloadLocation instead of sourceInfo, but we'll use sourceInfo
    // for now since we don't have good defaults
    if (taskExtension != null) {
      builder.setSourceInfo(taskExtension.mapScmForProject(scmInfo, pi).getDownloadLocation(pi));
    } else {
      builder.setSourceInfo(scmInfo.getDownloadLocation(pi));
    }
    return builder.build();
  }

  private Optional<SpdxPackage> createMavenModulePackage(
      ResolvedComponentResult resolvedComponentResult)
      throws InvalidSPDXAnalysisException, IOException {

    // if the project doesn't resolve to anything, ignore it
    File dependencyFile = resolvedArtifacts.get(resolvedComponentResult.getId());
    if (dependencyFile != null) {
      ModuleVersionIdentifier moduleId = resolvedComponentResult.getModuleVersion();
      PomInfo pomInfo = poms.get(resolvedComponentResult.getId().getDisplayName());

      SpdxPackageBuilder spdxPkgBuilder =
          doc.createPackage(
                  doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
                  moduleId.getGroup() + ":" + moduleId.getName() + ":" + moduleId.getVersion(),
                  new SpdxNoAssertionLicense(),
                  "NOASSERTION",
                  licenses.asSpdxLicense(pomInfo.getLicenses()))
              .setSupplier("NOASSERTION");

      String sourceRepo =
          ((ResolvedComponentResultInternal) resolvedComponentResult).getRepositoryName();
      if (sourceRepo == null) {
        throw new RuntimeException("Source repo was null?");
      }

      var repoUri = mavenArtifactRepositories.get(sourceRepo);
      if (taskExtension != null) {
        repoUri = taskExtension.mapRepoUri(repoUri, moduleId);
      }
      spdxPkgBuilder.setDownloadLocation(
          URIs.toDownloadLocation(repoUri, moduleId, dependencyFile.getName()).toString());

      var externalRef =
          doc.createExternalRef(
              ReferenceCategory.PACKAGE_MANAGER,
              new ReferenceType(SpdxConstants.SPDX_LISTED_REFERENCE_TYPES_PREFIX + "purl"),
              URIs.toPurl(repoUri, moduleId),
              null);
      spdxPkgBuilder.setExternalRefs(Collections.singletonList(externalRef));

      String sha1 =
          com.google.common.io.Files.asByteSource(dependencyFile).hash(Hashing.sha1()).toString();
      var checksumSha1 = doc.createChecksum(ChecksumAlgorithm.SHA1, sha1);
      String sha256 =
          com.google.common.io.Files.asByteSource(dependencyFile).hash(Hashing.sha256()).toString();
      var checksumSha256 = doc.createChecksum(ChecksumAlgorithm.SHA256, sha256);
      spdxPkgBuilder.setChecksums(List.of(checksumSha1, checksumSha256));
      spdxPkgBuilder.setFilesAnalyzed(false);

      return Optional.of(spdxPkgBuilder.build());
    }
    return Optional.empty();
  }

  public SpdxDocument getSpdxDocument() {
    return doc;
  }
}
