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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

/**
 * This class implements the MailService interface using an external SMTP server.
 */
class SmtpMailServiceImpl implements MailService {
  private final EnvironmentProvider envProvider;

  /**
   * Constructor.
   * @param envProvider The provider for environment variables.
   */
  SmtpMailServiceImpl(EnvironmentProvider envProvider) {
    this.envProvider = envProvider;
  }

  @Override
  public void send(Message message) throws IOException {
    sendSmtp(message, false);
  }

  @Override
  public void sendToAdmins(Message message) throws IOException {
    sendSmtp(message, true);
  }

  private void sendSmtp(Message message, boolean toAdmin)
      throws IllegalArgumentException, IOException {
    String smtpHost = envProvider.getenv("APPENGINE_SMTP_HOST");
    if (smtpHost == null || smtpHost.isEmpty()) {
      throw new IllegalArgumentException("SMTP_HOST environment variable is not set.");
    }
    Properties props = new Properties();
    props.put("mail.smtp.host", smtpHost);
    props.put("mail.smtp.port", envProvider.getenv("APPENGINE_SMTP_PORT"));
    props.put("mail.smtp.auth", "true");
    if (Boolean.parseBoolean(envProvider.getenv("APPENGINE_SMTP_USE_TLS"))) {
      props.put("mail.smtp.starttls.enable", "true");
    }

    Session session = Session.getInstance(props, new javax.mail.Authenticator() {
      protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
        return new javax.mail.PasswordAuthentication(
            envProvider.getenv("APPENGINE_SMTP_USER"), envProvider.getenv("APPENGINE_SMTP_PASSWORD"));
      }
    });

    try {
      MimeMessage mimeMessage = new MimeMessage(session);
      mimeMessage.setFrom(new InternetAddress(message.getSender()));

      List<InternetAddress> toRecipients = new ArrayList<>();
      List<InternetAddress> ccRecipients = new ArrayList<>();
      List<InternetAddress> bccRecipients = new ArrayList<>();

      if (toAdmin) {
        String adminRecipients = envProvider.getenv("APPENGINE_ADMIN_EMAIL_RECIPIENTS");
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

      if (message.getReplyTo() != null) {
        mimeMessage.setReplyTo(new Address[] {new InternetAddress(message.getReplyTo())});
      }

      mimeMessage.setSubject(message.getSubject());

      final boolean hasAttachments = message.getAttachments() != null && !message.getAttachments().isEmpty();
      final boolean hasHtmlBody = message.getHtmlBody() != null;
      final boolean hasAmpHtmlBody = message.getAmpHtmlBody() != null;
      final boolean hasTextBody = message.getTextBody() != null;

      if (hasTextBody && !hasHtmlBody && !hasAmpHtmlBody && !hasAttachments) {
        mimeMessage.setText(message.getTextBody());
      } else {
        MimeMultipart topLevelMultipart = new MimeMultipart("mixed");

        if (hasTextBody || hasHtmlBody || hasAmpHtmlBody) {
            MimeMultipart alternativeMultipart = new MimeMultipart("alternative");
            MimeBodyPart alternativeBodyPart = new MimeBodyPart();
            alternativeBodyPart.setContent(alternativeMultipart);

            if (hasTextBody) {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(message.getTextBody());
                alternativeMultipart.addBodyPart(textPart);
            } else if (hasHtmlBody) {
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
}
