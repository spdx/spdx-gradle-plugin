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

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.Checksum;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxFile;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.storage.IModelStore.IdType;

public class SpdxFileFactory {
  private final SpdxDocument doc;
  private final Path projectDir;

  public SpdxFileFactory(SpdxDocument spdxDocument, File projectDir) {
    this.doc = spdxDocument;
    this.projectDir = projectDir.toPath();
  }

  public SpdxFile newFile(Path file) throws InvalidSPDXAnalysisException, IOException {
    String sha1 =
        com.google.common.io.Files.asByteSource(file.toFile()).hash(Hashing.sha1()).toString();
    Path relativePath = projectDir.relativize(file);
    Checksum checksum = doc.createChecksum(ChecksumAlgorithm.SHA1, sha1);
    return doc.createSpdxFile(
            doc.getModelStore().getNextId(IdType.SpdxId, doc.getDocumentUri()),
            relativePath.toString(),
            new SpdxNoAssertionLicense(),
            Collections.emptyList(),
            "",
            checksum)
        .build();
  }
}
