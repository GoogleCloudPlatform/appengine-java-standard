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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Date;
import javax.mail.search.SearchTerm;

/**
 * @version $Rev: 578802 $ $Date: 2007-09-24 08:16:44 -0500 (Mon, 24 Sep 2007) $
 */
public abstract class Message implements Part {
    /**
     * Enumeration of types of recipients allowed by the Message class.
     */
    public static class RecipientType implements Serializable {
        /**
         * A "To" or primary recipient.
         */
        public static final RecipientType TO = new RecipientType("To");
        /**
         * A "Cc" or carbon-copy recipient.
         */
        public static final RecipientType CC = new RecipientType("Cc");
        /**
         * A "Bcc" or blind carbon-copy recipient.
         */
        public static final RecipientType BCC = new RecipientType("Bcc");
        protected String type;

        protected RecipientType(String type) {
            this.type = type;
        }

        protected Object readResolve() throws ObjectStreamException {
            if (type.equals("To")) {
                return TO;
            } else if (type.equals("Cc")) {
                return CC;
            } else if (type.equals("Bcc")) {
                return BCC;
            } else {
                throw new InvalidObjectException("Invalid RecipientType: " + type);
            }
        }

        public String toString() {
            return type;
        }
    }

    /**
     * The index of a message within its folder, or zero if the message was not retrieved from a folder.
     */
    protected int msgnum;
    /**
     * True if this message has been expunged from the Store.
     */
    protected boolean expunged;
    /**
     * The {@link Folder} that contains this message, or null if it was not obtained from a folder.
     */
    protected Folder folder;
    /**
     * The {@link Session} associated with this message.
     */
    protected Session session;

    /**
     * Default constructor.
     */
    protected Message() {
    }

    /**
     * Constructor initializing folder and message msgnum; intended to be used by implementations of Folder.
     *
     * @param folder the folder that contains the message
     * @param msgnum the message index within the folder
     */
    protected Message(Folder folder, int msgnum) {
        this.folder = folder;
        this.msgnum = msgnum;
        // make sure we copy the session information from the folder.
        this.session = folder.getStore().getSession();
    }

    /**
     * Constructor initializing the session; intended to by used by client created instances.
     *
     * @param session the session associated with this message
     */
    protected Message(Session session) {
        this.session = session;
    }

    /**
     * Return the "From" header indicating the identity of the person the message is from;
     * in some circumstances this may be different than the actual sender.
     *
     * @return a list of addresses this message is from; may be empty if the header is present but empty, or null
     *         if the header is not present
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract Address[] getFrom() throws MessagingException;

    /**
     * Set the "From" header for this message to the value of the "mail.user" property,
     * or if that property is not set, to the value of the system property "user.name"
     *
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void setFrom() throws MessagingException;

    /**
     * Set the "From" header to the supplied address.
     *
     * @param address the address of the person the message is from
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void setFrom(Address address) throws MessagingException;

    /**
     * Add multiple addresses to the "From" header.
     *
     * @param addresses the addresses to add
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void addFrom(Address[] addresses) throws MessagingException;

    /**
     * Get all recipients of the given type.
     *
     * @param type the type of recipient to get
     * @return a list of addresses; may be empty if the header is present but empty,
     *         or null if the header is not present
     * @throws MessagingException if there was a problem accessing the Store
     * @see RecipientType
     */
    public abstract Address[] getRecipients(RecipientType type) throws MessagingException;

    /**
     * Get all recipients of this message.
     * The default implementation extracts the To, Cc, and Bcc recipients using {@link #getRecipients(javax.mail.Message.RecipientType)}
     * and then concatentates the results into a single array; it returns null if no headers are defined.
     *
     * @return an array containing all recipients
     * @throws MessagingException if there was a problem accessing the Store
     */
    public Address[] getAllRecipients() throws MessagingException {
        Address[] to = getRecipients(RecipientType.TO);
        Address[] cc = getRecipients(RecipientType.CC);
        Address[] bcc = getRecipients(RecipientType.BCC);
        if (to == null && cc == null && bcc == null) {
            return null;
        }
        int length = (to != null ? to.length : 0) + (cc != null ? cc.length : 0) + (bcc != null ? bcc.length : 0);
        Address[] result = new Address[length];
        int j = 0;
        if (to != null) {
            for (int i = 0; i < to.length; i++) {
                result[j++] = to[i];
            }
        }
        if (cc != null) {
            for (int i = 0; i < cc.length; i++) {
                result[j++] = cc[i];
            }
        }
        if (bcc != null) {
            for (int i = 0; i < bcc.length; i++) {
                result[j++] = bcc[i];
            }
        }
        return result;
    }

    /**
     * Set the list of recipients for the specified type.
     *
     * @param type      the type of recipient
     * @param addresses the new addresses
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void setRecipients(RecipientType type, Address[] addresses) throws MessagingException;

    /**
     * Set the list of recipients for the specified type to a single address.
     *
     * @param type    the type of recipient
     * @param address the new address
     * @throws MessagingException if there was a problem accessing the Store
     */
    public void setRecipient(RecipientType type, Address address) throws MessagingException {
        setRecipients(type, new Address[]{address});
    }

    /**
     * Add recipents of a specified type.
     *
     * @param type      the type of recipient
     * @param addresses the addresses to add
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void addRecipients(RecipientType type, Address[] addresses) throws MessagingException;

    /**
     * Add a recipent of a specified type.
     *
     * @param type    the type of recipient
     * @param address the address to add
     * @throws MessagingException if there was a problem accessing the Store
     */
    public void addRecipient(RecipientType type, Address address) throws MessagingException {
        addRecipients(type, new Address[]{address});
    }

    /**
     * Get the addresses to which replies should be directed.
     * <p/>
     * As the most common behavior is to return to sender, the default implementation
     * simply calls {@link #getFrom()}.
     *
     * @return a list of addresses to which replies should be directed
     * @throws MessagingException if there was a problem accessing the Store
     */
    public Address[] getReplyTo() throws MessagingException {
        return getFrom();
    }

    /**
     * Set the addresses to which replies should be directed.
     * <p/>
     * The default implementation throws a MethodNotSupportedException.
     *
     * @param addresses to which replies should be directed
     * @throws MessagingException if there was a problem accessing the Store
     */
    public void setReplyTo(Address[] addresses) throws MessagingException {
        throw new MethodNotSupportedException("setReplyTo not supported");
    }

    /**
     * Get the subject for this message.
     *
     * @return the subject
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract String getSubject() throws MessagingException;

    /**
     * Set the subject of this message
     *
     * @param subject the subject
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void setSubject(String subject) throws MessagingException;

    /**
     * Return the date that this message was sent.
     *
     * @return the date this message was sent
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract Date getSentDate() throws MessagingException;

    /**
     * Set the date this message was sent.
     *
     * @param sent the date when this message was sent
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void setSentDate(Date sent) throws MessagingException;

    /**
     * Return the date this message was received.
     *
     * @return the date this message was received
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract Date getReceivedDate() throws MessagingException;

    /**
     * Return a copy the flags associated with this message.
     *
     * @return a copy of the flags for this message
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract Flags getFlags() throws MessagingException;

    /**
     * Check whether the supplied flag is set.
     * The default implementation checks the flags returned by {@link #getFlags()}.
     *
     * @param flag the flags to check for
     * @return true if the flags is set
     * @throws MessagingException if there was a problem accessing the Store
     */
    public boolean isSet(Flags.Flag flag) throws MessagingException {
        return getFlags().contains(flag);
    }

    /**
     * Set the flags specified to the supplied value; flags not included in the
     * supplied {@link Flags} parameter are not affected.
     *
     * @param flags the flags to modify
     * @param set   the new value of those flags
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void setFlags(Flags flags, boolean set) throws MessagingException;

    /**
     * Set a flag to the supplied value.
     * The default implmentation uses {@link #setFlags(Flags, boolean)}.
     *
     * @param flag the flag to set
     * @param set  the value for that flag
     * @throws MessagingException if there was a problem accessing the Store
     */
    public void setFlag(Flags.Flag flag, boolean set) throws MessagingException {
        setFlags(new Flags(flag), set);
    }

    /**
     * Return the message number for this Message.
     * This number refers to the relative position of this message in a Folder; the message
     * number for any given message can change during a session if the Folder is expunged.
     * Message numbers for messages in a folder start at one; the value zero indicates that
     * this message does not belong to a folder.
     *
     * @return the message number
     */
    public int getMessageNumber() {
        return msgnum;
    }

    /**
     * Set the message number for this Message.
     * This must be invoked by implementation classes when the message number changes.
     *
     * @param number the new message number
     */
    protected void setMessageNumber(int number) {
        msgnum = number;
    }

    /**
     * Return the folder containing this message. If this is a new or nested message
     * then this method returns null.
     *
     * @return the folder containing this message
     */
    public Folder getFolder() {
        return folder;
    }

    /**
     * Checks to see if this message has been expunged. If true, all methods other than
     * {@link #getMessageNumber()} are invalid.
     *
     * @return true if this method has been expunged
     */
    public boolean isExpunged() {
        return expunged;
    }

    /**
     * Set the expunged flag for this message.
     *
     * @param expunged true if this message has been expunged
     */
    protected void setExpunged(boolean expunged) {
        this.expunged = expunged;
    }

    /**
     * Create a new message suitable as a reply to this message with all headers set
     * up appropriately. The message body will be empty.
     * <p/>
     * if replyToAll is set then the new message will be addressed to all recipients
     * of this message; otherwise the reply will be addressed only to the sender as
     * returned by {@link #getReplyTo()}.
     * <p/>
     * The subject field will be initialized with the subject field from the orginal
     * message; the text "Re:" will be prepended unless it is already present.
     *
     * @param replyToAll if true, indciates the message should be addressed to all recipients not just the sender
     * @return a new message suitable as a reply to this message
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract Message reply(boolean replyToAll) throws MessagingException;

    /**
     * To ensure changes are saved to the Store, this message should be invoked
     * before its containing Folder is closed. Implementations may save modifications
     * immediately but are free to defer such updates to they may be sent to the server
     * in one batch; if saveChanges is not called then such changes may not be made
     * permanent.
     *
     * @throws MessagingException if there was a problem accessing the Store
     */
    public abstract void saveChanges() throws MessagingException;

    /**
     * Apply the specified search criteria to this message
     *
     * @param term the search criteria
     * @return true if this message matches the search criteria.
     * @throws MessagingException if there was a problem accessing the Store
     */
    public boolean match(SearchTerm term) throws MessagingException {
        return term.match(this);
    }
}
