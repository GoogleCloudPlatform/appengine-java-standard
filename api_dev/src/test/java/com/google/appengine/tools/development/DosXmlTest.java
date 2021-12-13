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

import static com.google.common.truth.Truth.assertThat;

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
import org.xml.sax.SAXParseException;

/**
 * Test the dos.xsd schema.
 *
 */
public class DosXmlTest extends TestCase {

  private static final String SIMPLE_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<blacklistentries>\n"
          + "  <blacklist>\n"
          + "    <subnet>192.168.1.1/24</subnet>\n"
          + "    <description>Foo</description>\n"
          + "  </blacklist>\n"
          + "  <blacklist>\n"
          + "    <subnet>192.168.1.2</subnet>\n"
          + "    <description>Foo</description>\n"
          + "  </blacklist>\n"
          + "  <blacklist>\n"
          + "    <subnet>::FFFF/120</subnet>\n"
          + "  </blacklist>\n"
          + "</blacklistentries>\n";

  private static final String BAD_XML_ENTRY =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<blacklistentries>\n"
          + "  <blacklistNOT>\n"
          + "    <subnet>192.168.1.1/24</subnet>\n"
          + "    <description>Foo</description>\n"
          + "  </blacklistNOT>\n"
          + "</blacklistentries>\n";

  private static final String MISSING_SUBNET_XML_ENTRY =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<blacklistentries>\n"
          + "  <blacklist>\n"
          + "    <description>Foo</description>\n"
          + "  </blacklist>\n"
          + "</blacklistentries>\n";

  private Validator validator;

  @Override
  public void setUp() {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

    Schema schema;
    try {
      schema =
          factory.newSchema(
              Resources.getResource("com/google/appengine/tools/development/dos.xsd"));
    } catch (SAXException e) {
      e.printStackTrace();
      fail(e.getMessage());
      return;
    }

    validator = schema.newValidator();
  }

  public void testGoodXml() {
    try {
      InputStream inputStream = new StringInputStream(SIMPLE_XML);
      validator.validate(new StreamSource(inputStream));
    } catch (SAXException e) {
      fail(e.getMessage());
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  public void testBadXmlEntry() {
    try {
      InputStream inputStream = new StringInputStream(BAD_XML_ENTRY);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-complex-type.2.4.a: Invalid content was found starting with element "
                  + "'blacklistNOT'. One of '{blacklist}' is expected.");
    } catch (IOException e) {
      fail(e.getMessage());
    } catch (SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testMissingSubnetXmlEntry() {
    try {
      InputStream inputStream = new StringInputStream(MISSING_SUBNET_XML_ENTRY);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-complex-type.2.4.b: The content of element 'blacklist' is not complete. "
                  + "One of '{subnet}' is expected.");
    } catch (IOException e) {
      fail(e.getMessage());
    } catch (SAXException e) {
      fail(e.getMessage());
    }
  }
}
