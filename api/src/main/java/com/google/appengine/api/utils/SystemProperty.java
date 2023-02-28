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

package com.google.appengine.api.utils;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Global system properties which are set by App Engine.
 * <p>
 * Example code:
 * <pre>{@code
 * if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Production) {
 *   // do something that's production-only
 * }
 * String version = SystemProperty.version.get();
 * }</pre>
 *
 */
public class SystemProperty {

  // We partition the system properties as follows:
  // c.g.a.runtime.*: Pertains to runtime environment (e.g. runtime MPM)
  // c.g.a.application.*: Pertains to app version uploaded by user
  // c.g.a.instance.*: Pertains to this specific clone

  /**
   * The current executing environment. Has the key,
   * {@code "com.google.appengine.runtime.environment"}.
   * Has the values {@code "Production"} and {@code "Development"}.
   */
  public static final Environment environment = new Environment();

  /**
   * The current executing runtime version. Has the key,
   * {@code "com.google.appengine.runtime.version"}.
   * A Version value is composed of period-separated integers, for example,
   * "1.2.8".
   */
  public static final SystemProperty version =
      new SystemProperty("com.google.appengine.runtime.version");

  /**
   * The application identifier for the current application.  Has the
   * key, {@code "com.google.appengine.application.id"}.
   */
  public static final SystemProperty applicationId =
      new SystemProperty("com.google.appengine.application.id");

  /**
   * The version identifier for the current application version.  Result is of
   * the form {@literal <major>.<minor>} where {@literal <major>} is the version
   * name supplied at deploy time and {@literal <minor>} is a timestamp value
   * maintained by App Engine. Has the key
   * {@code "com.google.appengine.application.version"}.
   */
  public static final SystemProperty applicationVersion =
      new SystemProperty("com.google.appengine.application.version");

  /**
   * @deprecated Use {@link
   *             com.google.appengine.api.modules.ModulesService#getCurrentInstanceId()}
   */
  @Deprecated
  public static final SystemProperty instanceReplicaId = 
    new SystemProperty("deprecated");

  /**
   * The current executing environment. Has the key,
   * {@code "com.google.appengine.runtime.environment"}.
   * The set of values are specified by {@link Environment.Value Value}.
   */
  public static class Environment extends SystemProperty {

    /**
     * The set of possible values for Environment.
     */
    public enum Value {
      Production,
      Development;

      public String value() {
        return toString();
      }
    }

    private Environment() {
      super("com.google.appengine.runtime.environment");
    }

    /**
     * Returns the Value that the SystemProperty is set to.
     *
     * @return null if the Environment is not set, or is set to a value that
     * does not correspond to any predefined {@code Value}.
     */
    public @Nullable Value value() {
      try {
        String propertyValue = get();
        return propertyValue != null ? Value.valueOf(propertyValue) : null;
      } catch (IllegalArgumentException e) {
        return null;
      }
    }

    /**
     * Sets the Environment to {@code value}. Equivalent to
     * {@code set(value.value())}.
     *
     */
    public void set(Value value) {
      set(value.value());
    }
  }

  private SystemProperty(String key) {
    this.key = key;
  }

  private final String key;

  /**
   * The key for the system property.
   */
  public String key() {
    return key;
  }

  /**
   * Gets the value of the system property.
   * Equivalent to {@code System.getProperty(key())}.
   */
  public @Nullable String get() {
    return System.getProperty(key());
  }

  /**
   * Sets the value of the system property.
   * Equivalent to {@code System.setProperty(key(), value)}.
   */
  public void set(String value) {
    System.setProperty(key(), value);
  }
}
