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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Class to parse dos.yaml into a DosXml object.
 *
 */
public class DosYamlReader {

  /**
   * Wrapper around DosXml to make the JavaBeans properties match the YAML file syntax.
   */
  public static class DosYaml {
    private List<DosXml.BlacklistEntry> entries;

    public List<DosXml.BlacklistEntry> getBlacklist() {
      return entries;
    }

    public void setBlacklist(List<DosXml.BlacklistEntry> entries) {
      this.entries = entries;
    }

    public DosXml toXml() {
      DosXml xml = new DosXml();
      if (entries != null) {
        for (DosXml.BlacklistEntry entry : entries) {
          xml.addBlacklistEntry(entry);
        }
      }
      return xml;
    }
  }

  private static final String FILENAME = "dos.yaml";
  private String appDir;

  public DosYamlReader(String appDir) {
    if (appDir.length() > 0 && appDir.charAt(appDir.length() - 1) != File.separatorChar) {
      appDir += File.separatorChar;
    }
    this.appDir = appDir;
  }

  public String getFilename() {
    return appDir + DosYamlReader.FILENAME;
  }

  public DosXml parse() {
    if (new File(getFilename()).exists()) {
      try {
        return parse(new FileReader(getFilename()));
      } catch (FileNotFoundException ex) {
        throw new AppEngineConfigException("Cannot find file " + getFilename(), ex);
      }
    }
    return null;
  }

  public static DosXml parse(Reader yaml) {
    try {
      DosYaml dosYaml = YamlUtils.parse(yaml, DosYaml.class);
      if (dosYaml == null) {
        throw new AppEngineConfigException("Empty dos configuration.");
      }
      return dosYaml.toXml();
    } catch (YamlException ex) {
      throw new AppEngineConfigException(ex.getMessage(), ex);
    }
  }

  public static DosXml parse(String yaml) {
    return parse(new StringReader(yaml));
  }
}
