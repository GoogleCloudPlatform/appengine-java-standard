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

import com.google.appengine.tools.info.AppengineSdk;
import com.google.appengine.tools.util.Logging;
import com.google.appengine.tools.util.Option;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.awt.Toolkit;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

/**
 * Contains operations common to the {@linkplain DevAppServerMain Java dev app server startup} and
 * the {@linkplain com.google.appengine.tools.development.devappserver2.StandaloneInstance
 * devappserver2 subprocess instance}.
 */
public abstract class SharedMain {

  private static String originalTimeZone;

  private boolean disableRestrictedCheck = false;
  private String runtime = null;
  private List<String> propertyOptions = null;

  /**
   * An exception class that is thrown to indicate that command-line processing should be aborted.
   */
  private static class TerminationException extends RuntimeException {

    TerminationException() {
      super();
    }
  }

  /**
   * Returns the list of built-in {@link Option Options} that apply to both the monolithic dev app
   * server (in the Java SDK) and instances running under the Python devappserver2.
   */
  protected List<Option> getSharedOptions() {
    return Arrays.asList(
        new Option("h", "help", true) {
          @Override
          public void apply() {
            printHelp(System.err);
            throw new TerminationException();
          }

          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(" --help, -h                 Show this help message and exit.");
          }
        },
        new Option(null, "sdk_root", false) {
          @Override
          public void apply() {
            System.setProperty("appengine.sdk.root", getValue());
          }

          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(
                " --sdk_root=DIR             Overrides where the SDK is located.");
          }
        },
        new Option(null, "disable_restricted_check", true) {
          @Override
          public void apply() {
            disableRestrictedCheck = true;
          }
          // --disable_restricted_check is undocumented on purpose. The feature was
          // created as 1.6.4 is having performance problems for certain apps around
          // Runtime.checkRestricted(). While we believe cl/29002288 fixes the
          // problem, we decided to leave this in case there
          // are still cases that have problems.
        },
        new Option(null, "property", false) {
          @Override
          public void apply() {
            propertyOptions = getValues();
          }
          // --property=PROPERTY
          // A generic way to provide properties. The value is a colon separated set of properties.
          // Each property may the form:
          //   NAME=VALUE
          //   NAME=        the value is the empty string
          //   NAME         shorthand for NAME=true
          //   noNAME       shorthand for NAME=false
          // --property may be specified on the command line multiple times.
          //
          // TODO - document this once a method to validate the keys are in place (the key
          // piece being a way for services to register keys without having to modify this file).
        },
        new Option(null, "allow_remote_shutdown", true) {
          @Override
          public void apply() {
            System.setProperty("appengine.allowRemoteShutdown", "true");
          }
        },
        new Option(null, "no_java_agent", true) {
          @Override
          public void apply() {
            // No op now.
          }
        },
        new Option(null, "runtime", false) {
          @Override
          public void apply() {
            runtime = getValue();
          }

          @Override
          public List<String> getHelpLines() {
            return ImmutableList.of(" --runtime=runtime_id       (java8 only).");
          }
        });
  }

  protected static void sharedInit() {
    recordTimeZone();
    Logging.initializeLogging();
    if (System.getProperty("os.name").equalsIgnoreCase("Mac OS X")) {
      // N.B.: Force AWT to initialize on the main thread.  If
      // this doesn't happen, we get a SIGBUS crash on Mac OS X when the
      // stub implementation of the Images API tries to use Java2D.
      Toolkit.getDefaultToolkit();
    }
  }

  /**
   * We attempt to record user.timezone before the JVM alters its value. This can happen just by
   * asking for {@link java.util.TimeZone#getDefault()}.
   *
   * <p>We need this information later, so that we can know if the user actually requested an
   * override of the timezone. We can still be wrong about this, for example, if someone directly or
   * indirectly calls {@code TimeZone.getDefault} before the main method to this class. This doesn't
   * happen in the App Engine tools themselves, but might theoretically happen in some third-party
   * tool that wraps the App Engine tools. In that case, they can set {@code
   * appengine.user.timezone} to override what we're inferring for user.timezone.
   */
  private static void recordTimeZone() {
    originalTimeZone = System.getProperty("user.timezone");
    // For some reasons, java8/11 default to "" and Java17 to null, so we keep default to empty
    // which is the value handled later.
    if (originalTimeZone == null) {
      originalTimeZone = "";
    }
  }

  protected abstract void printHelp(PrintStream out);

  protected void postServerActions(Map<String, String> stringProperties) {
    // TODO Consider reading properties from a file, or
    // parsing an Option.
    setTimeZone(stringProperties);
    if (disableRestrictedCheck) {
      stringProperties.put("appengine.disableRestrictedCheck", "");
    }
  }

  protected void addPropertyOptionToProperties(Map<String, String> properties) {
    properties.putAll(parsePropertiesList(propertyOptions));
  }

  protected Map<String, String> getSystemProperties() {
    Properties properties = System.getProperties();
    // We do not expect that the system properties will contain non-String keys or values,
    // but check just in case.
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
        throw new IllegalArgumentException("Non-string property " + entry.getKey());
      }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    Map<String, String> stringProperties = (Map<String, String>) (Map) properties;
    return Collections.checkedMap(stringProperties, String.class, String.class);
  }

  private void setTimeZone(Map<String, String> serviceProperties) {
    String timeZone = serviceProperties.get("appengine.user.timezone");
    if (timeZone != null) {
      TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    } else {
      timeZone = originalTimeZone;
    }
    serviceProperties.put("appengine.user.timezone.impl", timeZone);
  }

  public void validateWarPath(File war) {
    if (!war.exists()) {
      System.err.println("Unable to find the webapp directory " + war);
      printHelp(System.err);
      throw new TerminationException();
    } else if (!war.isDirectory()) {
      System.err.println("dev_appserver only accepts webapp directories, not war files.");
      printHelp(System.err);
      throw new TerminationException();
    }
  }

  /**
   * Calculate which runtime should be used for this local SDK execution. Can be Java8. The
   * information may come from the --runtime flag or appengine-web.xml or the lack of web.xml.
   */
  protected void configureRuntime(File appDirectory) {
    if (runtime != null) {
      return;
    }
    AppEngineWebXml appEngineWebXml =
        new AppEngineWebXmlReader(appDirectory.getAbsolutePath()).readAppEngineWebXml();
    runtime = appEngineWebXml.getRuntime();
    if (runtime == null) {
      runtime = "java8"; // Default.
    }
    if (runtime.equals("java7")) {
      throw new IllegalArgumentException("the Java7 runtime is not supported anymore.");
    }
    // Locally set the correct values for all runtimes, for EE8 and EE10/11 system properties to the
    // process of the devappserver.
    Map<String, String> props = appEngineWebXml.getSystemProperties();
    if (runtime.equals("java25")) {
      System.setProperty("appengine.use.jetty121", "true");
      // Validate that Jetty121 is not used with EE10.
      if (props.containsKey("appengine.use.EE10")) {
        // We really want the local dev_appserver to fail fast if Jetty121 is used with EE10.
        // Not like in production for already deployed apps.
        throw new IllegalArgumentException("appengine.use.EE10 is not supported in Jetty121");
      }
      if (props.containsKey("appengine.use.EE8")) {
        System.setProperty("appengine.use.EE8", props.get("appengine.use.EE8"));
      } else {
        System.setProperty("appengine.use.EE11", "true");
      }
    }
    if (props.containsKey("appengine.use.EE8")) {
      System.setProperty("appengine.use.EE8", props.get("appengine.use.EE8"));
    } else if (props.containsKey("appengine.use.EE11")) {
      System.setProperty("appengine.use.EE11", props.get("appengine.use.EE11"));
    } else if (props.containsKey("appengine.use.EE10")) {
      System.setProperty("appengine.use.EE10", props.get("appengine.use.EE10"));
    }
    if (props.containsKey("appengine.use.jetty121")) {
      System.setProperty("appengine.use.jetty121", props.get("appengine.use.jetty121"));
    }
    AppengineSdk.resetSdk();

    sharedInit();
  }

  protected String getRuntime() {
    return runtime;
  }

  /**
   * Parse the properties list. Each string in the last may take the form: name=value name shorthand
   * for name=true noname shorthand for name=false name= required syntax to specify an empty value
   *
   * @param properties A list of unparsed properties (may be null).
   * @return A map from property names to values.
   */
  @VisibleForTesting
  static Map<String, String> parsePropertiesList(List<String> properties) {
    Map<String, String> parsedProperties = new HashMap<>();
    if (properties != null) {
      for (String property : properties) {
        String[] propertyKeyValue = property.split("=", 2);
        if (propertyKeyValue.length == 2) {
          parsedProperties.put(propertyKeyValue[0], propertyKeyValue[1]);
        } else if (propertyKeyValue[0].startsWith("no")) {
          parsedProperties.put(propertyKeyValue[0].substring(2), "false");
        } else {
          parsedProperties.put(propertyKeyValue[0], "true");
        }
      }
    }
    return parsedProperties;
  }
}
