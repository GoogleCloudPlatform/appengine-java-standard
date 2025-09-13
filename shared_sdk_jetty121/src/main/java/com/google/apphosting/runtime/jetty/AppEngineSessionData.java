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

import java.util.Map;
import org.eclipse.jetty.session.SessionData;

/**
 * A specialization of the jetty SessionData class to allow direct access to the mutable attribute
 * map.
 */
public class AppEngineSessionData extends SessionData {

  public AppEngineSessionData(
      String id,
      String cpath,
      String vhost,
      long created,
      long accessed,
      long lastAccessed,
      long maxInactiveMs) {
    super(id, cpath, vhost, created, accessed, lastAccessed, maxInactiveMs);
  }

  /**
   * Get the mutable attributes. The standard {@link SessionData#getAllAttributes} return
   * unmodifiable map, which if stored in memcache or datastore, may be passed to an older session
   * implementation that is expecting a mutable map.
   *
   * @return The mutable attribute map that can be stored in memcache and datastore
   */
  public Map<String, Object> getMutableAttributes() {
    // TODO: Direct access to the mutable map is required to maintain binary
    // compatibility with jetty93 based runtimes for sessions stored in memcache and datastore.
    // This is a somewhat convoluted and inefficient approach, so once jetty93 runtimes are
    // removed this code should be revisited for simplicity and efficiency.  Also a version number
    // should eventually be added to make future changes to the session stores simpler.
    return _attributes;
  }
}
