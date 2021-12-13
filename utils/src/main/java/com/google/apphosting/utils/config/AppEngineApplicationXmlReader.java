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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Constructs an {@link AppEngineApplicationXml} from an xml document
 * corresponding to appengine-application.xsd.
 *
 * <p>We use Jetty's {@link XmlParser} utility to match other Appengine XML
 * parsing code.
 *
 */
public class AppEngineApplicationXmlReader {
  /**
   * Construct an {@link AppEngineApplicationXml} from the xml document
   * within the provided {@link InputStream}.
   *
   * @param is The InputStream containing the xml we want to parse and process
   *
   * @return Object representation of the xml document
   * @throws AppEngineConfigException If the input stream cannot be parsed
   */
  public AppEngineApplicationXml processXml(InputStream is) throws AppEngineConfigException {
    AppEngineApplicationXml.Builder builder = new AppEngineApplicationXml.Builder();
    String applicationId = "";
    Element root = XmlUtils.parseXml(is).getDocumentElement();
    NodeList nodes = root.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element)) {
        continue;
      }
      Element elt = (Element) node;
      if (elt.getTagName().equals("application")) {
        applicationId = XmlUtils.getText(elt);
      } else {
        throw new AppEngineConfigException(
            "Unrecognized element <" + elt.getTagName() + "> in appengine-application.xml.");
      }
    }

    if (applicationId.isEmpty()) {
      throw new AppEngineConfigException(
          "Missing or empty <application> element in appengine-application.xml.");
    }
    return builder.setApplicationId(applicationId).build();
  }
}
