package com.google.appengine.api.mail;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MailServiceFactoryImplTest {

  @Mock private EnvironmentProvider envProvider;

  @Test
  public void testGetMailService_smtp() {
    when(envProvider.getenv("APPENGINE_USE_SMTP_MAIL_SERVICE")).thenReturn("true");
    MailServiceFactoryImpl factory = new MailServiceFactoryImpl(envProvider);
    assertTrue(factory.getMailService() instanceof SmtpMailServiceImpl);
  }

  @Test
  public void testGetMailService_legacy() {
    when(envProvider.getenv("APPENGINE_USE_SMTP_MAIL_SERVICE")).thenReturn("false");
    MailServiceFactoryImpl factory = new MailServiceFactoryImpl(envProvider);
    assertTrue(factory.getMailService() instanceof LegacyMailServiceImpl);
  }

  @Test
  public void testGetMailService_legacy_null() {
    when(envProvider.getenv("APPENGINE_USE_SMTP_MAIL_SERVICE")).thenReturn(null);
    MailServiceFactoryImpl factory = new MailServiceFactoryImpl(envProvider);
    assertTrue(factory.getMailService() instanceof LegacyMailServiceImpl);
  }
}
