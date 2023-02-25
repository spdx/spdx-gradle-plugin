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

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.model.License;
import org.spdx.storage.listedlicense.LicenseJsonTOC;

// modified from https://github.com/spdx/spdx-maven-plugin org.spdx.maven.MavenToSpdxLicenseMapper
public class SpdxKnownLicenses {

  // this is modifiable as non-standard licenses can be added in
  private final ImmutableMap<String, String> licenses;

  private static final String SPDX_LICENSE_URL_PREFIX = "https://spdx.org/licenses/";
  private static final String REMOTE_LICENSES = SPDX_LICENSE_URL_PREFIX + "licenses.json";

  private SpdxKnownLicenses(Map<String, String> licenses) {
    this.licenses = ImmutableMap.copyOf(licenses);
  }

  public static SpdxKnownLicenses fromRemote()
      throws IOException, InterruptedException, JsonParseException {
    var licenseReq = HttpRequest.newBuilder(URI.create(REMOTE_LICENSES)).GET().build();
    var httpClient = HttpClients.newClient(30);
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                httpClient.send(licenseReq, BodyHandlers.ofInputStream()).body()))) {
      return new SpdxKnownLicenses(getLicenseToUrlMap(reader));
    }
  }

  // See: spdx-maven-plugin:MavenToSpdxLicenseMapper
  private static HashMap<String, String> getLicenseToUrlMap(BufferedReader jsonReader)
      throws JsonParseException {
    Gson gson = new Gson();
    LicenseJsonTOC jsonToc = gson.fromJson(jsonReader, LicenseJsonTOC.class);

    HashMap<String, String> licenseUrlToSpdxId = new HashMap<>();
    List<String> urlsWithMultipleIds = new ArrayList<>();
    for (LicenseJsonTOC.LicenseJson licenseJson : jsonToc.getLicenses()) {
      String licenseId = licenseJson.getLicenseId();
      licenseUrlToSpdxId.put(SPDX_LICENSE_URL_PREFIX + licenseId, licenseId);
      // see also has alt urls
      if (licenseJson.getSeeAlso() != null) {
        for (String otherUrl : licenseJson.getSeeAlso()) {
          // standardize keys against http
          String url = normalize(otherUrl);
          if (licenseUrlToSpdxId.containsKey(url)) {
            urlsWithMultipleIds.add(url);
          } else {
            licenseUrlToSpdxId.put(url, licenseId);
          }
        }
      }
    }
    // Remove any mappings which have ambiguous URL mappings
    for (String redundantUrl : urlsWithMultipleIds) {
      licenseUrlToSpdxId.remove(redundantUrl);
    }

    // custom licenses not reflected in the license list
    licenseUrlToSpdxId.put("http://www.apache.org/licenses/LICENSE-2.0.txt", "Apache-2.0");
    licenseUrlToSpdxId.put("http://www.opensource.org/licenses/cpl1.0.txt", "CPL-1.0");
    licenseUrlToSpdxId.put("http://www.opensource.org/licenses/mit-license.php", "MIT");
    // The following is in the listed licenses, but is duplicated in multiple SPDX license ID's
    // adding it back for the license it was originally targeted for
    licenseUrlToSpdxId.put("http://www.mozilla.org/MPL/MPL-1.0.txt", "MPL-1.0");

    return licenseUrlToSpdxId;
  }

  public String getIdFor(License license) {
    return licenses.get(normalize(license.getUrl()));
  }

  public boolean contains(License license) {
    return licenses.containsKey(normalize(license.getUrl()));
  }

  static String normalize(String licenseUrl) {
    return licenseUrl.replaceAll("https", "http");
  }
}
