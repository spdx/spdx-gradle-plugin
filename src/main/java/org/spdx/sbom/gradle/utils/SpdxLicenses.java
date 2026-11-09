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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.spdx.core.IModelCopyManager;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.ListedLicenses;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.ExtractedLicenseInfo;
import org.spdx.library.model.v2.license.SpdxNoAssertionLicense;
import org.spdx.sbom.gradle.maven.PomInfo.LicenseInfo;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;

public class SpdxLicenses {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private record LicenseCacheKey(@Nullable String name, @Nullable String normalizedUrl) {}

  private final Logger logger;
  private final Map<LicenseCacheKey, AnyLicenseInfo> projectLicenses = new HashMap<>();
  private final SpdxDocument doc;
  private final IModelStore modelStore;
  private final IModelCopyManager copyManager;
  private final SpdxKnownLicenses knownLicenses;

  private SpdxLicenses(
      Logger logger,
      SpdxDocument doc,
      IModelStore modelStore,
      IModelCopyManager copyManager,
      SpdxKnownLicenses knownLicenses) {
    this.logger = logger;
    this.doc = doc;
    this.modelStore = modelStore;
    this.copyManager = copyManager;
    this.knownLicenses = knownLicenses;
  }

  public static SpdxLicenses newSpdxLicenes(
      Logger logger, SpdxDocument doc, SpdxKnownLicenses spdxKnownLicenses) {
    return new SpdxLicenses(
        logger, doc, doc.getModelStore(), doc.getCopyManager(), spdxKnownLicenses);
  }

  /**
   * Validates and parses an explicit {@code license} string configured on an SBOM target, enforcing
   * exact case-sensitivity for listed IDs, {@code WITH} exceptions, operators ({@code AND}, {@code
   * OR}, {@code +}), and standalone {@code NONE}/{@code NOASSERTION}, while rejecting {@code
   * LicenseRef-*} identifiers.
   */
  public AnyLicenseInfo parseSpdxLicense(String expression) throws InvalidSPDXAnalysisException {
    String trimmed = expression.trim();
    if (!"NONE".equals(trimmed)
        && !"NOASSERTION".equals(trimmed)
        && !isValidListedExpression(trimmed)) {
      Optional<String> hint =
          ListedLicenses.getListedLicenses().listedLicenseIdCaseSensitive(trimmed);
      throw new GradleException(
          "Invalid SPDX license expression '"
              + trimmed
              + "'"
              + hint.map(id -> " (did you mean '" + id + "'?)").orElse(""));
    }
    return parseCompatV2(trimmed);
  }

  public AnyLicenseInfo asSpdxLicense(List<LicenseInfo> licenses)
      throws InvalidSPDXAnalysisException {
    Set<AnyLicenseInfo> spdxLicenses = new LinkedHashSet<>();
    for (var license : licenses) {
      AnyLicenseInfo resolved = getOrCreateLicense(license);
      if (!(resolved instanceof SpdxNoAssertionLicense)) {
        spdxLicenses.add(resolved);
      }
    }
    if (spdxLicenses.isEmpty()) {
      return new SpdxNoAssertionLicense();
    }
    if (spdxLicenses.size() == 1) {
      return spdxLicenses.iterator().next();
    }
    return doc.createConjunctiveLicenseSet(spdxLicenses);
  }

  private AnyLicenseInfo getOrCreateLicense(LicenseInfo license)
      throws InvalidSPDXAnalysisException {
    String name = license.getName();
    String rawUrl = license.getUrl();
    if (name == null && rawUrl == null) {
      logger.warn("Ignoring unusual license {}", license);
      return new SpdxNoAssertionLicense();
    }

    String normalizedUrl = rawUrl != null ? SpdxKnownLicenses.normalize(rawUrl) : null;
    LicenseCacheKey cacheKey = new LicenseCacheKey(name, normalizedUrl);
    AnyLicenseInfo cached = projectLicenses.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    AnyLicenseInfo resolved = resolveUncached(license, name, rawUrl, normalizedUrl);
    projectLicenses.put(cacheKey, resolved);
    return resolved;
  }

  /**
   * Resolves a POM license by: (1) exact SPDX ID/expression in name, (2) normalized URL, (3)
   * standard license name from licenses.json, or (4) a generated ExtractedLicenseInfo preserving
   * the raw URL.
   */
  private AnyLicenseInfo resolveUncached(
      LicenseInfo license,
      @Nullable String name,
      @Nullable String rawUrl,
      @Nullable String normalizedUrl)
      throws InvalidSPDXAnalysisException {
    if (name != null && isValidListedExpression(name)) {
      return parseCompatV2(name);
    }

    Optional<String> knownId =
        (normalizedUrl != null
                ? knownLicenses.getIdForNormalizedUrl(normalizedUrl)
                : Optional.<String>empty())
            .or(() -> name != null ? knownLicenses.getIdForName(name) : Optional.empty());
    if (knownId.isPresent()) {
      return parseCompatV2(knownId.get());
    }

    logger.debug("Non spdx-standard license detected in package: {}", license);
    return createNewUnknownLicense(name, rawUrl);
  }

  private AnyLicenseInfo parseCompatV2(String expression) throws InvalidSPDXAnalysisException {
    return LicenseInfoFactory.parseSPDXLicenseStringCompatV2(
        expression, modelStore, doc.getDocumentUri(), copyManager);
  }

  private AnyLicenseInfo createNewUnknownLicense(@Nullable String name, @Nullable String url)
      throws InvalidSPDXAnalysisException {
    var licenseId = modelStore.getNextId(IdType.LicenseRef);
    ExtractedLicenseInfo unknown =
        new ExtractedLicenseInfo(modelStore, doc.getDocumentUri(), licenseId, copyManager, true);
    if (name != null) {
      unknown.setName(name);
    }
    unknown.setExtractedText(name != null ? name : url);
    if (url != null) {
      unknown.setSeeAlso(Collections.singleton(url));
    }
    doc.addExtractedLicenseInfos(unknown);
    return unknown;
  }

  private static boolean isValidListedExpression(String expression) {
    if (expression.isBlank()) {
      return false;
    }
    ListedLicenses listed = ListedLicenses.getListedLicenses();
    boolean expectOperand = true;
    int depth = 0;
    List<String> tokens = tokenize(expression);
    for (int i = 0; i < tokens.size(); i++) {
      String token = tokens.get(i);
      if (expectOperand) {
        if ("(".equals(token)) {
          depth++;
        } else if (isExactListedLicense(token, listed)) {
          if (i + 1 < tokens.size() && "WITH".equals(tokens.get(i + 1))) {
            if (i + 2 >= tokens.size() || !isExactListedException(tokens.get(i + 2), listed)) {
              return false;
            }
            i += 2;
          }
          expectOperand = false;
        } else {
          return false;
        }
      } else if (")".equals(token)) {
        if (--depth < 0) {
          return false;
        }
      } else if ("AND".equals(token) || "OR".equals(token)) {
        expectOperand = true;
      } else {
        return false;
      }
    }
    return !expectOperand && depth == 0;
  }

  private static List<String> tokenize(String expr) {
    List<String> tokens = new ArrayList<>();
    for (String part : WHITESPACE.split(expr)) {
      int start = 0;
      while (start < part.length() && part.charAt(start) == '(') {
        tokens.add("(");
        start++;
      }
      int end = part.length();
      int trailingParens = 0;
      while (end > start && part.charAt(end - 1) == ')') {
        trailingParens++;
        end--;
      }
      if (start < end) {
        tokens.add(part.substring(start, end));
      }
      for (int i = 0; i < trailingParens; i++) {
        tokens.add(")");
      }
    }
    return tokens;
  }

  private static boolean isExactListedLicense(String token, ListedLicenses listed) {
    String id = token.endsWith("+") ? token.substring(0, token.length() - 1) : token;
    return !id.endsWith("+")
        && listed.listedLicenseIdCaseSensitive(id).filter(id::equals).isPresent();
  }

  private static boolean isExactListedException(String token, ListedLicenses listed) {
    return listed.listedExceptionIdCaseSensitive(token).filter(token::equals).isPresent();
  }
}
