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

import org.immutables.serial.Serial;
import org.immutables.value.Value.Derived;
import org.immutables.value.Value.Immutable;
import org.spdx.sbom.gradle.SpdxSbomExtension;

@Immutable
@Serial.Version(1)
public abstract class ScmInfo {
  public abstract String getTool();

  public abstract String getUri();

  public abstract String getRevision();

  @Derived
  public String getDownloadLocation(ProjectInfo project) {
    return getTool()
        + "+"
        + getUri()
        + "@"
        + getRevision()
        + "#"
        + project.getName()
        + "["
        + project.getPath()
        + "]";
  }

  public static ScmInfo from(SpdxSbomExtension.Target target) {
    return ImmutableScmInfo.builder()
        .tool(target.getScm().getTool().get())
        .uri(target.getScm().getUri().get())
        .revision(target.getScm().getRevision().get())
        .build();
  }

  public static ScmInfo from(String tool, String uri, String revision) {
    return ImmutableScmInfo.builder().tool(tool).uri(uri).revision(revision).build();
  }
}
