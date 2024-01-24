/*
 * Copyright 2024 The Project Authors.
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

import java.util.List;
import java.util.Properties;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;

/**
 * A filtered view of java properties that are required for building effective poms. Ensure minimal
 * passthrough of properties so we don't invalidate caches
 */
@Immutable(singleton = true, builder = false)
public abstract class PropertiesForPom {

  PropertiesForPom() {}

  private static final List<String> JAVA_PROP_KEYS = List.of("java.version");

  @Lazy
  public Properties get() {
    Properties p = new Properties();
    JAVA_PROP_KEYS.forEach(k -> p.put(k, System.getProperty(k)));
    return p;
  }

  /** Gets a singleton instance. */
  public static PropertiesForPom instance() {
    return ImmutablePropertiesForPom.of();
  }
}
