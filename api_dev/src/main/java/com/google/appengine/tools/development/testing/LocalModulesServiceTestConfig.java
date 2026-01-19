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

package com.google.appengine.tools.development.testing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError;
import com.google.appengine.api.modules.dev.LocalModulesService;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.appengine.tools.development.ModulesController;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.ApplicationException;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 *  Config for accessing the {@link LocalModulesService} in tests.
 *  <p>
 *  To understand the operation of {@link LocalModulesServiceTestConfig} please note that a number
 *  of {@link com.google.apphosting.api.ApiProxy.Environment} settings affect the
 *  operation of {@link com.google.appengine.api.modules.ModulesService}. For the test environment
 *  these settings are controlled using {@link LocalServiceTestHelper}:
 *  <ol>
 *  <li> {@link LocalServiceTestHelper#setEnvModuleId} specifies the result returned by
 *       {@link com.google.appengine.api.modules.ModulesService#getCurrentModule()}. The default
 *       module is "default".
 *  <li> {@link LocalServiceTestHelper#setEnvVersionId} specifies the result returned by
 *       {@link com.google.appengine.api.modules.ModulesService#getCurrentVersion()}. The default
 *       version is "1".
 *  <li> {@link LocalServiceTestHelper#setEnvInstance} specifies the result returned by
 *       {@link com.google.appengine.api.modules.ModulesService#getCurrentInstanceId()}. For manual
 *       scaling and basic scaling module versions you can explicitly specify a configured instance
 *       or if you prefer you can leave the default("-1") value and the testing framework will set
 *       the instance to 0. For automatic scaling module version you must leave the value as the
 *       default("-1").
 *  </ol>
 *  <p>
 *  The environment values for {@link LocalServiceTestHelper} must match a module, version (and
 *  instance if needed) that you configure or one that is automatically generated for you.
 *
 *  Changes made to the environment values for {@link LocalServiceTestHelper} after you call
 *  {@link #setUp} do not apply for {@link com.google.appengine.api.modules.ModulesService}
 *  during your test run.
 *
 *  For simple configurations {@link LocalModulesServiceTestConfig} will generate a configuration
 *  based on the current {@link com.google.apphosting.api.ApiProxy.Environment}. The generated
 *  {@link LocalModulesServiceTestConfig} contains the following elements:
 *  <ol>
 *  <li> If the envModuleId element is not "default" then an automatic scaling module version
 *       will be added with module = "default" and version = "1". This simulates the appengine
 *       rule that an application must have a version of the default module.
 *  <li> If the envInstance has not been set or is set to its default "-1" value then an
 *       automatic scaling module version will be added with module = envModuleId and
 *       version = envVerionId. Note here and below that in the event envVersionId contains
 *       a .&lt;minor-version&gt; suffix (eg "v1.9") the minor suffix is not included in the
 *       added module version.
 *  <li> If the envInstance has been set or is set to a non default ("-1") value then a
 *       manual scaling module version will be added with module = envModuleId and
 *       version = envVerionId and instance = envInstance.
 *  </ol>
 *  To configure a test to use ModulesService with a generated configuration perform these steps
 *  in the order given.
 *  <ol>
 *  <li> Construct a {@link LocalModulesServiceTestConfig}
 *  <li> Pass the {@link LocalModulesServiceTestConfig} to the {@link
 *       LocalServiceTestHelper} constructor with other needed {@link LocalServiceTestConfig}
 *       objects.
 *  <li> Update the test {@link com.google.apphosting.api.ApiProxy.Environment} to refer to a
 *       desired module, version and instance (if needed).
 *  <li> Start the testing environment with {@link #setUp} and execute your test.
 *  </ol>
 *
 *  Here is a sample usage:
 * <pre>{@code
 *   LocalServiceTestHelper helper;
 *   @Before
 *   public void createService() {
 *     helper = new LocalServiceTestHelper(new LocalModulesServiceTestConfig());
 *     helper.setEnvModuleId("m1");   // Optional
 *     helper.setEnvVersionId("v2");  // Optional
 *     helper.setEnvInstance("2");    // Optional
 *     helper.setUp();
 *   }
 * }</pre>
 *
 *  A tester may specify the module versions in the test configuration explicitly
 *  by following these steps.
 *  <ol>
 *  <li> Construct a {@link LocalModulesServiceTestConfig}
 *  <li> Add needed module versions with {@link #addDefaultModuleVersion},
 *       {@link #addAutomaticScalingModuleVersion}, {@link #addManualScalingModuleVersion} and
 *       {@link #addBasicScalingModuleVersion}. When multiple versions are added for the same
 *       module, the first will be the default.
 *  <li> Pass the {@link LocalModulesServiceTestConfig} to the {@link
 *       LocalServiceTestHelper} constructor with other needed {@link LocalServiceTestConfig}
 *       objects.
 *  <li> Update the test {@link com.google.apphosting.api.ApiProxy.Environment} to refer to a
 *       configured module instance as needed.
 *  <li> Start the testing environment with {@link #setUp}. Changes made
 *       to the configuration after calling {@link #setUp} are not available during your test.
 *  </ol>
 *  <p>
 *
 *  Here is a sample usage:
 * <pre>{@code
 *   LocalServiceTestHelper helper;
 *   @Before
 *   public void createService() {
 *     helper = new LocalServiceTestHelper(new LocalModulesServiceTestConfig()
 *        .addDefaultModuleVersion()
 *        .addAutomaticScalingModuleVersion("a1", "v1") // default version for module "a1"
 *        .addManualScalingModuleVersion("m1", "v1", 2) // default version for module "m1"
 *        .addManualScalingModuleVersion("m1", "v2", 3));
 *     helper.setEnvModuleId("m1");
 *     helper.setEnvVersionId("v2");
 *     helper.setEnvInstance("0");  // Optional
 *     helper.setUp();
 *   }
 * }</pre>
 */
public class LocalModulesServiceTestConfig implements LocalServiceTestConfig {
  // Keep this in sync with INSTANCE_ID_ENV_ATTRIBUTE in
  // com.google.appengine.api.modules.ModulesServiceImpl
  private static final String INSTANCE_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.id";
  public static final String DEFAULT_MODULE_NAME = "default";
  static final String DEFAULT_VERSION = "1";
  private static final Logger logger =
      Logger.getLogger(LocalModulesServiceTestConfig.class.getName());
  // Keep this in sync with LocalEnvironment.MAIN_INSTANCE
  static final int MAIN_INSTANCE = -1;
  static final int DYNAMIC_INSTANCE_COUNT = -1;
  private TestModulesController testModulesController;

  // Tracking for ModuleVersion's the user adds with addXXXModuleVersion()
  // methods.
  //
  // Implemented as a LinkedHashMap to remember the order module versions
  // are added which is needed to implement the rule that the first version for
  // a module is the default.
  private final Map<ModuleVersionKey, ModuleVersion> moduleVersionMap =
      new LinkedHashMap<ModuleVersionKey, ModuleVersion>();

  @Override
  public synchronized void setUp() {
    createModulesController(moduleVersionMap.isEmpty() ?
        generateModuleVersions() : moduleVersionMap.values());
    LocalModulesService localModulesService = getLocalModulesService();
    localModulesService.setModulesController(testModulesController);
  }

  @Override
  public void tearDown() {
  }

  /**
   * Adds a automatic scaling module version.
   * @param module the module name
   * @param version the version
   * @return this for chaining
   * @throws IllegalArgumentException if a module version with the same name and version
   *         has already been added.
   */
  public LocalModulesServiceTestConfig addAutomaticScalingModuleVersion(String module,
      String version) {
    ModuleVersion moduleVersion =
        new ModuleVersion(module, version, ScalingType.AUTOMATIC, DYNAMIC_INSTANCE_COUNT);
    addModuleVersion(moduleVersion);
    return this;
  }

  /**
   * Adds a manual scaling module version.
   * @param module the module name
   * @param version the version
   * @param numInstances the number of instances for the module version.
   * @return this for chaining
   * @throws IllegalArgumentException if a module version with the same name and version
   *         has already been added.
   * @throws IllegalArgumentException if numInstances &lt;= 0.
   */
  public LocalModulesServiceTestConfig addManualScalingModuleVersion(String module, String version,
      int numInstances) {
    validateInstances(numInstances);
    ModuleVersion moduleVersion =
        new ModuleVersion(module, version, ScalingType.MANUAL, numInstances);
    addModuleVersion(moduleVersion);
    return this;
  }

  /**
   * Adds a basic scaling module version.
   * @param module the module name
   * @param version the version
   * @param numInstances the number of instances for the module version.
   * @return this for chaining
   * @throws IllegalArgumentException if a module version with the same name and version
   *         has already been added.
   * @throws IllegalArgumentException if numInstances &lt;= 0.
   */
  public LocalModulesServiceTestConfig addBasicScalingModuleVersion(String module, String version,
      int numInstances) {
    validateInstances(numInstances);
    ModuleVersion moduleVersion =
        new ModuleVersion(module, version, ScalingType.BASIC, numInstances);
    addModuleVersion(moduleVersion);
    return this;
  }

  private void validateInstances(int numInstances) {
    if (numInstances <= 0) {
      throw new IllegalArgumentException("instanceCount " + numInstances + " <= 0");
    }
  }

  /**
   * Adds an automatic scaling module version that matches the default
   * {@link com.google.apphosting.api.ApiProxy.Environment} constructed by
   * {@link LocalServiceTestHelper} with module = "default" and version = "1".
   * @return this for chaining
   * @throws IllegalArgumentException if a module version with the same name and version
   *         has already been added.
   */
  public LocalModulesServiceTestConfig addDefaultModuleVersion() {
    ModuleVersion moduleVersion =  new ModuleVersion(DEFAULT_MODULE_NAME, DEFAULT_VERSION,
        ScalingType.AUTOMATIC, DYNAMIC_INSTANCE_COUNT);
    addModuleVersion(moduleVersion);
    return this;
  }

  /**
   * Clears any module versions that have been added with {@link #addAutomaticScalingModuleVersion},
   * {@link #addBasicScalingModuleVersion}, {@link #addManualScalingModuleVersion} and
   * {@link #addDefaultModuleVersion}.
   */
  public synchronized void clearModuleVersions() {
    moduleVersionMap.clear();
  }

  private Iterable<ModuleVersion> generateModuleVersions() {
    String envModuleId = ApiProxy.getCurrentEnvironment().getModuleId();
    String envVersionId = stripMinorVersion(ApiProxy.getCurrentEnvironment().getVersionId());
    String envInstance =
        (String) ApiProxy.getCurrentEnvironment().getAttributes().get(INSTANCE_ID_ENV_ATTRIBUTE);
    ImmutableList.Builder<ModuleVersion> versionsBuilder = ImmutableList.builder();
    if (!WebModule.DEFAULT_MODULE_NAME.equals(envModuleId)) {
      versionsBuilder.add(generateModuleVersion(
          WebModule.DEFAULT_MODULE_NAME, LocalServiceTestHelper.DEFAULT_VERSION_ID, null));
    }
    versionsBuilder.add(generateModuleVersion(envModuleId, envVersionId, envInstance));
    return versionsBuilder.build();
  }

  private ModuleVersion generateModuleVersion(String module, String version, String instance)  {
    if (instance == null) {
      return new ModuleVersion(module, version, ScalingType.AUTOMATIC, DYNAMIC_INSTANCE_COUNT);
    } else {
      return new ModuleVersion(module, version, ScalingType.MANUAL, Integer.parseInt(instance) + 1);
    }
  }

  private synchronized void addModuleVersion(ModuleVersion moduleVersion) {
    checkArgument(
        !moduleVersionMap.containsKey(moduleVersion.getKey()),
        "Module version module %s version %s is already defined: %s",
        moduleVersion.getModule(),
        moduleVersion.getVersion(),
        moduleVersionMap.values());
    moduleVersionMap.put(moduleVersion.getKey(), moduleVersion);
  }

  /**
   * Returns the {@link LocalModulesService} which is created on first use.
   */
  public static LocalModulesService getLocalModulesService() {
    return
        (LocalModulesService) LocalServiceTestHelper.getLocalService(LocalModulesService.PACKAGE);
  }

  /**
   * Throws an {@link IllegalStateException} if the specified module version has not been
   * configured.
   *
   * @param module the module name from the environment
   * @param version the version from the environment which may have a .minor-version suffix
   *        which is ignored.
   * @param instance the instance from the environment which may be {@link #DYNAMIC_INSTANCE_COUNT}.
   */
  void verifyEnvironment(String module, String version, int instance) {
    version = stripMinorVersion(version);
    testModulesController.verifyEnvironment(module, version, instance);
  }

  /**
   * Returns true iff the caller ({@link LocalServiceTestHelper} must include an instance in the
   * {@link com.google.apphosting.api.ApiProxy.Environment} for the given module and version.
   * <p>
   * This will return true if module version is manual scaling or basic scaling.
   *
   * This returns false if the user does not configure any module versions. In this case
   * {@link #setUp} will generate configuration so no instance is needed and this returns false.
   */
  synchronized boolean requiresEnvironmentInstance(String module, String version) {
    ModuleVersion moduleVersion = moduleVersionMap.get(new ModuleVersionKey(module, version));
    return moduleVersion != null && moduleVersion.getInitialNumInstances() != MAIN_INSTANCE;
  }

  private void createModulesController(Iterable<ModuleVersion> moduleVersions) {
    HashMap<String, String> defaultVersions = new HashMap<String, String>();
    ImmutableList.Builder<String> modulesListBuilder = ImmutableList.builder();
    ImmutableListMultimap.Builder<String, String> versionMapBuilder =
        ImmutableListMultimap.builder();
    ImmutableMap.Builder<ModuleVersionKey, AbstractModuleVersionState>
        moduleVersionStateMapBuilder = ImmutableMap.builder();
    for (ModuleVersion moduleVersion : moduleVersions) {
      // First version for a module is default
      if (!defaultVersions.containsKey(moduleVersion.getModule())) {
        defaultVersions.put(moduleVersion.getModule(), moduleVersion.getVersion());
        modulesListBuilder.add(moduleVersion.getModule());
      }
      moduleVersionStateMapBuilder.put(moduleVersion.getKey(),
          moduleVersion.getScalingType().getModuleVersionState(moduleVersion));
      versionMapBuilder.put(moduleVersion.getModule(), moduleVersion.getVersion());
    }

    checkArgument(
        defaultVersions.containsKey(DEFAULT_MODULE_NAME),
        "No version of the default module is configured: moduleVersions=%s",
        moduleVersions);

    ImmutableMap.Builder<String, String> defaultVersionsBuilder = ImmutableMap.builder();
    defaultVersionsBuilder.putAll(defaultVersions);

    testModulesController =
        new TestModulesController(
            moduleVersionStateMapBuilder.buildOrThrow(),
            modulesListBuilder.build(),
            versionMapBuilder.build().asMap(),
            defaultVersionsBuilder.buildOrThrow());
  }


  private static String stripMinorVersion(String version) {
    return Splitter.on('.').split(version).iterator().next();
  }

  private static class TestModulesController implements ModulesController {
    private final ImmutableMap<ModuleVersionKey, AbstractModuleVersionState> moduleVersionStateMap;
    private final ImmutableList<String> modules;
    private final ImmutableMap<String, Collection<String>> versions;
    private final ImmutableMap<String, String> defaultVersions;

    TestModulesController(ImmutableMap<ModuleVersionKey,
        AbstractModuleVersionState> moduleVersionStateMap,
        ImmutableList<String> modules,
        ImmutableMap<String, Collection<String>> versions,
        ImmutableMap<String, String> defaultVersions) {
      this.moduleVersionStateMap = checkNotNull(moduleVersionStateMap);
      this.modules = checkNotNull(modules);
      this.versions = checkNotNull(versions);
      this.defaultVersions = checkNotNull(defaultVersions);
    }

    @Override
    public Iterable<String> getModuleNames() {
      return modules;
    }

    @Override
    public Iterable<String> getVersions(String moduleName) throws ApplicationException {
      if (versions.containsKey(moduleName)) {
        return versions.get(moduleName);
      } else {
        logger.info("Operation getVersions failed because module=" + moduleName
            + " is not defined.");
        throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_MODULE_VALUE,
            "The specified module does not exist.");
      }
    }

    @Override
    public String getDefaultVersion(String moduleName) throws ApplicationException {
      if (defaultVersions.containsKey(moduleName)) {
        return defaultVersions.get(moduleName);
      } else {
        logger.info("Operation getDefaultVersion failed because module=" + moduleName
            + " is not defined.");
        throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_MODULE_VALUE,
            "Invalid module name.");
      }
    }

    @Override
    public int getNumInstances(String moduleName, String version) throws ApplicationException {
      AbstractModuleVersionState moduleVersionState =
          getModuleVersionState("getNumInstances", moduleName, version);
      return moduleVersionState.getNumInstances();
    }

    @Override
    public void setNumInstances(String moduleName, String version, int numInstances)
        throws ApiProxy.ApplicationException {
      AbstractModuleVersionState moduleVersionState =
          getModuleVersionState("setNumInstances", moduleName, version);
      moduleVersionState.setNumInstances(numInstances);
    }

    @Override
    public String getHostname(String moduleName, String version, int instance)
        throws ApplicationException {
      String operation = instance == MAIN_INSTANCE ? "getHostName" : "getInstanceHostname";
      if (!versions.containsKey(moduleName)) {
        logger.warning("Operation " + operation + " could not find the requested module "
            + " Module " + moduleName);
        throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_MODULE_VALUE,
            "The specified module does not exist.");
      }
      StringBuilder sb = new StringBuilder();
      if (instance != DYNAMIC_INSTANCE_COUNT) {
        AbstractModuleVersionState moduleVersionState =
            getModuleVersionState(operation, moduleName, version);
        sb.append(moduleVersionState.getInstanceDot(instance));
      }
      sb.append(version);
      sb.append(".");
      sb.append(moduleName);
      sb.append(".");
      sb.append(ApiProxy.getCurrentEnvironment().getAppId());
      sb.append(".");
      sb.append(getLocalModulesService().getServerHostname());
      return sb.toString();
    }

    @Override
    public void startModule(String moduleName, String version) throws ApplicationException {
      checkNotNull(moduleName);
      checkNotNull(version);
      AbstractModuleVersionState moduleVersionState =
          getModuleVersionState("startVersion", moduleName, version);
      moduleVersionState.start();
    }

    @Override
    public void stopModule(String moduleName, String version) throws ApplicationException {
      AbstractModuleVersionState moduleVersionState =
          getModuleVersionState("stopVersion", moduleName, version);
      moduleVersionState.stop();
    }

    @Override
    public String getScalingType(String moduleName) throws ApplicationException {
      // This method is used by Java dev appserver but not currently needed by
      // LocalModulesService so we do not implement it here.
      throw new UnsupportedOperationException();
    }

    @Override
    public ModuleState getModuleState(String moduleName) throws ApplicationException {
      // This method is used by Java dev appserver but not currently needed by
      // LocalModulesService so we do not implement it here.
      throw new UnsupportedOperationException();
    }

    private AbstractModuleVersionState
        getModuleVersionState(String operation, String module, String version) {
      AbstractModuleVersionState moduleVersionState =
          moduleVersionStateMap.get(new ModuleVersionKey(module, version));
      if (moduleVersionState == null) {
        logger.warning("Operation " + operation + " could not find the requested module version "
            + " Module " + module
            + " version " + version);
        throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
              "Could not find the given version.");
      }
      return moduleVersionState;
    }

    /**
     * Throws an {@link IllegalStateException} if the specified module, version,
     * instance does not match a configured {@link ModuleVersion}.
     */
    private void verifyEnvironment(String module, String version, int instance) {
      AbstractModuleVersionState moduleVersionState =
          moduleVersionStateMap.get(new ModuleVersionKey(module, version));
      if (moduleVersionState == null) {
        throw new IllegalStateException("The LocalServiceTestHelper Environment module = '"
            + module + "' version = '" + version + "' specifies a module version that has"
            + " not been configured, either add the needed module version (with one of"
            + " LocalModulesServiceTestConfig.addDefaultModuleVersion,"
            + " LocalModulesServiceTestConfig.addAutomaticScalingModuleeVersion,"
            + " LocalModulesServiceTestConfig.addBasicScalingModuleVersion,"
            + " or LocalModulesServiceTestConfig.addManualScalingModuleVersion) or correct the"
            + " Environment (with LocalServiceTestHelper.setEnvModuleId and"
            + " LocalServiceTestHelper.setEnvVersionId)");
      }

      // If caller specified an instance
      if (instance != LocalEnvironment.MAIN_INSTANCE) {
        // If the specified module version is an automatic scaling instance
        if (moduleVersionState
            .getModuleVersion().getInitialNumInstances() == DYNAMIC_INSTANCE_COUNT) {
            throw new IllegalStateException("The requested module version module = '"
                + module + "' version = '" + version + "' does not support instances but"
                + " the LocalServiceTestHelper environment has instances set. You can correct this"
                + " issue by providing a matching manually scaling or basic scaling module version"
                + " to LocalModulesServiceTestConfig.setVersions or by calling"
                + " LocalServiceTestHelper.setEnvModuleInstance with"
                + " com.google.appengine.tools.development.LocalEnvironment.MAIN_INSTANCE"
                + " (-1) which is the default value");
        }
        if (instance >= moduleVersionState.getNumInstancesInternal()) {
            throw new IllegalStateException("The requested module version module = '"
                + module + "' version = '" + version + "' has only "
                + moduleVersionState.getNumInstancesInternal() + " instances and"
                + " the LocalServiceTestHelper environment has envInstance set to " + instance
                + " which is too big. You can correct this issue by defining more instances"
                + " for the module version with LocalModulesServiceTestConfig.setVersions or by"
                + " calling LocalServiceTestHelper.setEnvModuleInstance with a supported instance"
                + " or com.google.appengine.tools.development.LocalEnvironment.MAIN_INSTANCE"
                + " (-1) which is the default value");
        }
      }
    }
  }

  enum ScalingType {
    AUTOMATIC() {
      @Override
      AutomaticScalingModuleVersionState getModuleVersionState(ModuleVersion moduleVersion){
        return new AutomaticScalingModuleVersionState(moduleVersion);
      }
    },
    BASIC(){
      @Override
      BasicScalingModuleVersionState getModuleVersionState(ModuleVersion moduleVersion){
        return new BasicScalingModuleVersionState(moduleVersion);
      }
    },
    MANUAL(){
      @Override
      ManualScalingModuleVersionState getModuleVersionState(ModuleVersion moduleVersion){
        return new ManualScalingModuleVersionState(moduleVersion);
      }
    };
    abstract AbstractModuleVersionState getModuleVersionState(ModuleVersion moduleVersion);
  }

  private static class ModuleVersionKey {
    private final String module;
    private final String version;

    ModuleVersionKey(String module, String version) {
      this.module = module;
      this.version = version;
    }

    public String getModule() {
      return module;
    }

    public String getVersion() {
      return version;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((module == null) ? 0 : module.hashCode());
      result = prime * result + ((version == null) ? 0 : version.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ModuleVersionKey other = (ModuleVersionKey) obj;
      if (module == null) {
        if (other.module != null) {
          return false;
        }
      } else if (!module.equals(other.module)) {
        return false;
      }

      if (version == null) {
        if (other.version != null) {
          return false;
        }
      } else if (!version.equals(other.version)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "ModuleVersionKey: module=" + module + " version=" + version;
    }
  }

  private abstract static class AbstractModuleVersionState {
    protected final ModuleVersion moduleVersion;
    protected final AtomicInteger numInstances;

    AbstractModuleVersionState(ModuleVersion moduleVersion) {
      this.moduleVersion = moduleVersion;
      this.numInstances = new AtomicInteger(moduleVersion.getInitialNumInstances());
    }

    int getNumInstances() {
      logger.warning("Operation getNumInstances requires a manually scaling module version but "
          + " Module " + moduleVersion.getModule()
          + " version " + moduleVersion.getVersion()
          + " is not manual scaling.");
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
          "Could not find the given version.");
    }

    int getNumInstancesInternal() {
      return numInstances.get();
    }

    void setNumInstances(@SuppressWarnings("unused") int numInstances) {
      logger.warning("Operation setNumInstances requires a manually scaling version but "
          + " Module " + moduleVersion.getModule()
          + " version " + moduleVersion.getVersion()
          + " is not manual scaling.");
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
          "Cannot set the number of instances for a module that has automatic scaling.");
    }

    ModuleVersion getModuleVersion() {
      return moduleVersion;
    }

    void start()  {
      logger.info("Stopped  Module " + moduleVersion.getModule()
          + " version " + moduleVersion.getVersion());
    }

    void stop() {
      logger.info("Started  Module " + moduleVersion.getModule()
          + " version " + moduleVersion.getVersion());
    }

    String getInstanceDot(int instance) {
      if (instance >= numInstances.get()) {
      logger.warning("Operation getInstanceHostname failed because instances value "
          + instance + " is out of range for module " + moduleVersion.getModule()
          + " version " + moduleVersion.getVersion() + " numInstances " + numInstances.get());
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_INSTANCES_VALUE,
          "The specified instance does not exist for this module/version.");
      }
      return instance + ".";
    }

    @Override
    public String toString() {
      return "AbstractModuleVersionState (" + moduleVersion + ")"
          + (numInstances.get() == DYNAMIC_INSTANCE_COUNT ?
              "" : "numInstances =" + numInstances.get());
    }
  }

  static class AutomaticScalingModuleVersionState extends AbstractModuleVersionState {
    AutomaticScalingModuleVersionState(ModuleVersion moduleVersion) {
      super(moduleVersion);
    }

    @Override
    void start() {
      reportStartStop("startVersion");
    }

    @Override
    void stop() {
      reportStartStop("stopVersion");
    }

    @Override
    String getInstanceDot(int instance) {
      logger.warning("Operation getInstanceHostname not allowed for dynamic module version "
      + " module " + moduleVersion.getModule()
      + " version " + moduleVersion.getVersion());
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
          "The specified instance does not exist for this module/version.");
    }

    private void reportStartStop(String operation) {
      logger.warning("Automatic scaling module "
          + " module " + moduleVersion.getModule()
          + " version " + moduleVersion.getVersion()
          + " does not support "
          + operation);
      throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE,
          "Could not find the specified version.");
    }
  }

  static class ManualScalingModuleVersionState extends AbstractModuleVersionState {
    ManualScalingModuleVersionState(ModuleVersion moduleVersion) {
      super(moduleVersion);
    }


    @Override
    int getNumInstances() {
      return numInstances.get();
    }

    @Override
    void setNumInstances(int numInstances) {
      if (numInstances <= 0) {
        logger.warning("Operation setNumInstances failed with invalid instances value "
            + numInstances
            + " for module " + moduleVersion.getModule()
            + " version " + moduleVersion.getVersion());
        throw new ApplicationException(ModulesServiceError.ErrorCode.INVALID_INSTANCES_VALUE,
            "The number of instances must be greater than 0.");
      }
      this.numInstances.set(numInstances);
    }
  }

  static class BasicScalingModuleVersionState extends AbstractModuleVersionState {
    BasicScalingModuleVersionState(ModuleVersion moduleVersion) {
      super(moduleVersion);
    }
  }

  /**
   * Holder for configuration information for a module version within
   * {@link LocalModulesServiceTestConfig}.
   * <p>
   * Please refer to {@link LocalModulesServiceTestConfig} for usage information.
   */
  private static class ModuleVersion {
    private final ModuleVersionKey key;
    private final ScalingType scalingType;
    private final int initialNumInstances;

    /**
     * Creates a ModuleVersion.
     * <p>
     * If the caller passes in a version with a .minor-version suffix the suffix is removed.
     * @param module the module name
     * @param version the version with an optional .minor-version  suffix
     * @param scalingType the scaling type for the ModuleVersion
     * @param initialNumInstances the initial number of instances or {@link #DYNAMIC_INSTANCE_COUNT}
     *        for automatic scaling instances.
     */
    ModuleVersion(String module, String version, ScalingType scalingType, int initialNumInstances) {
      checkNotNull(module);
      checkNotNull(version);
      if (scalingType == ScalingType.AUTOMATIC) {
        checkArgument(
            initialNumInstances == DYNAMIC_INSTANCE_COUNT,
            "Automatic scaling module version module %s version %s must have initialNumInstances %s",
            module,
            version,
            DYNAMIC_INSTANCE_COUNT);
      } else {
        checkArgument(
            initialNumInstances != DYNAMIC_INSTANCE_COUNT,
            "Automatic scaling module version module %s version %s must not have initialNumInstances %s",
            module,
            version,
            DYNAMIC_INSTANCE_COUNT);
      }
      version = stripMinorVersion(version);
      this.key = new ModuleVersionKey(module, version);
      this.scalingType = scalingType;
      this.initialNumInstances = initialNumInstances;
    }

    String getModule() {
      return key.getModule();
    }

    String getVersion() {
      return key.getVersion();
    }

    int getInitialNumInstances() {
      return initialNumInstances;
    }

    ModuleVersionKey getKey() {
      return key;
    }

    ScalingType getScalingType() {
      return scalingType;
    }

    @Override
    public String toString() {
      return "ModuleVersion module=" + key.getModule()
          + " version=" + key.getVersion()
          + " scalingType=" + scalingType
          + (initialNumInstances == DYNAMIC_INSTANCE_COUNT ?
              "" : " initialNumInstances=" + initialNumInstances);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + initialNumInstances;
      result = prime * result + ((key == null) ? 0 : key.hashCode());
      result = prime * result + ((scalingType == null) ? 0 : scalingType.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ModuleVersion other = (ModuleVersion) obj;
      if (initialNumInstances != other.initialNumInstances) {
        return false;
      }
      if (key == null) {
        if (other.key != null) {
          return false;
        }
      } else if (!key.equals(other.key)) {
        return false;
      }
      if (scalingType != other.scalingType) {
        return false;
      }
      return true;
    }
  }
}
