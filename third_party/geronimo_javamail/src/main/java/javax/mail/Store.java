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

import java.util.Vector;
import javax.mail.event.FolderEvent;
import javax.mail.event.FolderListener;
import javax.mail.event.StoreEvent;
import javax.mail.event.StoreListener;

/**
 * Abstract class that represents a message store.
 *
 * @version $Rev: 578802 $ $Date: 2007-09-24 08:16:44 -0500 (Mon, 24 Sep 2007) $
 */
public abstract class Store extends Service {
    private static final Folder[] FOLDER_ARRAY = new Folder[0];
    private final Vector folderListeners = new Vector(2);
    private final Vector storeListeners = new Vector(2);

    /**
     * Constructor specifying session and url of this store.
     * Subclasses MUST provide a constructor with this signature.
     *
     * @param session the session associated with this store
     * @param name the URL of the store
     */
    protected Store(Session session, URLName name) {
        super(session, name);
    }

    /**
     * Return a Folder object that represents the root of the namespace for the current user.
     *
     * Note that in some store configurations (such as IMAP4) the root folder might
     * not be the INBOX folder.
     *
     * @return the root Folder
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract Folder getDefaultFolder() throws MessagingException;

    /**
     * Return the Folder corresponding to the given name.
     * The folder might not physically exist; the {@link Folder#exists()} method can be used
     * to determine if it is real.
     * @param name the name of the Folder to return
     * @return the corresponding folder
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract Folder getFolder(String name) throws MessagingException;

    /**
     * Return the folder identified by the URLName; the URLName must refer to this Store.
     * Implementations may use the {@link URLName#getFile()} method to determined the folder name.
     *
     * @param name the folder to return
     * @return the corresponding folder
     * @throws MessagingException if there was a problem accessing the store
     */
    public abstract Folder getFolder(URLName name) throws MessagingException;

    /**
     * Return the root folders of the personal namespace belonging to the current user.
     *
     * The default implementation simply returns an array containing the folder returned by {@link #getDefaultFolder()}.
     * @return the root folders of the user's peronal namespaces
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] getPersonalNamespaces() throws MessagingException {
        return new Folder[]{getDefaultFolder()};
    }

    /**
     * Return the root folders of the personal namespaces belonging to the supplied user.
     *
     * The default implementation simply returns an empty array.
     *
     * @param user the user whose namespaces should be returned
     * @return the root folders of the given user's peronal namespaces
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] getUserNamespaces(String user) throws MessagingException {
        return FOLDER_ARRAY;
    }

    /**
     * Return the root folders of namespaces that are intended to be shared between users.
     *
     * The default implementation simply returns an empty array.
     * @return the root folders of all shared namespaces
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] getSharedNamespaces() throws MessagingException {
        return FOLDER_ARRAY;
    }


    public void addStoreListener(StoreListener listener) {
        storeListeners.add(listener);
    }

    public void removeStoreListener(StoreListener listener) {
        storeListeners.remove(listener);
    }

    protected void notifyStoreListeners(int type, String message) {
        queueEvent(new StoreEvent(this, type, message), storeListeners);
    }


    public void addFolderListener(FolderListener listener) {
        folderListeners.add(listener);
    }

    public void removeFolderListener(FolderListener listener) {
        folderListeners.remove(listener);
    }

    protected void notifyFolderListeners(int type, Folder folder) {
        queueEvent(new FolderEvent(this, folder, type), folderListeners);
    }

    protected void notifyFolderRenamedListeners(Folder oldFolder, Folder newFolder) {
        queueEvent(new FolderEvent(this, oldFolder, newFolder, FolderEvent.RENAMED), folderListeners);
    }
}
