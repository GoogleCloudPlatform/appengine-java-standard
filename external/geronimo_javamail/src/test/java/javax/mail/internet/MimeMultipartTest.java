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

package javax.mail.internet;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.MailcapCommandMap;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import junit.framework.TestCase;

/**
 * @version $Rev: 646017 $ $Date: 2008-04-08 13:01:42 -0500 (Tue, 08 Apr 2008) $
 */
public class MimeMultipartTest extends TestCase {
    private CommandMap defaultMap;

    public void testWriteTo() throws MessagingException, IOException, Exception {
        writeToSetUp();

        MimeMultipart mp = new MimeMultipart();
        MimeBodyPart part1 = new MimeBodyPart();
        part1.setHeader("foo", "bar");
        part1.setContent("Hello World", "text/plain");
        mp.addBodyPart(part1);
        MimeBodyPart part2 = new MimeBodyPart();
        part2.setContent("Hello Again", "text/plain");
        mp.addBodyPart(part2);
        mp.writeTo(System.out);

        writeToTearDown();
    }

    public void testPreamble() throws MessagingException, IOException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("rickmcg@gmail.com"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("rick@us.ibm.com"));
        message.setSubject("test subject");

        BodyPart messageBodyPart1 = new MimeBodyPart();
        messageBodyPart1.setHeader("Content-Type", "text/xml");
        messageBodyPart1.setHeader("Content-Transfer-Encoding", "binary");
        messageBodyPart1.setText("This is a test");

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart1);
        multipart.setPreamble("This is a preamble");

        assertEquals("This is a preamble", multipart.getPreamble());

        message.setContent(multipart);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        message.writeTo(out);
        out.writeTo(System.out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        MimeMessage newMessage = new MimeMessage(session, in);
        assertEquals("This is a preamble\r\n", ((MimeMultipart)newMessage.getContent()).getPreamble());
    }

    public void testMIMEWriting() throws IOException, MessagingException {
        File basedir = new File(System.getProperty("basedir", "."));
        File testInput = new File(basedir, "src/test/resources/wmtom.bin");
        FileInputStream inStream = new FileInputStream(testInput);
        Properties props = new Properties();
        javax.mail.Session session = javax.mail.Session
                .getInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(session, inStream);
        DataHandler dh = mimeMessage.getDataHandler();
        MimeMultipart multiPart = new MimeMultipart(dh.getDataSource());
        MimeBodyPart mimeBodyPart0 = (MimeBodyPart) multiPart.getBodyPart(0);
        Object object0 = mimeBodyPart0.getContent();
        assertNotNull(object0);
        MimeBodyPart mimeBodyPart1 = (MimeBodyPart) multiPart.getBodyPart(1);
        Object object1 = mimeBodyPart1.getContent();
        assertNotNull(object1);
    assertEquals(2, multiPart.getCount());
    }

    protected void writeToSetUp() throws Exception {
        defaultMap = CommandMap.getDefaultCommandMap();
        MailcapCommandMap myMap = new MailcapCommandMap();
        myMap.addMailcap("text/plain;;    x-java-content-handler=" + DummyTextHandler.class.getName());
        myMap.addMailcap("multipart/*;;    x-java-content-handler=" + DummyMultipartHandler.class.getName());
        CommandMap.setDefaultCommandMap(myMap);
    }

    protected void writeToTearDown() throws Exception {
        CommandMap.setDefaultCommandMap(defaultMap);
    }

    public static class DummyTextHandler implements DataContentHandler {
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Object getTransferData(DataFlavor df, DataSource ds) throws UnsupportedFlavorException, IOException {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Object getContent(DataSource ds) throws IOException {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
            os.write(((String)obj).getBytes());
        }
    }

    public static class DummyMultipartHandler implements DataContentHandler {
        public DataFlavor[] getTransferDataFlavors() {
            throw new UnsupportedOperationException();
        }

        public Object getTransferData(DataFlavor df, DataSource ds) throws UnsupportedFlavorException, IOException {
            throw new UnsupportedOperationException();
        }

        public Object getContent(DataSource ds) throws IOException {
            throw new UnsupportedOperationException();
        }

        public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
            MimeMultipart mp = (MimeMultipart) obj;
            try {
                mp.writeTo(os);
            } catch (MessagingException e) {
                throw (IOException) new IOException(e.getMessage()).initCause(e);
            }
        }
    }
}
