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
package org.spdx.sbom.gradle.project;

import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Provider;
import org.immutables.serial.Serial;
import org.immutables.value.Value.Immutable;
import org.spdx.sbom.gradle.SpdxSbomExtension;

@Immutable
@Serial.Version(1)
public interface DocumentInfo {
  String getNamespace();

  String getName();

  Optional<String> getCreator();

  Optional<UberPackageInfo> getUberPackageInfo();

  Optional<String> getSupplier();

  @Immutable
  @Serial.Version(1)
  interface UberPackageInfo {
    String getName();

    String getVersion();

    String getSupplier();
  }

  static DocumentInfo from(SpdxSbomExtension.Target target) {
    var document = target.getDocument();
    var builder =
        ImmutableDocumentInfo.builder()
            .name(document.getName().get().trim())
            .namespace(document.getNamespace().get().trim())
            .creator(trimmedOptional(document.getCreator()))
            .supplier(trimmedOptional(document.getPackageSupplier()));
    var uberPackage = target.getDocument().getUberPackage();
    if (!uberPackage.getName().isPresent()
        && !uberPackage.getSupplier().isPresent()
        && !uberPackage.getVersion().isPresent()) {
      return builder.build();
    } else if (uberPackage.getName().isPresent()
        && uberPackage.getSupplier().isPresent()
        && uberPackage.getVersion().isPresent()) {
      return builder
          .uberPackageInfo(
              ImmutableUberPackageInfo.builder()
                  .name(uberPackage.getName().get().trim())
                  .version(uberPackage.getVersion().get().trim())
                  .supplier(uberPackage.getSupplier().get().trim())
                  .build())
          .build();
    } else {
      throw new GradleException(
          "Must configure all properties of uberPackage if setting uberPackage on sbom target:"
              + target.getName());
    }
  }

  private static Optional<String> trimmedOptional(Provider<String> property) {
    return Optional.ofNullable(property.getOrNull()).map(String::trim).filter(s -> !s.isEmpty());
  }
}
