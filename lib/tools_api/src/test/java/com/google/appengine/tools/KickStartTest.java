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

package com.google.appengine.tools;

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.JAVA_HOME;
import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.base.StandardSystemProperty.PATH_SEPARATOR;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.KickStart.AppEnvironment;
import com.google.appengine.tools.development.resource.ResourceExtractor;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test KickStart utilities. */
@RunWith(JUnit4.class)
public class KickStartTest {
  private static final String PACKAGE_PATH =
      KickStartTest.class.getPackage().getName().replace('.', '/');

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private String extractAppResourceToFilePath(String resourceName) {
    String resourcePath = String.format("%s/development/testdata/%s", PACKAGE_PATH, resourceName);
    return extractResourceToFilePath(resourcePath);
  }

  private String extractResourceToFilePath(String resourcePath) {
    File dstFile;
    try {
      String resourceName = Paths.get(resourcePath).getFileName().toString();
      dstFile = temporaryFolder.newFolder(resourceName);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ResourceExtractor.toFile(resourcePath, dstFile.getAbsolutePath());
    return dstFile.getAbsolutePath();
  }

  @Test
  public void testReadAppEnvironment() throws InterruptedException {
    AppEnvironment java8Config =
        KickStart.readAppEnvironment(extractAppResourceToFilePath("warjava8"));
    assertThat(java8Config.serviceName).isEqualTo("default");
    AppEnvironment java8EarConfig =
        KickStart.readAppEnvironment(extractAppResourceToFilePath("eartwomodulesoneisjava8"));
    assertThat(java8EarConfig.serviceName).isEqualTo("modulebis");
    AppEnvironment vmEarConfig =
        KickStart.readAppEnvironment(extractAppResourceToFilePath("eartwomodulesoneisvm"));
    assertThat(vmEarConfig.serviceName).isEqualTo("module1");
  }

  @Test
  public void testGoogleLegacy() {
    AppEnvironment java11Config =
        KickStart.readAppEnvironment(extractAppResourceToFilePath("wargooglelegacy"));
    assertThat(java11Config.serviceName).isEqualTo("default");
  }

  private static final ImmutableList<String> APP_ENGINE_WEB_XML_PREFIX =
      ImmutableList.of(
          "<?xml version='1.0' encoding='UTF-8'?>",
          "<appengine-web-app xmlns='http://appengine.google.com/ns/1.0'",
          "   xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'",
          "   xsi:schemaLocation='http://kenai.com/projects/nbappengine/downloads/download/"
              + "schema/appengine-web.xsd appengine-web.xsd'>",
          "  <application>fileencoding</application>",
          "  <version>1</version>",
          "  <threadsafe>true</threadsafe>");
  private static final ImmutableList<String> APP_ENGINE_WEB_XML_SUFFIX =
      ImmutableList.of("</appengine-web-app>");

  /** If you explicitly ask for UTF-8 encoding then you should get it. */
  @Test
  public void testUtf8Encoding() throws Exception {
    ImmutableList<String> utf8Properties =
        ImmutableList.of(
            "  <system-properties>",
            "    <property name='appengine.file.encoding' value='UTF-8' />",
            "  </system-properties>");
    checkEncoding(utf8Properties, "UTF-8");
  }

  /**
   * If you explicitly ask for ASCII encoding then you should get it, even with the java8 runtime.
   */
  @Test
  public void testAsciiEncoding() throws Exception {
    ImmutableList<String> asciiProperties =
        ImmutableList.of(
            "  <runtime>java8</runtime>",
            "  <system-properties>",
            "    <property name='appengine.file.encoding' value='US-ASCII' />",
            "  </system-properties>");
    checkEncoding(asciiProperties, "US-ASCII");
  }

  /**
   * If you don't ask for any encoding, but you do specify the java8 runtime, then the encoding
   * defaults to UTF-8. This mimics what happens in prod.
   */
  @Test
  public void testJava8RuntimeDefaultsToUt8Encoding() throws Exception {
    checkEncoding(ImmutableList.of("<runtime>java8</runtime>"), "UTF-8");
  }

  private void checkEncoding(ImmutableList<String> propertyLines, String expectedEncoding)
      throws IOException, InterruptedException {
    ImmutableList<String> allLines =
        ImmutableList.<String>builder()
            .addAll(APP_ENGINE_WEB_XML_PREFIX)
            .addAll(propertyLines)
            .addAll(APP_ENGINE_WEB_XML_SUFFIX)
            .build();
    File webInf = temporaryFolder.newFolder("WEB-INF");
    File appEngineWebXml = new File(webInf, "appengine-web.xml");
    try (PrintWriter appEngineWebXmlWriter = new PrintWriter(appEngineWebXml, "UTF-8")) {
      for (String line : allLines) {
        appEngineWebXmlWriter.println(line);
      }
    }
    List<String> outputLines = runWithKickStart(temporaryFolder.getRoot());
    // We only look at the last line of output. KickStart might output something before it,
    // for example the command it is executing.
    String actualEncoding = outputLines.isEmpty() ? null : Iterables.getLast(outputLines);
    String message = "Output from KickStart:\n" + Joiner.on("\n").join(outputLines);
    assertWithMessage(message).that(actualEncoding).isEqualTo(expectedEncoding);
  }

  private List<String> runWithKickStart(File webInfDir) throws IOException, InterruptedException {
    String javaBinary = JAVA_HOME.value() + "/bin/java";
    String classpath = getPrintDefaultCharsetClasspath();
    ImmutableList<String> args =
        ImmutableList.of(
            javaBinary,
            "-classpath",
            classpath,
            KickStart.class.getName(),
            "--test_mode",
            "com.google.appengine.tools.PrintDefaultCharset",
            webInfDir.getPath());
    if (!JAVA_SPECIFICATION_VERSION.value().equals("1.8")) {
      args =
          ImmutableList.of(
              javaBinary,
              "-classpath",
              classpath,
              "--add-opens",
              "java.base/java.net=ALL-UNNAMED",
              "--add-opens",
              "java.base/sun.net.www.protocol.http=ALL-UNNAMED",
              "--add-opens",
              "java.base/sun.net.www.protocol.https=ALL-UNNAMED",
              KickStart.class.getName(),
              "--test_mode",
              "com.google.appengine.tools.PrintDefaultCharset",
              webInfDir.getPath());
    }
    Process p =
        new ProcessBuilder(args)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start();
    List<String> lines;
    try (Reader processOutputReader = new InputStreamReader(p.getInputStream(), UTF_8)) {
      lines = CharStreams.readLines(processOutputReader);
    }
    p.waitFor();
    return lines;
  }

  private String getPrintDefaultCharsetClasspath() throws IOException {
    // compile the PrintDefaultCharset.class file into the temporary folder root
    compilePrintDefaultCharsetClassFileToTemporaryFolder();
    // the test classpath has what is needed to load KickStart.class
    String testClasspath = JAVA_CLASS_PATH.value();

    return String.format(
        "%s%s%s", testClasspath, PATH_SEPARATOR.value(), temporaryFolder.getRoot());
  }

  private void compilePrintDefaultCharsetClassFileToTemporaryFolder() throws IOException {
    String sourceFileName = "PrintDefaultCharset.java";
    String sourceFileResourcePath = String.format("%s/%s", PACKAGE_PATH, sourceFileName);
    // the class file must be run from a directory structure matching the package path
    File dstFolder = temporaryFolder.newFolder(PACKAGE_PATH);
    String sourceFilePath = Paths.get(dstFolder.getAbsolutePath(), sourceFileName).toString();
    ResourceExtractor.toFile(sourceFileResourcePath, sourceFilePath);
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    compiler.run(null, null, null, sourceFilePath);
    String classFileName = sourceFileName.replaceFirst(".java", ".class");
    String classFilePath = sourceFilePath.replaceFirst(sourceFileName, classFileName);
    assertThat(new File(classFilePath).exists()).isTrue();
  }
}
