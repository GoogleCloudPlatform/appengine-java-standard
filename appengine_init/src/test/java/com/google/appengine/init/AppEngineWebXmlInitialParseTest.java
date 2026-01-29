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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** */
@RunWith(JUnit4.class)
public class AppEngineWebXmlInitialParseTest {
  private static final String TEST_CONFIG_FILE = "appengine-web.xml";
  private Path tempDir;
  private Path tempFile;

  @Before
  public void setUp() throws IOException {
    // Clear all relevant system properties before each test to ensure isolation
    System.clearProperty("appengine.use.EE8");
    System.clearProperty("appengine.use.EE10");
    System.clearProperty("appengine.use.EE11");
    System.clearProperty("appengine.use.jetty121");
    System.clearProperty("GAE_RUNTIME");
    System.clearProperty("appengine.git.hash");
    System.clearProperty("appengine.build.timestamp");
    System.clearProperty("appengine.build.version");
  }

  @After
  public void tearDown() throws IOException {
    // Clear system properties again after each test
    System.clearProperty("appengine.use.EE8");
    System.clearProperty("appengine.use.EE10");
    System.clearProperty("appengine.use.EE11");
    System.clearProperty("appengine.use.jetty121");
    System.clearProperty("GAE_RUNTIME");
    System.clearProperty("appengine.git.hash");
    System.clearProperty("appengine.build.timestamp");
    System.clearProperty("appengine.build.version");

    assertFalse(Boolean.getBoolean("appengine.use.EE10"));

    // Delete the temporary file and directory.
    Files.deleteIfExists(tempFile);
    Files.deleteIfExists(tempDir);
  }

  private void createTempAppEngineWebXml(String content) throws IOException {
    // Create a temporary configuration file for the tests
    tempDir = Files.createTempDirectory("appengine-init-test");
    tempFile = tempDir.resolve(TEST_CONFIG_FILE);
    try (Writer writer = Files.newBufferedWriter(tempFile, UTF_8)) {
      writer.write(content);
    }
  }

  /** Test of parse method, of class AppEngineWebXmlInitialParse. */
  @Test
  public void testJava17() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="foo" value="bar"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertFalse(Boolean.getBoolean("appengine.use.EE8")); // Default to jetty 9.4 which is EE6
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava17WithJetty121() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="appengine.use.jetty121" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava17WithEE8AndJetty121() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="appengine.use.EE8" value="true"/>
                <property name="appengine.use.jetty121" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava17EE10() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="foo" value="bar"/>
                <property name="appengine.use.EE10" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="foo" value="bar"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertTrue(Boolean.getBoolean("appengine.use.EE10")); // Default to EE10
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21WithEE10Explicit() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="appengine.use.EE10" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertTrue(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21WithEE8AndJetty121() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="appengine.use.EE8" value="true"/>
                <property name="appengine.use.jetty121" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21EE8() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="foo" value="bar"/>
                <property name="appengine.use.EE8" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21EE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="foo" value="bar"/>
                <property name="appengine.use.EE11" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11")); // Default to EE11
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121")); // Default to jetty 12.1
  }

  @Test
  public void testJava25() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java25</runtime>
            <system-properties>
                <property name="java.util.logging.config.file" value="WEB-INF/logging.properties"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121")); // Default to jetty 12.1
  }

  @Test
  public void testJava25WithOverrideEE8() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java25</runtime>
            <system-properties>
                <property name="java.util.logging.config.file" value="WEB-INF/logging.properties"/>
            </system-properties>
        </appengine-web-app>
        """);

    System.setProperty("appengine.use.EE8", "true");

    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21WithOverrideEE8() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="java.util.logging.config.file" value="WEB-INF/logging.properties"/>
            </system-properties>
        </appengine-web-app>
        """);
    System.setProperty("appengine.use.EE8", "true");

    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
    assertEquals("java21", System.getProperty("GAE_RUNTIME"));
  }

  @Test
  public void testJava21WithOverrideJetty121IsEE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="foo" value="bar"/>
                <property name="appengine.use.jetty121" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11")); // Default to EE11
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121")); // Default to jetty 12.1
  }

  @Test
  public void testJava21WithSystemOverrideJetty121IsEE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="foo" value="bar"/>
            </system-properties>
        </appengine-web-app>
        """);
    System.setProperty("appengine.use.jetty121", "true");

    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11")); // Default to EE11
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121")); // Default to jetty 12.1
  }

  @Test
  public void testJava17WithEE10AndJetty121IsEE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="appengine.use.EE10" value="true"/>
                <property name="appengine.use.jetty121" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21WithEE10AndJetty121IsEE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="appengine.use.EE10" value="true"/>
                <property name="appengine.use.jetty121" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava21WithEE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java21</runtime>
            <system-properties>
                <property name="appengine.use.EE11" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava17WithExperimentEnableJetty12() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
        </appengine-web-app>
        """);
    AppEngineWebXmlInitialParse parser =
        new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());
    parser.setEnvProvider(
        key -> {
          if (Objects.equals(key, "EXPERIMENT_ENABLE_JETTY12_FOR_JAVA")) {
            return "true";
          }
          return null;
        });
    parser.handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava17WithWhitespace() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>
              java17
            </runtime>
        </appengine-web-app>
        """);
    AppEngineWebXmlInitialParse parser =
        new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());
    parser.setEnvProvider(
        key -> {
          if (Objects.equals(key, "EXPERIMENT_ENABLE_JETTY12_FOR_JAVA")) {
            return "true";
          }
          return null;
        });
    parser.handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
  }

  @Test
  public void testHttpConnectorExperiment() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
        </appengine-web-app>
        """);
    AppEngineWebXmlInitialParse parser =
        new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());
    parser.setEnvProvider(
        key -> {
          if (Objects.equals(key, "EXPERIMENT_ENABLE_HTTP_CONNECTOR_FOR_JAVA")) {
            return "true";
          }
          return null;
        });
    parser.handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.HttpConnector"));
    assertTrue(Boolean.getBoolean("appengine.ignore.cancelerror"));
  }

  @Test
  public void testMultipleEEFlags() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="appengine.use.EE8" value="true"/>
                <property name="appengine.use.EE10" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
                .handleRuntimeProperties());
  }

  @Test
  public void testJava25WithEE10() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java25</runtime>
            <system-properties>
                <property name="appengine.use.EE10" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
                .handleRuntimeProperties());
  }

  @Test
  public void testJava17EE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="appengine.use.EE11" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava17EE8() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
            <system-properties>
                <property name="appengine.use.EE8" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testJava25EE11() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java25</runtime>
            <system-properties>
                <property name="appengine.use.EE11" value="true"/>
            </system-properties>
        </appengine-web-app>
        """);
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11"));
    assertFalse(Boolean.getBoolean("appengine.use.EE8"));
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertTrue(Boolean.getBoolean("appengine.use.jetty121"));
  }

  @Test
  public void testSystemPropertyOverrideRuntime() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
        </appengine-web-app>
        """);
    System.setProperty("GAE_RUNTIME", "java21");
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    // Should behave like java21 (EE10=true)
    assertTrue(Boolean.getBoolean("appengine.use.EE10"));
  }

  @Test
  public void testSystemPropertyOverrideRuntimeWithWhitespace() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
        </appengine-web-app>
        """);
    System.setProperty("GAE_RUNTIME", " java21 ");
    new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
    // Should behave like java21
    assertTrue(Boolean.getBoolean("appengine.use.EE10"));
  }

  @Test
  public void testLogEE8() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java17</runtime>
              <system-properties>
                  <property name="appengine.use.EE8" value="true"/>
              </system-properties>
          </appengine-web-app>
          """);
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains("appengine.use.EE8=true")) {
          found = true;
          break;
        }
      }
      assertTrue("Log message should contain appengine.use.EE8=true", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testLogEE10() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java21</runtime>
          </appengine-web-app>
          """);
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains("appengine.use.EE10=true")) {
          found = true;
          break;
        }
      }
      assertTrue("Log message should contain appengine.use.EE10=true", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testLogEE11() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java25</runtime>
          </appengine-web-app>
          """);
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains("appengine.use.EE11=true")) {
          found = true;
          break;
        }
      }
      assertTrue("Log message should contain appengine.use.EE11=true", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testLogJetty121() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java25</runtime>
          </appengine-web-app>
          """);
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains("appengine.use.jetty121=true")) {
          found = true;
          break;
        }
      }
      assertTrue("Log message should contain appengine.use.jetty121=true", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testLogAllFlagsFalse() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java17</runtime>
          </appengine-web-app>
          """);
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains(" no extra flag set")) {
          found = true;
          break;
        }
      }
      assertTrue("Log message should contain ' no extra flag set'", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testLogNotAllFlagsFalseWhenFlagIsSet() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java21</runtime>
          </appengine-web-app>
          """);
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath()).handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains(" no extra flag set")) {
          found = true;
          break;
        }
      }
      assertFalse("Log message should NOT contain ' no extra flag set'", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testLogExperimentJetty12() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java17</runtime>
          </appengine-web-app>
          """);
      AppEngineWebXmlInitialParse parser =
          new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());
      parser.setEnvProvider(
          key -> {
            if (Objects.equals(key, "EXPERIMENT_ENABLE_JETTY12_FOR_JAVA")) {
              return "true";
            }
            return null;
          });
      parser.handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains("with Jetty 12")) {
          found = true;
          break;
        }
      }
      assertTrue("Log message should contain with Jetty 12", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testLogExperimentHttpConnector() throws IOException {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java17</runtime>
          </appengine-web-app>
          """);
      AppEngineWebXmlInitialParse parser =
          new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());
      parser.setEnvProvider(
          key -> {
            if (Objects.equals(key, "EXPERIMENT_ENABLE_HTTP_CONNECTOR_FOR_JAVA")) {
              return "true";
            }
            return null;
          });
      parser.handleRuntimeProperties();
      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains("with HTTP Connector")) {
          found = true;
          break;
        }
      }
      assertTrue("Log message should contain with HTTP Connector", found);
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static class TestHandler extends Handler {
    final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
  }
}
