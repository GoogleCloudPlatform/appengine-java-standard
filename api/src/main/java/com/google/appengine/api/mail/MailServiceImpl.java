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

import com.google.appengine.api.mail.MailServicePb.MailAttachment;
import com.google.appengine.api.mail.MailServicePb.MailHeader;
import com.google.appengine.api.mail.MailServicePb.MailMessage;
import com.google.appengine.api.mail.MailServicePb.MailServiceError.ErrorCode;
import com.google.apphosting.api.ApiProxy;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

/**
 * This class implements raw access to the mail service.
 * Applications that don't want to make use of Sun's JavaMail
 * can use it directly -- but they will forego the typing and
 * convenience methods that JavaMail provides.
 *
 */
class MailServiceImpl implements MailService {
  static final String PACKAGE = "mail";
  private final SystemEnvironmentProvider envProvider;

  /** Default constructor, used in production. */
  MailServiceImpl() {
    this(new SystemEnvironmentProvider());
  }

  /** Constructor for testing, allowing a mock environment provider. */
  MailServiceImpl(SystemEnvironmentProvider envProvider) {
    this.envProvider = envProvider;
  }

  /** {@inheritDoc} */
  @Override
  public void sendToAdmins(Message message)
      throws IllegalArgumentException, IOException {
    doSend(message, true);
  }

  /** {@inheritDoc} */
  @Override
  public void send(Message message)
      throws IllegalArgumentException, IOException {
    doSend(message, false);
  }

  private void sendSmtp(Message message, boolean toAdmin)
      throws IllegalArgumentException, IOException {
    String smtpHost = envProvider.getenv("SMTP_HOST");
    if (smtpHost == null || smtpHost.isEmpty()) {
      throw new IllegalArgumentException("SMTP_HOST environment variable is not set.");
    }
    Properties props = new Properties();
    props.put("mail.smtp.host", smtpHost);
    props.put("mail.smtp.port", envProvider.getenv("SMTP_PORT"));
    props.put("mail.smtp.auth", "true");
    if (Boolean.parseBoolean(envProvider.getenv("SMTP_USE_TLS"))) {
      props.put("mail.smtp.starttls.enable", "true");
    }

    Session session = Session.getInstance(props, new javax.mail.Authenticator() {
      protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
        return new javax.mail.PasswordAuthentication(
            envProvider.getenv("SMTP_USER"), envProvider.getenv("SMTP_PASSWORD"));
      }
    });

    try {
      MimeMessage mimeMessage = new MimeMessage(session);
      mimeMessage.setFrom(new InternetAddress(message.getSender()));

      List<InternetAddress> toRecipients = new ArrayList<>();
      List<InternetAddress> ccRecipients = new ArrayList<>();
      List<InternetAddress> bccRecipients = new ArrayList<>();

      if (toAdmin) {
        String adminRecipients = envProvider.getenv("ADMIN_EMAIL_RECIPIENTS");
        if (adminRecipients == null || adminRecipients.isEmpty()) {
          throw new IllegalArgumentException("Admin recipients not configured.");
        }
        toRecipients.addAll(Arrays.asList(InternetAddress.parse(adminRecipients)));
      } else {
        if (message.getTo() != null) {
          toRecipients.addAll(toInternetAddressList(message.getTo()));
        }
        if (message.getCc() != null) {
          ccRecipients.addAll(toInternetAddressList(message.getCc()));
        }
        if (message.getBcc() != null) {
          bccRecipients.addAll(toInternetAddressList(message.getBcc()));
        }
      }

      List<Address> allTransportRecipients = new ArrayList<>();
      allTransportRecipients.addAll(toRecipients);
      allTransportRecipients.addAll(ccRecipients);
      allTransportRecipients.addAll(bccRecipients);

      if (allTransportRecipients.isEmpty()) {
        throw new IllegalArgumentException("No recipients specified.");
      }

      if (!toRecipients.isEmpty()) {
        mimeMessage.setRecipients(RecipientType.TO, toRecipients.toArray(new Address[0]));
      }
      if (!ccRecipients.isEmpty()) {
        mimeMessage.setRecipients(RecipientType.CC, ccRecipients.toArray(new Address[0]));
      }
      // Bcc recipients are not set on the MimeMessage to prevent them from being in the headers.

      if (message.getReplyTo() != null) {
        mimeMessage.setReplyTo(new Address[] {new InternetAddress(message.getReplyTo())});
      }

      mimeMessage.setSubject(message.getSubject());

      final boolean hasAttachments = message.getAttachments() != null && !message.getAttachments().isEmpty();
      final boolean hasHtmlBody = message.getHtmlBody() != null;
      final boolean hasAmpHtmlBody = message.getAmpHtmlBody() != null;
      final boolean hasTextBody = message.getTextBody() != null;

      // Case 1: Plain text only, no attachments. Simplest case.
      if (hasTextBody && !hasHtmlBody && !hasAmpHtmlBody && !hasAttachments) {
        mimeMessage.setText(message.getTextBody());
      } else {
        // Case 2: Anything more complex requires multipart.
        MimeMultipart topLevelMultipart = new MimeMultipart("mixed");

        // The bodies (text, html, amp) are grouped in a "multipart/alternative"
        if (hasTextBody || hasHtmlBody || hasAmpHtmlBody) {
            MimeMultipart alternativeMultipart = new MimeMultipart("alternative");
            MimeBodyPart alternativeBodyPart = new MimeBodyPart();
            alternativeBodyPart.setContent(alternativeMultipart);

            if (hasTextBody) {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(message.getTextBody());
                alternativeMultipart.addBodyPart(textPart);
            } else if (hasHtmlBody) {
                // If there is an HTML body but no text body, add an empty text part for compatibility.
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText("");
                alternativeMultipart.addBodyPart(textPart);
            }

            if (hasHtmlBody) {
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(message.getHtmlBody(), "text/html");
                alternativeMultipart.addBodyPart(htmlPart);
            }
            if (hasAmpHtmlBody) {
                MimeBodyPart ampPart = new MimeBodyPart();
                ampPart.setContent(message.getAmpHtmlBody(), "text/x-amp-html");
                alternativeMultipart.addBodyPart(ampPart);
            }
            topLevelMultipart.addBodyPart(alternativeBodyPart);
        }

        // Add attachments to the top-level mixed part.
        if (hasAttachments) {
          for (Attachment attachment : message.getAttachments()) {
            MimeBodyPart attachmentBodyPart = new MimeBodyPart();
            DataSource source =
                new ByteArrayDataSource(attachment.getData(), "application/octet-stream");
            attachmentBodyPart.setDataHandler(new DataHandler(source));
            attachmentBodyPart.setFileName(attachment.getFileName());
            if (attachment.getContentID() != null) {
              attachmentBodyPart.setContentID(attachment.getContentID());
            }
            topLevelMultipart.addBodyPart(attachmentBodyPart);
          }
        }
        mimeMessage.setContent(topLevelMultipart);
      }

      if (message.getHeaders() != null) {
        for (Header header : message.getHeaders()) {
          mimeMessage.addHeader(header.getName(), header.getValue());
        }
      }

      // Update headers to match content, e.g., setting the Content-Type
      mimeMessage.saveChanges();

      Transport transport = session.getTransport("smtp");
      try {
        transport.connect();
        transport.sendMessage(mimeMessage, allTransportRecipients.toArray(new Address[0]));
      } finally {
        if (transport != null) {
          transport.close();
        }
      }

    } catch (MessagingException e) {
      if (e instanceof javax.mail.AuthenticationFailedException) {
        throw new IllegalArgumentException("SMTP authentication failed: " + e.getMessage(), e);
      }
      throw new IOException("Error sending email via SMTP: " + e.getMessage(), e);
    }
  }

  private List<InternetAddress> toInternetAddressList(Collection<String> addresses)
      throws AddressException {
    List<InternetAddress> list = new ArrayList<>();
    for (String address : addresses) {
      list.add(new InternetAddress(address));
    }
    return list;
  }

  /**
   * Does the actual sending of the message.
   * @param message The message to be sent.
   * @param toAdmin Whether the message is to be sent to the admins.
   */
  private void doSend(Message message, boolean toAdmin)
      throws IllegalArgumentException, IOException {
    if ("true".equals(envProvider.getenv("USE_SMTP_MAIL_SERVICE"))) {
      sendSmtp(message, toAdmin);
      return;
    }
    // Could perform basic checks to save on RPCs in case of missing args etc.
    // I'm not doing this on purpose, to make sure the semantics of the two
    // implementations stay the same.
    // The benefit of not doing basic checks here is that we will pick up
    // changes in semantics (ie from address can now also be the logged-in user)
    // for free.

    MailMessage.Builder msgProto = MailMessage.newBuilder();
    if (message.getSender() != null) {
      msgProto.setSender(message.getSender());
    }
    if (message.getTo() != null) {
      msgProto.addAllTo(message.getTo());
    }
    if (message.getCc() != null) {
      msgProto.addAllCc(message.getCc());
    }
    if (message.getBcc() != null) {
      msgProto.addAllBcc(message.getBcc());
    }
    if (message.getReplyTo() != null) {
      msgProto.setReplyTo(message.getReplyTo());
    }
    if (message.getSubject() != null) {
      msgProto.setSubject(message.getSubject());
    }
    if (message.getTextBody() != null) {
      msgProto.setTextBody(message.getTextBody());
    }
    if (message.getHtmlBody() != null) {
      msgProto.setHtmlBody(message.getHtmlBody());
    }
    if (message.getAmpHtmlBody() != null) {
      msgProto.setAmpHtmlBody(message.getAmpHtmlBody());
    }
    if (message.getAttachments() != null) {
      for (Attachment attach : message.getAttachments()) {
        MailAttachment.Builder attachProto = MailAttachment.newBuilder();
        attachProto.setFileName(attach.getFileName());
        attachProto.setData(ByteString.copyFrom(attach.getData()));
        String contentId = attach.getContentID();
        if (contentId != null) {
          attachProto.setContentID(contentId);
        }
        msgProto.addAttachment(attachProto);
      }
    }
    if (message.getHeaders() != null) {
      for (Header header : message.getHeaders()) {
        msgProto.addHeader(
            MailHeader.newBuilder().setName(header.getName()).setValue(header.getValue()));
      }
    }

    byte[] msgBytes = msgProto.buildPartial().toByteArray();
    try {
      // Returns VoidProto -- just ignore the return value.
      if (toAdmin) {
        ApiProxy.makeSyncCall(PACKAGE, "SendToAdmins", msgBytes);
      } else {
        ApiProxy.makeSyncCall(PACKAGE, "Send", msgBytes);
      }
    } catch (ApiProxy.ApplicationException ex) {
      // Pass all the error details straight through (same as python).
      switch (ErrorCode.forNumber(ex.getApplicationError())) {
        case BAD_REQUEST:
          throw new IllegalArgumentException("Bad Request: " +
                                             ex.getErrorDetail());
        case UNAUTHORIZED_SENDER:
          throw new IllegalArgumentException("Unauthorized Sender: " +
                                             ex.getErrorDetail());
        case INVALID_ATTACHMENT_TYPE:
          throw new IllegalArgumentException("Invalid Attachment Type: " +
                                             ex.getErrorDetail());
        case INVALID_HEADER_NAME:
          throw new IllegalArgumentException("Invalid Header Name: " +
                                             ex.getErrorDetail());
        case INTERNAL_ERROR:
        default:
          throw new IOException(ex.getErrorDetail());
      }
    }
  }
}
