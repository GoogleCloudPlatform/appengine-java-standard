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

import java.io.InputStream;
import java.util.EnumSet;
import org.w3c.dom.Element;

/**
 * Creates a {@link BackendsXml} instance from
 * <appdir>WEB-INF/backends.xml.  If you want to read the
 * configuration from a different file, subclass and override
 * {@link #getFilename()}.  If you want to read the configuration from
 * something that isn't a file, subclass and override
 * {@link #getInputStream()}.
 *
 */
public class BackendsXmlReader extends AbstractConfigXmlReader<BackendsXml> {

  // Relative location of the config file
  private static final String FILENAME = "WEB-INF/backends.xml";

  /**
   * Constructs the reader for {@code backends.xml} in a given application directory.
   * @param appDir the application directory
   */
  public BackendsXmlReader(String appDir) {
    super(appDir, false);
  }

  /**
   * Parses the config file.
   * @return A {@link BackendsXml} object representing the parsed configuration.
   */
  public BackendsXml readBackendsXml() {
    return readConfigXml();
  }

  @Override
  protected BackendsXml processXml(InputStream is) {
    BackendsXml backendsXml = new BackendsXml();

    Element root = XmlUtils.parseXml(is).getDocumentElement();
    for (Element backendElement : XmlUtils.getChildren(root, "backend")) {
      backendsXml.addBackend(convertBackendNode(backendElement));
    }

    return backendsXml;
  }

  @Override
  protected String getRelativeFilename() {
    return FILENAME;
  }

  private BackendsXml.Entry convertBackendNode(Element backendElement) {
    String name = trim(backendElement.getAttribute("name"));
    Integer instances = null;
    String instanceClass = XmlUtils.getOptionalChildElementBody(backendElement, "class");
    Integer maxConcurrentRequests = null;
    EnumSet<BackendsXml.Option> options = EnumSet.noneOf(BackendsXml.Option.class);

    String instancesText = XmlUtils.getOptionalChildElementBody(backendElement, "instances");
    if (instancesText != null) {
      instances = Integer.valueOf(instancesText);
    }

    String maxConcurrentRequestsText =
        XmlUtils.getOptionalChildElementBody(backendElement, "max-concurrent-requests");
    if (maxConcurrentRequestsText != null) {
      maxConcurrentRequests = Integer.valueOf(maxConcurrentRequestsText);
    }

    Element optionsElement = XmlUtils.getOptionalChildElement(backendElement, "options");
    if (optionsElement != null) {
      if (getBooleanOption(optionsElement, "fail-fast")) {
        options.add(BackendsXml.Option.FAIL_FAST);
      }
      if (getBooleanOption(optionsElement, "dynamic")) {
        options.add(BackendsXml.Option.DYNAMIC);
      }
      if (getBooleanOption(optionsElement, "public")) {
        options.add(BackendsXml.Option.PUBLIC);
      }
    }
    return new BackendsXml.Entry(
        name, instances, instanceClass, maxConcurrentRequests, options, null);
  }

  private boolean getBooleanOption(Element optionsElement, String optionName) {
    Element optionElement = XmlUtils.getOptionalChildElement(optionsElement, optionName);
    if (optionElement == null) {
      return false;
    }
    String value = XmlUtils.getText(optionElement);
    return (value.equalsIgnoreCase("true") || value.equals("1"));
  }

  private String trim(String attribute) {
    return attribute == null ? null : attribute.trim();
  }
}
