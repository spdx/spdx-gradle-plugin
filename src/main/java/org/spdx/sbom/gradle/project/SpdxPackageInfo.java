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
import org.immutables.serial.Serial;
import org.immutables.value.Value.Immutable;
import org.spdx.sbom.gradle.SpdxPackageExtension;

@Immutable
@Serial.Version(1)
public interface SpdxPackageInfo {

  String getName();

  String getVersion();

  boolean getCreatePackage();

  Optional<ScmInfo> getScmInfo();

  Optional<String> getSupplier();

  static SpdxPackageInfo from(SpdxPackageExtension ext) {
    if (ext == null) {
      throw new NullPointerException("Extension value cannot be null");
    }
    var builder =
        ImmutableSpdxPackageInfo.builder()
            .name(ext.getName().get())
            .version(ext.getVersion().get())
            .createPackage(ext.getCreatePackage().get())
            .supplier(Optional.ofNullable(ext.getSupplier().getOrNull()));

    var scm = ext.getScm();
    if (scm.getRevision().isPresent() && scm.getTool().isPresent() && scm.getUri().isPresent()) {
      builder.scmInfo(
          ScmInfo.from(scm.getTool().get(), scm.getUri().get(), scm.getRevision().get()));
    }
    // this default should be set by the plugin, if it is missing something is wrong
    if (!scm.getTool().isPresent()) {
      throw new IllegalStateException("no default scm tool was configured by the plugin");
    }

    if ((scm.getUri().isPresent() && !scm.getRevision().isPresent())
        || (!scm.getUri().isPresent() && scm.getRevision().isPresent())) {
      throw new IllegalArgumentException(
          "uri and revision must be set if specifying scm info on spdx package extension (project: "
              + ext.getName().get()
              + ")");
    }
    return builder.build();
  }
}
