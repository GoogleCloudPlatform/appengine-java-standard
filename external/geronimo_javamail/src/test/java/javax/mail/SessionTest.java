/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package javax.mail;

import java.lang.InterruptedException;
import java.lang.Thread;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;

import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class SessionTest extends TestCase {
    public class MailThread extends Thread {
      public volatile boolean success;
      private final Session session;

      MailThread(Session session) {
        success = true;
        this.session = session;
      }

      public void run() {
        try {
          for (int i = 0; i < 1000; i++) {
            InternetAddress addr = new InternetAddress("person@example.com",
                                                       "Me");
            Transport t = session.getTransport(addr);
          }
        } catch (Exception e) {
          success = false;
          e.printStackTrace();
        }
      }
    }

    public void testAddProvider() throws MessagingException {
        Properties props = System.getProperties();
         // Get a Session object
        Session mailSession = Session.getDefaultInstance(props, null);

        mailSession.addProvider(new Provider(Provider.Type.TRANSPORT, "foo", NullTransport.class.getName(), "Apache", "Java 1.4 Test"));

        // retrieve the transport
        Transport trans = mailSession.getTransport("foo");

        assertTrue(trans instanceof NullTransport);

        mailSession.setProtocolForAddress("foo", "foo");

        trans = mailSession.getTransport(new FooAddress());

        assertTrue(trans instanceof NullTransport);
    }

    public void testConcurrentTransport() throws InterruptedException {
      int kThreads = 1000;
      Properties props = new Properties();
      Session session = Session.getDefaultInstance(props, null);
      session.addProvider(new Provider(Provider.Type.TRANSPORT, "smtp", NullTransport.class.getName(), "Apache", "Java 1.4 Test"));
      MailThread threads[] = new MailThread[kThreads];
      for (int i = 0; i < kThreads; i++) {
        threads[i] = new MailThread(session);
        threads[i].start();
      }
      for (int i = 0; i < kThreads; i++) {
        threads[i].join();
        assertTrue(threads[i].success);
      }
    }

    static public class NullTransport extends Transport {
        public NullTransport(Session session, URLName urlName) {
            super(session, urlName);
        }

        public void sendMessage(Message message, Address[] addresses) throws MessagingException {
            // do nothing
        }

        protected boolean protocolConnect(String host, int port, String user, String password) throws MessagingException {
            return true; // always connect
        }

    }

    static public class FooAddress extends Address {
        public FooAddress() {
        }

        public String getType() {
            return "foo";
        }

        public String toString() {
            return "yada";
        }


        public boolean equals(Object other) {
            return true;
        }
    }
}
