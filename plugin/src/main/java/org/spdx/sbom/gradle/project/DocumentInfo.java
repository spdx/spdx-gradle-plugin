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
import org.immutables.serial.Serial;
import org.immutables.value.Value.Immutable;
import org.spdx.sbom.gradle.SpdxSbomExtension;

@Immutable
@Serial.Version(1)
public interface DocumentInfo {
  String getNamespace();

  String getName();

  Optional<String> getCreator();

  Optional<RootPackage> getRootPackage();

  @Immutable
  @Serial.Version(1)
  interface RootPackage {
    String getName();

    String getVersion();

    String getSupplier();
  }

  static DocumentInfo from(SpdxSbomExtension.Target target) {
    var document = target.getDocument();
    var builder =
        ImmutableDocumentInfo.builder()
            .name(document.getName().get())
            .namespace(document.getNamespace().get())
            .creator(Optional.ofNullable(document.getCreator().getOrNull()));
    var rootPackage = target.getDocument().getRootPackage();
    if (!rootPackage.getName().isPresent()
        && !rootPackage.getSupplier().isPresent()
        && !rootPackage.getVersion().isPresent()) {
      return builder.build();
    } else if (rootPackage.getName().isPresent()
        && rootPackage.getSupplier().isPresent()
        && rootPackage.getVersion().isPresent()) {
      return builder
          .rootPackage(
              ImmutableRootPackage.builder()
                  .name(rootPackage.getName().get())
                  .version(rootPackage.getVersion().get())
                  .supplier(rootPackage.getSupplier().get())
                  .build())
          .build();
    } else {
      throw new GradleException(
          "Must configure all properties of rootPackage if setting rootPackage on sbom target:"
              + target.getName());
    }
  }
}
