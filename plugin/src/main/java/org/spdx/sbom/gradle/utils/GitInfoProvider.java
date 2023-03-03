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
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecOperations;
import org.gradle.process.internal.ExecException;

public class GitInfoProvider {

  private final ExecOperations execOperations;
  private final Logger logger;

  public GitInfoProvider(ExecOperations execOperations, Logger logger) {
    this.execOperations = execOperations;
    this.logger = logger;
  }

  public GitInfo getGitInfo() {
    Optional<String> commitHash = exec("rev-parse", "HEAD");
    Optional<String> origin = exec("config", "--get", "remote.origin.url");
    Optional<String> tag = exec("describe", "--tags", "--always", "--dirty");

    return ImmutableGitInfo.builder().tag(tag).commitHash(commitHash).origin(origin).build();
  }

  private Optional<String> exec(String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      execOperations.exec(
          execSpec -> {
            execSpec.executable("git");
            execSpec.args(Arrays.stream(args).toArray());
            execSpec.setStandardOutput(out);
            execSpec.setErrorOutput(err);
          });
    } catch (ExecException e) {
      logger.warn(e.getMessage() + " " + Arrays.asList(args));
      return Optional.empty();
    }
    return Optional.of(out.toString(StandardCharsets.UTF_8));
  }
}
