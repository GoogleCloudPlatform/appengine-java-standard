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
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import javax.activation.DataHandler;
import javax.mail.internet.InternetAddress;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class SimpleTextMessage extends Message {
    public static final Address[] ADDRESS_ARRAY = new Address[0];
    @SuppressWarnings("rawtypes") private List _bcc = new LinkedList();
    @SuppressWarnings("rawtypes") private List _cc = new LinkedList();
    private String _description;
    private Flags _flags = new Flags();
    @SuppressWarnings("rawtypes") private List _from = new LinkedList();
    private Date _received;
    private Date _sent;
    private String _subject;
    private String _text;
    @SuppressWarnings("rawtypes") private List _to = new LinkedList();
    /**
     * @param folder
     * @param number
     */
    public SimpleTextMessage(Folder folder, int number) {
        super(folder, number);
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#addFrom(javax.mail.Address[])
     */
    @SuppressWarnings("unchecked")  // Legacy
    public void addFrom(Address[] addresses) throws MessagingException {
        _from.addAll(Arrays.asList(addresses));
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#addHeader(java.lang.String, java.lang.String)
     */
    public void addHeader(String name, String value)
        throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#addRecipients(javax.mail.Message.RecipientType, javax.mail.Address[])
     */
    @SuppressWarnings("unchecked")  // Legacy
    public void addRecipients(RecipientType type, Address[] addresses)
        throws MessagingException {
        getList(type).addAll(Arrays.asList(addresses));
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getAllHeaders()
     */
    @SuppressWarnings("rawtypes")
    public Enumeration getAllHeaders() throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getContent()
     */
    public Object getContent() throws IOException, MessagingException {
        return _text;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getContentType()
     */
    public String getContentType() throws MessagingException {
        return "text/plain";
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getDataHandler()
     */
    public DataHandler getDataHandler() throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getDescription()
     */
    public String getDescription() throws MessagingException {
        return _description;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getDisposition()
     */
    public String getDisposition() throws MessagingException {
        return Part.INLINE;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getFileName()
     */
    public String getFileName() throws MessagingException {
        return null;
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#getFlags()
     */
    public Flags getFlags() throws MessagingException {
        return _flags;
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#getFrom()
     */
    @SuppressWarnings("unchecked")  // Legacy
    public Address[] getFrom() throws MessagingException {
        return (Address[]) _from.toArray(ADDRESS_ARRAY);
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getHeader(java.lang.String)
     */
    public String[] getHeader(String name) throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getInputStream()
     */
    public InputStream getInputStream()
        throws IOException, MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getLineCount()
     */
    public int getLineCount() throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    @SuppressWarnings("rawtypes")
    private List getList(RecipientType type) throws MessagingException {
        @SuppressWarnings("rawtypes") List list;
        if (type == RecipientType.TO) {
            list = _to;
        } else if (type == RecipientType.CC) {
            list = _cc;
        } else if (type == RecipientType.BCC) {
            list = _bcc;
        } else {
            throw new MessagingException("Address type not understood");
        }
        return list;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getMatchingHeaders(java.lang.String[])
     */
    @SuppressWarnings("rawtypes")
    public Enumeration getMatchingHeaders(String[] names)
        throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getNonMatchingHeaders(java.lang.String[])
     */
    @SuppressWarnings("rawtypes")
    public Enumeration getNonMatchingHeaders(String[] names)
        throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#getReceivedDate()
     */
    public Date getReceivedDate() throws MessagingException {
        return _received;
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#getRecipients(javax.mail.Message.RecipientType)
     */
    @SuppressWarnings("unchecked")  // Legacy
    public Address[] getRecipients(RecipientType type)
        throws MessagingException {
        return (Address[]) getList(type).toArray(ADDRESS_ARRAY);
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#getSentDate()
     */
    public Date getSentDate() throws MessagingException {
        return _sent;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#getSize()
     */
    public int getSize() throws MessagingException {
        return _text.length();
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#getSubject()
     */
    public String getSubject() throws MessagingException {
        return _subject;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#isMimeType(java.lang.String)
     */
    public boolean isMimeType(String mimeType) throws MessagingException {
        return mimeType.equals("text/plain") || mimeType.equals("text/*");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#removeHeader(java.lang.String)
     */
    public void removeHeader(String name) throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#reply(boolean)
     */
  @SuppressWarnings({"unchecked", "rawtypes"})  // Legacy
    public Message reply(boolean replyToAll) throws MessagingException {
        try {
            SimpleTextMessage replyx = (SimpleTextMessage) this.clone();
            replyx._to = new LinkedList(_from);
            if (replyToAll) {
                replyx._to.addAll(_cc);
            }
            return replyx;
        } catch (CloneNotSupportedException e) {
            throw new MessagingException(e.getMessage());
        }
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#saveChanges()
     */
    public void saveChanges() throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setContent(javax.mail.Multipart)
     */
    public void setContent(Multipart content) throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setContent(java.lang.Object, java.lang.String)
     */
    public void setContent(Object content, String type)
        throws MessagingException {
        setText((String) content);
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setDataHandler(javax.activation.DataHandler)
     */
    public void setDataHandler(DataHandler handler) throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setDescription(java.lang.String)
     */
    public void setDescription(String description) throws MessagingException {
        _description = description;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setDisposition(java.lang.String)
     */
    public void setDisposition(String disposition) throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setFileName(java.lang.String)
     */
    public void setFileName(String name) throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#setFlags(javax.mail.Flags, boolean)
     */
    public void setFlags(Flags flags, boolean set) throws MessagingException {
        if (set) {
            _flags.add(flags);
        } else {
            _flags.remove(flags);
        }
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#setFrom()
     */
    public void setFrom() throws MessagingException {
        setFrom(new InternetAddress("root@localhost"));
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#setFrom(javax.mail.Address)
     */
  @SuppressWarnings("unchecked")  // Legacy
    public void setFrom(Address address) throws MessagingException {
        _from.clear();
        _from.add(address);
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setHeader(java.lang.String, java.lang.String)
     */
    public void setHeader(String name, String value)
        throws MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#setRecipients(javax.mail.Message.RecipientType, javax.mail.Address[])
     */
  @SuppressWarnings("unchecked")  // Legacy
    public void setRecipients(RecipientType type, Address[] addresses)
        throws MessagingException {
        @SuppressWarnings("rawtypes")
        List list = getList(type);
        list.clear();
        list.addAll(Arrays.asList(addresses));
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#setSentDate(java.util.Date)
     */
    public void setSentDate(Date sent) throws MessagingException {
        _sent = sent;
    }
    /* (non-Javadoc)
     * @see javax.mail.Message#setSubject(java.lang.String)
     */
    public void setSubject(String subject) throws MessagingException {
        _subject = subject;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#setText(java.lang.String)
     */
    public void setText(String content) throws MessagingException {
        _text = content;
    }
    /* (non-Javadoc)
     * @see javax.mail.Part#writeTo(java.io.OutputStream)
     */
    public void writeTo(OutputStream out)
        throws IOException, MessagingException {
        throw new UnsupportedOperationException("Method not implemented");
    }
}
