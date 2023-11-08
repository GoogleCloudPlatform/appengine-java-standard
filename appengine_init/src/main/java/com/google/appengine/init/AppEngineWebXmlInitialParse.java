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
package com.google.appengine.init;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/** Simple quick initial parse of appengine-web.xml shared between local tooling and runtime. */
public final class AppEngineWebXmlInitialParse {

  private static final Logger logger =
      Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
  private String runtimeId = "";
  private boolean settingDoneInAppEngineWebXml = false;
  private final String file;

  private static final String PROPERTIES = "system-properties";
  private static final String PROPERTY = "property";
  private static final String RUNTIME = "runtime";

  public void handleRuntimeProperties() {
    try (final InputStream stream = new FileInputStream(file)) {
      final XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(stream);
      while (reader.hasNext()) {
        final XMLEvent event = reader.nextEvent();
        if (event.isStartElement()
            && event.asStartElement().getName().getLocalPart().equals(PROPERTIES)) {
          setAppEngineUseProperties(reader);
        } else if (event.isStartElement()
            && event.asStartElement().getName().getLocalPart().equals(RUNTIME)) {
          XMLEvent runtime = reader.nextEvent();
          if (runtime.isCharacters()) {
            runtimeId = runtime.asCharacters().getData();
          }
        }
      }
    } catch (IOException | XMLStreamException e) {
      // Not critical, we can ignore and continue.
      logger.log(Level.WARNING, "Cannot parse correctly {0}", file);
    }
    // Once runtimeId is known and we parsed all the file, correct default properties if needed,
    // and only if the setting has not been defined in appengine-web.xml.
    if (!settingDoneInAppEngineWebXml && (runtimeId != null)) {
      switch (runtimeId) {
        case "java21": // Force default to EE10.
          System.clearProperty("appengine.use.EE8");
          System.setProperty("appengine.use.EE10", "true");
          break;
        case "java11": // EE8 and EE10 not supported
        case "java8":
          System.clearProperty("appengine.use.EE8");
          System.clearProperty("appengine.use.EE10");
          break;
        default:
          break;
      }
    }
  }

  private void setAppEngineUseProperties(final XMLEventReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      final XMLEvent event = reader.nextEvent();
      if (event.isEndElement()
          && event.asEndElement().getName().getLocalPart().equals(PROPERTIES)) {
        return;
      }
      if (event.isStartElement()) {
        final StartElement element = event.asStartElement();
        final String elementName = element.getName().getLocalPart();
        if (elementName.equals(PROPERTY)) {
          String prop = element.getAttributeByName(new QName("name")).getValue();
          String value = element.getAttributeByName(new QName("value")).getValue();
          if (prop.equals("appengine.use.EE8") || prop.equals("appengine.use.EE10")) {
            // appengine.use.EE10 or appengine.use.EE8
            settingDoneInAppEngineWebXml = true;
            System.setProperty(prop, value);
          }
        }
      }
    }
  }

  public AppEngineWebXmlInitialParse(String file) {
    this.file = file;
  }
}
