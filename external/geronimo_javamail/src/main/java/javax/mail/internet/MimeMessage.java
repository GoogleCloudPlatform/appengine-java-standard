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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.HeaderTokenizer.Token;

import org.apache.geronimo.mail.util.ASCIIUtil;
import org.apache.geronimo.mail.util.SessionUtil;

/**
 * @version $Rev: 702800 $ $Date: 2008-10-08 05:38:14 -0500 (Wed, 08 Oct 2008) $
 */
public class MimeMessage extends Message implements MimePart {
	private static final String MIME_ADDRESS_STRICT = "mail.mime.address.strict";
	private static final String MIME_DECODEFILENAME = "mail.mime.decodefilename";
	private static final String MIME_ENCODEFILENAME = "mail.mime.encodefilename";

	private static final String MAIL_ALTERNATES = "mail.alternates";
	private static final String MAIL_REPLYALLCC = "mail.replyallcc";

    // static used to ensure message ID uniqueness
    private static int messageID = 0;


    /**
     * Extends {@link javax.mail.Message.RecipientType} to support addition recipient types.
     */
    public static class RecipientType extends Message.RecipientType {
        /**
         * Recipient type for Usenet news.
         */
        public static final RecipientType NEWSGROUPS = new RecipientType("Newsgroups");

        protected RecipientType(String type) {
            super(type);
        }

        /**
         * Ensure the singleton is returned.
         *
         * @return resolved object
         */
        protected Object readResolve() throws ObjectStreamException {
            if (this.type.equals("Newsgroups")) {
                return NEWSGROUPS;
            } else {
                return super.readResolve();
            }
        }
    }

    /**
     * The {@link DataHandler} for this Message's content.
     */
    protected DataHandler dh;
    /**
     * This message's content (unless sourced from a SharedInputStream).
     */
    protected byte[] content;
    /**
     * If the data for this message was supplied by a {@link SharedInputStream}
     * then this is another such stream representing the content of this message;
     * if this field is non-null, then {@link #content} will be null.
     */
    protected InputStream contentStream;
    /**
     * This message's headers.
     */
    protected InternetHeaders headers;
    /**
     * This message's flags.
     */
    protected Flags flags;
    /**
     * Flag indicating that the message has been modified; set to true when
     * an empty message is created or when {@link #saveChanges()} is called.
     */
    protected boolean modified;
    /**
     * Flag indicating that the message has been saved.
     */
    protected boolean saved;

    private final MailDateFormat dateFormat = new MailDateFormat();

    /**
     * Create a new MimeMessage.
     * An empty message is created, with empty {@link #headers} and empty {@link #flags}.
     * The {@link #modified} flag is set.
     *
     * @param session the session for this message
     */
    public MimeMessage(Session session) {
        super(session);
        headers = new InternetHeaders();
        flags = new Flags();
        // empty messages are modified, because the content is not there, and require saving before use.
        modified = true;
        saved = false;
    }

    /**
     * Create a MimeMessage by reading an parsing the data from the supplied stream.
     *
     * @param session the session for this message
     * @param in      the stream to load from
     * @throws MessagingException if there is a problem reading or parsing the stream
     */
    public MimeMessage(Session session, InputStream in) throws MessagingException {
        this(session);
        parse(in);
        // this message is complete, so marked as unmodified.
        modified = false;
        // and no saving required
        saved = true;
    }

    /**
     * Copy a MimeMessage.
     *
     * @param message the message to copy
     * @throws MessagingException is there was a problem copying the message
     */
    public MimeMessage(MimeMessage message) throws MessagingException {
        super(message.session);
        // get a copy of the source message flags 
        flags = message.getFlags(); 
        // this is somewhat difficult to do.  There's a lot of data in both the superclass and this
        // class that needs to undergo a "deep cloning" operation.  These operations don't really exist
        // on the objects in question, so the only solution I can come up with is to serialize the
        // message data of the source object using the write() method, then reparse the data in this
        // object.  I've not found a lot of uses for this particular constructor, so perhaps that's not
        // really all that bad of a solution.
        
        // serialize this out to an in-memory stream.
        ByteArrayOutputStream copy = new ByteArrayOutputStream();

        try {
            // write this out the stream.
            message.writeTo(copy);
            copy.close();
            // I think this ends up creating a new array for the data, but I'm not aware of any more
            // efficient options.
            ByteArrayInputStream inData = new ByteArrayInputStream(copy.toByteArray());
            // now reparse this message into this object.
            inData.close();
            parse (inData);
            // writing out the source data requires saving it, so we should consider this one saved also.
            saved = true;
            // this message is complete, so marked as unmodified.
            modified = false;
        } catch (IOException e) {
            // I'm not sure ByteArrayInput/OutputStream actually throws IOExceptions or not, but the method
            // signatures declare it, so we need to deal with it.  Turning it into a messaging exception
            // should fit the bill.
            throw new MessagingException("Error copying MimeMessage data", e);
        }
    }

    /**
     * Create an new MimeMessage in the supplied {@link Folder} and message number.
     *
     * @param folder the Folder that contains the new message
     * @param number the message number of the new message
     */
    protected MimeMessage(Folder folder, int number) {
        super(folder, number);
        headers = new InternetHeaders();
        flags = new Flags();
        // saving primarly involves updates to the message header.  Since we're taking the header info
        // from a message store in this context, we mark the message as saved.
        saved = true;
        // we've not filled in the content yet, so this needs to be marked as modified
        modified = true;
    }

    /**
     * Create a MimeMessage by reading an parsing the data from the supplied stream.
     *
     * @param folder the folder for this message
     * @param in     the stream to load from
     * @param number the message number of the new message
     * @throws MessagingException if there is a problem reading or parsing the stream
     */
    protected MimeMessage(Folder folder, InputStream in, int number) throws MessagingException {
        this(folder, number);
        parse(in);
        // this message is complete, so marked as unmodified.
        modified = false;
        // and no saving required
        saved = true;
    }


    /**
     * Create a MimeMessage with the supplied headers and content.
     *
     * @param folder  the folder for this message
     * @param headers the headers for the new message
     * @param content the content of the new message
     * @param number  the message number of the new message
     * @throws MessagingException if there is a problem reading or parsing the stream
     */
    protected MimeMessage(Folder folder, InternetHeaders headers, byte[] content, int number) throws MessagingException {
        this(folder, number);
        this.headers = headers;
        this.content = content;
        // this message is complete, so marked as unmodified.
        modified = false;
    }

    /**
     * Parse the supplied stream and initialize {@link #headers} and {@link #content} appropriately.
     *
     * @param in the stream to read
     * @throws MessagingException if there was a problem parsing the stream
     */
    protected void parse(InputStream in) throws MessagingException {
        in = new BufferedInputStream(in);
        // create the headers first from the stream.  Note:  We need to do this 
        // by calling createInternetHeaders because subclasses might wish to add 
        // additional headers to the set initialized from the stream. 
        headers = createInternetHeaders(in);

        // now we need to get the rest of the content as a byte array...this means reading from the current
        // position in the stream until the end and writing it to an accumulator ByteArrayOutputStream.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte buffer[] = new byte[1024];
            int count;
            while ((count = in.read(buffer, 0, 1024)) != -1) {
                baos.write(buffer, 0, count);
            }
        } catch (Exception e) {
            throw new MessagingException(e.toString(), e);
        }
        // and finally extract the content as a byte array.
        content = baos.toByteArray();
    }

    /**
     * Get the message "From" addresses.  This looks first at the
     * "From" headers, and no "From" header is found, the "Sender"
     * header is checked.  Returns null if not found.
     *
     * @return An array of addresses identifying the message from target.  Returns
     *         null if this is not resolveable from the headers.
     * @exception MessagingException
     */
    public Address[] getFrom() throws MessagingException {
        // strict addressing controls this.
        boolean strict = isStrictAddressing();
        Address[] result = getHeaderAsInternetAddresses("From", strict);
        if (result == null) {
            result = getHeaderAsInternetAddresses("Sender", strict);
        }
        return result;
    }

    /**
     * Set the current message "From" recipient.  This replaces any
     * existing "From" header.  If the address is null, the header is
     * removed.
     *
     * @param address The new "From" target.
     *
     * @exception MessagingException
     */
    public void setFrom(Address address) throws MessagingException {
        setHeader("From", address);
    }

    /**
     * Set the "From" header using the value returned by {@link InternetAddress#getLocalAddress(javax.mail.Session)}.
     *
     * @throws MessagingException if there was a problem setting the header
     */
    public void setFrom() throws MessagingException {
        InternetAddress address = InternetAddress.getLocalAddress(session);
        // no local address resolvable?  This is an error.
        if (address == null) {
            throw new MessagingException("No local address defined");
        }
        setFrom(address);
    }

    /**
     * Add a set of addresses to the existing From header.
     *
     * @param addresses The list to add.
     *
     * @exception MessagingException
     */
    public void addFrom(Address[] addresses) throws MessagingException {
        addHeader("From", addresses);
    }

    /**
     * Return the "Sender" header as an address.
     *
     * @return the "Sender" header as an address, or null if not present
     * @throws MessagingException if there was a problem parsing the header
     */
    public Address getSender() throws MessagingException {
        Address[] addrs = getHeaderAsInternetAddresses("Sender", isStrictAddressing());
        return addrs != null && addrs.length > 0 ? addrs[0] : null;
    }

    /**
     * Set the "Sender" header.  If the address is null, this
     * will remove the current sender header.
     *
     * @param address the new Sender address
     *
     * @throws MessagingException
     *                if there was a problem setting the header
     */
    public void setSender(Address address) throws MessagingException {
        setHeader("Sender", address);
    }

    /**
     * Gets the recipients by type.  Returns null if there are no
     * headers of the specified type.  Acceptable RecipientTypes are:
     *
     *   javax.mail.Message.RecipientType.TO
     *   javax.mail.Message.RecipientType.CC
     *   javax.mail.Message.RecipientType.BCC
     *   javax.mail.internet.MimeMessage.RecipientType.NEWSGROUPS
     *
     * @param type   The message RecipientType identifier.
     *
     * @return The array of addresses for the specified recipient types.
     * @exception MessagingException
     */
    public Address[] getRecipients(Message.RecipientType type) throws MessagingException {
        // is this a NEWSGROUP request?  We need to handle this as a special case here, because
        // this needs to return NewsAddress instances instead of InternetAddress items.
        if (type == RecipientType.NEWSGROUPS) {
            return getHeaderAsNewsAddresses(getHeaderForRecipientType(type));
        }
        // the other types are all internet addresses.
        return getHeaderAsInternetAddresses(getHeaderForRecipientType(type), isStrictAddressing());
    }

    /**
     * Retrieve all of the recipients defined for this message.  This
     * returns a merged array of all possible message recipients
     * extracted from the headers.  The relevant header types are:
     *
     *
     *    javax.mail.Message.RecipientType.TO
     *    javax.mail.Message.RecipientType.CC
     *    javax.mail.Message.RecipientType.BCC
     *    javax.mail.internet.MimeMessage.RecipientType.NEWSGROUPS
     *
     * @return An array of all target message recipients.
     * @exception MessagingException
     */
    public Address[] getAllRecipients() throws MessagingException {
        List recipients = new ArrayList();
        addRecipientsToList(recipients, RecipientType.TO);
        addRecipientsToList(recipients, RecipientType.CC);
        addRecipientsToList(recipients, RecipientType.BCC);
        addRecipientsToList(recipients, RecipientType.NEWSGROUPS);

        // this is supposed to return null if nothing is there.
        if (recipients.isEmpty()) {
            return null;
        }
        return (Address[]) recipients.toArray(new Address[recipients.size()]);
    }

    /**
     * Utility routine to merge different recipient types into a
     * single list.
     *
     * @param list   The accumulator list.
     * @param type   The recipient type to extract.
     *
     * @exception MessagingException
     */
    private void addRecipientsToList(List list, Message.RecipientType type) throws MessagingException {

        Address[] recipients;
        if (type == RecipientType.NEWSGROUPS) {
            recipients = getHeaderAsNewsAddresses(getHeaderForRecipientType(type));
        }
        else {
            recipients = getHeaderAsInternetAddresses(getHeaderForRecipientType(type), isStrictAddressing());
        }
        if (recipients != null) {
            list.addAll(Arrays.asList(recipients));
        }
    }

    /**
     * Set a recipients list for a particular recipient type.  If the
     * list is null, the corresponding header is removed.
     *
     * @param type      The type of recipient to set.
     * @param addresses The list of addresses.
     *
     * @exception MessagingException
     */
    public void setRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        setHeader(getHeaderForRecipientType(type), InternetAddress.toString(addresses, 0));
    }

    /**
     * Set a recipient field to a string address (which may be a
     * list or group type).
     *
     * If the address is null, the field is removed.
     *
     * @param type    The type of recipient to set.
     * @param address The address string.
     *
     * @exception MessagingException
     */
    public void setRecipients(Message.RecipientType type, String address) throws MessagingException {
        setOrRemoveHeader(getHeaderForRecipientType(type), address);
    }


    /**
     * Add a list of addresses to a target recipient list.
     *
     * @param type    The target recipient type.
     * @param address An array of addresses to add.
     *
     * @exception MessagingException
     */
    public void addRecipients(Message.RecipientType type, Address[] address) throws MessagingException {
        addHeader(getHeaderForRecipientType(type), address);
    }

    /**
     * Add an address to a target recipient list by string name.
     *
     * @param type    The target header type.
     * @param address The address to add.
     *
     * @exception MessagingException
     */
    public void addRecipients(Message.RecipientType type, String address) throws MessagingException {
        addHeader(getHeaderForRecipientType(type), address);
    }

    /**
     * Get the ReplyTo address information.  The headers are parsed
     * using the "mail.mime.address.strict" setting.  If the "Reply-To" header does
     * not have any addresses, then the value of the "From" field is used.
     *
     * @return An array of addresses obtained from parsing the header.
     * @exception MessagingException
     */
    public Address[] getReplyTo() throws MessagingException {
         Address[] addresses = getHeaderAsInternetAddresses("Reply-To", isStrictAddressing());
         if (addresses == null) {
             addresses = getFrom();
         }
         return addresses;
    }

    /**
     * Set the Reply-To field to the provided list of addresses.  If
     * the address list is null, the header is removed.
     *
     * @param address The new field value.
     *
     * @exception MessagingException
     */
    public void setReplyTo(Address[] address) throws MessagingException {
        setHeader("Reply-To", address);
    }

    /**
     * Returns the value of the "Subject" header.  If the subject
     * is encoded as an RFC 2047 value, the value is decoded before
     * return.  If decoding fails, the raw string value is
     * returned.
     *
     * @return The String value of the subject field.
     * @exception MessagingException
     */
    public String getSubject() throws MessagingException {
        String subject = getSingleHeader("Subject");
        if (subject == null) {
            return null;
        } else {
            try {
                // this needs to be unfolded before decodeing.
                return MimeUtility.decodeText(MimeUtility.unfold(subject));
            } catch (UnsupportedEncodingException e) {
                // ignored.
            }
        }

        return subject;
    }

    /**
     * Set the value for the "Subject" header.  If the subject
     * contains non US-ASCII characters, it is encoded in RFC 2047
     * fashion.
     *
     * If the subject value is null, the Subject field is removed.
     *
     * @param subject The new subject value.
     *
     * @exception MessagingException
     */
    public void setSubject(String subject) throws MessagingException {
        // just set this using the default character set.
        setSubject(subject, null);
    }

    public void setSubject(String subject, String charset) throws MessagingException {
        // standard null removal (yada, yada, yada....)
        if (subject == null) {
            removeHeader("Subject");
        }
        else {
            try {
                String s = MimeUtility.fold(9, MimeUtility.encodeText(subject, charset, null));
                // encode this, and then fold to fit the line lengths.
                setHeader("Subject", MimeUtility.fold(9, MimeUtility.encodeText(subject, charset, null)));
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException("Encoding error", e);
            }
        }
    }

    /**
     * Get the value of the "Date" header field.  Returns null if
     * if the field is absent or the date is not in a parseable format.
     *
     * @return A Date object parsed according to RFC 822.
     * @exception MessagingException
     */
    public Date getSentDate() throws MessagingException {
        String value = getSingleHeader("Date");
        if (value == null) {
            return null;
        }
        try {
            return dateFormat.parse(value);
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    /**
     * Set the message sent date.  This updates the "Date" header.
     * If the provided date is null, the header is removed.
     *
     * @param sent   The new sent date value.
     *
     * @exception MessagingException
     */
    public void setSentDate(Date sent) throws MessagingException {
        setOrRemoveHeader("Date", dateFormat.format(sent));
    }

    /**
     * Get the message received date.  The Sun implementation is
     * documented as always returning null, so this one does too.
     *
     * @return Always returns null.
     * @exception MessagingException
     */
    public Date getReceivedDate() throws MessagingException {
        return null;
    }

    /**
     * Return the content size of this message.  This is obtained
     * either from the size of the content field (if available) or
     * from the contentStream, IFF the contentStream returns a positive
     * size.  Returns -1 if the size is not available.
     *
     * @return Size of the content in bytes.
     * @exception MessagingException
     */
    public int getSize() throws MessagingException {
        if (content != null) {
            return content.length;
        }
        if (contentStream != null) {
            try {
                int size = contentStream.available();
                if (size > 0) {
                    return size;
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return -1;
    }

    /**
     * Retrieve the line count for the current message.  Returns
     * -1 if the count cannot be determined.
     *
     * The Sun implementation always returns -1, so this version
     * does too.
     *
     * @return The content line count (always -1 in this implementation).
     * @exception MessagingException
     */
    public int getLineCount() throws MessagingException {
        return -1;
    }

    /**
     * Returns the current content type (defined in the "Content-Type"
     * header.  If not available, "text/plain" is the default.
     *
     * @return The String name of the message content type.
     * @exception MessagingException
     */
    public String getContentType() throws MessagingException {
        String value = getSingleHeader("Content-Type");
        if (value == null) {
            value = "text/plain";
        }
        return value;
    }


    /**
     * Tests to see if this message has a mime-type match with the
     * given type name.
     *
     * @param type   The tested type name.
     *
     * @return If this is a type match on the primary and secondare portion of the types.
     * @exception MessagingException
     */
    public boolean isMimeType(String type) throws MessagingException {
        return new ContentType(getContentType()).match(type);
    }

    /**
     * Retrieve the message "Content-Disposition" header field.
     * This value represents how the part should be represented to
     * the user.
     *
     * @return The string value of the Content-Disposition field.
     * @exception MessagingException
     */
    public String getDisposition() throws MessagingException {
        String disp = getSingleHeader("Content-Disposition");
        if (disp != null) {
            return new ContentDisposition(disp).getDisposition();
        }
        return null;
    }


    /**
     * Set a new dispostion value for the "Content-Disposition" field.
     * If the new value is null, the header is removed.
     *
     * @param disposition
     *               The new disposition value.
     *
     * @exception MessagingException
     */
    public void setDisposition(String disposition) throws MessagingException {
        if (disposition == null) {
            removeHeader("Content-Disposition");
        }
        else {
            // the disposition has parameters, which we'll attempt to preserve in any existing header.
            String currentHeader = getSingleHeader("Content-Disposition");
            if (currentHeader != null) {
                ContentDisposition content = new ContentDisposition(currentHeader);
                content.setDisposition(disposition);
                setHeader("Content-Disposition", content.toString());
            }
            else {
                // set using the raw string.
                setHeader("Content-Disposition", disposition);
            }
        }
    }

    /**
     * Decode the Content-Transfer-Encoding header to determine
     * the transfer encoding type.
     *
     * @return The string name of the required encoding.
     * @exception MessagingException
     */
    public String getEncoding() throws MessagingException {
        // this might require some parsing to sort out.
        String encoding = getSingleHeader("Content-Transfer-Encoding");
        if (encoding != null) {
            // we need to parse this into ATOMs and other constituent parts.  We want the first
            // ATOM token on the string.
            HeaderTokenizer tokenizer = new HeaderTokenizer(encoding, HeaderTokenizer.MIME);

            Token token = tokenizer.next();
            while (token.getType() != Token.EOF) {
                // if this is an ATOM type, return it.
                if (token.getType() == Token.ATOM) {
                    return token.getValue();
                }
            }
            // not ATOMs found, just return the entire header value....somebody might be able to make sense of
            // this.
            return encoding;
        }
        // no header, nothing to return.
        return null;
    }

    /**
     * Retrieve the value of the "Content-ID" header.  Returns null
     * if the header does not exist.
     *
     * @return The current header value or null.
     * @exception MessagingException
     */
    public String getContentID() throws MessagingException {
        return getSingleHeader("Content-ID");
    }

    public void setContentID(String cid) throws MessagingException {
        setOrRemoveHeader("Content-ID", cid);
    }

    public String getContentMD5() throws MessagingException {
        return getSingleHeader("Content-MD5");
    }

    public void setContentMD5(String md5) throws MessagingException {
        setOrRemoveHeader("Content-MD5", md5);
    }

    public String getDescription() throws MessagingException {
        String description = getSingleHeader("Content-Description");
        if (description != null) {
            try {
                // this could be both folded and encoded.  Return this to usable form.
                return MimeUtility.decodeText(MimeUtility.unfold(description));
            } catch (UnsupportedEncodingException e) {
                // ignore
            }
        }
        // return the raw version for any errors.
        return description;
    }

    public void setDescription(String description) throws MessagingException {
        setDescription(description, null);
    }

    public void setDescription(String description, String charset) throws MessagingException {
        if (description == null) {
            removeHeader("Content-Description");
        }
        else {
            try {
                setHeader("Content-Description", MimeUtility.fold(21, MimeUtility.encodeText(description, charset, null)));
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException(e.getMessage(), e);
            }
        }

    }

    public String[] getContentLanguage() throws MessagingException {
        return getHeader("Content-Language");
    }

    public void setContentLanguage(String[] languages) throws MessagingException {
        if (languages == null) {
            removeHeader("Content-Language");
        } else if (languages.length == 1) {
            setHeader("Content-Language", languages[0]);
        } else {
            StringBuffer buf = new StringBuffer(languages.length * 20);
            buf.append(languages[0]);
            for (int i = 1; i < languages.length; i++) {
                buf.append(',').append(languages[i]);
            }
            setHeader("Content-Language", buf.toString());
        }
    }

    public String getMessageID() throws MessagingException {
        return getSingleHeader("Message-ID");
    }

    public String getFileName() throws MessagingException {
        // see if there is a disposition.  If there is, parse off the filename parameter.
        String disposition = getDisposition();
        String filename = null;

        if (disposition != null) {
            filename = new ContentDisposition(disposition).getParameter("filename");
        }

        // if there's no filename on the disposition, there might be a name parameter on a
        // Content-Type header.
        if (filename == null) {
            String type = getContentType();
            if (type != null) {
                try {
                    filename = new ContentType(type).getParameter("name");
                } catch (ParseException e) {
                }
            }
        }
        // if we have a name, we might need to decode this if an additional property is set.
        if (filename != null && SessionUtil.getBooleanProperty(session, MIME_DECODEFILENAME, false)) {
            try {
                filename = MimeUtility.decodeText(filename);
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException("Unable to decode filename", e);
            }
        }

        return filename;
    }


    public void setFileName(String name) throws MessagingException {
        // there's an optional session property that requests file name encoding...we need to process this before
        // setting the value.
        if (name != null && SessionUtil.getBooleanProperty(session, MIME_ENCODEFILENAME, false)) {
            try {
                name = MimeUtility.encodeText(name);
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException("Unable to encode filename", e);
            }
        }

        // get the disposition string.
        String disposition = getDisposition();
        // if not there, then this is an attachment.
        if (disposition == null) {
            disposition = Part.ATTACHMENT;
        }
        // now create a disposition object and set the parameter.
        ContentDisposition contentDisposition = new ContentDisposition(disposition);
        contentDisposition.setParameter("filename", name);

        // serialize this back out and reset.
        setDisposition(contentDisposition.toString());
    }

    public InputStream getInputStream() throws MessagingException, IOException {
        return getDataHandler().getInputStream();
    }

    protected InputStream getContentStream() throws MessagingException {
        if (contentStream != null) {
            return contentStream;
        }

        if (content != null) {
            return new ByteArrayInputStream(content);
        } else {
            throw new MessagingException("No content");
        }
    }

    public InputStream getRawInputStream() throws MessagingException {
        return getContentStream();
    }

    public synchronized DataHandler getDataHandler() throws MessagingException {
        if (dh == null) {
            dh = new DataHandler(new MimePartDataSource(this));
        }
        return dh;
    }

    public Object getContent() throws MessagingException, IOException {
        return getDataHandler().getContent();
    }

    public void setDataHandler(DataHandler handler) throws MessagingException {
        dh = handler;
        // if we have a handler override, then we need to invalidate any content
        // headers that define the types.  This information will be derived from the
        // data heander unless subsequently overridden.
        removeHeader("Content-Type");
        removeHeader("Content-Transfer-Encoding");
    }

    public void setContent(Object content, String type) throws MessagingException {
        setDataHandler(new DataHandler(content, type));
    }

    public void setText(String text) throws MessagingException {
        setText(text, null, "plain");
    }

    public void setText(String text, String charset) throws MessagingException {
        setText(text, charset, "plain");
    }


    public void setText(String text, String charset, String subtype) throws MessagingException {
        // we need to sort out the character set if one is not provided.
        if (charset == null) {
            // if we have non us-ascii characters here, we need to adjust this.
            if (!ASCIIUtil.isAscii(text)) {
                charset = MimeUtility.getDefaultMIMECharset();
            }
            else {
                charset = "us-ascii";
            }
        }
        setContent(text, "text/" + subtype + "; charset=" + MimeUtility.quote(charset, HeaderTokenizer.MIME));
    }

    public void setContent(Multipart part) throws MessagingException {
        setDataHandler(new DataHandler(part, part.getContentType()));
        part.setParent(this);
    }

    public Message reply(boolean replyToAll) throws MessagingException {
        // create a new message in this session.
        MimeMessage reply = createMimeMessage(session);

        // get the header and add the "Re:" bit, if necessary.
        String newSubject = getSubject();
        if (newSubject != null) {
            // check to see if it already begins with "Re: " (in any case).
            // Add one on if we don't have it yet.
            if (!newSubject.regionMatches(true, 0, "Re: ", 0, 4)) {
                newSubject = "Re: " + newSubject;
            }
            reply.setSubject(newSubject);
        }
        
        // if this message has a message ID, then add a In-Reply-To and References
        // header to the reply message 
        String messageID = getSingleHeader("Message-ID"); 
        if (messageID != null) {
            // this one is just set unconditionally 
            reply.setHeader("In-Reply-To", messageID); 
            // we might already have a references header.  If so, then add our message id 
            // on the the end
            String references = getSingleHeader("References"); 
            if (references == null) {
                references = messageID; 
            }
            else {
                references = references + " " + messageID; 
            }
            // and this is a replacement for whatever might be there.              
            reply.setHeader("References", MimeUtility.fold("References: ".length(), references)); 
        }

        Address[] toRecipients = getReplyTo();

        // set the target recipients the replyTo value
        reply.setRecipients(Message.RecipientType.TO, getReplyTo());

        // need to reply to everybody?  More things to add.
        if (replyToAll) {
            // when replying, we want to remove "duplicates" in the final list.

            HashMap masterList = new HashMap();

            // reply to all implies add the local sender.  Add this to the list if resolveable.
            InternetAddress localMail = InternetAddress.getLocalAddress(session);
            if (localMail != null) {
                masterList.put(localMail.getAddress(), localMail);
            }
            // see if we have some local aliases to deal with.
            String alternates = session.getProperty(MAIL_ALTERNATES);
            if (alternates != null) {
                // parse this string list and merge with our set.
                Address[] alternateList = InternetAddress.parse(alternates, false);
                mergeAddressList(masterList, alternateList);
            }

            // the master list now contains an a list of addresses we will exclude from
            // the addresses.  From this point on, we're going to prune any additional addresses
            // against this list, AND add any new addresses to the list

            // now merge in the main recipients, and merge in the other recipents as well
            Address[] toList = pruneAddresses(masterList, getRecipients(Message.RecipientType.TO));
            if (toList.length != 0) {
                // now check to see what sort of reply we've been asked to send.
                // if replying to all as a CC, then we need to add to the CC list, otherwise they are
                // TO recipients.
                if (SessionUtil.getBooleanProperty(session, MAIL_REPLYALLCC, false)) {
                    reply.addRecipients(Message.RecipientType.CC, toList);
                }
                else {
                    reply.addRecipients(Message.RecipientType.TO, toList);
                }
            }
            // and repeat for the CC list.
            toList = pruneAddresses(masterList, getRecipients(Message.RecipientType.CC));
            if (toList.length != 0) {
                reply.addRecipients(Message.RecipientType.CC, toList);
            }

            // a news group list is separate from the normal addresses.  We just take these recepients
            // asis without trying to prune duplicates.
            toList = getRecipients(RecipientType.NEWSGROUPS);
            if (toList != null && toList.length != 0) {
                reply.addRecipients(RecipientType.NEWSGROUPS, toList);
            }
        }

        // this is a bit of a pain.  We can't set the flags here by specifying the system flag, we need to
        // construct a flag item instance inorder to set it.

        // this is an answered email.
        setFlags(new Flags(Flags.Flag.ANSWERED), true);
        // all done, return the constructed Message object.
        return reply;
    }


    /**
     * Merge a set of addresses into a master accumulator list, eliminating
     * duplicates.
     *
     * @param master The set of addresses we've accumulated so far.
     * @param list   The list of addresses to merge in.
     */
    private void mergeAddressList(Map master, Address[] list) {
        // make sure we have a list.
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.length; i++) {
            InternetAddress address = (InternetAddress)list[i];

            // if not in the master list already, add it now.
            if (!master.containsKey(address.getAddress())) {
                master.put(address.getAddress(), address);
            }
        }
    }


    /**
     * Prune a list of addresses against our master address list,
     * returning the "new" addresses.  The master list will be
     * updated with this new set of addresses.
     *
     * @param master The master address list of addresses we've seen before.
     * @param list   The new list of addresses to prune.
     *
     * @return An array of addresses pruned of any duplicate addresses.
     */
    private Address[] pruneAddresses(Map master, Address[] list) {
        // return an empy array if we don't get an input list.
        if (list == null) {
            return new Address[0];
        }

        // optimistically assume there are no addresses to eliminate (common).
        ArrayList prunedList = new ArrayList(list.length);
        for (int i = 0; i < list.length; i++) {
            InternetAddress address = (InternetAddress)list[i];

            // if not in the master list, this is a new one.  Add to both the master list and
            // the pruned list.
            if (!master.containsKey(address.getAddress())) {
                master.put(address.getAddress(), address);
                prunedList.add(address);
            }
        }
        // convert back to list form.
        return (Address[])prunedList.toArray(new Address[0]);
    }


    /**
     * Write the message out to a stream in RFC 822 format.
     *
     * @param out    The target output stream.
     *
     * @exception MessagingException
     * @exception IOException
     */
    public void writeTo(OutputStream out) throws MessagingException, IOException {
        writeTo(out, null);
    }

    /**
     * Write the message out to a target output stream, excluding the
     * specified message headers.
     *
     * @param out    The target output stream.
     * @param ignoreHeaders
     *               An array of header types to ignore.  This can be null, which means
     *               write out all headers.
     *
     * @exception MessagingException
     * @exception IOException
     */
    public void writeTo(OutputStream out, String[] ignoreHeaders) throws MessagingException, IOException {
        // make sure everything is saved before we write
        if (!saved) {
            saveChanges();
        }

        // write out the headers first
        headers.writeTo(out, ignoreHeaders);
        // add the separater between the headers and the data portion.
        out.write('\r');
        out.write('\n');

        // if the modfied flag, we don't have current content, so the data handler needs to
        // take care of writing this data out.
        if (modified) {
            OutputStream encoderStream = MimeUtility.encode(out, getEncoding());
            dh.writeTo(encoderStream);
            encoderStream.flush();
        } else {
            // if we have content directly, we can write this out now.
            if (content != null) {
                out.write(content);
            }
            else {
                // see if we can get a content stream for this message.  We might have had one
                // explicitly set, or a subclass might override the get method to provide one.
                InputStream in = getContentStream();

                byte[] buffer = new byte[8192];
                int length = in.read(buffer);
                // copy the data stream-to-stream.
                while (length > 0) {
                    out.write(buffer, 0, length);
                    length = in.read(buffer);
                }
                in.close();
            }
        }

        // flush any data we wrote out, but do not close the stream.  That's the caller's duty.
        out.flush();
    }


    /**
     * Retrieve all headers that match a given name.
     *
     * @param name   The target name.
     *
     * @return The set of headers that match the given name.  These headers
     *         will be the decoded() header values if these are RFC 2047
     *         encoded.
     * @exception MessagingException
     */
    public String[] getHeader(String name) throws MessagingException {
        return headers.getHeader(name);
    }

    /**
     * Get all headers that match a particular name, as a single string.
     * Individual headers are separated by the provided delimiter.   If
     * the delimiter is null, only the first header is returned.
     *
     * @param name      The source header name.
     * @param delimiter The delimiter string to be used between headers.  If null, only
     *                  the first is returned.
     *
     * @return The headers concatenated as a single string.
     * @exception MessagingException
     */
    public String getHeader(String name, String delimiter) throws MessagingException {
        return headers.getHeader(name, delimiter);
    }

    /**
     * Set a new value for a named header.
     *
     * @param name   The name of the target header.
     * @param value  The new value for the header.
     *
     * @exception MessagingException
     */
    public void setHeader(String name, String value) throws MessagingException {
        headers.setHeader(name, value);
    }

    /**
     * Conditionally set or remove a named header.  If the new value
     * is null, the header is removed.
     *
     * @param name   The header name.
     * @param value  The new header value.  A null value causes the header to be
     *               removed.
     *
     * @exception MessagingException
     */
    private void setOrRemoveHeader(String name, String value) throws MessagingException {
        if (value == null) {
            headers.removeHeader(name);
        }
        else {
            headers.setHeader(name, value);
        }
    }

    /**
     * Add a new value to an existing header.  The added value is
     * created as an additional header of the same type and value.
     *
     * @param name   The name of the target header.
     * @param value  The removed header.
     *
     * @exception MessagingException
     */
    public void addHeader(String name, String value) throws MessagingException {
        headers.addHeader(name, value);
    }

    /**
     * Remove a header with the given name.
     *
     * @param name   The name of the removed header.
     *
     * @exception MessagingException
     */
    public void removeHeader(String name) throws MessagingException {
        headers.removeHeader(name);
    }

    /**
     * Retrieve the complete list of message headers, as an enumeration.
     *
     * @return An Enumeration of the message headers.
     * @exception MessagingException
     */
    public Enumeration getAllHeaders() throws MessagingException {
        return headers.getAllHeaders();
    }

    public Enumeration getMatchingHeaders(String[] names) throws MessagingException {
        return headers.getMatchingHeaders(names);
    }

    public Enumeration getNonMatchingHeaders(String[] names) throws MessagingException {
        return headers.getNonMatchingHeaders(names);
    }

    public void addHeaderLine(String line) throws MessagingException {
        headers.addHeaderLine(line);
    }

    public Enumeration getAllHeaderLines() throws MessagingException {
        return headers.getAllHeaderLines();
    }

    public Enumeration getMatchingHeaderLines(String[] names) throws MessagingException {
        return headers.getMatchingHeaderLines(names);
    }

    public Enumeration getNonMatchingHeaderLines(String[] names) throws MessagingException {
        return headers.getNonMatchingHeaderLines(names);
    }


    /**
     * Return a copy the flags associated with this message.
     *
     * @return a copy of the flags for this message
     * @throws MessagingException if there was a problem accessing the Store
     */
    public synchronized Flags getFlags() throws MessagingException {
        return (Flags) flags.clone();
    }


    /**
     * Check whether the supplied flag is set.
     * The default implementation checks the flags returned by {@link #getFlags()}.
     *
     * @param flag the flags to check for
     * @return true if the flags is set
     * @throws MessagingException if there was a problem accessing the Store
     */
    public synchronized boolean isSet(Flags.Flag flag) throws MessagingException {
        return flags.contains(flag);
    }

    /**
     * Set or clear a flag value.
     *
     * @param flags  The set of flags to effect.
     * @param set    The value to set the flag to (true or false).
     *
     * @exception MessagingException
     */
    public synchronized void setFlags(Flags flag, boolean set) throws MessagingException {
        if (set) {
            flags.add(flag);
        }
        else {
            flags.remove(flag);
        }
    }

    /**
     * Saves any changes on this message.  When called, the modified
     * and saved flags are set to true and updateHeaders() is called
     * to force updates.
     *
     * @exception MessagingException
     */
    public void saveChanges() throws MessagingException {
        // setting modified invalidates the current content.
        modified = true;
        saved = true;
        // update message headers from the content.
        updateHeaders();
    }

    /**
     * Update the internet headers so that they make sense.  This
     * will attempt to make sense of the message content type
     * given the state of the content.
     *
     * @exception MessagingException
     */
    protected void updateHeaders() throws MessagingException {

        DataHandler handler = getDataHandler();

        try {
            // figure out the content type.  If not set, we'll need to figure this out.
            String type = dh.getContentType();
            // we might need to reconcile the content type and our explicitly set type
            String explicitType = getSingleHeader("Content-Type"); 
            // parse this content type out so we can do matches/compares.
            ContentType content = new ContentType(type);

            // is this a multipart content?
            if (content.match("multipart/*")) {
                // the content is suppose to be a MimeMultipart.  Ping it to update it's headers as well.
                try {
                    MimeMultipart part = (MimeMultipart)handler.getContent();
                    part.updateHeaders();
                } catch (ClassCastException e) {
                    throw new MessagingException("Message content is not MimeMultipart", e);
                }
            }
            else if (!content.match("message/rfc822")) {
                // simple part, we need to update the header type information
                // if no encoding is set yet, figure this out from the data handler content.
                if (getSingleHeader("Content-Transfer-Encoding") == null) {
                    setHeader("Content-Transfer-Encoding", MimeUtility.getEncoding(handler));
                }

                // is a content type header set?  Check the property to see if we need to set this.
                if (explicitType == null) {
                    if (SessionUtil.getBooleanProperty(session, "MIME_MAIL_SETDEFAULTTEXTCHARSET", true)) {
                        // is this a text type?  Figure out the encoding and make sure it is set.
                        if (content.match("text/*")) {
                            // the charset should be specified as a parameter on the MIME type.  If not there,
                            // try to figure one out.
                            if (content.getParameter("charset") == null) {

                                String encoding = getEncoding();
                                // if we're sending this as 7-bit ASCII, our character set need to be
                                // compatible.
                                if (encoding != null && encoding.equalsIgnoreCase("7bit")) {
                                    content.setParameter("charset", "us-ascii");
                                }
                                else {
                                    // get the global default.
                                    content.setParameter("charset", MimeUtility.getDefaultMIMECharset());
                                }
                                // replace the original type string 
                                type = content.toString(); 
                            }
                        }
                    }
                }
            }

            // if we don't have a content type header, then create one.
            if (explicitType == null) {
                // get the disposition header, and if it is there, copy the filename parameter into the
                // name parameter of the type.
                String disp = getSingleHeader("Content-Disposition");
                if (disp != null) {
                    // parse up the string value of the disposition
                    ContentDisposition disposition = new ContentDisposition(disp);
                    // now check for a filename value
                    String filename = disposition.getParameter("filename");
                    // copy and rename the parameter, if it exists.
                    if (filename != null) {
                        content.setParameter("name", filename);
                        // set the header with the updated content type information.
                        type = content.toString();
                    }
                }
                // if no header has been set, then copy our current type string (which may 
                // have been modified above) 
                setHeader("Content-Type", type); 
            }

            // make sure we set the MIME version
            setHeader("MIME-Version", "1.0");
            // new javamail 1.4 requirement.
            updateMessageID();

        } catch (IOException e) {
            throw new MessagingException("Error updating message headers", e);
        }
    }


    /**
     * Create a new set of internet headers from the 
     * InputStream
     * 
     * @param in     The header source.
     * 
     * @return A new InternetHeaders object containing the 
     *         appropriate headers.
     * @exception MessagingException
     */
    protected InternetHeaders createInternetHeaders(InputStream in) throws MessagingException {
        // internet headers has a constructor for just this purpose
        return new InternetHeaders(in);
    }

    /**
     * Convert a header into an array of NewsAddress items.
     *
     * @param header The name of the source header.
     *
     * @return The parsed array of addresses.
     * @exception MessagingException
     */
    private Address[] getHeaderAsNewsAddresses(String header) throws MessagingException {
        // NB:  We're using getHeader() here to allow subclasses an opportunity to perform lazy loading
        // of the headers.
        String mergedHeader = getHeader(header, ",");
        if (mergedHeader != null) {
            return NewsAddress.parse(mergedHeader);
        }
        return null;
    }

    private Address[] getHeaderAsInternetAddresses(String header, boolean strict) throws MessagingException {
        // NB:  We're using getHeader() here to allow subclasses an opportunity to perform lazy loading
        // of the headers.
        String mergedHeader = getHeader(header, ",");

        if (mergedHeader != null) {
            return InternetAddress.parseHeader(mergedHeader, strict);
        }
        return null;
    }

    /**
     * Check to see if we require strict addressing on parsing
     * internet headers.
     *
     * @return The current value of the "mail.mime.address.strict" session
     *         property, or true, if the property is not set.
     */
    private boolean isStrictAddressing() {
        return SessionUtil.getBooleanProperty(session, MIME_ADDRESS_STRICT, true);
    }

    /**
     * Set a named header to the value of an address field.
     *
     * @param header  The header name.
     * @param address The address value.  If the address is null, the header is removed.
     *
     * @exception MessagingException
     */
    private void setHeader(String header, Address address) throws MessagingException {
        if (address == null) {
            removeHeader(header);
        }
        else {
            setHeader(header, address.toString());
        }
    }

    /**
     * Set a header to a list of addresses.
     *
     * @param header    The header name.
     * @param addresses An array of addresses to set the header to.  If null, the
     *                  header is removed.
     */
    private void setHeader(String header, Address[] addresses) {
        if (addresses == null) {
            headers.removeHeader(header);
        }
        else {
            headers.setHeader(header, addresses);
        }
    }

    private void addHeader(String header, Address[] addresses) throws MessagingException {
        headers.addHeader(header, InternetAddress.toString(addresses));
    }

    private String getHeaderForRecipientType(Message.RecipientType type) throws MessagingException {
        if (RecipientType.TO == type) {
            return "To";
        } else if (RecipientType.CC == type) {
            return "Cc";
        } else if (RecipientType.BCC == type) {
            return "Bcc";
        } else if (RecipientType.NEWSGROUPS == type) {
            return "Newsgroups";
        } else {
            throw new MessagingException("Unsupported recipient type: " + type.toString());
        }
    }

    /**
     * Utility routine to get a header as a single string value
     * rather than an array of headers.
     *
     * @param name   The name of the header.
     *
     * @return The single string header value.  If multiple headers exist,
     *         the additional ones are ignored.
     * @exception MessagingException
     */
    private String getSingleHeader(String name) throws MessagingException {
        String[] values = getHeader(name);
        if (values == null || values.length == 0) {
            return null;
        } else {
            return values[0];
        }
    }

    /**
     * Update the message identifier after headers have been updated.
     *
     * The default message id is composed of the following items:
     *
     * 1)  A newly created object's hash code.
     * 2)  A uniqueness counter
     * 3)  The current time in milliseconds
     * 4)  The string JavaMail
     * 5)  The user's local address as returned by InternetAddress.getLocalAddress().
     *
     * @exception MessagingException
     */
    protected void updateMessageID() throws MessagingException {
        StringBuffer id = new StringBuffer();

        id.append('<');
        id.append(new Object().hashCode());
        id.append('.');
        id.append(messageID++);
        id.append(System.currentTimeMillis());
        id.append('.');
        id.append("JavaMail.");

        // get the local address and apply a suitable default.

        InternetAddress localAddress = InternetAddress.getLocalAddress(session);
        if (localAddress != null) {
            id.append(localAddress.getAddress());
        }
        else {
            id.append("javamailuser@localhost");
        }
        id.append('>');

        setHeader("Message-ID", id.toString());
    }

    /**
     * Method used to create a new MimeMessage instance.  This method
     * is used whenever the MimeMessage class needs to create a new
     * Message instance (e.g, reply()).  This method allows subclasses
     * to override the class of message that gets created or set
     * default values, if needed.
     *
     * @param session The session associated with this message.
     *
     * @return A newly create MimeMessage instance.
     * @throws javax.mail.MessagingException if the MimeMessage could not be created
     */
    protected MimeMessage createMimeMessage(Session session) throws javax.mail.MessagingException {
        return new MimeMessage(session);
    }

}
