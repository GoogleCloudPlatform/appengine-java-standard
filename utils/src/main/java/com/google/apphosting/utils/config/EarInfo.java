/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.utils.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Holder for information from an EAR directory.
 *
 */
public class EarInfo {
  private static final Logger LOGGER = Logger.getLogger(EarInfo.class.getName());

  private final File earDirectory;
  private final AppEngineApplicationXml appEngineApplicationXml;
  private final ApplicationXml applicationXml;
  private final List<WebModule> webModules;
  private final Map<String, WebModule> moduleMap;

  EarInfo(File earDirectory, AppEngineApplicationXml appEngineApplicationXml,
      ApplicationXml applicationXml, List<WebModule> webModules)
          throws AppEngineConfigException{
    this.earDirectory = earDirectory;
    this.appEngineApplicationXml = appEngineApplicationXml;
    this.applicationXml = applicationXml;
    this.webModules = ImmutableList.copyOf(webModules.iterator());

    ImmutableMap.Builder<String, WebModule> builder = ImmutableMap.builder();
    for (WebModule webModule : webModules) {
      builder.put(webModule.getModuleName(), webModule);
    }
    try {
      moduleMap = builder.build();
    } catch (IllegalArgumentException iae) {
      if (iae.getMessage().startsWith("duplicate key: ")) {
        String msg = "Invalid EAR - Duplicate module name";
        LOGGER.info(msg);
        throw new AppEngineConfigException(msg, iae);
      } else {
        throw iae;
      }
    }
  }

  public File getEarDirectory() {
    return earDirectory;
  }

  public ApplicationXml getApplicationXml() {
    return applicationXml;
  }

  public AppEngineApplicationXml getAppengineApplicationXml() {
    return appEngineApplicationXml;
  }

  public List<WebModule> getWebModules() {
    return webModules;
  }

  WebModule getWebModule(String moduleName) {
    return moduleMap.get(moduleName);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((appEngineApplicationXml == null) ? 0 : appEngineApplicationXml.hashCode());
    result = prime * result + ((applicationXml == null) ? 0 : applicationXml.hashCode());
    result = prime * result + ((earDirectory == null) ? 0 : earDirectory.hashCode());
    result = prime * result + ((webModules == null) ? 0 : webModules.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    EarInfo other = (EarInfo) obj;
    if (appEngineApplicationXml == null) {
      if (other.appEngineApplicationXml != null) {
        return false;
      }
    } else if (!appEngineApplicationXml.equals(other.appEngineApplicationXml)) {
      return false;
    }
    if (applicationXml == null) {
      if (other.applicationXml != null) {
        return false;
      }
    } else if (!applicationXml.equals(other.applicationXml)) {
      return false;
    }
    if (earDirectory == null) {
      if (other.earDirectory != null) {
        return false;
      }
    } else if (!earDirectory.equals(other.earDirectory)) {
      return false;
    }
    if (webModules == null) {
      if (other.webModules != null) {
        return false;
      }
    } else if (!webModules.equals(other.webModules)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EarInfo: earDirectory=" + earDirectory
        + " appEngineApplicationXml=" + appEngineApplicationXml
        + " applicationXml=" + applicationXml
        + " webModules=" + webModules;
  }
}
