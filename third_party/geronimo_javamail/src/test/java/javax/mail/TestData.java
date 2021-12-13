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

import javax.mail.internet.MimeMessage;

public class TestData {
    public static Store getTestStore() {
        return new Store(
            getTestSession(),
            new URLName("http://alex@test.com")) {
            public Folder getDefaultFolder() throws MessagingException {
                return getTestFolder();
            }
            public Folder getFolder(String name) throws MessagingException {
                if (name.equals("test")) {
                    return getTestFolder();
                } else {
                    return null;
                }
            }
            public Folder getFolder(URLName name) throws MessagingException {
                return getTestFolder();
            }
        };
    }
    public static Session getTestSession() {
        return Session.getDefaultInstance(System.getProperties());
    }
    public static Folder getTestFolder() {
        return new Folder(getTestStore()) {
            public void appendMessages(Message[] messages)
                throws MessagingException {
            }
            public void close(boolean expunge) throws MessagingException {
            }
            public boolean create(int type) throws MessagingException {
                return false;
            }
            public boolean delete(boolean recurse) throws MessagingException {
                return false;
            }
            public boolean exists() throws MessagingException {
                return false;
            }
            public Message[] expunge() throws MessagingException {
                return null;
            }
            public Folder getFolder(String name) throws MessagingException {
                return null;
            }
            public String getFullName() {
                return null;
            }
            public Message getMessage(int id) throws MessagingException {
                return null;
            }
            public int getMessageCount() throws MessagingException {
                return 0;
            }
            public String getName() {
                return null;
            }
            public Folder getParent() throws MessagingException {
                return null;
            }
            public Flags getPermanentFlags() {
                return null;
            }
            public char getSeparator() throws MessagingException {
                return 0;
            }
            public int getType() throws MessagingException {
                return 0;
            }
            public boolean hasNewMessages() throws MessagingException {
                return false;
            }
            public boolean isOpen() {
                return false;
            }
            public Folder[] list(String pattern) throws MessagingException {
                return null;
            }
            public void open(int mode) throws MessagingException {
            }
            public boolean renameTo(Folder newName) throws MessagingException {
                return false;
            }
        };
    }
    public static Transport getTestTransport() {
        return new Transport(
            getTestSession(),
            new URLName("http://host.name")) {
            public void sendMessage(Message message, Address[] addresses)
                throws MessagingException {
                // TODO Auto-generated method stub
            }
        };
    }
    public static Message getMessage() {
        return new MimeMessage(getTestFolder(), 1) {
        };
    }
}
