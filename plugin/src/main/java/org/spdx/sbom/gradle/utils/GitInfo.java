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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.process.internal.ExecException;
import org.immutables.value.Value.Derived;
import org.immutables.value.Value.Immutable;

@Immutable
public interface GitInfo {

  Optional<String> getCommitHash();

  Optional<String> getOrigin();

  Optional<String> getTag();

  @Derived
  default String asSourceInfo() {
    return
        getOrigin().map(String::trim).orElse("<no-origin>")
            + ":"
            + getCommitHash().map(String::trim).orElse("<no-commit>")
            + ":"
            + getTag().map(String::trim).orElse("<no-tag>");
  }

  static GitInfo extractGitInfo(Project project) {
    Optional<String> commitHash = exec(project, "rev-parse", "HEAD");
    Optional<String> origin = exec(project, "config", "--get", "remote.origin.url");
    Optional<String> tag = exec(project, "describe", "--tags", "--always", "--dirty");

    return ImmutableGitInfo.builder().tag(tag).commitHash(commitHash).origin(origin).build();
  }

  private static Optional<String> exec(Project project, String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      project.exec(
          execSpec -> {
            execSpec.executable("git");
            execSpec.args(Arrays.stream(args).toArray());
            execSpec.setStandardOutput(out);
            execSpec.setErrorOutput(err);
          });
    } catch (ExecException e) {
      project.getLogger().warn(e.getMessage() + " " + Arrays.asList(args));
      return Optional.empty();
    }
    return Optional.of(out.toString(StandardCharsets.UTF_8));
  }
}
