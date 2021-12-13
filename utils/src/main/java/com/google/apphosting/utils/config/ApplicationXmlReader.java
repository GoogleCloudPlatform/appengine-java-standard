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
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Constructs an {@link ApplicationXml} from an xml document
 * corresponding to http://java.sun.com/xml/ns/javaee/application_5.xsd.
 *
 *
 */
public class ApplicationXmlReader {
  /**
   * Construct an {@link ApplicationXml} from the xml document
   * within the provided {@link InputStream}.
   *
   * @param is The InputStream containing the xml we want to parse and process.
   *
   * @return Object representation of the xml document.
   * @throws AppEngineConfigException If the input stream cannot be parsed.
   */
  public ApplicationXml processXml(InputStream is) {
    ApplicationXml.Builder builder = ApplicationXml.builder();
    HashSet<String> contextRoots = new HashSet<String>();

    Element root = XmlUtils.parseXml(is).getDocumentElement();
    NodeList nodes = root.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element)) {
        continue;
      }

      Element elt = (Element) node;
      switch (elt.getTagName()) {
        case "icon":
        case "display-name":
        case "description":
        case "module":
          handleModuleNode(elt, builder.getModulesBuilder(), contextRoots);
          break;
        case "security-role":
        case "library-directory":
          break;
        default:
          reportUnrecognizedTag(elt.getTagName());
      }
    }
    return builder.build();
  }

  private void handleModuleNode(
      Element root, ApplicationXml.Modules.Builder builder, Set<String> contextRoots) {

    NodeList nodes = root.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element)) {
        continue;
      }
      Element elt = (Element) node;
      switch (elt.getTagName()) {
        case "alt-dd":
        case "connector":
        case "ejb":
        case "java":
          break;
        case "web":
          handleWebNode(elt, builder, contextRoots);
          break;
        default:
          reportUnrecognizedTag(elt.getTagName());
      }
    }
  }

  private void handleWebNode(
      Element root, ApplicationXml.Modules.Builder builder, Set<String> contextRoots) {
    String contextRoot = null;
    String webUri = null;
    String what = "application.xml <" + root.getTagName() + "> element";

    NodeList nodes = root.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element)) {
        continue;
      }
      Element elt = (Element) node;
      if ("web-uri".equals(elt.getTagName())) {
        if (webUri != null) {
          throw new AppEngineConfigException(
              "web-uri multiply defined in application.xml web module.");
        }
        webUri = XmlUtils.getText(elt);
        if (webUri.isEmpty()) {
          throw new AppEngineConfigException(
              "web-uri is empty in application.xml web module.");
        }
      } else if ("context-root".equals(elt.getTagName())) {
        if (contextRoot != null) {
          throw new AppEngineConfigException(
              "context-root multiply defined in application.xml web module.");
        }
        contextRoot = XmlUtils.getText(elt);
        if (contextRoot.isEmpty()) {
          throw new AppEngineConfigException(
              "context-root is empty in application.xml web module.");
        }
        if (contextRoots.contains(contextRoot)) {
          throw new AppEngineConfigException(
              "context-root value '" + contextRoot + "' is not unique.");
        }
        contextRoots.add(contextRoot);
      } else {
        reportUnrecognizedTag(elt.getTagName());
      }
    }

    if (null == webUri) {
      throw new AppEngineConfigException(
          "web-uri not defined in application.xml web module.");
    }
    if (null == contextRoot) {
      throw new AppEngineConfigException(
          "context-root not defined in application.xml web module.");
    }
    builder.addWeb(new ApplicationXml.Modules.Web(webUri, contextRoot));
  }

  private void reportUnrecognizedTag(String tag) throws AppEngineConfigException {
    throw new AppEngineConfigException("Unrecognized element <" + tag
        + "> in application.xml.");
  }
}
