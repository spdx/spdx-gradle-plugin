/*
 * Copyright 2026 The Project Authors.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.spdx.sbom.gradle.maven.PomInfo.LicenseInfo.of;

import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logging;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxModelFactoryCompatV2;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.ConjunctiveLicenseSet;
import org.spdx.library.model.v2.license.DisjunctiveLicenseSet;
import org.spdx.library.model.v2.license.ExtractedLicenseInfo;
import org.spdx.library.model.v2.license.InvalidLicenseExpression;
import org.spdx.library.model.v2.license.SpdxListedLicense;
import org.spdx.library.model.v2.license.SpdxNoAssertionLicense;
import org.spdx.library.model.v2.license.WithExceptionOperator;
import org.spdx.storage.simple.InMemSpdxStore;

class SpdxLicensesTest {

  private static SpdxKnownLicenses knownLicenses;

  @BeforeAll
  static void setUp() throws Exception {
    System.setProperty("org.spdx.useJARLicenseInfoOnly", "true");
    SpdxModelFactory.init();
    knownLicenses = SpdxKnownLicenses.knownLicenses(true);
  }

  private static SpdxLicenses newLicenses() throws Exception {
    SpdxDocument doc =
        SpdxModelFactoryCompatV2.createSpdxDocumentV2(
            new InMemSpdxStore(), "https://example.com/test", new ModelCopyManager());
    return newLicenses(doc);
  }

  private static SpdxLicenses newLicenses(SpdxDocument doc) {
    return SpdxLicenses.newSpdxLicenes(
        Logging.getLogger(SpdxLicensesTest.class), doc, knownLicenses);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Apache-2.0",
        "  MIT  ",
        "Apache-2.0 OR MIT",
        "(Apache-2.0 AND MIT) OR BSD-3-Clause",
        "GPL-2.0-only WITH Classpath-exception-2.0",
        "LGPL-2.1+",
        "( Apache-2.0 OR (LGPL-2.1+) )",
        "LGPL-2.1+ WITH WxWindows-exception-3.1",
        "NONE",
        "NOASSERTION"
      })
  void parseSpdxLicense_validExpressions(String expr) throws Exception {
    AnyLicenseInfo parsed = newLicenses().parseSpdxLicense(expr);
    assertFalse(parsed instanceof InvalidLicenseExpression);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "apache-2.0",
        "Apache-2.0 or MIT",
        "GPL-2.0-only WITH classpath-exception-2.0",
        "LicenseRef-custom",
        "DocumentRef-ext:LicenseRef-custom",
        "Apache-2.0 OR NONE",
        "Apache-2.0 AND NOASSERTION",
        "()",
        "(Apache-2.0",
        "Apache-2.0)",
        "Apache-2.0 AND",
        "AND Apache-2.0",
        "Apache-2.0 MIT",
        "(Apache-2.0) WITH Classpath-exception-2.0",
        "(Apache-2.0)+",
        "LGPL-2.1++",
        "LGPL-2.1 +",
        "GPL-2.0-only WITH MIT"
      })
  void parseSpdxLicense_invalidExpressionsRejected(String expr) throws Exception {
    SpdxLicenses licenses = newLicenses();
    GradleException ex = assertThrows(GradleException.class, () -> licenses.parseSpdxLicense(expr));
    if ("apache-2.0".equals(expr)) {
      assertThat(ex.getMessage(), containsString("(did you mean 'Apache-2.0'?)"));
    }
  }

  @Test
  void resolvesPomLicensesByExpressionUrlAndStandardName() throws Exception {
    SpdxDocument doc =
        SpdxModelFactoryCompatV2.createSpdxDocumentV2(
            new InMemSpdxStore(), "https://example.com/test", new ModelCopyManager());
    SpdxLicenses licenses = newLicenses(doc);

    for (String url :
        List.of(
            "https://spdx.org/licenses/Apache-2.0",
            "https://spdx.org/licenses/Apache-2.0.html",
            "https://spdx.org/licenses/Apache-2.0.txt",
            "https://www.apache.org/licenses/LICENSE-2.0/")) {
      AnyLicenseInfo resolved = licenses.asSpdxLicense(List.of(of("Custom Name", url)));
      assertEquals("Apache-2.0", ((SpdxListedLicense) resolved).getLicenseId());
    }

    // Exact SPDX ID/expression in <name> takes precedence over <url>
    AnyLicenseInfo withException =
        licenses.asSpdxLicense(
            List.of(
                of(
                    "Apache-2.0 WITH LLVM-exception",
                    "https://www.apache.org/licenses/LICENSE-2.0.txt")));
    assertTrue(withException instanceof WithExceptionOperator);

    AnyLicenseInfo dualExpr =
        licenses.asSpdxLicense(
            List.of(of("Apache-2.0 OR MIT", "https://www.apache.org/licenses/LICENSE-2.0")));
    assertTrue(dualExpr instanceof DisjunctiveLicenseSet);

    // Standard name from licenses.json prefers active ID (GPL-2.0-only) over deprecated (GPL-2.0)
    AnyLicenseInfo gpl2Only =
        licenses.asSpdxLicense(List.of(of("GNU General Public License v2.0 only", null)));
    assertEquals("GPL-2.0-only", ((SpdxListedLicense) gpl2Only).getLicenseId());
    assertEquals(0, doc.getExtractedLicenseInfos().size());

    // Ambiguous URL across active licenses falls back to ExtractedLicenseInfo
    AnyLicenseInfo ambiguousGpl =
        licenses.asSpdxLicense(
            List.of(of("Ambiguous GPL v2 Reference", "https://opensource.org/licenses/GPL-2.0")));
    assertTrue(ambiguousGpl instanceof ExtractedLicenseInfo);
  }

  @Test
  void deduplicatesFiltersBlanksAndHandlesCustomExtractedLicenses() throws Exception {
    SpdxDocument doc =
        SpdxModelFactoryCompatV2.createSpdxDocumentV2(
            new InMemSpdxStore(), "https://example.com/test", new ModelCopyManager());
    SpdxLicenses licenses = newLicenses(doc);

    AnyLicenseInfo twoValidAndBlanks =
        licenses.asSpdxLicense(
            List.of(
                of("MIT", null),
                of("", "   "),
                of("NOASSERTION", "NOASSERTION"),
                of("Apache-2.0", null)));
    assertEquals(2, ((ConjunctiveLicenseSet) twoValidAndBlanks).getMembers().size());

    AnyLicenseInfo deduplicated =
        licenses.asSpdxLicense(
            List.of(
                of("Apache-2.0", null),
                of(
                    "The Apache License, Version 2.0",
                    "https://www.apache.org/licenses/LICENSE-2.0.txt")));
    assertEquals("Apache-2.0", ((SpdxListedLicense) deduplicated).getLicenseId());

    // Raw URL preserved on ExtractedLicenseInfo while normalized variants share the same LicenseRef
    String rawHttpsUrl = "https://acme.example.com/licenses/v1/";
    ExtractedLicenseInfo first =
        (ExtractedLicenseInfo)
            licenses.asSpdxLicense(List.of(of("Acme Proprietary License", rawHttpsUrl)));
    ExtractedLicenseInfo second =
        (ExtractedLicenseInfo)
            licenses.asSpdxLicense(
                List.of(of("Acme Proprietary License", "http://acme.example.com/licenses/v1")));
    assertEquals(first.getLicenseId(), second.getLicenseId());
    assertEquals(Set.of(rawHttpsUrl), Set.copyOf(first.getSeeAlso()));

    // Distinct name-only and URL-only unknown licenses do not collide
    ExtractedLicenseInfo nameOnlyA =
        (ExtractedLicenseInfo) licenses.asSpdxLicense(List.of(of("Custom License A", null)));
    ExtractedLicenseInfo nameOnlyB =
        (ExtractedLicenseInfo) licenses.asSpdxLicense(List.of(of("Custom License B", null)));
    assertNotEquals(nameOnlyA.getLicenseId(), nameOnlyB.getLicenseId());
    assertTrue(nameOnlyA.getSeeAlso().isEmpty());

    String urlOnlyValue = "https://custom.example.com/LICENSE";
    ExtractedLicenseInfo urlOnly =
        (ExtractedLicenseInfo) licenses.asSpdxLicense(List.of(of(null, urlOnlyValue)));
    assertEquals(urlOnlyValue, urlOnly.getExtractedText());
    assertEquals(Set.of(urlOnlyValue), Set.copyOf(urlOnly.getSeeAlso()));
    assertTrue(urlOnly.getName().isEmpty());

    assertTrue(licenses.asSpdxLicense(List.of(of(null, null))) instanceof SpdxNoAssertionLicense);
  }
}
