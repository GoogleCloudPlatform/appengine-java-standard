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


import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.google.appengine.tools.info.AppengineSdk;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.CronXml;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.apphosting.utils.config.RetryParametersXml;
import com.google.apphosting.utils.config.StagingOptions;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Tests for the Application functionality
 *
 * @author fabbott@google.com (Freeland Abbott)
 */
@RunWith(Parameterized.class)
public class ApplicationTest {
  private static final String SDK_ROOT_PROPERTY = "appengine.sdk.root";

  private static final String TEST_FILES = getWarPath("sampleapp");
  private static final String TEST_FILES_RUNTIME_DEFINED = getWarPath("sampleapp-runtime");
  private static final String TEST_FILES_AUTOMATIC_MODULE =
      getWarPath("sampleapp-automatic-module");
  private static final String TEST_FILES_MANUAL_MODULE = getWarPath("sampleapp-manual-module");
  private static final String TEST_FILES_BASIC_MODULE = getWarPath("sampleapp-basic-module");
  private static final String TEST_FILES_BACKENDS = getWarPath("sampleapp-backends");
  private static final String TEST_JSPX_FILES = getWarPath("sample-jspx");
  private static final String TEST_TAGS_FILES = getWarPath("sample-jsptaglibrary");
  private static final String TEST_ERROR_IN_TAGS_FILES = getWarPath("sample-error-in-tag-file");
  private static final String NOJSP_TEST_FILES = getWarPath("sample-nojsps");
  private static final String BAD_WEBXML_TEST_FILES = getWarPath("sample-badweb");
  private static final String BAD_RUNTIME_CHANNEL = getWarPath("sample-badruntimechannel");
  private static final String TEST_JAVA11 = getWarPath("sample-java11");
  private static final String TEST_JAVA17 = getWarPath("sample-java17");
  private static final String BAD_ENTRYPOINT = getWarPath("sample-badentrypoint");
  private static final String BAD_APPENGINEWEBXML_TEST_FILES = getWarPath("sample-badaeweb");
  private static final String BAD_INDEXESXML_TEST_FILES = getWarPath("sample-badindexes");
  private static final String MISSING_APP_ID_TEST_FILES = getWarPath("sample-missingappid");
  private static final String INCLUDE_HTTP_HEADERS_TEST_FILES = getWarPath("http-headers");
  private static final String XMLORDERING_TEST_FILES = getWarPath("xmlorder");
  private static final String BAD_CRONXML_TEST_FILES = getWarPath("badcron");
  private static final String JAVA8_NO_WEBXML = getWarPath("java8-no-webxml");
  private static final String CRON_RETRY_PARAMETERS_TEST_FILES =
      getWarPath("cron-good-retry-parameters");
  private static final String CRON_NEGATIVE_RETRY_LIMIT_TEST_FILES =
      getWarPath("cron-negative-retry-limit");
  private static final String CRON_NEGATIVE_MAX_BACKOFF_TEST_FILES =
      getWarPath("cron-negative-max-backoff");
  private static final String CRON_TWO_MAX_DOUBLINGS_TEST_FILES =
      getWarPath("cron-two-max-doublings");
  private static final String CRON_BAD_AGE_LIMIT_TEST_FILES = getWarPath("cron-bad-job-age-limit");

  private static final String LEGACY_AUTO_ID_POLICY_TEST_FILES =
      getWarPath("sample-legacy-auto-ids");
  private static final String UNSPECIFIED_AUTO_ID_POLICY_TEST_FILES =
      getWarPath("sample-unspecified-auto-ids");
  private static final String DEFAULT_AUTO_ID_POLICY_TEST_FILES =
      getWarPath("sample-default-auto-ids");
  private static final String JAVA8_JAR_TEST_FILES = getWarPath("java8-jar");
  private static final String CLASSES_TEST_FILES = getWarPath("sample-with-classes");

  private static final String SDK_ROOT =getSDKRoot();

  private static final String SERVLET3_STANDARD_APP_ROOT =getWarPath("bundle_standard");
  private static final String SERVLET3_STANDARD_APP_NO_JSP_ROOT =
      getWarPath("bundle_standard_with_no_jsp");
  private static final String SERVLET3_STANDARD_WEBLISTENER_MEMCACHE =
      getWarPath("bundle_standard_with_weblistener_memcache");
  private static final String SERVLET3_STANDARD_APP_WITH_CONTAINER_INIT =
      getWarPath("bundle_standard_with_container_initializer");
  private static final String SERVLET3_APP_ID = "servlet3-demo";

  private static final String STAGE_TEST_APP = getWarPath("stage-sampleapp");
  private static final String STAGE_WITH_APPID_AND_VERSION_TEST_APP =
      getWarPath("stage-with-appid-and-version");
  private static final String STAGE_WITH_STAGING_OPTIONS = getWarPath("stage-with-staging-options");

  private static final int RANDOM_HTML_SIZE = 704;
  private static final String APPID = "sampleapp";
  private static final String MODULE_ID = "stan";
  private static final String APPVER = "1";
  private String oldSdkRoot;
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final String APPENGINE_WEB_XML_CONTENT =
      "<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">"
          + "<application>sampleapp</application><version>1</version><static-files/>"
          + "<threadsafe>true</threadsafe><vm>true</vm><resource-files/></appengine-web-app>";

  @Parameterized.Parameters
  public static List<Object[]> version() {
    return Arrays.asList(new Object[][] {{"EE6"}, {"EE8"}, {"EE10"}});
  }

  public ApplicationTest(String version) {
    switch (version) {
      case "EE6":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE10", "false");
        break;
      case "EE8":
        System.setProperty("appengine.use.EE8", "true");
        System.setProperty("appengine.use.EE10", "false");
        break;
      case "EE10":
        System.setProperty("appengine.use.EE8", "false");
        System.setProperty("appengine.use.EE10", "true");
        break;
      default:
        // fall through
    }
    System.setProperty("appengine.sdk.root", "../../sdk_assembly/target/appengine-java-standard");
    AppengineSdk.resetSdk();    
  }

  private static String getWarPath(String directoryName) {
          File currentDirectory = new File("").getAbsoluteFile();

    String appRoot =
        new File(
                currentDirectory,
                "../testlocalapps/"
                    + directoryName
                    + "/target/"
                    + directoryName
                    + "-2.0.23-SNAPSHOT")
            .getAbsolutePath();

//    assertThat(appRoot.isDirectory()).isTrue();
return appRoot;


  }
   private static String getSDKRoot()  {
          File currentDirectory = new File("").getAbsoluteFile();
    String sdkRoot= null;
      try {
      sdkRoot =
          new File(currentDirectory, "../../sdk_assembly/target/appengine-java-sdk")
              .getCanonicalPath();
      } catch (IOException ex) {
          Logger.getLogger(ApplicationTest.class.getName()).log(Level.SEVERE, null, ex);
      }
return sdkRoot;


  }
  /** Set the appengine.sdk.root system property to make SdkInfo happy. */
  @Before
  public void setUp() {
    oldSdkRoot = System.setProperty(SDK_ROOT_PROPERTY, SDK_ROOT);
  }

  @After
  public void tearDown() {
    if (oldSdkRoot != null) {
      System.setProperty(SDK_ROOT_PROPERTY, oldSdkRoot);
    } else {
      System.clearProperty(SDK_ROOT_PROPERTY);
    }
  }

  private static void checkIfEntryIsInJarFile(File jarFile, String entryName) {
    assertThat(jarFile.exists()).isTrue();
    try {
      JarFile jar = new JarFile(jarFile);
      try {
        Enumeration<JarEntry> jarEntries = jar.entries();
        while (jarEntries.hasMoreElements()) {
          JarEntry jarEntry = jarEntries.nextElement();
          if (jarEntry.getName().equals(entryName)) {
            return;
          }
        }
      } finally {
        jar.close();
      }
    } catch (IOException e) {
      throw new AssertionError("Cannot read jar file: " + jarFile.getAbsolutePath(), e);
    }
    fail("Jar does not contain entry: " + entryName);
  }

  @Test
  public void testModuleOverride() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES_AUTOMATIC_MODULE);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getModule()).isEqualTo(MODULE_ID);
    assertThat(testApp.getVersion()).isEqualTo(APPVER);

    testApp = Application.readApplication(TEST_FILES_AUTOMATIC_MODULE, null, "newModule", null);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getModule()).isEqualTo("newModule");
    assertThat(testApp.getVersion()).isEqualTo(APPVER);

    testApp =
        Application.readApplication(
            TEST_FILES_AUTOMATIC_MODULE, "newId", "newModule", "newVersion");
    assertThat(testApp.getAppId()).isEqualTo("newId");
    assertThat(testApp.getModule()).isEqualTo("newModule");
    assertThat(testApp.getVersion()).isEqualTo("newVersion");
  }

  @Test
  public void testAppIdAppVerOverride() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getModule()).isNull();
    assertThat(testApp.getVersion()).isEqualTo(APPVER);

    testApp = Application.readApplication(TEST_FILES, null, null, null);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getModule()).isNull();
    assertThat(testApp.getVersion()).isEqualTo(APPVER);

    testApp = Application.readApplication(TEST_FILES, null, null, "newVersion");
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getModule()).isNull();
    assertThat(testApp.getVersion()).isEqualTo("newVersion");

    testApp = Application.readApplication(TEST_FILES, "newId", null, null);
    assertThat(testApp.getAppId()).isEqualTo("newId");
    assertThat(testApp.getModule()).isNull();
    assertThat(testApp.getVersion()).isEqualTo(APPVER);

    testApp = Application.readApplication(TEST_FILES, null, "newModule", null);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getModule()).isEqualTo("newModule");
    assertThat(testApp.getVersion()).isEqualTo(APPVER);

    testApp = Application.readApplication(TEST_FILES, "newId", "newModule", "newVersion");
    assertThat(testApp.getAppId()).isEqualTo("newId");
    assertThat(testApp.getModule()).isEqualTo("newModule");
    assertThat(testApp.getVersion()).isEqualTo("newVersion");
  }

  @Test
  public void testMissingAppIdNoOverride() throws Exception {
    Application testApp =
        Application.readApplication(MISSING_APP_ID_TEST_FILES, null, null, "ignoredVersion");
    assertThrows(AppEngineConfigException.class, () -> testApp.validate());
  }

  @Test
  public void testMissingAppIdAndVersionWithOverride() throws Exception {
    Application testApp =
        Application.readApplication(MISSING_APP_ID_TEST_FILES, "newId", null, "newVersion");
    assertThat(testApp.getAppId()).isEqualTo("newId");
    assertThat(testApp.getVersion()).isEqualTo("newVersion");
  }

  @Test
  public void testLegacyAutoIdPolicy() throws IOException {
    Application testApp = Application.readApplication(LEGACY_AUTO_ID_POLICY_TEST_FILES);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getAppEngineWebXml().getAutoIdPolicy()).isEqualTo("legacy");
  }

  @Test
  public void testDefaultAutoIdPolicy() throws IOException {
    Application testApp = Application.readApplication(DEFAULT_AUTO_ID_POLICY_TEST_FILES);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getAppEngineWebXml().getAutoIdPolicy()).isEqualTo("default");
  }

  @Test
  public void testUnspecifiedAutoIdPolicy() throws IOException {
    Application testApp = Application.readApplication(UNSPECIFIED_AUTO_ID_POLICY_TEST_FILES);
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    assertThat(testApp.getAppEngineWebXml().getAutoIdPolicy()).isNull();
  }

  @Test
  public void testReadApplicationForStaging() throws IOException {
    Application testApp = Application.readApplication(STAGE_TEST_APP, null, null, null);
    assertThat(testApp.getAppId()).isNull();
    assertThat(testApp.getVersion()).isNull();

    // should not throw exception
    testApp.validateForStaging();
  }

  @Test
  public void testReadApplicationForStagingWithAppIdAndVersionFromCommandLine() throws IOException {
    Application testApp =
        Application.readApplication(STAGE_TEST_APP, "a-override", null, "v-override");

    // should not throw exception
    testApp.validateForStaging();
  }

  //TODO(ludo)  @Test
  public void testReadApplicationForStagingWithAppIdAndVersionFromFile() throws IOException {
    Application testApp =
        Application.readApplication(STAGE_WITH_APPID_AND_VERSION_TEST_APP, null, null, null);

    // should not throw exception
    testApp.validateForStaging();
  }

  @Test
  public void testStagingWithFiles() throws Exception {
    Application testApp = Application.readApplication(TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setSplitJarFiles(Optional.of(true)).build());

    testApp.createStagingDirectory(opts);
    testStagedFiles(testApp);
    File stage = testApp.getStagingDir();

    File appYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "application: 'sampleapp'");
    assertFileContains(appYaml, "\nversion: '1'");
  }

  @Test
  public void testStagingWithRelativeFiles() throws Exception {
    // If this assumption is false, presumably we're on Windows somehow, and our fabricated
    // relative path probably won't work because of those stupid C: drive letter things.
    assumeTrue(File.separatorChar == '/');
    String currentDirAbsolute = Paths.get("").toAbsolutePath().toString();
    assertThat(currentDirAbsolute).startsWith("/");
    String currentDirToRoot = currentDirAbsolute.substring(1).replaceAll("([^/]+)", "..");
    assertThat(TEST_FILES).startsWith("/");
    String testFilesRelative = currentDirToRoot + TEST_FILES;
    assertThat(new File(testFilesRelative).isDirectory()).isTrue();
    Application testApp = Application.readApplication(testFilesRelative);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setSplitJarFiles(Optional.of(true)).build());

    testApp.createStagingDirectory(opts);
    testStagedFiles(testApp);
    File stage = testApp.getStagingDir();
    File appYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "application: 'sampleapp'");
    assertFileContains(appYaml, "\nversion: '1'");
  }

  @Test
  public void testZipClassesDirectoryWithoutClasses() throws Exception {
    Application testApp = Application.readApplication(TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(
        StagingOptions.builder()
            .setSplitJarFiles(Optional.of(true))
            .setJarClasses(Optional.of(true))
            .build());

    testApp.createStagingDirectory(opts);
    testStagedFiles(testApp);
    File stage = testApp.getStagingDir();
    File appYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "application: 'sampleapp'");
  }

  /**
   * The sane staging defaults split jars and jars classes, hence re-using logic from the above test
   * (testZipClassesDirectoryWithoutClasses).
   */
  @Test
  public void testSaneStagingDefaults() throws Exception {
    Application testApp = Application.readApplication(TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setDefaultStagingOptions(StagingOptions.SANE_DEFAULTS);
 
    testApp.createStagingDirectory(opts);
    testStagedFiles(testApp);
    File stage = testApp.getStagingDir();
    File appYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "application: 'sampleapp'");
  }

  @Test
  public void testStagingForGcloudWithFilesAndConfigErasure() throws Exception {
    Application testApp = Application.readApplication(TEST_FILES, null, null, null);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    testApp.validateForStaging();

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setSplitJarFiles(Optional.of(true)).build());

    File stagingDir = testApp.createStagingDirectory(opts, temporaryFolder.newFolder());
    testStagedFiles(testApp);
    // user staging copies yaml files from appengine-generated into the root
    File copiedAppYaml = new File(stagingDir, "app.yaml");
    File originalAppYaml = new File(stagingDir, "WEB-INF/appengine-generated/app.yaml");
    assertThat(copiedAppYaml.canRead()).isTrue();

    // the copied app.yaml is the original with a precautionary new line and an
    // extra 'skip_files' line.
    List<String> expectedCopiedAppYaml = Files.readLines(originalAppYaml, UTF_8);
    expectedCopiedAppYaml.add("");
    expectedCopiedAppYaml.add("skip_files: app.yaml");
    assertThat(Files.readLines(copiedAppYaml, UTF_8)).isEqualTo(expectedCopiedAppYaml);

    String yamlString = Files.asCharSource(copiedAppYaml, UTF_8).read();
    assertThat(yamlString.startsWith("application:")).isFalse();
    assertThat(yamlString).doesNotContain("\napplication:");
    assertThat(yamlString.startsWith("version:")).isFalse();
    assertThat(yamlString).doesNotContain("\nversion:");
    assertFileContains(copiedAppYaml, "\nskip_files: app.yaml");
  }

  private static void testStagedFiles(Application testApp) throws Exception {
    File stage = testApp.getStagingDir();
    File html = new File(stage, "__static__/random.html");
    assertWithMessage("Can read " + html).that(html.canRead()).isTrue();
    assertThat(html.length()).isEqualTo(RANDOM_HTML_SIZE);
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    File jspJar = new File(stage, "WEB-INF/lib/_ah_compiled_jsps-0000.jar");
    assertThat(jspJar.exists()).isTrue();
    // Test that the classes in the generated jar are for Java8.
    assertThat(getJavaJarVersion(jspJar)).isEqualTo(8);
    checkIfEntryIsInJarFile(jspJar, "org/apache/jsp/nested/testing_jsp.class");
    assertThat(new File(stage, "nested/dukebanner.html").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/lib").isDirectory()).isTrue();
    File appYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertThat(Files.asCharSource(appYaml, UTF_8).read()).contains("api_version: 'user_defined'");
    int count = 0;
    for (File file : AppengineSdk.getSdk().getUserJspLibFiles()) {
      if (file.getName().contains("apache-jsp")) {
         count++;
      }
    }
    // Cannot have both the -nolog.jar and the regular jar.
    assertThat(count).isEqualTo(2); // org.eclipse and org.mortbay
  }

  //TODO(ludo) @Test
  public void testStageForGcloudOnlyCopyAppYamlToRoot() throws IOException {
    Application testApp =
        Application.readApplication(getWarPath("stage-with-all-xmls"), null, null, null);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    File stagingDir = testApp.createStagingDirectory(opts, temporaryFolder.newFolder());
    File generationDir = GenerationDirectory.getGenerationDirectory(stagingDir);

    assertThat(new File(generationDir, "app.yaml").exists()).isTrue();
    assertThat(new File(stagingDir, "app.yaml").exists()).isTrue();

    assertThat(new File(generationDir, "cron.yaml").exists()).isTrue();
    assertThat(new File(stagingDir, "cron.yaml").exists()).isFalse();

    assertThat(new File(generationDir, "dispatch.yaml").exists()).isTrue();
    assertThat(new File(stagingDir, "dispatch.yaml").exists()).isFalse();

    assertThat(new File(generationDir, "dos.yaml").exists()).isTrue();
    assertThat(new File(stagingDir, "dos.yaml").exists()).isFalse();

    assertThat(new File(generationDir, "index.yaml").exists()).isTrue();
    assertThat(new File(stagingDir, "index.yaml").exists()).isFalse();

    assertThat(new File(generationDir, "queue.yaml").exists()).isTrue();
    assertThat(new File(stagingDir, "queue.yaml").exists()).isFalse();
  }

  //TODO(ludo) @Test
  public void testDoNotStageDispatchForUpdate() throws IOException {
    Application testApp =
        Application.readApplication(getWarPath("sample-dispatch"), null, null, null);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    File stagingDir = testApp.createStagingDirectory(opts);

    File dispatchYaml = new File(stagingDir, "dispatch.yaml");
    assertThat(dispatchYaml.exists()).isFalse();
  }

  @Test
  public void testAppEngineApiJarIncluded() throws Exception {
    File tmpDir = Files.createTempDir();
    try {
      doTestAppEngineApiJarIncluded(tmpDir, "impl", "lib/impl/appengine-api.jar");
    } finally {
      deleteRecursively(tmpDir.toPath());
    }
  }

  private static void doTestAppEngineApiJarIncluded(File tmpDir, String testName, String apiJarPath)
      throws Exception {
    File sdkRoot = new File(SDK_ROOT);
    File apiJar = new File(sdkRoot, apiJarPath);
    assertWithMessage(apiJar.toString()).that(apiJar.exists()).isTrue();
    //TODO(ludo)  File remoteApiJar = new File(sdkRoot, "lib/appengine-remote-api.jar");
    //TODO(ludo) assertWithMessage(remoteApiJar.toString()).that(remoteApiJar.exists()).isTrue();
    File testDir = new File(tmpDir, testName);
    File webInf = new File(testDir, "WEB-INF");
    File webInfLib = new File(webInf, "lib");
    boolean madeWebInfLib = webInfLib.mkdirs();
    assertThat(madeWebInfLib).isTrue();
    Files.copy(apiJar, new File(webInfLib, "appengine-api.jar"));
    //TODO(ludo) Files.copy(remoteApiJar, new File(webInfLib, "appengine-remote-api.jar"));
    File testAppRoot = new File(TEST_FILES);
    Files.copy(new File(testAppRoot, "WEB-INF/web.xml"), new File(webInf, "web.xml"));
    Files.copy(
        new File(testAppRoot, "WEB-INF/appengine-web.xml"), new File(webInf, "appengine-web.xml"));

    Application application = Application.readApplication(testDir.getAbsolutePath());
    application.setDetailsWriter(new PrintWriter(new OutputStreamWriter(System.out, UTF_8)));

    assertThat(application.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    application.createStagingDirectory(opts);
    File stage = application.getStagingDir();
    File appYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "api_version: 'user_defined'");
    File stagedApiJar = new File(stage, "WEB-INF/lib/appengine-api.jar");
    assertWithMessage(stagedApiJar.toString()).that(stagedApiJar.exists()).isTrue();
    byte[] originalApiJarBytes = Files.toByteArray(apiJar);
    byte[] stagedApiJarBytes = Files.toByteArray(stagedApiJar);
    assertThat(originalApiJarBytes).isEqualTo(stagedApiJarBytes);
  }

  @Test
  public void testStagingJava8() throws Exception {
    Application testApp = Application.readApplication(TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-generated/app.yaml").canRead()).isTrue();
    assertFileContains(new File(stage, "WEB-INF/appengine-generated/app.yaml"), "runtime: java8\n");
  }

  @Test
  public void testStagingJava11() throws Exception {
    Application testApp = Application.readApplication(TEST_JAVA11);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-generated/app.yaml").canRead()).isTrue();
    File generatedAppYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(generatedAppYaml, "runtime: java11\n");
    assertFileContains(generatedAppYaml, "runtime_channel: canary\n");
    assertFileContains(generatedAppYaml, "entrypoint: 'java -jar foo.jar'\n");
  }

  @Test
  public void testStagingJava17() throws Exception {
    Application testApp = Application.readApplication(TEST_JAVA17);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-generated/app.yaml").canRead()).isTrue();
    File generatedAppYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(generatedAppYaml, "runtime: java17\n");
    assertFileContains(generatedAppYaml, "runtime_channel: canary\n");
    assertFileContains(generatedAppYaml, "entrypoint: 'java -jar foo.jar'\n");
  }

  @Test
  public void testJspCompilerJava8() throws Exception {
    Application testApp = Application.readApplication(SERVLET3_STANDARD_APP_ROOT);
    assertThat(testApp.getJSPCClassName())
        .contains("com.google.appengine.tools.development.jetty");
     assertThat(testApp.getJSPCClassName())
        .contains("LocalJspC");
  }

  @Test
  public void testStagingJava8RuntimeJava8JarFile() throws Exception {
    Application testApp = Application.readApplication(JAVA8_JAR_TEST_FILES);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setAllowAnyRuntime(true);
    opts.setRuntime("java8");
    File stage = testApp.createStagingDirectory(opts);
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-generated/app.yaml").canRead()).isTrue();
    assertFileContains(new File(stage, "WEB-INF/appengine-generated/app.yaml"), "runtime: java8\n");
  }

  @Test
  public void testStagingManuallySetUnsupportedRuntime() throws IOException {
    try {
      Application testApp = Application.readApplication(TEST_FILES);
      testApp.setDetailsWriter(
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

      assertThat(testApp.getAppId()).isEqualTo(APPID);

      ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
      opts.setRuntime("foo");

      testApp.createStagingDirectory(opts);
      fail("Did not get expected AppEngineConfigException");
    } catch (AppEngineConfigException expected) {
      // Expect an exception flagging the runtime id as invalid.
      assertThat(expected).hasMessageThat().contains("Invalid runtime id: foo");
    }
  }

  @Test
  public void testStagingManuallySetUnsupportedRuntimeAllowAnyRuntime() throws Exception {
    Application testApp = Application.readApplication(TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setRuntime("foo");
    opts.setAllowAnyRuntime(true);

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-generated/app.yaml").canRead()).isTrue();
    assertFileContains(new File(stage, "WEB-INF/appengine-generated/app.yaml"), "runtime: foo\n");
  }

  private static void assertFileContains(File file, String expectedSubstring) throws Exception {
    String contentsAsString = Files.asCharSource(file, UTF_8).read();
    assertThat(contentsAsString).contains(expectedSubstring);
  }

  @Test
  public void testWithJspx() throws IOException {
    Application testApp = Application.readApplication(TEST_JSPX_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setSplitJarFiles(Optional.of(true)).build());

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    File html = new File(stage, "__static__/random.html");
    assertThat(html.canRead()).isTrue();
    assertThat(html.length()).isEqualTo(RANDOM_HTML_SIZE);
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    checkIfEntryIsInJarFile(
        new File(stage, "WEB-INF/lib/_ah_compiled_jsps-0000.jar"),
        "org/apache/jsp/nested/testing_jspx.class");
    assertThat(new File(stage, "nested/dukebanner.html").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/lib").isDirectory()).isTrue();
  }

 /* @Test
  public void testWithBigJarWithTlds() throws Exception {
    Application testApp =
        Application.readApplication(
            TestUtil.getSrcDir()
                + "/google3/javatests/com/google/appengine/tools/admin/deploy_fileset_big_jar_tld");
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setSplitJarFiles(Optional.of(true)).build());

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/lib").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/lib/bigjar_with_tlds-0000.jar").canRead()).isTrue();
    //    checkIfEntryIsInJarFile(new File(stage, "WEB-INF/lib/bigjar_with_tlds-0000.jar"),
    //       "META-INF/fn.tl");

    File quickstartXml = new File(stage, "WEB-INF/quickstart-web.xml");

    assertThat(quickstartXml.canRead()).isTrue();

    Document quickstartDoc =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(quickstartXml);
    // We want to verify that the list of tlds defined by:
    // <context-param>
    //   <param-value><![CDATA[
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/fmt-1_0-rt.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/fmt.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/c.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/permittedTaglibs.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/x-1_0.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/fn.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/sql-1_0.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/fmt-1_0.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/sql-1_0-rt.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/x.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/scriptfree.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/c-1_0.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/sql.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/c-1_0-rt.tld",
    //    "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-0163.jar!/META-INF/x-1_0-rt.tld"
    //       ...
    // We are not sure about the number of split jars, we just need to check if an entry
    // contains "jar:${WAR.uri}/WEB-INF/lib/bigjar_with_tlds-" and ".jar!/META-INF/fmt.tld"

    NodeList nodeList = quickstartDoc.getElementsByTagName("context-param");
    boolean match = false;
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node contextParam = nodeList.item(i).getFirstChild();
      do {
        String nodeName = contextParam.getNodeName();
        if (nodeName.equals("param-value")) {
          String content = contextParam.getFirstChild().getTextContent();
          if (content.contains("bigjar_with_tlds")) {

            match |= CONTENT_PATTERN.matcher(content).find();
          }
        }
      } while ((contextParam = contextParam.getNextSibling()) != null);
    }
    assertThat(match).isTrue();
  }
  */

  private static final Pattern CONTENT_PATTERN =
      Pattern.compile(
          "\"jar:\\$\\{WAR\\.uri}/WEB-INF/lib/bigjar_with_tlds"
              + "-\\d.*\\.jar!/META-INF/fmt.tld\",?");

  @Test
  public void testJspWithTag() throws IOException {
    Application testApp = Application.readApplication(TEST_TAGS_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    checkIfEntryIsInJarFile(
        new File(stage, "WEB-INF/lib/_ah_compiled_jsps-0000.jar"),
        "org/apache/jsp/nested/message_jsp.class");
    checkIfEntryIsInJarFile(
        new File(stage, "WEB-INF/lib/_ah_compiled_jsps-0000.jar"),
        "org/apache/jsp/tag/web/ui/page_tag.class");
    assertThat(new File(stage, "nested/message.jsp").canRead()).isTrue();
  }

  @Test
  public void testJspWithTagOnJava8gStandardRuntime() throws Exception {

    doTestJspWithRuntime("java8g");
  }

  @Test
  public void testJspWithTagOnJava8StandardRuntime() throws Exception {

    doTestJspWithRuntime("java8");
  }

  private static void doTestJspWithRuntime(String runtime) throws Exception {
    Application testApp = Application.readApplication(TEST_TAGS_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    opts.setRuntime(runtime);
    opts.setAllowAnyRuntime(true);
    // do not jar the generated classes for JSPs.
    opts.setStagingOptions(StagingOptions.builder().setJarJsps(Optional.of(false)).build());

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    File appYaml = new File(stage, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "runtime: " + runtime);

    assertThat(new File(stage, "nested/message.jsp").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    File class1 = new File(stage, "WEB-INF/classes/org/apache/jsp/nested/message_jsp.class");
    assertThat(class1.exists()).isTrue();

    File class2 = new File(stage, "WEB-INF/classes/org/apache/jsp/tag/web/ui/page_tag.class");
    assertThat(class2.exists()).isTrue();
    File genCodeDir = testApp.getJspJavaFilesGeneratedTempDirectory();
    File servlet2 = new File(genCodeDir, "org/apache/jsp/tag/web/ui/page_tag.java");
    assertThat(servlet2.exists()).isTrue();
    assertThat(Files.asCharSource(servlet2, UTF_8).read())
        .contains("* Version: JspC/ApacheTomcat");
  }

  @Test
  public void testDeleteJSPs() throws IOException {
    Application testApp = Application.readApplication(TEST_TAGS_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setDeleteJsps(Optional.of(true)).build());

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    checkIfEntryIsInJarFile(
        new File(stage, "WEB-INF/lib/_ah_compiled_jsps-0000.jar"),
        "org/apache/jsp/nested/message_jsp.class");
    checkIfEntryIsInJarFile(
        new File(stage, "WEB-INF/lib/_ah_compiled_jsps-0000.jar"),
        "org/apache/jsp/tag/web/ui/page_tag.class");
    assertThat(new File(stage, "nested/message.jsp").exists()).isFalse();
  }

  @Test
  public void testDoNotJarJSPs() throws IOException {
    Application testApp = Application.readApplication(TEST_TAGS_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setJarJsps(Optional.of(false)).build());

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/classes/org/apache/jsp/nested/message_jsp.class").exists())
        .isTrue();
    assertThat(new File(stage, "WEB-INF/lib/_ah_compiled_jsps-0000.jar").exists()).isFalse();
    assertThat(new File(stage, "nested/message.jsp").exists()).isTrue();
  }

  @Test
  public void testErrorInTagFile() throws IOException {
    Application testApp = Application.readApplication(TEST_ERROR_IN_TAGS_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    assertThat(testApp.getAppId()).isEqualTo(APPID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    JspCompilationException exception =
        assertThrows(JspCompilationException.class, () -> testApp.createStagingDirectory(opts));
    assertThat(exception).hasMessageThat().isEqualTo("Failed to compile jsp files.");
  }

  @Test
  public void testZipClassesWithoutJSPs() throws IOException {
    Application testApp = Application.readApplication(CLASSES_TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    assertThat(testApp.getAppId()).isEqualTo(APPID);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    opts.setStagingOptions(StagingOptions.builder().setJarClasses(Optional.of(true)).build());

    testApp.createStagingDirectory(opts);
    File stage = testApp.getStagingDir();
    assertThat(new File(stage, "WEB-INF").isDirectory()).isTrue();
    assertThat(new File(stage, "WEB-INF/web.xml").canRead()).isTrue();
    assertThat(new File(stage, "WEB-INF/appengine-web.xml").canRead()).isTrue();
    checkIfEntryIsInJarFile(
        new File(stage, "WEB-INF/lib/_ah_webinf_classes-0000.jar"), "foo/AClass.class");
    assertThat(new File(stage, "WEB-INF/classes/foo/AClass.class").exists()).isFalse();
  }

  /**
   * Check that precedence is respected, where defaults, appengine-web.xml and flags are applied in
   * order. Flags have the highest precedence.
   *
   * <pre>
   * Defaults are:
   * - splitJarFiles: false
   * - splitJarFilesExcludes: {} (empty set)
   * - jarJsps: true
   * - jarClasses: false
   * - deleteJsps: false
   * - compileEncoding: "UTF-8"
   *
   * Sample app's appengine-web.xml:
   * - splitJarFilesExcludes: {"foo", "bar"}
   * - deleteJsps: true
   * - compileEncoding: "UTF-16"
   *
   * Flags (populated through ApplicationProcessingOptions):
   * - splitJarFiles: true
   * - splitJarFilesExcludes: {"baz"}
   * - deleteJsps: false
   *
   * Expected result:
   * - splitJarFiles: true
   * - splitJarFilesExcludes: {"baz"}
   * - jarJsps: true
   * - jarClasses: false
   * - deleteJsps: false
   * - compileEncoding: "UTF-16"
   * </pre>
   */
  @Test
  public void testStagingOptionPrecedence() throws IOException {
    Application testApp = Application.readApplication(STAGE_WITH_STAGING_OPTIONS);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    // These correspond to the flags passed in to AppCfg
    opts.setStagingOptions(
        StagingOptions.builder()
            .setSplitJarFiles(Optional.of(true))
            .setSplitJarFilesExcludes(Optional.of(ImmutableSortedSet.of("baz")))
            .setDeleteJsps(Optional.of(false))
            .build());

    StagingOptions expected =
        StagingOptions.builder()
            .setSplitJarFiles(Optional.of(true))
            .setSplitJarFilesExcludes(Optional.of(ImmutableSortedSet.of("baz")))
            .setJarJsps(Optional.of(true))
            .setJarClasses(Optional.of(false))
            .setDeleteJsps(Optional.of(false))
            .setCompileEncoding(Optional.of("UTF-16"))
            .build();

    assertThat(testApp.getStagingOptions(opts)).isEqualTo(expected);
  }

  @Test
  public void testWriteDefaultWebXml() throws IOException {
    File tempAppDir = temporaryFolder.newFolder();
    File webinfFile = new File(tempAppDir, "WEB-INF");
    AppEngineConfigException exception =
        assertThrows(
            AppEngineConfigException.class,
            () -> Application.readApplication(tempAppDir.toString()));
    String expectedMessage =
        "Could not find the WEB-INF directory in "
            + webinfFile
            + ". The given application file path must point to an exploded WAR directory that"
            + " contains a WEB-INF directory.";
    assertThat(exception).hasMessageThat().isEqualTo(expectedMessage);
    webinfFile.mkdir();
    Files.asCharSink(new File(webinfFile, "appengine-web.xml"), UTF_8)
        .write(APPENGINE_WEB_XML_CONTENT);
    File webXmlFile = new File(webinfFile, "web.xml");
    assertThat(webXmlFile.exists()).isFalse();
    try {
      Application.readApplication(tempAppDir.toString());
      assertThat(webXmlFile.canRead()).isTrue();
      assertThat(Files.asCharSource(webXmlFile, UTF_8).read())
          .isEqualTo(Application.DEFAULT_WEB_XML_CONTENT);
    } finally {
      deleteRecursively(tempAppDir.toPath());
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    java.nio.file.Files.walkFileTree(
        path,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException exc)
              throws IOException {
            java.nio.file.Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            java.nio.file.Files.delete(file);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  @Test
  public void testXmlValidation() throws IOException {
    Application testApp = Application.readApplication(XMLORDERING_TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    try {
      testApp = Application.readApplication(BAD_WEBXML_TEST_FILES);
      fail("Bad web.xml wasn't detected");
    } catch (AppEngineConfigException ex) {
      // good
    }
    try {
      testApp = Application.readApplication(BAD_APPENGINEWEBXML_TEST_FILES);
      fail("Bad appengine-web.xml wasn't detected");
    } catch (AppEngineConfigException ex) {
      // good
    }
    try {
      testApp = Application.readApplication(BAD_INDEXESXML_TEST_FILES);
      fail("Bad datastore-indexes.xml wasn't detected");
    } catch (AppEngineConfigException ex) {
      // good
    }
    try {
      testApp = Application.readApplication(BAD_CRONXML_TEST_FILES);
      fail("Bad datastore-indexes.xml wasn't detected");
    } catch (AppEngineConfigException ex) {
      // good
    }
  }

  @Test
  public void testBadRuntimeChannel() throws IOException {
    try {
      Application.readApplication(BAD_RUNTIME_CHANNEL);
      fail("iappengine-web.xml content was incorrectly accepted with a runtime-channel.");
    } catch (AppEngineConfigException ex) {
      assertThat(ex)
          .hasMessageThat()
          .isEqualTo("'runtime-channel' is not valid with this runtime.");
    }
  }

  @Test
  public void testBadEntrypoint() throws IOException {
    try {
      Application.readApplication(BAD_ENTRYPOINT);
      fail("appengine-web.xml content was incorrectly accepted with an entrypoint.");
    } catch (AppEngineConfigException ex) {
      assertThat(ex).hasMessageThat().isEqualTo("'entrypoint' is not valid with this runtime.");
    }
  }

  @Test
  public void testCronEntries() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    CronXml cron = testApp.getCronXml();
    assertWithMessage("App without cron entries should have null getCronXml()").that(cron).isNull();

    testApp = Application.readApplication(NOJSP_TEST_FILES);
    cron = testApp.getCronXml();
    assertThat(cron.getEntries()).isEmpty();

    testApp = Application.readApplication(XMLORDERING_TEST_FILES);
    cron = testApp.getCronXml();
    List<CronXml.Entry> entries = cron.getEntries();
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).getTimezone()).isEqualTo("HST");
    assertThat(entries.get(0).getSchedule()).isEqualTo("every 2 hours");
    assertThat(entries.get(0).getUrl()).isEqualTo("http://nowhere/fast");
    assertThat(entries.get(0).getDescription()).isEmpty();
    assertThat(entries.get(1).getTimezone()).isEqualTo("UTC");
    assertThat(entries.get(1).getSchedule()).isEqualTo("every 3 hours");
    assertThat(entries.get(1).getUrl()).isEqualTo("http://nowhere");
    assertThat(entries.get(1).getDescription()).isEqualTo("Why it's here");
  }

  @Test
  public void testCronRetryParameterEntries() throws IOException {
    Application testApp = Application.readApplication(CRON_RETRY_PARAMETERS_TEST_FILES);
    CronXml cron = testApp.getCronXml();
    assertWithMessage("Application should contain cron entries.").that(cron).isNotNull();
    List<CronXml.Entry> entries = cron.getEntries();
    assertThat(entries).hasSize(4);

    // all entries should exist for entry 0
    RetryParametersXml retryParameters = entries.get(0).getRetryParameters();
    assertThat(retryParameters.getRetryLimit()).isEqualTo(3);
    assertThat(retryParameters.getAgeLimitSec()).isEqualTo(60 * 60 * 24 * 2);
    assertThat(retryParameters.getMinBackoffSec()).isEqualTo(2.4);
    assertThat(retryParameters.getMaxBackoffSec()).isEqualTo(10.5);
    assertThat(retryParameters.getMaxDoublings()).isEqualTo(4);

    // only retry limit should be set for entry 1
    retryParameters = entries.get(1).getRetryParameters();
    assertThat(retryParameters.getRetryLimit()).isEqualTo(2);
    assertThat(retryParameters.getAgeLimitSec()).isNull();
    assertThat(retryParameters.getMinBackoffSec()).isNull();
    assertThat(retryParameters.getMaxBackoffSec()).isNull();
    assertThat(retryParameters.getMaxDoublings()).isNull();

    // no parameters should be set for entry 2
    retryParameters = entries.get(2).getRetryParameters();
    assertThat(retryParameters.getRetryLimit()).isNull();
    assertThat(retryParameters.getAgeLimitSec()).isNull();
    assertThat(retryParameters.getMinBackoffSec()).isNull();
    assertThat(retryParameters.getMaxBackoffSec()).isNull();
    assertThat(retryParameters.getMaxDoublings()).isNull();

    // no retry parameters should exist for entry 3
    retryParameters = entries.get(3).getRetryParameters();
    assertThat(retryParameters).isNull();
  }

  @Test
  public void testCronRetryParameterNegativeRetryLimit() throws IOException {
    AppEngineConfigException aece =
        assertThrows(
            AppEngineConfigException.class,
            () -> Application.readApplication(CRON_NEGATIVE_RETRY_LIMIT_TEST_FILES));
    assertThat(aece).hasMessageThat().contains("XML error");
    assertThat(aece).hasCauseThat().hasMessageThat().contains("'-3'");
  }

  @Test
  public void testCronRetryParameterNegativeMaxBackoff() throws IOException {
    AppEngineConfigException aece =
        assertThrows(
            AppEngineConfigException.class,
            () -> Application.readApplication(CRON_NEGATIVE_MAX_BACKOFF_TEST_FILES));
    assertThat(aece).hasMessageThat().contains("XML error");
    assertThat(aece).hasCauseThat().hasMessageThat().contains("'-10.5'");
  }

  @Test
  public void testCronRetryParameterBadAgeLimit() throws IOException {
    AppEngineConfigException aece =
        assertThrows(
            AppEngineConfigException.class,
            () -> Application.readApplication(CRON_BAD_AGE_LIMIT_TEST_FILES));
    assertThat(aece).hasMessageThat().contains("XML error");
    assertThat(aece).hasCauseThat().hasMessageThat().contains("'2x'");
  }

  @Test
  public void testCronRetryParameterTwoMaxDoublings() throws IOException {
    AppEngineConfigException aece =
        assertThrows(
            AppEngineConfigException.class,
            () -> Application.readApplication(CRON_TWO_MAX_DOUBLINGS_TEST_FILES));
    assertThat(aece).hasMessageThat().contains("XML error");
    assertThat(aece).hasCauseThat().hasMessageThat().contains("'max-doublings'");
  }

  @Test
  public void testRuntime() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES_RUNTIME_DEFINED);
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    testApp.createStagingDirectory(opts);
    assertThat(testApp.getRuntime()).isEqualTo("foo-bar");
  }

  @Test
  public void testServer() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES_AUTOMATIC_MODULE);
    assertThat(testApp.getModule()).isEqualTo("stan");
    assertThat(testApp.getInstanceClass()).isEqualTo("F8");
    AppEngineWebXml.AutomaticScaling settings = testApp.getAppEngineWebXml().getAutomaticScaling();
    assertThat(settings.isEmpty()).isFalse();
    assertThat(settings.getMinPendingLatency()).isEqualTo("10.5s");
    assertThat(settings.getMaxPendingLatency()).isEqualTo("10900ms");
    assertThat(settings.getMinIdleInstances()).isEqualTo("automatic");
    assertThat(settings.getMaxIdleInstances()).isEqualTo("10");
    assertThat(settings.getMaxConcurrentRequests()).isEqualTo("20");
  }

  @Test
  public void testManualServer() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES_MANUAL_MODULE);
    assertThat(testApp.getModule()).isEqualTo("stan");
    assertThat(testApp.getInstanceClass()).isEqualTo("B8");
    AppEngineWebXml.ManualScaling settings = testApp.getAppEngineWebXml().getManualScaling();
    assertThat(settings.isEmpty()).isFalse();
    assertThat(settings.getInstances()).isEqualTo("10");
  }

  @Test
  public void testBasicServer() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES_BASIC_MODULE);
    assertThat(testApp.getModule()).isEqualTo("stan");
    assertThat(testApp.getInstanceClass()).isEqualTo("B8");
    AppEngineWebXml.BasicScaling settings = testApp.getAppEngineWebXml().getBasicScaling();
    assertThat(settings.isEmpty()).isFalse();
    assertThat(settings.getMaxInstances()).isEqualTo("11");
    assertThat(settings.getIdleTimeout()).isEqualTo("10m");

    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    File stagingDir = testApp.createStagingDirectory(opts);
  }

  @Test
  public void testIncludeHttpHeaders() throws IOException {
    Application testApp = Application.readApplication(INCLUDE_HTTP_HEADERS_TEST_FILES);
    List<AppEngineWebXml.StaticFileInclude> includes =
        testApp.getAppEngineWebXml().getStaticFileIncludes();

    assertThat(includes).hasSize(1);

    Map<String, String> httpHeaders = includes.get(0).getHttpHeaders();
    assertThat(httpHeaders).hasSize(2);
    assertThat(httpHeaders.get("P3P")).isEqualTo("P3P header value");
    assertThat(httpHeaders.get("Access-Control-Allow-Origin")).isEqualTo("http://example.org");
  }

  //TODO(ludo ) @Test
  public void testDispatch() throws IOException {
    Application testApp = Application.readApplication(getWarPath("sample-dispatch"));
    String expectYaml = "dispatch:\n" + "- url: '*/userapp/*'\n" + "  module: web\n";
    assertThat(testApp.getDispatchXml().toYaml()).isEqualTo(expectYaml);
  }

  //TODO(ludo ) @Test
  public void testDispatch_yaml() throws IOException {
    Application testApp = Application.readApplication(getWarPath("sample-dispatch-yaml"));
    String expectYaml = "dispatch:\n" + "- url: '*/*'\n" + "  module: web\n";
    assertThat(testApp.getDispatchXml().toYaml()).isEqualTo(expectYaml);
  }

  //TODO(ludo)  @Test
  public void testDispatch_xmlAndYaml() throws IOException {
    Application testApp = Application.readApplication(getWarPath("sample-dispatch-xml-and-yaml"));
    String expectYaml = "dispatch:\n" + "- url: '*/userapp/*'\n" + "  module: web\n";
    assertThat(testApp.getDispatchXml().toYaml()).isEqualTo(expectYaml);
  }

  @Test
  public void testDispatch_invalidXml() throws IOException {
    AppEngineConfigException aece =
        assertThrows(
            AppEngineConfigException.class,
            () -> Application.readApplication(getWarPath("sample-baddispatch")));
    assertThat(aece).hasMessageThat().contains("error validating");
  }

  @Test
  public void testDispatch_invalidYaml() throws IOException {
    AppEngineConfigException aece =
        assertThrows(
            AppEngineConfigException.class,
            () -> Application.readApplication(getWarPath("sample-baddispatch-yaml")));
    String expect =
        "Unable to find property 'not-url' in "
            + "com.google.apphosting.utils.config.DispatchYamlReader$DispatchYamlEntry";
    assertThat(aece).hasMessageThat().isEqualTo(expect);
  }

  @Test
  public void testDispatch_missing() throws IOException {
    Application testApp = Application.readApplication(TEST_FILES_BACKENDS);
    assertThat(testApp.getDispatchXml()).isNull();
  }


  @Test
  public void testUseJava8Standard() throws Exception {
    Application testApp = Application.readApplication(SERVLET3_STANDARD_APP_ROOT);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(SERVLET3_APP_ID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    
    File stageDir = testApp.createStagingDirectory(opts, temporaryFolder.newFolder());
    File appYaml = new File(stageDir, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "runtime: java8");
    // See if the index.jsp file has been pre-compiled:
    File jspJar = new File(stageDir, "WEB-INF/lib/_ah_compiled_jsps-0000.jar");
    assertThat(jspJar.exists()).isTrue();
    // Test that the classes in the generated jar are for Java8.
    assertThat(getJavaJarVersion(jspJar)).isEqualTo(8);

    // See if one of the Jetty9 or Jetty12  JSP jars is being added:
    assertThat(
            new File(stageDir, "WEB-INF/lib/org.apache.taglibs.taglibs-standard-impl-1.2.5.jar")
                    .exists()
                || new File(
                        stageDir,
                        "WEB-INF/lib/org.glassfish.web.jakarta.servlet.jsp.jstl-3.0.1.jar")
                    .exists()
                || new File(
                        stageDir, "WEB-INF/lib/org.glassfish.web.javax.servlet.jsp.jstl-1.2.5.jar")
                    .exists())
        .isTrue();
  }

  @Test
  public void testStageGaeStandardJava8Servlet31QuickstartWebListenerMemcache()
      throws IOException, ParserConfigurationException, SAXException {
    Application testApp = Application.readApplication(SERVLET3_STANDARD_WEBLISTENER_MEMCACHE);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(SERVLET3_APP_ID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    File stageDir = testApp.createStagingDirectory(opts, temporaryFolder.newFolder());

    File quickstartXml = new File(stageDir, "WEB-INF/quickstart-web.xml");
    File minQuickstartXml = new File(stageDir, "WEB-INF/min-quickstart-web.xml");

    assertThat(quickstartXml.canRead()).isTrue();
    assertThat(minQuickstartXml.canRead()).isTrue();

    Document quickstartDoc =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(quickstartXml);
    // No more CloudSqlConnectionCleanupFilter in 9.4 (defined as a RequestListener)
    assertThat(
            quickstartDoc.getDocumentElement().getElementsByTagName("filter-mapping").getLength())
        .isEqualTo(0);
    // No more default servlets in webdefault.xml, all defined inside our jetty 9.4 init code.
    assertThat(
            quickstartDoc.getDocumentElement().getElementsByTagName("servlet-mapping").getLength())
        .isEqualTo(0);

    Document minQuickstartDoc =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(minQuickstartXml);
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("filter-mapping")
                .getLength())
        .isEqualTo(0);

    // 0 user defined annotated servlet in the SERVLET3_STANDARD_WEBLISTENER_MEMCACHE application.
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("servlet-mapping")
                .getLength())
        .isEqualTo(0);

    // 1 user defined annotated weblistener in the SERVLET3_STANDARD_WEBLISTENER_MEMCACHE
    // application.
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("listener-class")
                .getLength())
        .isEqualTo(1);

    // We should not see any welcome-file entries:
    assertThat(
            minQuickstartDoc.getDocumentElement().getElementsByTagName("welcome-file").getLength())
        .isEqualTo(0);

    // We should not see any security-constraint entries:
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("security-constraint")
                .getLength())
        .isEqualTo(0);
  }

  @Test
  public void testStageGaeStandardJava8Servlet31QuickstartWithoutJSP()
      throws IOException, ParserConfigurationException, SAXException {
    Application testApp = Application.readApplication(SERVLET3_STANDARD_APP_NO_JSP_ROOT);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(SERVLET3_APP_ID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    File stageDir = testApp.createStagingDirectory(opts, temporaryFolder.newFolder());

    File quickstartXml = new File(stageDir, "WEB-INF/quickstart-web.xml");
    File minQuickstartXml = new File(stageDir, "WEB-INF/min-quickstart-web.xml");

    assertThat(quickstartXml.canRead()).isTrue();
    assertThat(minQuickstartXml.canRead()).isTrue();

    Document quickstartDoc =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(quickstartXml);
    assertThat(
            quickstartDoc.getDocumentElement().getElementsByTagName("filter-mapping").getLength())
        .isEqualTo(0);
    assertThat(
            quickstartDoc.getDocumentElement().getElementsByTagName("servlet-mapping").getLength())
        .isEqualTo(1);

    Document minQuickstartDoc =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(minQuickstartXml);
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("filter-mapping")
                .getLength())
        .isEqualTo(0);
    // 1 user defined annotated servlet in the SERVLET3_STANDARD_APP_NO_JSP_ROOT application.
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("servlet-mapping")
                .getLength())
        .isEqualTo(1);

    // We should not see any welcome-file entries:
    assertThat(
            minQuickstartDoc.getDocumentElement().getElementsByTagName("welcome-file").getLength())
        .isEqualTo(0);
    // We should not see any security-constraint entries:
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("security-constraint")
                .getLength())
        .isEqualTo(0);

    // We want to verify we do not have any TLDs and resourcesdefined from useless JSP jars:
    // <context-param>
    //    <param-name>org.eclipse.jetty.tlds</param-name>
    //    <param-value><![CDATA[]]></param-value>
    // </context-param>
    // <context-param>
    //   <param-name>org.eclipse.jetty.resources</param-name>
    //   <param-value><![CDATA[]]></param-value>
    // </context-param>
    //  <context-param>
    //   <param-name>org.eclipse.jetty.originAttribute</param-name>
    //   <param-value>origin</param-value>
    //  </context-param>

    NodeList nodeList = quickstartDoc.getElementsByTagName("context-param");

    // TODO: review. This expectation used to be 3, this is because the Jetty
    //  QuickStartGeneratorConfiguration.generateQuickStartWebXml will now
    //  add an empty set if it doesn't have any SCIs instead of not setting the context param.
    if (Boolean.getBoolean("appengine.use.EE8")||Boolean.getBoolean("appengine.use.EE10")) {
      assertThat(nodeList.getLength()).isEqualTo(4);
    } else {
      assertThat(nodeList.getLength()).isEqualTo(3);      
    }
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node contextParam = nodeList.item(i).getFirstChild();
      int nbParamValue = 0;
      do {
        String nodeName = contextParam.getNodeName();
        if (nodeName.equals("param-value")) {
          nbParamValue++;
          String content = contextParam.getFirstChild().getTextContent();
          assertThat(content).isIn(Arrays.asList("", "origin"));
        }
      } while ((contextParam = contextParam.getNextSibling()) != null);
      assertThat(nbParamValue).isEqualTo(1);
    }

    List<String> patterns = testApp.getWebXml().getServletPatterns();
    assertThat(patterns).contains("/test/*");
    assertThat(patterns).doesNotContain("/_ah/queue/__deferred__");
    assertThat(patterns).doesNotContain("/_ah/sessioncleanup");
    assertThat(patterns).doesNotContain("/");
    assertThat(patterns).doesNotContain("/*");
  }

  @Test
  public void testStageGaeStandardJava8Servlet31QuickstartWithJSP()
      throws IOException, ParserConfigurationException, SAXException {

    Application testApp = Application.readApplication(SERVLET3_STANDARD_APP_ROOT);
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    assertThat(testApp.getAppId()).isEqualTo(SERVLET3_APP_ID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    // We want JSP compilation for Standard Java8.
    opts.setCompileJsps(true);
    File stageDir = testApp.createStagingDirectory(opts, temporaryFolder.newFolder());

    File quickstartXml = new File(stageDir, "WEB-INF/quickstart-web.xml");
    File minQuickstartXml = new File(stageDir, "WEB-INF/min-quickstart-web.xml");

    assertThat(quickstartXml.canRead()).isTrue();
    assertThat(minQuickstartXml.canRead()).isTrue();

    Document quickstartDoc =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(quickstartXml);
    assertThat(
            quickstartDoc.getDocumentElement().getElementsByTagName("filter-mapping").getLength())
        .isEqualTo(0);
    assertThat(
            quickstartDoc.getDocumentElement().getElementsByTagName("servlet-mapping").getLength())
        .isEqualTo(2);

    Document minQuickstartDoc =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(minQuickstartXml);
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("filter-mapping")
                .getLength())
        .isEqualTo(0);
    // 2 user defined servlets in the SERVLET3_APP_ID application, 1 by annotation and 1 by
    // a pre-compiled JSP.
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("servlet-mapping")
                .getLength())
        .isEqualTo(2);

    // We should not see any welcome-file entries:
    assertThat(
            minQuickstartDoc.getDocumentElement().getElementsByTagName("welcome-file").getLength())
        .isEqualTo(0);
    // We should not see any security-constraint entries:
    assertThat(
            minQuickstartDoc
                .getDocumentElement()
                .getElementsByTagName("security-constraint")
                .getLength())
        .isEqualTo(0);

    // We want to verify that the list of tlds defined by:
    // <context-param>
    //   <param-name>org.eclipse.jetty.tlds</param-name>
    //   <param-value><![CDATA[
    //     "jar:file:/Users/ludo/.m2/repository/...,
    //     "jar:${WAR}/WEB-INF/lib/jstl-1.2.jar!/META-INF/scriptfree.tld",
    //       ...
    // do not contain a local file reference, that would mean that the quickstart classpath
    // contains tld jars, that should be on the user web app classpath.

    NodeList nodeList = quickstartDoc.getElementsByTagName("context-param");
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node contextParam = nodeList.item(i).getFirstChild();
      do {
        String nodeName = contextParam.getNodeName();
        if (nodeName.equals("param-value")) {
          String content = contextParam.getFirstChild().getTextContent();
          assertThat(content).doesNotContain("\"jar:file");
        }
      } while ((contextParam = contextParam.getNextSibling()) != null);
    }

    List<String> patterns = testApp.getWebXml().getServletPatterns();
    assertThat(patterns).contains("/test/*");
    assertThat(patterns).contains("/index.jsp");
    assertThat(patterns).doesNotContain("/_ah/queue/__deferred__");
    assertThat(patterns).doesNotContain("/_ah/sessioncleanup");
    assertThat(patterns).doesNotContain("/");
    assertThat(patterns).doesNotContain("/*");
  }

  @Test
  public void testStageGaeStandardJava8WithOnlyJasperContextInitializer()
      throws IOException, ParserConfigurationException, SAXException {

    Application testApp = Application.readApplication(SERVLET3_STANDARD_APP_ROOT);
    testApp.setDetailsWriter(new PrintWriter(new OutputStreamWriter(System.out, UTF_8)));

    assertThat(testApp.getAppId()).isEqualTo(SERVLET3_APP_ID);

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    // We want JSP compilation for Standard Java8.
    opts.setCompileJsps(true);

    testApp.createStagingDirectory(opts, temporaryFolder.newFolder());
    assertThat(testApp.getWebXml().getFallThroughToRuntime()).isFalse();
    String expectedJasperInitializer;
    if (Boolean.getBoolean("appengine.use.EE8")) {
        expectedJasperInitializer
                = "\"ContainerInitializer"
                + "{org.eclipse.jetty.ee8.apache.jsp.JettyJasperInitializer"
                + ",interested=[],applicable=[],annotated=[]}\"";
    } else if (Boolean.getBoolean("appengine.use.EE10")) {
        expectedJasperInitializer
                = "\"ContainerInitializer"
                + "{org.eclipse.jetty.ee10.apache.jsp.JettyJasperInitializer"
                + ",interested=[],applicable=[],annotated=[]}\"";
    } else {
        expectedJasperInitializer
                = "\"ContainerInitializer"
                + "{org.eclipse.jetty.apache.jsp.JettyJasperInitializer"
                + ",interested=[],applicable=[],annotated=[]}\"";
    }
    Map<String, String> trimmedContextParams =
        Maps.transformValues(testApp.getWebXml().getContextParams(), String::trim);
    assertThat(trimmedContextParams)
        .containsEntry("org.eclipse.jetty.containerInitializers", expectedJasperInitializer);
  }

  //TODO(ludo) @Test
  public void testStageGaeStandardJava8WithContextInitializers()
      throws IOException, ParserConfigurationException, SAXException {
    Application testApp = Application.readApplication(SERVLET3_STANDARD_APP_WITH_CONTAINER_INIT);
    testApp.setDetailsWriter(new PrintWriter(new OutputStreamWriter(System.out, UTF_8)));
    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();

    // We want JSP compilation for Standard Java8.
    opts.setCompileJsps(true);
    testApp.createStagingDirectory(opts, temporaryFolder.newFolder());
    assertThat(testApp.getWebXml().getFallThroughToRuntime()).isTrue();
    String expectedJasperInitializer =
        "\"ContainerInitializer"
            + "{servletthree.Servlet3ContainerInitializer"
            + ",interested=[],applicable=[],annotated=[]}\"";
    Map<String, String> trimmedContextParams =
        Maps.transformValues(testApp.getWebXml().getContextParams(), String::trim);
    assertThat(trimmedContextParams)
        .containsEntry("org.eclipse.jetty.containerInitializers", expectedJasperInitializer);
  }


  @Test
  public void testCountClasses() throws IOException {
    assertThat(Application.countClasses(new File(CLASSES_TEST_FILES, "/WEB-INF/classes")))
        .isEqualTo(1);
  }

  @Test
  public void testCountClasses_noClassesDir() throws IOException {
    // Test that we don't explode when WEB-INF/classes directory doesn't exist.
    Application testApp = Application.readApplication(TEST_FILES);
    testApp.createStagingDirectory(new ApplicationProcessingOptions());
  }

  @Test
  public void testMimetypes() throws Exception {
    assertThat(Application.guessContentTypeFromName("foo.class")).isEqualTo("application/java-vm");
    assertThat(Application.guessContentTypeFromName("foo.css")).isEqualTo("text/css");
    assertThat(Application.guessContentTypeFromName("foo.gif")).isEqualTo("image/gif");
    assertThat(Application.guessContentTypeFromName("foo.ico")).isEqualTo("image/x-icon");
    assertThat(Application.guessContentTypeFromName("foo.java")).isEqualTo("text/plain");
    assertThat(Application.guessContentTypeFromName("foo.jar"))
        .isEqualTo("application/java-archive");
    assertThat(Application.guessContentTypeFromName("foo.jpe")).isEqualTo("image/jpeg");
    assertThat(Application.guessContentTypeFromName("foo.jpeg")).isEqualTo("image/jpeg");
    assertThat(Application.guessContentTypeFromName("foo.json")).isEqualTo("application/json");
    assertThat(Application.guessContentTypeFromName("foo.htm")).isEqualTo("text/html");
    assertThat(Application.guessContentTypeFromName("foo.html")).isEqualTo("text/html");
    assertThat(Application.guessContentTypeFromName("foo.zip")).isEqualTo("application/zip");
    assertThat(Application.guessContentTypeFromName("foo.ludo"))
        .isEqualTo("application/octet-stream");
    assertThat(Application.guessContentTypeFromName("foo.wasm")).isEqualTo("application/wasm");
  }

  @Test
  public void testJava8NoWebXmlNoApiJar() throws Exception {

    Path temp = CopyDirVisitor.createTempDirectoryFrom(Paths.get(JAVA8_NO_WEBXML));
    Application testApp = Application.readApplication(temp.toFile().getAbsolutePath());
    testApp.setDetailsWriter(
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));

    ApplicationProcessingOptions opts = new ApplicationProcessingOptions();
    // No JSP compilation for this sample.
    opts.setCompileJsps(false);
    File stageDir = testApp.createStagingDirectory(opts, temporaryFolder.newFolder());

    File appYaml = new File(stageDir, "WEB-INF/appengine-generated/app.yaml");
    assertFileContains(appYaml, "runtime: java8");
    assertFileContains(appYaml, "threadsafe: True");
    assertFileContains(appYaml, "api_version: 'user_defined'");
  }

  /** returns the Java version of the first class in the jar, or -1 if error. */
  private static int getJavaJarVersion(File jarFile) throws Exception {
    try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jarFile))) {
      JarEntry jarEntry = jarInputStream.getNextJarEntry();
      while (jarEntry != null) {
        if (jarEntry.getName().endsWith(".class")) {
          // We only check the first class file in the jar.
          DataInputStream in = new DataInputStream(jarInputStream);
          if (in.readInt() == 0xcafebabe) {
            in.readShort(); // discard minor version
            int majorVersion = in.readShort();
            int actualVersion = majorVersion - 44;
            return actualVersion;
          }
          jarEntry = jarInputStream.getNextJarEntry();
        }
      }
      return -1;
    }
  }

  private static class CopyDirVisitor extends SimpleFileVisitor<Path> {

    private final Path fromPath;
    private final Path toPath;

    CopyDirVisitor(Path fromPath, Path toPath) {
      this.fromPath = fromPath;
      this.toPath = toPath;
    }
    // Return a temp directory that contains the from directory
    static Path createTempDirectoryFrom(Path from) throws IOException {
      Path to = java.nio.file.Files.createTempDirectory("staging");
      java.nio.file.Files.walkFileTree(
          from,
          EnumSet.of(FileVisitOption.FOLLOW_LINKS),
          Integer.MAX_VALUE,
          new CopyDirVisitor(from, to));
      deleteOnExit(to.toFile());
      return to;
    }

    private static void deleteOnExit(File folder) {
      folder.deleteOnExit();
      for (File file : folder.listFiles()) {
        if (file.isDirectory()) {
          deleteOnExit(file);
        } else {
          file.deleteOnExit();
        }
      }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      Path targetPath = toPath.resolve(fromPath.relativize(dir));
      if (!java.nio.file.Files.exists(targetPath)) {
        java.nio.file.Files.createDirectory(targetPath);
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      java.nio.file.Files.copy(
          file, toPath.resolve(fromPath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
      return FileVisitResult.CONTINUE;
    }
  }
}