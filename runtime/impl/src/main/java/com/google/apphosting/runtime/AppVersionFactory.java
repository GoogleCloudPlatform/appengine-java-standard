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

package com.google.apphosting.runtime;

import static java.nio.file.FileVisitOption.FOLLOW_LINKS;

import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.AppId;
import com.google.apphosting.base.AppVersionKey;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXmlReader;
import com.google.apphosting.utils.config.ClassPathBuilder;
import com.google.auto.value.AutoBuilder;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.flogger.GoogleLogger;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * {@code AppVersionFactory} constructs instances of {@code
 * AppVersion}.  It contains all of the logic about how application
 * resources are laid out on the filesystem, and is responsible for
 * constructing a {@link ClassLoader} that can be used to load
 * resources and classes for a particular application.
 *
 */
public class AppVersionFactory {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  // The 32-bit Java7 spawner doesn't set either GAE_ENV or GAE_RUNTIME. When the following
  // system property is set to true, the runtime will default GAE_ENV to "standard" and
  // GAE_RUNTIME to the value in the appinfo.
  private static final boolean USE_DEFAULT_VALUES_FOR_GAE_ENV_VARS = Boolean.getBoolean(
      "com.google.appengine.runtime.use.default.values.for.gae.env.vars");

  // Environment variables set by the appserver.
  private static final String GAE_ENV = "GAE_ENV";
  private static final String GAE_RUNTIME = "GAE_RUNTIME";

  public static Builder builder() {
    return new AutoBuilder_AppVersionFactory_Builder();
  }

  public static Builder builderForTest() {
    return builder()
        .setDefaultToNativeUrlStreamHandler(true)
        .setForceUrlfetchUrlStreamHandler(false)
        .setIgnoreDaemonThreads(true)
        .setUseEnvVarsFromAppInfo(false)
        .setFixedApplicationPath(null);
  }

  /** Builder for AppVersionFactory. */
  @AutoBuilder
  public abstract static class Builder {
    /** The root directory where all application versions are persisted. */
    public abstract Builder setSandboxPlugin(NullSandboxPlugin x);

    public abstract Builder setSharedDirectory(File x);

    /** The runtime version which is reported to users. */
    public abstract Builder setRuntimeVersion(String x);

    public abstract Builder setDefaultToNativeUrlStreamHandler(boolean x);

    public abstract Builder setForceUrlfetchUrlStreamHandler(boolean x);

    public abstract Builder setIgnoreDaemonThreads(boolean x);

    public abstract Builder setUseEnvVarsFromAppInfo(boolean x);

    public abstract Builder setFixedApplicationPath(String x);

    public abstract AppVersionFactory build();
  }

  private final NullSandboxPlugin sandboxPlugin;

  /**
   * The root directory for application versions.  All other paths are
   * relative to this directory.
   */
  private final File sharedDirectory;

  private final String runtimeVersion;

  private final boolean defaultToNativeUrlStreamHandler;

  private final boolean forceUrlfetchUrlStreamHandler;

  private final boolean ignoreDaemonThreads;

  private final boolean useEnvVarsFromAppInfo;

  private final String fixedApplicationPath;

  /** Construct a new {@code AppVersionFactory}. */
  public AppVersionFactory(
      NullSandboxPlugin sandboxPlugin,
      File sharedDirectory,
      String runtimeVersion,
      boolean defaultToNativeUrlStreamHandler,
      boolean forceUrlfetchUrlStreamHandler,
      boolean ignoreDaemonThreads,
      boolean useEnvVarsFromAppInfo,
      @Nullable String fixedApplicationPath) {
    this.sandboxPlugin = sandboxPlugin;
    this.sharedDirectory = sharedDirectory;
    this.runtimeVersion = runtimeVersion;
    this.defaultToNativeUrlStreamHandler = defaultToNativeUrlStreamHandler;
    this.forceUrlfetchUrlStreamHandler = forceUrlfetchUrlStreamHandler;
    this.ignoreDaemonThreads = ignoreDaemonThreads;
    this.useEnvVarsFromAppInfo = useEnvVarsFromAppInfo;
    this.fixedApplicationPath = fixedApplicationPath;
  }

  public AppEngineWebXml readAppEngineWebXml(AppInfo appInfo) throws FileNotFoundException {
    File rootDirectory = getRootDirectory(appInfo);
    AppEngineWebXmlReader reader =
        new AppEngineWebXmlReader(rootDirectory.getPath()) {
          @Override
          protected boolean allowMissingThreadsafeElement() {
            // There are many apps deployed in production that don't have a threadsafe
            // element, so to avoid breaking apps we allow the missing element.
            return true;
          }
        };
    AppEngineWebXml appEngineWebXml = reader.readAppEngineWebXml();
    logger.atFine().log("Loaded appengine-web.xml: %s", appEngineWebXml);
    return appEngineWebXml;
  }

  /**
   * Create an {@code AppVersion} from the specified {@code AppInfo} protocol buffer.
   *
   * @param appInfo The application configuration.
   * @param configuration The runtime configuration for the application.
   * @throws FileNotFoundException if any of the specified files cannot be found.
   * @throws IOException if there is a problem verifying that the root directory from
   *     {@code appInfo} is already the current directory.
   */
  public AppVersion createAppVersion(
      AppInfo appInfo,
      AppEngineWebXml appEngineWebXml,
      ApplicationEnvironment.RuntimeConfiguration configuration)
      throws IOException {
    AppVersionKey appVersionKey = AppVersionKey.fromAppInfo(appInfo);

    File rootDirectory = getRootDirectory(appInfo);
    logger.atFine().log("Loaded appengine-web.xml: %s", appEngineWebXml);
    Map<String, String> sysProps = createSystemProperties(appEngineWebXml, appInfo);
    Map<String, String> envVars = createEnvironmentVariables(appEngineWebXml, appInfo);

    ThreadGroup rootThreadGroup = new ThreadGroup("App Engine: " + appVersionKey);

    FileEncodingSetter.set(sysProps);

    // Pull in Mail API system properties
    String supportExtendedAttachments = System.getProperty(
        "appengine.mail.supportExtendedAttachmentEncodings");
    if (supportExtendedAttachments != null) {
      // b/23225723
      sysProps.put("appengine.mail.supportExtendedAttachmentEncodings",
                   supportExtendedAttachments);
    }

    String forceCloudsqlReadahead = System.getProperty(
        "appengine.jdbc.forceReadaheadOnCloudsqlSocket");
    if (forceCloudsqlReadahead != null) {
      sysProps.put("appengine.jdbc.forceReadaheadOnCloudsqlSocket",
                   forceCloudsqlReadahead);
    }

    String preventInlining = System.getProperty("appengine.mail.filenamePreventsInlining");
    if (preventInlining != null) {
      // b/19910938
      sysProps.put("appengine.mail.filenamePreventsInlining", preventInlining);
    }

    ApplicationEnvironment environment =
        new ApplicationEnvironment(
            appInfo.getAppId(),
            appInfo.getVersionId(),
            sysProps,
            envVars,
            rootDirectory,
            configuration);

    String urlStreamHandlerType = appEngineWebXml.getUrlStreamHandlerType();
    if (urlStreamHandlerType == null && defaultToNativeUrlStreamHandler) {
      urlStreamHandlerType = AppEngineWebXml.URL_HANDLER_NATIVE;
    }
    if (forceUrlfetchUrlStreamHandler) {
      urlStreamHandlerType = AppEngineWebXml.URL_HANDLER_URLFETCH;
    }
    boolean useNative = NetworkServiceDiverter.useNativeUrlStreamHandler(urlStreamHandlerType);
    if (!useNative) {
      URL.setURLStreamHandlerFactory(new StreamHandlerFactory());
    }

    // Pull in URLFetch system properties (b/10429975):
    String deadline = sysProps.get(URLFetchService.DEFAULT_DEADLINE_PROPERTY);
    if (deadline != null) {
      try {
        Double.parseDouble(deadline);
      } catch (NumberFormatException e) {
        logger.atInfo().log(
            "Invalid value for %s property", URLFetchService.DEFAULT_DEADLINE_PROPERTY);
      }
      System.setProperty(URLFetchService.DEFAULT_DEADLINE_PROPERTY, deadline);
    }

    ClassLoader classLoader =
        createClassLoader(environment, rootDirectory, appInfo, appEngineWebXml);
    SessionsConfig sessionsConfig =
        new SessionsConfig(
            appEngineWebXml.getSessionsEnabled(),
            appEngineWebXml.getAsyncSessionPersistence(),
            appEngineWebXml.getAsyncSessionPersistenceQueueName());
    UncaughtExceptionHandler uncaughtExceptionHandler =
        (thread, ex) -> logger.atWarning().withCause(ex).log("Uncaught exception from %s", thread);
    ThreadGroupPool threadGroupPool =
        ThreadGroupPool.builder()
            .setParentThreadGroup(rootThreadGroup)
            .setThreadGroupNamePrefix("Request #")
            .setUncaughtExceptionHandler(uncaughtExceptionHandler)
            .setIgnoreDaemonThreads(ignoreDaemonThreads)
            .build();
    suppressJaxbWarningReflectionIsNotAllowed(classLoader);
    setApplicationDirectory(rootDirectory.getAbsolutePath());
    return AppVersion.builder()
        .setAppVersionKey(appVersionKey)
        .setAppInfo(appInfo)
        .setRootDirectory(rootDirectory)
        .setClassLoader(classLoader)
        .setEnvironment(environment)
        .setSessionsConfig(sessionsConfig)
        .setPublicRoot(appEngineWebXml.getPublicRoot())
        .setThreadGroupPool(threadGroupPool)
        .build();
  }

  /**
   * Creates a new {@code AppVersion} with a default RuntimeConfiguration.
   *
   * @param appInfo The application configuration.
   * @throws FileNotFoundException if appengine-web.xml cannot be read.
   * @throws IOException if there is a problem verifying that the root directory from
   *     {@code appInfo} is already the current directory.
   */
  public AppVersion createAppVersionForTest(AppInfo appInfo) throws IOException {
    AppEngineWebXml appEngineWebXml = readAppEngineWebXml(appInfo);
    return createAppVersion(
        appInfo, appEngineWebXml, ApplicationEnvironment.RuntimeConfiguration.DEFAULT_FOR_TEST);
  }

  private File getRootDirectory(AppInfo appInfo) throws FileNotFoundException {
    if (Strings.isNullOrEmpty(fixedApplicationPath)) {
      AppVersionKey appVersionKey = AppVersionKey.fromAppInfo(appInfo);
      return getRootDirectory(appVersionKey);
    }
    File rootDirectory = new File(fixedApplicationPath);
    if (!rootDirectory.isDirectory()) {
      throw new FileNotFoundException(
          "Application directory not found or is not a directory: " + fixedApplicationPath);
    }
    return rootDirectory;
  }

  /**
   * Return the top-level directory for the specified application
   * version.  The directory returned will be an absolute path beneath
   * {@code sharedDirectory}.
   *
   * @throws FileNotFoundException If the directory that would be
   * returned does not exist.
   */
  private File getRootDirectory(AppVersionKey appVersionKey) throws FileNotFoundException {
    // N.B.: Don't check to see if any directories above
    // this one exist -- we don't have permission to stat them.
    File root =
        new File(new File(sharedDirectory, appVersionKey.getAppId()), appVersionKey.getVersionId());

    if (!root.isDirectory()) {
      throw new FileNotFoundException(root.toString());
    }
    return root.getAbsoluteFile();
  }

  /**
   * Creates the system properties that will be seen by the user
   * application.  This is a combination of properties that they've
   * requested (via {@code appengine-web.xml}), information about the
   * runtime, information about the application, and information about
   * this particular JVM instance.
   */
  private Map<String, String> createSystemProperties(
      AppEngineWebXml appEngineWebXml, AppInfo appInfo) {
    Map<String, String> props = new HashMap<>();
    props.putAll(appEngineWebXml.getSystemProperties());
    props.put(
        SystemProperty.environment.key(), SystemProperty.Environment.Value.Production.value());
    props.put(SystemProperty.version.key(), runtimeVersion);
    AppId appId = AppId.parse(appInfo.getAppId());
    props.put(SystemProperty.applicationId.key(), appId.getLongAppId());
    props.put(SystemProperty.applicationVersion.key(), appInfo.getVersionId());

    return props;
  }

  /**
   * Creates the environment variables that will be seen by the application.
   * The resulting map must be unmodifiable.
   */
  private Map<String, String> createEnvironmentVariables(AppEngineWebXml appEngineWebXml,
      AppInfo appInfo) {
    Map<String, String> envVars = new HashMap<>();
    envVars.putAll(appEngineWebXml.getEnvironmentVariables());
    if (useEnvVarsFromAppInfo) {
      // We add env vars from AppInfo on top of those from appengine-web.xml because
      // for a long time Java appcfg was not correctly populating the env_variables
      // section in the generated app.yaml (see b/79371098), meaning they were missing
      // from AppInfos of deployed apps.
      for (AppInfo.EnvironmentVariable envVar : appInfo.getEnvironmentVariableList()) {
        envVars.put(envVar.getName(), envVar.getValue());
      }
    }
    String gaeEnv = System.getenv(GAE_ENV);
    if (USE_DEFAULT_VALUES_FOR_GAE_ENV_VARS && gaeEnv == null) {
      gaeEnv = "standard";
    }
    if (gaeEnv != null) {
      envVars.putIfAbsent(GAE_ENV, gaeEnv);
    }
    String gaeRuntime = System.getenv(GAE_RUNTIME);
    if (USE_DEFAULT_VALUES_FOR_GAE_ENV_VARS && gaeRuntime == null) {
      gaeRuntime = appInfo.getRuntimeId();
    }
    if (gaeRuntime != null) {
      envVars.putIfAbsent(GAE_RUNTIME, gaeRuntime);
    }
    return Collections.unmodifiableMap(envVars);
  }

  /**
   * Create a {@link ClassLoader} that loads resources from the application version specified in
   * {@code appInfo}.
   *
   * @throws IOException If any files specified in {@code appInfo} do not exist on the filesystem.
   */
  private ClassLoader createClassLoader(
      ApplicationEnvironment environment,
      File root,
      AppInfo appInfo,
      AppEngineWebXml appEngineWebXml)
      throws IOException {
    ClassPathUtils classPathUtils = sandboxPlugin.getClassPathUtils();

    ClassPathBuilder classPathBuilder =
        new ClassPathBuilder(appEngineWebXml.getClassLoaderConfig());

    // From the servlet spec, SRV.9.5 "The Web application class loader must load
    // classes from the WEB-INF/ classes directory first, and then from library JARs
    // in the WEB-INF/lib directory."
    try {
      File classes = new File(new File(root, "WEB-INF"), "classes");
      if (classes.isDirectory()) {
        classPathBuilder.addClassesUrl(classes.toURI().toURL());
      }
    } catch (MalformedURLException ex) {
      logger.atWarning().withCause(ex).log("Could not add WEB-INF/classes");
    }

    // If there is an API version specified in the AppInfo, then the
    // user code does not include the API code and we need to append
    // our own copy of the requested API version.
    //
    // N.B.: The API version jar should come after
    // WEB-INF/classes (to avoid violating the servlet spec) but
    // before other WEB-INF/lib jars (in case the user is including
    // appengine-tools-api.jar or something else superfluous).
    String apiVersion = appInfo.getApiVersion();
    if (!apiVersion.isEmpty() && !Ascii.equalsIgnoreCase(apiVersion, "none")) {
      // For now, the only valid version has been "1.0" since the beginning of App Engine.

      if (classPathUtils == null) {
        logger.atInfo().log("Ignoring API version setting %s", apiVersion);
      } else {
        File apiJar = classPathUtils.getApiJarForVersion(apiVersion);
        if (apiJar != null) {
          logger.atInfo().log("Adding API jar %s for version %s", apiJar, apiVersion);
          try {
            classPathBuilder.addAppengineJar(new URL("file", "", apiJar.getAbsolutePath()));
          } catch (MalformedURLException ex) {
            logger.atWarning().withCause(ex).log("Could not parse URL for %s, ignoring.", apiJar);
          }

          File appengineApiLegacyJar = classPathUtils.getAppengineApiLegacyJar();
          if (appengineApiLegacyJar != null) {
            logger.atInfo().log("Adding appengine-api-legacy jar %s", appengineApiLegacyJar);
            try {
              // Add appengine-api-legacy jar with appengine-api-jar priority.
              classPathBuilder.addAppengineJar(
                  new URL("file", "", appengineApiLegacyJar.getAbsolutePath()));
            } catch (MalformedURLException ex) {
              logger.atWarning().withCause(ex).log(
                  "Could not parse URL for %s, ignoring.", appengineApiLegacyJar);
            }
          }
        } else {
          // TODO: We should probably return an
          // UPResponse::UNKNOWN_API_VERSION here, but I'd like to be
          // lenient until API versions are well established.
          logger.atWarning().log(
              "The Java runtime is not adding an API jar for this application, as the "
                  + "Java api_version defined in app.yaml or appinfo is unknown: %s",
              apiVersion);
        }
      }
    }
    if (!appInfo.getFileList().isEmpty()) {
      for (AppInfo.File appFile : appInfo.getFileList()) {
        File file = new File(root, appFile.getPath());
        // _ah*.jar are jars for classes or jsps from the classes directory.
        // Treat them as if they are from WEB-INF/classes, per servlet specification.
        if (appFile.getPath().startsWith("WEB-INF/lib/_ah")) {
          try {
            classPathBuilder.addClassesUrl(new URL("file", "", file.getAbsolutePath()));
          } catch (MalformedURLException ex) {
            logger.atWarning().withCause(ex).log("Could not get URL for file: %s", file);
          }
        } else if (appFile.getPath().startsWith("WEB-INF/lib/")) {
          try {
            classPathBuilder.addAppJar(new URL("file", "", file.getAbsolutePath()));
          } catch (MalformedURLException ex) {
            logger.atWarning().withCause(ex).log("Could not get URL for file: %s", file);
          }
        }
      }
    } else {
      Path pathToLib = FileSystems.getDefault().getPath(root.getAbsolutePath(), "WEB-INF", "lib");
      if (pathToLib.toFile().isDirectory()) {
        // Search for jar regular files only under 1 level under WEB-INF/lib
        // FOLLOW_LINKS is there in case WEB-INF/lib is itself a symbolic link.
        try (Stream<Path> stream = Files.walk(pathToLib, 1, FOLLOW_LINKS)) {
          stream
              .filter(Files::isRegularFile)
              .forEach(
                  path -> {

                    // _ah*.jar are jars for classes or jsps from the classes directory.
                    // Treat them as if they are from WEB-INF/classes, per servlet specification.
                    if (path.getFileName().toString().startsWith("_ah")) {
                      try {
                        classPathBuilder.addClassesUrl(new URL("file", "", path.toString()));
                      } catch (MalformedURLException ex) {
                        logger.atWarning().withCause(ex).log(
                            "Could not get URL for file: %s", path);
                      }
                    } else {
                      try {
                        classPathBuilder.addAppJar(new URL("file", "", path.toString()));
                      } catch (MalformedURLException ex) {
                        logger.atWarning().withCause(ex).log(
                            "Could not get URL for file: %s", path);
                      }
                    }
                  });
        }
      }
    }

    switch (appEngineWebXml.getUseGoogleConnectorJ()) {
      case NOT_STATED_BY_USER:
        environment.setUseGoogleConnectorJ(null);
        break;
      case TRUE:
        environment.setUseGoogleConnectorJ(true);
        break;
      case FALSE:
        environment.setUseGoogleConnectorJ(false);
        break;
    }

    return sandboxPlugin.createApplicationClassLoader(getUrls(classPathBuilder), root, environment);
  }

  private URL[] getUrls(ClassPathBuilder classPathBuilder) {
    URL[] urls = classPathBuilder.getUrls();
    String message = classPathBuilder.getLogMessage();
    if (!message.isEmpty()) {
      // Log to the user's logs.
      ApiProxy.log(
          new ApiProxy.LogRecord(
              ApiProxy.LogRecord.Level.warn, System.currentTimeMillis() * 1000, message));
    }
    return urls;
  }

  /**
   * Suppresses the warning that JAXB logs when reflection is not allowed on
   * a field.
   * Most annoyingly, the warning is logged the first time that a JAX-WS service
   * class is instantiated, due to the private fields in the
   * {@code javax.xml.ws.wsaddressing.W3CEndpointReference} class.
   * Since this warning is only logged once, irrespective of the number of class/field
   * combinations for which reflection fails, there is little that is lost in
   * suppressing it. See b/5609065 for more information.
   *
   * @param classLoader the application ClassLoader
   */
  private void suppressJaxbWarningReflectionIsNotAllowed(ClassLoader classLoader) {
    // Suppressing the warning is only meaningful in runtimes that install a security manager.
    if (System.getSecurityManager() != null) {
      try {
        // Must use reflection here because the JAXB implementation classes are
        // on the application classpath.
        Class<?> accessorClass =
            classLoader.loadClass("com.sun.xml.bind.v2.runtime.reflect.Accessor");
        Field accessWarned = accessorClass.getDeclaredField("accessWarned");
        accessWarned.setAccessible(true);
        accessWarned.setBoolean(null, true);
      } catch (Exception e) {
        logger.atWarning().withCause(e).log("failed to suppress JAXB warning reflectively");
      }
    }
  }

  private static void setApplicationDirectory(String path) throws IOException {
    // Set the (real) "user.dir" system property to the application directory,
    // so that calls like File.getAbsolutePath() will return the expected path
    // for application files.
    System.setProperty("user.dir", path);
    if (!Boolean.getBoolean("com.google.apphosting.runtime.disableChdir")) {
      NullSandboxPlugin.chdir(path);

      File gotCwd = new File(".").getCanonicalFile();
      File wantCwd = new File(path).getCanonicalFile();
      if (!gotCwd.equals(wantCwd)) {
        logger.atWarning().log("Want current directory to be %s but it is %s", wantCwd, gotCwd);
      }
    }
  }
}
