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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class SimpleFolder extends Folder {
    private static final Message[] MESSAGE_ARRAY = new Message[0];
    @SuppressWarnings("rawtypes") private List _messages = new LinkedList();
    private String _name;
    public SimpleFolder(Store store) {
        this(store, "SimpleFolder");
    }
    SimpleFolder(Store store, String name) {
        super(store);
        _name = name;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#appendMessages(javax.mail.Message[])
     */
    @SuppressWarnings("unchecked")  // Legacy
    public void appendMessages(Message[] messages) throws MessagingException {
        for (int i = 0; i < messages.length; i++) {
            Message message = messages[i];
            _messages.add(message);
        }
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#close(boolean)
     */
    public void close(boolean expunge) throws MessagingException {
        if (expunge) {
            expunge();
        }
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#create(int)
     */
    public boolean create(int type) throws MessagingException {
        if (type == HOLDS_MESSAGES) {
            return true;
        } else {
            throw new MessagingException("Cannot create folders that hold folders");
        }
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#delete(boolean)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})  // Legacy
    public boolean delete(boolean recurse) throws MessagingException {
        _messages = new LinkedList();
        return true;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#exists()
     */
    public boolean exists() throws MessagingException {
        return true;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#expunge()
     */
    @SuppressWarnings({"unchecked", "rawtypes"})  // Legacy
    public Message[] expunge() throws MessagingException {
        @SuppressWarnings("rawtypes") Iterator it = _messages.iterator();

        @SuppressWarnings("rawtypes") List result = new LinkedList();
        while (it.hasNext()) {
            Message message = (Message) it.next();
            if (message.isSet(Flags.Flag.DELETED)) {
                it.remove();
                result.add(message);
            }
        }
        // run through and renumber the messages
        for (int i = 0; i < _messages.size(); i++) {
            Message message = (Message) _messages.get(i);
            message.setMessageNumber(i);
        }
        return (Message[]) result.toArray(MESSAGE_ARRAY);
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getFolder(java.lang.String)
     */
    public Folder getFolder(String name) throws MessagingException {
        return null;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getFullName()
     */
    public String getFullName() {
        return getName();
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getMessage(int)
     */
    public Message getMessage(int id) throws MessagingException {
        return (Message) _messages.get(id);
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getMessageCount()
     */
    public int getMessageCount() throws MessagingException {
        return _messages.size();
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getName()
     */
    public String getName() {
        return _name;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getParent()
     */
    public Folder getParent() throws MessagingException {
        return null;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getPermanentFlags()
     */
    public Flags getPermanentFlags() {
        return null;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getSeparator()
     */
    public char getSeparator() throws MessagingException {
        return '/';
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#getType()
     */
    public int getType() throws MessagingException {
        return HOLDS_MESSAGES;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#hasNewMessages()
     */
    public boolean hasNewMessages() throws MessagingException {
        return false;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#isOpen()
     */
    public boolean isOpen() {
        return true;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#list(java.lang.String)
     */
    public Folder[] list(String pattern) throws MessagingException {
        return null;
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#open(int)
     */
    public void open(int mode) throws MessagingException {
        if (mode != HOLDS_MESSAGES) {
            throw new MessagingException("SimpleFolder can only be opened with HOLDS_MESSAGES");
        }
    }
    /* (non-Javadoc)
     * @see javax.mail.Folder#renameTo(javax.mail.Folder)
     */
    public boolean renameTo(Folder newName) throws MessagingException {
        _name = newName.getName();
        return true;
    }
}
