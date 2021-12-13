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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Utility functions for processing XML.
 */
public class XmlUtils {

  /* Returns the trimmed text from the passed XmlParser.Node in node or an empty
   * if the passed in node does not contain any text.
   */
  static String getText(Element node) throws AppEngineConfigException {
    String content = node.getTextContent();
    if (content == null) {
      return "";
    }
    return content.trim();
  }

  static Document parseXml(InputStream inputStream) {
    return parseXml(inputStream, null);
  }

  public static Document parseXml(InputStream inputStream, String filename) {
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(inputStream);
      doc.getDocumentElement().normalize();
      return doc;
    } catch (IOException e) {
      String msg = "Received IOException parsing the input stream" + maybeFilename(filename);
      throw new AppEngineConfigException(msg, e);
    } catch (SAXException e) {
      String msg = "Received SAXException parsing the input stream" + maybeFilename(filename);
      throw new AppEngineConfigException(msg, e);
    } catch (ParserConfigurationException e) {
      String msg = "Received ParserConfigurationException parsing the input stream"
          + maybeFilename(filename);
      throw new AppEngineConfigException(msg, e);
    }
  }

  private static String maybeFilename(String filename) {
    if (filename == null) {
      return ".";
    } else {
      return " for " + filename;
    }
  }

  /**
   * Validates a given XML document against a given schema.
   *
   * @param xmlFilename filename with XML document.
   * @param schema XSD schema to validate with.
   *
   * @throws AppEngineConfigException for malformed XML, or IO errors
   */
  public static void validateXml(String xmlFilename, File schema) {
    Path xml = Paths.get(xmlFilename);
    if (!Files.exists(xml)) {
      throw new AppEngineConfigException("Xml file: " + xml + " does not exist.");
    }
    if (!schema.exists()) {
      throw new AppEngineConfigException("Schema file: " + schema.getPath() + " does not exist.");
    }
    try {
      validateXmlContent(new String(Files.readAllBytes(xml), UTF_8), schema);
    } catch (IOException ex) {
      throw new AppEngineConfigException(
          "IO error validating " + xmlFilename + " against " + schema.getPath(), ex);
    }
  }

  /**
   * Validates a given XML document against a given schema.
   *
   * @param content a String containing the entire XML to validate.
   * @param schema XSD schema to validate with.
   *
   * @throws AppEngineConfigException for malformed XML, or IO errors
   */
  static void validateXmlContent(String content, File schema) {
    if (!schema.exists()) {
      throw new AppEngineConfigException("Schema file: " + schema.getPath() + " does not exist.");
    }
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      try {
        factory
            .newSchema(schema)
            .newValidator()
            .validate(new StreamSource(new ByteArrayInputStream(
                content.getBytes(UTF_8))));
      } catch (SAXException ex) {
        throw new AppEngineConfigException(
            "XML error validating " + content + " against " + schema.getPath(), ex);
      }
    } catch (IOException ex) {
      throw new AppEngineConfigException(
          "IO error validating " + content + " against " + schema.getPath(), ex);
    }
  }

  static String getRequiredChildElementBody(Element element, String tagName) {
    return getChildElementBody(element, tagName, true);
  }

  static String getOptionalChildElementBody(Element element, String tagName) {
    return getChildElementBody(element, tagName, false);
  }

  static String getChildElementBody(Element element, String tagName, boolean required) {
    Element elt = getChildElement(element, tagName, required);
    if (elt == null) {
      return null;
    }
    String result = getText(elt);
    return result.isEmpty() ? null : result;
  }

  static Element getOptionalChildElement(Element parent, String tagName) {
    return getChildElement(parent, tagName, false);
  }

  static Element getChildElement(Element parent, String tagName, boolean required) {
    NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes == null || nodes.getLength() == 0) {
      if (required) {
        throw new IllegalStateException(
            String.format("Missing tag %s in element %s.", tagName, parent));
      } else {
        return null;
      }
    }
    return (Element) nodes.item(0);
  }

  public static String getAttributeOrNull(Element element, String name) {
    if (!element.hasAttribute(name)) {
      return null;
    } else {
      return element.getAttribute(name);
    }
  }

  static List<Element> getChildren(Element element) {
    return getChildren(element, null);
  }

  /**
   * Returns the immediate children of the given element that have the given {@code tagName}.
   * If the {@code tagName} is null, all immediate children are returned.
   */
  public static List<Element> getChildren(Element element, String tagName) {
    NodeList nodes = element.getChildNodes();

    List<Element> elements = new ArrayList<>(nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++) {
      Node item = nodes.item(i);
      if (item instanceof Element) {
        Element itemElement = (Element) item;
        if (tagName == null || tagName.equals(itemElement.getTagName())) {
          elements.add(itemElement);
        }
      }
    }
    return elements;
  }
}
