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

import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServicePb;
import com.google.appengine.api.mail.MailServicePb.MailAttachment;
import com.google.appengine.api.mail.MailServicePb.MailServiceError;
import com.google.appengine.api.mail.MailStubServicePb;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.proto2api.ApiBasePb;
import com.google.auto.service.AutoService;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stub implementation of the Google App Engine mail api.
 * This implementation logs messages using a {@link Logger} associated with
 * this class and keeps messages that were sent in memory.  If you want to
 * access the list of sent messages you can get ahold of the registered
 * LocalMailService instance as follows:
 *
 * <blockquote>
 * <pre>
 * ApiProxyLocal proxy = (ApiProxyLocal) ApiProxy.getDelegate();
 * LocalMailService mailService =
 *     (LocalMailService) proxy.getService(LocalMailService.PACKAGE);
 * </pre>
 * </blockquote>
 *
 * You can then access the list via {@link #getSentMessages()} and clear the
 * list via {@link #clearSentMessages()}.
 *
 * By default, messages are logged at {@link Level#INFO} and the body of the
 * message is excluded.  The log level and whether or not the body of the
 * message is logged can be configured.  See {@link #LOG_MAIL_BODY_PROPERTY}
 * and {@link #LOG_MAIL_LEVEL_PROPERTY} for more information.
 *
 */
@AutoService(LocalRpcService.class)
public final class LocalMailService extends AbstractLocalRpcService {

  // TODO: Provide an implementation that delegates to javax.mail so that
  // we can actually send mail in the local runtime.

  /** The package name for this service. */
  public static final String PACKAGE = "mail";

  /**
   * Init property that determines whether or not we log the body of the email.
   * Value must be a string representation of either {@link Boolean#TRUE} or
   * {@link Boolean#FALSE}.
   */
  public static final String LOG_MAIL_BODY_PROPERTY = "mail.log_mail_body";
  static final boolean DEFAULT_LOG_MAIL_BODY = false;

  /**
   * Init property that specifies the {@link Level} at which we log mail
   * messages.  Value must be a string representation of a {@link Level}
   * (calling {@link Level#parse(String)} with the value as the arg should
   * return a valid instance).
   */
  public static final String LOG_MAIL_LEVEL_PROPERTY = "mail.log_mail_level";
  static final Level DEFAULT_LOG_MAIL_LEVEL = Level.INFO;

  // Visible for testing
  boolean logMailBody = DEFAULT_LOG_MAIL_BODY;
  // Visible for testing
  Level logMailLevel = DEFAULT_LOG_MAIL_LEVEL;
  // Visible for testing
  Logger logger = Logger.getLogger(getClass().getName());

  // Visible for testing
  static final ImmutableSet<String> DENYLIST =
      ImmutableSet.of(
          // N.B. Keep synchronized with //depot/google3/apphosting/api/email_message_builder.cc
          "ade",
          "adp",
          "bat",
          "chm",
          "cmd",
          "com",
          "cpl",
          "exe",
          "hta",
          "ins",
          "isp",
          "jse",
          "lib",
          "mde",
          "msc",
          "msp",
          "mst",
          "pif",
          "scr",
          "sct",
          "shb",
          "sys",
          "vb",
          "vbe",
          "vbs",
          "vxd",
          "wsc",
          "wsf",
          "wsh");
  // N.B. denylisting of ".zip" and ".gzip" are optional in email_message_builder

  /** All messages that have been sent. */
  private final List<MailServicePb.MailMessage> sentMessages =
      Collections.synchronizedList(new ArrayList<>());

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    String logMailBodyStr = properties.get(LOG_MAIL_BODY_PROPERTY);
    if (logMailBodyStr != null) {
      logMailBody = Boolean.parseBoolean(logMailBodyStr);
    } else {
      logMailBody = DEFAULT_LOG_MAIL_BODY;
    }

    String logLevelStr = properties.get(LOG_MAIL_LEVEL_PROPERTY);
    if (logLevelStr != null) {
      logMailLevel = Level.parse(logLevelStr);
    } else {
      logMailLevel = DEFAULT_LOG_MAIL_LEVEL;
    }
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    clearSentMessages();
  }

  public ApiBasePb.VoidProto send(Status status, MailServicePb.MailMessage msg) {
    logMailMessage("send", msg);
    checkAttachments(msg);
    sentMessages.add(msg);
    return ApiBasePb.VoidProto.getDefaultInstance();
  }

  public ApiBasePb.VoidProto sendToAdmins(Status status, MailServicePb.MailMessage msg) {
    logMailMessage("sendToAdmins", msg);
    checkAttachments(msg);
    sentMessages.add(msg);
    return ApiBasePb.VoidProto.getDefaultInstance();
  }

  private void checkAttachments(MailServicePb.MailMessage msg) {
    for (MailAttachment attachment : msg.getAttachmentList()) {
      checkAttachmentFileName(attachment.getFileName());
    }
  }

  private void checkAttachmentFileName(String fileName) {
    final String invalidTypeMsg = "Invalid attachment type";
    fileName = Ascii.toLowerCase(fileName).trim();
    if (fileName.startsWith(".")) {
      // throw new IllegalArgumentException(invalidAttachmentTypeMsg);
      throw new ApiProxy.ApplicationException(
          MailServiceError.ErrorCode.INVALID_ATTACHMENT_TYPE.getNumber(), invalidTypeMsg);
    }
    final int extensionStart = fileName.lastIndexOf('.');
    if (extensionStart == -1) {
      throw new ApiProxy.ApplicationException(
          MailServiceError.ErrorCode.INVALID_ATTACHMENT_TYPE.getNumber(), invalidTypeMsg);
    }
    String extension = fileName.substring(extensionStart + 1);
    if (DENYLIST.contains(extension)) {
      throw new ApiProxy.ApplicationException(
          MailServiceError.ErrorCode.INVALID_ATTACHMENT_TYPE.getNumber(), invalidTypeMsg);
    }
  }

  private void log(String logMsg) {
    logger.log(logMailLevel, logMsg);
  }

  // Visible for testing
  void logMailMessage(String method, MailServicePb.MailMessage msg) {
    log(String.format("%s.%s", MailService.class.getSimpleName(), method));
    log(String.format("  From: %s", msg.getSender()));

    for (String to : msg.getToList()) {
      log(String.format("  To: %s", to));
    }

    for (String cc : msg.getCcList()) {
      log(String.format("  Cc: %s", cc));
    }

    for (String bcc : msg.getBccList()) {
      log(String.format("  Bcc: %s", bcc));
    }

    if (msg.hasReplyTo()) {
      log(String.format("  Reply-to: %s", msg.getReplyTo()));
    }

    log(String.format("  Subject: %s", msg.getSubject()));

    if (msg.hasTextBody()) {
      log("  Body:");
      log("    Content-type: text/plain");
      log(String.format("    Data length: %d", msg.getTextBody().length()));
      if (logMailBody) {
        log(String.format("-----\n%s\n-----", msg.getTextBody()));
      }
    }

    if (msg.hasAmpHtmlBody()) {
      log("  Body:");
      log("    Content-type: text/x-amp-html");
      log(String.format("    Data length: %d", msg.getAmpHtmlBody().length()));
      if (logMailBody) {
        log(String.format("-----\n%s\n-----", msg.getAmpHtmlBody()));
      }
    }

    if (msg.hasHtmlBody()) {
      log("  Body:");
      log("    Content-type: text/html");
      log(String.format("    Data length: %d", msg.getHtmlBody().length()));
      if (logMailBody) {
        log(String.format("-----\n%s\n-----", msg.getHtmlBody()));
      }
    }

    for (MailServicePb.MailAttachment attachment : msg.getAttachmentList()) {
      log("  Attachment:");
      log(String.format("    File name: %s", attachment.getFileName()));
      log(String.format("    Data length: %d", attachment.getData().size()));
    }
  }

  /**
   * @return A list of all messages that have been sent.
   */
  public List<MailServicePb.MailMessage> getSentMessages() {
    return getSentMessagesInternal();
  }

  private List<MailServicePb.MailMessage> getSentMessagesInternal() {
    return new ArrayList<>(sentMessages);
  }

  /**
   * Clear the list of sent messages.
   */
  public void clearSentMessages() {
    clearSentMessagesInternal();
  }

  private void clearSentMessagesInternal() {
    sentMessages.clear();
  }

  MailStubServicePb.GetLogMailBodyResponse getLogMailBody() {
    return MailStubServicePb.GetLogMailBodyResponse.newBuilder()
        .setLogMailBody(logMailBody)
        .build();
  }

  Level getLogMailLevel() {
    return logMailLevel;
  }

  @Override
  public Integer getMaxApiRequestSize() {
    // Keep this in sync with MAX_REQUEST_SIZE in <internal6>.
    return 32 << 20;  // 32 MB
  }
}
