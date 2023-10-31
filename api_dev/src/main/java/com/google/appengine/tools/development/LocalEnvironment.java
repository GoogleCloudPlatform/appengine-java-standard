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

package com.google.appengine.tools.development;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.appengine.api.NamespaceManager;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.WebModule;
// <internal24>
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code LocalEnvironment} is a simple
 * {@link com.google.apphosting.api.ApiProxy.Environment} that reads
 * application-specific details (e.g. application identifer) directly from the
 * custom deployment descriptor.
 *
 */
abstract public class LocalEnvironment implements ApiProxy.Environment {
  private static final Logger logger = Logger.getLogger(LocalEnvironment.class.getName());
  static final Pattern APP_ID_PATTERN = Pattern.compile("([^:.]*)(:([^:.]*))?(.*)?");

  // Keep this in sync with other strings.
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  // Default port for tests that do not specify a port.
  public static final Integer TESTING_DEFAULT_PORT = Integer.valueOf(8080);

  // Environment attribute key where the instance id is stored. Keep in sync
  // with ModulesServiceImpl.INSTANCE_ID_ENV_ATTRIBUTE.
  public static final String INSTANCE_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.id";

  // Environment attribute key where the instance port is stored. Keep in sync with
  // duplicate definitions of this value found in testing service stubs.
  public static final String PORT_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.port";

  /**
   * {@code ApiProxy.Environment} instances used with {@code
   * ApiProxyLocalFactory} should have a entry in the map returned by
   * {@code getAttributes()} with this key, and a {@link
   * java.util.concurrent.Semaphore} as the value.  This is used
   * internally to track asynchronous API calls.
   */
  public static final String API_CALL_SEMAPHORE =
      "com.google.appengine.tools.development.api_call_semaphore";

  /**
   * The name of an {@link #getAttributes() attribute} that contains a
   * (String) unique identifier for the curent request.
   */
  public static final String REQUEST_ID =
      "com.google.appengine.runtime.request_log_id";

  /**
   * The name of an {@link #getAttributes() attribute} that contains a
   * a {@link Date} object representing the time this request was
   * started.
   */
  public static final String START_TIME_ATTR =
      "com.google.appengine.tools.development.start_time";

  /**
   * The name of an {@link #getAttributes() attribute} that contains a {@code
   * Set<RequestEndListener>}. The set of {@link RequestEndListener
   * RequestEndListeners} is populated by from within the service calls. The
   * listeners are invoked at the end of a user request.
   */
  public static final String REQUEST_END_LISTENERS =
      "com.google.appengine.tools.development.request_end_listeners";

  /**
   * The name of an {@link #getAttributes() attribute} that contains the {@link
   * javax.servlet.http.HttpServletRequest} instance.
   */
  public static final String HTTP_SERVLET_REQUEST =
      "com.google.appengine.http_servlet_request";

  private static final String REQUEST_THREAD_FACTORY_ATTR =
      "com.google.appengine.api.ThreadManager.REQUEST_THREAD_FACTORY";

  private static final String BACKGROUND_THREAD_FACTORY_ATTR =
      "com.google.appengine.api.ThreadManager.BACKGROUND_THREAD_FACTORY";

  /**
   * For historical and probably compatibility reasons dev appserver assigns all
   * versions a minor version of 1.
   */
  private static final String MINOR_VERSION_SUFFIX = ".1";

  /**
   * In production, this is a constant that defines the {@link #getAttributes() attribute} name
   * that contains the hostname on which the default version is listening.
   * In the local development server, the {@link #getAttributes() attribute} contains the
   * listening port in addition to the hostname, and is the one and only frontend app instance
   * hostname and port.
   */
  // Must be kept in sync with java/com/google/apphosting/runtime/ApiProxyImpl.java
  // Must be kept in sync with java/com/google/appengine/api/backends/BackendServiceImpl.java
  public static final String DEFAULT_VERSION_HOSTNAME =
      "com.google.appengine.runtime.default_version_hostname";

  private final String appId;
  private final String moduleId;
  private final String versionId;

  private final Collection<RequestEndListener> requestEndListeners;

  // This map can be accessed by multiple threads in the event of
  // asynchronous API calls.
  protected final ConcurrentMap<String, Object> attributes =
      new ConcurrentHashMap<String, Object>();

  // null means the request has no deadline
  @Nullable private final Long endTime;
  /**
   * Instance number for a main instance.
   * <p>
   * Clients depend on this literal having the value -1 so do not change this
   * value without making needed updates to clients.
   */
  public static final int MAIN_INSTANCE = -1;

  /**
   * Deprecated constructor used by tests.
   */
  @Deprecated
  protected LocalEnvironment(String appId, String majorVersionId) {
    this(appId, WebModule.DEFAULT_MODULE_NAME, majorVersionId, LocalEnvironment.MAIN_INSTANCE,
        TESTING_DEFAULT_PORT, null);
  }

  /**
   * Deprecated constructor used by tests.
   */
  @Deprecated
  protected LocalEnvironment(String appId, String majorVersionId, Long deadlineMillis) {
    this(appId, WebModule.DEFAULT_MODULE_NAME, majorVersionId, LocalEnvironment.MAIN_INSTANCE,
        TESTING_DEFAULT_PORT, deadlineMillis);
  }

  protected LocalEnvironment(String appId, String moduleName, String majorVersionId, int instance,
      Integer port, Long deadlineMillis) {
    this.appId = appId;
    this.moduleId = moduleName;
    this.versionId = (majorVersionId != null ? majorVersionId : "no_version")
        + MINOR_VERSION_SUFFIX;

    if (deadlineMillis == null) {
      this.endTime = null;
    } else if (deadlineMillis < 0) {
      throw new IllegalArgumentException("deadlineMillis must be a non-negative integer.");
    } else {
      this.endTime = System.currentTimeMillis() + deadlineMillis;
    }
    setInstance(attributes, instance);
    setPort(attributes, port);
    requestEndListeners =
        Collections.newSetFromMap(new ConcurrentHashMap<RequestEndListener, Boolean>(10));
    attributes.put(REQUEST_ID, generateRequestId());
    attributes.put(REQUEST_END_LISTENERS, requestEndListeners);
    attributes.put(START_TIME_ATTR, new Date());
    attributes.put(REQUEST_THREAD_FACTORY_ATTR, new RequestThreadFactory());
    attributes.put(BACKGROUND_THREAD_FACTORY_ATTR, new BackgroundThreadFactory(appId,
        moduleName, majorVersionId));
  }

  /** Sets the instance for the provided attributes. */
  public static void setInstance(Map<String, Object> attributes, int instance) {
    // First we remove the old value if there is one.
    attributes.remove(INSTANCE_ID_ENV_ATTRIBUTE);
    // Next we set the new value if needed.
    if (instance != LocalEnvironment.MAIN_INSTANCE) {
      attributes.put(INSTANCE_ID_ENV_ATTRIBUTE, Integer.toString(instance));
    }
  }

  /**
   * Sets the {@link #PORT_ID_ENV_ATTRIBUTE} value to the provided port value or clears it if port
   * is null.
   */
  public static void setPort(Map<String, Object> attributes, Integer port) {
    if (port == null) {
      attributes.remove(PORT_ID_ENV_ATTRIBUTE);
    } else {
      attributes.put(PORT_ID_ENV_ATTRIBUTE, port);
    }
  }

  /**
   * Returns the current instance or {@link #MAIN_INSTANCE} if none is defined.
   */
  static int getCurrentInstance() {
    int result = MAIN_INSTANCE;
    String instance = (String) ApiProxy.getCurrentEnvironment().getAttributes().get(
        LocalEnvironment.INSTANCE_ID_ENV_ATTRIBUTE);
    if (instance != null) {
      result = Integer.parseInt(instance);
    }
    return result;
  }

  /**
   * Returns the current instance's port or null if none is defined.
   */
  static Integer getCurrentPort() {
    return (Integer) ApiProxy.getCurrentEnvironment().getAttributes().get(
        LocalEnvironment.PORT_ID_ENV_ATTRIBUTE);
  }

  private static AtomicInteger requestID = new AtomicInteger();

  /**
   * Generates a unique request ID using the same algorithm as the Python dev appserver. It is
   * similar to the production algorithm, but with less embedded data. The primary goal is that the
   * IDs be sortable by timestamp, so the initial bytes consist of seconds then microseconds packed
   * in big-endian byte order. To ensure uniqueness, a hash of the incrementing counter is appended.
   * Hexadecimal encoding is used instead of base64 in order to preserve comparison order.
   */
  // <internal25>
  private String generateRequestId() {
    try {
      ByteBuffer buf = ByteBuffer.allocate(12);

      // 8 bytes of timestamp. ByteByffer is big-endian by default.
      long now = System.currentTimeMillis();
      buf.putInt((int) (now / 1000)); // seconds
      buf.putInt((int) ((now * 1000) % 1000000)); // microseconds

      // 4 bytes of hashed counter. See
      // http://g3doc/apphosting/g3doc/wiki-carryover/logsv2Design_doc.md#Row_Name_Format
      String nextID = Integer.toString(requestID.getAndIncrement());
      byte[] hashBytes =
          MessageDigest.getInstance("SHA-1").digest(nextID.getBytes(US_ASCII));
      buf.put(hashBytes, 0, 4);

      // 2 hex characters per byte.
      return String.format("%x", new BigInteger(buf.array()));
    } catch (Exception e) {
      return "";
    }
  }

  @Override
  public String getAppId() {
    return appId;
  }

  @Override
  public String getModuleId() {
    return moduleId;
  }

  @Override
  public String getVersionId() {
    return versionId;
  }

  @Override
  public String getAuthDomain() {
    // Always return 'Google Accounts' in dev appserver.
    // Note that this is a property of the request not the appinfo, but the
    // default is set in the appinfo.
    return "gmail.com";
  }

  @Override
  @Deprecated
  public final String getRequestNamespace() {
    String appsNamespace = (String) getAttributes().get(APPS_NAMESPACE_KEY);
    return appsNamespace == null ? "" : appsNamespace;
  }

  @Override
  public ConcurrentMap<String, Object> getAttributes() {
    return attributes;
  }

  public void callRequestEndListeners() {
    for (RequestEndListener listener : requestEndListeners) {
      try {
        listener.onRequestEnd(this);
      } catch (Exception ex) {
        logger.log(Level.WARNING,
                   "Exception while attempting to invoke RequestEndListener " + listener.getClass()
                   + ": ", ex);
      }
    }
    // Clear the end listeners to allow unit tests to call this manually but not
    // have the automatic invocation run them twice.
    requestEndListeners.clear();
  }

  @Override
  public long getRemainingMillis() {
    if (endTime != null) {
      return endTime - System.currentTimeMillis();
    }

    // NOTE: In the case of background threads -- or the fictitious
    // LocalApiProxyServletFilter -- let the request run forever.
    // Or approximately forever, which is almost 300 000 000
    // years.
    return Long.MAX_VALUE;
  }

  /**
   * Returns the major version component from the provided version id or null
   * if the provided version id has no major version component.
   */
  public static String getMajorVersion(String versionId) {
    Matcher matcher = APP_ID_PATTERN.matcher(versionId);
    matcher.find();
    return matcher.group(3) == null ? matcher.group(1) : matcher.group(3);
  }
}
