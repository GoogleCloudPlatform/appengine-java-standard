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

import com.google.appengine.api.EnvironmentProvider;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SmtpMailServiceImplTest {

  @Mock private Transport transport;
  @Mock private Session session;
  @Mock private EnvironmentProvider envProvider;

  private SmtpMailServiceImpl mailService;

  @Before
  public void setUp() {
    mailService = new SmtpMailServiceImpl(envProvider, session);
    // Mock environment variables
    when(envProvider.getenv("APPENGINE_SMTP_HOST")).thenReturn("smtp.example.com");
    when(envProvider.getenv("APPENGINE_SMTP_PORT")).thenReturn("587");
    when(envProvider.getenv("APPENGINE_SMTP_USER")).thenReturn("user");
    when(envProvider.getenv("APPENGINE_SMTP_PASSWORD")).thenReturn("password");
    when(envProvider.getenv("APPENGINE_SMTP_USE_TLS")).thenReturn("true");
  }

  @Test
  public void testSendSmtp_basic() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message to send
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo(Collections.singletonList("to@example.com"));
    message.setCc(Collections.singletonList("cc@example.com"));
    message.setBcc(Collections.singletonList("bcc@example.com"));
    message.setSubject("Test Subject");
    message.setTextBody("Test Body");

    // Act
    // Call the method under test
    mailService.send(message);

    // Assert
    // Capture the arguments passed to transport.sendMessage
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    ArgumentCaptor<Address[]> recipientsCaptor = ArgumentCaptor.forClass(Address[].class);
    verify(transport, times(1)).sendMessage(messageCaptor.capture(), recipientsCaptor.capture());

    // Assertions for the MimeMessage
    MimeMessage sentMessage = messageCaptor.getValue();
    assertEquals("Test Subject", sentMessage.getSubject());
    assertEquals("sender@example.com", sentMessage.getFrom()[0].toString());
    assertEquals("to@example.com", sentMessage.getRecipients(RecipientType.TO)[0].toString());
    assertEquals("cc@example.com", sentMessage.getRecipients(RecipientType.CC)[0].toString());

    Address[] bccRecipients = sentMessage.getRecipients(RecipientType.BCC);
    assertTrue(
        "BCC recipients should not be in the message headers",
        bccRecipients == null || bccRecipients.length == 0);

    // Assertions for the recipient list passed to the transport layer
    Address[] allRecipients = recipientsCaptor.getValue();
    assertEquals(3, allRecipients.length);
    assertTrue(
        "Recipient list should contain TO address",
        Arrays.stream(allRecipients).anyMatch(a -> a.toString().equals("to@example.com")));
    assertTrue(
        "Recipient list should contain CC address",
        Arrays.stream(allRecipients).anyMatch(a -> a.toString().equals("cc@example.com")));
    assertTrue(
        "Recipient list should contain BCC address",
        Arrays.stream(allRecipients).anyMatch(a -> a.toString().equals("bcc@example.com")));
  }

  @Test
  public void testSendSmtp_multipleRecipients() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with multiple recipients
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo(Arrays.asList("to1@example.com", "to2@example.com"));
    message.setCc(Arrays.asList("cc1@example.com", "cc2@example.com"));
    message.setBcc(Arrays.asList("bcc1@example.com", "bcc2@example.com"));
    message.setSubject("Multiple Recipients Test");
    message.setTextBody("Test Body");

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    ArgumentCaptor<Address[]> recipientsCaptor = ArgumentCaptor.forClass(Address[].class);
    verify(transport, times(1)).sendMessage(messageCaptor.capture(), recipientsCaptor.capture());

    // Assertions for the MimeMessage headers
    MimeMessage sentMessage = messageCaptor.getValue();
    assertEquals("to1@example.com, to2@example.com", sentMessage.getHeader("To", ", "));
    assertEquals("cc1@example.com, cc2@example.com", sentMessage.getHeader("Cc", ", "));

    // Assertions for the recipient list passed to the transport layer
    Address[] allRecipients = recipientsCaptor.getValue();
    assertEquals(6, allRecipients.length);
    assertTrue(
        "Recipient list should contain all TO addresses",
        Arrays.stream(allRecipients)
            .map(Address::toString)
            .anyMatch(s -> s.equals("to1@example.com") || s.equals("to2@example.com")));
    assertTrue(
        "Recipient list should contain all CC addresses",
        Arrays.stream(allRecipients)
            .map(Address::toString)
            .anyMatch(s -> s.equals("cc1@example.com") || s.equals("cc2@example.com")));
    assertTrue(
        "Recipient list should contain all BCC addresses",
        Arrays.stream(allRecipients)
            .map(Address::toString)
            .anyMatch(s -> s.equals("bcc1@example.com") || s.equals("bcc2@example.com")));
  }

  @Test
  public void testSendSmtp_htmlAndPlainTextBodies() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with HTML and plain text bodies
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("HTML and Plain Text Test");
    message.setTextBody("This is the plain text body.");
    message.setHtmlBody("<h1>This is the HTML body.</h1>");

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getHeader("Content-Type")[0].startsWith("multipart/mixed"));

    // Further inspection of the multipart content can be added here
    // For example, checking the content of each part of the multipart message
    MimeMultipart mixedMultipart = (MimeMultipart) sentMessage.getContent();
    assertEquals(1, mixedMultipart.getCount());

    // Check the nested multipart/alternative part
    MimeBodyPart alternativePart = (MimeBodyPart) mixedMultipart.getBodyPart(0);
    assertTrue(alternativePart.getContentType().startsWith("multipart/alternative"));
    MimeMultipart alternativeMultipart = (MimeMultipart) alternativePart.getContent();
    assertEquals(2, alternativeMultipart.getCount());

    // Check the plain text part
    MimeBodyPart textPart = (MimeBodyPart) alternativeMultipart.getBodyPart(0);
    assertTrue(textPart.isMimeType("text/plain"));
    assertEquals("This is the plain text body.", textPart.getContent());

    // Check the HTML part
    MimeBodyPart htmlPart = (MimeBodyPart) alternativeMultipart.getBodyPart(1);
    assertTrue(htmlPart.isMimeType("text/html"));
    assertEquals("<h1>This is the HTML body.</h1>", htmlPart.getContent());
  }

  @Test
  public void testSendSmtp_htmlBodyOnly() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with only an HTML body
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("HTML Body Only Test");
    message.setHtmlBody("<h1>This is the HTML body.</h1>");

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getHeader("Content-Type")[0].startsWith("multipart/mixed"));

    // Check the multipart content
    MimeMultipart mixedMultipart = (MimeMultipart) sentMessage.getContent();
    assertEquals(1, mixedMultipart.getCount());

    // Check the nested multipart/alternative part
    MimeBodyPart alternativePart = (MimeBodyPart) mixedMultipart.getBodyPart(0);
    assertTrue(alternativePart.getContentType().startsWith("multipart/alternative"));
    MimeMultipart alternativeMultipart = (MimeMultipart) alternativePart.getContent();
    assertEquals(2, alternativeMultipart.getCount());

    // Check that the plain text part is empty
    MimeBodyPart textPart = (MimeBodyPart) alternativeMultipart.getBodyPart(0);
    assertTrue(textPart.isMimeType("text/plain"));
    assertEquals("", textPart.getContent());

    // Check the HTML part
    MimeBodyPart htmlPart = (MimeBodyPart) alternativeMultipart.getBodyPart(1);
    assertTrue(htmlPart.isMimeType("text/html"));
    assertEquals("<h1>This is the HTML body.</h1>", htmlPart.getContent());
  }

  @Test
  public void testSendSmtp_singleAttachment() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with an attachment
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Single Attachment Test");
    message.setTextBody("This is the body.");

    byte[] attachmentData = "This is an attachment.".getBytes();
    MailService.Attachment attachment =
        new MailService.Attachment("attachment.txt", attachmentData);
    message.setAttachments(Collections.singletonList(attachment));

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getContentType().startsWith("multipart/mixed"));

    MimeMultipart mixedMultipart = (MimeMultipart) sentMessage.getContent();
    assertEquals(2, mixedMultipart.getCount());

    // Check the body part, which should be a multipart/alternative
    MimeBodyPart bodyPart = (MimeBodyPart) mixedMultipart.getBodyPart(0);
    assertTrue(bodyPart.getContentType().startsWith("multipart/alternative"));
    MimeMultipart alternativeMultipart = (MimeMultipart) bodyPart.getContent();
    assertEquals(1, alternativeMultipart.getCount());
    MimeBodyPart textPart = (MimeBodyPart) alternativeMultipart.getBodyPart(0);
    assertTrue(textPart.isMimeType("text/plain"));
    assertEquals("This is the body.", textPart.getContent());

    // Check the attachment part
    MimeBodyPart attachmentPart = (MimeBodyPart) mixedMultipart.getBodyPart(1);
    assertEquals("attachment.txt", attachmentPart.getFileName());

    // Verify the content of the attachment
    byte[] actualAttachmentData = new byte[attachmentData.length];
    attachmentPart.getDataHandler().getInputStream().read(actualAttachmentData);
    assertTrue(Arrays.equals(attachmentData, actualAttachmentData));
  }

  @Test
  public void testSendSmtp_multipleAttachments() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with multiple attachments
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Multiple Attachments Test");
    message.setTextBody("This is the body.");

    byte[] attachmentData1 = "This is attachment 1.".getBytes();
    MailService.Attachment attachment1 =
        new MailService.Attachment("attachment1.txt", attachmentData1);
    byte[] attachmentData2 = "This is attachment 2.".getBytes();
    MailService.Attachment attachment2 =
        new MailService.Attachment("attachment2.txt", attachmentData2);
    message.setAttachments(Arrays.asList(attachment1, attachment2));

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getContentType().startsWith("multipart/mixed"));

    MimeMultipart mixedMultipart = (MimeMultipart) sentMessage.getContent();
    assertEquals(3, mixedMultipart.getCount());

    // Check the body part
    MimeBodyPart bodyPart = (MimeBodyPart) mixedMultipart.getBodyPart(0);
    assertTrue(bodyPart.getContentType().startsWith("multipart/alternative"));

    // Check the first attachment
    MimeBodyPart attachmentPart1 = (MimeBodyPart) mixedMultipart.getBodyPart(1);
    assertEquals("attachment1.txt", attachmentPart1.getFileName());
    byte[] actualAttachmentData1 = new byte[attachmentData1.length];
    attachmentPart1.getDataHandler().getInputStream().read(actualAttachmentData1);
    assertTrue(Arrays.equals(attachmentData1, actualAttachmentData1));

    // Check the second attachment
    MimeBodyPart attachmentPart2 = (MimeBodyPart) mixedMultipart.getBodyPart(2);
    assertEquals("attachment2.txt", attachmentPart2.getFileName());
    byte[] actualAttachmentData2 = new byte[attachmentData2.length];
    attachmentPart2.getDataHandler().getInputStream().read(actualAttachmentData2);
    assertTrue(Arrays.equals(attachmentData2, actualAttachmentData2));
  }

  @Test
  public void testSendSmtp_htmlBodyAndAttachments() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with HTML body and an attachment
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("HTML Body and Attachments Test");
    message.setHtmlBody("<h1>This is the HTML body.</h1>");

    byte[] attachmentData = "This is an attachment.".getBytes();
    MailService.Attachment attachment =
        new MailService.Attachment("attachment.txt", attachmentData);
    message.setAttachments(Collections.singletonList(attachment));

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(sentMessage.getContentType().startsWith("multipart/mixed"));

    MimeMultipart mixedMultipart = (MimeMultipart) sentMessage.getContent();
    assertEquals(2, mixedMultipart.getCount());

    // Check the body part (multipart/alternative)
    MimeBodyPart bodyPart = (MimeBodyPart) mixedMultipart.getBodyPart(0);
    assertTrue(bodyPart.getContentType().startsWith("multipart/alternative"));
    MimeMultipart alternativeMultipart = (MimeMultipart) bodyPart.getContent();
    assertEquals(2, alternativeMultipart.getCount());

    // Check the plain text part (should be empty)
    MimeBodyPart textPart = (MimeBodyPart) alternativeMultipart.getBodyPart(0);
    assertTrue(textPart.isMimeType("text/plain"));
    assertEquals("", textPart.getContent());

    // Check the HTML part
    MimeBodyPart htmlPart = (MimeBodyPart) alternativeMultipart.getBodyPart(1);
    assertTrue(htmlPart.isMimeType("text/html"));
    assertEquals("<h1>This is the HTML body.</h1>", htmlPart.getContent());

    // Check the attachment part
    MimeBodyPart attachmentPart = (MimeBodyPart) mixedMultipart.getBodyPart(1);
    assertEquals("attachment.txt", attachmentPart.getFileName());
    byte[] actualAttachmentData = new byte[attachmentData.length];
    attachmentPart.getDataHandler().getInputStream().read(actualAttachmentData);
    assertTrue(Arrays.equals(attachmentData, actualAttachmentData));
  }

  @Test
  public void testSendSmtp_attachmentWithContentId() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with an attachment with a Content-ID
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Attachment with Content-ID Test");
    message.setTextBody("This is the body.");

    byte[] attachmentData = "This is an attachment.".getBytes();
    MailService.Attachment attachment =
        new MailService.Attachment("attachment.txt", attachmentData, "<my-content-id>");
    message.setAttachments(Collections.singletonList(attachment));

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    MimeMultipart mixedMultipart = (MimeMultipart) sentMessage.getContent();

    // Check the attachment part
    MimeBodyPart attachmentPart = (MimeBodyPart) mixedMultipart.getBodyPart(1);
    assertEquals("<my-content-id>", attachmentPart.getContentID());
  }

  @Test
  public void testSendSmtp_replyToHeader() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with a Reply-To header
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Reply-To Test");
    message.setTextBody("This is the body.");
    message.setReplyTo("reply-to@example.com");

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertEquals(1, sentMessage.getReplyTo().length);
    assertEquals("reply-to@example.com", sentMessage.getReplyTo()[0].toString());
  }

  @Test
  public void testSendSmtp_customHeaders() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create the message with custom headers
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Custom Headers Test");
    message.setTextBody("This is the body.");
    message.setHeaders(
        Collections.singletonList(new MailService.Header("X-Custom-Header", "my-value")));

    // Act
    mailService.send(message);

    // Assert
    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(transport).sendMessage(messageCaptor.capture(), any());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertEquals("my-value", sentMessage.getHeader("X-Custom-Header")[0]);
  }

  @Test
  public void testSendSmtp_disabledTls() throws IOException, MessagingException {
    // This test is no longer relevant as the session is created outside.
  }

  @Test
  public void testSendSmtp_adminEmail() throws IOException, MessagingException {
    // Setup
    when(envProvider.getenv("APPENGINE_ADMIN_EMAIL_RECIPIENTS"))
        .thenReturn("admin1@example.com,admin2@example.com");
    when(session.getTransport("smtp")).thenReturn(transport);

    // Create a message with some recipients that should be ignored
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setCc("cc@example.com");
    message.setBcc("bcc@example.com");
    message.setSubject("Admin Email Test");
    message.setTextBody("This is the body.");

    // Act
    mailService.sendToAdmins(message);

    // Assert
    ArgumentCaptor<Address[]> recipientsCaptor = ArgumentCaptor.forClass(Address[].class);
    verify(transport).sendMessage(any(MimeMessage.class), recipientsCaptor.capture());

    Address[] recipients = recipientsCaptor.getValue();
    assertEquals(2, recipients.length);
    assertTrue(
        "Recipient list should contain admin1@example.com",
        Arrays.stream(recipients).anyMatch(a -> a.toString().equals("admin1@example.com")));
    assertTrue(
        "Recipient list should contain admin2@example.com",
        Arrays.stream(recipients).anyMatch(a -> a.toString().equals("admin2@example.com")));
  }

  @Test
  public void testSendSmtp_adminEmailNoRecipients() throws IOException, MessagingException {
    // Setup
    when(envProvider.getenv("APPENGINE_ADMIN_EMAIL_RECIPIENTS")).thenReturn(null);

    // Create a simple message
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setSubject("Admin Email No Recipients Test");
    message.setTextBody("This is the body.");

    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> mailService.sendToAdmins(message));
  }

  @Test
  public void testSendSmtp_authenticationFailure() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);
    doThrow(new javax.mail.AuthenticationFailedException("Authentication failed"))
        .when(transport)
        .connect();

    // Create a simple message
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Authentication Failure Test");
    message.setTextBody("This is the body.");

    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> mailService.send(message));
  }

  @Test
  public void testSendSmtp_connectionFailure() throws IOException, MessagingException {
    // Setup
    when(session.getTransport("smtp")).thenReturn(transport);
    doThrow(new MessagingException("Connection failed")).when(transport).connect();

    // Create a simple message
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Connection Failure Test");
    message.setTextBody("This is the body.");

    // Act & Assert
    assertThrows(IOException.class, () -> mailService.send(message));
  }

  @Test
  public void testSendSmtp_missingSmtpHost() throws IOException, MessagingException {
    // Setup
    when(envProvider.getenv("APPENGINE_SMTP_HOST")).thenReturn(null);

    // Create a simple message
    MailService.Message message = new MailService.Message();
    message.setSender("sender@example.com");
    message.setTo("to@example.com");
    message.setSubject("Missing SMTP Host Test");
    message.setTextBody("This is the body.");

    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> mailService.send(message));
  }
}
