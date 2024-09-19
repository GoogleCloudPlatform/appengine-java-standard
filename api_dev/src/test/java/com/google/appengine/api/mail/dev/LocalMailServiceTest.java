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

package com.google.appengine.api.mail.dev;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailService.Attachment;
import com.google.appengine.api.mail.MailServiceFactory;
import com.google.appengine.api.mail.MailServicePb;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.testing.LocalMailServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LocalMailServiceTest {

  private LocalServiceTestHelper helper;
  private MailService mailService;

  @Before
  public void setUp() throws Exception {
    helper = new LocalServiceTestHelper(new LocalMailServiceTestConfig());
    helper.setUp();
    mailService = MailServiceFactory.getMailService();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
  }

  /** Tests that a sent message is logged correctly. */
  @Test
  public void testSend() throws IOException {
    MailService.Message msg = new MailService.Message();
    msg.setSender("me");
    msg.setSubject("stuff");
    msg.setTextBody("12345");
    msg.setTo("someone");
    mailService.send(msg);
    LocalMailService localMailService = getLocalMailService();
    assertThat(localMailService.getSentMessages().size()).isEqualTo(1);
    assertThat(localMailService.getSentMessages().get(0).getTextBody())
        .isEqualTo(msg.getTextBody());
  }

  /** Tests that a message sent to admins is logged correctly. */
  @Test
  public void testSendToAdmins() throws IOException {
    MailService.Message msg = new MailService.Message();
    msg.setSender("me");
    msg.setSubject("stuff");
    msg.setTextBody("12345");
    mailService.sendToAdmins(msg);
    LocalMailService localMailService = getLocalMailService();

    assertThat(localMailService.getSentMessages().size()).isEqualTo(1);
    assertThat(localMailService.getSentMessages().get(0).getTextBody())
        .isEqualTo(msg.getTextBody());
  }

  /** Tests that init properties are correctly set. */
  @Test
  public void testInitProperties() {
    LocalMailService localMailService = getLocalMailService();
    assertThat(localMailService.logMailBody).isEqualTo(LocalMailService.DEFAULT_LOG_MAIL_BODY);
    assertThat(localMailService.logMailLevel).isEqualTo(LocalMailService.DEFAULT_LOG_MAIL_LEVEL);

    ApiProxyLocal delegate = (ApiProxyLocal) ApiProxy.getDelegate();
    delegate.stop();
    delegate.setProperty(LocalMailService.LOG_MAIL_BODY_PROPERTY, Boolean.TRUE.toString());
    delegate.setProperty(LocalMailService.LOG_MAIL_LEVEL_PROPERTY, Level.FINE.toString());
    localMailService = getLocalMailService();
    assertThat(localMailService.getLogMailBody().getLogMailBody()).isTrue();
    assertThat(localMailService.logMailLevel).isEqualTo(Level.FINE);

    delegate.stop();
    delegate.setProperty(LocalMailService.LOG_MAIL_LEVEL_PROPERTY, Level.INFO.toString());
    localMailService = getLocalMailService();
    assertThat(localMailService.logMailLevel).isEqualTo(Level.INFO);
  }

  /** Test that {@link MailService#send} works with a non-denylisted attachment. */
  @Test
  public void testNonDenylistedAttachment() throws Exception {
    MailService.Message msg = makeMessageWithAttachmentNamed("virtuous.txt");
    mailService.send(msg);
    assertThat(getLocalMailService().getSentMessages().size()).isEqualTo(1);
  }

  /** Tests that an extensionless attachment causes {@link MailService#send} to fail. */
  @Test
  public void testExtensionlessAttachment() throws Exception {
    verifySendFailsForAttachmentNamed("noextension");
  }

  /** Tests that a 'hidden' attachment causes {@link MailService#send} to fail. */
  @Test
  public void testHiddenFileAttachment() throws Exception {
    verifySendFailsForAttachmentNamed(".hidden");
  }

  /** Tests that a denylisted attachment causes {@link MailService#send} to fail. */
  @Test
  public void testDenylistedAttachments() throws Exception {
    verifySendFailsForAttachmentNamed("exploit.exe");
  }

  private void verifySendFailsForAttachmentNamed(final String fileName) throws IOException {
    MailService.Message msg = makeMessageWithAttachmentNamed(fileName);
    assertThrows(IllegalArgumentException.class, () -> mailService.send(msg));
    assertThat(getLocalMailService().getSentMessages()).isEmpty();
  }

  private MailService.Message makeMessageWithAttachmentNamed(final String fileName) {
    MailService.Message msg = new MailService.Message();
    msg.setSender("sender");
    msg.setSubject("subject");
    msg.setTo("to");
    msg.setTextBody("body");
    msg.setAttachments(new Attachment(fileName, "content".getBytes(UTF_8)));
    return msg;
  }

  /** Tests that the correct log message is generated for a message. */
  @Test
  public void testLogMsg() {
    final StringBuilder sb = new StringBuilder();
    LocalMailService localMailService = getLocalMailService();
    localMailService.logger =
        new Logger("test", null) {
          @Override
          public void log(Level level, String msg) {
            sb.append(msg);
          }

          @Override
          public void logp(Level level, String className, String methodName, String msg) {
            sb.append(msg);
          }
        };
    localMailService.logMailBody = true;
    MailServicePb.MailMessage msg =
        MailServicePb.MailMessage.newBuilder()
            .setSender("me")
            .setSubject("the subject")
            .addTo("to1")
            .addTo("to2")
            .addCc("cc1")
            .addCc("cc2")
            .addBcc("bcc1")
            .addBcc("bcc2")
            .setReplyTo("the reply-to")
            .setTextBody("text body")
            .setHtmlBody("html body")
            .addAttachment(
                MailServicePb.MailAttachment.newBuilder()
                    .setFileName("filename 1")
                    .setData(ByteString.copyFromUtf8("some data 1")))
            .addAttachment(
                MailServicePb.MailAttachment.newBuilder()
                    .setFileName("filename 2")
                    .setData(ByteString.copyFromUtf8("some data 2")))
            .build();
    localMailService.logMailMessage("yar", msg);
    assertThat(sb.toString())
        .isEqualTo(
            "MailService.yar"
                + "  From: me"
                + "  To: to1"
                + "  To: to2"
                + "  Cc: cc1"
                + "  Cc: cc2"
                + "  Bcc: bcc1"
                + "  Bcc: bcc2"
                + "  Reply-to: the reply-to"
                + "  Subject: the subject"
                + "  Body:"
                + "    Content-type: text/plain"
                + "    Data length: 9"
                + "-----\ntext body\n-----"
                + "  Body:"
                + "    Content-type: text/html"
                + "    Data length: 9"
                + "-----\nhtml body\n-----"
                + "  Attachment:"
                + "    File name: filename 1"
                + "    Data length: 11"
                + "  Attachment:"
                + "    File name: filename 2"
                + "    Data length: 11");
  }

  private LocalMailService getLocalMailService() {
    return LocalMailServiceTestConfig.getLocalMailService();
  }
}
