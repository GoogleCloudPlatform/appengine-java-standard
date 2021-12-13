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

package com.google.appengine.api;

import com.google.apphosting.api.ApiProxy;
import java.util.regex.Pattern;

/**
 * Provides functions for manipulating the current namespace used for
 * App Engine APIs.
 *
 * <p>The "current namespace" is the string that is returned by
 * {@link #get()} and used by a number of APIs including Datatore,
 * Memcache and Task Queue.
 *
 * <p>When a namespace aware class (e.g.,
 * {@link com.google.appengine.api.datastore.Key},
 * {@link com.google.appengine.api.datastore.Query} and
 * {@link com.google.appengine.api.memcache.MemcacheService}) is constructed, it
 * determines which namespace will be used by calling
 * {@link NamespaceManager#get()} if it is otherwise unspecified. If
 * {@link NamespaceManager#get()} returns null, the current namespace is unset
 * and these APIs will use the empty ("") namespace in its place.
 *
 * <p>Example: <pre>
 * {@link NamespaceManager}.{@link #set}("a-namespace");
 * MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
 * // Store record in namespace "a-namespace"
 * memcache.put("key1", "value1");
 *
 * {@link NamespaceManager}.{@link #set}("other-namespace");
 * // Store record in namespace "other-namespace"
 * memcache.put("key2", "value2");
 *
 * MemcacheService boundMemcache =
 *     MemcacheServiceFactory.getMemcacheService("specific-namespace");
 * {@link NamespaceManager}.{@link #set}("whatever-namespace");
 * // The record is still stored in namespace "specific-namespace".
 * boundMemcache.put("key3", "value3");
 * </pre>
 *
 * <p>MemcacheService {@code memcache} (in the above example) uses the current
 * namespace and {@code key1} will be stored in namespace {@code "a-namespace"},
 * while {@code key2} is stored in namespace {@code "other-namespace"}. It is
 * possible to override the current namespace and store data in specific
 * namespace. In the above example {@code key3} is stored in namespace
 * {@code "specific-namespace"}.
 *
 * <p>The Task Queue {@link com.google.appengine.api.taskqueue.Queue#add}
 * methods will forward the {@link NamespaceManager} settings into the task
 * being added causing the added task to be executed with the same current
 * namespace as the task creator. The exception is that an unset current
 * namespace (i.e. {@link NamespaceManager#get()} returns null) will be
 * forwarded as an empty ("") namespace to the created task's requests.
 *
 * @see <a
 * href="http://cloud.google.com/appengine/docs/java/multitenancy/">
 * Multitenancy and the Namespaces Java API.  In <em>Google App Engine
 * Developer's Guide</em></a>.
 */
public final class NamespaceManager {
  // Namespace string valid pattern matcher.
  // See http://b/2104357 for a discussion of choice of format.
  // Note:  Keep this consistent with the Python namespace_manager and
  // apphosting/base/id_util.cc.
  private final static int NAMESPACE_MAX_LENGTH = 100;
  private final static String NAMESPACE_REGEX = "[0-9A-Za-z._-]{0," + NAMESPACE_MAX_LENGTH +"}";
  private static final Pattern NAMESPACE_PATTERN = Pattern.compile(NAMESPACE_REGEX);

  /**
   * We store the current namespace as an environment attribute identified
   * by this key.
   */
  // Keep these in sync with other occurrences.
  private static final String CURRENT_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".currentNamespace";
  private static final String APPS_NAMESPACE_KEY =
      NamespaceManager.class.getName() + ".appsNamespace";

  /**
   * Set the value used to initialize the namespace of namespace-aware services.
   *
   * @param newNamespace the new namespace.
   * @throws IllegalArgumentException if namespace string is invalid.
   */
  public static void set(String newNamespace) {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (newNamespace == null) {
      if (environment != null) {
        environment.getAttributes().remove(CURRENT_NAMESPACE_KEY);
      }
    } else {
      validateNamespace(newNamespace);
      if (environment == null) {
        // IllegalStateException would make more sense, but this is compatible with code that
        // detected the absence of an environment by the NullPointerException it provoked due
        // to unprotected dereferences of `environment` in earlier versions.
        throw new NullPointerException(
            "Operation not allowed in a thread that is neither the original request thread "
                + "nor a thread created by ThreadManager");
      }
      // store this value as an attribute in the environment
      environment.getAttributes().put(CURRENT_NAMESPACE_KEY, newNamespace);
    }
  }

  /**
   *Returns the current namespace setting or either {@code null} or "" (empty) if not set.
   *
   * <p>If the current namespace is unset, callers should assume
   * the use of the "" (empty) namespace in all namespace-aware services.
   */
  public static String get() {
    return getNamespaceForKey(CURRENT_NAMESPACE_KEY);
  }

  /**
   * Returns the Google Apps domain referring this request or
   * otherwise the empty string ("").
   */
  public static String getGoogleAppsNamespace() {
    String appsNamespace = getNamespaceForKey(APPS_NAMESPACE_KEY);
    return appsNamespace == null ? "" : appsNamespace;
  }

  private static String getNamespaceForKey(String key) {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment == null) {
      return null;
    } else {
      return (String) environment.getAttributes().get(key);
    }
  }

  /**
   * Validate the format of a namespace string.
   * @throws IllegalArgumentException If the format of the namespace string
   *     is invalid.
   */
  public static void validateNamespace(String namespace) {
    if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
        throw new IllegalArgumentException(
            "Namespace '" + namespace + "' does not match pattern '" + NAMESPACE_PATTERN + "'.");
    }
  }

  // Make unconstructible.
  private NamespaceManager() {
  }
}
