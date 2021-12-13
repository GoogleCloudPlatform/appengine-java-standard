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
 * Test the queue.xsd schema.
 *
 */
public class QueueXmlTest extends TestCase {

  private static final String SIMPLE_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>a-name</name>\n"
          + "    <rate>5/s</rate>\n"
          + "    <bucket-size>3</bucket-size>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0</rate>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>c-name</name>\n"
          + "    <rate>10/m</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String SIMPLE_XML_WITH_STORAGE_LIMIT =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <total-storage-limit>104M</total-storage-limit>\n"
          + "  <queue>\n"
          + "    <name>a-name</name>\n"
          + "    <rate>5/s</rate>\n"
          + "    <bucket-size>3</bucket-size>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0</rate>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>c-name</name>\n"
          + "    <rate>10/m</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String COMPLEX_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <total-storage-limit>106M</total-storage-limit>\n"
          + "  <queue>\n"
          + "    <name>a-name</name>\n"
          + "    <rate>5/s</rate>\n"
          + "    <bucket-size>3</bucket-size>\n"
          + "    <max-concurrent-requests>2</max-concurrent-requests>\n"
          + "    <target>module1</target>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0</rate>\n"
          + "    <retry-parameters>\n"
          + "      <task-retry-limit>100</task-retry-limit>\n"
          + "      <task-age-limit>10.e-1d</task-age-limit>\n"
          + "      <min-backoff-seconds>0.5</min-backoff-seconds>\n"
          + "      <max-backoff-seconds>900</max-backoff-seconds>\n"
          + "      <max-doublings>10</max-doublings>\n"
          + "    </retry-parameters>\n"
          + "    <target>version1.module1</target>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>b1-name</name>\n"
          + "    <rate>0</rate>\n"
          + "    <retry-parameters>\n"
          + "      <task-retry-limit>100</task-retry-limit>\n"
          + "      <task-age-limit>10e-1d</task-age-limit>\n"
          + "      <min-backoff-seconds>0.5</min-backoff-seconds>\n"
          + "      <max-backoff-seconds>900</max-backoff-seconds>\n"
          + "      <max-doublings>10</max-doublings>\n"
          + "    </retry-parameters>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>b2-name</name>\n"
          + "    <rate>0</rate>\n"
          + "    <retry-parameters>\n"
          + "      <task-retry-limit>100</task-retry-limit>\n"
          + "      <task-age-limit>1d</task-age-limit>\n"
          + "      <min-backoff-seconds>0.5</min-backoff-seconds>\n"
          + "      <max-backoff-seconds>900</max-backoff-seconds>\n"
          + "      <max-doublings>10</max-doublings>\n"
          + "    </retry-parameters>\n"
          + "    <mode>push</mode>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>c-name</name>\n"
          + "    <rate>10/m</rate>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>pull-queue</name>\n"
          + "    <mode>pull</mode>"
          + "    <acl>\n"
          + "      <user-email>admin@console.com</user-email>\n"
          + "      <writer-email>admin@console.com</writer-email>\n"
          + "      <user-email>app@hosting.com</user-email>\n"
          + "      <writer-email>app@hosting.com</writer-email>\n"
          + "    </acl>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_XML_ENTRY =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queueNOT>\n"
          + "    <name>a-name</name>\n"
          + "    <rate>5/S</rate>\n"
          + "    <bucket-size>size</bucket-size>\n"
          + "  </queueNOT>\n"
          + "</queue-entries>\n";

  private static final String BAD_XML_MODE =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>q-name</name>\n"
          + "    <mode>bad-mode</mode>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_XML_NAME =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>b_name</name>\n"
          + "    <rate>0</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_XML_RATE =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0/w</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_XML_MAX_CONCURRENT_REQUESTS =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>20/s</rate>\n"
          + "    <max-concurrent-requests>abc</max-concurrent-requests>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_XML_STORAGE_LIMIT =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <total-storage-limit>130.2MB</total-storage-limit>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0/w</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_XML_MULTIPLE_STORAGE_LIMIT =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <total-storage-limit>104M</total-storage-limit>\n"
          + "  <queue>\n"
          + "    <name>a-name</name>\n"
          + "    <rate>5/s</rate>\n"
          + "    <bucket-size>3</bucket-size>\n"
          + "  </queue>\n"
          + "  <total-storage-limit>116M</total-storage-limit>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0</rate>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>c-name</name>\n"
          + "    <rate>10/m</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_RETRY_PARAMETERS_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <total-storage-limit>106M</total-storage-limit>\n"
          + "  <queue>\n"
          + "    <name>a-name</name>\n"
          + "    <rate>5/s</rate>\n"
          + "    <bucket-size>3</bucket-size>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0</rate>\n"
          + "    <retry-parameters>\n"
          + "      <task-retry-limit>100h</task-retry-limit>\n"
          + "      <task-age-limit>1d</task-age-limit>\n"
          + "      <min-backoff-seconds>-0.5</min-backoff-seconds>\n"
          + "      <max-backoff-seconds>900</max-backoff-seconds>\n"
          + "      <max-doublings>10</max-doublings>\n"
          + "    </retry-parameters>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>c-name</name>\n"
          + "    <rate>10/m</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String MULTIPLE_RETRY_PARAMETERS_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <total-storage-limit>106M</total-storage-limit>\n"
          + "  <queue>\n"
          + "    <name>a-name</name>\n"
          + "    <rate>5/s</rate>\n"
          + "    <bucket-size>3</bucket-size>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>b-name</name>\n"
          + "    <rate>0</rate>\n"
          + "    <retry-parameters>\n"
          + "      <task-retry-limit>100</task-retry-limit>\n"
          + "      <task-age-limit>1d</task-age-limit>\n"
          + "      <min-backoff-seconds>0.5</min-backoff-seconds>\n"
          + "      <max-backoff-seconds>900</max-backoff-seconds>\n"
          + "      <max-doublings>10</max-doublings>\n"
          + "    </retry-parameters>\n"
          + "    <retry-parameters></retry-parameters>\n"
          + "  </queue>\n"
          + "  <queue>\n"
          + "    <name>c-name</name>\n"
          + "    <rate>10/m</rate>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_RETRY_PARAMETERS_IN_ACL_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>Q</name>\n"
          + "    <mode>push</mode>\n"
          + "    <acl>\n"
          + "      <user-email>q@google.com</user-email>\n"
          + "      <max-backoff-seconds>100</max-backoff-seconds>\n"
          + "    </acl>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String BAD_USER_EMAIL_IN_RETRY_PARAMETERS_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>Q</name>\n"
          + "    <mode>push</mode>\n"
          + "    <retry-parameters>\n"
          + "      <max-backoff-seconds>100</max-backoff-seconds>\n"
          + "      <user-email>q@google.com</user-email>\n"
          + "    </retry-parameters>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private static final String OK_EMPTY_ACL_XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<queue-entries>\n"
          + "  <queue>\n"
          + "    <name>Q</name>\n"
          + "    <mode>pull</mode>\n"
          + "    <acl>\n"
          + "    </acl>\n"
          + "  </queue>\n"
          + "</queue-entries>\n";

  private Validator validator;

  @Override
  public void setUp() {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

    Schema schema;
    try {
      schema =
          factory.newSchema(
              Resources.getResource("com/google/appengine/tools/development/queue.xsd"));
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
    } catch (SAXException | IOException e) {
      fail(e.getMessage());
    }
  }

  public void testGoodXmlWithStorageLimit() {
    try {
      InputStream inputStream = new StringInputStream(SIMPLE_XML_WITH_STORAGE_LIMIT);
      validator.validate(new StreamSource(inputStream));
    } catch (SAXException | IOException e) {
      fail(e.getMessage());
    }
  }

  public void testComplexXml() {
    try {
      InputStream inputStream = new StringInputStream(COMPLEX_XML);
      validator.validate(new StreamSource(inputStream));
    } catch (SAXException | IOException e) {
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
                  + "'queueNOT'. One of '{total-storage-limit, queue}' is expected.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadMode() {
    try {
      InputStream inputStream = new StringInputStream(BAD_XML_MODE);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-pattern-valid: Value 'bad-mode' is not facet-valid with respect to pattern "
                  + "'push|pull' for type 'queue-mode-Type'.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadName() {
    try {
      InputStream inputStream = new StringInputStream(BAD_XML_NAME);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-pattern-valid: Value 'b_name' is not facet-valid with respect to pattern "
                  + "'[a-zA-Z\\d\\-]{1,100}' for type 'name-Type'.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadRate() {
    try {
      InputStream inputStream = new StringInputStream(BAD_XML_RATE);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-pattern-valid: Value '0/w' is not facet-valid with respect to pattern "
                  + "'0|([0-9]+(\\.[0-9]*)?)/([smhd])' for type 'rate-Type'.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadMaxConcurrentRequests() {
    try {
      InputStream inputStream = new StringInputStream(BAD_XML_MAX_CONCURRENT_REQUESTS);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("cvc-datatype-valid.1.2.1: 'abc' is not a valid value for 'integer'.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadStorageLimit() {
    try {
      InputStream inputStream = new StringInputStream(BAD_XML_STORAGE_LIMIT);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-pattern-valid: Value '130.2MB' is not facet-valid with respect to pattern "
                  + "'([0-9]+(\\.[0-9]*)?[BKMGT]?)' for type 'total-storage-limit-Type'.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testMultipleStorageLimit() {
    try {
      InputStream inputStream = new StringInputStream(BAD_XML_MULTIPLE_STORAGE_LIMIT);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-complex-type.2.4.a: Invalid content was found starting with"
                  + " element 'total-storage-limit'. One of '{queue}' is expected.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadRetryParameters() {
    try {
      InputStream inputStream = new StringInputStream(BAD_RETRY_PARAMETERS_XML);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("cvc-datatype-valid.1.2.1: '100h' is not a valid value for 'integer'.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadRetryParametersInAclXml() {
    try {
      InputStream inputStream = new StringInputStream(BAD_RETRY_PARAMETERS_IN_ACL_XML);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-complex-type.2.4.a: Invalid content was found starting with element"
                  + " 'max-backoff-seconds'. One of '{user-email, writer-email}' is expected.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testBadUserEmailInRetryParameters() {
    try {
      InputStream inputStream = new StringInputStream(BAD_USER_EMAIL_IN_RETRY_PARAMETERS_XML);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-complex-type.2.4.a: Invalid content was found starting with element"
                  + " 'user-email'. One of '{task-retry-limit, task-age-limit, min-backoff-seconds,"
                  + " max-doublings}' is expected.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }

  public void testOkEmptyAcl() throws Exception {
    InputStream inputStream = new StringInputStream(OK_EMPTY_ACL_XML);
    validator.validate(new StreamSource(inputStream));
  }

  public void testMultipleRetryParameters() {
    try {
      InputStream inputStream = new StringInputStream(MULTIPLE_RETRY_PARAMETERS_XML);
      validator.validate(new StreamSource(inputStream));
      fail("Validator should complain.");
    } catch (SAXParseException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "cvc-complex-type.2.4.a: Invalid content was found starting "
                  + "with element 'retry-parameters'. One of '{bucket-size, "
                  + "max-concurrent-requests, target, mode, acl}' is expected.");
    } catch (IOException | SAXException e) {
      fail(e.getMessage());
    }
  }
}
