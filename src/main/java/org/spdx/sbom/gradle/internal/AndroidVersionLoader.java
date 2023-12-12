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
package org.spdx.sbom.gradle.internal;

import java.lang.reflect.Method;
import org.gradle.api.Project;

public class AndroidVersionLoader {
  public String getApplicationVersion(Project project) {
    Object android = project.getExtensions().findByName("android");
    if (android != null) {
      try {
        Method defaultConfig = android.getClass().getMethod("getDefaultConfig");
        Object configInstance = defaultConfig.invoke(android);
        Method getVersionName = configInstance.getClass().getMethod("getVersionName");
        return (String) getVersionName.invoke(configInstance);
      } catch (Throwable t) {
        new Object();
      }
    }

    return null;
  }
}
