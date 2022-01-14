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

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;

/**
 * Manager for a web applications System Properties.
 * <p>
 * In production each Server Instance has its own independent System Properties.
 * In the Java Development Server the System Properties are shared.
 * <p>
 * We modify the system properties when the dev appserver is launched using
 * key/value pairs defined in appengine-web.xml. This can make it very easy to
 * leak state across tests that launch instances of the dev appserver, so we
 * keep track of the original values all system properties at start-up.
 * We then restore the values when we shutdown the server.
 *
 */
public class SystemPropertiesManager {
  private static final Logger logger = Logger.getLogger(SystemPropertiesManager.class.getName());

  /*
   * Map property name to the appengine-web.xml file where the property is defined.
   * Defining a property in separate appengine-web.xml files with conflicting
   * values currently causes an AppEngineConfigException because System
   * Properties are a JVM global resource shared between servers.
   */
  @GuardedBy("this")
  private final Map<String, File> propertyNameToFileMap;
  @GuardedBy("this")
  private final Properties originalSystemProperties;

  SystemPropertiesManager() {
    propertyNameToFileMap = new HashMap<String, File>();
    originalSystemProperties = new Properties();
    originalSystemProperties.putAll(System.getProperties());
  }

  synchronized Properties getOriginalSystemProperties() {
    Properties result =  new Properties();
    result.putAll(originalSystemProperties);
    return result;
  }

  /**
   * Sets {@link SystemProperty} values for the current application.
   * <p>
   * In a single server application (loaded from a single WAR):
   * <ol>
   * <b>appId</b> should be set to {@link AppEngineWebXml#getAppId()}
   * </li>.
   * <li>
   * <b>majorVersionId</b> should be set to the {@link
   * com.google.apphosting.utils.config.AppEngineWebXml#getMajorVersionId()}.
   * </li>
   * </ol>
   * <p>
   * In a multi server application (loaded from an EAR):
   * <ol>
   * <li>
   * <b>appId</b> should be set to {@link
   * com.google.apphosting.utils.config.AppEngineApplicationXml#getApplicationId()}
   * </li>.
   * <li>
   * <b>majorVersionId</b> should be set to the {@link AppEngineWebXml#getMajorVersionId()}
   * for the first web application in the EAR directory according to
   * {@link com.google.apphosting.utils.config.EarHelper#readEarInfo(String, File)}.
   * This setting is for compatibility purposes. The value may not match the
   * configuration for all servers in cases different servers have different
   * major versions.
   * </li>
   * @param release the SDK release (SdkInfo.getLocalVersion().getRelease()).
   * @param applicationId the application id.
   * @param majorVersionId the application version.
   */
  public void setAppengineSystemProperties(String release, String applicationId,
      String majorVersionId) {
    // NB Shouldn't need to save away these properties
    SystemProperty.environment.set(SystemProperty.Environment.Value.Development);
    if (release == null) {
      // Happens in unit tests and other possibly weird scenarios
      release = "null";
    }
    SystemProperty.version.set(release);
    SystemProperty.applicationId.set(applicationId);
    SystemProperty.applicationVersion.set(majorVersionId + ".1");
  }

  public synchronized void setSystemProperties(
      AppEngineWebXml appEngineWebXml, File appengineWebXmlFile) throws AppEngineConfigException {
    Map<String, String> originalSystemProperties = copySystemProperties();

    // Validate before making any changes.
    for (Map.Entry<String, String> entry : appEngineWebXml.getSystemProperties().entrySet()) {
      if (propertyNameToFileMap.containsKey(entry.getKey())
          && !entry.getValue().equals(System.getProperty(entry.getKey()))
          && !propertyNameToFileMap.get(entry.getKey()).equals(appengineWebXmlFile)) {
        String template = "Property %s is defined in %s and in %s with different values. "
            + "Currently Java Development Server requires matching values.";
        String message = String.format(template, entry.getKey(),
            appengineWebXmlFile.getAbsolutePath(), propertyNameToFileMap.get(entry.getKey()));
        logger.severe(message);
        throw new AppEngineConfigException(message);
      }
      if (originalSystemProperties.containsKey(entry.getKey())) {
        String message = String.format(
            "Overwriting system property key '%s', value '%s' with value '%s' from '%s'",
            entry.getKey(), originalSystemProperties.get(entry.getKey()),
                entry.getValue(), appengineWebXmlFile.getAbsolutePath());
        logger.info(message);
      }
    }

    // Clear entries from propertyToAppengineWebXmlFileMap for server being
    // reloaded. Note that when an appengine-web.xml file is changed the
    // properties being set in the file may change too. It would be nice to
    // clear them from system properties too but we don't know if some other
    // server also accesses a property. Though we confirmed no two servers
    // set the same system property in appengine-web.xml to different values
    // we have not checked they do not set or use a common property in code at
    // runtime.
    for (Iterator<Map.Entry<String, File>> iterator =
        propertyNameToFileMap.entrySet().iterator();
        iterator.hasNext();) {
      Map.Entry<String, File> entry = iterator.next();
      if (entry.getValue().equals(appengineWebXmlFile)) {
        iterator.remove();
      }
    }

    for (Map.Entry<String, String> entry : appEngineWebXml.getSystemProperties().entrySet()) {
      propertyNameToFileMap.put(entry.getKey(), appengineWebXmlFile);
    }
    System.getProperties().putAll(appEngineWebXml.getSystemProperties());
  }

  /**
   * Clears system properties we set in {@link #setSystemProperties}. Also,
   * restores system properties to their values at the time this
   * {@link SystemPropertiesManager} was constructed. Additional system
   * properties added after this {@link SystemPropertiesManager} was
   * constructed are not cleared for historical reasons.
   * <p>
   * Note that restoring system properties that have been set by non-user
   * code we did not write could theoretically affect behavior.
   */
  public synchronized void restoreSystemProperties () {
    // First clear out all the values we set when we started the server.
    for (String key : propertyNameToFileMap.keySet()) {
      System.clearProperty(key);
    }

    propertyNameToFileMap.clear();

    // Now restore any values that were overridden to their original
    // values.
    System.getProperties().putAll(originalSystemProperties);
    // TODO: Consider just calling
    // System.setProperties(originalSysProps) here instead.  There
    // should not be any system properties set that should survive
    // past this point, but it's a bit risky to change this now.
  }

  /**
   * Returns a map with a copy of return value from {@link System#getProperties()}.
   */
  @VisibleForTesting
  static Map<String, String> copySystemProperties() {
    HashMap<String, String> copy = new HashMap<String, String>();
    for (String key : System.getProperties().stringPropertyNames()) {
      copy.put(key, System.getProperties().getProperty(key));
    }
    return copy;
  }
}
