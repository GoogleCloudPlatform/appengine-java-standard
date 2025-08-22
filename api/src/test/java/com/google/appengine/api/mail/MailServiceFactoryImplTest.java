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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MailServiceFactoryImplTest {

  @Mock private EnvironmentProvider envProvider;

  @Test
  public void testGetMailService_smtp() {
    when(envProvider.getenv("APPENGINE_USE_SMTP_MAIL_SERVICE")).thenReturn("true");
    when(envProvider.getenv("APPENGINE_SMTP_HOST")).thenReturn("smtp.example.com");
    when(envProvider.getenv("APPENGINE_SMTP_PORT")).thenReturn("587");
    when(envProvider.getenv("APPENGINE_SMTP_USER")).thenReturn("user");
    when(envProvider.getenv("APPENGINE_SMTP_PASSWORD")).thenReturn("password");
    when(envProvider.getenv("APPENGINE_SMTP_USE_TLS")).thenReturn("true");
    MailServiceFactoryImpl factory = new MailServiceFactoryImpl(envProvider);
    assertTrue(factory.getMailService() instanceof SmtpMailServiceImpl);
  }

  @Test
  public void testGetMailService_legacy() {
    when(envProvider.getenv("APPENGINE_USE_SMTP_MAIL_SERVICE")).thenReturn("false");
    MailServiceFactoryImpl factory = new MailServiceFactoryImpl(envProvider);
    assertTrue(factory.getMailService() instanceof MailServiceImpl);
  }

  @Test
  public void testGetMailService_legacy_null() {
    when(envProvider.getenv("APPENGINE_USE_SMTP_MAIL_SERVICE")).thenReturn(null);
    MailServiceFactoryImpl factory = new MailServiceFactoryImpl(envProvider);
    assertTrue(factory.getMailService() instanceof MailServiceImpl);
  }
}
