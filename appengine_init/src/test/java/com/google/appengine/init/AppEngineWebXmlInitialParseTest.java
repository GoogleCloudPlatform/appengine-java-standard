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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
