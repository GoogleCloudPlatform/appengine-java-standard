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
import java.util.Arrays;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

/**
 * The {@code MailService} provides a way for user code to send emails
 * to arbitrary destinations.
 *
 */
public interface MailService {

  public class Attachment {
    private final String fileName;
    private final byte[] data;
    private final @Nullable String contentID;

    /**
     * Attachments are an optional part of messages, but if present, all
     * information about them must be provided.
     *
     * @param fileName The attachment must have a filename associated with it.
     * The extension on that filename must be present and not blocked, or
     * there will be a failure at send time.
     *
     * @param data An array with arbitrary byte content. The array must be
     * be present, but may be of zero length.
     *
     * @throws IllegalArgumentException if either fileName or data are missing.
     */
    public Attachment (String fileName, byte[] data) {
      this(fileName, data, null);
    }

     /**
     * Attachments are an optional part of messages, but if present, all
     * information about them must be provided.
     *
     * @param fileName The attachment must have a filename associated with it.
     * The extension on that filename must be present and blocked, or
     * there will be a failure at send time.
     *
     * @param data An array with arbitrary byte content. The array must be
     * be present, but may be of zero length.
     *
     * @param contentID The attachment's content ID. May be null.
     *
     * @throws IllegalArgumentException if either fileName or data are missing.
     */
    public Attachment(String fileName, byte[] data, @Nullable String contentID) {
      if (data == null || fileName == null || fileName.length() == 0) {
        throw new IllegalArgumentException("Attachment needs content and name");
      }
      this.fileName = fileName;
      this.data = data;
      this.contentID = contentID;
    }

    /**
     * Gets the file name of this attachment.
     *
     * @return The file name of this attachment.
     */
    public String getFileName() {
      return fileName;
    }

    /**
     * Gets the content of this attachment.
     *
     * @return The raw data of this attachment.
     */
    public byte[] getData() {
      return data;
    }

    public @Nullable String getContentID() {
      return contentID;
    }
  }

  public class Header {
    private final String name;
    private final String value;

    /**
     * Headers are an optional part of messages, but if present, all
     * information about them must be provided.
     *
     * @param name The name of the header. It must be present and allowed.
     *
     * @param value The value of the header. It must be present, and it's
     * content cannot be of zero length.
     *
     * @throws IllegalArgumentException if either name or data are missing.
     */
    public Header(String name, String value) {
      if (name == null || value == null ||
          name.length() == 0 || value.length() == 0) {
        throw new IllegalArgumentException("Header needs name and value");
      }
      this.name = name;
      this.value = value;
    }

    /**
     * Gets the name of this header.
     *
     * @return The name of this header.
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the value of this header.
     *
     * @return The value of this header.
     */
    public String getValue() {
      return value;
    }
  }

  /**
   * Messages are prepared by the caller, and then submitted to the Mail service
   * for sending. Different fields are subject to different constraints, as
   * enumerated in the {@code send} and {@code sendToAdmins} methods.
   */
  public class Message {
    private String sender;
    private String replyTo;
    private Collection<String> to;
    private Collection<String> cc;
    private Collection<String> bcc;
    private String subject;
    private String textBody;
    private String htmlBody;
    private String ampHtmlBody;
    private Collection<MailService.Attachment> attachments;
    private Collection<MailService.Header> headers;

    public Message() {}

    /**
     * Convenience constructor for simple messages
     * @param sender The sender's email address.
     * @param to The recipient's email address or null for empty to address.
     * @param subject The message subject.
     * @param textBody The text body of the message.
     */
    public Message(String sender, String to, String subject, String textBody) {
      this.sender = sender;
      if (to == null) {
        this.to = Arrays.asList();
      } else {
        this.to = Arrays.asList(to);
      }
      this.subject = subject;
      this.textBody = textBody;
    }

    /**
     * Gets the sender of this message.
     *
     * @return The sender of this message.
     */
    public String getSender() {
      return sender;
    }

    /**
     * {@code sender} must correspond to the valid email address of one of
     * the admins for this application, or to the email address of the
     * currently logged-in user. Sender is really the From: field of the email.
     */
    public void setSender(String sender) {
      this.sender = sender;
    }

    /**
     * Gets the reply to field of this message.
     *
     * @return The reply to field of this message.
     */
    public String getReplyTo() {
      return replyTo;
    }

    /**
     * {@code replyTo} may be {@code null}, or must be a valid email
     * address otherwise.
     */
    public void setReplyTo(String replyTo) {
      this.replyTo = replyTo;
    }

    /**
     * Gets the recipients in the 'to' field of this message.
     *
     * @return A collection containing the 'to' field recipients.
     */
    public Collection<String> getTo() {
      return to;
    }

    /**
     * Sets the 'to' field of this message. Each string in the collection
     * represents exactly one email address. Having null (or invalid addresses)
     * will lead to eventual failure during the send process.
     * @param to A collection containing the email addresses to set as the 'to'
     * field.
     */
    public void setTo(Collection<String> to) {
      this.to = to;
    }

    /**
     * Sets the 'to' field of this message. Each string represents exactly one
     * email address. Having null (or invalid addresses) will lead to eventual
     * failure during the send process.
     *
     * @param to The email addresses to set as the 'to' field.
     */
    public void setTo(String... to) {
      this.to = Arrays.asList(to);
    }

    /**
     * Gets the recipients in the 'cc' field of this message.
     *
     * @return A collection containing the 'cc' field recipients.
     */
    public Collection<String> getCc() {
      return cc;
    }

    /**
     * Sets the 'cc' field of this message. Each string in the collection
     * represents exactly one email address. Having null (or invalid addresses)
     * will lead cc eventual failure during the send process.
     * @param cc A collection containing the email addresses cc set as the 'cc'
     * field.
     */
    public void setCc(Collection<String> cc) {
      this.cc = cc;
    }

    /**
     * Sets the 'cc' field of this message. Each string represents exactly one
     * email address. Having null (or invalid addresses) will lead cc eventual
     * failure during the send process.
     *
     * @param cc The email addresses cc set as the 'cc' field.
     */
    public void setCc(String... cc) {
      this.cc = Arrays.asList(cc);
    }

    /**
     * Gets the recipients in the 'bcc' field of this message.
     *
     * @return A collection containing the 'bcc' field recipients.
     */
    public Collection<String> getBcc() {
      return bcc;
    }

    /**
     * Sets the 'bcc' field of this message. Each string in the collection
     * represents exactly one email address. Having null (or invalid addresses)
     * will lead bcc eventual failure during the send process.
     * @param bcc A collection containing the email addresses bcc set as the
     * 'bcc' field.
     */
    public void setBcc(Collection<String> bcc) {
      this.bcc = bcc;
    }

    /**
     * Sets the 'bcc' field of this message. Each string represents exactly one
     * email address. Having null (or invalid addresses) will lead bcc eventual
     * failure during the send process.
     *
     * @param bcc The email addresses bcc set as the 'bcc' field.
     */
    public void setBcc(String... bcc) {
      this.bcc = Arrays.asList(bcc);
    }

    /**
     * Gets the subject of this message.
     *
     * @return The subject of this message.
     */
    public String getSubject() {
      return subject;
    }

    /**
     * Sets the subject of this message. A null or empty subject will lead to
     * eventual failure during the send process.
     *
     * @param subject A string containing the new subject of this message.
     */
    public void setSubject(String subject) {
      this.subject = subject;
    }

    /**
     * Gets the text body of this message.
     *
     * @return The text body.
     */
    public String getTextBody() {
      return textBody;
    }

    /**
     * Sets the text body of this message. At least one of {@code textBody} and
     * {@code htmlBody} must not be {@code null}.
     * @param textBody A string containing the new text body of this message.
     */
    public void setTextBody(String textBody) {
      this.textBody = textBody;
    }

    /**
     * Gets the html body of this message.
     *
     * @return The html body.
     */
    public String getHtmlBody() {
      return htmlBody;
    }

    /**
     * Sets the html body of this message. At least one of {@code textBody} and {@code htmlBody}
     * must not be {@code null}.
     *
     * @param htmlBody A string containing the new html body of this message.
     */
    public void setHtmlBody(String htmlBody) {
      this.htmlBody = htmlBody;
    }

    /**
     * Gets the AMP HTML body of this message. See {@link #setAmpHtmlBody} for more details.
     *
     * @return The AMP HTML body.
     */
    public String getAmpHtmlBody() {
      return ampHtmlBody;
    }

    /**
     * Sets the AMP HTML body of this message. This field is optional. Setting AMP HTML body makes
     * the email an AMP Email. Plain text or HTML may become fallback content depending on the email
     * client used.
     *
     * @param ampHtmlBody A string containing the new AMP HTML body of this message.
     */
    public void setAmpHtmlBody(String ampHtmlBody) {
      this.ampHtmlBody = ampHtmlBody;
    }

    /**
     * Gets the attachments of this message.
     *
     * @return A collection containing the attachments of this message.
     */
    public Collection<MailService.Attachment> getAttachments() {
      return attachments;
    }

    /**
     * Sets the attachments of this message. {@code attachments} may be
     * {@code null}, otherwise each attachment must have a corresponding file
     * name with an extension not on the block list.
     * @param attachments A collection of attachments.
     */
    public void setAttachments(Collection<MailService.Attachment> attachments) {
      this.attachments = attachments;
    }

    /**
     * Sets the attachments of this message. {@code attachments} may be
     * {@code null}, otherwise each attachment must have a corresponding file
     * name with an extension not on the block list.
     * @param attachments Attachments to attach to this message.
     */
    public void setAttachments(MailService.Attachment... attachments) {
      this.attachments = Arrays.asList(attachments);
    }

    /**
     * Gets the headers of this message.
     *
     * @return A collection containing the headers of this message.
     */
    public Collection<MailService.Header> getHeaders() {
      return headers;
    }

    /**
     * Sets the headers of this message. {@code headers} may be {@code null},
     * otherwise each header name must be one of the allowed names.
     * @param headers A collection of headers.
     */
    public void setHeaders(Collection<MailService.Header> headers) {
      this.headers = headers;
    }

    /**
     * Sets the headers of this message. {@code headers} may be {@code null},
     * otherwise each header name must be one of the allowed names.
     * @param headers A collection of headers.
     */
    public void setHeaders(MailService.Header... headers) {
      this.headers = Arrays.asList(headers);
    }
  }

  /**
   * Sends a mail that has been prepared in a MailService.Message.
   * <p>
   * The message will be delivered asynchronously, and delivery problems
   * will result in a bounce to the specified sender.
   * <p>
   * {@code Sender} and at least one of the collections for {@code to, cc, bcc}
   * must not be {@code null}.
   *
   * @param message The message to be sent.
   * @throws IllegalArgumentException when incorrect arguments are passed.
   * @throws IOException on internal delivery errors.
   */
  public void send(MailService.Message message) throws IOException;

  /**
   * Send an email alert to all admins of an application.
   * <p>
   * The message will be delivered asynchronously, and delivery problems
   * will result in a bounce to the admins.
   * <p>
   * The content of the {@code to, cc, bcc} fields should be {@code
   * null}.
   *
   * @param message The message to be sent.
   * @throws IllegalArgumentException when incorrect arguments are passed.
   * @throws IOException on internal delivery errors.
   */
  public void sendToAdmins(MailService.Message message) throws IOException;
}
