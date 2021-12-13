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

import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServiceFactory;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.event.TransportEvent;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;

/**
 * Implementation of the 'Google Message Transport' which really just
 * connects to the exposed MailService and uses it to deliver a message.
 * <p>
 * The special destination address "admins" results in a delivery
 * of the message to the owners of the application.
 * <p>
 * Note that most RFC822 headers are silently ignored.
 *
 *
 */
public class GMTransport extends Transport {

  private static final String FILENAME_PREVENTS_INLINING_PROPERTY =
      "appengine.mail.filenamePreventsInlining";

  private static final String SUPPORT_EXTENDED_ATTACHMENT_ENCODINGS_PROPERTY =
      "appengine.mail.supportExtendedAttachmentEncodings";

  private static final String ADMINS_ADDRESS = "admins";

  // This field has to be in sync with the values present in the method
  // InitializeHeaderAllowlist of file apphosting/api/email_message_builder.cc.
  private static final String[] HEADERS_ALLOWLIST = {
    "Auto-Submitted",
    "In-Reply-To",
    "List-Id",
    "List-Unsubscribe",
    "On-Behalf-Of",
    "References",
    "Resent-Date",
    "Resent-From",
    "Resent-To"
  };

  public GMTransport(Session session, URLName urlName) {
    super(session, urlName);
  }

  private static class SupportExtendedAttachmentEncodingsHolder {
    static final boolean INSTANCE =
        Boolean.getBoolean(SUPPORT_EXTENDED_ATTACHMENT_ENCODINGS_PROPERTY);
  }

  private static class FilenamePreventsInlininHolder {
    static final boolean INSTANCE =
        Boolean.getBoolean(FILENAME_PREVENTS_INLINING_PROPERTY);
  }

  /** {@inheritDoc} */
  @Override
  protected boolean protocolConnect(String host, int port,
      String user, String password) {
    // dummy method, our mail transport mechanism is 'connection-less'.
    return true;
  }

  private boolean canInline(Message message, BodyPart bodyPart) throws MessagingException {
    if (!FilenamePreventsInlininHolder.INSTANCE) {
      return true;
    }

    // Always allow inlining of attachments explicitly set as "multipart/alternative"
    if (message.isMimeType("multipart/alternative")) {
      return true;
    }

    // If an attachment has a filename specified, we do not inline it as multipart/alternative
    if (bodyPart instanceof MimeBodyPart) {
       MimeBodyPart mimeBodyPart = (MimeBodyPart) bodyPart;
       return mimeBodyPart.getFileName() == null || mimeBodyPart.getFileName().isEmpty();
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void sendMessage(Message message, Address[] addresses)
      throws MessagingException {
    MailService service = MailServiceFactory.getMailService();
    MailService.Message msg = new MailService.Message();

    // fill in message sender. If there is a Sender: header field we use
    // that, otherwise we use the From: field.
    String sender = null;
    if (message instanceof MimeMessage) {
      Address senderAddr = ((MimeMessage) message).getSender();
      if (senderAddr != null) {
        sender = senderAddr.toString();
      }
    }
    if (sender == null && message.getFrom() != null
        && message.getFrom().length > 0) {
      // there better be a from address, or we fail right here
      // in RFC822 it's okay to have multiple from addresses, but not for
      // Prometheus. We pick the first one only.
      sender = message.getFrom()[0].toString();
    }
    // mail_service.cc does a string compare on this. so, be precise.
    msg.setSender(sender);

    // optionally fill in reply-to header verbatim,
    // keeping multiple addresses, if present
    // the default javaMail implementation returns the From: address
    // array, if no Reply-To: header is present, which gives us a way
    // to preserve multiple from addresses
    try {
      msg.setReplyTo(Joiner.on(", ").useForNull("null").join(message.getReplyTo()));
    } catch (NullPointerException e) {
      // don't do anything, the header field will stay unset
    }

    //is this intended to go to Admins?
    boolean toAdmins = false;
    Address[] allRecipients = message.getAllRecipients();
    if (allRecipients != null) {
      for (Address addr : allRecipients) {
        if (ADMINS_ADDRESS.equals(addr.toString())) {
          toAdmins = true;
        }
      }
    }

    // fill in To:, Cc:, Bcc: header, if present.
    // Otherwise they will be (re-set) to null.
    if (!toAdmins) {
      Set<String> allAddresses = new HashSet<String>();
      for (Address addr : addresses) {
        allAddresses.add(addr.toString());
      }
      msg.setTo(convertAddressFields(message.getRecipients(RecipientType.TO), allAddresses));
      msg.setCc(convertAddressFields(message.getRecipients(RecipientType.CC), allAddresses));
      msg.setBcc(convertAddressFields(message.getRecipients(RecipientType.BCC), allAddresses));
    }

    // subject
    msg.setSubject(message.getSubject());

    //text and html bodies
    Object textObject = null;
    Object htmlObject = null;
    Object ampHtmlObject = null;
    String textType = null;
    String htmlType = null;
    String ampHtmlType = null;
    Multipart otherMessageParts = null;

    // headers
    List<MailService.Header> headers = new ArrayList<MailService.Header>();
    // The message exposes all fields as headers so we need to fetch only those
    // that are allowlisted.
    @SuppressWarnings("unchecked")
    List<Header> originalHeaders = Collections.list(message.getMatchingHeaders(HEADERS_ALLOWLIST));
    for (Header header : originalHeaders) {
      headers.add(new MailService.Header(header.getName(), header.getValue()));
    }
    msg.setHeaders(headers);

    if (message.getContentType() == null) {
      // it's not defined, and it's not a MimeMessage (which would force this
      // to be text/plain) -- treat this as a 'plain' text body.
      try {
        textObject = message.getContent();
        textType = message.getContentType();
      } catch (IOException e) {
        throw new MessagingException("Getting typeless content failed", e);
      }
    } else if (message.isMimeType("text/html")) {
      try {
        htmlObject = message.getContent();
        htmlType = message.getContentType();
      } catch (IOException e) {
        throw new MessagingException("Getting html content failed", e);
      }
    } else if (message.isMimeType("text/*")) {
      // text body (could be anything, but we treat it as plain). Otherwise,
      // we'd have to trash all but plain explicitly, which I find rude.
      try {
        textObject = message.getContent();
        textType = message.getContentType();
      } catch (IOException e) {
        throw new MessagingException("Getting text/* content failed", e);
      }
    } else if (message.isMimeType("multipart/*")) {
      // ah, now the fun starts once again. lets find a first plain and/or
      // html part to use them as the message body. we also clone the multipart
      // since we don't want to modify the original input by deleting parts.
      // this will also give you a runtime exception if the returned object
      // is not really a Multipart.
      Multipart mp;
      try {
        mp = (Multipart) message.getContent();
        for (int i = 0; i < mp.getCount(); i++) {
          BodyPart bp = mp.getBodyPart(i);
          if (bp.isMimeType("text/plain") && textObject == null && canInline(message, bp)) {
            textObject = bp.getContent();
            textType = bp.getContentType();
          } else if (bp.isMimeType("text/html") && htmlObject == null && canInline(message, bp)) {
            htmlObject = bp.getContent();
            htmlType = bp.getContentType();
          } else if (bp.isMimeType("text/x-amp-html")
              && ampHtmlObject == null
              && canInline(message, bp)) {
            ampHtmlObject = bp.getContent();
            ampHtmlType = bp.getContentType();
          } else {
            if (otherMessageParts == null) {
              String type = mp.getContentType();
              assert (type.startsWith("multipart/"));
              otherMessageParts = new MimeMultipart(
                  type.substring("multipart/".length()));
            }
            otherMessageParts.addBodyPart(bp);
          }
        }
      } catch (IOException e) {
        throw new MessagingException("Getting multipart content failed", e);
      }
    }

    if (textObject != null) {
      msg.setTextBody(convertAttachmentToString(textObject, textType));
    }

    if (htmlObject != null) {
      msg.setHtmlBody(convertAttachmentToString(htmlObject, htmlType));
    }

    if (ampHtmlObject != null) {
      msg.setAmpHtmlBody(convertAttachmentToString(ampHtmlObject, ampHtmlType));
    }

    // convert arbitrary attachments
    // fail for missing filenames
    if (otherMessageParts != null) {
      msg.setAttachments(convertAttachments(otherMessageParts));
    }

    try {
      if (toAdmins) {
        service.sendToAdmins(msg);
      } else {
        service.send(msg);
      }
    } catch (IOException e) {
      notifyTransportListeners(
          TransportEvent.MESSAGE_NOT_DELIVERED, new Address[0], addresses,
          new Address[0], message);
      throw new SendFailedException("MailService IO failed", e);
    } catch (IllegalArgumentException e) {
      throw new MessagingException("Illegal Arguments", e);
    }

    notifyTransportListeners(
        TransportEvent.MESSAGE_DELIVERED, addresses, new Address[0],
        new Address[0], message);
  }

  /**
   * Returns the attachments in 'multipart' as an ArrayList, converting String, byte[], InputStream
   * and nested attachments.
   *
   * @param multipart The input list of attachments
   * @return An ArrayList with all attachments
   * @throws MessagingException if the conversion fails due to unsupported or invalid encodings
   */
  ArrayList<MailService.Attachment> convertAttachments(Multipart multipart)
      throws MessagingException {
    ArrayList<MailService.Attachment> result = new ArrayList<>();
    convertAttachments(multipart, result);
    return result;
  }

  /**
   * Adds the attachment in "multipart" to "result", converting String, byte[], InputStream and
   * nested attachments.
   * @param multipart The input list of attachments
   * @param result The output ArrayList that the attachments get added to
   * @throws MessagingException if the conversion fails due to unsupported or invalid encodings
   */
  private void convertAttachments(Multipart multipart, List<MailService.Attachment> result)
      throws MessagingException {
    for (int i = 0; i < multipart.getCount(); i++) {
      BodyPart bp = multipart.getBodyPart(i);
      String name = bp.getFileName();
      byte[] data;
      try {
        Object o = bp.getContent();
        if (o instanceof InputStream) {
          data = inputStreamToBytes((InputStream) o);
        } else if (o instanceof String) {
          data = ((String) o).getBytes();
        } else if (SupportExtendedAttachmentEncodingsHolder.INSTANCE && o instanceof byte[]) {
          data = (byte[]) o;
        } else if (SupportExtendedAttachmentEncodingsHolder.INSTANCE && o instanceof Multipart) {
          convertAttachments((Multipart) o, result);
          continue;
        } else {
          throw new MessagingException("Converting attachment data failed");
        }
      } catch (IOException e) {
        throw new MessagingException("Extracting attachment data failed", e);
      }
      String contentID = null;
      String[] contentIDHeaders = bp.getHeader("content-id");
      if (contentIDHeaders != null) {
        contentID = contentIDHeaders[0];
      }
      MailService.Attachment attachment = new MailService.Attachment(name, data, contentID);
      result.add(attachment);
    }
  }

  // The Apache Geronimo implementation of Transport.send() puts
  // transport objects in a HashMap, so we need to implement
  // hashCode() and equals() if we want to do the right thing.

  @Override
  public int hashCode() {
    return session.hashCode() * 13 + url.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GMTransport) {
      GMTransport transport = (GMTransport) obj;
      return session.equals(transport.session) && url.equals(transport.url);
    }
    return false;
  }

  // is there a more compact way of doing this?
  /**
   * Converts an array of addresses into a collection of strings representing
   * those addresses
   * @param targetAddrs addresses to be converted
   * @param allAddrs all addresses for this transport
   * @return A collection of strings representing the intersection
   * between targetAddrs and allAddrs.
   */
  private Collection<String> convertAddressFields(Address[] targetAddrs, Set<String> allAddrs) {
    if (targetAddrs == null || targetAddrs.length == 0) {
      return null;
    }
    ArrayList<String> ourAddrs = new ArrayList<String>(targetAddrs.length);
    for (Address addr : targetAddrs) {
      String email = addr.toString();
      if (allAddrs.contains(email)) {
        ourAddrs.add(email);
      }
    }
    return ourAddrs;
  }

  /**
   * Gets all the available data in a String, a byte[] or an InputStream and returns it as a String
   * using the character set specified in the type parameter.
   *
   * @param attachmentData The string, byte array or input stream to be read.
   * @param type The encoding type of the data.
   * @return A String containing the data.
   * @throws MessagingException if the data type of attachmentData is unsupported
   */
  private String convertAttachmentToString(Object attachmentData, String type)
      throws MessagingException {
    String charset = null;
    String[] args = type.split(";");
    for (String arg : args) {
      if (arg.trim().startsWith("charset=")) {
        charset = arg.split("=")[1];
        break;
      }
    }

    try {
      byte[] attachmentBytes = null;

      if (attachmentData instanceof String) {
        return (String) attachmentData;
      } else if (SupportExtendedAttachmentEncodingsHolder.INSTANCE
                 && attachmentData instanceof byte[]) {
        attachmentBytes = (byte[]) attachmentData;
      } else if (attachmentData instanceof InputStream) {
        attachmentBytes = inputStreamToBytes((InputStream) attachmentData);
      } else {
        throw new MessagingException("Converting body of type " + type + " failed");
      }

      if (charset != null) {
        return new String(attachmentBytes, charset);
      } else {
        return new String(attachmentBytes);
      }
    } catch (UnsupportedEncodingException e) {
      throw new MessagingException("Unsupported charset: " + charset, e);
    } catch (IOException e) {
      throw new MessagingException("Stringifying body of type " + type + " failed", e);
    }
  }

  /**
   * Gets all the available data in an InputStream and returns it as a byte
   * array.
   * @param in The input stream to be read.
   * @return A byte array containing the data.
   * @throws IOException If there is a problem with the input stream.
   */
  private byte[] inputStreamToBytes(InputStream in) throws IOException {
    byte[] bytes = new byte[in.available()];
    /* int count = */ in.read(bytes);
    return bytes;
  }
}
