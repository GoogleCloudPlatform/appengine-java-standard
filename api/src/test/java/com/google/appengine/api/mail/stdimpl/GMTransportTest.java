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

package com.google.appengine.api.mail.stdimpl;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.mail.MailServicePb.MailAttachment;
import com.google.appengine.api.mail.MailServicePb.MailHeader;
import com.google.appengine.api.mail.MailServicePb.MailMessage;
import com.google.appengine.api.mail.MailServicePb.MailServiceError.ErrorCode;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Tests for the GMTransport JavaMail wrapper over the MailService.
 *
 */
@RunWith(JUnit4.class)
public class GMTransportTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final String PACKAGE = "mail";

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  @Mock private ApiProxy.Environment environment;
  private Session session;

  @Before
  public void setUp() throws Exception {
    System.setProperty("appengine.mail.filenamePreventsInlining", "true");
    System.setProperty("appengine.mail.supportExtendedAttachmentEncodings", "true");
    ApiProxy.setDelegate(delegate);
    ApiProxy.setEnvironmentForCurrentThread(environment);

    Properties props = new Properties();
    props.put("mail.debug", "true");
    session = Session.getInstance(props);
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setDelegate(null);
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  @Test
  public void testSendMessage_toAndCcRecipient() throws Exception {
    runRecipientTest("user@example", "cc@example.com", null);
  }

  @Test
  public void testSendMessage_toAndBccRecipient() throws Exception {
    runRecipientTest("user@example", null, "bcc@example.com");
  }

  @Test
  public void testSendMessage_allRecipients() throws Exception {
    runRecipientTest("user@example", "cc@example.com", "bcc@example.com");
  }

  @Test
  public void testSendMessage_ccRecipient() throws Exception {
    runRecipientTest(null, "cc@example.com", null);
  }

  @Test
  public void testSendMessage_bccRecipient() throws Exception {
    runRecipientTest(null, null, "bcc@example.com");
  }

  @Test
  public void testSendMessage_ccAndBccRecipient() throws Exception {
    runRecipientTest(null, "cc@example.com", "bcc@example.com");
  }

  /** Runs a test with the given recipients. */
  private void runRecipientTest(String to, String cc, String bcc) throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    if (to != null) {
      message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
    }
    if (cc != null) {
      message.setRecipient(Message.RecipientType.CC, new InternetAddress(cc));
    }
    if (bcc != null) {
      message.setRecipient(Message.RecipientType.BCC, new InternetAddress(bcc));
    }
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    message.setText(text);
    MailMessage.Builder msgProto =
        MailMessage.newBuilder()
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject);

    if (to != null) {
      msgProto.addTo(to);
    }
    if (cc != null) {
      msgProto.addCc(cc);
    }
    if (bcc != null) {
      msgProto.addBcc(bcc);
    }

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(
            same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.build().toByteArray()));
  }

  /** Tests that a unicode encoded message works correctly */
  @Test
  public void testSendMessage_unicodeBody() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "\u0163\u00ee\u00e9.";
    InputStream stream = new ByteArrayInputStream(text.getBytes(UTF_16));
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    message.setContent(stream, "text/plain; charset=utf-16");
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject)
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests a message with a single attachment. */
  @Test
  public void testSendMessage_simpleMultipart() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    String filename = "file.txt";
    String fileContents = "file contents.";
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText(text);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setText(fileContents);
    body.setFileName(filename);
    multi.addBodyPart(body);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject)
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filename)
                    .setData(ByteString.copyFromUtf8(fileContents)))
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /**
   * Tests that sending an AMP Email works. An AMP Email is an email with a text/x-amp-html body
   * part under multipart.
   */
  @Test
  public void testSendMessage_ampEmail() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    String html = "<html></html>";
    String ampHtml = "<html ⚡4email></html>";
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart textBody = new MimeBodyPart();
    textBody.setText(text);
    multi.addBodyPart(textBody);
    MimeBodyPart htmlBody = new MimeBodyPart();
    htmlBody.setContent(html, "text/html");
    multi.addBodyPart(htmlBody);
    MimeBodyPart ampHtmlBody = new MimeBodyPart();
    ampHtmlBody.setContent(ampHtml, "text/x-amp-html");
    multi.addBodyPart(ampHtmlBody);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setHtmlBody(html)
            .setAmpHtmlBody(ampHtml)
            .setSubject(subject)
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests a message with a single attachment encoded in byte[]. */
  @Test
  public void testSendMessage_byteAttachment() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    String filename = "file.txt";
    byte[] fileContents = "file contents.".getBytes(UTF_8);
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText(text);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(fileContents, "text/plain");
    body.setFileName(filename);
    multi.addBodyPart(body);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject)
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filename)
                    .setData(ByteString.copyFrom(fileContents)))
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests a message with a nested attachments. */
  @Test
  public void testSendMessage_nestedAttachment() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    String filenameOuter = "file.txt";
    String filenameNested1 = "file1.txt";
    String filenameNested2 = "file2.txt";
    byte[] fileContents = "file contents.".getBytes(UTF_8);
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText(text);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(fileContents, "text/plain");
    body.setFileName(filenameOuter);
    multi.addBodyPart(body);
    MimeMultipart nested = new MimeMultipart();
    body = new MimeBodyPart();
    body.setContent(fileContents, "text/plain");
    body.setFileName(filenameNested1);
    nested.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(fileContents, "text/plain");
    body.setFileName(filenameNested2);
    nested.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(nested);
    multi.addBodyPart(body);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject)
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filenameOuter)
                    .setData(ByteString.copyFrom(fileContents)))
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filenameNested1)
                    .setData(ByteString.copyFrom(fileContents)))
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filenameNested2)
                    .setData(ByteString.copyFrom(fileContents)))
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests a message with multiple attachments and text and html bodies. */
  @Test
  public void testSendMessage_complexMultipart() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    String filename = "file.txt";
    String fileContents = "file contents.";
    String imageFilename = "image.png";
    String imageContents = "an image";
    String imageContentID = "<image>";
    String imageMimeType = "image/png";
    String htmlBody = "<html><body><p>Hello!</body></html>";
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText(text);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setText(fileContents);
    body.setFileName(filename);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(imageContents, imageMimeType);
    body.setFileName(imageFilename);
    body.setContentID(imageContentID);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(htmlBody, "text/html");
    multi.addBodyPart(body);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setHtmlBody(htmlBody)
            .setSubject(subject)
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filename)
                    .setData(ByteString.copyFromUtf8(fileContents)))
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(imageFilename)
                    .setData(ByteString.copyFromUtf8(imageContents))
                    .setContentID(imageContentID))
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /*
   * Tests a message with an HTML body and a text attachment.
   */
  @Test
  public void testSendMessage_dontInlineAttachments() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject("Subject");
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setContent("<b>Test Test</b>", "text/html");
    multi.addBodyPart(body);
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setContent("Test Test", "text/plain");
    attachment.setFileName("dontinline.me");
    multi.addBodyPart(attachment);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setHtmlBody("<b>Test Test</b>")
            .setSubject("Subject")
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName("dontinline.me")
                    .setData(ByteString.copyFromUtf8("Test Test")))
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /*
   * Tests a message with an HTML body and a text alternative that has a
   * filename specified.
   */
  @Test
  public void testSendMessage_inlineAlternateEncoding() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject("Subject");
    MimeMultipart multi = new MimeMultipart("alternative");
    MimeBodyPart body = new MimeBodyPart();
    body.setContent("<b>Test Test</b>", "text/html");
    multi.addBodyPart(body);
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setContent("Test Test", "text/plain");
    attachment.setFileName("inline.me");
    multi.addBodyPart(attachment);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setHtmlBody("<b>Test Test</b>")
            .setTextBody("Test Test")
            .setSubject("Subject")
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests that a message sent to the admin address is sent to admins. */
  @Test
  public void testSendMessage_toAdmins() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("admins");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    message.setText(text);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject)
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(
            same(environment), eq(PACKAGE), eq("SendToAdmins"), eq(msgProto.toByteArray()));
  }

  /** Tests a multipart message sent to admins. */
  @Test
  public void testSendMessage_multipartToAdmins() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("admins");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "subject";
    String text = "message text.";
    String filename = "file.txt";
    String fileContents = "file contents.";
    String imageFilename = "image.png";
    String imageContents = "an image";
    String imageMimeType = "image/png";
    String htmlBody = "<html><body><p>Hello!</body></html>";
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText(text);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setText(fileContents);
    body.setFileName(filename);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(imageContents, imageMimeType);
    body.setFileName(imageFilename);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(htmlBody, "text/html");
    multi.addBodyPart(body);
    message.setContent(multi);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setHtmlBody(htmlBody)
            .setSubject(subject)
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filename)
                    .setData(ByteString.copyFromUtf8(fileContents)))
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(imageFilename)
                    .setData(ByteString.copyFromUtf8(imageContents)))
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(
            same(environment), eq(PACKAGE), eq("SendToAdmins"), eq(msgProto.toByteArray()));
  }

  /** Tests a multipart message with multiple recipients of each type. */
  @Test
  public void testSendMessage_multipleRecipients() throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress[] to =
        new InternetAddress[] {
          new InternetAddress("user@example.com"),
          new InternetAddress("another_user@example.com"),
          new InternetAddress("anotheruser@example.com"),
          new InternetAddress("someone@example.com"),
          new InternetAddress("someoneelse@example.com"),
          new InternetAddress("auseralloneword@example.com"),
        };
    InternetAddress[] cc =
        new InternetAddress[] {
          new InternetAddress("cc@example.com"), new InternetAddress("another_cc@example.com")
        };
    InternetAddress[] bcc =
        new InternetAddress[] {
          new InternetAddress("bcc@example.com"), new InternetAddress("another_bcc@example.com")
        };

    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "subject";
    String text = "message text.";
    String filename = "file.txt";
    String fileContents = "file contents.";
    String imageFilename = "image.png";
    String imageContents = "an image";
    String imageMimeType = "image/png";
    String htmlBody = "<html><body><p>Hello!</body></html>";
    message.addRecipients(Message.RecipientType.TO, to);
    message.addRecipients(Message.RecipientType.CC, cc);
    message.addRecipients(Message.RecipientType.BCC, bcc);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    MimeMultipart multi = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText(text);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setText(fileContents);
    body.setFileName(filename);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(imageContents, imageMimeType);
    body.setFileName(imageFilename);
    multi.addBodyPart(body);
    body = new MimeBodyPart();
    body.setContent(htmlBody, "text/html");
    multi.addBodyPart(body);
    message.setContent(multi);
    MailMessage.Builder msgProto =
        MailMessage.newBuilder()
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject)
            .setHtmlBody(htmlBody)
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filename)
                    .setData(ByteString.copyFromUtf8(fileContents)))
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(imageFilename)
                    .setData(ByteString.copyFromUtf8(imageContents)));

    for (InternetAddress address : to) {
      msgProto.addTo(address.toString());
    }
    for (InternetAddress address : cc) {
      msgProto.addCc(address.toString());
    }
    for (InternetAddress address : bcc) {
      msgProto.addBcc(address.toString());
    }

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(
            same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.build().toByteArray()));
  }

  /** Sets up a send call to throw an exception with the given error code. */
  private Message setupSendCallWithApplicationException(ErrorCode code) throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";
    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    message.setText(text);
    MailMessage msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject)
            .build();

    when(delegate.makeSyncCall(
            same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.toByteArray())))
        .thenThrow(new ApiProxy.ApplicationException(code.getNumber(), "detail"));

    return message;
  }

  /** Tests that the right exception is passed back for each error code. */
  @Test
  public void testSendWithExceptions() throws Exception {
    Message msg1 = setupSendCallWithApplicationException(ErrorCode.BAD_REQUEST);
    MessagingException ex1 = assertThrows(MessagingException.class, () -> GMTransport.send(msg1));
    Throwable cause1 = getRootException(ex1);
    assertThat(cause1).isInstanceOf(IllegalArgumentException.class);
    assertThat(ex1).hasCauseThat().hasMessageThat().contains("detail");

    Message msg2 = setupSendCallWithApplicationException(ErrorCode.INVALID_ATTACHMENT_TYPE);
    MessagingException ex2 = assertThrows(MessagingException.class, () -> GMTransport.send(msg2));
    Throwable cause2 = getRootException(ex2);
    assertThat(cause2).isInstanceOf(IllegalArgumentException.class);
    assertThat(ex2).hasCauseThat().hasMessageThat().contains("detail");

    Message msg3 = setupSendCallWithApplicationException(ErrorCode.UNAUTHORIZED_SENDER);
    MessagingException ex3 = assertThrows(MessagingException.class, () -> GMTransport.send(msg3));
    Throwable cause3 = getRootException(ex3);
    assertThat(cause3).isInstanceOf(IllegalArgumentException.class);
    assertThat(ex3).hasCauseThat().hasMessageThat().contains("detail");

    Message msg4 = setupSendCallWithApplicationException(ErrorCode.INTERNAL_ERROR);
    SendFailedException ex4 = assertThrows(SendFailedException.class, () -> GMTransport.send(msg4));
    Throwable cause4 = getRootException(ex4);
    assertThat(cause4).isInstanceOf(IOException.class);
    assertThat(ex4).hasCauseThat().hasMessageThat().contains("detail");
  }

  /** Sends an email with headers and checks that the expectedHeaders are actually sent. */
  private void runHeadersTest(String[] headers, String[] expectedHeaders) throws Exception {
    MimeMessage message = new MimeMessage(session);
    InternetAddress to = new InternetAddress("user@example.com");
    InternetAddress from = new InternetAddress("sender@example.com");
    InternetAddress replyTo = new InternetAddress("replies@example.com");
    String subject = "<insert subject here>";
    String text = "message text.";

    message.setRecipient(Message.RecipientType.TO, to);
    message.setSender(from);
    message.setReplyTo(new InternetAddress[] {replyTo});
    message.setSubject(subject);
    message.setText(text);
    for (String headerName : headers) {
      message.setHeader(headerName, headerName + ": value");
    }

    MailMessage.Builder msgProto =
        MailMessage.newBuilder()
            .addTo(to.toString())
            .setSender(from.toString())
            .setReplyTo(replyTo.toString())
            .setTextBody(text)
            .setSubject(subject);
    for (String headerName : expectedHeaders) {
      msgProto.addHeader(
          MailHeader.newBuilder().setName(headerName).setValue(headerName + ": value"));
    }

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    GMTransport.send(message);

    verify(delegate)
        .makeSyncCall(
            same(environment), eq(PACKAGE), eq("Send"), eq(msgProto.build().toByteArray()));
  }

  /** Tests that valid headers can be set. */
  @Test
  public void testSendWithHeaders() throws Exception {
    String[] headers = new String[] {"Resent-From", "In-Reply-To"};
    runHeadersTest(headers, headers);
  }

  /** Tests that invalid headers are not set. */
  @Test
  public void testSendWithInvalidHeaders() throws Exception {
    runHeadersTest(new String[] {"X-Resent-From"}, new String[] {});
  }

  /** Tests that invalid headers are discarded and valid headers are kept. */
  @Test
  public void testSendWithValidInvalidHeaders() throws Exception {
    runHeadersTest(
        new String[] {"X-Resent-From", "Resent-From", "In-Reply-To"},
        new String[] {"Resent-From", "In-Reply-To"});
  }

  private Throwable getRootException(Throwable ex) {
    while (ex.getCause() != null) {
      ex = ex.getCause();
    }
    return ex;
  }
}
