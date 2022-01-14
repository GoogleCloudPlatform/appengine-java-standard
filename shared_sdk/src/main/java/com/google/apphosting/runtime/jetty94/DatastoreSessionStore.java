/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.runtime.jetty94;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.apphosting.runtime.SessionStore;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.GoogleLogger;
// <internal22>
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.server.session.AbstractSessionDataStore;
import org.eclipse.jetty.server.session.SessionData;
import org.eclipse.jetty.server.session.UnreadableSessionDataException;
import org.eclipse.jetty.server.session.UnwriteableSessionDataException;
import org.eclipse.jetty.util.ClassLoadingObjectInputStream;

/**
 * Jetty Store that uses DataStore for sessions. We cannot re-use the Jetty 9.4
 * GCloudSessionDataStore purely because AppEngine uses the compat GAE Datastore APIs.
 */
class DatastoreSessionStore implements SessionStore {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  static final String SESSION_ENTITY_TYPE = "_ah_SESSION";
  private static final String EXPIRES_PROP = "_expires";
  private static final String VALUES_PROP = "_values";
  private static final String SESSION_PREFIX = "_ahs";

  private final SessionDataStoreImpl impl;

  DatastoreSessionStore(boolean useTaskqueue, Optional<String> queueName) {
    impl = useTaskqueue ? new DeferredDatastoreSessionStore(queueName) : new SessionDataStoreImpl();
  }

  static String keyForSessionId(String id) {
    // TODO The id startsWith check is only needed while sessions created
    // with versions of 9.4 prior to 9.4.27 are still valid.
    return id.startsWith(SESSION_PREFIX) ? id : SESSION_PREFIX + id;
  }

  static String normalizeSessionId(String id) {
    // TODO The id startsWith check is only needed while sessions created
    // with versions of 9.4 prior to 9.4.27 are still valid.
    return id.startsWith(SESSION_PREFIX) ? id.substring(SESSION_PREFIX.length()) : id;
  }

  SessionDataStoreImpl getSessionDataStoreImpl() {
    return impl;
  }

  @Override
  public com.google.apphosting.runtime.SessionData getSession(String key) {
    throw new UnsupportedOperationException("Not supported.");
  }

  @Override
  public void saveSession(String key, com.google.apphosting.runtime.SessionData data) {
    throw new UnsupportedOperationException("saveSession is not supported.");
  }

  @Override
  public void deleteSession(String key) {
    try {
      impl.delete(key);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static class SessionDataStoreImpl extends AbstractSessionDataStore {
    private static final int MAX_RETRIES = 10;
    private static final int INITIAL_BACKOFF_MS = 50;
    private final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

    /**
     * Scavenging is not performed by the Jetty session setup, so this method will never be called.
     *
     * @see org.eclipse.jetty.server.session.AbstractSessionDataStore#doGetExpired(java.util.Set)
     */
    @Override
    public Set<String> doGetExpired(Set<String> candidates) {
      return ImmutableSet.of();
    }

    /** Save a session to Appengine datastore. */
    @Override
    public void doStore(String id, SessionData data, long lastSaveTime)
        throws InterruptedException, IOException, UnwriteableSessionDataException, Retryable {

      Entity entity = entityFromSession(id, data);
      int backoff = INITIAL_BACKOFF_MS;

      // Attempt the update with exponential back-off.
      for (int attempts = 0; attempts < MAX_RETRIES; attempts++) {
        try {
          datastore.put(entity);
          return;
        } catch (DatastoreTimeoutException ex) {
          Thread.sleep(backoff);

          backoff *= 2;
        }
      }
      // Retries have been exceeded.
      throw new UnwriteableSessionDataException(id, _context, null);
    }

    /**
     * Even though this is a passivating store, we return false because no passivation/activation
     * listeners are called in Appengine.
     *
     * @see org.eclipse.jetty.server.session.SessionDataStore#isPassivating()
     */
    @Override
    public boolean isPassivating() {
      return false;
    }

    /**
     * Check if the session matching the given key exists in datastore.
     *
     * @see org.eclipse.jetty.server.session.SessionDataStore#exists(java.lang.String)
     */
    @Override
    public boolean exists(String id) throws Exception {
      try {
        Entity entity = datastore.get(createKeyForSession(id));

        logger.atFinest().log("Session %s %s", id, (entity != null) ? "exists" : "does not exist");
        return true;
      } catch (EntityNotFoundException ex) {
        logger.atFine().log("Session %s does not exist", id);
        return false;
      }
    }

    /**
     * Remove the Entity for the given session key.
     *
     * @see org.eclipse.jetty.server.session.SessionDataMap#delete(java.lang.String)
     */
    @Override
    public boolean delete(String id) throws IOException {
      datastore.delete(createKeyForSession(id));
      return true;
    }

    /**
     * Read in data for a session from datastore.
     *
     * @see org.eclipse.jetty.server.session.SessionDataMap#load(java.lang.String)
     */
    @Override
    public SessionData doLoad(String id) throws Exception {
      try {
        Entity entity = datastore.get(createKeyForSession(id));
        logger.atFinest().log("Loaded session %s from datastore.", id);
        return sessionFromEntity(entity, normalizeSessionId(id));
      } catch (EntityNotFoundException ex) {
        logger.atFine().log("Unable to find specified session %s", id);
        return null;
      }
    }

    /** Return a {@link Key} for the given session id string ( sessionId) in the empty namespace. */
    static Key createKeyForSession(String id) {
      String originalNamespace = NamespaceManager.get();
      try {
        NamespaceManager.set("");
        return KeyFactory.createKey(SESSION_ENTITY_TYPE, keyForSessionId(id));
      } finally {
        NamespaceManager.set(originalNamespace);
      }
    }

    /**
     * Create an Entity for the session.
     *
     * @param data the SessionData for the session
     * @param id the session id
     * @return a datastore Entity
     */
    Entity entityFromSession(String id, SessionData data) throws IOException {
      String originalNamespace = NamespaceManager.get();

      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(((AppEngineSessionData) data).getMutableAttributes());
        oos.flush();

        NamespaceManager.set("");
        Entity entity = new Entity(SESSION_ENTITY_TYPE, SESSION_PREFIX + id);
        entity.setProperty(EXPIRES_PROP, data.getExpiry());
        entity.setProperty(VALUES_PROP, new Blob(baos.toByteArray()));
        return entity;
      } finally {
        NamespaceManager.set(originalNamespace);
      }
    }

    /**
     * Re-inflate a session from appengine datastore.
     *
     * @param entity the appengine datastore Entity
     * @param id the session id
     * @return the Jetty SessionData for the session
     * @throws Exception on error in conversion
     */
    SessionData sessionFromEntity(final Entity entity, final String id) throws Exception {
      if (entity == null) {
        return null;
      }
      // Keep this System.currentTimeMillis API, and do not use the close source suggested one.
      @SuppressWarnings("NowMillis")
      final long time = System.currentTimeMillis();
      final AtomicReference<SessionData> reference = new AtomicReference<>();
      final AtomicReference<Exception> exception = new AtomicReference<>();
      Runnable load =
          () -> {
            try {
              SessionData session = createSessionData(entity, id, time);
              reference.set(session);
            } catch (UnreadableSessionDataException ex) {
              exception.set(ex);
            }
          };
      // Ensure this runs in the context classloader.
      _context.run(load);

      if (exception.get() != null) {
        throw exception.get();
      }
      return reference.get();
    }

    @Override
    public SessionData newSessionData(
        String id, long created, long accessed, long lastAccessed, long maxInactiveMs) {
      return new AppEngineSessionData(
          id,
          this._context.getCanonicalContextPath(),
          this._context.getVhost(),
          created,
          accessed,
          lastAccessed,
          maxInactiveMs);
    }

    // <internal23>
    private SessionData createSessionData(Entity entity, String id, long time)
        throws UnreadableSessionDataException {
      // Turn an Entity into a Session.
      long expiry = (Long) entity.getProperty(EXPIRES_PROP);
      Blob blob = (Blob) entity.getProperty(VALUES_PROP);

      // As the max inactive interval of the session is not stored, it must
      // be defaulted to whatever is set on the session handler from web.xml.
      SessionData session =
          newSessionData(
              id,
              time,
              time,
              time,
              (1000L * _context.getSessionHandler().getMaxInactiveInterval()));
      session.setExpiry(expiry);

      try (ClassLoadingObjectInputStream ois =
          new ClassLoadingObjectInputStream(new ByteArrayInputStream(blob.getBytes()))) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) ois.readObject();

        // TODO: avoid this data copy
        session.putAllAttributes(map);
      } catch (Exception ex) {
        throw new UnreadableSessionDataException(id, _context, ex);
      }
      return session;
    }
  }
}
