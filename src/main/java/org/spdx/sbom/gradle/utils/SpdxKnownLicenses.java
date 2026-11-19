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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.spdx.storage.listedlicense.LicenseJsonTOC;

// modified from https://github.com/spdx/spdx-maven-plugin org.spdx.maven.MavenToSpdxLicenseMapper
public class SpdxKnownLicenses {

  private final ImmutableMap<String, String> licensesByUrl;
  private final ImmutableMap<String, String> licensesByName;

  private static final String SPDX_LICENSE_URL_PREFIX = "https://spdx.org/licenses/";
  private static final String NORMALIZED_SPDX_LICENSE_URL_PREFIX = "http://spdx.org/licenses/";
  private static final String REMOTE_LICENSES = SPDX_LICENSE_URL_PREFIX + "licenses.json";
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private SpdxKnownLicenses(Map<String, String> licensesByUrl, Map<String, String> licensesByName) {
    this.licensesByUrl = ImmutableMap.copyOf(licensesByUrl);
    this.licensesByName = ImmutableMap.copyOf(licensesByName);
  }

  public static SpdxKnownLicenses knownLicenses(boolean onlyUseLocalLicenses)
      throws IOException, JsonParseException {
    InputStream inputStream = null;
    if (!onlyUseLocalLicenses) {
      try {
        java.net.URL url = new java.net.URL(REMOTE_LICENSES);
        inputStream = url.openStream();
      } catch (IOException e) {
        // Fallback to local cache if offline
      }
    }
    if (inputStream == null) {
      inputStream =
          SpdxKnownLicenses.class
              .getClassLoader()
              .getResourceAsStream("resources/stdlicenses/licenses.json");
    }
    if (inputStream == null) {
      throw new IOException("Could not load licenses.json from web or classpath resources");
    }
    return fromStream(inputStream);
  }

  private static SpdxKnownLicenses fromStream(InputStream stream)
      throws IOException, JsonParseException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
      LicenseJsonTOC jsonToc = new Gson().fromJson(reader, LicenseJsonTOC.class);
      return new SpdxKnownLicenses(getLicenseToUrlMap(jsonToc), getLicenseNameToIdMap(jsonToc));
    }
  }

  // See: spdx-maven-plugin:MavenToSpdxLicenseMapper
  private static Map<String, String> getLicenseToUrlMap(LicenseJsonTOC jsonToc) {
    Map<String, String> licenseUrlToSpdxId =
        indexPreferringActive(jsonToc, SpdxKnownLicenses::candidateUrlsFor);

    // Custom licenses not reflected in the license list
    licenseUrlToSpdxId.put("http://www.apache.org/licenses/LICENSE-2.0.txt", "Apache-2.0");
    licenseUrlToSpdxId.put("http://www.opensource.org/licenses/cpl1.0.txt", "CPL-1.0");
    licenseUrlToSpdxId.put("http://www.opensource.org/licenses/mit-license.php", "MIT");
    // Duplicated across multiple SPDX license IDs; map to the license it originally targeted
    licenseUrlToSpdxId.put("http://www.mozilla.org/MPL/MPL-1.0.txt", "MPL-1.0");

    return licenseUrlToSpdxId;
  }

  private static List<String> candidateUrlsFor(LicenseJsonTOC.LicenseJson licenseJson) {
    String base = NORMALIZED_SPDX_LICENSE_URL_PREFIX + licenseJson.getLicenseId();
    List<String> urls = new ArrayList<>();
    urls.add(base);
    urls.add(base + ".html");
    urls.add(base + ".txt");
    urls.add(base + ".json");
    if (licenseJson.getSeeAlso() != null) {
      for (String otherUrl : licenseJson.getSeeAlso()) {
        urls.add(normalize(otherUrl));
      }
    }
    return urls;
  }

  private static Map<String, String> getLicenseNameToIdMap(LicenseJsonTOC jsonToc) {
    return indexPreferringActive(jsonToc, lic -> List.of(normalizeName(lic.getName())));
  }

  /**
   * Indexes keys to SPDX license IDs in two passes: active IDs first, then deprecated IDs for keys
   * never seen on any active license. Ambiguous keys within a pass are excluded.
   */
  private static Map<String, String> indexPreferringActive(
      LicenseJsonTOC jsonToc,
      Function<LicenseJsonTOC.LicenseJson, Collection<String>> keysExtractor) {
    Map<String, String> index = new HashMap<>();
    Set<String> skip = Set.of();
    for (boolean deprecated : new boolean[] {false, true}) {
      for (LicenseJsonTOC.LicenseJson lic : jsonToc.getLicenses()) {
        if (lic.isDeprecatedLicenseId() == deprecated) {
          for (String key : keysExtractor.apply(lic)) {
            if (!skip.contains(key)) {
              index.merge(key, lic.getLicenseId(), (a, b) -> a.equals(b) ? a : "");
            }
          }
        }
      }
      skip = Set.copyOf(index.keySet());
    }
    index.values().removeIf(String::isEmpty);
    return index;
  }

  Optional<String> getIdForNormalizedUrl(String normalizedUrl) {
    return Optional.ofNullable(licensesByUrl.get(normalizedUrl));
  }

  Optional<String> getIdForName(String name) {
    return Optional.ofNullable(licensesByName.get(normalizeName(name)));
  }

  static String normalize(String licenseUrl) {
    String normalized =
        licenseUrl.startsWith("https://") ? "http://" + licenseUrl.substring(8) : licenseUrl;
    int end = normalized.length();
    while (end > 0 && normalized.charAt(end - 1) == '/') {
      end--;
    }
    return end == normalized.length() ? normalized : normalized.substring(0, end);
  }

  private static String normalizeName(String name) {
    return WHITESPACE.matcher(name).replaceAll(" ").toLowerCase(Locale.ROOT);
  }
}
