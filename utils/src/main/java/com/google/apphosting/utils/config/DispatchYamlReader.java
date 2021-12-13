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

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.apphosting.utils.config.DispatchXml.DispatchEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;

/**
 * Class to parse dispatch.yaml into a {@link DispatchXml}.
 *
 */
public class DispatchYamlReader {
  private static final String DISPATCH_FILENAME = "dispatch.yaml";
  private final String parentDirectory;

  /**
   * Constructs a {@link DispatchYamlReader}.
   * @param parentDirectory the directory containing the dispatch.yaml file.
   */
  public DispatchYamlReader(String parentDirectory) {
    if (parentDirectory.length() > 0
        && parentDirectory.charAt(parentDirectory.length() - 1) != File.separatorChar) {
      parentDirectory += File.separatorChar;
    }
    this.parentDirectory = parentDirectory;
  }

  public String getFilename() {
    return parentDirectory + DISPATCH_FILENAME;
  }

  public DispatchXml parse() {
    DispatchXml result = null;
    try {
      return parseImpl(new FileReader(getFilename()));
    } catch (FileNotFoundException ex) {
      // By convention we return null if dispatch.yaml does not exist.
    }
    return null;
  }

  @VisibleForTesting
  static DispatchXml parseImpl(Reader yaml) {
    YamlReader reader = new YamlReader(yaml);
    reader.getConfig().setPropertyElementType(DispatchYaml.class, "dispatch",
        DispatchYamlEntry.class);
    try {
      DispatchYaml dispatchYaml = reader.read(DispatchYaml.class);
      if (dispatchYaml == null) {
        throw new AppEngineConfigException("Empty dispatch.yaml configuration.");
      }
      return dispatchYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  /**
   * Top level bean for a parsed dispatch.yaml file that meets the
   * requirements of {@link YamlReader}.
   */
  public static class DispatchYaml {
    private List<DispatchYamlEntry> dispatchEntries;

    public List<DispatchYamlEntry> getDispatch() {
      return dispatchEntries;
    }

    public void setDispatch(List<DispatchYamlEntry> entries) {
      this.dispatchEntries = entries;
    }

    public DispatchXml toXml() {
      DispatchXml.Builder builder = DispatchXml.builder();
      if (dispatchEntries != null) {
        for (DispatchYamlEntry entry : dispatchEntries) {
          builder.addDispatchEntry(entry.asDispatchEntry());
        }
      }
      return builder.build();
    }
  }

  /**
   * Bean for a parsed single uri to module mapping entry in a
   * dispatch.yaml file that meets the requirements of
   * {@link YamlReader}.
   */
  public static class DispatchYamlEntry {
    private String url;
    private String module;
    private String service;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    @Deprecated // Prefer getService when possible.
    public String getModule() {
      return module;
    }

    @Deprecated // Prefer setService when possible.
    public void setModule(String module) {
      this.module = module;
    }

    public String getService() {
      return service;
    }

    public void setService(String service) {
      this.service = service;
    }

    DispatchEntry asDispatchEntry() {
      Preconditions.checkState(
          service != null ^ module != null,
          "Only one of service: or module: should be set in a dispatch entry.");
      return new DispatchEntry(url, module != null ? module : service);
    }
  }
}
