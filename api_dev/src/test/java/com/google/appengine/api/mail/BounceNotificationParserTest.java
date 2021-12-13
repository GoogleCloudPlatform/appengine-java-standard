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

package com.google.appengine.api.mail;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.tools.development.testing.FakeHttpServletRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the BounceNotificationParser class.
 *
 */
@RunWith(JUnit4.class)
public class BounceNotificationParserTest {
  @Test
  public void testNormal() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"boundary\"");
    req.setPostData(
        "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-to\r\n"
            + "\r\n"
            + "destination@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-cc\r\n"
            + "\r\n"
            + "cc@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-bcc\r\n"
            + "\r\n"
            + "bcc@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-from\r\n"
            + "\r\n"
            + "source@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-subject\r\n"
            + "\r\n"
            + "Tests Summary\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-text\r\n"
            + "\r\n"
            + "Body Goes Here\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-to\r\n"
            + "\r\n"
            + "foo@bar.bounces.google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-from\r\n"
            + "\r\n"
            + "destination@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-subject\r\n"
            + "\r\n"
            + "Bounce\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-text\r\n"
            + "\r\n"
            + "Description\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=raw-message\r\n"
            + "\r\n"
            + "Date: Wed, 29 Mar 2006 13:28:22 -0800\r\n"
            + "Message-Id: <200603292128.k2TLSMbs026326@google.com>\r\n"
            + "From: \"Unit Test\" <source@google.com>\r\n"
            + "To: destination@google.com\r\n"
            + "Subject: Tests Summary\r\n"
            + "X-Google-Appengine-App-Id: s~app-name\r\n"
            + "X-Google-Appengine-App-Id-Alias: app-name\r\n"
            + "\r\n"
            + "Body Goes Here\r\n"
            + "\r\n"
            + "--boundary--\r\n",
        "US-ASCII");

    BounceNotification notification = BounceNotificationParser.parse(req);

    assertThat(notification.getOriginal().getFrom()).isEqualTo("source@google.com");
    assertThat(notification.getOriginal().getTo()).isEqualTo("destination@google.com");
    assertThat(notification.getOriginal().getCc()).isEqualTo("cc@google.com");
    assertThat(notification.getOriginal().getBcc()).isEqualTo("bcc@google.com");
    assertThat(notification.getOriginal().getSubject()).isEqualTo("Tests Summary");
    assertThat(notification.getOriginal().getText()).isEqualTo("Body Goes Here");

    assertThat(notification.getNotification().getFrom()).isEqualTo("destination@google.com");
    assertThat(notification.getNotification().getTo()).isEqualTo("foo@bar.bounces.google.com");
    assertThat(notification.getNotification().getSubject()).isEqualTo("Bounce");
    assertThat(notification.getNotification().getText()).isEqualTo("Description");
  }

  /**
   * Make sure that parsing succeeds, and all expected fields are there when fields are missing.
   *
   * <p>The reason for this test is that we may add a new field to the set of bounce fields in the
   * future. If the API code is used in a future version while older code is running in the PFE to
   * make the post, it could be missing expected fields and we should be resilient to that.
   */
  @Test
  public void testMissingFields() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"boundary\"");
    req.setPostData(
        "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-subject\r\n"
            + "\r\n"
            + "Tests Summary\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-text\r\n"
            + "\r\n"
            + "Body Goes Here\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-to\r\n"
            + "\r\n"
            + "foo@bar.bounces.google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-from\r\n"
            + "\r\n"
            + "destination@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-subject\r\n"
            + "\r\n"
            + "Bounce\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-text\r\n"
            + "\r\n"
            + "Description\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=raw-message\r\n"
            + "\r\n"
            + "Date: Wed, 29 Mar 2006 13:28:22 -0800\r\n"
            + "Message-Id: <200603292128.k2TLSMbs026326@google.com>\r\n"
            + "From: \"Unit Test\" <source@google.com>\r\n"
            + "To: destination@google.com\r\n"
            + "Subject: Tests Summary\r\n"
            + "X-Google-Appengine-App-Id: s~app-name\r\n"
            + "X-Google-Appengine-App-Id-Alias: app-name\r\n"
            + "\r\n"
            + "Body Goes Here\r\n"
            + "\r\n"
            + "--boundary--\r\n",
        "US-ASCII");

    BounceNotification notification = BounceNotificationParser.parse(req);

    assertThat(notification.getOriginal().getFrom()).isEqualTo(null);
    assertThat(notification.getOriginal().getTo()).isEqualTo(null);
    assertThat(notification.getOriginal().getCc()).isEqualTo(null);
    assertThat(notification.getOriginal().getBcc()).isEqualTo(null);
    assertThat(notification.getOriginal().getSubject()).isEqualTo("Tests Summary");
    assertThat(notification.getOriginal().getText()).isEqualTo("Body Goes Here");

    assertThat(notification.getNotification().getFrom()).isEqualTo("destination@google.com");
    assertThat(notification.getNotification().getTo()).isEqualTo("foo@bar.bounces.google.com");
    assertThat(notification.getNotification().getSubject()).isEqualTo("Bounce");
    assertThat(notification.getNotification().getText()).isEqualTo("Description");
  }

  /**
   * Make sure that parsing succeeds, and all expected fields are there, when we add some extra
   * fields to the payload.
   *
   * <p>The reason for this test is that we may add a new field to the set of bounce fields in the
   * future and we want existing code to be resilient to that.
   */
  @Test
  public void testExtraFields() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"boundary\"");
    req.setPostData(
        "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=new-field\r\n"
            + "\r\n"
            + "destination@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-to\r\n"
            + "\r\n"
            + "destination@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-from\r\n"
            + "\r\n"
            + "source@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-subject\r\n"
            + "\r\n"
            + "Tests Summary\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=original-text\r\n"
            + "\r\n"
            + "Body Goes Here\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-to\r\n"
            + "\r\n"
            + "foo@bar.bounces.google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-from\r\n"
            + "\r\n"
            + "destination@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-subject\r\n"
            + "\r\n"
            + "Bounce\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-text\r\n"
            + "\r\n"
            + "Description\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=raw-message\r\n"
            + "\r\n"
            + "Date: Wed, 29 Mar 2006 13:28:22 -0800\r\n"
            + "Message-Id: <200603292128.k2TLSMbs026326@google.com>\r\n"
            + "From: \"Unit Test\" <source@google.com>\r\n"
            + "To: destination@google.com\r\n"
            + "Subject: Tests Summary\r\n"
            + "X-Google-Appengine-App-Id: s~app-name\r\n"
            + "X-Google-Appengine-App-Id-Alias: app-name\r\n"
            + "\r\n"
            + "Body Goes Here\r\n"
            + "\r\n"
            + "--boundary--\r\n",
        "US-ASCII");

    BounceNotification notification = BounceNotificationParser.parse(req);

    assertThat(notification.getOriginal().getFrom()).isEqualTo("source@google.com");
    assertThat(notification.getOriginal().getTo()).isEqualTo("destination@google.com");
    assertThat(notification.getOriginal().getSubject()).isEqualTo("Tests Summary");
    assertThat(notification.getOriginal().getText()).isEqualTo("Body Goes Here");

    assertThat(notification.getNotification().getFrom()).isEqualTo("destination@google.com");
    assertThat(notification.getNotification().getTo()).isEqualTo("foo@bar.bounces.google.com");
    assertThat(notification.getNotification().getSubject()).isEqualTo("Bounce");
    assertThat(notification.getNotification().getText()).isEqualTo("Description");
  }

  @Test
  public void testNoOriginal() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setContentType("multipart/form-data; boundary=\"boundary\"");
    req.setPostData(
        "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-to\r\n"
            + "\r\n"
            + "foo@bar.bounces.google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-from\r\n"
            + "\r\n"
            + "destination@google.com\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-subject\r\n"
            + "\r\n"
            + "Bounce\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=notification-text\r\n"
            + "\r\n"
            + "Description\r\n"
            + "--boundary\r\n"
            + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
            + "Content-Disposition: form-data; name=raw-message\r\n"
            + "\r\n"
            + "Date: Wed, 29 Mar 2006 13:28:22 -0800\r\n"
            + "Message-Id: <200603292128.k2TLSMbs026326@google.com>\r\n"
            + "From: \"Unit Test\" <source@google.com>\r\n"
            + "To: destination@google.com\r\n"
            + "Subject: Tests Summary\r\n"
            + "X-Google-Appengine-App-Id: s~app-name\r\n"
            + "X-Google-Appengine-App-Id-Alias: app-name\r\n"
            + "\r\n"
            + "Body Goes Here\r\n"
            + "\r\n"
            + "--boundary--\r\n",
        "US-ASCII");

    BounceNotification notification = BounceNotificationParser.parse(req);
    assertThat(notification.getOriginal()).isNull();
  }
}
