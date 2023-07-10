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
package org.spdx.sbom.gradle.uri;

import java.net.URI;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class URIsTest {
  private static ModuleVersionIdentifier moduleId;
  private static String filename;

  @BeforeAll
  public static void initModuleId() {
    moduleId = DefaultModuleVersionIdentifier.newId("com.test", "test", "1.0.0");
    filename = "test-1.0.0.jar";
  }

  @Test
  public void toDownloadLocation() {
    URI downloadLocation =
        URIs.toDownloadLocation(URI.create("https://repo.maven.org/maven2"), moduleId, filename);
    Assertions.assertEquals(
        "https://repo.maven.org/maven2/com/test/test/1.0.0/test-1.0.0.jar",
        downloadLocation.toString());
  }

  @Test
  public void toDownloadLocation_withTrailingSlash() {
    URI downloadLocation =
        URIs.toDownloadLocation(URI.create("https://repo.maven.org/maven2/"), moduleId, filename);
    Assertions.assertEquals(
        "https://repo.maven.org/maven2/com/test/test/1.0.0/test-1.0.0.jar",
        downloadLocation.toString());
  }

  @Test
  public void toDownloadLocation_noassertion() {
    URI downloadLocation = URIs.toDownloadLocation(URI.create("NOASSERTION"), moduleId, filename);
    Assertions.assertEquals("NOASSERTION", downloadLocation.toString());
  }

  @Test
  public void toPurl_mavenCentral() {
    String purl = URIs.toPurl(URI.create("https://repo.maven.org/maven2"), moduleId);
    Assertions.assertEquals("pkg:maven/com.test/test@1.0.0", purl);
  }

  @Test
  public void toPurl_otherRepo() {
    String purl = URIs.toPurl(URI.create("https://repo.other.org/maven2"), moduleId);
    Assertions.assertEquals(
        "pkg:maven/com.test/test@1.0.0?repository_url=repo.other.org%2Fmaven2", purl);
  }

  @Test
  public void toPurl_trailingSlashes() {
    String purl = URIs.toPurl(URI.create("https://repo.other.org/maven2///"), moduleId);
    Assertions.assertEquals(
        "pkg:maven/com.test/test@1.0.0?repository_url=repo.other.org%2Fmaven2", purl);
  }

  @Test
  public void toPurl_noassertion() {
    String purl = URIs.toPurl(URI.create("NOASSERTION"), moduleId);
    Assertions.assertEquals("pkg:maven/com.test/test@1.0.0", purl);
  }
}
