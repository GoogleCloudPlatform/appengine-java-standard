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

package com.google.apphosting.runtime;

import static com.google.apphosting.runtime.SessionManagerUtil.deserialize;
import static com.google.apphosting.runtime.SessionManagerUtil.serialize;

import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.common.flogger.GoogleLogger;
import java.util.logging.Level;

/**
 * A {@link SessionStore} implementation on top of memcache.
 *
 */
public class MemcacheSessionStore implements SessionStore {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final MemcacheService memcache;

  public MemcacheSessionStore() {
    memcache = MemcacheServiceFactory.getMemcacheService("");
    memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
  }

  @Override
  public SessionData getSession(String key) {
    byte[] sessionBytes = (byte[]) memcache.get(key);
    if (sessionBytes != null) {
      logger.atFinest().log("Loaded session %s from memcache.", key);
      return (SessionData) deserialize(sessionBytes);
    }
    return null;
  }

  @Override
  public void saveSession(String key, SessionData data) throws Retryable {
    try {
      memcache.put(key, serialize(data));
    } catch (ApiProxy.ApiDeadlineExceededException e) {
      throw new Retryable(e);
    }
  }

  @Override
  public void deleteSession(String key) {
    memcache.delete(key);
  }
}
