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

import com.google.appengine.tools.development.EnvironmentVariableChecker.MismatchReportingPolicy;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.apphosting.utils.config.AppEngineWebXml.ScalingType;
import com.google.apphosting.utils.config.BackendsXml;
import com.google.apphosting.utils.config.BackendsXmlReader;
import com.google.apphosting.utils.config.BackendsYamlReader;
import com.google.apphosting.utils.config.EarHelper;
import com.google.apphosting.utils.config.EarInfo;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;

/**
 * Manager for an application's configuration. Supports both single WAR
 * directory configurations and EAR directory configurations. Also includes
 * support for rereading configurations.
 * <p>
 */
public class ApplicationConfigurationManager {
  private static final Logger logger =
      Logger.getLogger(ApplicationConfigurationManager.class.getName());

  // Configuration root directory. Either an EAR or a WAR.
  private final File configurationRoot;
  private final SystemPropertiesManager systemPropertiesManager;
  private final String sdkRelease;
  private final File applicationSchemaFile;
  @GuardedBy("this")
  private MismatchReportingPolicy environmentVariableMismatchReportingPolicy =
    MismatchReportingPolicy.EXCEPTION;
  @GuardedBy("this")
  private final List<ModuleConfigurationHandle> moduleConfigurationHandles;

  /**
   * Creates a new {@link ApplicationConfigurationManager} from an EAR directory.
   */
  static ApplicationConfigurationManager newEarConfigurationManager(File earRoot,
      String sdkVersion, File applicationSchemaFile)
      throws AppEngineConfigException {
    return newEarConfigurationManager(earRoot, sdkVersion, applicationSchemaFile, "");
  }

  public static ApplicationConfigurationManager newEarConfigurationManager(File earRoot,
      String sdkVersion, File applicationSchemaFile, String appIdPrefix)
      throws AppEngineConfigException {
    if (!EarHelper.isEar(earRoot.getAbsolutePath())) {
      String message = String.format(
          "ApplicationConfigurationManager.newEarConfigurationManager passed an invalid EAR: %s",
          earRoot.getAbsolutePath());
      logger.severe(message);
      throw new AppEngineConfigException(message);
    }
    return new ApplicationConfigurationManager(earRoot, null, null, null, sdkVersion,
        applicationSchemaFile, appIdPrefix);
  }

  /**
   * Creates a new {@link ApplicationConfigurationManager} from a WAR directory.
   * <p>
   * @param warRoot The location of the war directory.
   * @param externalResourceDirectory If not {@code null}, a resource directory external
   *        to the applicationDirectory. This will be searched before
   *        applicationDirectory when looking for resources.
   * @param webXmlLocation The location of a file whose format complies with
   *        http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd.  If null we will use
   *        <appDir>/WEB-INF/web.xml.
   * @param appEngineWebXmlLocation The location of the app engine config file.
   *        If null we will use <appDir>/WEB-INF/appengine-web.xml.
   * @param sdkRelease The sdk version (SdkInfo.getLocalVersion().getRelease()).
   */
  static ApplicationConfigurationManager newWarConfigurationManager(File warRoot,
      File appEngineWebXmlLocation, File webXmlLocation, File externalResourceDirectory,
      String sdkRelease) throws AppEngineConfigException {
    return newWarConfigurationManager(warRoot, appEngineWebXmlLocation, webXmlLocation,
        externalResourceDirectory, sdkRelease, "");
  }

  public static ApplicationConfigurationManager newWarConfigurationManager(File warRoot,
      File appEngineWebXmlLocation, File webXmlLocation, File externalResourceDirectory,
      String sdkRelease, String appIdPrefix) throws AppEngineConfigException {
    if (EarHelper.isEar(warRoot.getAbsolutePath())) {
      String message = String.format(
          "ApplicationConfigurationManager.newWarConfigurationManager passed an EAR: %s",
          warRoot.getAbsolutePath());
      logger.severe(message);
      throw new AppEngineConfigException(message);
    }
    return new ApplicationConfigurationManager(warRoot,
        appEngineWebXmlLocation, webXmlLocation, externalResourceDirectory, sdkRelease,
        null, appIdPrefix);
  }

  /**
   * Returns the {@link ModuleConfigurationHandle} for the primary module which
   * will be the only module for configurations read from a single WAR directory
   * or the first module in applicationDirectory/META-INF/application.xml
   * module order for configurations read from an EAR directory.
   */
  synchronized ModuleConfigurationHandle getPrimaryModuleConfigurationHandle() {
    return moduleConfigurationHandles.get(0);
  }

  /**
   * Returns {@link List} with a {@link ModuleConfigurationHandle} for each
   * configured module.
   * <p>
   * The returned list is immutable.
   */
  public synchronized List<ModuleConfigurationHandle> getModuleConfigurationHandles() {
    return ImmutableList.copyOf(moduleConfigurationHandles);
  }

  /**
   * Constructs an {@link ApplicationConfigurationManager} by reading the
   * configuration from an exploded WAR directory or EAR directory.
   *
   * @param configurationRoot the root directory for the applications EAR or WAR.
   * @param appEngineWebXmlLocation for a WAR configuration a non null value
   *   overrides the default of configurationRoot/WEB-INF/appengine-web.xml and
   *   ignored for an EAR configuration.
   * @param webXmlLocation for a WAR configuration a non null value
   *   overrides the default of configurationRoot/WEB-INF/web.xml and ignored
   *   for an EAR configuration.
   * @param externalResourceDirectory for a WAR configuration the optional
   *   external resource directory or null and ignored for an EAR configuration.
   * @param sdkRelease the SDK version string.
   * @param applicationSchemaFile the appengine-application.xsd schema file for validation.
   */
  private ApplicationConfigurationManager(File configurationRoot, File appEngineWebXmlLocation,
      File webXmlLocation, File externalResourceDirectory, String sdkRelease,
      File applicationSchemaFile, String appIdPrefix) {
    this.configurationRoot  = configurationRoot;
    this.systemPropertiesManager = new SystemPropertiesManager();
    this.sdkRelease = sdkRelease;
    this.applicationSchemaFile = applicationSchemaFile;
    if (EarHelper.isEar(configurationRoot.getAbsolutePath())) {
      EarInfo earInfo = readEarConfiguration();
      ImmutableList.Builder<ModuleConfigurationHandle> builder = ImmutableList.builder();
      for (WebModule module : earInfo.getWebModules()) {
        builder.add(new EarModuleConfigurationHandle(module));
      }
      moduleConfigurationHandles = builder.build();
    } else {
      ModuleConfigurationHandle warConfigurationHandle = new WarModuleConfigurationHandle(
          appEngineWebXmlLocation, webXmlLocation, externalResourceDirectory, appIdPrefix);
      warConfigurationHandle.readConfiguration();
      moduleConfigurationHandles = ImmutableList.of(warConfigurationHandle);
    }
  }

  /**
   * Performs various validations and registers logging configuration and
   * system properties for a {@link WebModule} so they may be combined with
   * values from other modules to construct an applications runtime
   * configuration.
   * <p>
   * Though this function provides little or no real abstraction and badly
   * fails the 'does one thing' test it avoids some code duplication..
   *
   * @param module module
   * @param loggingConfigurationManager for validating and combining the
   *     the applications logging configuration.
   * @param externalResourceDirectory the externalResourceDirectory for
   *     obtaining logging configuration.
   */
  private synchronized void validateAndRegisterGlobalValues(WebModule module,
      LoggingConfigurationManager loggingConfigurationManager,
      File externalResourceDirectory) {
    module.getWebXml().validate();
    AppEngineWebXml appEngineWebXml = module.getAppEngineWebXml();
    loggingConfigurationManager.read(systemPropertiesManager.getOriginalSystemProperties(),
        appEngineWebXml.getSystemProperties(), module.getApplicationDirectory(),
        externalResourceDirectory);
    systemPropertiesManager.setSystemProperties(appEngineWebXml, module.getAppEngineWebXmlFile());
  }

  /**
   * Reads or rereads an application's EAR configuration, performs validations,
   * and calculate the application's logging configuration and system
   * properties.
   * @return the {@link EarInfo} for the configuration.
   */
  private synchronized EarInfo readEarConfiguration() {
    if (!EarHelper.isEar(configurationRoot.getAbsolutePath())) {
      String message = String.format("Unsupported update from EAR to WAR for: %s",
          configurationRoot.getAbsolutePath());
      logger.severe(message);
      throw new AppEngineConfigException(message);
    }
    EarInfo earInfo = EarHelper.readEarInfo(configurationRoot.getAbsolutePath(),
        applicationSchemaFile);
    String majorVersionId = null;
    String urlStreamHandlerType = null;
    LoggingConfigurationManager loggingConfigurationManager = new LoggingConfigurationManager();
    for (WebModule module : earInfo.getWebModules()) {
      module.getWebXml().validate();
      AppEngineWebXml appEngineWebXml = module.getAppEngineWebXml();
      if (majorVersionId == null) {
        majorVersionId = appEngineWebXml.getMajorVersionId();
        // Use the UrlStreamHandlerType consistent with appEngineWebXml that
        // we snatched majorVersionId.
        urlStreamHandlerType = appEngineWebXml.getUrlStreamHandlerType();
      }
      validateAndRegisterGlobalValues(module, loggingConfigurationManager, null);
      // Note that stubs are not re-initialized after re-read.
    }
    systemPropertiesManager.setAppengineSystemProperties(sdkRelease,
        earInfo.getAppengineApplicationXml().getApplicationId(), majorVersionId);
    loggingConfigurationManager.updateLoggingConfiguration();
    updateUrlStreamHandlerMode(urlStreamHandlerType);
    return earInfo;
  }

  /**
   * Checks that the applications combined environment variables are consistent
   * and reports inconsistencies based on {@link
   * #getEnvironmentVariableMismatchReportingPolicy}.
   */
  private synchronized void checkEnvironmentVariables() {
    //TODO: Avoid repeating warnings for repeated calls.
    EnvironmentVariableChecker environmentVariableChecker =
        new EnvironmentVariableChecker(environmentVariableMismatchReportingPolicy);
    for (ModuleConfigurationHandle moduleConfigurationHandle : moduleConfigurationHandles) {
      WebModule module = moduleConfigurationHandle.getModule();
      environmentVariableChecker.add(module.getAppEngineWebXml(), module.getAppEngineWebXmlFile());
    }
    environmentVariableChecker.check();
  }

  public synchronized void setEnvironmentVariableMismatchReportingPolicy(
      MismatchReportingPolicy environmentVariableMismatchReportingPolicy) {
     this.environmentVariableMismatchReportingPolicy = environmentVariableMismatchReportingPolicy;
  }

  synchronized MismatchReportingPolicy getEnvironmentVariableMismatchReportingPolicy() {
    return this.environmentVariableMismatchReportingPolicy;
  }

  private void updateUrlStreamHandlerMode(String urlStreamHandlerType) {
    if (urlStreamHandlerType == null) {
      // Native is default for Java8/Jetty9:
      LocalURLFetchServiceStreamHandler.setUseNativeHandlers(true);
    } else {
      LocalURLFetchServiceStreamHandler.setUseNativeHandlers(
          AppEngineWebXml.URL_HANDLER_NATIVE.equals(urlStreamHandlerType));
    }
  }

  @Override
  public synchronized String toString() {
    return "ApplicationConfigurationManager: configurationRoot="  + configurationRoot
        + " systemPropertiesManager=" + systemPropertiesManager
        + " sdkVersion=" + sdkRelease
        + " environmentVariableMismatchReportingPolicy="
        + environmentVariableMismatchReportingPolicy
        + " moduleConfigurationHandles=" + moduleConfigurationHandles;
  }

  /**
   * Handle for accessing and rereading a module configuration.
   * <p>
   * A WAR configuration supports a single module with additional
   * backends specified in war/WEB-INF/backends.xml. All instances
   * of the single module and all backends instances share a single
   * {@link ModuleConfigurationHandle} so updates made by a call to
   * {@link #readConfiguration()} will be visible to all module and backend
   * instances. An EAR configuration supports multiple modules. All instances
   * of a module share a single {@link ModuleConfigurationHandle} so updates
   * made by a call to {@link #readConfiguration()} for a particular module are
   * visible to all instances of the module.
   * <p>
   * To control when changes become visible clients should keep and refresh
   * references to values which will be replaced when the configuration is
   * reread including {@link #getModule()} and for WAR configurations
   * {@link #getBackendsXml()}.
   * <p>
   * Implementations synchronize operations that read or write state
   * that may be changed by {@link #readConfiguration()} on
   * ApplicationConfigurationManager.this. Note that configuration updates
   * involving edits to multiple configuration files are not guaranteed to be
   * atomic in the case {@link #readConfiguration()} is called after one write
   * and before another during a multi-write configuration change. Given this
   * and that backends are about to be deprecated, no synchronized operation
   * is provided for a client to obtain the combined values returned by calling
   * {@link #getModule()} and then calling {@link #getBackendsXml()}.
   */
  public interface ModuleConfigurationHandle {
    /**
     * Returns the {@link WebModule} for this configuration.
     */
    WebModule getModule();
    /**
     * Checks if the configuration specifies environment variables that do not
     * match the JVM's environment variables.
     * <p>
     * This check is broken out rather than implemented during construction for
     * backwards compatibility. The check is deferred until {@link DevAppServer#start()}
     * to give {@link DevAppServer} clients a chance to call
     * {@link DevAppServer#setThrowOnEnvironmentVariableMismatch(boolean)}
     * before reporting errors.
     */
    void checkEnvironmentVariables();

    /**
     * Returns the {@link BackendsXml} for this configuration.
     * <p>
     * For EAR configurations this will return null. For WAR configurations this
     * will return a value read from the war/WEB-INF/backends.xml if one is
     * specified or null otherwise.
     */
    BackendsXml getBackendsXml();

    /**
     * Reads or rereads the configuration from disk to pick up any changes.
     * Calling this function affects global state visible to all the modules
     * in the application including:
     * <ol>
     * <li> system properties
     * <li> the logging configuration
     * </ol>
     *
     * Because for EAR configurations the global state includes information from
     * all the modules in the EAR, this rereads the configuration for every module.
     * This does not update the {@link ModuleConfigurationHandle} for any other
     * modules. Certain configuration changes are not currently supported
     * including changes that
     * <ol>
     * <li> Adds entries to {@link ApplicationConfigurationManager#getModuleConfigurationHandles()}
     * <li> removes entries from
     *      {@link ApplicationConfigurationManager#getModuleConfigurationHandles()}
     * <li> Changes the application directory for a {@link ModuleConfigurationHandle} returned
     *      by {@link ApplicationConfigurationManager#getModuleConfigurationHandles()}
     * </ol>
     * @throws AppEngineConfigException if the configuration on disk is not valid
     *     or includes unsupported changes.
     */
    void readConfiguration() throws AppEngineConfigException;

    /**
     * Clears {link {@link System#getProperties()} values that have been set by this
     * configuration.
     */
    void restoreSystemProperties();
  }

  private class WarModuleConfigurationHandle implements ModuleConfigurationHandle {
    private final File rawAppEngineWebXmlLocation;
    //Null means use applicationDirectrory/WEB-INF/web.xml. After
    //calling readConfiguration webModule holds the resolved value.
    private final File rawWebXmlLocation;
    private final File externalResourceDirectory;
    private final String appIdPrefix;
    @GuardedBy("ApplicationConfigurationManager.this")
    private BackendsXml backendsXml;
    @GuardedBy("ApplicationConfigurationManager.this")
    private WebModule webModule;
    /**
     * @param appEngineWebXmlLocation absolute paths are accepted, relative
     *        paths are under applicationDirectory and null means to use
     *        applicationDirectory/WEB-INF/appengine-web.xml.
     * @param webXmlLocation absolute paths are accepted, relative
     *        paths are under applicationDirectory and null means to use
     *        applicationDirectory/WEB-INF/web.xml.
     * @param externalResourceDirectory If not {@code null}, a resource directory external
     *        to the applicationDirectory. This will be searched before
     *        applicationDirectory when looking for resources.
     */
    WarModuleConfigurationHandle(File appEngineWebXmlLocation, File webXmlLocation,
        File externalResourceDirectory, String appIdPrefix) {
      this.rawAppEngineWebXmlLocation = appEngineWebXmlLocation;
      this.rawWebXmlLocation = webXmlLocation;
      this.externalResourceDirectory = externalResourceDirectory;
      this.appIdPrefix = appIdPrefix;
    }

    @Override
    public WebModule getModule() {
      synchronized (ApplicationConfigurationManager.this) {
        return webModule;
      }
    }

    @Override
    public void checkEnvironmentVariables() {
      ApplicationConfigurationManager.this.checkEnvironmentVariables();
    }

    @Override
    public BackendsXml getBackendsXml() {
      synchronized (ApplicationConfigurationManager.this) {
        return backendsXml;
      }
    }

    @Override
    public void readConfiguration() {
      synchronized (ApplicationConfigurationManager.this) {
        //TODO: Get WAR files to work.
        if (EarHelper.isEar(configurationRoot.getAbsolutePath())) {
          String message = String.format("Unsupported update from WAR to EAR for: %s",
              configurationRoot.getAbsolutePath());
          logger.severe(message);
          throw new AppEngineConfigException(message);
        }
        WebModule updatedWebModule = EarHelper.readWebModule(null, configurationRoot,
            rawAppEngineWebXmlLocation, rawWebXmlLocation, appIdPrefix);
        if (webModule != null) {
          checkDynamicModuleUpdateAllowed(webModule, updatedWebModule);
        }
        webModule = updatedWebModule;
        String baseDir = configurationRoot.getAbsolutePath();
        File webinf = new File(baseDir, "WEB-INF");
        backendsXml =
            new BackendsXmlReader(baseDir).readBackendsXml();
        if (backendsXml == null) {
          BackendsYamlReader backendsYaml = new BackendsYamlReader(webinf.getPath());
          backendsXml = backendsYaml.parse();
        }
        AppEngineWebXml appEngineWebXml = webModule.getAppEngineWebXml();
        String appId = System.getenv("APPLICATION_ID");
        if (appId == null
            && (appEngineWebXml.getAppId() == null || appEngineWebXml.getAppId().isEmpty())) {
          appId = "no_app_id";
          // it's acceptable for app id to be blank in appengine-web.xml when running
          // locally, but some local services require an app id.
        }
        if (appId != null) {
          appEngineWebXml.setAppId(appId);
        }
        LoggingConfigurationManager loggingConfigurationManager = new LoggingConfigurationManager();
        validateAndRegisterGlobalValues(webModule, loggingConfigurationManager,
            externalResourceDirectory);
        systemPropertiesManager.setAppengineSystemProperties(sdkRelease,
            appEngineWebXml.getAppId(), appEngineWebXml.getMajorVersionId());
        loggingConfigurationManager.updateLoggingConfiguration();
        updateUrlStreamHandlerMode(appEngineWebXml.getUrlStreamHandlerType());
      }
    }

    @Override
    public void restoreSystemProperties() {
      synchronized (ApplicationConfigurationManager.this) {
        systemPropertiesManager.restoreSystemProperties();
      }
    }

    @Override
    public String toString() {
      synchronized (ApplicationConfigurationManager.this) {
        return "WarConfigurationHandle: webModule=" + webModule
            + " backendsXml=" + backendsXml
            + " appEngineWebXmlLocation=" + rawAppEngineWebXmlLocation
            + " webXmlLocation=" + rawWebXmlLocation
            + " externalResourceDirectory=" + externalResourceDirectory;
      }
    }
  }

  private class EarModuleConfigurationHandle implements ModuleConfigurationHandle {
    @GuardedBy("ApplicationConfigurationManager.this")
    private WebModule webModule;

    EarModuleConfigurationHandle(WebModule webModule) {
      this.webModule = webModule;
    }

    @Override
    public WebModule getModule() {
      synchronized (ApplicationConfigurationManager.this) {
        return webModule;
      }
    }

    @Override
    public void checkEnvironmentVariables() {
      synchronized (ApplicationConfigurationManager.this) {
        ApplicationConfigurationManager.this.checkEnvironmentVariables();
      }
    }

    @Override
    public BackendsXml getBackendsXml() {
      return null;
    }

    @Override
    public void readConfiguration() {
      synchronized (ApplicationConfigurationManager.this) {
        EarInfo earInfo = readEarConfiguration();
        checkDynamicUpdateAllowed(earInfo);
        for (WebModule module : earInfo.getWebModules()) {
          if (module.getApplicationDirectory().equals(webModule.getApplicationDirectory())) {
            webModule = module;
            return;
          }
        }

        throw new IllegalStateException("Expected web module not found.");
      }
    }

    /**
     * Checks that the passed in {@link EarInfo} which was read from the EAR
     * directory for this {@link ApplicationConfigurationManager} is consistent
     * with the current configuration for purposes of a dynamic configuration
     * update. The following restrictions apply
     * <ol>
     * <li> The set of WAR directory names may not change.
     * <li> Module names may not change.
     * <li> Module {@link ScalingType} may not change.
     * <li> For modules with a configured number of instances the configured number
     *      may not change.
     * </ol>
     * @throws AppEngineConfigException if the modules do not match.
     */
    @GuardedBy("ApplicationConfigurationManager.this")
    private void checkDynamicUpdateAllowed(EarInfo updatedEarInfo) throws AppEngineConfigException {
      Map<File, WebModule> currentModuleMap = getCurrentModuleMap();
      Map<File, WebModule> updatedModuleMap = getUpdatedModuleMap(updatedEarInfo);

      checkWarDirectoriesMatch(currentModuleMap.keySet(), updatedModuleMap.keySet());

      // If we get this far currentMap.keySet().equals(updatedMap.keySet())
      for (File currentWarFile : currentModuleMap.keySet()) {
        WebModule currentModule = currentModuleMap.get(currentWarFile);
        WebModule updatedModule = updatedModuleMap.get(currentWarFile);
        checkDynamicModuleUpdateAllowed(currentModule, updatedModule);
      }
    }

    @GuardedBy("ApplicationConfigurationManager.this")
    private Map<File, WebModule> getCurrentModuleMap() {
      ImmutableSortedMap.Builder<File, WebModule> currentModuleMapBuilder =
          ImmutableSortedMap.naturalOrder();
      for (ModuleConfigurationHandle handle : moduleConfigurationHandles) {
        currentModuleMapBuilder.put(handle.getModule().getApplicationDirectory(),
            handle.getModule());
      }
      return currentModuleMapBuilder.build();
    }

    private Map<File, WebModule> getUpdatedModuleMap(EarInfo earInfo) {
      ImmutableSortedMap.Builder<File, WebModule> updatedModuleMapBuilder =
          ImmutableSortedMap.naturalOrder();
      for (WebModule module : earInfo.getWebModules()) {
        updatedModuleMapBuilder.put(module.getApplicationDirectory(), module);
      }
      return updatedModuleMapBuilder.build();
    }

    private void checkWarDirectoriesMatch(Set<File>currentWarDirectories,
        Set<File>updatedWarDirectories) {
      if (!currentWarDirectories.equals(updatedWarDirectories)) {
        String message = String.format(
            "Unsupported configuration change of war directories from '%s' to '%s'",
            currentWarDirectories, updatedWarDirectories);
        logger.severe(message);
        throw new AppEngineConfigException(message);
      }
    }

    @Override
    public void restoreSystemProperties() {
      synchronized (ApplicationConfigurationManager.this) {
        systemPropertiesManager.restoreSystemProperties();
      }
    }

    @Override
    public String toString() {
      synchronized (ApplicationConfigurationManager.this) {
        return "WarConfigurationHandle: webModule=" + webModule;
      }
    }
  }

  private static void checkDynamicModuleUpdateAllowed(WebModule currentModule,
      WebModule updatedModule) throws AppEngineConfigException {
    checkServerNamesMatch(currentModule, updatedModule);
    checkScalingTypesMatch(currentModule, updatedModule);
    checkInstanceCountsMatch(currentModule, updatedModule);
  }

  private static void checkServerNamesMatch(WebModule currentModule, WebModule updatedModule)
      throws AppEngineConfigException {
    String currentModuleName = currentModule.getModuleName();
    String updatedModuleName = updatedModule.getModuleName();
    if (!currentModuleName.equals(updatedModuleName)) {
      String message = String.format(
          "Unsupported configuration change of module name from '%s' to '%s' in '%s'",
          currentModuleName, updatedModuleName, currentModule.getAppEngineWebXmlFile());
      logger.severe(message);
      throw new AppEngineConfigException(message);
    }
  }

  private static void checkScalingTypesMatch(WebModule currentModule, WebModule updatedModule)
      throws AppEngineConfigException {
    ScalingType currentScalingType = currentModule.getAppEngineWebXml().getScalingType();
    ScalingType updatedScalingType = updatedModule.getAppEngineWebXml().getScalingType();
    if (!currentScalingType.equals(updatedScalingType)) {
      String message = String.format(
          "Unsupported configuration change of scaling from '%s' to '%s' in '%s'",
          currentScalingType, updatedScalingType, currentModule.getAppEngineWebXmlFile());
      logger.severe(message);
      throw new AppEngineConfigException(message);
    }
  }

  private static void checkInstanceCountsMatch(WebModule currentModule, WebModule updatedModule)
      throws AppEngineConfigException {
    ScalingType currentScalingType = currentModule.getAppEngineWebXml().getScalingType();
    switch (currentScalingType) {
      case MANUAL:
        String currentManualInstances =
            currentModule.getAppEngineWebXml().getManualScaling().getInstances();
        String updatedManualInstances =
            updatedModule.getAppEngineWebXml().getManualScaling().getInstances();
        if (!Objects.equals(currentManualInstances, updatedManualInstances)) {
          String template =
              "Unsupported configuration change of manual scaling instances from '%s' "
                  + "to '%s' in '%s'";
          String message =
              String.format(
                  template,
                  currentManualInstances,
                  updatedManualInstances,
                  currentModule.getAppEngineWebXmlFile());
          logger.severe(message);
          throw new AppEngineConfigException(message);
        }
        break;

      case BASIC:
        String currentBasicMaxInstances =
            currentModule.getAppEngineWebXml().getBasicScaling().getMaxInstances();
        String updatedBasicMaxInstances =
            updatedModule.getAppEngineWebXml().getBasicScaling().getMaxInstances();
        if (!Objects.equals(currentBasicMaxInstances, updatedBasicMaxInstances)) {
          String template =
              "Unsupported configuration change of basic scaling max instances "
                  + "from '%s' to '%s' in '%s'";
          String message =
              String.format(
                  template,
                  currentBasicMaxInstances,
                  updatedBasicMaxInstances,
                  currentModule.getAppEngineWebXmlFile());
          logger.severe(message);
        }
        break;

    default:
      // Fall through.
    }
  }
}
