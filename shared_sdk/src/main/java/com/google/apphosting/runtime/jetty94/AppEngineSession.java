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

import java.io.NotSerializableException;
import java.io.Serializable;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.session.Session;
import org.eclipse.jetty.server.session.SessionData;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.thread.Locker.Lock;

/**
 * This subclass exists to prevent a call to setMaxInactiveInterval(int) marking the session as
 * dirty and thus requiring it to be written out: in AppEngine the maxInactiveInterval of a session
 * is not persisted. It also keeps the Jetty 9.3 behavior for setAttribute calls which is to throw a
 * RuntimeException for non serializable values.
 */
class AppEngineSession extends Session {
  /**
   * To reduce our datastore put time, we only consider a session dirty on access if it is at least
   * 25% of the way to its expiration time. So a session that expires in 1 hr will only be re-stored
   * every 15 minutes, unless a "real" attribute change occurs.
   */
  private static final double UPDATE_TIMESTAMP_RATIO = 0.75;

  /**
   * Create a new session object. Usually after the data has been loaded.
   *
   * @param handler the SessionHandler to which the session pertains
   * @param data the info of the session
   */
  AppEngineSession(SessionHandler handler, SessionData data) {
    super(handler, data);
  }

  /**
   * Create a new session object. Usually called by a request asking for a new session.
   *
   * @param handler the SessionHandler to which the session pertains
   * @param request the request asking for the session creation
   * @param data the info of the session
   */
  public AppEngineSession(SessionHandler handler, HttpServletRequest request, SessionData data) {
    super(handler, request, data);
  }

  /** @see org.eclipse.jetty.server.session.Session#setMaxInactiveInterval(int) */
  @Override
  public void setMaxInactiveInterval(int secs) {
    try (Lock lock = _lock.lockIfNotHeld()) {
      boolean savedDirty = _sessionData.isDirty();
      super.setMaxInactiveInterval(secs);
      // Ensure it is unchanged by call to setMaxInactiveInterval
      _sessionData.setDirty(savedDirty);
    }
  }

  /**
   * If the session is nearing its expiry time, we mark it as dirty whether or not any attributes
   * change during this access. The default Jetty implementation does not handle the AppEngine
   * specific dirty state.
   */
  @Override
  protected boolean access(long time) {
    try (Lock lock = _lock.lock()) {
      if (isValid()) {
        long timeRemaining = _sessionData.getExpiry() - time;
        if (timeRemaining < (_sessionData.getMaxInactiveMs() * UPDATE_TIMESTAMP_RATIO)) {
          _sessionData.setDirty(true);
        }
      }
      return super.access(time);
    }
  }

  @Override
  public void setAttribute(String name, Object value) {
    // We want to keep the previous Jetty 9 App Engine implementation that emits a
    // NotSerializableException wrapped in a RuntimeException, and do the check as soon as possible.
    if ((value != null) && !(value instanceof Serializable)) {
      throw new RuntimeException(new NotSerializableException(value.getClass().getName()));
    }
    super.setAttribute(name, value);
  }

  @Override
  public boolean isResident() {
    // Are accesses to non resident sessions allowed?  This flag preserves GAE on jetty-9.3
    // behaviour. May be set in JavaRuntimeMain. If set will pretend to always be resident
    return super.isResident() || Boolean.getBoolean("gae.allow_non_resident_session_access");
  }
}
