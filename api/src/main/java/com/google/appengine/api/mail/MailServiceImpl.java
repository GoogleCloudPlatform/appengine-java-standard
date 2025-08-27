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

/**
 * This class implements raw access to the mail service.
 * Applications that don't want to make use of Sun's JavaMail
 * can use it directly -- but they will forego the typing and
 * convenience methods that JavaMail provides.
 *
 */
class MailServiceImpl implements MailService {
  static final String PACKAGE = "mail";

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

  /**
   * Does the actual sending of the message.
   * @param message The message to be sent.
   * @param toAdmin Whether the message is to be sent to the admins.
   */
  private void doSend(Message message, boolean toAdmin)
      throws IllegalArgumentException, IOException {
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
