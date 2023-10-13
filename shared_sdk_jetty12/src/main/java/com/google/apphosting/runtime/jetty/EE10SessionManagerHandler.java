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

import static com.google.common.io.BaseEncoding.base64Url;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.session.CachingSessionDataStore;
import org.eclipse.jetty.session.DefaultSessionIdManager;
import org.eclipse.jetty.session.HouseKeeper;
import org.eclipse.jetty.session.ManagedSession;
import org.eclipse.jetty.session.NullSessionCache;
import org.eclipse.jetty.session.SessionData;
import org.eclipse.jetty.session.SessionDataStore;
import org.eclipse.jetty.session.SessionManager;

/**
 * Utility that configures the new Jetty 9.4 Servlet Session Manager in App Engine. It is used both
 * by the GAE runtime and the GAE SDK.
 */
// Needs to be public as it will be used by the GAE runtime as well as the GAE local SDK.
// More info at go/appengine-jetty94-sessionmanagement.
public class EE10SessionManagerHandler {
  private final AppEngineSessionIdManager idManager;
  private final NullSessionCache cache;
  private final MemcacheSessionDataMap memcacheMap;

  private EE10SessionManagerHandler(
      AppEngineSessionIdManager idManager,
      NullSessionCache cache,
      MemcacheSessionDataMap memcacheMap) {
    this.idManager = idManager;
    this.cache = cache;
    this.memcacheMap = memcacheMap;
  }

  /** Setup a new App Engine session manager based on the given configuration. */
  public static EE10SessionManagerHandler create(Config config) {
    ServletContextHandler context = config.servletContextHandler();
    Server server = context.getServer();
    AppEngineSessionIdManager idManager = new AppEngineSessionIdManager(server);
    context.getSessionHandler().setSessionIdManager(idManager);
    HouseKeeper houseKeeper = new HouseKeeper();
    // Do not scavenge. This can throw a generic Exception, not sure why.
    try {
      houseKeeper.setIntervalSec(0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    idManager.setSessionHouseKeeper(houseKeeper);

    if (config.enableSession()) {
      NullSessionCache cache = new AppEngineSessionCache(context.getSessionHandler());
      DatastoreSessionStore dataStore =
          new DatastoreSessionStore(config.asyncPersistence(), config.asyncPersistenceQueueName());
      MemcacheSessionDataMap memcacheMap = new MemcacheSessionDataMap();
      CachingSessionDataStore cachingDataStore =
          new CachingSessionDataStore(memcacheMap, dataStore.getSessionDataStoreImpl());
      cache.setSessionDataStore(cachingDataStore);
      context.getSessionHandler().setSessionCache(cache);
      return new EE10SessionManagerHandler(idManager, cache, memcacheMap);

    } else {
      // No need to configure an AppEngineSessionIdManager, nor a MemcacheSessionDataMap.
      NullSessionCache cache = new AppEngineNullSessionCache(context.getSessionHandler());
      // Non-persisting SessionDataStore
      SessionDataStore nullStore = new AppEngineNullSessionDataStore();
      cache.setSessionDataStore(nullStore);
      context.getSessionHandler().setSessionCache(cache);
      return new EE10SessionManagerHandler(/* idManager= */ null, cache, /* memcacheMap= */ null);
    }
  }

  @VisibleForTesting
  AppEngineSessionIdManager getIdManager() {
    return idManager;
  }

  @VisibleForTesting
  NullSessionCache getCache() {
    return cache;
  }

  @VisibleForTesting
  MemcacheSessionDataMap getMemcacheMap() {
    return memcacheMap;
  }

  /**
   * Options to configure an App Engine Datastore/Task Queue based Session Manager on a Jetty Web
   * App context.
   */
  @AutoValue
  public abstract static class Config {
    /** Whether to turn on Datatstore based session management. False by default. */
    public abstract boolean enableSession();

    /** Whether to use task queue based async session management. False by default. */
    public abstract boolean asyncPersistence();

    /**
     * Optional task queue name to use for the async persistence mechanism. When not provided, use
     * the default value setup by the task queue system.
     */
    public abstract Optional<String> asyncPersistenceQueueName();

    /** Jetty web app context to use for the session management configuration. */
    public abstract ServletContextHandler servletContextHandler();

    /** Returns an {@code Config.Builder}. */
    public static Builder builder() {
      return new AutoValue_EE10SessionManagerHandler_Config.Builder()
          .setEnableSession(false)
          .setAsyncPersistence(false);
    }

    /** Builder for {@code Config} instances. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setServletContextHandler(ServletContextHandler context);

      public abstract Builder setEnableSession(boolean enableSession);

      public abstract Builder setAsyncPersistence(boolean asyncPersistence);

      public abstract Builder setAsyncPersistenceQueueName(String asyncPersistenceQueueName);

      /** Returns a configured {@code Config} instance. */
      public abstract Config build();
    }
  }

  /** This does no caching, and is a factory for the new NullSession class. */
  private static class AppEngineNullSessionCache extends NullSessionCache {

    /**
     * Creates a new AppEngineNullSessionCache.
     *
     * @param handler the SessionHandler to which this cache belongs
     */
    AppEngineNullSessionCache(SessionHandler handler) {
      super(handler);
      // Saves a call to the SessionDataStore.
      setSaveOnCreate(false);
      setRemoveUnloadableSessions(false);
    }

    @Override
    public ManagedSession newSession(SessionData data) {
      return new NullSession(getSessionManager(), data);
    }
  }

  /**
   * An extension to the standard Jetty Session class that ensures only the barest minimum support.
   * This is a replacement for the NoOpSession.
   */
  @VisibleForTesting
  static class NullSession extends ManagedSession {

    /**
     * Create a new NullSession.
     *
     * @param sessionManager the SessionManager to which this session belongs
     * @param data the info of the session
     */
    private NullSession(SessionManager sessionManager, SessionData data) {
      super(sessionManager, data);
    }

    @Override
    public long getCreationTime() {
      return 0;
    }

    @Override
    public boolean isNew() {
      return false;
    }

    @Override
    public Object getAttribute(String name) {
      return null;
    }

    @Override
    public Object removeAttribute(String name) {
      return null;
    }

    @Override
    public Object setAttribute(String name, Object value) {
      if ("org.eclipse.jetty.security.sessionCreatedSecure".equals(name)) {
        // This attribute gets set when generated JSP pages call HttpServletRequest.getSession(),
        // which creates a session if one does not exist. If HttpServletRequest.isSecure() is true,
        // meaning this is an https request, then Jetty wants to record that fact by setting this
        // attribute in the new session.
        // Possibly we should just ignore all setAttribute calls.
        return null;
      }
      throwException(name, value);
      return null;
    }

    // This code path will be tested when we hook up the new session manager in the GAE
    // runtime at:
    // javatests/com/google/apphosting/tests/usercode/testservlets/CountServlet.java?q=%22&l=77
    private static void throwException(String name, Object value) {
      throw new RuntimeException(
          "Session support is not enabled in appengine-web.xml.  "
              + "To enable sessions, put <sessions-enabled>true</sessions-enabled> in that "
              + "file.  Without it, getSession() is allowed, but manipulation of session "
              + "attributes is not. Could not set \""
              + name
              + "\" to "
              + value);
    }

    @Override
    public long getLastAccessedTime() {
      return 0;
    }

    @Override
    public int getMaxInactiveInterval() {
      return 0;
    }
  }

  /**
   * Sessions are not cached and shared in AppEngine so this extends the NullSessionCache. This
   * subclass exists because SessionCaches are factories for Sessions. We subclass Session for
   * Appengine.
   */
  private static class AppEngineSessionCache extends NullSessionCache {

    /**
     * Create a new cache.
     *
     * @param handler the SessionHandler to which this cache pertains
     */
    AppEngineSessionCache(SessionHandler handler) {
      super(handler);
      setSaveOnCreate(true);
    }

    @Override
    public ManagedSession newSession(SessionData data) {
      return new AppEngineSession(getSessionManager(), data);
    }
  }

  /**
   * Extension to Jetty DefaultSessionIdManager that uses a GAE specific algorithm to generate
   * session ids, so that we keep compatibility with previous session implementation.
   */
  static class AppEngineSessionIdManager extends DefaultSessionIdManager {

    // This is just useful for testing.
    private static final AtomicReference<String> lastId = new AtomicReference<>(null);

    @VisibleForTesting
    static String lastId() {
      return lastId.get();
    }

    /**
     * Create a new id manager.
     *
     * @param server the Jetty server instance to which this id manager belongs.
     */
    AppEngineSessionIdManager(Server server) {
      super(server, new SecureRandom());
    }

    /**
     * Generate a new session id.
     *
     * @see org.eclipse.jetty.session.DefaultSessionIdManager#newSessionId(long)
     */
    @Override
    public synchronized String newSessionId(long seedTerm) {
      byte[] randomBytes = new byte[16];
      _random.nextBytes(randomBytes);
      // Use a web-safe encoding in case the session identifier gets
      // passed via a URL path parameter.
      String id = base64Url().omitPadding().encode(randomBytes);
      lastId.set(id);
      return id;
    }
  }
}
