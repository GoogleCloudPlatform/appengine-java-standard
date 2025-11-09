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

import static com.google.common.truth.Truth.assertWithMessage;

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

/** Test the {@code dispatch.xsd} schema. */
public class DispatchXsdTest extends TestCase {

  public void testValidate_noEntry() {
    String dispatchNoEntries =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "</dispatch-entries>\n";
    validate(dispatchNoEntries);
  }

  public void testValidate_oneEntry() {
    String dispatchOneEntry =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <module>M</module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    validate(dispatchOneEntry);
  }

  public void testValidate_twowoEntries() {
    String dispatchTwoEntries =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <module>M</module>\n"
            + "  </dispatch>\n"
            + "  <dispatch>\n"
            + "    <url>p2/m2</url>\n"
            + "    <module>M2</module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    validate(dispatchTwoEntries);
  }

  public void testValidate_malformed() {
    String malformed =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <module>M</module>\n"
            + "</dispatch-entries>\n";
    String expectedMessage =
        "The end-tag for element type \"dispatch\" must end with a '>' delimiter.";
    validate(malformed, expectedMessage);
  }

  public void testValidate_badRootElement() {
    String badRoot =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<NOT-dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <module>M</module>\n"
            + "  </dispatch>\n"
            + "</NOT-dispatch-entries>\n";
    String expectedMessage = "Cannot find the declaration of element 'NOT-dispatch-entries'.";
    validate(badRoot, expectedMessage);
  }

  public void testValidate_badDispatchElement() {
    String badDispatch =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <NOT-dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <module>M</module>\n"
            + "  </NOT-dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage = "Invalid content was found starting with element 'NOT-dispatch'.";
    validate(badDispatch, expectedMessage);
  }

  public void testValidate_notModuleElement() {
    String notModule =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <NOT-module>M</NOT-module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage = "Invalid content was found starting with element 'NOT-module'.";
    validate(notModule, expectedMessage);
  }

  public void testValidate_elementInsideModule() {
    String extraInModule =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <module><extra/></module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage =
        "Element 'module' is a simple type, so it must have no element "
            + "information item [children].";
    validate(extraInModule, expectedMessage);
  }

  public void testValidate_missingModuleElement() {
    String missingModule =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage =
        "The content of element 'dispatch' is not complete. One of '{module}' is expected.";
    validate(missingModule, expectedMessage);
  }

  public void testValidate_extraModuleElement() {
    String extraModule =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <module>M</module>\n"
            + "    <module>M2</module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage = "Invalid content was found starting with element 'module'.";
    validate(extraModule, expectedMessage);
  }

  public void testValidate_missingUrlElement() {
    String missingUrl =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <module>M</module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage =
        "The content of element 'dispatch' is not complete. One of '{url}' is expected.";
    validate(missingUrl, expectedMessage);
  }

  public void testValidate_notUrlElement() {
    String notModule =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <NOT-url>p/m</NOTurl>\n"
            + "    <module>M</module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage = "Invalid content was found starting with element 'NOT-url'.";
    validate(notModule, expectedMessage);
  }

  public void testValidate_extraUrlElement() {
    String extraUrl =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url>p/m</url>\n"
            + "    <url>p/m</url>\n"
            + "    <module>M</module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage = "Invalid content was found starting with element 'url'.";
    validate(extraUrl, expectedMessage);
  }

  public void testValidate_elementInsideUrl() {
    String extraInUrl =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<dispatch-entries>\n"
            + "  <dispatch>\n"
            + "    <url><extra/></url>\n"
            + "    <module>m</module>\n"
            + "  </dispatch>\n"
            + "</dispatch-entries>\n";
    String expectedMessage =
        "Element 'url' is a simple type, so it must have no element "
            + "information item [children].";
    validate(extraInUrl, expectedMessage);
  }

  private void validate(String xml) {
    validate(xml, null);
  }

  private Validator getValidator() {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try {
      Schema schema =
          factory.newSchema(
              Resources.getResource("com/google/appengine/tools/development/dispatch.xsd"));
      return schema.newValidator();
    } catch (SAXException e) {
      e.printStackTrace();
      fail(e.getMessage());
      // unreachable
      return null;
    }
  }

  private void validate(String xml, String expectedSaxParserExceptionMessage) {
    try {
      InputStream inputStream = new StringInputStream(xml);
      getValidator().validate(new StreamSource(inputStream));
      if (expectedSaxParserExceptionMessage != null) {
        fail("Validator should complain.");
      }
    } catch (SAXParseException e) {
      if (expectedSaxParserExceptionMessage != null) {
        assertWithMessage(
                "Message [%s] must contain [%s]", e.getMessage(), expectedSaxParserExceptionMessage)
            .that(e.getMessage().contains(expectedSaxParserExceptionMessage))
            .isTrue();
      } else {
        fail(e.getMessage());
      }
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }
}
