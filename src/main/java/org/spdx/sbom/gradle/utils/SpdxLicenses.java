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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.logging.Logger;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.ExtractedLicenseInfo;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.ListedLicenses;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.sbom.gradle.maven.PomInfo.LicenseInfo;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;

public class SpdxLicenses {

  private final Logger logger;
  private final Map<String, AnyLicenseInfo> projectLicenses = new HashMap<>();

  private final SpdxDocument doc;
  private final IModelStore modelStore;
  private final ModelCopyManager copyManager;
  private final SpdxKnownLicenses knownLicenses;

  private SpdxLicenses(
      Logger logger,
      SpdxDocument doc,
      IModelStore modelStore,
      ModelCopyManager copyManager,
      SpdxKnownLicenses knownLicenses) {
    this.logger = logger;
    this.doc = doc;
    this.modelStore = modelStore;
    this.copyManager = copyManager;
    this.knownLicenses = knownLicenses;
  }

  public static SpdxLicenses newSpdxLicenes(
      Logger logger, SpdxDocument doc, SpdxKnownLicenses spdxKnownLicenses)
      throws InvalidSPDXAnalysisException {
    ListedLicenses.initializeListedLicenses(new SpdxListedLicenseEmbeddedStore());
    return new SpdxLicenses(
        logger, doc, doc.getModelStore(), doc.getCopyManager(), spdxKnownLicenses);
  }

  public AnyLicenseInfo asSpdxLicense(List<LicenseInfo> licenses)
      throws InvalidSPDXAnalysisException {
    if (licenses.size() == 0) {
      return new SpdxNoAssertionLicense();
    }
    if (licenses.size() == 1) {
      return getOrCreateLicense(licenses.get(0));
    }
    List<AnyLicenseInfo> spdxLicenses = new ArrayList<>();
    for (var license : licenses) {
      spdxLicenses.add(getOrCreateLicense(license));
    }
    return doc.createConjunctiveLicenseSet(spdxLicenses);
  }

  private AnyLicenseInfo getOrCreateLicense(LicenseInfo license)
      throws InvalidSPDXAnalysisException {
    // convert all license

    if (license.getUrl() == null) {
      logger.warn("Ignoring unusual license " + license);
      return new SpdxNoAssertionLicense();
    }

    String normalizedLicenseUrl = SpdxKnownLicenses.normalize(license.getUrl());
    if (projectLicenses.containsKey(normalizedLicenseUrl)) {
      return projectLicenses.get(normalizedLicenseUrl);
    }
    // it's a known license
    if (knownLicenses.contains(license)) {
      var knownLicense =
          LicenseInfoFactory.parseSPDXLicenseString(
              knownLicenses.getIdFor(license), modelStore, doc.getDocumentUri(), copyManager);
      projectLicenses.put(normalizedLicenseUrl, knownLicense);
      return knownLicense;
    }

    // handle new unknown licenses
    // this is maybe not preferable, alternative is the user defining all the licenses
    logger.debug("Non spdx-standard license detected in package: " + license);
    var unknownLicense = createNewUnknownLicense(license);
    projectLicenses.put(normalizedLicenseUrl, unknownLicense);
    return unknownLicense;
  }

  private AnyLicenseInfo createNewUnknownLicense(LicenseInfo license)
      throws InvalidSPDXAnalysisException {
    var licenseId = modelStore.getNextId(IdType.LicenseRef, doc.getDocumentUri());
    ExtractedLicenseInfo unknown =
        new ExtractedLicenseInfo(modelStore, doc.getDocumentUri(), licenseId, copyManager, true);
    unknown.setName(license.getName());
    unknown.setExtractedText(license.getName());
    unknown.setSeeAlso(Collections.singleton(SpdxKnownLicenses.normalize(license.getUrl())));
    doc.addExtractedLicenseInfos(unknown);
    return LicenseInfoFactory.parseSPDXLicenseString(
        licenseId, modelStore, doc.getDocumentUri(), copyManager);
  }
}
