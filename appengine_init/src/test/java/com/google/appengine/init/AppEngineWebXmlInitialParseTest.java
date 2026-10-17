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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.Writer;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
    System.clearProperty("appengine.ignore.cancelerror");
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
    System.clearProperty("appengine.ignore.cancelerror");
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
    assertTrue(Boolean.getBoolean("appengine.use.EE8")); // Default to jetty 12.0 which is EE8
    assertFalse(Boolean.getBoolean("appengine.use.EE10"));
    assertFalse(Boolean.getBoolean("appengine.use.EE11"));
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
    assertTrue(Boolean.getBoolean("appengine.use.EE8")); // Default to jetty 12.0 which is EE8
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
  public void testJava17WithJetty12() throws IOException {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
        </appengine-web-app>
        """);
    AppEngineWebXmlInitialParse parser =
        new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());

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
    parser.handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE8"));
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
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
          .handleRuntimeProperties();
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
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
          .handleRuntimeProperties();
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
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
          .handleRuntimeProperties();
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
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
          .handleRuntimeProperties();
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
              <runtime>java11</runtime>
          </appengine-web-app>
          """);
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
          .handleRuntimeProperties();
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
      new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath())
          .handleRuntimeProperties();
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
  public void testLogJetty12() throws IOException {
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

      parser.handleRuntimeProperties();
      boolean foundJetty12 = false;
      boolean foundHttpConnector = false;
      for (LogRecord record : handler.records) {
        if (record.getMessage().contains("with Jetty 12")) {
          foundJetty12 = true;
        }
        if (record.getMessage().contains("with HTTP Connector")) {
          foundHttpConnector = true;
        }
      }
      assertTrue("Log message should contain with Jetty 12", foundJetty12);
      assertTrue("Log message should contain with HTTP Connector", foundHttpConnector);
      assertTrue(Boolean.getBoolean("appengine.ignore.cancelerror"));
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

  @Test
  public void testReportCloneAttribute() throws Exception {
    assertReportedEeVersion("java17", "8");
    setUp();
    assertReportedEeVersion("java21", "10");
    setUp();
    assertReportedEeVersion("java25", "11");
    setUp();
    assertReportedEeVersion("java11", "6");
  }

  private static String getServerHostPort(HttpServer server) {
    InetAddress addr = server.getAddress().getAddress();
    String host =
        addr instanceof Inet6Address ? "[" + addr.getHostAddress() + "]" : addr.getHostAddress();
    return host + ":" + server.getAddress().getPort();
  }

  private static final class RecordedMetadataRequest {
    final String method;
    final String metadataFlavor;
    final String path;
    final String body;

    RecordedMetadataRequest(String method, String metadataFlavor, String path, String body) {
      this.method = method;
      this.metadataFlavor = metadataFlavor;
      this.path = path;
      this.body = body;
    }
  }

  private void assertReportedEeVersion(String runtime, String expectedEeVersion) throws Exception {
    BlockingQueue<RecordedMetadataRequest> requests = new LinkedBlockingQueue<>();
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
          requests.add(
              new RecordedMetadataRequest(
                  exchange.getRequestMethod(),
                  exchange.getRequestHeaders().getFirst("Metadata-Flavor"),
                  exchange.getRequestURI().getPath(),
                  body));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    String metadataHost = getServerHostPort(server);

    try {
      createTempAppEngineWebXml(
          "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">\n"
              + "    <runtime>"
              + runtime
              + "</runtime>\n"
              + "</appengine-web-app>\n");

      AppEngineWebXmlInitialParse parser =
          new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());
      parser.setEnvProvider(
          key -> {
            if (key.equals("GAE_REPORT_CLONE_ATTRIBUTES")) {
              return "true";
            }
            if (key.equals("GCE_METADATA_HOST")) {
              return metadataHost;
            }
            return null;
          });
      parser.handleRuntimeProperties();

      // The request is done in a separate thread, so we need to wait a bit.
      RecordedMetadataRequest request = requests.poll(5, SECONDS);
      assertNotNull("Expected metadata request within 5 seconds", request);
      assertEquals("PUT", request.method);
      assertEquals("Google", request.metadataFlavor);
      assertEquals(
          "/computeMetadata/v1/instance/guest-attributes/goog-gae-java/ee-version", request.path);
      assertEquals(expectedEeVersion, request.body);
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void testGetMetadataGuestAttributesUrl() throws Exception {
    createTempAppEngineWebXml(
        """
        <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
            <runtime>java17</runtime>
        </appengine-web-app>
        """);
    AppEngineWebXmlInitialParse parser =
        new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());

    parser.setEnvProvider(key -> null);
    assertEquals(
        "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/goog-gae-java/",
        parser.getMetadataGuestAttributesUrl());

    parser.setEnvProvider(key -> key.equals("GCE_METADATA_HOST") ? "" : null);
    assertEquals(
        "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/goog-gae-java/",
        parser.getMetadataGuestAttributesUrl());

    parser.setEnvProvider(key -> key.equals("GCE_METADATA_HOST") ? "127.0.0.1:8080" : null);
    assertEquals(
        "http://127.0.0.1:8080/computeMetadata/v1/instance/guest-attributes/goog-gae-java/",
        parser.getMetadataGuestAttributesUrl());
  }

  @Test
  public void testReportCloneAttributeNon200LogsWarning() throws Exception {
    Logger logger = Logger.getLogger(AppEngineWebXmlInitialParse.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] unused = exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    server.start();
    String metadataHost = getServerHostPort(server);

    try {
      createTempAppEngineWebXml(
          """
          <appengine-web-app xmlns="http://appengine.google.com/ns/1.0">
              <runtime>java17</runtime>
          </appengine-web-app>
          """);
      AppEngineWebXmlInitialParse parser =
          new AppEngineWebXmlInitialParse(tempFile.toFile().getAbsolutePath());
      parser.setEnvProvider(key -> key.equals("GCE_METADATA_HOST") ? metadataHost : null);
      parser.reportCloneAttribute("ee-version", "8");

      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record
            .getMessage()
            .contains("Failed to report ee-version=8 to metadata server. Response code: 500")) {
          found = true;
          break;
        }
      }
      assertTrue("Expected warning log for non-200 response code", found);
    } finally {
      server.stop(0);
      logger.removeHandler(handler);
    }
  }

  @Test
  public void testReportCloneAttributeInvalidUrlLogsWarning() throws Exception {
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
      parser.setEnvProvider(key -> key.equals("GCE_METADATA_HOST") ? "not a valid uri" : null);
      parser.reportCloneAttribute("ee-version", "8");

      boolean found = false;
      for (LogRecord record : handler.records) {
        if (record
            .getMessage()
            .contains("Exception reporting ee-version=8 to metadata server:")) {
          found = true;
          break;
        }
      }
      assertTrue("Expected warning log for exception", found);
    } finally {
      logger.removeHandler(handler);
    }
  }
}
