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
import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * JavaBean representation of the Java backends.yaml file.
 *
 * <p>Some fields and methods in this class are accessed via reflection by yamlbeans. That's why
 * they contain underscore characters (_).
 *
 */
public class BackendsYamlReader {

  public static class BackendsYaml {

    private List<Entry> backends;

    public List<Entry> getBackends() {
      return backends;
    }

    public void setBackends(List<Entry> backends) {
      this.backends = backends;
    }

    public BackendsXml toXml() {
      BackendsXml xml = new BackendsXml();
      for (Entry backend : backends) {
        xml.addBackend(backend.toXml());
      }
      return xml;
    }

    public static class Entry {
      private String name;
      private Integer instances;
      // We will perform a substitution on the input YAML so "class:" becomes "clazz:".
      private String clazz;
      private BackendsXml.State state;
      private Integer maxConcurrentRequests;
      private Set<BackendsXml.Option> options = EnumSet.noneOf(BackendsXml.Option.class);

      public String getName() {
        return name;
      }

      public void setName(String name) {
        this.name = name;
      }

      public Integer getInstances() {
        return instances;
      }

      public void setInstances(Integer instances) {
        this.instances = instances;
      }

      public String getClazz() {
        return clazz;
      }

      public void setClazz(String clazz) {
        this.clazz = clazz;
      }

      public Integer getMax_concurrent_requests() {
        return maxConcurrentRequests;
      }

      public void setMax_concurrent_requests(Integer maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
      }

      public String getOptions() {
        List<String> optionNames = new ArrayList<String>();
        for (BackendsXml.Option option : options) {
          optionNames.add(option.getYamlValue());
        }
        return Joiner.on(", ").useForNull("null").join(optionNames);
      }

      public void setOptions(String optionString) {
        options.clear();
        for (String optionName : optionString.split(" *, *")) {
          options.add(BackendsXml.Option.fromYamlValue(optionName));
        }
      }

      public String getState() {
        return (state != null) ? state.getYamlValue() : null;
      }

      public void setState(String state) {
        this.state = (state != null) ? BackendsXml.State.fromYamlValue(state) : null;
      }

      public BackendsXml.Entry toXml() {
        return new BackendsXml.Entry(
            name,
            instances,
            clazz,
            maxConcurrentRequests,
            options,
            state);
      }
    }
  }

  private static final String FILENAME = "backends.yaml";
  private String appDir;

  public BackendsYamlReader(String appDir) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
  }

  public String getFilename() {
    return appDir + FILENAME;
  }

  public BackendsXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  public static BackendsXml parse(Reader yaml) {
    String hackedYaml;
    try {
      hackedYaml = CharStreams.toString(yaml).replaceAll("(?m)^(\\s*)class:", "$1clazz:");
    } catch (IOException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
    YamlReader reader = new YamlReader(hackedYaml);
    reader.getConfig().setPropertyElementType(BackendsYaml.class,
                                              "backends",
                                              BackendsYaml.Entry.class);

    try {
      BackendsYaml backendsYaml = reader.read(BackendsYaml.class);
      if (backendsYaml == null) {
        throw new AppEngineConfigException("Empty backends configuration.");
      }
      return backendsYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static BackendsXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
