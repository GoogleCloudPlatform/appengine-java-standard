/*
 * Copyright 2025 Google LLC
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

/**
 * Factory for creating a {@link MailService}.
 */
final class MailServiceFactoryImpl implements IMailServiceFactory {

  private static final String APPENGINE_USE_SMTP_MAIL_SERVICE_ENV = "APPENGINE_USE_SMTP_MAIL_SERVICE";
  private final EnvironmentProvider envProvider;

  MailServiceFactoryImpl() {
    this(new SystemEnvironmentProvider());
  }

  // For testing
  MailServiceFactoryImpl(EnvironmentProvider envProvider) {
    this.envProvider = envProvider;
  }

  @Override
  @SuppressWarnings("YodaCondition")
  public MailService getMailService() {
    if ("true".equals(envProvider.getenv(APPENGINE_USE_SMTP_MAIL_SERVICE_ENV))) {
      return new SmtpMailServiceImpl(envProvider);
    }
    return new MailServiceImpl();
  }
}
