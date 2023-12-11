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

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class MavenPackageSupplierBuilder {
  public static String buildPackageSupplier(PomInfo pomInfo) {
    var organizationName =
        pomInfo
            .getOrganization()
            .map(o -> o.getName().trim())
            .flatMap(o -> Optional.ofNullable(o.isEmpty() ? null : o))
            .map(n -> "Organization: " + n);

    // if all the developers have the same organization, use it as the supplier
    var developersOrganization =
        pomInfo.getDevelopers().stream()
            .map(d -> d.getOrganization().orElse(null))
            .collect(uniqueOrEmpty())
            .map(o -> "Organization: " + o);

    // otherwise find the first developer that has a name, or only has an organization
    // and construct the supplier based on those properties
    var supplierFromDeveloper =
        pomInfo.getDevelopers().stream()
            .filter(
                d ->
                    d.getName().isPresent()
                        || d.getOrganization().isPresent() && d.getEmail().isEmpty())
            .findFirst()
            .flatMap(MavenPackageSupplierBuilder::findPackageSupplierFromDeveloper);

    var packageSupplier =
        Stream.of(organizationName, developersOrganization, supplierFromDeveloper)
            .filter(Optional::isPresent)
            .findFirst()
            .flatMap(v -> v);

    return packageSupplier.orElse("Organization: NOASSERTION");
  }

  private static <T> Collector<T, Set<T>, Optional<T>> uniqueOrEmpty() {
    return Collector.of(
        java.util.HashSet::new,
        Set::add,
        (left, right) -> {
          left.addAll(right);
          return left;
        },
        set -> set.size() == 1 ? Optional.ofNullable(set.iterator().next()) : Optional.empty());
  }

  private static Optional<String> findPackageSupplierFromDeveloper(PomInfo.DeveloperInfo d) {
    Optional<String> name = d.getName();
    Optional<String> email = d.getEmail();
    Optional<String> organization = d.getOrganization();

    if (name.isPresent()) {
      return email
          .map(s -> String.format("Person: %s (%s)", name.get(), s))
          .or(() -> Optional.of(String.format("Person: %s", name.get())));
    } else if (organization.isPresent()) {
      return Optional.of(String.format("Organization: %s", organization.get()));
    }

    return Optional.empty();
  }
}
