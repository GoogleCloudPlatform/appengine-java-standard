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

/**
 * Provides a service to send email messages on behalf of administrators or authenticated users,
 * also accessible via a <a
 * href="http://www.oracle.com/technetwork/java/javamail/index.html">JavaMail</a> interface.
 * Receiving messages is not supported via the JavaMail API but is supported via <a
 * href="http://cloud.google.com/appengine/docs/java/mail/receiving.html">an HTTP interface</a>.
 *
 * <p>This low-level API is intended primarily for framework authors. For application developers we
 * provide a custom {@code javax.mail.Transport} that allows the standard {@code javax.mail}
 * interface to be used to send emails. No special configuration is required to send emails via this
 * interface.
 *
 * <p>The {@link MailService.Message} class represents a message, including sender and recipient
 * information, and possibly attachments as {@link MailService.Attachment} objects. These can be
 * independently created via their respective constructors.
 *
 * <p>Sending a message requires a {@link MailService} object, created by the {@link
 * MailServiceFactory}. Messages are sent asynchronously, so the {@code MailService} methods will
 * always succeed immediately. Any errors in the mail message will be returned to the sender's
 * address as "bounce" messages.
 *
 * @see com.google.appengine.api.mail.MailService
 * @see <a href="http://cloud.google.com/appengine/docs/java/mail/">The Mail Java API in the
 *     <em>Google App Engine Developer's Guide</em></a>
 * @see <a href="http://www.oracle.com/technetwork/java/javamail/index.html">JavaMail API</a>
 */
package com.google.appengine.api.mail;
