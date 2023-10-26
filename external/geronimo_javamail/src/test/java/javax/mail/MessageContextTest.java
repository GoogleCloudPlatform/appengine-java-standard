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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.activation.DataHandler;
import javax.mail.internet.MimeMessage;
import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class MessageContextTest extends TestCase {
    public void testNothing() {
    }
    /*
    public void testMessageContext() {
        Part p;
        MessageContext mc;
        p = new TestPart();
        mc = new MessageContext(p);
        assertSame(p, mc.getPart());
        assertNull(mc.getMessage());
        assertNull(mc.getSession());

        Session s = Session.getDefaultInstance(null);
        MimeMessage m = new MimeMessage(s);
        p = new TestMultipart(m);
        mc = new MessageContext(p);
        assertSame(p, mc.getPart());
        assertSame(m,mc.getMessage());
        assertSame(s,mc.getSession());

    }
    private static class TestMultipart extends Multipart implements Part {
        public TestMultipart(Part p) {
            parent = p;
        }
        public void writeTo(OutputStream out) throws IOException, MessagingException {
        }
        public void addHeader(String name, String value) throws MessagingException {
        }
        public Enumeration getAllHeaders() throws MessagingException {
            return null;
        }
        public Object getContent() throws IOException, MessagingException {
            return null;
        }
        public DataHandler getDataHandler() throws MessagingException {
            return null;
        }
        public String getDescription() throws MessagingException {
            return null;
        }
        public String getDisposition() throws MessagingException {
            return null;
        }
        public String getFileName() throws MessagingException {
            return null;
        }
        public String[] getHeader(String name) throws MessagingException {
            return null;
        }
        public InputStream getInputStream() throws IOException, MessagingException {
            return null;
        }
        public int getLineCount() throws MessagingException {
            return 0;
        }
        public Enumeration getMatchingHeaders(String[] names) throws MessagingException {
            return null;
        }
        public Enumeration getNonMatchingHeaders(String[] names) throws MessagingException {
            return null;
        }
        public int getSize() throws MessagingException {
            return 0;
        }
        public boolean isMimeType(String mimeType) throws MessagingException {
            return false;
        }
        public void removeHeader(String name) throws MessagingException {
        }
        public void setContent(Multipart content) throws MessagingException {
        }
        public void setContent(Object content, String type) throws MessagingException {
        }
        public void setDataHandler(DataHandler handler) throws MessagingException {
        }
        public void setDescription(String description) throws MessagingException {
        }
        public void setDisposition(String disposition) throws MessagingException {
        }
        public void setFileName(String name) throws MessagingException {
        }
        public void setHeader(String name, String value) throws MessagingException {
        }
        public void setText(String content) throws MessagingException {
        }
    }
    private static class TestBodyPart extends BodyPart {
        public TestBodyPart(Multipart p) {
            super();
            parent = p;
        }
        public void addHeader(String name, String value)
            throws MessagingException {
        }
        public Enumeration getAllHeaders() throws MessagingException {
            return null;
        }
        public Object getContent() throws IOException, MessagingException {
            return null;
        }
        public String getContentType() throws MessagingException {
            return null;
        }
        public DataHandler getDataHandler() throws MessagingException {
            return null;
        }
        public String getDescription() throws MessagingException {
            return null;
        }
        public String getDisposition() throws MessagingException {
            return null;
        }
        public String getFileName() throws MessagingException {
            return null;
        }
        public String[] getHeader(String name) throws MessagingException {
            return null;
        }
        public InputStream getInputStream()
            throws IOException, MessagingException {
            return null;
        }
        public int getLineCount() throws MessagingException {
            return 0;
        }
        public Enumeration getMatchingHeaders(String[] names)
            throws MessagingException {
            return null;
        }
        public Enumeration getNonMatchingHeaders(String[] names)
            throws MessagingException {
            return null;
        }
        public int getSize() throws MessagingException {
            return 0;
        }
        public boolean isMimeType(String mimeType) throws MessagingException {
            return false;
        }
        public void removeHeader(String name) throws MessagingException {
        }
        public void setContent(Multipart content) throws MessagingException {
        }
        public void setContent(Object content, String type)
            throws MessagingException {
        }
        public void setDataHandler(DataHandler handler)
            throws MessagingException {
        }
        public void setDescription(String description)
            throws MessagingException {
        }
        public void setDisposition(String disposition)
            throws MessagingException {
        }
        public void setFileName(String name) throws MessagingException {
        }
        public void setHeader(String name, String value)
            throws MessagingException {
        }
        public void setText(String content) throws MessagingException {
        }
        public void writeTo(OutputStream out)
            throws IOException, MessagingException {
        }
    }
    private static class TestPart implements Part {
        public void addHeader(String name, String value)
            throws MessagingException {
        }
        public Enumeration getAllHeaders() throws MessagingException {
            return null;
        }
        public Object getContent() throws IOException, MessagingException {
            return null;
        }
        public String getContentType() throws MessagingException {
            return null;
        }
        public DataHandler getDataHandler() throws MessagingException {
            return null;
        }
        public String getDescription() throws MessagingException {
            return null;
        }
        public String getDisposition() throws MessagingException {
            return null;
        }
        public String getFileName() throws MessagingException {
            return null;
        }
        public String[] getHeader(String name) throws MessagingException {
            return null;
        }
        public InputStream getInputStream()
            throws IOException, MessagingException {
            return null;
        }
        public int getLineCount() throws MessagingException {
            return 0;
        }
        public Enumeration getMatchingHeaders(String[] names)
            throws MessagingException {
            return null;
        }
        public Enumeration getNonMatchingHeaders(String[] names)
            throws MessagingException {
            return null;
        }
        public int getSize() throws MessagingException {
            return 0;
        }
        public boolean isMimeType(String mimeType) throws MessagingException {
            return false;
        }
        public void removeHeader(String name) throws MessagingException {
        }
        public void setContent(Multipart content) throws MessagingException {
        }
        public void setContent(Object content, String type)
            throws MessagingException {
        }
        public void setDataHandler(DataHandler handler)
            throws MessagingException {
        }
        public void setDescription(String description)
            throws MessagingException {
        }
        public void setDisposition(String disposition)
            throws MessagingException {
        }
        public void setFileName(String name) throws MessagingException {
        }
        public void setHeader(String name, String value)
            throws MessagingException {
        }
        public void setText(String content) throws MessagingException {
        }
        public void writeTo(OutputStream out)
            throws IOException, MessagingException {
        }
    }
    */
}
