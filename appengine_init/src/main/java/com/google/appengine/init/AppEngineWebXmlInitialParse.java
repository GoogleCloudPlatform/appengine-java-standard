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
package com.google.appengine.init;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/** Simple quick initial parse of appengine-web.xml shared between local tooling and runtime. */
public final class AppEngineWebXmlInitialParse {

  private static final Logger logger =
      Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());

  /** Provider for environment variables, allowing for substitution in tests. */
  private UnaryOperator<String> envProvider = System::getenv;

  private String runtimeId = "";
  private final String appEngineWebXmlFile;

  private static final String PROPERTIES = "system-properties";
  private static final String PROPERTY = "property";
  private static final String RUNTIME = "runtime";

  /** git commit number if the build is done via Maven */
  public static final String GIT_HASH;

  /** a formatted build timestamp with pattern yyyy-MM-dd'T'HH:mm:ssXXX */
  public static final String BUILD_TIMESTAMP;

  private static final String BUILD_VERSION;

  private static final Properties BUILD_PROPERTIES = new Properties();

  static {
    try (InputStream inputStream =
        AppEngineWebXmlInitialParse.class.getResourceAsStream(
            "/com/google/appengine/init/build.properties")) {
      BUILD_PROPERTIES.load(inputStream);
    } catch (Exception ok) {
      // File not there; that's fine, just continue.
    }
    GIT_HASH = BUILD_PROPERTIES.getProperty("buildNumber", "unknown");
    System.setProperty("appengine.git.hash", GIT_HASH);
    BUILD_TIMESTAMP = BUILD_PROPERTIES.getProperty("timestamp", "unknown");
    BUILD_VERSION = BUILD_PROPERTIES.getProperty("version", "unknown");
  }

  /**
   * Handles the logic for setting Jetty and Jakarta EE versions based on runtime, {@code
   * appengine-web.xml} properties, and System properties. System properties override {@code
   * appengine-web.xml} properties. It ensures that only one EE version is active and sets defaults
   * based on the Java runtime if no explicit version is chosen.
   *
   * <p>Only one of {@code appengine.use.EE8}, {@code appengine.use.EE10}, or {@code
   * appengine.use.EE11} can be set to {@code true}, otherwise an {@link IllegalArgumentException}
   * is thrown. If {@code appengine.use.EE11} is true, {@code appengine.use.jetty121} is also forced
   * to true. If {@code runtime} is {@code java25}, {@code appengine.use.jetty121} is forced to
   * true. For {@code java17} and {@code java21} runtimes, if {@code appengine.use.EE10=true} and
   * {@code appengine.use.jetty121=true}, then {@code appengine.use.EE11} is forced to true and a
   * warning is logged, as EE10 is not supported on Jetty 12.1.
   *
   * <p>If none of {@code appengine.use.EE8}, {@code appengine.use.EE10}, or {@code
   * appengine.use.EE11} are set to true, defaults are applied as follows:
   *
   * <ul>
   *   <li>{@code runtime="java17"}: Defaults to Jetty 9.4 based environment (EE6 / Servlet 3.1).
   *   <li>{@code runtime="java21"}: Defaults to Jetty 12.0 / EE10, unless {@code
   *       appengine.use.jetty121=true}, in which case it defaults to Jetty 12.1 / EE11.
   *   <li>{@code runtime="java25"}: Defaults to Jetty 12.1 / EE11.
   * </ul>
   *
   * <p>Resulting configurations:
   *
   * <ul>
   *   <li>{@code <runtime>java17</runtime>}:
   *       <ul>
   *         <li>No flags set: Jetty 9.4 (EE6) unless env variable
   *             EXPERIMENT_ENABLE_JETTY12_FOR_JAVA is set to true, in which case Jetty 12.0 (EE8)
   *             is used.
   *         <li>{@code appengine.use.EE8=true}: Jetty 12.0 (EE8)
   *         <li>{@code appengine.use.EE10=true}: Jetty 12.0 (EE10)
   *         <li>{@code appengine.use.EE10=true} and {@code appengine.use.jetty121=true}: Jetty 12.1
   *             (EE11)
   *         <li>{@code appengine.use.EE11=true}: Jetty 12.1 (EE11)
   *       </ul>
   *   <li>{@code <runtime>java21</runtime>}:
   *       <ul>
   *         <li>No flags set: Jetty 12.0 (EE10)
   *         <li>{@code appengine.use.jetty121=true}: Jetty 12.1 (EE11)
   *         <li>{@code appengine.use.EE8=true}: Jetty 12.0 (EE8)
   *         <li>{@code appengine.use.EE10=true}: Jetty 12.0 (EE10)
   *         <li>{@code appengine.use.EE10=true} and {@code appengine.use.jetty121=true}: Jetty 12.1
   *             (EE11)
   *         <li>{@code appengine.use.EE11=true}: Jetty 12.1 (EE11)
   *       </ul>
   *   <li>{@code <runtime>java25</runtime>}:
   *       <ul>
   *         <li>No flags set: Jetty 12.1 (EE11)
   *         <li>{@code appengine.use.EE8=true}: Jetty 12.1 (EE8)
   *         <li>{@code appengine.use.EE11=true}: Jetty 12.1 (EE11)
   *         <li>{@code appengine.use.EE10=true}: Throws {@link IllegalArgumentException}.
   *       </ul>
   * </ul>
   *
   * @throws IllegalArgumentException if more than one EE version flag is set to true, or if {@code
   *     appengine.use.EE10=true} with {@code runtime="java25"}.
   */
  public void handleRuntimeProperties() {

    // See if the Mendel experiment to enable HttpConnector is set automatically via env var:
    if (Objects.equals(envProvider.apply("EXPERIMENT_ENABLE_HTTP_CONNECTOR_FOR_JAVA"), "true")) {
      System.setProperty("appengine.ignore.cancelerror", "true");
      System.setProperty("appengine.use.HttpConnector", "true");
    }
    Properties appEngineWebXmlProperties = new Properties();
    try (final InputStream stream = new FileInputStream(appEngineWebXmlFile)) {
      final XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(stream);
      while (reader.hasNext()) {
        final XMLEvent event = reader.nextEvent();
        if (event.isStartElement()
            && event.asStartElement().getName().getLocalPart().equals(PROPERTIES)) {
          setAppEngineUseProperties(appEngineWebXmlProperties, reader);
        } else if (event.isStartElement()
            && event.asStartElement().getName().getLocalPart().equals(RUNTIME)) {
          XMLEvent runtime = reader.nextEvent();
          if (runtime.isCharacters()) {
            runtimeId = runtime.asCharacters().getData().trim();
            appEngineWebXmlProperties.setProperty("GAE_RUNTIME", runtimeId);
          }
        }
      }
    } catch (IOException | XMLStreamException e) {
      // Not critical, we can ignore and continue.
      e.printStackTrace();
      logger.log(Level.WARNING, "Cannot parse correctly {0}", appEngineWebXmlFile);
    }

    //  Override appEngineWebXmlProperties with system properties
    Properties systemProps = System.getProperties();
    for (String propName : systemProps.stringPropertyNames()) {
      if (propName.startsWith("appengine.") || propName.startsWith("GAE_RUNTIME")) {
        appEngineWebXmlProperties.setProperty(propName, systemProps.getProperty(propName));
      }
    }
    // Once runtimeId is known and we parsed all the file, correct default properties if needed,
    // and only if the setting has not been defined in appengine-web.xml.
    for (String propName : appEngineWebXmlProperties.stringPropertyNames()) {
      if (propName.startsWith("appengine.") || propName.startsWith("GAE_RUNTIME")) {
        System.setProperty(propName, appEngineWebXmlProperties.getProperty(propName));
      }
    }
    // reset runtimeId to the value possibly overridden by system properties
    runtimeId = System.getProperty("GAE_RUNTIME");
    if (runtimeId != null) {
      runtimeId = runtimeId.trim();
    }

    if ((Objects.equals(runtimeId, "java17") || Objects.equals(runtimeId, "java21"))
        && Boolean.parseBoolean(System.getProperty("appengine.use.EE10", "false"))
        && Boolean.parseBoolean(System.getProperty("appengine.use.jetty121", "false"))) {
      System.setProperty("appengine.use.EE11", "true");
      System.setProperty("appengine.use.EE10", "false");
      logger.warning(
          "appengine.use.EE10 is not supported with Jetty 12.1 for runtime "
              + runtimeId
              + ", upgrading to appengine.use.EE11.");
    }

    // 4. Parse and validate
    boolean useEE8 = Boolean.parseBoolean(System.getProperty("appengine.use.EE8", "false"));
    boolean useEE10 = Boolean.parseBoolean(System.getProperty("appengine.use.EE10", "false"));
    boolean useEE11 = Boolean.parseBoolean(System.getProperty("appengine.use.EE11", "false"));

    int trueCount = 0;
    if (useEE8) {
      trueCount++;
    }
    if (useEE10) {
      trueCount++;
    }
    if (useEE11) {
      trueCount++;
    }
    if (trueCount > 1) {
      throw new IllegalArgumentException(
          "Only one of appengine.use.EE8, appengine.use.EE10, or appengine.use.EE11 can be true.");
    }
    if (trueCount == 0) {
      // Apply defaults based on javaVersion
      if (Objects.equals(runtimeId, "java17")) {
        System.setProperty(
            "appengine.use.EE8",
            String.valueOf(
                Objects.equals(envProvider.apply("EXPERIMENT_ENABLE_JETTY12_FOR_JAVA"), "true")));
      } else if (Objects.equals(runtimeId, "java21")) {
        if (Boolean.parseBoolean(System.getProperty("appengine.use.jetty121", "false"))) {
          System.setProperty("appengine.use.EE11", "true");
          System.setProperty("appengine.use.EE10", "false");
        } else {
          System.setProperty("appengine.use.EE10", "true");
        }
      } else if (Objects.equals(runtimeId, "java25")) {
        System.setProperty("appengine.use.EE11", "true");
        System.setProperty("appengine.use.jetty121", "true");
      }
    } else {
      if (Objects.equals(runtimeId, "java25")) {
        System.setProperty("appengine.use.jetty121", "true");
      }
    }
    if ((appEngineWebXmlProperties.getProperty("appengine.use.jetty121") == null)
        && Boolean.getBoolean("appengine.use.EE11")) {
      System.setProperty("appengine.use.jetty121", "true");
    }

    if (Objects.equals(runtimeId, "java25") && Boolean.getBoolean("appengine.use.EE10")) {
      throw new IllegalArgumentException("appengine.use.EE10 is not supported in Jetty121");
    }

    // Log the runtime configuration so we can see it in the app logs.
    StringBuilder configLog =
        new StringBuilder("AppEngine runtime configuration: runtimeId=").append(runtimeId);
    if (Objects.equals(envProvider.apply("EXPERIMENT_ENABLE_JETTY12_FOR_JAVA"), "true")) {
      configLog.append(", with Jetty 12");
    }
    if (Objects.equals(envProvider.apply("EXPERIMENT_ENABLE_HTTP_CONNECTOR_FOR_JAVA"), "true")) {
      configLog.append(", with HTTP Connector");
    }
    int initialLength = configLog.length();
    if (Boolean.getBoolean("appengine.use.EE8")) {
      configLog.append(", appengine.use.EE8=true");
    }
    if (Boolean.getBoolean("appengine.use.EE10")) {
      configLog.append(", appengine.use.EE10=true");
    }
    if (Boolean.getBoolean("appengine.use.EE11")) {
      configLog.append(", appengine.use.EE11=true");
    }
    if (Boolean.getBoolean("appengine.use.jetty121")) {
      configLog.append(", appengine.use.jetty121=true");
    }
    if (configLog.length() == initialLength) {
      configLog.append(" no extra flag set");
    }
    logger.info(configLog.toString());
  }

  /**
   * Reads {@code <property>} elements from inside a {@code <system-properties>} block in {@code
   * appengine-web.xml} and populates the provided {@link Properties} config object. Also sets
   * specific system properties required for runtime configuration during this initial parse phase.
   *
   * @param config The {@link Properties} object to populate with properties from the XML.
   * @param reader The {@link XMLEventReader} positioned at the start of the system-properties
   *     block.
   * @throws XMLStreamException if there is an error reading the XML stream.
   */
  private void setAppEngineUseProperties(Properties config, final XMLEventReader reader)
      throws XMLStreamException {
    while (reader.hasNext()) {
      final XMLEvent event = reader.nextEvent();
      if (event.isEndElement()
          && event.asEndElement().getName().getLocalPart().equals(PROPERTIES)) {
        return;
      }
      if (event.isStartElement()) {
        final StartElement element = event.asStartElement();
        final String elementName = element.getName().getLocalPart();
        if (elementName.equals(PROPERTY)) {
          String prop = element.getAttributeByName(new QName("name")).getValue();
          String value = element.getAttributeByName(new QName("value")).getValue();
          config.put(prop, value);
          if (prop.equalsIgnoreCase("com.google.apphosting.runtime.jetty94.LEGACY_MODE")) {
            System.setProperty("com.google.apphosting.runtime.jetty94.LEGACY_MODE", value);
          } else if (prop.equalsIgnoreCase("appengine.use.HttpConnector")) {
            System.setProperty("appengine.use.HttpConnector", value);
          } else if (prop.equalsIgnoreCase("appengine.use.allheaders")) {
            System.setProperty("appengine.use.allheaders", value);
          } else if (prop.equalsIgnoreCase("appengine.ignore.responseSizeLimit")) {
            System.setProperty("appengine.ignore.responseSizeLimit", value);
          }
        }
      }
    }
  }

  public AppEngineWebXmlInitialParse(String file) {
    this.appEngineWebXmlFile = file;
    if (!GIT_HASH.equals("unknown")) {
      logger.log(
          Level.INFO,
          "appengine runtime jars built on {0} from commit {1}, version {2}",
          new Object[] {BUILD_TIMESTAMP, GIT_HASH, BUILD_VERSION});
    }
  }

  void setEnvProvider(UnaryOperator<String> envProvider) {
    this.envProvider = envProvider;
  }
}
