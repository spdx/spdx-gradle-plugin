/*
 * Copyright 2025 The Project Authors.
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
import org.immutables.value.Value;

@Value.Immutable
@Serial.Version(1)
public interface IsolatedProjectInfo {
  String getPath();

  // is required, but only available via user injection when in project isolation mode
  String getVersion();

  // optionally, but only available via user injection when in project isolation mode
  Optional<String> getGroup();

  Optional<String> getDescription();

  static IsolatedProjectInfo of(String path, String version, String group, String description) {
    return ImmutableIsolatedProjectInfo.builder()
        .path(path)
        .version(version)
        .group(group)
        .description(description)
        .build();
  }

  static IsolatedProjectInfo of(String path, String version) {
    return ImmutableIsolatedProjectInfo.builder().path(path).version(version).build();
  }
}
