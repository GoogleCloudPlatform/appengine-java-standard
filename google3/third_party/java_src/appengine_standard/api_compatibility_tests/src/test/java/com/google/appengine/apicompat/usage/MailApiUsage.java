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
package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.mail.BounceNotification;
import com.google.appengine.api.mail.BounceNotificationParser;
import com.google.appengine.api.mail.IMailServiceFactory;
import com.google.appengine.api.mail.IMailServiceFactoryProvider;
import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServiceFactory;
import com.google.appengine.api.mail.stdimpl.GMTransport;
import com.google.appengine.api.utils.HttpRequestParser;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.apicompat.UsageTracker;
import com.google.appengine.spi.FactoryProvider;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Service;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

/** Exhaustive usage of the Mail Api. Used for backward compatibility checks. */
public class MailApiUsage {

  /**
   * Exhaustive use of {@link MailServiceFactory}.
   */
  public static class MailServiceFactoryUsage extends ExhaustiveApiUsage<MailServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      MailServiceFactory unused = new MailServiceFactory(); // ugh
      MailServiceFactory.getMailService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IMailServiceFactory}.
   */
  public static class IMailServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IMailServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IMailServiceFactory iMailServiceFactory) {
      iMailServiceFactory.getMailService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IMailServiceFactoryProvider}.
   */
  public static class IMailServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IMailServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IMailServiceFactoryProvider unused = new IMailServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link MailService}.
   */
  public static class MailServiceUsage extends ExhaustiveApiInterfaceUsage<MailService> {

    @Override
    protected Set<Class<?>> useApi(MailService mailSvc) {
      MailService.Message msg = null;
      try {
        mailSvc.send(msg);
      } catch (IOException e) {
        // ok
      }
      try {
        mailSvc.sendToAdmins(msg);
      } catch (IOException e) {
        // ok
      }
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link MailService.Message}.
   */
  public static class MessageUsage extends ExhaustiveApiUsage<MailService.Message> {

    @Override
    public Set<Class<?>> useApi() {
      MailService.Message msg = new MailService.Message();
      msg = new MailService.Message("sender", "to", "subj", "body");
      Collection<MailService.Attachment> attachments = msg.getAttachments();
      Collection<String> strColl = msg.getBcc();
      strColl = msg.getCc();
      strColl = msg.getTo();
      Collection<MailService.Header> headers = msg.getHeaders();
      msg.getHtmlBody();
      msg.getAmpHtmlBody();
      msg.getSender();
      msg.getSubject();
      msg.getReplyTo();
      msg.getTextBody();
      MailService.Attachment attachment1 =
          new MailService.Attachment("yar", "bytes".getBytes(UTF_8));
      MailService.Attachment attachment2 =
          new MailService.Attachment("yar", "bytes".getBytes(UTF_8), "<yar>");
      attachments = Arrays.asList(attachment1, attachment2);
      msg.setAttachments(attachments);
      msg.setAttachments(attachment1, attachment2);
      msg.setBcc("bcc1", "bcc2");
      msg.setBcc(strColl);
      msg.setCc("cc1", "cc2");
      msg.setCc(strColl);
      msg.setTo("to1", "to2");
      msg.setTo(strColl);
      msg.setHeaders(headers);
      MailService.Header header1 = new MailService.Header("yam", "yar");
      MailService.Header header2 = new MailService.Header("yam", "yar");
      msg.setHeaders(header1, header2);
      msg.setHtmlBody("body");
      msg.setAmpHtmlBody("<html ⚡4email></html>");
      msg.setReplyTo("reply to");
      msg.setSender("sender");
      msg.setSubject("subj");
      msg.setTextBody("text body");
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link MailService.Attachment}.
   */
  public static class AttachmentUsage extends ExhaustiveApiUsage<MailService.Attachment> {

    @Override
    public Set<Class<?>> useApi() {
      MailService.Attachment attachment =
          new MailService.Attachment("file name", "bytes".getBytes(UTF_8));
      MailService.Attachment attachmentContentID =
          new MailService.Attachment("file name", "bytes".getBytes(UTF_8), "<contentid>");
      attachment.getData();
      attachment.getFileName();
      attachmentContentID.getContentID();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link MailService.Header}.
   */
  public static class HeaderUsage extends ExhaustiveApiUsage<MailService.Header> {

    @Override
    public Set<Class<?>> useApi() {
      MailService.Header header = new MailService.Header("this", "that");
      header.getName();
      header.getValue();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link GMTransport}.
   */
  public static class GMTransportUsage extends ExhaustiveApiUsage<GMTransport> {

    static class MyTransport extends GMTransport {

      @UsageTracker.DoNotTrackConstructorInvocation
      MyTransport(Session session, URLName urlName) {
        super(session, urlName);
        protocolConnect("host", 0, "me", "you");
      }
    }

    @Override
    public Set<Class<?>> useApi() {
      Session session = Session.getDefaultInstance(new Properties());
      URLName urlName = new URLName("this");
      GMTransport transport = new MyTransport(session, urlName);
      boolean unusedEquals = transport.equals(transport);
      int unusedHashCode = transport.hashCode();
      javax.mail.Message msg = new MimeMessage(session);
      Address[] addresses;
      try {
        addresses = new Address[] {new InternetAddress("yar")};
      } catch (AddressException e) {
        throw new RuntimeException(e);
      }
      try {
        transport.sendMessage(msg, addresses);
      } catch (MessagingException e) {
        // ok
      }
      return classes(Object.class, Transport.class, Service.class);
    }
  }

  /**
   * Exhaustive use of {@link BounceNotification}.
   */
  public static class BounceNotificationUsage extends ExhaustiveApiUsage<BounceNotification> {

    @Override
    public Set<Class<?>> useApi() {
      Constructor<BounceNotification> ctor;
      try {
        ctor = BounceNotification.class.getDeclaredConstructor(
            MimeMessage.class, BounceNotification.Details.class, BounceNotification.Details.class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      ctor.setAccessible(true);
      BounceNotification bn;
      try {
        bn = ctor.newInstance(null, null, null);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      bn.getNotification();
      bn.getOriginal();
      bn.getRawMessage();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link BounceNotification.Details}.
   */
  public static class BounceNotificationDetailsUsage
      extends ExhaustiveApiUsage<BounceNotification.Details> {

    @Override
    public Set<Class<?>> useApi() {
      Constructor<BounceNotification.Details> ctor;
      try {
        ctor = BounceNotification.Details.class.getDeclaredConstructor(String.class, String.class,
            String.class, String.class, String.class, String.class);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      ctor.setAccessible(true);
      BounceNotification.Details details;
      try {
        details = ctor.newInstance("yar", "yar", "yar", "yar", "yar", "yar");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      ctor.setAccessible(true);
      details.getFrom();
      details.getSubject();
      details.getText();
      details.getTo();
      details.getCc();
      details.getBcc();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link BounceNotificationParser}.
   */
  public static class BounceNotificationParserUsage
      extends ExhaustiveApiUsage<BounceNotificationParser> {

    @Override
    public Set<Class<?>> useApi() {
      BounceNotificationParser unused = new BounceNotificationParser(); // TODO(maxr): deprecate
      HttpServletRequest request = null;
      try {
        BounceNotificationParser.parse(request);
      } catch (IOException e) {
        // ok
      } catch (MessagingException e) {
        // ok
      } catch (NullPointerException e) {
        // easier than constructing a mock since we're not interested in the runtime result
      }
      return classes(Object.class, HttpRequestParser.class);
    }
  }
}
