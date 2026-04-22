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
package org.spdx.sbom.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

public interface SpdxSbomExtension {

  NamedDomainObjectContainer<Target> getTargets();

  abstract class Target {
    public abstract String getName();

    public abstract ListProperty<String> getConfigurations();

    public abstract Property<Boolean> getIgnoreNonMavenDependencies();

    public abstract RegularFileProperty getOutputFile();

    @Nested
    public abstract Scm getScm();

    public void scm(Action<? super Scm> configure) {
      configure.execute(getScm());
    }

    @Nested
    public abstract Document getDocument();

    public void document(Action<? super Document> configure) {
      configure.execute(getDocument());
    }
  }

  abstract class Scm {
    public abstract Property<String> getTool();

    public abstract Property<String> getUri();

    public abstract Property<String> getRevision();
  }

  abstract class Document {
    public abstract Property<String> getNamespace();

    public abstract Property<String> getName();

    public abstract Property<String> getCreator();

    public abstract Property<String> getPackageSupplier();

    @Nested
    public abstract UberPackage getUberPackage();

    public void uberPackage(Action<? super UberPackage> configure) {
      configure.execute(getUberPackage());
    }
  }

  interface UberPackage {
    Property<String> getName();

    Property<String> getVersion();

    Property<String> getSupplier();
  }
}
