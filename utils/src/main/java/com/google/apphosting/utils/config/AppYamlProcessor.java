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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * A utility to convert an app.yaml file into a web.xml and an appengine-web.xml file
 *
 */
public final class AppYamlProcessor {


  /**
   * Reads the app.yaml file from the given directory and generates
   * a web.xml and an appengine-web.xml file in the same directory. Usually this directory
   * will be the WEB-INF directory of a WAR directory. If the directory
   * does not contain an app.yaml file, does nothing.
   * @param baseDir A directory possibly containing an app.yaml file
   * @param aewebPath The path to the appengine-web.xml file to generate.
   * @param webXmlPath The path to the web.xml file to generate.
   */
  public static void convert(File baseDir, String aewebPath, String webXmlPath) {
    File appYamlFile = new File(baseDir, "app.yaml");
    if (!appYamlFile.exists()) {
      return;
    }
    File aeweb = new File(aewebPath);
    File webXml = new File(webXmlPath);
    if (aeweb.exists() && webXml.exists() &&
        aeweb.lastModified() >= appYamlFile.lastModified() &&
        webXml.lastModified() >= appYamlFile.lastModified()) {
      return;
    }
    AppYaml appYaml;
    try {
      appYaml = AppYaml.parse(new FileReader(appYamlFile));
    } catch (FileNotFoundException ex) {
      throw new AppEngineConfigException("Unable to parse " + appYamlFile, ex);
    }


    try {
      FileWriter aewebWriter = new FileWriter(aeweb);
      appYaml.generateAppEngineWebXml(aewebWriter);
      aewebWriter.close();
      aeweb.setLastModified(appYamlFile.lastModified());
    } catch (IOException ex) {
      throw new AppEngineConfigException("Unable to generate " + aeweb, ex);
    }
    try {
      FileWriter webXmlWriter = new FileWriter(webXml);
      appYaml.generateWebXml(webXmlWriter);
      webXmlWriter.close();
      webXml.setLastModified(appYamlFile.lastModified());
    } catch (IOException ex) {
      throw new AppEngineConfigException("Unable to generate " + webXml, ex);
    }
  }

}
