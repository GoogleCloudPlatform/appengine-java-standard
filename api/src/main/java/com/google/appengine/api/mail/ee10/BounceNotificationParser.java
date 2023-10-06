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

package com.google.appengine.api.mail.ee10;

import com.google.appengine.api.mail.BounceNotification;
import com.google.appengine.api.utils.ee10.HttpRequestParser;
import java.io.IOException;
import java.util.Properties;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The {@code BounceNotificationParser} parses an incoming HTTP request into
 * a description of a bounce notification.
 *
 */
public final class BounceNotificationParser extends HttpRequestParser {
  /**
   * Parse the POST data of the given request to get details about the bounce notification.
   *
   * @param request The {@link HttpServletRequest} whose POST data should be parsed.
   * @return a BounceNotification
   * @throws IOException
   * @throws MessagingException
   */
  public static BounceNotification parse(HttpServletRequest request)
      throws IOException, MessagingException {
    MimeMultipart multipart = parseMultipartRequest(request);

    BounceNotification.DetailsBuilder originalDetailsBuilder = null;
    BounceNotification.DetailsBuilder notificationDetailsBuilder = null;
    BounceNotification.BounceNotificationBuilder bounceNotificationBuilder =
        new BounceNotification.BounceNotificationBuilder();
    int parts = multipart.getCount();
    for (int i = 0; i < parts; i++) {
      BodyPart part = multipart.getBodyPart(i);
      String fieldName = getFieldName(part);
      if ("raw-message".equals(fieldName)) {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session, part.getInputStream());
        bounceNotificationBuilder.withRawMessage(message);
      } else {
        String[] subFields = fieldName.split("-");
        BounceNotification.DetailsBuilder detailsBuilder = null;
        if ("original".equals(subFields[0])) {
          if (originalDetailsBuilder == null) {
            originalDetailsBuilder = new BounceNotification.DetailsBuilder();
          }
          detailsBuilder = originalDetailsBuilder;
        } else if ("notification".equals(subFields[0])) {
          if (notificationDetailsBuilder == null) {
            notificationDetailsBuilder = new BounceNotification.DetailsBuilder();
          }
          detailsBuilder = notificationDetailsBuilder;
        }
        if (detailsBuilder != null) {
          String field = subFields[1];
          String value = getTextContent(part);
          if ("to".equals(field)) {
            detailsBuilder.withTo(value);
          } else if ("from".equals(field)) {
            detailsBuilder.withFrom(value);
          } else if ("subject".equals(field)) {
            detailsBuilder.withSubject(value);
          } else if ("text".equals(field)) {
            detailsBuilder.withText(value);
          } else if ("cc".equals(field)) {
            detailsBuilder.withCc(value);
          } else if ("bcc".equals(field)) {
            detailsBuilder.withBcc(value);
          }
        }
      }
    }

    if (originalDetailsBuilder != null) {
      bounceNotificationBuilder.withOriginal(originalDetailsBuilder.build());
    }
    if (notificationDetailsBuilder != null) {
      bounceNotificationBuilder.withNotification(notificationDetailsBuilder.build());
    }
    return bounceNotificationBuilder.build();
  }
}
