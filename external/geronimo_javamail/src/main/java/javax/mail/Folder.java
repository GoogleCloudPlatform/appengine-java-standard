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

import java.util.ArrayList;
import java.util.List;

import javax.mail.Flags.Flag;
import javax.mail.event.ConnectionEvent;
import javax.mail.event.ConnectionListener;
import javax.mail.event.FolderEvent;
import javax.mail.event.FolderListener;
import javax.mail.event.MailEvent;
import javax.mail.event.MessageChangedEvent;
import javax.mail.event.MessageChangedListener;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.search.SearchTerm;

/**
 * An abstract representation of a folder in a mail system; subclasses would
 * implement Folders for each supported protocol.
 * <p/>
 * Depending on protocol and implementation, folders may contain other folders, messages,
 * or both as indicated by the {@link Folder#HOLDS_FOLDERS} and {@link Folder#HOLDS_MESSAGES} flags.
 * If the immplementation supports hierarchical folders, the format of folder names is
 * implementation dependent; however, components of the name are separated by the
 * delimiter character returned by {@link Folder#getSeparator()}.
 * <p/>
 * The case-insensitive folder name "INBOX" is reserved to refer to the primary folder
 * for the current user on the current server; not all stores will provide an INBOX
 * and it may not be available at all times.
 *
 * @version $Rev: 582780 $ $Date: 2007-10-08 06:17:15 -0500 (Mon, 08 Oct 2007) $
 */
public abstract class Folder {
    /**
     * Flag that indicates that a folder can contain messages.
     */
    public static final int HOLDS_MESSAGES = 1;
    /**
     * Flag that indicates that a folder can contain other folders.
     */
    public static final int HOLDS_FOLDERS = 2;

    /**
     * Flag indicating that this folder cannot be modified.
     */
    public static final int READ_ONLY = 1;
    /**
     * Flag indictaing that this folder can be modified.
     * Question: what does it mean if both are set?
     */
    public static final int READ_WRITE = 2;

    /**
     * The store that this folder is part of.
     */
    protected Store store;
    /**
     * The current mode of this folder.
     * When open, this can be {@link #READ_ONLY} or {@link #READ_WRITE};
     * otherwise is set to -1.
     */
    protected int mode = -1;

    private final ArrayList connectionListeners = new ArrayList(2);
    private final ArrayList folderListeners = new ArrayList(2);
    private final ArrayList messageChangedListeners = new ArrayList(2);
    private final ArrayList messageCountListeners = new ArrayList(2);
    // the EventQueue spins off a new thread, so we only create this 
    // if we have actual listeners to dispatch an event to. 
    private EventQueue queue = null;

    /**
     * Constructor that initializes the Store.
     *
     * @param store the store that this folder is part of
     */
    protected Folder(Store store) {
        this.store = store;
    }

    /**
     * Return the name of this folder.
     * This can be invoked when the folder is closed.
     *
     * @return this folder's name
     */
    public abstract String getName();

    /**
     * Return the full absolute name of this folder.
     * This can be invoked when the folder is closed.
     *
     * @return the full name of this folder
     */
    public abstract String getFullName();

    /**
     * Return the URLName for this folder, which includes the location of the store.
     *
     * @return the URLName for this folder
     * @throws MessagingException
     */
    public URLName getURLName() throws MessagingException {
        URLName baseURL = store.getURLName(); 
        return new URLName(baseURL.getProtocol(), baseURL.getHost(), baseURL.getPort(), 
            getFullName(), baseURL.getUsername(), null); 
    }

    /**
     * Return the store that this folder is part of.
     *
     * @return the store this folder is part of
     */
    public Store getStore() {
        return store;
    }

    /**
     * Return the parent for this folder; if the folder is at the root of a heirarchy
     * this returns null.
     * This can be invoked when the folder is closed.
     *
     * @return this folder's parent
     * @throws MessagingException
     */
    public abstract Folder getParent() throws MessagingException;

    /**
     * Check to see if this folder physically exists in the store.
     * This can be invoked when the folder is closed.
     *
     * @return true if the folder really exists
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract boolean exists() throws MessagingException;

    /**
     * Return a list of folders from this Folder's namespace that match the supplied pattern.
     * Patterns may contain the following wildcards:
     * <ul><li>'%' which matches any characater except hierarchy delimiters</li>
     * <li>'*' which matches any character including hierarchy delimiters</li>
     * </ul>
     * This can be invoked when the folder is closed.
     *
     * @param pattern the pattern to search for
     * @return a, possibly empty, array containing Folders that matched the pattern
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract Folder[] list(String pattern) throws MessagingException;

    /**
     * Return a list of folders to which the user is subscribed and which match the supplied pattern.
     * If the store does not support the concept of subscription then this should match against
     * all folders; the default implementation of this method achieves this by defaulting to the
     * {@link #list(String)} method.
     *
     * @param pattern the pattern to search for
     * @return a, possibly empty, array containing subscribed Folders that matched the pattern
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] listSubscribed(String pattern) throws MessagingException {
        return list(pattern);
    }

    /**
     * Convenience method that invokes {@link #list(String)} with the pattern "%".
     *
     * @return a, possibly empty, array of subfolders
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] list() throws MessagingException {
        return list("%");
    }

    /**
     * Convenience method that invokes {@link #listSubscribed(String)} with the pattern "%".
     *
     * @return a, possibly empty, array of subscribed subfolders
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] listSubscribed() throws MessagingException {
        return listSubscribed("%");
    }

    /**
     * Return the character used by this folder's Store to separate path components.
     *
     * @return the name separater character
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract char getSeparator() throws MessagingException;

    /**
     * Return the type of this folder, indicating whether it can contain subfolders,
     * messages, or both. The value returned is a bitmask with the appropriate bits set.
     *
     * @return the type of this folder
     * @throws MessagingException if there was a problem accessing the store
     * @see #HOLDS_FOLDERS
     * @see #HOLDS_MESSAGES
     */
    public abstract int getType() throws MessagingException;

    /**
     * Create a new folder capable of containing subfoldera and/or messages as
     * determined by the type parameter. Any hierarchy defined by the folder
     * name will be recursively created.
     * If the folder was sucessfully created, a {@link FolderEvent#CREATED CREATED FolderEvent}
     * is sent to all FolderListeners registered with this Folder or with the Store.
     *
     * @param type the type, indicating if this folder should contain subfolders, messages or both
     * @return true if the folder was sucessfully created
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract boolean create(int type) throws MessagingException;

    /**
     * Determine if the user is subscribed to this Folder. The default implementation in
     * this class always returns true.
     *
     * @return true is the user is subscribed to this Folder
     */
    public boolean isSubscribed() {
        return true;
    }

    /**
     * Set the user's subscription to this folder.
     * Not all Stores support subscription; the default implementation in this class
     * always throws a MethodNotSupportedException
     *
     * @param subscribed whether to subscribe to this Folder
     * @throws MessagingException          if there was a problem accessing the store
     * @throws MethodNotSupportedException if the Store does not support subscription
     */
    public void setSubscribed(boolean subscribed) throws MessagingException {
        throw new MethodNotSupportedException();
    }

    /**
     * Check to see if this Folder conatins messages with the {@link Flag.RECENT} flag set.
     * This can be used when the folder is closed to perform a light-weight check for new mail;
     * to perform an incremental check for new mail the folder must be opened.
     *
     * @return true if the Store has recent messages
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract boolean hasNewMessages() throws MessagingException;

    /**
     * Get the Folder determined by the supplied name; if the name is relative
     * then it is interpreted relative to this folder. This does not check that
     * the named folder actually exists.
     *
     * @param name the name of the folder to return
     * @return the named folder
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract Folder getFolder(String name) throws MessagingException;

    /**
     * Delete this folder and possibly any subfolders. This operation can only be
     * performed on a closed folder.
     * If recurse is true, then all subfolders are deleted first, then any messages in
     * this folder are removed and it is finally deleted; {@link FolderEvent#DELETED}
     * events are sent as appropriate.
     * If recurse is false, then the behaviour depends on the folder type and store
     * implementation as followd:
     * <ul>
     * <li>If the folder can only conrain messages, then all messages are removed and
     * then the folder is deleted; a {@link FolderEvent#DELETED} event is sent.</li>
     * <li>If the folder can onlu contain subfolders, then if it is empty it will be
     * deleted and a {@link FolderEvent#DELETED} event is sent; if the folder is not
     * empty then the delete fails and this method returns false.</li>
     * <li>If the folder can contain both subfolders and messages, then if the folder
     * does not contain any subfolders, any messages are deleted, the folder itself
     * is deleted and a {@link FolderEvent#DELETED} event is sent; if the folder does
     * contain subfolders then the implementation may choose from the following three
     * behaviors:
     * <ol>
     * <li>it may return false indicting the operation failed</li>
     * <li>it may remove all messages within the folder, send a {@link FolderEvent#DELETED}
     * event, and then return true to indicate the delete was performed. Note this does
     * not delete the folder itself and the {@link #exists()} operation for this folder
     * will return true</li>
     * <li>it may remove all messages within the folder as per the previous option; in
     * addition it may change the type of the Folder to only HOLDS_FOLDERS indictaing
     * that messages may no longer be added</li>
     * </li>
     * </ul>
     * FolderEvents are sent to all listeners registered with this folder or
     * with the Store.
     *
     * @param recurse whether subfolders should be recursively deleted as well
     * @return true if the delete operation succeeds
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract boolean delete(boolean recurse) throws MessagingException;

    /**
     * Rename this folder; the folder must be closed.
     * If the rename is successfull, a {@link FolderEvent#RENAMED} event is sent to
     * all listeners registered with this folder or with the store.
     *
     * @param newName the new name for this folder
     * @return true if the rename succeeded
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract boolean renameTo(Folder newName) throws MessagingException;

    /**
     * Open this folder; the folder must be able to contain messages and
     * must currently be closed. If the folder is opened successfully then
     * a {@link ConnectionEvent#OPENED} event is sent to listeners registered
     * with this Folder.
     * <p/>
     * Whether the Store allows multiple connections or if it allows multiple
     * writers is implementation defined.
     *
     * @param mode READ_ONLY or READ_WRITE
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract void open(int mode) throws MessagingException;

    /**
     * Close this folder; it must already be open.
     * A {@link ConnectionEvent#CLOSED} event is sent to all listeners registered
     * with this folder.
     *
     * @param expunge whether to expunge all deleted messages
     * @throws MessagingException if there was a problem accessing the store; the folder is still closed
     */
    public abstract void close(boolean expunge) throws MessagingException;

    /**
     * Indicates that the folder has been opened.
     *
     * @return true if the folder is open
     */
    public abstract boolean isOpen();

    /**
     * Return the mode of this folder ass passed to {@link #open(int)}, or -1 if
     * the folder is closed.
     *
     * @return the mode this folder was opened with
     */
    public int getMode() {
        return mode;
    }

    /**
     * Get the flags supported by this folder.
     *
     * @return the flags supported by this folder, or null if unknown
     * @see Flags
     */
    public abstract Flags getPermanentFlags();

    /**
     * Return the number of messages this folder contains.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     *
     * @return the number of messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract int getMessageCount() throws MessagingException;

    /**
     * Return the numbew of messages in this folder that have the {@link Flag.RECENT} flag set.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     * The default implmentation of this method iterates over all messages
     * in the folder; subclasses should override if possible to provide a more
     * efficient implementation.
     *
     * @return the number of new messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
    public int getNewMessageCount() throws MessagingException {
        return getCount(Flags.Flag.RECENT, true);
    }

    /**
     * Return the numbew of messages in this folder that do not have the {@link Flag.SEEN} flag set.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     * The default implmentation of this method iterates over all messages
     * in the folder; subclasses should override if possible to provide a more
     * efficient implementation.
     *
     * @return the number of new messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
    public int getUnreadMessageCount() throws MessagingException {
        return getCount(Flags.Flag.SEEN, false);
    }

    /**
     * Return the numbew of messages in this folder that have the {@link Flag.DELETED} flag set.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     * The default implmentation of this method iterates over all messages
     * in the folder; subclasses should override if possible to provide a more
     * efficient implementation.
     *
     * @return the number of new messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
    public int getDeletedMessageCount() throws MessagingException {
        return getCount(Flags.Flag.DELETED, true);
    }

    private int getCount(Flag flag, boolean value) throws MessagingException {
        if (!isOpen()) {
            return -1;
        }
        Message[] messages = getMessages();
        int total = 0;
        for (int i = 0; i < messages.length; i++) {
            if (messages[i].getFlags().contains(flag) == value) {
                total++;
            }
        }
        return total;
    }

    /**
     * Retrieve the message with the specified index in this Folder;
     * messages indices start at 1 not zero.
     * Clients should note that the index for a specific message may change
     * if the folder is expunged; {@link Message} objects should be used as
     * references instead.
     *
     * @param index the index of the message to fetch
     * @return the message
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract Message getMessage(int index) throws MessagingException;

    /**
     * Retrieve messages with index between start and end inclusive
     *
     * @param start index of first message
     * @param end   index of last message
     * @return an array of messages from start to end inclusive
     * @throws MessagingException if there was a problem accessing the store
     */
    public Message[] getMessages(int start, int end) throws MessagingException {
        Message[] result = new Message[end - start + 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = getMessage(start++);
        }
        return result;
    }

    /**
     * Retrieve messages with the specified indices.
     *
     * @param ids the indices of the messages to fetch
     * @return the specified messages
     * @throws MessagingException if there was a problem accessing the store
     */
    public Message[] getMessages(int ids[]) throws MessagingException {
        Message[] result = new Message[ids.length];
        for (int i = 0; i < ids.length; i++) {
            result[i] = getMessage(ids[i]);
        }
        return result;
    }

    /**
     * Retrieve all messages.
     *
     * @return all messages in this folder
     * @throws MessagingException if there was a problem accessing the store
     */
    public Message[] getMessages() throws MessagingException {
        return getMessages(1, getMessageCount());
    }

    /**
     * Append the supplied messages to this folder. A {@link MessageCountEvent} is sent
     * to all listeners registered with this folder when all messages have been appended.
     * If the array contains a previously expunged message, it must be re-appended to the Store
     * and implementations must not abort this operation.
     *
     * @param messages the messages to append
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract void appendMessages(Message[] messages) throws MessagingException;

    /**
     * Hint to the store to prefetch information on the supplied messaged.
     * Subclasses should override this method to provide an efficient implementation;
     * the default implementation in this class simply returns.
     *
     * @param messages messages for which information should be fetched
     * @param profile  the information to fetch
     * @throws MessagingException if there was a problem accessing the store
     * @see FetchProfile
     */
    public void fetch(Message[] messages, FetchProfile profile) throws MessagingException {
        return;
    }

    /**
     * Set flags on the messages to the supplied value; all messages must belong to this folder.
     * This method may be overridden by subclasses that can optimize the setting
     * of flags on multiple messages at once; the default implementation simply calls
     * {@link Message#setFlags(Flags, boolean)} for each supplied messages.
     *
     * @param messages whose flags should be set
     * @param flags    the set of flags to modify
     * @param value    the value the flags should be set to
     * @throws MessagingException if there was a problem accessing the store
     */
    public void setFlags(Message[] messages, Flags flags, boolean value) throws MessagingException {
        for (int i = 0; i < messages.length; i++) {
            Message message = messages[i];
            message.setFlags(flags, value);
        }
    }

    /**
     * Set flags on a range of messages to the supplied value.
     * This method may be overridden by subclasses that can optimize the setting
     * of flags on multiple messages at once; the default implementation simply
     * gets each message and then calls {@link Message#setFlags(Flags, boolean)}.
     *
     * @param start first message end set
     * @param end   last message end set
     * @param flags the set of flags end modify
     * @param value the value the flags should be set end
     * @throws MessagingException if there was a problem accessing the store
     */
    public void setFlags(int start, int end, Flags flags, boolean value) throws MessagingException {
        for (int i = start; i <= end; i++) {
            Message message = getMessage(i);
            message.setFlags(flags, value);
        }
    }

    /**
     * Set flags on a set of messages to the supplied value.
     * This method may be overridden by subclasses that can optimize the setting
     * of flags on multiple messages at once; the default implementation simply
     * gets each message and then calls {@link Message#setFlags(Flags, boolean)}.
     *
     * @param ids   the indexes of the messages to set
     * @param flags the set of flags end modify
     * @param value the value the flags should be set end
     * @throws MessagingException if there was a problem accessing the store
     */
    public void setFlags(int ids[], Flags flags, boolean value) throws MessagingException {
        for (int i = 0; i < ids.length; i++) {
            Message message = getMessage(ids[i]);
            message.setFlags(flags, value);
        }
    }

    /**
     * Copy the specified messages to another folder.
     * The default implementation simply appends the supplied messages to the
     * target folder using {@link #appendMessages(Message[])}.
     * @param messages the messages to copy
     * @param folder the folder to copy to
     * @throws MessagingException if there was a problem accessing the store
     */
    public void copyMessages(Message[] messages, Folder folder) throws MessagingException {
        folder.appendMessages(messages);
    }

    /**
     * Permanently delete all supplied messages that have the DELETED flag set from the Store.
     * The original message indices of all messages actually deleted are returned and a
     * {@link MessageCountEvent} event is sent to all listeners with this folder. The expunge
     * may cause the indices of all messaged that remain in the folder to change.
     *
     * @return the original indices of messages that were actually deleted
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract Message[] expunge() throws MessagingException;

    /**
     * Search this folder for messages matching the supplied search criteria.
     * The default implementation simply invoke <code>search(term, getMessages())
     * applying the search over all messages in the folder; subclasses may provide
     * a more efficient mechanism.
     *
     * @param term the search criteria
     * @return an array containing messages that match the criteria
     * @throws MessagingException if there was a problem accessing the store
     */
    public Message[] search(SearchTerm term) throws MessagingException {
        return search(term, getMessages());
    }

    /**
     * Search the supplied messages for those that match the supplied criteria;
     * messages must belong to this folder.
     * The default implementation iterates through the messages, returning those
     * whose {@link Message#match(javax.mail.search.SearchTerm)} method returns true;
     * subclasses may provide a more efficient implementation.
     *
     * @param term the search criteria
     * @param messages the messages to search
     * @return an array containing messages that match the criteria
     * @throws MessagingException if there was a problem accessing the store
     */
    public Message[] search(SearchTerm term, Message[] messages) throws MessagingException {
        List result = new ArrayList(messages.length);
        for (int i = 0; i < messages.length; i++) {
            Message message = messages[i];
            if (message.match(term)) {
                result.add(message);
            }
        }
        return (Message[]) result.toArray(new Message[result.size()]);
    }

    public void addConnectionListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }

    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    protected void notifyConnectionListeners(int type) {
        queueEvent(new ConnectionEvent(this, type), connectionListeners);
    }

    public void addFolderListener(FolderListener listener) {
        folderListeners.add(listener);
    }

    public void removeFolderListener(FolderListener listener) {
        folderListeners.remove(listener);
    }

    protected void notifyFolderListeners(int type) {
        queueEvent(new FolderEvent(this, this, type), folderListeners);
    }

    protected void notifyFolderRenamedListeners(Folder newFolder) {
        queueEvent(new FolderEvent(this, this, newFolder, FolderEvent.RENAMED), folderListeners);
    }

    public void addMessageCountListener(MessageCountListener listener) {
        messageCountListeners.add(listener);
    }

    public void removeMessageCountListener(MessageCountListener listener) {
        messageCountListeners.remove(listener);
    }

    protected void notifyMessageAddedListeners(Message[] messages) {
        queueEvent(new MessageCountEvent(this, MessageCountEvent.ADDED, false, messages), messageChangedListeners);
    }

    protected void notifyMessageRemovedListeners(boolean removed, Message[] messages) {
        queueEvent(new MessageCountEvent(this, MessageCountEvent.REMOVED, removed, messages), messageChangedListeners);
    }

    public void addMessageChangedListener(MessageChangedListener listener) {
        messageChangedListeners.add(listener);
    }

    public void removeMessageChangedListener(MessageChangedListener listener) {
        messageChangedListeners.remove(listener);
    }

    protected void notifyMessageChangedListeners(int type, Message message) {
        queueEvent(new MessageChangedEvent(this, type, message), messageChangedListeners);
    }

    /**
     * Unregisters all listeners.
     */
    protected void finalize() throws Throwable {
        // shut our queue down, if needed. 
        if (queue != null) {
            queue.stop();
            queue = null; 
        }
        connectionListeners.clear();
        folderListeners.clear();
        messageChangedListeners.clear();
        messageCountListeners.clear();
        store = null;
        super.finalize();
    }

    /**
     * Returns the full name of this folder; if null, returns the value from the superclass.
     * @return a string form of this folder
     */
    public String toString() {
        String name = getFullName();
        return name == null ? super.toString() : name;
    }
    
    
    /**
     * Add an event on the event queue, creating the queue if this is the 
     * first event with actual listeners. 
     * 
     * @param event     The event to dispatch.
     * @param listeners The listener list.
     */
    private synchronized void queueEvent(MailEvent event, ArrayList listeners) {
        // if there are no listeners to dispatch this to, don't put it on the queue. 
        // This allows us to delay creating the queue (and its new thread) until 
        // we 
        if (listeners.isEmpty()) {
            return; 
        }
        // first real event?  Time to get the queue kicked off. 
        if (queue == null) {
            queue = new EventQueue(); 
        }
        // tee it up and let it rip. 
        queue.queueEvent(event, (List)listeners.clone()); 
    }
}
