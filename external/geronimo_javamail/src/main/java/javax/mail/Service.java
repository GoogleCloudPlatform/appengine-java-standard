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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;   
import java.util.Vector;

import javax.mail.event.ConnectionEvent;
import javax.mail.event.ConnectionListener;
import javax.mail.event.MailEvent;

/**
 * @version $Rev: 597092 $ $Date: 2007-11-21 08:02:38 -0600 (Wed, 21 Nov 2007) $
 */
public abstract class Service {
    /**
     * The session from which this service was created.
     */
    protected Session session;
    /**
     * The URLName of this service
     */
    protected URLName url;
    /**
     * Debug flag for this service, set from the Session's debug flag.
     */
    protected boolean debug;

    private boolean connected;
    private final Vector connectionListeners = new Vector(2);
    // the EventQueue spins off a new thread, so we only create this 
    // if we have actual listeners to dispatch an event to. 
    private EventQueue queue = null;
    // when returning the URL, we need to ensure that the password and file information is 
    // stripped out. 
    private URLName exposedUrl; 

    /**
     * Construct a new Service.
     * @param session the session from which this service was created
     * @param url the URLName of this service
     */
    protected Service(Session session, URLName url) {
        this.session = session;
        this.url = url;
        this.debug = session.getDebug();
    }

    /**
     * A generic connect method that takes no parameters allowing subclasses
     * to implement an appropriate authentication scheme.
     * The default implementation calls <code>connect(null, null, null)</code>
     * @throws AuthenticationFailedException if authentication fails
     * @throws MessagingException for other failures
     */
    public void connect() throws MessagingException {
        connect(null, null, null);
    }

    /**
     * Connect to the specified host using a simple username/password authenticaion scheme
     * and the default port.
     * The default implementation calls <code>connect(host, -1, user, password)</code>
     *
     * @param host the host to connect to
     * @param user the user name
     * @param password the user's password
     * @throws AuthenticationFailedException if authentication fails
     * @throws MessagingException for other failures
     */
    public void connect(String host, String user, String password) throws MessagingException {
        connect(host, -1, user, password);
    }

    /**
     * Connect to the specified host using a simple username/password authenticaion scheme
     * and the default host and port.
     * The default implementation calls <code>connect(host, -1, user, password)</code>
     *
     * @param user the user name
     * @param password the user's password
     * @throws AuthenticationFailedException if authentication fails
     * @throws MessagingException for other failures
     */
    public void connect(String user, String password) throws MessagingException {
        connect(null, -1, user, password);
    }

    /**
     * Connect to the specified host at the specified port using a simple username/password authenticaion scheme.
     *
     * If this Service is already connected, an IllegalStateException is thrown.
     *
     * @param host the host to connect to
     * @param port the port to connect to; pass -1 to use the default for the protocol
     * @param user the user name
     * @param password the user's password
     * @throws AuthenticationFailedException if authentication fails
     * @throws MessagingException for other failures
     * @throws IllegalStateException if this service is already connected
     */
    public void connect(String host, int port, String user, String password) throws MessagingException {

        if (isConnected()) {
            throw new IllegalStateException("Already connected");
        }

        // before we try to connect, we need to derive values for some parameters that may not have
        // been explicitly specified.  For example, the normal connect() method leaves us to derive all
        // of these from other sources.  Some of the values are derived from our URLName value, others
        // from session parameters.  We need to go through all of these to develop a set of values we
        // can connect with.

        // this is the protocol we're connecting with.  We use this largely to derive configured values from
        // session properties.
        String protocol = null;

        // if we're working with the URL form, then we can retrieve the protocol from the URL.
        if (url != null) {
            protocol = url.getProtocol();
        }
        
        // if the port is -1, see if we have an override from url. 
        if (port == -1) {
            if (protocol != null) {
                port = url.getPort();
            }
        }

        // now try to derive values for any of the arguments we've been given as defaults
        if (host == null) {
            // first choice is from the url, if we have
            if (url != null) {
                host = url.getHost();
                // it is possible that this could return null (rare).  If it does, try to get a
                // value from a protocol specific session variable.
                if (host == null) {
                	if (protocol != null) {
                		host = session.getProperty("mail." + protocol + ".host");
                	}
                }
            }
            // this may still be null...get the global mail property
            if (host == null) {
                host = session.getProperty("mail.host");
            }
        }

        // ok, go after userid information next.
        if (user == null) {
            // first choice is from the url, if we have
            if (url != null) {
                user = url.getUsername();
                // make sure we get the password from the url, if we can.
                if (password == null) {
                    password = url.getPassword();
                }
                // user still null?  We have several levels of properties to try yet
                if (user == null) {
                	if (protocol != null) {
                		user = session.getProperty("mail." + protocol + ".user");
                	}
                }
            }

            // this may still be null...get the global mail property
            if (user == null) {
                user = session.getProperty("mail.user");
            }

            // finally, we try getting the system defined user name
            try {
                user = System.getProperty("user.name");
            } catch (SecurityException e) {
                // we ignore this, and just us a null username.
            }
        }
        // if we have an explicitly given user name, we need to see if this matches the url one and
        // grab the password from there.
        else {
            if (url != null && user.equals(url.getUsername())) {
                password = url.getPassword();
            }
        }

        // we need to update the URLName associated with this connection once we have all of the information,
        // which means we also need to propogate the file portion of the URLName if we have this form when
        // we start.
        String file = null;
        if (url != null) {
            file = url.getFile();
        }

        // see if we have cached security information to use.  If this is not cached, we'll save it
        // after we successfully connect.
        boolean cachePassword = false;


        // still have a null password to this point, and using a url form?
        if (password == null && url != null) {
            // construct a new URL, filling in any pieces that may have been explicitly specified.
            setURLName(new URLName(protocol, host, port, file, user, password));
            // now see if we have a saved password from a previous request.
            PasswordAuthentication cachedPassword = session.getPasswordAuthentication(getURLName());

            // if we found a saved one, see if we need to get any the pieces from here.
            if (cachedPassword != null) {
                // not even a resolved userid?  Then use both bits.
                if (user == null) {
                    user = cachedPassword.getUserName();
                    password = cachedPassword.getPassword();
                }
                // our user name must match the cached name to be valid.
                else if (user.equals(cachedPassword.getUserName())) {
                    password = cachedPassword.getPassword();
                }
            }
            else
            {
                // nothing found in the cache, so we need to save this if we can connect successfully.
                cachePassword = true;
            }
        }

        // we've done our best up to this point to obtain all of the information needed to make the
        // connection.  Now we pass this off to the protocol handler to see if it works.  If we get a
        // connection failure, we may need to prompt for a password before continuing.
        try {
            connected = protocolConnect(host, port, user, password);
        }
        catch (AuthenticationFailedException e) {
        }

        if (!connected) {
            InetAddress ipAddress = null;

            try {
                ipAddress = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
            }

            // now ask the session to try prompting for a password.
            PasswordAuthentication promptPassword = session.requestPasswordAuthentication(ipAddress, port, protocol, null, user);

            // if we were able to obtain new information from the session, then try again using the
            // provided information .
            if (promptPassword != null) {
                user = promptPassword.getUserName();
                password = promptPassword.getPassword();
            }

            connected = protocolConnect(host, port, user, password);
        }


        // if we're still not connected, then this is an exception.
        if (!connected) {
            throw new AuthenticationFailedException();
        }

        // the URL name needs to reflect the most recent information.
        setURLName(new URLName(protocol, host, port, file, user, password));

        // we need to update the global password cache with this information.
        if (cachePassword) {
            session.setPasswordAuthentication(getURLName(), new PasswordAuthentication(user, password));
        }

        // we're now connected....broadcast this to any interested parties.
        setConnected(connected);
        notifyConnectionListeners(ConnectionEvent.OPENED);
    }

    /**
     * Attempt the protocol-specific connection; subclasses should override this to establish
     * a connection in the appropriate manner.
     * 
     * This method should return true if the connection was established.
     * It may return false to cause the {@link #connect(String, int, String, String)} method to
     * reattempt the connection after trying to obtain user and password information from the user.
     * Alternatively it may throw a AuthenticatedFailedException to abandon the conection attempt.
     * 
     * @param host     The target host name of the service.
     * @param port     The connection port for the service.
     * @param user     The user name used for the connection.
     * @param password The password used for the connection.
     * 
     * @return true if a connection was established, false if there was authentication 
     *         error with the connection.
     * @throws AuthenticationFailedException
     *                if authentication fails
     * @throws MessagingException
     *                for other failures
     */
    protected boolean protocolConnect(String host, int port, String user, String password) throws MessagingException {
        return false;
    }

    /**
     * Check if this service is currently connected.
     * The default implementation simply returns the value of a private boolean field;
     * subclasses may wish to override this method to verify the physical connection.
     *
     * @return true if this service is connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Notification to subclasses that the connection state has changed.
     * This method is called by the connect() and close() methods to indicate state change;
     * subclasses should also call this method if the connection is automatically closed
     * for some reason.
     *
     * @param connected the connection state
     */
    protected void setConnected(boolean connected) {
        this.connected = connected;
    }

    /**
     * Close this service and terminate its physical connection.
     * The default implementation simply calls setConnected(false) and then
     * sends a CLOSED event to all registered ConnectionListeners.
     * Subclasses overriding this method should still ensure it is closed; they should
     * also ensure that it is called if the connection is closed automatically, for
     * for example in a finalizer.
     *
     *@throws MessagingException if there were errors closing; the connection is still closed
     */
    public void close() throws MessagingException {
        setConnected(false);
        notifyConnectionListeners(ConnectionEvent.CLOSED);
    }

    /**
     * Return a copy of the URLName representing this service with the password and file information removed.
     *
     * @return the URLName for this service
     */
    public URLName getURLName() {
        // if we haven't composed the URL version we hand out, create it now.  But only if we really 
        // have a URL. 
        if (exposedUrl == null) {
            if (url != null) {
                exposedUrl = new URLName(url.getProtocol(), url.getHost(), url.getPort(), null, url.getUsername(), null);
            }
        }
        return exposedUrl; 
    }

    /**
     * Set the url field.
     * @param url the new value
     */
    protected void setURLName(URLName url) {
        this.url = url;
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

    public String toString() {
        // NOTE:  We call getURLName() rather than use the URL directly 
        // because the get method strips out the password information. 
        URLName url = getURLName(); 
        
        return url == null ? super.toString() : url.toString();
    }

    protected void queueEvent(MailEvent event, Vector listeners) {
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

    protected void finalize() throws Throwable {
        // stop our event queue if we had to create one 
        if (queue != null) {
            queue.stop();
        }
        connectionListeners.clear();
        super.finalize();
    }


    /**
     * Package scope utility method to allow Message instances
     * access to the Service's session.
     *
     * @return The Session the service is associated with.
     */
    Session getSession() {
        return session;
    }
}
