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

import java.io.IOException;
import javax.inject.Inject;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.spdx.library.ListedLicenses;

/** A shared service for loading remote spdx license list. */
public abstract class SpdxKnownLicensesService
    implements BuildService<SpdxKnownLicensesService.Params> {

  public interface Params extends BuildServiceParameters {
    Property<Boolean> getOnlyUseLocalLicenses();
  }

  private final SpdxKnownLicenses spdxKnownLicenses;

  @Inject
  public SpdxKnownLicensesService() throws IOException {
    boolean offline = getParameters().getOnlyUseLocalLicenses().getOrElse(false);
    System.setProperty("org.spdx.useJARLicenseInfoOnly", String.valueOf(offline));
    ListedLicenses.getListedLicenses();
    this.spdxKnownLicenses = SpdxKnownLicenses.knownLicenses(offline);
  }

  public SpdxKnownLicenses getKnownLicenses() {
    return spdxKnownLicenses;
  }
}
