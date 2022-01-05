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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.tools.admin.RepoInfo.SourceContext;
import com.google.appengine.tools.info.AppengineSdk;
import com.google.appengine.tools.info.Version;
import com.google.appengine.tools.util.ApiVersionFinder;
import com.google.appengine.tools.util.FileIterator;
import com.google.appengine.tools.util.JarSplitter;
import com.google.appengine.tools.util.JarTool;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.config.AppYamlProcessor;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.BackendsXmlReader;
import com.google.apphosting.utils.config.BackendsYamlReader;
import com.google.apphosting.utils.config.CronXml;
import com.google.apphosting.utils.config.CronXmlReader;
import com.google.apphosting.utils.config.CronYamlReader;
import com.google.apphosting.utils.config.DispatchXml;
import com.google.apphosting.utils.config.DispatchXmlReader;
import com.google.apphosting.utils.config.DispatchYamlReader;
import com.google.apphosting.utils.config.DosXml;
import com.google.apphosting.utils.config.DosXmlReader;
import com.google.apphosting.utils.config.DosYamlReader;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.apphosting.utils.config.IndexesXml;
import com.google.apphosting.utils.config.IndexesXmlReader;
import com.google.apphosting.utils.config.QueueXml;
import com.google.apphosting.utils.config.QueueXmlReader;
import com.google.apphosting.utils.config.QueueYamlReader;
import com.google.apphosting.utils.config.StagingOptions;
import com.google.apphosting.utils.config.WebXml;
import com.google.apphosting.utils.config.WebXmlReader;
import com.google.apphosting.utils.config.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jetty.http.MimeTypes;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * An App Engine application. You can {@link #readApplication read} an {@code Application} from a
 * path, and {@link com.google.appengine.tools.admin.AppAdminFactory#createAppAdmin create} an
 * {@link com.google.appengine.tools.admin.AppAdmin} to upload, create indexes, or otherwise manage
 * it.
 *
 */
public class Application implements GenericApplication {

  // JSP compilation
  private static final int MAX_COMPILED_JSP_JAR_SIZE = 1024 * 1024 * 5;
  private static final String COMPILED_JSP_JAR_NAME_PREFIX = "_ah_compiled_jsps";

  // WEB-INF/classes jarring
  private static final int MAX_CLASSES_JAR_SIZE = 1024 * 1024 * 5;
  private static final String CLASSES_JAR_NAME_PREFIX = "_ah_webinf_classes";

  // Runtime ids.
  // Should accept java8* for multiple variations of Java8.
  private static final String JAVA_8_RUNTIME_ID = "java8";
  private static final String GOOGLE_RUNTIME_ID = "google";
  private static final String GOOGLE_LEGACY_RUNTIME_ID = "googlelegacy";
  private static final String JAVA_11_RUNTIME_ID = "java11";

  private static final ImmutableSet<String> ALLOWED_RUNTIME_IDS =
      ImmutableSet.of(
          JAVA_8_RUNTIME_ID, JAVA_11_RUNTIME_ID, GOOGLE_RUNTIME_ID, GOOGLE_LEGACY_RUNTIME_ID);

  // Beta settings keys
  private static final String BETA_SOURCE_REFERENCE_KEY = "source_reference";

  private static final Pattern JSP_REGEX = Pattern.compile(".*\\.jspx?");
  // Jetty's Container Initializer Pattern is taken from
  // org.eclipse.jetty.plus.annotation.ContainerInitializer (9.3.x branch).
  private static final Pattern CONTAINER_INITIALIZER_PATTERN =
      Pattern.compile(
          "ContainerInitializer\\{(.*),interested=(.*),applicable=(.*),annotated=(.*)\\}");

  // Regex for detecting if a URL starts with a protocol.
  private static final Pattern HAS_PROTOCOL_RE = Pattern.compile("^\\w+:");

  // If we detect many .class files, the SDK will output a log message suggesting that the user
  // package .class files into jars to improve classloading times.
  private static final int SUGGEST_JAR_THRESHOLD = 100;

  // Default web.xml if no user-provided web.xml
  static final String DEFAULT_WEB_XML_CONTENT =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<web-app version=\"3.1\" "
          + "xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\" "
          + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
          + "xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee "
          + "http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd\">"
          + "</web-app>";

  private static boolean shouldAttemptSymlink = Utility.isOsUnix();
  // TODO: this should probably move somewhere else, although having
  // it here ensures that we're actually in an SDK context so SdkInfo makes
  // sense!
  private static File sdkDocsDir;

  public static synchronized File getSdkDocsDir() {
    if (null == sdkDocsDir) {
      sdkDocsDir = AppengineSdk.getSdk().getResourcesDirectory();
    }
    return sdkDocsDir;
  }

  private static Version sdkVersion;

  public static synchronized Version getSdkVersion() {
    if (null == sdkVersion) {
      sdkVersion = AppengineSdk.getSdk().getLocalVersion();
    }
    return sdkVersion;
  }

  private static final String STAGEDIR_PREFIX = "appcfg";

  private static final Logger logger = Logger.getLogger(Application.class.getName());

  private static final MimeTypes mimeTypes = new MimeTypes();

  private final CronXml cronXml;
  private final DispatchXml dispatchXml;
  private final DosXml dosXml;
  private final QueueXml queueXml;
  private final IndexesXml indexesXml;
  private final BackendsXml backendsXml;
  private final File baseDir;
  private final SourceContext sourceContext;

  private AppEngineWebXml appEngineWebXml;
  private WebXml webXml;
  private String servletVersion;
  private File stageDir;
  private File externalResourceDir;
  private String apiVersion;
  private String calculatedRuntime;
  private String appYaml;

  private UpdateListener listener;
  private PrintWriter detailsWriter;
  private int updateProgress = 0;
  private int progressAmount = 0;
  // location of the generated Servlets from JSPs.
  // Needed only for testing the output of the JSP compiler.
  private File jspJavaFilesGeneratedTempDirectory;

  protected Application() {
    // This constructor should be used only for creating mock applications
    // for testing.
    this.backendsXml = null;
    this.cronXml = null;
    this.dispatchXml = null;
    this.dosXml = null;
    this.indexesXml = null;
    this.queueXml = null;

    this.baseDir = null;
    this.sourceContext = null;
  }

  private Application(
      String explodedPath,
      String appId,
      String module,
      String appVersion,
      RepoInfo.SourceContext sourceContext) {
    this.baseDir = new File(explodedPath);
    // Normalize the exploded path.
    explodedPath = buildNormalizedPath(baseDir);
    File webinf = new File(baseDir, "WEB-INF");
    if (!webinf.getName().equals("WEB-INF")) {
      // On OSes with case-insensitive file systems, it is possible to
      // create a directory with different case.  Some of our handling
      // code is case-sensitive so we disallow this.
      throw new AppEngineConfigException("WEB-INF directory must be capitalized.");
    }

    String webinfPath = webinf.getPath();
    AppEngineWebXmlReader aewebReader = new AppEngineWebXmlReader(explodedPath);
    WebXmlReader webXmlReader = new WebXmlReader(explodedPath);
    AppYamlProcessor.convert(webinf, aewebReader.getFilename(), webXmlReader.getFilename());

    File webXmlFile = new File(webinfPath, "web.xml");
    if (!webXmlFile.exists()) {
      writeDefaultWebXml(webXmlFile);
    }

    if (new File(aewebReader.getFilename()).exists()) {
      XmlUtils.validateXml(
          aewebReader.getFilename(), new File(getSdkDocsDir(), "appengine-web.xsd"));
    }
    appEngineWebXml = aewebReader.readAppEngineWebXml();
    appEngineWebXml.setSourcePrefix(explodedPath);

    if (appId != null) {
      appEngineWebXml.setAppId(appId);
    }
    if (appVersion != null) {
      appEngineWebXml.setMajorVersionId(appVersion);
    }
    if (module != null) {
      appEngineWebXml.setModule(module);
    }

    // Auto-detect and propagate source context to the server.
    if (sourceContext == null) {
      sourceContext = new RepoInfo(baseDir).getSourceContext();
      if (sourceContext != null) {
        String sourceRef = sourceContext.getRevisionId();
        if (sourceContext.getRepositoryUrl() != null
            && HAS_PROTOCOL_RE.matcher(sourceContext.getRepositoryUrl()).find()) {
          sourceRef = sourceContext.getRepositoryUrl() + "#" + sourceRef;
        }
        // The option is available since 1.9.23.
        appEngineWebXml.addBetaSetting(BETA_SOURCE_REFERENCE_KEY, sourceRef);
      }
    }
    this.sourceContext = sourceContext;

    webXml = webXmlReader.readWebXml();
    // TODO: validateXml(webXml.getFilename(), new File(SDKDOCS, "servlet.xsd"));
    webXml.validate();
    servletVersion = webXmlReader.getServletVersion();

    validateFilterClasses();
    validateRuntime();

    CronXmlReader cronReader = new CronXmlReader(explodedPath);
    if (new File(cronReader.getFilename()).exists()) {
      XmlUtils.validateXml(cronReader.getFilename(), new File(getSdkDocsDir(), "cron.xsd"));
    }
    CronXml parsedCronXml = cronReader.readCronXml();
    if (parsedCronXml == null) {
      CronYamlReader cronYaml = new CronYamlReader(webinfPath);
      parsedCronXml = cronYaml.parse();
    }
    this.cronXml = parsedCronXml;

    QueueXmlReader queueReader = new QueueXmlReader(explodedPath);
    if (new File(queueReader.getFilename()).exists()) {
      XmlUtils.validateXml(queueReader.getFilename(), new File(getSdkDocsDir(), "queue.xsd"));
    }
    QueueXml parsedQueueXml = queueReader.readQueueXml();
    if (parsedQueueXml == null) {
      QueueYamlReader queueYaml = new QueueYamlReader(webinfPath);
      parsedQueueXml = queueYaml.parse();
    }
    this.queueXml = parsedQueueXml;

    DispatchXmlReader dispatchXmlReader =
        new DispatchXmlReader(explodedPath, DispatchXmlReader.DEFAULT_RELATIVE_FILENAME);
    if (new File(dispatchXmlReader.getFilename()).exists()) {
      XmlUtils.validateXml(
          dispatchXmlReader.getFilename(), new File(getSdkDocsDir(), "dispatch.xsd"));
    }
    DispatchXml parsedDispatchXml = dispatchXmlReader.readDispatchXml();
    if (parsedDispatchXml == null) {
      DispatchYamlReader dispatchYamlReader = new DispatchYamlReader(webinfPath);
      parsedDispatchXml = dispatchYamlReader.parse();
    }
    this.dispatchXml = parsedDispatchXml;

    DosXmlReader dosReader = new DosXmlReader(explodedPath);
    if (new File(dosReader.getFilename()).exists()) {
      XmlUtils.validateXml(dosReader.getFilename(), new File(getSdkDocsDir(), "dos.xsd"));
    }
    DosXml parsedDosXml = dosReader.readDosXml();
    if (parsedDosXml == null) {
      DosYamlReader dosYaml = new DosYamlReader(webinfPath);
      parsedDosXml = dosYaml.parse();
    }
    this.dosXml = parsedDosXml;

    IndexesXmlReader indexReader = new IndexesXmlReader(explodedPath);
    File datastoreSchema = new File(getSdkDocsDir(), "datastore-indexes.xsd");
    if (new File(indexReader.getFilename()).exists()) {
      XmlUtils.validateXml(indexReader.getFilename(), datastoreSchema);
    }
    // TODO: consider validating the auto-generated file too, in case
    // someone edited it by hand.  However, note that the "autoGenerate" attribute
    // of the datastore-indexes XML element is currently required by the XML Schema;
    // so check whether the dev server actually does include it (or consider
    // changing it to optional, since it kinda doesn't really make sense in the
    // case of the auto-generated file.
    //
    // validateXml(indexReader.getAutoFilename(), datastoreSchema);
    indexesXml = indexReader.readIndexesXml();

    BackendsXmlReader backendsReader = new BackendsXmlReader(explodedPath);
    if (new File(backendsReader.getFilename()).exists()) {
      XmlUtils.validateXml(backendsReader.getFilename(), new File(getSdkDocsDir(), "backends.xsd"));
    }
    BackendsXml parsedBackendsXml = backendsReader.readBackendsXml();
    if (parsedBackendsXml == null) {
      BackendsYamlReader backendsYaml = new BackendsYamlReader(webinfPath);
      parsedBackendsXml = backendsYaml.parse();
    }
    this.backendsXml = parsedBackendsXml;
  }

  /**
   * Builds a normalized path for the given directory in which forward slashes are used as the file
   * separator on all platforms.
   *
   * @param dir A directory
   * @return The normalized path
   */
  private static String buildNormalizedPath(File dir) {
    String normalizedPath = dir.getPath();
    if (File.separatorChar == '\\') {
      normalizedPath = normalizedPath.replace('\\', '/');
    }
    return normalizedPath;
  }

  /**
   * Reads the App Engine application from {@code path}. The path may either be a WAR file or the
   * root of an exploded WAR directory.
   *
   * @param path a not {@code null} path.
   * @throws IOException if an error occurs while trying to read the {@code Application}.
   * @throws com.google.apphosting.utils.config.AppEngineConfigException if the {@code
   *     Application's} appengine-web.xml file is malformed.
   */
  public static Application readApplication(String path) throws IOException {
    // TODO If path is a WAR file, explode to temporary directory first.
    return readApplication(path, null);
  }

  /**
   * Reads the App Engine application from {@code path}. The path may either be a WAR file or the
   * root of an exploded WAR directory.
   *
   * @param path a not {@code null} path.
   * @param sourceContext an explicit RepoInfo.SourceContext. If {@code null}, the source context
   *     will be inferred from the current directory.
   * @throws IOException if an error occurs while trying to read the {@code Application}.
   * @throws com.google.apphosting.utils.config.AppEngineConfigException if the {@code
   *     Application's} appengine-web.xml file is malformed.
   */
  public static Application readApplication(String path, SourceContext sourceContext)
      throws IOException {
    // TODO If path is a WAR file, explode to temporary directory first.
    return new Application(path, null, null, null, sourceContext);
  }

  /**
   * Validates an application for regular AppCfg flows that requires application to be specified.
   */
  void validate() {
    if (appEngineWebXml.getAppId() == null) {
      throw new AppEngineConfigException(
          "No app id supplied and XML files have no <application> element");
    }
  }

  /**
   * Validate an application for staging for gcloud which doesn't care if application or version are
   * specified
   */
  void validateForStaging() {
    // don't do anything
  }

  /**
   * Given an AppEngineWebXml, ensure that Flex-specific settings are only present if actually Flex,
   * and that Java11-specific settings are only present if actually Java11.
   *
   * @throws AppEngineConfigException If an option is applied to the wrong runtime.
   */
  void validateRuntime() {
    if (!appEngineWebXml.isFlexible()) {
      if (appEngineWebXml.getNetwork() != null) {
        // validate network properties
        if (appEngineWebXml.getNetwork().getSessionAffinity()) {
          throw new AppEngineConfigException(
              "'session-affinity' is an <env>flex</env> specific " + "field.");
        }

        if (appEngineWebXml.getNetwork().getSubnetworkName() != null
            && !appEngineWebXml.getNetwork().getSubnetworkName().isEmpty()) {
          throw new AppEngineConfigException(
              "'subnetwork-name' is an <env>flex</env> specific " + "field.");
        }

        if (appEngineWebXml.getLivenessCheck() != null) {
          throw new AppEngineConfigException(
              "'liveness-check' is an <env>flex</env> specific " + "field.");
        }

        if (appEngineWebXml.getReadinessCheck() != null) {
          throw new AppEngineConfigException(
              "'readiness-check' is an <env>flex</env> specific " + "field.");
        }
      }
    }
    if (!appEngineWebXml.isJava11OrAbove()) {
      if (appEngineWebXml.getRuntimeChannel() != null) {
        throw new AppEngineConfigException(
            "'runtime-channel' is not valid with this runtime.");
      }
      if (appEngineWebXml.getEntrypoint() != null) {
        throw new AppEngineConfigException(
            "'entrypoint' is not valid with this runtime.");
      }
    }
  }

  private void validateFilterClasses() {
    if (!isJava8OrAbove()) {
      return;
    }
    // Only for Java8*, do not accept appstats filter, as it is not supported.
    for (String filter : webXml.getFilterClasses()) {
      if ("com.google.appengine.tools.appstats.AppstatsFilter".equals(filter)) {
        throw new AppEngineConfigException(
            "AppStats is not supported anymore, please do not include "
                + "appengine-api-labs.jar in your app, and remove the "
                + filter
                + " filter in web.xml.");
      }
    }
  }

  /**
   * Sets the external resource directory. Call this method before invoking {@link
   * #createStagingDirectory(ApplicationProcessingOptions)}.
   *
   * <p>The external resource directory is a directory outside of the war directory where additional
   * files live. These files will be copied into the staging directory during an upload, after the
   * war directory is copied there. Consequently if there are any name collisions the files in the
   * external resource directory will win.
   *
   * @param path a not {@code null} path to an existing directory.
   * @throws IllegalArgumentException If {@code path} does not refer to an existing directory.
   */
  public void setExternalResourceDir(String path) {
    if (path == null) {
      throw new NullPointerException("path is null");
    }
    if (stageDir != null) {
      throw new IllegalStateException(
          "This method must be invoked prior to createStagingDirectory()");
    }
    File dir = new File(path);
    if (!dir.exists()) {
      throw new IllegalArgumentException("path does not exist: " + path);
    }
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException(path + " is not a directory.");
    }
    this.externalResourceDir = dir;
  }

  /**
   * Reads the App Engine application from {@code path}. The path may either be a WAR file or the
   * root of an exploded WAR directory.
   *
   * @param path a not {@code null} path.
   * @param appId if non-null, use this as an application id override.
   * @param module if non-null, use this as a module id override.
   * @param appVersion if non-null, use this as an application version override.
   * @throws IOException if an error occurs while trying to read the {@code Application}.
   * @throws com.google.apphosting.utils.config.AppEngineConfigException if the {@code
   *     Application's} appengine-web.xml file is malformed.
   */
  public static Application readApplication(
      String path, String appId, String module, String appVersion) throws IOException {
    // TODO If path is a WAR file, explode to temporary directory first.
    return new Application(path, appId, module, appVersion, null);
  }

  /**
   * Returns the application identifier, from the AppEngineWebXml config
   *
   * @return application identifier
   */
  @Override
  public String getAppId() {
    return appEngineWebXml.getAppId();
  }

  /**
   * Returns the application version, from the AppEngineWebXml config
   *
   * @return application version
   */
  @Override
  public String getVersion() {
    return appEngineWebXml.getMajorVersionId();
  }

  @Override
  public String getModule() {
    if (appEngineWebXml.getModule() != null) {
      return appEngineWebXml.getModule();

    } else {
      return appEngineWebXml.getService();
    }
  }

  @Override
  public String getInstanceClass() {
    return appEngineWebXml.getInstanceClass();
  }

  @Override
  public boolean isPrecompilationEnabled() {
    return appEngineWebXml.getPrecompilationEnabled();
  }

  @Override
  public List<ErrorHandler> getErrorHandlers() {
    class ErrorHandlerImpl implements ErrorHandler {
      private final AppEngineWebXml.ErrorHandler errorHandler;

      public ErrorHandlerImpl(AppEngineWebXml.ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
      }

      @Override
      public String getFile() {
        return "__static__/" + errorHandler.getFile();
      }

      @Override
      public String getErrorCode() {
        return errorHandler.getErrorCode();
      }

      @Override
      public String getMimeType() {
        return getMimeTypeIfStatic(getFile());
      }
    }
    List<ErrorHandler> errorHandlers = new ArrayList<ErrorHandler>();
    for (AppEngineWebXml.ErrorHandler errorHandler : appEngineWebXml.getErrorHandlers()) {
      errorHandlers.add(new ErrorHandlerImpl(errorHandler));
    }
    return errorHandlers;
  }

  @Override
  public String getMimeTypeIfStatic(String path) {
    if (!path.contains("__static__/")) {
      return null;
    }
    String mimeType = webXml.getMimeTypeForPath(path);
    if (mimeType != null) {
      return mimeType;
    }
    return guessContentTypeFromName(path);
  }

  /**
   * @param fileName path of a file with extension
   * @return the mimetype of the file (or application/octect-stream if not recognized)
   */
  public static String guessContentTypeFromName(String fileName) {
    String defaultValue = "application/octet-stream";
    // now we try first Jetty APY, then special cases, then  FileTypeMap, then  URLConnection
    // All methods we try may return null, and we want to make sure we return the good default
    // non null value.
    try {
      // try first jetty
      String buffer = mimeTypes.getMimeByExtension(fileName);
      if (buffer != null) {
        return buffer;
      }
      // special cases, not handled by Jetty version 6 or the other methods
      String lowerName = fileName.toLowerCase();
      if (lowerName.endsWith(".json")) {
        return "application/json";
      } else if (lowerName.endsWith(".wasm")) {
        return "application/wasm";
      }
      // URLConnection
      String ret = URLConnection.guessContentTypeFromName(fileName);
      if (ret != null) {
        return ret;
      }
      // finally a non null default value
      return defaultValue;
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Error identify mimetype for " + fileName, t);
      return defaultValue;
    }
  }
  /**
   * Returns the AppEngineWebXml describing the application.
   *
   * @return a not {@code null} deployment descriptor
   */
  public AppEngineWebXml getAppEngineWebXml() {
    return appEngineWebXml;
  }

  /**
   * Modified app.yaml for Cloud SDK deployment. This method is not called for App Engine Classic
   * deployment, and called only for the "stage" command. Replaces module to service and clears out
   * the application/version params.
   *
   * @return a not {@code null} deployment descriptor
   */
  public AppEngineWebXml getScrubbedAppEngineWebXml() {
    AppEngineWebXml scrubbedAppEngineWebXml = appEngineWebXml.clone();
    scrubbedAppEngineWebXml.setAppId(null);
    scrubbedAppEngineWebXml.setMajorVersionId(null);
    if (appEngineWebXml.getModule() != null) {
      appEngineWebXml.setService(appEngineWebXml.getModule());
      appEngineWebXml.setModule(null);
    }
    return scrubbedAppEngineWebXml;
  }

  /**
   * Returns the CronXml describing the applications' cron jobs.
   *
   * @return a cron descriptor, possibly empty or {@code null}
   */
  @Override
  public CronXml getCronXml() {
    return cronXml;
  }

  /**
   * Returns the QueueXml describing the applications' task queues.
   *
   * @return a queue descriptor, possibly empty or {@code null}
   */
  @Override
  public QueueXml getQueueXml() {
    return queueXml;
  }

  @Override
  public DispatchXml getDispatchXml() {
    return dispatchXml;
  }

  /**
   * Returns the DosXml describing the applications' DoS entries.
   *
   * @return a dos descriptor, possibly empty or {@code null}
   */
  @Override
  public DosXml getDosXml() {
    return dosXml;
  }

  /**
   * Returns the IndexesXml describing the applications' indexes.
   *
   * @return a index descriptor, possibly empty or {@code null}
   */
  @Override
  public IndexesXml getIndexesXml() {
    return indexesXml;
  }

  /**
   * Returns the WebXml describing the applications' servlets and generic web application
   * information.
   *
   * @return a WebXml descriptor, possibly empty but not {@code null}
   */
  public WebXml getWebXml() {
    return webXml;
  }

  @Override
  public BackendsXml getBackendsXml() {
    return backendsXml;
  }

  /**
   * Returns the desired API version for the current application, or {@code "none"} if no API
   * version was used.
   *
   * @throws IllegalStateException if createStagingDirectory has not been called.
   */
  @Override
  public String getApiVersion() {
    if (apiVersion == null) {
      throw new IllegalStateException("Must call createStagingDirectory first.");
    }
    return apiVersion;
  }

  /**
   * Returns the desired runtime for the current application.
   *
   * @throws IllegalStateException if createStagingDirectory has not been called.
   */
  @Override
  public String getRuntime() {
    if (calculatedRuntime == null) {
      throw new IllegalStateException("Null runtime. Must call createStagingDirectory first.");
    }
    return calculatedRuntime;
  }

  /**
   * Returns a path to an exploded WAR directory for the application. This may be a temporary
   * directory.
   *
   * @return a not {@code null} path pointing to a directory
   */
  @Override
  public String getPath() {
    return baseDir.getAbsolutePath();
  }

  /** Returns the staging directory, or {@code null} if none has been created. */
  @Override
  public File getStagingDir() {
    return stageDir;
  }

  @Override
  public void resetProgress() {
    updateProgress = 0;
    progressAmount = 0;
  }

  /**
   * Gets the effective staging options from global defaults, appengine-web.xml and flags, in
   * ascending order of precedence. For instance, a flag overrides a value of appengine-web.xml.
   *
   * @param opts User-specified options for processing the application.
   * @return StagingOptions a complete object respecting the precedence in assignment
   */
  public StagingOptions getStagingOptions(ApplicationProcessingOptions opts) {
    return StagingOptions.merge(
        opts.getDefaultStagingOptions(),
        appEngineWebXml.getStagingOptions(),
        opts.getStagingOptions());
  }

  /**
   * Creates a new staging directory, if needed, or returns the existing one if already created.
   *
   * @param opts User-specified options for processing the application.
   * @return staging directory
   * @throws IOException
   */
  @Override
  public File createStagingDirectory(ApplicationProcessingOptions opts) throws IOException {
    if (stageDir != null) {
      return stageDir;
    }

    // Java can't atomically make a temp directory, just a file, so let's spin
    // a few times on the off chance someone grabs our filename while we're
    // discarding it to make a replacement directory....
    int i = 0;
    while (stageDir == null && i++ < 3) {
      try {
        stageDir = File.createTempFile(STAGEDIR_PREFIX, null);
      } catch (IOException ex) {
        continue;
      }
      stageDir.delete();
      if (!stageDir.mkdir()) {
        stageDir = null; // try again
      }
    }
    if (i == 3) {
      throw new IOException("Couldn't create a temporary directory in 3 tries.");
    }

    calculatedRuntime = determineRuntime(opts);
    return populateStagingDirectory(opts, /* isStaging= */ false, calculatedRuntime);
  }

  /**
   * Populates and creates (if necessary) a user specified, staging directory
   *
   * @param opts User-specified options for processing the application.
   * @param stagingDir User-specified staging directory (must be empty or not exist)
   * @return staging directory
   * @throws IOException if an error occurs trying to create or populate the staging directory
   */
  @Override
  public File createStagingDirectory(ApplicationProcessingOptions opts, File stagingDir)
      throws IOException {
    if (!stagingDir.exists()) {
      // TODO Should we really be failing if parent doesn't exist, perhaps mkdirs here?
      if (!stagingDir.mkdir()) {
        throw new IOException("Could not create staging directory at " + stagingDir.getPath());
      }
    }

    stageDir = stagingDir;
    // TODO Are soft links okay? I don't know, disable for now
    shouldAttemptSymlink = false;

    calculatedRuntime = determineRuntime(opts);
    populateStagingDirectory(opts, /* isStaging= */ true, calculatedRuntime);

    // copy app.yaml to root
    copyAppYamlToRoot();

    try {
      File classesDir = new File(stagingDir, "WEB-INF/classes");
      if (classesDir.exists() && countClasses(classesDir) > SUGGEST_JAR_THRESHOLD) {
        logger.info(
            "We detected that you have a large number of .class files in WEB-INF/classes."
                + " You may be able to reduce request latency by packaging your .class files into"
                + " jars. To do this, supply <enable-jar-classes>true</enable-jar-classes>"
                + " in the <staging> tag in appengine-web.xml or one of the following methods:"
                + " You can supply the --enable_jar_classes flag when using appcfg on"
                + " command line."
                + " If you're using the Cloud SDK based app-maven-plugin, add"
                + " <stage.enableJarClasses>true</stage.enableJarClasses> in the plugin's"
                + " <configuration> tag."
                + " If you are using the AppCfg based appengine-maven-plugin, supply"
                + " <enableJarClasses>true</enableJarClasses> in the plugin's <configuration> tag."
                + " Note that this flag will put the jar in WEB-INF/lib rather than"
                + " WEB-INF/classes. The classloader first looks in WEB-INF/classes and then"
                + " WEB-INF/lib when loading a class. As a result, this flag could change"
                + " classloading order, which may affect the behavior of your app.");
      }
    } catch (IOException ex) {
      // ignore
    }
    return stageDir;
  }

  /**
   * Copy app.yaml to application root for gcloud based deployments, because it is in the root we
   * want to configure app.yaml to explicitly skip itself from deployments
   */
  private void copyAppYamlToRoot() throws IOException {
    File destinationAppYaml = new File(stageDir, "app.yaml");
    copyOrLinkFile(
        new File(GenerationDirectory.getGenerationDirectory(stageDir), "app.yaml"),
        destinationAppYaml);

    // Process skip files only for Java8 and legacy Web Apps.
    // The Cloud SDK logic cannot determine if a Java11 app is compat or not at this stage.
    if (appEngineWebXml.getRuntime().startsWith(JAVA_8_RUNTIME_ID)
        || appEngineWebXml.getRuntime().startsWith(GOOGLE_LEGACY_RUNTIME_ID)) {
      Files.asCharSink(destinationAppYaml, UTF_8, FileWriteMode.APPEND)
          .write("\nskip_files: app.yaml\n");
    }
  }

  @VisibleForTesting
  static int countClasses(File classesDir) throws IOException {
    ClassCounterVisitor classCounterVisitor = new ClassCounterVisitor();
    java.nio.file.Files.walkFileTree(
        classesDir.toPath(),
        ImmutableSet.of(FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        classCounterVisitor);
    return classCounterVisitor.classCount();
  }

  private boolean isJava8OrAbove() {
    return (appEngineWebXml.getRuntime().startsWith(JAVA_8_RUNTIME_ID)
        || appEngineWebXml.getRuntime().equals(JAVA_11_RUNTIME_ID)
        || appEngineWebXml.getRuntime().startsWith(GOOGLE_LEGACY_RUNTIME_ID));
  }

  private static class ClassCounterVisitor extends SimpleFileVisitor<Path> {
    private int classCount = 0;

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
      if (file.getFileName().toString().endsWith(".class")) {
        classCount++;
      }
      return FileVisitResult.CONTINUE;
    }

    private int classCount() {
      return classCount;
    }
  }

  private File populateStagingDirectory(
      ApplicationProcessingOptions opts,
      boolean isStaging,
      String runtime)
      throws IOException {
    if (runtime.equals("java7")) {
      throw new AppEngineConfigException("GAE Java7 is not supported anymore.");
    }
    File staticDir = new File(stageDir, "__static__");
    staticDir.mkdir();
    copyOrLink(baseDir, stageDir, staticDir, /* forceResource= */ false, opts, runtime);
    if (externalResourceDir != null) {
      // If there is an external resource directory then we now copy its
      // contents to the staging directory. We copy it after the baseDir
      // so that it wins any name collisions.
      String previousPrefix = appEngineWebXml.getSourcePrefix();
      String newPrefix = buildNormalizedPath(externalResourceDir);
      try {
        appEngineWebXml.setSourcePrefix(newPrefix);
        copyOrLink(
            externalResourceDir, stageDir, staticDir, /* forceResource= */ false, opts, runtime);
      } finally {
        appEngineWebXml.setSourcePrefix(previousPrefix);
      }
    }

    // Now determine our API version, and remove the API jars so we don't
    // upload them, except for env:flex where we keep the jar at the user level.
    apiVersion = findApiVersion(stageDir);

    StagingOptions staging = getStagingOptions(opts);

    if (opts.isCompileJspsSet()) {
      compileJsps(stageDir, opts, runtime);
    }

    if (staging.jarClasses().get() && new File(stageDir, "WEB-INF/classes").isDirectory()) {
      zipWebInfClassesFiles(new File(stageDir, "WEB-INF"));
    }

    int maxJarSize = 32000000;
    // We need to split the jars before processing the quickstart logic, so that that logic takes
    // the split jars.
    if (staging.splitJarFiles().get()) {
      splitJars(
          new File(new File(stageDir, "WEB-INF"), "lib"),
          maxJarSize,
          staging.splitJarFilesExcludes().get());
    }

    // must call after compileJsps because that reloads the web.xml
    boolean vm = appEngineWebXml.getUseVm() || appEngineWebXml.isFlexible();
    if (vm) {
      statusUpdate("Warning: Google App Engine Java compat Flexible product is deprecated.");
      statusUpdate("Warning: See https://cloud.google.com/appengine/docs/flexible/java/upgrading");
    }

    boolean isServlet31 = "3.1".equals(servletVersion);
    // Do not create quickstart for Java7 standardapps, even is Servlet 3.1 schema is used.
    // This behaviour is compatible with what was there before supporting Java8, we just now print
    // a warning.
    if (!isJava8OrAbove() && !vm && isServlet31) {
      statusUpdate("Warning: you are using the Java7 runtime with a Servlet 3.1 web.xml file.");
      statusUpdate("The Servlet 3.1 annotations will be ignored and not processed.");
    } else if (opts.isQuickstart() || isServlet31) {
      // Cover Flex compat (deprecated but still there in Java7 or Java8 flavor) and Java8 standard:
      try {
        createQuickstartWebXml(opts);
        webXml =
            new WebXmlReader(stageDir.getAbsolutePath(), "/WEB-INF/min-quickstart-web.xml")
                .readWebXml();
      } catch (SAXException | ParserConfigurationException | TransformerException e) {
        throw new IOException(e);
      }
      if (!webXml.getContextParams().isEmpty()) {
        fallThroughToRuntimeOnContextInitializers();
      }
    }

    // And generate app.yaml string
    appYaml = generateAppYaml(stageDir, runtime, appEngineWebXml);

    // Write prepared {app,backends,index,cron,queue,dos}.yaml files to generation
    // subdirectory within stage directory.
    if (GenerationDirectory.getGenerationDirectory(stageDir).mkdirs()) {
      writePreparedYamlFile(
          "app",
          isStaging ? generateAppYaml(stageDir, runtime, getScrubbedAppEngineWebXml()) : appYaml);
      writePreparedYamlFile("backends", backendsXml == null ? null : backendsXml.toYaml());
      writePreparedYamlFile("index", indexesXml.size() == 0 ? null : indexesXml.toYaml());
      writePreparedYamlFile("cron", cronXml == null ? null : cronXml.toYaml());
      writePreparedYamlFile("queue", queueXml == null ? null : queueXml.toYaml());
      writePreparedYamlFile("dos", dosXml == null ? null : dosXml.toYaml());
      if (isStaging && dispatchXml != null) {
        writePreparedYamlFile("dispatch", dispatchXml.toYaml());
      }
    }

    exportRepoInfoFile();

    return stageDir;
  }

  private void fallThroughToRuntimeOnContextInitializers() {
    // If ServletContextIntializers are used, we need to fall through to
    // the runtime for handling of most of the url patterns.
    // See b/38029573.
    String value = webXml.getContextParams().get("org.eclipse.jetty.containerInitializers");
    if (value == null) {
      return;
    }

    Set<String> initializers = new HashSet<>();
    Matcher matcher = CONTAINER_INITIALIZER_PATTERN.matcher(value);
    boolean foundJasperInitializer = false;
    while (matcher.find()) {
      String containerInitializer = matcher.group(1);
      if ("org.eclipse.jetty.apache.jsp.JettyJasperInitializer".equals(containerInitializer)) {
        foundJasperInitializer = true;
      }
      initializers.add(containerInitializer);
    }
    if (initializers.isEmpty()) {
      return;
    }
    if (initializers.size() == 1 && foundJasperInitializer) {
      // We ignore the default jasper initializer from jetty if this is the only initializer.
      return;
    }

    // There are initializers other than jasper's, so we must fall through to runtime
    // to handle /*.
    webXml.setFallThroughToRuntime(true);
  }

  @Override
  public void exportRepoInfoFile() {
    File target = new File(stageDir, "WEB-INF/classes/source-context.json");
    if (target.exists()) {
      return; // The source context file already exists, nothing to do.
    }

    if (sourceContext == null || sourceContext.getJson() == null) {
      return; // Not a valid git repo
    }

    try {
      // The directory will almost always exist. The mkdirs() addresses a rare corner case (which is
      // hit in tests).
      target.getParentFile().mkdirs();
      Files.asCharSink(target, UTF_8).write(sourceContext.getJson());
    } catch (IOException ex) {
      logger.log(Level.FINE, "Failed to write git repository information file.", ex);
      return; // Failed to generate the source context file.
    }

    statusUpdate("Generated git repository information file.");
  }

  /** Write yaml file to generation subdirectory within stage directory. */
  private void writePreparedYamlFile(String yamlName, String yamlString) throws IOException {
    File f = new File(GenerationDirectory.getGenerationDirectory(stageDir), yamlName + ".yaml");
    if (yamlString != null && f.createNewFile()) {
      Writer fw = Files.newWriter(f, UTF_8);
      fw.write(yamlString);
      fw.close();
    }
  }

  private String findApiVersion(File baseDir) {
    ApiVersionFinder finder = new ApiVersionFinder();

    if (appEngineWebXml.isJava11OrAbove()) {
      return "none";
    }
    if (!appEngineWebXml.isFlexible()) {
      return "user_defined";
    }
    String foundApiVersion = null;
    File webInf = new File(baseDir, "WEB-INF");
    File libDir = new File(webInf, "lib");
    for (File file : new FileIterator(libDir)) {
      if (file.getPath().endsWith(".jar")) {
        try {
          String apiVersion = finder.findApiVersion(file);
          if (apiVersion != null) {
            if (foundApiVersion == null) {
              foundApiVersion = apiVersion;
            } else if (!foundApiVersion.equals(apiVersion)) {
              logger.warning(
                  "Warning: found duplicate API version: "
                      + foundApiVersion
                      + ", using "
                      + apiVersion);
            }
          }
        } catch (IOException ex) {
          logger.log(Level.WARNING, "Could not identify API version in " + file, ex);
        }
      }
    }

    if (foundApiVersion == null) {
      foundApiVersion = "none";
      // We did not find an API jar, and we are on Flex: treat as error (or warning).
      if (!Boolean.getBoolean("com.google.appengine.allow_missing_api_jar")) {
        throw new AppEngineConfigException(
            "GAE Flex compat applications need to depend on "
                + "the GAE API jar, but it was not found in the WEB-INF/lib directory.");
      } else {
        logger.log(Level.WARNING, "Could not find the GAE API jar in the WEB-INF/lib directory.");
      }
    }
    return foundApiVersion;
  }

  /**
   * Returns the runtime id to use in the generated app.yaml.
   *
   * <p>This method returns {@code "java7" or "java8"}, unless an explicit runtime id was specified
   * using the {@code -r} option.
   *
   * <p>Before accepting an explicit runtime id, this method validates it against the list of
   * supported Java runtimes (currently only {@code "java7"}), unless validation was turned off
   * using the {@code --allowAnyRuntimes} option.
   */
  private String determineRuntime(ApplicationProcessingOptions opts) {
    boolean vm = appEngineWebXml.getUseVm() || appEngineWebXml.isFlexible();
    // It is a custom runtime if there is a Dockerfile and vm is true or env is flex:
    if (vm && new File(baseDir, "Dockerfile").exists()) {
      return "custom";
    }
    String runtime = opts.getRuntime();
    if (runtime != null) {
      if (!opts.isAllowAnyRuntime() && !ALLOWED_RUNTIME_IDS.contains(runtime)) {
        throw new AppEngineConfigException("Invalid runtime id: " + runtime);
      }
      return runtime;
    }
    return appEngineWebXml.getRuntime();
  }

  @VisibleForTesting
  String getJSPCClassName() {
    return "com.google.appengine.tools.development.jetty9.LocalJspC";
  }

  private void compileJsps(File stage, ApplicationProcessingOptions opts, String runtime)
      throws IOException {
    statusUpdate("Scanning for jsp files.");

    StagingOptions staging = getStagingOptions(opts);

    if (matchingFileExists(new File(stage.getPath()), JSP_REGEX)) {
      statusUpdate("Compiling jsp files.");

      File webInf = new File(stage, "WEB-INF");

      for (File file : AppengineSdk.getSdk().getUserJspLibFiles()) {
        copyOrLinkFile(file, new File(new File(webInf, "lib"), file.getName()));
      }
      for (File file : AppengineSdk.getSdk().getSharedJspLibFiles()) {
        copyOrLinkFile(file, new File(new File(webInf, "lib"), file.getName()));
      }

      File classes = new File(webInf, "classes");
      File generatedWebXml = new File(webInf, "generated_web.xml");
      // Generate the .class files in a temp dir
      jspJavaFilesGeneratedTempDirectory = Files.createTempDir();
      String classpath = getJspClasspath(classes, getJspJavaFilesGeneratedTempDirectory());

      // We do not want the -compile flag there, as we will compile all the generated files
      // at once, in a second step.
      String[] args = {
        "-classpath",
        classpath,
        "-uriroot",
        stage.getPath(),
        "-p",
        "org.apache.jsp",
        "-l",
        "-v",
        "-webinc",
        generatedWebXml.getPath(),
        "-d",
        getJspJavaFilesGeneratedTempDirectory().getPath(),
        "-javaEncoding",
        staging.compileEncoding().get(),
      };
      if (detailsWriter == null) {
        detailsWriter =
            new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(System.out, Charset.defaultCharset())),
                true);
      }
      URL[] classLoaderUrls = getJspClassPathURLs(classes, getJspJavaFilesGeneratedTempDirectory());
      ClassLoader platformLoader = ClassLoader.getSystemClassLoader().getParent();
      try (URLClassLoader urlClassLoader = new URLClassLoader(classLoaderUrls, platformLoader)) {
        Class<?> jspCompiler = urlClassLoader.loadClass(getJSPCClassName());
        Method main = jspCompiler.getMethod("main", String[].class);
        main.invoke(null, (Object) args);

      } catch (InvocationTargetException e) {
        detailsWriter.println("Error while executing: " + formatCommand(Arrays.asList(args)));
        throw new JspCompilationException(
            "Failed to compile jsp files.", JspCompilationException.Source.JASPER, e.getCause());
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException("Failed to compile jsp files.", e);
      }
      // Now that the Java servlet files from jsp have been generated, we compile them in a single
      // invocation to speed up the deployment process.
      compileJspJavaFiles(
          classpath, webInf, getJspJavaFilesGeneratedTempDirectory(), opts, runtime);

      // Reread the web.xml as it has been modified by Jasper to add the mapping of the generated
      // servlets.
      webXml = new WebXmlReader(stage.getPath()).readWebXml();
    }
  }

  // Access mostly for unit test verifications.
  File getJspJavaFilesGeneratedTempDirectory() {
    return jspJavaFilesGeneratedTempDirectory;
  }

  private void compileJspJavaFiles(
      String classpath,
      File webInf,
      File jspClassDir,
      ApplicationProcessingOptions opts,
      String runtime)
      throws IOException {

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new RuntimeException(
          "Cannot get the System Java Compiler. Please use a JDK, not a JRE.");
    }
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

    ArrayList<File> files = new ArrayList<File>();
    for (File f : new FileIterator(jspClassDir)) {
      if (f.getPath().toLowerCase().endsWith(".java")) {
        files.add(f);
      }
    }
    if (files.isEmpty()) {
      return;
    }
    StagingOptions staging = getStagingOptions(opts);
    List<String> optionList = new ArrayList<String>();
    optionList.addAll(Arrays.asList("-classpath", classpath.toString()));
    optionList.addAll(Arrays.asList("-d", jspClassDir.getPath()));
    optionList.addAll(Arrays.asList("-encoding", staging.compileEncoding().get()));
    // Depending on the runtime, select the correct bytecode target for the jsp classes compilation.
    // If the runtime is unknown and forced (like java9), keep the default settings.
    if (runtime.startsWith(JAVA_8_RUNTIME_ID)) {
      optionList.addAll(Arrays.asList("-source", "8"));
      optionList.addAll(Arrays.asList("-target", "8"));
    } else if (runtime.startsWith(GOOGLE_LEGACY_RUNTIME_ID) || runtime.equals(JAVA_11_RUNTIME_ID)) {
      // TODO: for now, it's still possible to use a JDK8 to compile and deploy Java11
      // apps.
      optionList.addAll(Arrays.asList("-source", "8"));
      optionList.addAll(Arrays.asList("-target", "8"));
    }

    Iterable<? extends JavaFileObject> compilationUnits =
        fileManager.getJavaFileObjectsFromFiles(files);
    boolean success =
        compiler.getTask(null, fileManager, null, optionList, null, compilationUnits).call();
    fileManager.close();

    if (!success) {
      throw new JspCompilationException(
          "Failed to compile the generated JSP java files.", JspCompilationException.Source.JSPC);
    }
    if (staging.jarJsps().get()) {
      zipJasperGeneratedFiles(webInf, jspClassDir);
    } else {
      copyOrLinkDirectories(jspClassDir, new File(webInf, "classes"));
    }
    if (staging.deleteJsps().get()) {
      for (File f : new FileIterator(webInf.getParentFile())) {
        if (f.getPath().toLowerCase().endsWith(".jsp")) {
          f.delete();
        }
      }
    }
  }

  private void zipJasperGeneratedFiles(File webInfDir, File jspClassDir) throws IOException {
    // Create jar files in WEB-INF/lib
    // Don't include the .java files in the jar
    Set<String> fileTypesToExclude = ImmutableSet.of(".java");
    File libDir = new File(webInfDir, "lib");
    JarTool jarTool =
        new JarTool(
            COMPILED_JSP_JAR_NAME_PREFIX,
            jspClassDir,
            libDir,
            MAX_COMPILED_JSP_JAR_SIZE,
            fileTypesToExclude);
    jarTool.run();
    recursiveDelete(jspClassDir);
  }

  private void zipWebInfClassesFiles(File webInfDir) throws IOException {
    // Create jar files in WEB-INF/lib
    File libDir = new File(webInfDir, "lib");
    File classesDir = new File(webInfDir, "classes");
    JarTool jarTool =
        new JarTool(CLASSES_JAR_NAME_PREFIX, classesDir, libDir, MAX_CLASSES_JAR_SIZE, null);
    jarTool.run();
    recursiveDelete(classesDir);
    // And recreate an empty classes directory to be safe.
    classesDir.mkdir();
  }

  private String getJspClasspath(File classDir, File genDir) {
    StringBuilder classpath = new StringBuilder();
    for (URL lib : AppengineSdk.getSdk().getImplLibs()) {
      try {
        classpath.append(Paths.get(lib.toURI()));
      } catch (URISyntaxException e) {
        // We don't expect the exception here, but fall back
        // on getPath just in case.
        classpath.append(lib.getPath());
      }
      classpath.append(File.pathSeparatorChar);
    }
    for (File lib : AppengineSdk.getSdk().getSharedLibFiles()) {
      classpath.append(lib.getPath());
      classpath.append(File.pathSeparatorChar);
    }
    classpath.append(AppengineSdk.getSdk().getToolsApiJarFile().getAbsolutePath());
    classpath.append(File.pathSeparatorChar);

    // Add user classes to classpath
    classpath.append(classDir.getPath());
    classpath.append(File.pathSeparatorChar);
    // Add JSP generated classes to classpath
    classpath.append(genDir.getPath());
    classpath.append(File.pathSeparatorChar);

    for (File f : new FileIterator(new File(classDir.getParentFile(), "lib"))) {
      String filename = f.getPath().toLowerCase();
      if (filename.endsWith(".jar") || filename.endsWith(".zip")) {
        classpath.append(f.getPath());
        classpath.append(File.pathSeparatorChar);
      }
    }

    return classpath.toString();
  }

  private URL[] getJspClassPathURLs(File classDir, File genDir) {
    try {
      List<URL> urls = new ArrayList<>(AppengineSdk.getSdk().getImplLibs());
      for (File lib : AppengineSdk.getSdk().getSharedLibFiles()) {
        urls.add(lib.toURI().toURL());
      }
      urls.add(AppengineSdk.getSdk().getToolsApiJarFile().toURI().toURL());
      // Add user classes to classpath
      urls.add(classDir.toURI().toURL());
      // Add JSP generated classes to classpath
      urls.add(genDir.toURI().toURL());

      for (File f : new FileIterator(new File(classDir.getParentFile(), "lib"))) {
        String filename = f.getPath().toLowerCase();
        if (filename.endsWith(".jar") || filename.endsWith(".zip")) {
          urls.add(f.toURI().toURL());
        }
      }
      return urls.toArray(new URL[urls.size()]);
    } catch (MalformedURLException e) {
      throw new AppEngineConfigException(
          "Failure while creating the JSP compiler classloader URLs.", e);
    }
  }

  private String formatCommand(Iterable<String> args) {
    StringBuilder command = new StringBuilder();
    for (String chunk : args) {
      command.append(chunk);
      command.append(" ");
    }
    return command.toString();
  }

  /**
   * Scans a given directory tree, testing whether any file matches a given pattern.
   *
   * @param dir the directory under which to scan
   * @param regex the pattern to look for
   * @returns Returns {@code true} on the first instance of such a file, {@code false} otherwise.
   */
  private static boolean matchingFileExists(File dir, Pattern regex) {
    for (File file : dir.listFiles()) {
      if (file.isDirectory()) {
        if (matchingFileExists(file, regex)) {
          return true;
        }
      } else {
        if (regex.matcher(file.getName()).matches()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Invokes the JarSplitter code on any jar files found in {@code dir}. Any jars larger than {@code
   * max} will be split into fragments of at most that size.
   *
   * @param dir the directory to search, recursively
   * @param max the maximum allowed size
   * @param excludes a set of suffixes to exclude.
   * @throws IOException on filesystem errors.
   */
  private static void splitJars(File dir, int max, Set<String> excludes) throws IOException {
    String[] children = dir.list();
    if (children == null) {
      return;
    }
    for (String name : children) {
      File subfile = new File(dir, name);
      if (subfile.isDirectory()) {
        splitJars(subfile, max, excludes);
      } else if (name.endsWith(".jar")) {
        if (subfile.length() > max) {
          new JarSplitter(subfile, dir, max, /* replicateManifests= */ false, 4, excludes).run();
          subfile.delete();
        }
      }
    }
  }

  private static final Pattern SKIP_FILES = Pattern.compile("^(.*/)?((#.*#)|(.*~)|(.*/RCS/.*)|)$");

  /**
   * Copies files from the app to the upload staging directory, or makes symlinks instead if
   * supported. Puts the files into the correct places for static vs. resource files, recursively.
   *
   * @param sourceDir application war dir, or on recursion a subdirectory of it
   * @param resDir staging resource dir, or on recursion a subdirectory matching the subdirectory in
   *     {@code sourceDir}
   * @param staticDir staging {@code __static__} dir, or an appropriate recursive subdirectory
   * @param forceResource if all files should be considered resource files
   * @param opts processing options, used primarily for handling of *.jsp files
   */
  private void copyOrLink(
      File sourceDir,
      File resDir,
      File staticDir,
      boolean forceResource,
      ApplicationProcessingOptions opts,
      String runtime)
      throws IOException {

    for (String name : sourceDir.list()) {
      File file = new File(sourceDir, name);

      String path = file.getPath();
      if (File.separatorChar == '\\') {
        path = path.replace('\\', '/');
      }

      if (file.getName().startsWith(".")
          || file.equals(GenerationDirectory.getGenerationDirectory(baseDir))) {
        continue;
      }

      if (file.isDirectory()) {
        if (file.getName().equals("WEB-INF")) {
          copyOrLink(
              file,
              new File(resDir, name),
              new File(staticDir, name),
              /* forceResource= */ true,
              opts,
              runtime);
        } else {
          copyOrLink(
              file,
              new File(resDir, name),
              new File(staticDir, name),
              forceResource,
              opts,
              runtime);
        }
      } else {
        if (SKIP_FILES.matcher(path).matches()) {
          continue;
        }

        if (forceResource
            || appEngineWebXml.includesResource(path)
            || (opts.isCompileJspsSet() && name.toLowerCase().endsWith(".jsp"))) {
          copyOrLinkFile(file, new File(resDir, name));
        }
        if (!forceResource && appEngineWebXml.includesStatic(path)) {
          copyOrLinkFile(file, new File(staticDir, name));
        }
      }
    }
  }

  /**
   * Attempts to symlink a single file, or copies it if symlinking is either unsupported or fails.
   *
   * @param source source file
   * @param dest destination file
   */
  private void copyOrLinkFile(File source, File dest) throws IOException {
    dest.getParentFile().mkdirs();
    // Try to do a symlink, fallback if it fails.
    //
    // Don't try to create a symlink for web.xml, since JSP
    // precompilation will modify it.
    if (shouldAttemptSymlink && !source.getName().endsWith("web.xml")) {

      try {
        // If we are attempting to create a symlink from the external resource
        // directory and we have already created a symlink from the war dir
        // for a file with the same name, then we want to delete the first one
        // or the symlink call will fail.
        dest.delete();
      } catch (Exception e) {
        System.err.println("Warning: We tried to delete " + dest.getPath());
        System.err.println("in order to create a symlink from " + source.getPath());
        System.err.println("but the delete failed with message: " + e.getMessage());
      }

      try {
        java.nio.file.Files.createSymbolicLink(dest.toPath(), source.toPath().toAbsolutePath());
        return;
      } catch (IOException e) {
        System.err.println("Failed to create symlink: " + e.getMessage());
      }
      if (dest.delete()) {
        System.err.println(
            "createSymbolicLink failed but symlink was created, removed: "
                + dest.getAbsolutePath());
      }
    }
    try (FileInputStream inStream = new FileInputStream(source);
        FileOutputStream outStream = new FileOutputStream(dest)) {
      byte[] buffer = new byte[1024];
      int readlen = inStream.read(buffer);
      while (readlen > -1) {
        outStream.write(buffer, 0, readlen);
        readlen = inStream.read(buffer);
      }
    }
  }

  /** Copy (or link) one directory into another one. */
  private void copyOrLinkDirectories(File sourceDir, File destination) throws IOException {
    for (String name : sourceDir.list()) {
      File file = new File(sourceDir, name);
      if (file.isDirectory()) {
        copyOrLinkDirectories(file, new File(destination, name));
      } else {
        copyOrLinkFile(file, new File(destination, name));
      }
    }
  }

  /** deletes the staging directory, if one was created. */
  @Override
  public void cleanStagingDirectory() {
    if (stageDir != null) {
      recursiveDelete(stageDir);
    }
  }

  /** Recursive directory deletion. */
  public static void recursiveDelete(File dead) {
    String[] files = dead.list();
    if (files != null) {
      for (String name : files) {
        recursiveDelete(new File(dead, name));
      }
    }
    dead.delete();
  }

  @Override
  public void setListener(UpdateListener l) {
    listener = l;
  }

  @Override
  public void setDetailsWriter(PrintWriter detailsWriter) {
    this.detailsWriter = detailsWriter;
  }

  @Override
  public void statusUpdate(String message, int amount) {
    updateProgress += progressAmount;
    if (updateProgress > 99) {
      updateProgress = 99;
    }
    progressAmount = amount;
    if (listener != null) {
      listener.onProgress(new UpdateProgressEvent(Thread.currentThread(), message, updateProgress));
    }
  }

  @Override
  public void statusUpdate(String message) {
    int amount = progressAmount / 4;
    updateProgress += amount;
    if (updateProgress > 99) {
      updateProgress = 99;
    }
    progressAmount -= amount;
    if (listener != null) {
      listener.onProgress(new UpdateProgressEvent(Thread.currentThread(), message, updateProgress));
    }
  }

  private String generateAppYaml(File stageDir, String runtime, AppEngineWebXml aeWebXml) {
    Set<String> staticFiles = new HashSet<String>();
    for (File f : new FileIterator(new File(stageDir, "__static__"))) {
      staticFiles.add(Utility.calculatePath(f, stageDir));
    }

    AppYamlTranslator translator =
        new AppYamlTranslator(
            aeWebXml,
            getWebXml(),
            getBackendsXml(),
            getApiVersion(),
            staticFiles,
            null,
            runtime,
            getSdkVersion());
    String yaml = translator.getYaml();
    logger.fine("Generated app.yaml file:\n" + yaml);
    return yaml;
  }

  /**
   * Returns the app.yaml string.
   *
   * @throws IllegalStateException if createStagingDirectory has not been called.
   */
  @Override
  public String getAppYaml() {
    if (appYaml == null) {
      throw new IllegalStateException("Must call createStagingDirectory first.");
    }
    return appYaml;
  }

  private void writeDefaultWebXml(File webXmlFile) {
    try {
      Files.asCharSink(webXmlFile, UTF_8).write(DEFAULT_WEB_XML_CONTENT);
    } catch (IOException e) {
      String message =
          "Error encountered when attempting to generate file " + webXmlFile.getAbsolutePath();
      if (!webXmlFile.getParentFile().exists()) {
        message =
            "Could not find the WEB-INF directory in "
                + webXmlFile.getParent()
                + ". The given application file path must point to an exploded WAR directory that"
                + " contains a WEB-INF directory.";
      }
      throw new AppEngineConfigException(message, e);
    }
  }

  /**
   * Generates a quickstart-web.xml. Minimizes and saves in min-quickstart-web.xml
   *
   * @return Relative path to min-quickstart-web.xml
   */
  private void createQuickstartWebXml(ApplicationProcessingOptions opts)
      throws IOException, SAXException, ParserConfigurationException, TransformerException {
    String javaCmd = opts.getJavaExecutable().getPath();
    AppengineSdk.WebDefaultXmlType jettyVersion;
    boolean notGAEStandard = appEngineWebXml.getUseVm() || appEngineWebXml.isFlexible();
    if (notGAEStandard) {
      throw new AppEngineConfigException(
          "Google App Engine Flex or Managed VM runtime is not supported anymore.");
    } else {
      // GAE Standard with servlet 3.1 (and Java8 or Java11).
      if (!isJava8OrAbove()) {
        throw new AppEngineConfigException(
            "Servlet 3.1 annotations processing is only supported with Java8 runtime."
                + " Please downgrade the servlet version to 2.5 in the web.xml file.");
      }
      jettyVersion = AppengineSdk.WebDefaultXmlType.JETTY93_STANDARD;
    }
    String quickstartClassPath = AppengineSdk.getSdk().getQuickStartClasspath(jettyVersion);
    File webDefaultXml = new File(AppengineSdk.getSdk().getWebDefaultXml(jettyVersion));
    String[] args = {
      javaCmd,
      "-cp",
      quickstartClassPath,
      "com.google.appengine.tools.development.jetty9.QuickStartGenerator",
      stageDir.getAbsolutePath(),
      webDefaultXml.getAbsolutePath()
    };
    Process quickstartProcess = Utility.startProcess(detailsWriter, args);
    int status;
    try {
      status = quickstartProcess.waitFor();
    } catch (InterruptedException ex) {
      status = 1;
    }

    if (status != 0) {
      detailsWriter.println("Error while executing: " + formatCommand(Arrays.asList(args)));
      throw new RuntimeException("Failed to generate quickstart-web.xml.");
    }

    File quickstartXml = new File(stageDir, "/WEB-INF/quickstart-web.xml");
    File minimizedQuickstartXml = new File(stageDir, "/WEB-INF/min-quickstart-web.xml");

    Document quickstartDoc =
        getFilteredQuickstartDoc(!notGAEStandard, quickstartXml, webDefaultXml);

    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    StreamResult result = new StreamResult(Files.newWriter(minimizedQuickstartXml, UTF_8));
    DOMSource source = new DOMSource(quickstartDoc);
    transformer.transform(source, result);
  }

  /**
   * Removes mappings from quickstart-web.xml that come from webdefault.xml.
   *
   * <p>The quickstart-web.xml generated by the quickstartgenerator process includes the contents of
   * the user's web.xml, entries derived from Java annotations, and entries derived from the
   * contents of webdefault.xml. All of those are appropriate for the Java web server. But when
   * generating an app.yaml for appcfg or dev_appserver, the webdefault.xml entries are not
   * appropriate, since app.yaml should only reflect what is specific to the user's app. So this
   * method returns a modified min-quickstart-web Document from which the webdefault.xml entries
   * have been removed. Specifically, we look at <servlet-mapping>, <filter-mapping>, <welcome-file>
   * and <web-resource-name> elements in webdefault.xml; then we look at those elements inside
   * quickstart-web.xml and remove them from it. welcome-file needs treatement only for Flex of VM,
   * since we compile the JSPs at the server level.
   *
   * @return a filtered quickstart Document object appropriate for translation to app.yaml
   */
  static Document getFilteredQuickstartDoc(
      boolean isGAEStandard, File quickstartXml, File webDefaultXml)
      throws ParserConfigurationException, IOException, SAXException {

    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder webDefaultDocBuilder = docBuilderFactory.newDocumentBuilder();
    Document webDefaultDoc = webDefaultDocBuilder.parse(webDefaultXml);
    DocumentBuilder quickstartDocBuilder = docBuilderFactory.newDocumentBuilder();
    Document quickstartDoc = quickstartDocBuilder.parse(quickstartXml);

    if (isGAEStandard) {
      // Remove from quickstartDoc all "welcome-file" defined in webDefaultDoc.
      removeNodes(webDefaultDoc, quickstartDoc, "welcome-file", 0);
      // Remove from quickstartDoc all parents of "servlet-name" defined in webDefaultDoc:
      removeNodes(webDefaultDoc, quickstartDoc, "servlet-name", 1);
      // Remove from quickstartDoc all parents of "filter-name" defined in webDefaultDoc:
      removeNodes(webDefaultDoc, quickstartDoc, "filter-name", 1);
      // Remove from quickstartDoc all grand-parents of "web-resource-name" defined in
      // webDefaultDoc, for example we remove this entire section for deferred_queue:
      // <security-constraint>
      //     <web-resource-collection>
      //      <web-resource-name>deferred_queue</web-resource-name>
      //      <url-pattern>/_ah/queue/__deferred__</url-pattern>
      //   </web-resource-collection>
      //   <auth-constraint>
      //     <role-name>admin</role-name>
      //   </auth-constraint>
      // </security-constraint>
      removeNodes(webDefaultDoc, quickstartDoc, "web-resource-name", 2);

      return quickstartDoc;
    }

    // For Flex or vm:true, we keep the current processing:
    final Set<String> tagsToExamine = ImmutableSet.of("filter-mapping", "servlet-mapping");
    final String urlPatternTag = "url-pattern";

    Set<String> defaultRoots = Sets.newHashSet();
    List<Node> nodesToRemove = Lists.newArrayList();

    webDefaultDoc.getDocumentElement().normalize();
    NodeList webDefaultChildren =
        webDefaultDoc.getDocumentElement().getElementsByTagName(urlPatternTag);
    for (int i = 0; i < webDefaultChildren.getLength(); i++) {
      Node child = webDefaultChildren.item(i);
      if (tagsToExamine.contains(child.getParentNode().getNodeName())) {
        String url = child.getTextContent().trim();
        if (url.startsWith("/")) {
          defaultRoots.add(url);
        }
      }
    }

    quickstartDoc.getDocumentElement().normalize();
    NodeList quickstartChildren =
        quickstartDoc.getDocumentElement().getElementsByTagName(urlPatternTag);
    for (int i = 0; i < quickstartChildren.getLength(); i++) {
      Node child = quickstartChildren.item(i);
      if (tagsToExamine.contains(child.getParentNode().getNodeName())) {
        String url = child.getTextContent().trim();
        if (defaultRoots.contains(url)) {
          nodesToRemove.add(child.getParentNode());
        }
      }
    }
    for (Node node : nodesToRemove) {
      quickstartDoc.getDocumentElement().removeChild(node);
    }

    return quickstartDoc;
  }

  /**
   * All nodes with the given tagName in webDefaultDoc that are also present in quickstartDoc, are
   * removed from quickstartDoc. The removal happens on the parent level of the node (which can be 0
   * for 'welcome-file', 1 for 'filter-name' or 'servlet-name', or 2 for 'web-resource-name').
   */
  private static void removeNodes(
      Document webDefaultDoc, Document quickstartDoc, String tagName, int parentLevel) {
    NodeList nodes = webDefaultDoc.getDocumentElement().getElementsByTagName(tagName);
    Set<String> namesToDelete = Sets.newHashSet();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node child = nodes.item(i);
      namesToDelete.add(child.getTextContent().trim());
    }

    // If tagName is "web-resource-name" and webdefault.xml contains
    // <web-resource-name>deferred_queue</web-resource-name>
    // then namesToDelete contains "deferred_queue".
    // Now iterate over quickstart-web.xml to find <web-resource-name> elements that
    // match the ones we saw in webdefault.xml, and remove them.
    NodeList nodesResult = quickstartDoc.getDocumentElement().getElementsByTagName(tagName);
    List<Node> nodesToRemove = Lists.newArrayList();
    for (int i = 0; i < nodesResult.getLength(); i++) {
      Node child = nodesResult.item(i);
      if (namesToDelete.contains(child.getTextContent().trim())) {
        Node nodeToRemove = child;
        for (int p = 0; p < parentLevel; p++) {
          nodeToRemove = nodeToRemove.getParentNode();
        }
        nodesToRemove.add(nodeToRemove);
      }
    }
    for (Node node : nodesToRemove) {
      node.getParentNode().removeChild(node);
    }
  }
}
