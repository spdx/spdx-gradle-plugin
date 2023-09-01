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
package org.spdx.sbom.gradle.maven;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Organization;
import org.immutables.serial.Serial;
import org.immutables.value.Value.Immutable;

@Immutable
@Serial.Version(1)
public interface PomInfo {
  List<LicenseInfo> getLicenses();

  URI getHomepage();

  Optional<Organization> getOrganization();

  List<DeveloperInfo> getDevelopers();

  @Immutable
  interface LicenseInfo {
    String getUrl();

    String getName();
  }

  @Immutable
  interface DeveloperInfo {
    Optional<String> getName();

    Optional<String> getEmail();

    Optional<String> getOrganization();
  }
}
