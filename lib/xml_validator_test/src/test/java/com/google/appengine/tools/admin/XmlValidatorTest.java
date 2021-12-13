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

package com.google.appengine.tools.admin;

import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link XmlValidator}. */
@RunWith(JUnit4.class)
public class XmlValidatorTest {
  private static final Logger logger = Logger.getLogger(XmlValidatorTest.class.getName());

  private static final String CRON_XSD = "com/google/appengine/tools/development/cron.xsd";
  private static final String DISPATCH_XSD = "com/google/appengine/tools/development/dispatch.xsd";
  private static final String DATASTORE_INDEXES_XSD =
      "com/google/appengine/tools/development/datastore-indexes.xsd";

  private static final String DISPATCH_XML =
      Joiner.on("\n")
          .join(
              "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
              "<dispatch-entries>",
              "  <dispatch>",
              "    <!-- Default module serves the typical web resources and all static resources."
                  + " -->",
              "    <url>simple-sample.appspot.com/*</url>",
              "    <module>default</module>",
              "  </dispatch>",
              "  <dispatch>",
              "      <!-- Default module serves simple hostname request. -->",
              "      <url>*/favicon.ico</url>",
              "      <module>default</module>",
              "  </dispatch>",
              "  <dispatch>",
              "      <!-- Send all mobile traffic to the mobile frontend. -->",
              "      <url>*/mobile/*</url>",
              "      <module>mobile-frontend</module>",
              "  </dispatch>",
              "  <dispatch>",
              "      <!-- Send all work to the one static backend. -->",
              "      <url>*/work/*</url>",
              "      <module>static-backend</module>",
              "  </dispatch>",
              "</dispatch-entries>");
  private static final String DATASTORE_INDEXES_XML =
      Joiner.on("\n")
          .join(
              "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
              "<datastore-indexes",
              "  autoGenerate=\"true\">",
              "    <datastore-index kind=\"Employee\" ancestor=\"false\">",
              "        <property name=\"lastName\" direction=\"asc\" />",
              "        <property name=\"hireDate\" direction=\"desc\" />",
              "    </datastore-index>",
              "    <datastore-index kind=\"Project\" ancestor=\"false\">",
              "        <property name=\"dueDate\" direction=\"asc\" />",
              "    </datastore-index>",
              "</datastore-indexes>");

  @Rule public TemporaryFolder testDir = new TemporaryFolder();
  private File xmlValidatorJar;

  @Before
  public void setUp() throws Exception {
    xmlValidatorJar = getLibXmlValidatorJarFile();
    assertWithMessage(xmlValidatorJar.toString() + " error, cannot read the jar.")
        .that(xmlValidatorJar.canRead())
        .isTrue();
  }

  @Test
  public void oneBad() throws Exception {
    TestXml testXml =
        new TestXml("cron.xml", "bogus cron file contents", xsdResourceToFilePath(CRON_XSD));
    validate(/* shouldPass= */ false, testXml);
  }

  @Test
  public void oneAlsoBad() throws Exception {
    String bogusDatastoreIndexesXml = DATASTORE_INDEXES_XML.replace("kind", "wibble");
    TestXml testXml =
        new TestXml(
            "datastore-indexes.xml",
            bogusDatastoreIndexesXml,
            xsdResourceToFilePath(DATASTORE_INDEXES_XSD));
    validate(/* shouldPass= */ false, testXml);
  }

  @Test
  public void twoGood() throws Exception {
    TestXml testDispatchXml =
        new TestXml("dispatch.xml", DISPATCH_XML, xsdResourceToFilePath(DISPATCH_XSD));
    TestXml testDatastoreIndexesXml =
        new TestXml(
            "datastoreIndexes.xml",
            DATASTORE_INDEXES_XML,
            xsdResourceToFilePath(DATASTORE_INDEXES_XSD));
    validate(/* shouldPass= */ true, testDispatchXml, testDatastoreIndexesXml);
  }

  private static class TestXml {
    private final String xmlBaseName;
    private final String xmlContents;
    private final String xsdFile;

    TestXml(String xmlFile, String xmlContents, String xsd) {
      this.xmlBaseName = xmlFile;
      this.xmlContents = xmlContents;
      this.xsdFile = xsd;
    }
  }

  private void validate(boolean shouldPass, TestXml... testXmls)
      throws IOException, InterruptedException {
    logger.info("Running: one ");
    String javaBin = JAVA_HOME.value() + "/bin/java";
    List<String> args = new ArrayList<>();
    args.add(javaBin);
    args.add("-verbose:class");
    args.add("-classpath");
    args.add(xmlValidatorJar.getAbsolutePath());
    args.add(XmlValidator.class.getName());
    for (TestXml testXml : testXmls) {
      File xmlFile = testDir.newFile(testXml.xmlBaseName);
      Files.asCharSink(xmlFile, UTF_8).write(testXml.xmlContents);
      args.add(xmlFile.toString());
      args.add(testXml.xsdFile);
    }
    System.out.println(args);
    Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
    String output;
    try (InputStream processOutput = process.getInputStream()) {
      byte[] outputBytes = ByteStreams.toByteArray(processOutput);
      output = new String(outputBytes, UTF_8);
    }
    int status = process.waitFor();
    if (shouldPass) {
      assertWithMessage("Did not get expected success status. Output: %s", output)
          .that(status)
          .isEqualTo(0);
    } else {
      assertWithMessage("Should have failed but did not. Output: %s", output)
          .that(status)
          .isEqualTo(1);
    }
  }

  private String xsdResourceToFilePath(String xsdResourcePath) {
    File xsdFile = extractResourceToTmpFile(xsdResourcePath);
    return xsdFile.getAbsolutePath();
  }

  private File extractResourceToTmpFile(String resourcePath) {
    File tmpFile;
    try (InputStream inputStream = Resources.getResource(resourcePath).openStream()) {
      tmpFile = testDir.newFile(Paths.get(resourcePath).getFileName().toString());
      java.nio.file.Files.copy(inputStream, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return tmpFile;
  }

  public static File getLibXmlValidatorJarFile() {
    Path xmlValidatorJarPath = getLibXmlValidatorJarPath();
    return xmlValidatorJarPath.toFile();
  }

  public static Path getLibXmlValidatorJarPath() {
    URL xmlValidatorJarUrl = XmlValidator.class.getProtectionDomain().getCodeSource().getLocation();
    try {
      Path xmlValidatorJarPath = Paths.get(xmlValidatorJarUrl.toURI());
      // ensure that the XmlValidator class remains in libxmlvalidator.jar
      String pattern = ".*libxmlvalidator.*\\.jar";
      if (!xmlValidatorJarPath.getFileName().toString().matches(pattern)) {
        String msg =
            String.format(
                "Expected %s class to be located in unexpected jar '%s'.",
                XmlValidator.class.getSimpleName(),
                xmlValidatorJarPath.getFileName().toString());
        throw new RuntimeException(msg);
      }
      return xmlValidatorJarPath;
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
