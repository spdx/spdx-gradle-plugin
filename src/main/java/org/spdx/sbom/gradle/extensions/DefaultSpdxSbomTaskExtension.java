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
package org.spdx.sbom.gradle.extensions;

import java.net.URI;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spdx.sbom.gradle.project.ProjectInfo;
import org.spdx.sbom.gradle.project.ScmInfo;

public abstract class DefaultSpdxSbomTaskExtension implements SpdxSbomTaskExtension {
  @Override
  public URI mapRepoUri(@Nullable URI original, @NotNull ModuleVersionIdentifier moduleId) {
    return original;
  }

  @Override
  public ScmInfo mapScmForProject(@NotNull ScmInfo original, @NotNull ProjectInfo projectInfo) {
    return original;
  }

  @Override
  public boolean shouldCreatePackageForProject(@NotNull ProjectInfo projectInfo) {
    return true;
  }
}
