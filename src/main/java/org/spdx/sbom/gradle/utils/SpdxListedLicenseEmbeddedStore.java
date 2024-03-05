/*
 * Copyright 2024 The Project Authors.
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

import java.io.IOException;
import java.io.InputStream;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.storage.listedlicense.SpdxListedLicenseModelStore;

/**
 * Original Author: @goneall {@link org.spdx.storage.listedlicense.SpdxListedLicenseLocalStore}
 *
 * <p>Model store for listend licenses using embedded JSON files in the resources/standard_licenses
 * directory.
 */
public class SpdxListedLicenseEmbeddedStore extends SpdxListedLicenseModelStore {

  static final String LISTED_LICENSE_JSON_LOCAL_DIR = "standard_licenses";

  public SpdxListedLicenseEmbeddedStore() throws InvalidSPDXAnalysisException {
    super();
  }

  @Override
  public InputStream getTocInputStream() throws IOException {
    String fileName = LISTED_LICENSE_JSON_LOCAL_DIR + "/licenses.json";
    InputStream retval = SpdxListedLicenseEmbeddedStore.class.getResourceAsStream("/" + fileName);
    if (retval == null) {
      throw new IOException("Unable to open local local license table of contents file");
    }
    return retval;
  }

  @Override
  public InputStream getLicenseInputStream(String licenseId) throws IOException {

    String fileName = LISTED_LICENSE_JSON_LOCAL_DIR + "/" + licenseId + ".json";
    InputStream retval = SpdxListedLicenseEmbeddedStore.class.getResourceAsStream("/" + fileName);
    if (retval == null) {
      throw new IOException(
          "Unable to open local local license JSON file for license ID " + licenseId);
    }
    return retval;
  }

  @Override
  public InputStream getExceptionTocInputStream() throws IOException {
    String fileName = LISTED_LICENSE_JSON_LOCAL_DIR + "/exceptions.json";
    InputStream retval = SpdxListedLicenseEmbeddedStore.class.getResourceAsStream("/" + fileName);
    if (retval == null) {
      throw new IOException("Unable to open local local license table of contents file");
    }
    return retval;
  }

  @Override
  public InputStream getExceptionInputStream(String exceptionId) throws IOException {
    return getLicenseInputStream(exceptionId);
  }

  @Override
  public void close() throws Exception {
    // Nothing to do for the embedded store
  }
}
