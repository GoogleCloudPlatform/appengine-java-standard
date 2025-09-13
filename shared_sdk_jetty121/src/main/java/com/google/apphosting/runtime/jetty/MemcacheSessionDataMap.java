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

package com.google.apphosting.runtime.jetty;

import com.google.apphosting.runtime.MemcacheSessionStore;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.session.SessionContext;
import org.eclipse.jetty.session.SessionData;
import org.eclipse.jetty.session.SessionDataMap;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

/**
 * Interface to the MemcacheService to load/store/delete sessions. The standard Jetty 9.4
 * MemcachedSessionDataMap cannot be used because it relies on a different version of memcached api.
 * For compatibility with existing cached sessions, this impl must translate between the stored
 * com.google.apphosting.runtime.SessionData and the org.eclipse.jetty.server.session.SessionData
 * that this api references.
 */
class MemcacheSessionDataMap extends AbstractLifeCycle implements SessionDataMap {
  private SessionContext context;
  private MemcacheSessionStore memcacheSessionStore;

  /**
   * @see org.eclipse.jetty.util.component.AbstractLifeCycle#doStart()
   */
  @Override
  public void doStart() throws Exception {
    memcacheSessionStore = new MemcacheSessionStore();
  }

  /**
   * @see SessionDataMap#initialize(org.eclipse.jetty.session.SessionContext)
   */
  @Override
  public void initialize(SessionContext context) throws Exception {
    this.context = context;
  }

  /**
   * Load an App Engine session data from memcache service and transform it to a Jetty session data
   *
   * @see SessionDataMap#load(java.lang.String)
   */
  @Override
  public SessionData load(String id) throws Exception {

    final AtomicReference<com.google.apphosting.runtime.SessionData> reference =
        new AtomicReference<>();
    final AtomicReference<Exception> exception = new AtomicReference<>();

    context.run(
        () -> {
          try {
            reference.set(
                memcacheSessionStore.getSession(DatastoreSessionStore.keyForSessionId(id)));
          } catch (Exception e) {
            exception.set(e);
          }
        });
    if (exception.get() != null) {
      throw exception.get();
    }

    com.google.apphosting.runtime.SessionData runtimeSession = reference.get();
    if (runtimeSession != null) {
      return appEngineToJettySessionData(
          DatastoreSessionStore.normalizeSessionId(id), runtimeSession);
    }
    return null;
  }

  /**
   * Save a Jetty session data as an AppEngine session data to memcache service
   *
   * @see SessionDataMap #store(java.lang.String, org.eclipse.jetty.server.session.SessionData)
   */
  @Override
  public void store(String id, SessionData data) throws Exception {
    AtomicReference<Exception> exception = new AtomicReference<>();
    context.run(
        () -> {
          try {
            memcacheSessionStore.saveSession(
                DatastoreSessionStore.keyForSessionId(id), jettySessionDataToAppEngine(data));
          } catch (Exception e) {
            exception.set(e);
          }
        });
    if (exception.get() != null) {
      throw exception.get();
    }
  }

  /**
   * Delete session data out of memcache service.
   *
   * @see SessionDataMap#delete(java.lang.String)
   */
  @Override
  public boolean delete(String id) throws Exception {
    context.run(
        () -> memcacheSessionStore.deleteSession(DatastoreSessionStore.keyForSessionId(id)));
    return true;
  }

  /**
   * Convert an appengine SessionData object into a Jetty SessionData object.
   *
   * @param id the session id
   * @param runtimeSession SessionData
   * @return a Jetty SessionData
   */
  SessionData appEngineToJettySessionData(
      String id, com.google.apphosting.runtime.SessionData runtimeSession) {
    // Keep this System.currentTimeMillis API, and do not use the close source suggested one.
    @SuppressWarnings("NowMillis")
    long now = System.currentTimeMillis();
    long maxInactiveMs = 1000L * this.context.getSessionManager().getMaxInactiveInterval();
    SessionData jettySession =
        new AppEngineSessionData(
            id,
            this.context.getCanonicalContextPath(),
            this.context.getVhost(),
            /* created= */ now,
            /* accessed= */ now,
            /* lastAccessed= */ now,
            maxInactiveMs);
    jettySession.setExpiry(runtimeSession.getExpirationTime());
    // TODO: avoid this data copy
    jettySession.putAllAttributes(runtimeSession.getValueMap());
    return jettySession;
  }

  /**
   * Convert a Jetty SessionData object into an Appengine Runtime SessionData object.
   *
   * @param session the Jetty SessionData
   * @return an Appengine Runtime SessionData
   */
  com.google.apphosting.runtime.SessionData jettySessionDataToAppEngine(SessionData session) {
    com.google.apphosting.runtime.SessionData runtimeSession =
        new com.google.apphosting.runtime.SessionData();
    runtimeSession.setExpirationTime(session.getExpiry());
    runtimeSession.setValueMap(((AppEngineSessionData) session).getMutableAttributes());
    return runtimeSession;
  }
}
