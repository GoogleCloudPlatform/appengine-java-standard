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
import java.io.IOException;
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
 * Unit tests for the MailServiceImpl class. Cloned from URLFetchService.
 *
 */
@RunWith(JUnit4.class)
public class MailServiceImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  @Mock private ApiProxy.Environment environment;
  private MailService service;

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);
    ApiProxy.setEnvironmentForCurrentThread(environment);
    service = new MailServiceImpl();
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setDelegate(null);
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  /**
   * Creates a new simple message.
   *
   * @return A message.
   */
  private MailService.Message newMessage() {
    // must be the owner of the application
    String sender = "sender@example.com";
    String to = "recipient@example.com";
    String subject = "prometheus testSimpleSend";
    String textBody = "test body";

    return new MailService.Message(sender, to, subject, textBody);
  }

  /**
   * Creates a new MailMessage based on the provided Message
   *
   * @param msg The message to use.
   * @return A MailMessage containing the fields of {@code msg}.
   */
  private MailMessage newMailMessage(MailService.Message msg) {
    MailMessage.Builder msgProto = MailMessage.newBuilder();
    msgProto.setSender(msg.getSender());
    for (String to : msg.getTo()) {
      msgProto.addTo(to);
    }
    msgProto.setSubject(msg.getSubject());
    if (msg.getTextBody() != null) {
      msgProto.setTextBody(msg.getTextBody());
    }
    if (msg.getHtmlBody() != null) {
      msgProto.setHtmlBody(msg.getHtmlBody());
    }
    if (msg.getAmpHtmlBody() != null) {
      msgProto.setAmpHtmlBody(msg.getAmpHtmlBody());
    }

    return msgProto.build();
  }

  // With the current implementation, this is supposed to pass,
  // although the delivery agent will throw an error when receiving
  // the RPC. Just making sure we accept whatever arguments the
  // caller wants to provide, and to catch regression on http://b/1522935
  @Test
  public void testSendAllNull() throws Exception {
    // all fields are null
    MailService.Message msg = new MailService.Message();
    MailMessage msgProto = MailMessage.getDefaultInstance();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    new MailServiceImpl().send(msg);
    verify(delegate)
        .makeSyncCall(
            same(environment), eq(MailServiceImpl.PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests that a message with an attachment works correctly. */
  @Test
  public void testDoSend_withAttachment() throws Exception {
    String filename = "name.txt";
    byte[] fileContents = "content".getBytes();
    MailService.Message msg = newMessage();
    MailService.Attachment attachment = new MailService.Attachment(filename, fileContents);
    msg.setAttachments(attachment);
    MailMessage.Builder msgProto = newMailMessage(msg).toBuilder();
    msgProto.addAttachment(
        MailAttachment.newBuilder()
            .setFileName(filename)
            .setData(ByteString.copyFrom(fileContents)));

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.send(msg);
    verify(delegate)
        .makeSyncCall(
            same(environment),
            eq(MailServiceImpl.PACKAGE),
            eq("Send"),
            eq(msgProto.build().toByteArray()));
  }

  /** Tests that a message with an attachment works correctly. */
  @Test
  public void testDoSend_withContentIDAttachment() throws Exception {
    String filename = "name.txt";
    byte[] fileContents = "content".getBytes();
    String contentID = "<image>";
    MailService.Message msg = newMessage();
    MailService.Attachment attachment =
        new MailService.Attachment(filename, fileContents, contentID);
    msg.setAttachments(attachment);
    MailMessage msgProto =
        newMailMessage(msg).toBuilder()
            .addAttachment(
                MailAttachment.newBuilder()
                    .setFileName(filename)
                    .setData(ByteString.copyFrom(fileContents))
                    .setContentID(contentID))
            .build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.send(msg);

    verify(delegate)
        .makeSyncCall(
            same(environment), eq(MailServiceImpl.PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests that sending a AMP Email message works correctly. */
  @Test
  public void testDoSend_ampEmail() throws Exception {
    String text = "text";
    String html = "<html></html>";
    String ampHtml = "<html ⚡4email></html>";
    MailService.Message msg = newMessage();
    msg.setTextBody(text);
    msg.setHtmlBody(html);
    msg.setAmpHtmlBody(ampHtml);
    MailMessage msgProto =
        newMailMessage(msg).toBuilder()
            .setTextBody(text)
            .setHtmlBody(html)
            .setAmpHtmlBody(ampHtml)
            .build();
    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.send(msg);
    verify(delegate)
        .makeSyncCall(
            same(environment), eq(MailServiceImpl.PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
  }

  /** Tests that a message with a header works correctly. */
  @Test
  public void testDoSend_withHeader() throws Exception {
    String name = "In-Reply-To";
    String value = "<some message id>";
    MailService.Message msg = newMessage();
    MailService.Header header = new MailService.Header(name, value);
    msg.setHeaders(header);
    MailMessage.Builder msgProto = newMailMessage(msg).toBuilder();
    msgProto.addHeader(MailHeader.newBuilder().setName(name).setValue(value));

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.send(msg);

    verify(delegate)
        .makeSyncCall(
            same(environment),
            eq(MailServiceImpl.PACKAGE),
            eq("Send"),
            eq(msgProto.build().toByteArray()));
  }

  /** Tests that the reply to field works correctly. */
  @Test
  public void testDoSend_replyToAddress() throws Exception {
    MailService.Message msg = newMessage();
    msg.setReplyTo("reply@example.com");
    MailMessage msgProto = newMailMessage(msg).toBuilder().setReplyTo("reply@example.com").build();
    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.send(msg);

    verify(delegate)
        .makeSyncCall(
            same(environment), eq(MailServiceImpl.PACKAGE), eq("Send"), eq(msgProto.toByteArray()));
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
    MailService.Message message = new MailService.Message();
    String from = "sender@example.com";
    String subject = "<insert subject here>";
    String text = "message text.";
    MailMessage.Builder msgProto = MailMessage.newBuilder();
    if (to != null) {
      message.setTo(to, "another_" + to);
      msgProto.addTo(to);
      msgProto.addTo("another_" + to);
    }
    if (cc != null) {
      message.setCc(cc, "another_" + cc);
      msgProto.addCc(cc);
      msgProto.addCc("another_" + cc);
    }
    if (bcc != null) {
      message.setBcc(bcc, "another_" + bcc);
      msgProto.addBcc(bcc);
      msgProto.addBcc("another_" + bcc);
    }
    message.setSender(from);
    message.setSubject(subject);
    message.setTextBody(text);
    msgProto.setSender(from);
    msgProto.setTextBody(text);
    msgProto.setSubject(subject);

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.send(message);

    verify(delegate)
        .makeSyncCall(
            same(environment),
            eq(MailServiceImpl.PACKAGE),
            eq("Send"),
            eq(msgProto.build().toByteArray()));
  }

  /**
   * Sets up a call to the mail service that will return an exception containing {@code code} as its
   * error code.
   *
   * @param code The error code to return.
   * @return A Message to send to the mail service.
   */
  private MailService.Message setupSendCallWithApplicationException(ErrorCode code)
      throws Exception {
    MailService.Message msg = newMessage();
    MailMessage msgProto = newMailMessage(msg);

    when(delegate.makeSyncCall(
            same(environment), eq(MailServiceImpl.PACKAGE), eq("Send"), eq(msgProto.toByteArray())))
        .thenThrow(new ApiProxy.ApplicationException(code.getNumber(), "detail"));

    return msg;
  }

  /** Tests that mail service errors translate to the correct exceptions. */
  @Test
  public void testSendWithExceptions() throws Exception {

    for (ErrorCode code :
        new ErrorCode[] {
          ErrorCode.BAD_REQUEST,
          ErrorCode.INVALID_ATTACHMENT_TYPE,
          ErrorCode.INVALID_HEADER_NAME,
          ErrorCode.UNAUTHORIZED_SENDER
        }) {
      MailService.Message msg = setupSendCallWithApplicationException(code);
      IllegalArgumentException iae =
          assertThrows(IllegalArgumentException.class, () -> service.send(msg));
      assertThat(iae).hasMessageThat().contains("detail");
    }

    MailService.Message msg1 = setupSendCallWithApplicationException(ErrorCode.INTERNAL_ERROR);
    IOException ioe1 = assertThrows(IOException.class, () -> service.send(msg1));
    assertThat(ioe1).hasMessageThat().isEqualTo("detail");

    MailService.Message msg2 = setupSendCallWithApplicationException(ErrorCode.INVALID_CONTENT_ID);
    IOException ioe2 = assertThrows(IOException.class, () -> service.send(msg2));
    assertThat(ioe2).hasMessageThat().isEqualTo("detail");
  }

  /** Tests that a message sent to admins works correctly. */
  @Test
  public void testSendToAdmins() throws Exception {
    MailService.Message msg = newMessage();
    MailMessage msgProto = newMailMessage(msg);

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.sendToAdmins(msg);

    verify(delegate)
        .makeSyncCall(
            same(environment),
            eq(MailServiceImpl.PACKAGE),
            eq("SendToAdmins"),
            eq(msgProto.toByteArray()));
  }

  /** Tests that a message created with the multi-arg constructor sent to admins works correctly. */
  @Test
  public void testSendToAdmins_multiArgConstructor() throws Exception {
    MailService.Message msg = new MailService.Message("user@example.com", null, "Subject", "body");
    MailMessage msgProto = newMailMessage(msg);

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    service.sendToAdmins(msg);

    verify(delegate)
        .makeSyncCall(
            same(environment),
            eq(MailServiceImpl.PACKAGE),
            eq("SendToAdmins"),
            eq(msgProto.toByteArray()));
  }
}
