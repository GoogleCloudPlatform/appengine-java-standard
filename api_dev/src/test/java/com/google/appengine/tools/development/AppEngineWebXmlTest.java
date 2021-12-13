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

package com.google.appengine.tools.development;

import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import junit.framework.TestCase;
import org.apache.tools.ant.filters.StringInputStream;
import org.xml.sax.SAXException;

/**
 * Test the appengine-web.xsd schema.
 *
 */
public class AppEngineWebXmlTest extends TestCase {
  private static final String SIMPLE_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
          + "  <application>socketting</application>\n"
          + "  <version>1</version>\n"
          + "  <threadsafe>true</threadsafe>\n"
          + "  <system-properties>\n"
          + "    <property name=\"java.util.logging.config.file\" value=\"logging.properties\"/>\n"
          + "  </system-properties>\n"
          + "</appengine-web-app>\n";

  private static final String URLSTREAMHANDLER_GOOD =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
          + "  <application>socketting</application>\n"
          + "  <version>1</version>\n"
          + "  <url-stream-handler>native</url-stream-handler>\n"
          + "</appengine-web-app>\n";

  private static final String URLSTREAMHANDLER_BAD =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
          + "  <application>socketting</application>\n"
          + "  <version>1</version>\n"
          + "  <url-stream-handler>invalid handler</url-stream-handler>\n"
          + "</appengine-web-app>\n";

  private Validator validator;

  @Override
  public void setUp() {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

    Schema schema;
    try {
      schema =
          factory.newSchema(
              Resources.getResource("com/google/appengine/tools/development/appengine-web.xsd"));
    } catch (SAXException e) {
      e.printStackTrace();
      fail(e.getMessage());
      return;
    }

    validator = schema.newValidator();
  }

  private void assertGoodXml(String xml) {
    try {
      InputStream inputStream = new StringInputStream(xml);
      validator.validate(new StreamSource(inputStream));
    } catch (SAXException e) {
      fail(e.getMessage());
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  private void assertBadXml(String xml) throws Exception {
    try {
      InputStream inputStream = new StringInputStream(xml);
      validator.validate(new StreamSource(inputStream));
      fail("Expected failure.");
    } catch (SAXException e) {
      // pass
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  public void testGoodXml() {
    assertGoodXml(SIMPLE_XML);
  }

  public void testUrlStreamHandlerGood() {
    assertGoodXml(URLSTREAMHANDLER_GOOD);
  }

  public void testUrlStreamHandlerBad() throws Exception {
    assertBadXml(URLSTREAMHANDLER_BAD);
  }
}
