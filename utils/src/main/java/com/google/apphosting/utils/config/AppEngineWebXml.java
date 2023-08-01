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

package com.google.apphosting.utils.config;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.security.Permissions;
import java.security.UnresolvedPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Struct describing the config data that lives in WEB-INF/appengine-web.xml.
 *
 * Any additions to this class should also be made to the YAML
 * version in AppYaml.java.
 *
 */
public class AppEngineWebXml implements Cloneable {
  /**
   * Enumeration of supported scaling types.
   */
  public static enum ScalingType {AUTOMATIC, MANUAL, BASIC}

  // System properties defined by the application in appengine-web.xml
  private final Map<String, String> systemProperties = Maps.newHashMap();

  // Beta settings defined by the application in appengine-web.xml.
  // Using a linked hash map is not strictly needed but makes testing easier.
  private final Map<String, String> betaSettings = Maps.newLinkedHashMap();

  private final Resources resources;

  private final Network network;

  private StagingOptions staging;

  private HealthCheck healthCheck;

  private LivenessCheck livenessCheck;

  private ReadinessCheck readinessCheck;

  // Environment variables defined by the application in appengine-web.xml
  private final Map<String, String> envVariables = Maps.newHashMap();

  private final Map<String, String> buildEnvVariables = Maps.newHashMap();

  private final List<UserPermission> userPermissions = new ArrayList<UserPermission>();

  public static final String WARMUP_SERVICE = "warmup";

  public static final String URL_HANDLER_URLFETCH = "urlfetch";
  public static final String URL_HANDLER_NATIVE = "native";
  // Runtime ids.
  // Should accept java8* for multiple variations of Java8.
  private static final String JAVA_8_RUNTIME_ID = "java8";
  // This was used for Java6, but now is used only for Managed VMs, not for standard editiom.
  private static final String JAVA_RUNTIME_ID = "java";
  private static final String JAVA_11_RUNTIME_ID = "java11";

  private String entrypoint;

  private String runtimeChannel;

  private String appId;

  private String majorVersionId;

  private String module;
  // Used to be module, but now replaced by service.
  private String service;
  private String instanceClass;

  private final AutomaticScaling automaticScaling;
  private final ManualScaling manualScaling;
  private final BasicScaling basicScaling;

  private String runtime;
  private boolean sslEnabled = true;
  private boolean useSessions = false;
  private boolean asyncSessionPersistence = false;
  private String asyncSessionPersistenceQueueName;

  private final List<StaticFileInclude> staticFileIncludes;
  private final List<String> staticFileExcludes;
  private final List<String> resourceFileIncludes;
  private final List<String> resourceFileExcludes;

  private Pattern staticIncludePattern;
  private Pattern staticExcludePattern;
  private Pattern resourceIncludePattern;
  private Pattern resourceExcludePattern;

  private String publicRoot = "";

  private String appRoot;

  private final Set<String> inboundServices;
  private boolean precompilationEnabled = true;

  private final List<AdminConsolePage> adminConsolePages = new ArrayList<AdminConsolePage>();
  private final List<ErrorHandler> errorHandlers = new ArrayList<ErrorHandler>();

  private ClassLoaderConfig classLoaderConfig;

  private VpcAccessConnector vpcAccessConnector;

  private String urlStreamHandlerType = null;

  // TODO: Set this to true at some future point.
  private boolean threadsafe = false;
  // Keeps track of whether or not there was a <threadsafe> element in the xml.
  // It would be easier to just make the threadsafe member variable a Boolean
  // but we can't change the public signature of this method.
  private boolean threadsafeValueProvided = false;

  // TODO: Revert once sequential auto id deprecation period expires.
  private String autoIdPolicy;

  // appEngineApis logic is true for Java8 by default(i.e field not used), and false for Java11.
  private boolean appEngineApis = false;

  private boolean codeLock = false;
  private boolean useVm = false;
  // Default env is standard (GAE V1)
  // TODO: Add a test emitting an error if "env" is present but it has an invalid value.
  private String env = "standard";
  private ApiConfig apiConfig;
  private final List<String> apiEndpointIds;

  private String serviceAccount;

  /**
   * Represent user's choice w.r.t the usage of Google's customized connector-j.
   */
  public static enum UseGoogleConnectorJ {
    NOT_STATED_BY_USER,
    TRUE,
    FALSE,
  }
  // Identify if the user has explicitly stated if the application wishes to use Google's
  // customized connector-j.
  private UseGoogleConnectorJ useGoogleConnectorJ = UseGoogleConnectorJ.NOT_STATED_BY_USER;

  public AppEngineWebXml() {
    automaticScaling = new AutomaticScaling();
    manualScaling = new ManualScaling();
    basicScaling = new BasicScaling();
    resources = new Resources();
    network = new Network();
    staging = StagingOptions.EMPTY;

    staticFileIncludes = new ArrayList<StaticFileInclude>();
    staticFileExcludes = new ArrayList<String>();
    staticFileExcludes.add("WEB-INF/**");
    staticFileExcludes.add("**.jsp");
    resourceFileIncludes = new ArrayList<String>();
    resourceFileExcludes = new ArrayList<String>();
    inboundServices = new LinkedHashSet<String>();
    apiEndpointIds = new ArrayList<String>();
  }

  @Override
  public AppEngineWebXml clone() {
    try {
      return (AppEngineWebXml) super.clone();
    } catch (CloneNotSupportedException ce) {
      throw new RuntimeException("Could not clone AppEngineWebXml", ce);
    }
  }

  /**
   * @return An unmodifiable map whose entries correspond to the
   * system properties defined in appengine-web.xml.
   */
  public Map<String, String> getSystemProperties() {
    return Collections.unmodifiableMap(systemProperties);
  }

  public void addSystemProperty(String key, String value) {
    systemProperties.put(key, value);
  }

  /**
   * @return An unmodifiable map whose entires correspond to the
   * vm settings defined in appengine-web.xml.
   */
  public Map<String, String> getBetaSettings() {
    return Collections.unmodifiableMap(betaSettings);
  }

  public void addBetaSetting(String key, String value) {
    betaSettings.put(key, value);
  }

  public void setHealthCheck(HealthCheck healthCheck) {
    this.healthCheck = healthCheck;
  }

  public HealthCheck getHealthCheck() {
    return healthCheck;
  }

  public void setLivenessCheck(LivenessCheck livenessCheck) {
    this.livenessCheck = livenessCheck;
  }

  public LivenessCheck getLivenessCheck() {
    return livenessCheck;
  }

  public void setReadinessCheck(ReadinessCheck readinessCheck) {
    this.readinessCheck = readinessCheck;
  }

  public ReadinessCheck getReadinessCheck() {
    return readinessCheck;
  }

  public Resources getResources() {
    return resources;
  }

  public Network getNetwork() {
    return network;
  }

  public StagingOptions getStagingOptions() {
    return staging;
  }

  public void setStagingOptions(StagingOptions opts) {
    staging = opts;
  }

  /**
   * @return An unmodifiable map whose entires correspond to the
   * environment variables defined in appengine-web.xml.
   */
  public Map<String, String> getEnvironmentVariables() {
    return Collections.unmodifiableMap(envVariables);
  }

  public void addEnvironmentVariable(String key, String value) {
    envVariables.put(key, value);
  }

  /**
   * @return An unmodifiable map whose entries correspond to the build environment variables defined
   *     in appengine-web.xml.
   */
  public Map<String, String> getBuildEnvironmentVariables() {
    return Collections.unmodifiableMap(buildEnvVariables);
  }

  public void addBuildEnvironmentVariable(String key, String value) {
    buildEnvVariables.put(key, value);
  }

  public String getEntrypoint() {
    return entrypoint;
  }

  public void setEntrypoint(String entrypoint) {
    this.entrypoint = entrypoint;
  }

  public String getRuntimeChannel() {
    return runtimeChannel;
  }

  public void setRuntimeChannel(String runtimeChannel) {
    this.runtimeChannel = runtimeChannel;
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getMajorVersionId() {
    return majorVersionId;
  }

  public void setMajorVersionId(String majorVersionId) {
    this.majorVersionId = majorVersionId;
  }

  public String getRuntime() {
    if (runtime != null) {
      return runtime;
    }
    // The new env:flex means java, not java7:
    if (isFlexible()) {
      runtime = JAVA_RUNTIME_ID;
    } else {
      runtime = JAVA_8_RUNTIME_ID;
    }
    return runtime;
  }

  /*
   * Test the runtime (java8*) to tell if web.xml is mandatory or not.
   */
  public boolean isWebXmlRequired() {
    return false;
  }

  /*
   * Test if the runtime is at least Java11.
   */
  public boolean isJava11OrAbove() {
    return getRuntime().equals("google")
        || getRuntime().equals("googlelegacy")
        || getRuntime().equals("java11")
        || getRuntime().equals("java17")
        || getRuntime().equals("java21");
  }

  public void setRuntime(String runtime) {
    this.runtime = runtime;
  }

  public String getModule() {
    return module;
  }

  public String getService() {
    return service;
  }

  /**
   * Sets instanceClass (aka class in the xml/yaml files). Normalizes empty and null
   * inputs to null.
   */
  public void setInstanceClass(String instanceClass) {
    this.instanceClass = toNullIfEmptyOrWhitespace(instanceClass);
  }

  public String getInstanceClass() {
    return instanceClass;
  }

  public AutomaticScaling getAutomaticScaling() {
    return automaticScaling;
  }

  public ManualScaling getManualScaling() {
    return manualScaling;
  }

  public BasicScaling getBasicScaling() {
    return basicScaling;
  }

  public ScalingType getScalingType() {
    if (!getBasicScaling().isEmpty()) {
      return ScalingType.BASIC;
    } else if (!getManualScaling().isEmpty()) {
      return ScalingType.MANUAL;
    } else {
      return ScalingType.AUTOMATIC;
    }
  }

  public void setModule(String module) {
    this.module = module;
  }

  public void setService(String service) {
    this.service = service;
  }

  public void setSslEnabled(boolean ssl) {
    sslEnabled = ssl;
  }

  public boolean getSslEnabled() {
    return sslEnabled;
  }

  public void setSessionsEnabled(boolean sessions) {
    useSessions = sessions;
  }

  public boolean getSessionsEnabled() {
    return useSessions;
  }

  public void setAsyncSessionPersistence(boolean asyncSessionPersistence) {
    this.asyncSessionPersistence = asyncSessionPersistence;
  }

  public boolean getAsyncSessionPersistence() {
    return asyncSessionPersistence;
  }

  public void setAsyncSessionPersistenceQueueName(String asyncSessionPersistenceQueueName) {
    this.asyncSessionPersistenceQueueName = asyncSessionPersistenceQueueName;
  }

  public String getAsyncSessionPersistenceQueueName() {
    return asyncSessionPersistenceQueueName;
  }

  public List<StaticFileInclude> getStaticFileIncludes() {
    return staticFileIncludes;
  }

  public List<String> getStaticFileExcludes() {
    return staticFileExcludes;
  }

  public StaticFileInclude includeStaticPattern(String pattern, String expiration) {
    staticIncludePattern = null;
    StaticFileInclude staticFileInclude = new StaticFileInclude(pattern, expiration);
    staticFileIncludes.add(staticFileInclude);
    return staticFileInclude;
  }

  public void excludeStaticPattern(String url) {
    staticExcludePattern = null;
    staticFileExcludes.add(url);
  }

  public List<String> getResourcePatterns() {
    return resourceFileIncludes;
  }

  public List<String> getResourceFileExcludes() {
    return resourceFileExcludes;
  }

  public void includeResourcePattern(String url) {
    resourceExcludePattern = null;
    resourceFileIncludes.add(url);
  }

  public void excludeResourcePattern(String url) {
    resourceIncludePattern = null;
    resourceFileExcludes.add(url);
  }

  public void addUserPermission(String className, String name, String actions) {
    if (className.startsWith("java.")) {
      throw new AppEngineConfigException("Cannot specify user-permissions for " +
                                         "classes in java.* packages.");
    }

    userPermissions.add(UserPermission.of(className, name, actions));
  }

  public Permissions getUserPermissions() {
    Permissions permissions = new Permissions();
    for (UserPermission permission : userPermissions) {
      permissions.add(new UnresolvedPermission(permission.getClassName(),
                                               permission.getName(),
                                               permission.getActions(),
                                               null));
    }
    permissions.setReadOnly();
    return permissions;
  }


  public void setPublicRoot(String root) {
    if (root.indexOf('*') != -1) {
      throw new AppEngineConfigException("public-root cannot contain wildcards");
    }
    if (root.endsWith("/")) {
        root = root.substring(0, root.length() - 1);
    }
    if (root.length() > 0 && !root.startsWith("/")) {
      root = "/" + root;
    }
    staticIncludePattern = null;
    publicRoot = root;
  }

  public String getPublicRoot() {
    return publicRoot;
  }

  public void addInboundService(String service) {
    inboundServices.add(service);
  }

  public Set<String> getInboundServices() {
    return inboundServices;
  }

  public boolean getPrecompilationEnabled() {
    return precompilationEnabled;
  }

  public void setPrecompilationEnabled(boolean precompilationEnabled) {
    this.precompilationEnabled = precompilationEnabled;
  }

  public boolean getWarmupRequestsEnabled() {
    return inboundServices.contains(WARMUP_SERVICE);
  }

  public void setWarmupRequestsEnabled(boolean warmupRequestsEnabled) {
    if (warmupRequestsEnabled) {
      inboundServices.add(WARMUP_SERVICE);
    } else {
      inboundServices.remove(WARMUP_SERVICE);
    }
  }

  public List<AdminConsolePage> getAdminConsolePages() {
    return Collections.unmodifiableList(adminConsolePages);
  }

  public void addAdminConsolePage(AdminConsolePage page) {
    adminConsolePages.add(page);
  }

  public List<ErrorHandler> getErrorHandlers() {
    return Collections.unmodifiableList(errorHandlers);
  }

  public void addErrorHandler(ErrorHandler handler) {
    errorHandlers.add(handler);
  }

  public boolean getThreadsafe() {
    return threadsafe;
  }

  public boolean getAppEngineApis() {
    return appEngineApis;
  }

  public void setAppEngineApis(boolean appEngineApis) {
    this.appEngineApis = appEngineApis;
  }

  public boolean getThreadsafeValueProvided() {
    return threadsafeValueProvided;
  }

  public void setThreadsafe(boolean threadsafe) {
    this.threadsafe = threadsafe;
    this.threadsafeValueProvided = true;
  }

  public void setAutoIdPolicy(String policy) {
    autoIdPolicy = policy;
  }

  public String getAutoIdPolicy() {
    return autoIdPolicy;
  }

  public boolean getCodeLock() {
    return codeLock;
  }

  public void setCodeLock(boolean codeLock) {
    this.codeLock = codeLock;
  }

  public void setUseVm(boolean useVm) {
    this.useVm = useVm;
  }

  public boolean getUseVm() {
    return useVm;
  }

  public void setEnv(String env) {
    this.env = env;
  }

  public String getEnv() {
    return env;
  }

  public boolean isFlexible() {
    return ("flex".equalsIgnoreCase(env) || "2".equals(env) || "flexible".equalsIgnoreCase(env));
  }

  public ApiConfig getApiConfig() {
    return apiConfig;
  }

  public void setApiConfig(ApiConfig config) {
    apiConfig = config;
  }

  public ClassLoaderConfig getClassLoaderConfig() {
    return classLoaderConfig;
  }

  public void setClassLoaderConfig(ClassLoaderConfig classLoaderConfig) {
    if (this.classLoaderConfig != null) {
      throw new AppEngineConfigException("class-loader-config may only be specified once.");
    }
    this.classLoaderConfig = classLoaderConfig;
  }

  public VpcAccessConnector getVpcAccessConnector() {
    return vpcAccessConnector;
  }

  public void setVpcAccessConnector(VpcAccessConnector vpcAccessConnector) {
    if (this.vpcAccessConnector != null) {
      throw new AppEngineConfigException("vpc-access-connector may only be specified once.");
    }
    this.vpcAccessConnector = vpcAccessConnector;
  }

  public void setServiceAccount(String serviceAccount) {
    this.serviceAccount = toNullIfEmptyOrWhitespace(serviceAccount);
  }

  public String getServiceAccount() {
    return serviceAccount;
  }


  public String getUrlStreamHandlerType() {
    return urlStreamHandlerType;
  }

  public void setUrlStreamHandlerType(String urlStreamHandlerType) {
    if (this.classLoaderConfig != null) {
      throw new AppEngineConfigException("url-stream-handler may only be specified once.");
    }
    if (!URL_HANDLER_URLFETCH.equals(urlStreamHandlerType)
        && !URL_HANDLER_NATIVE.equals(urlStreamHandlerType)) {
      throw new AppEngineConfigException(
          "url-stream-handler must be " + URL_HANDLER_URLFETCH + " or " + URL_HANDLER_NATIVE +
          " given " + urlStreamHandlerType);
    }
    this.urlStreamHandlerType = urlStreamHandlerType;
  }

  /**
   * Returns true if {@code url} matches one of the servlets or servlet
   * filters listed in this web.xml that has api-endpoint set to true.
   */
  public boolean isApiEndpoint(String id) {
    return apiEndpointIds.contains(id);
  }

  public void addApiEndpoint(String id) {
    apiEndpointIds.add(id);
  }

  public void setUseGoogleConnectorJ(boolean useGoogleConnectorJ) {
    if (useGoogleConnectorJ) {
      this.useGoogleConnectorJ = UseGoogleConnectorJ.TRUE;
    } else {
      this.useGoogleConnectorJ = UseGoogleConnectorJ.FALSE;
    }
  }

  public UseGoogleConnectorJ getUseGoogleConnectorJ() {
    return useGoogleConnectorJ;
  }

  @Override
  public String toString() {
    return "AppEngineWebXml{"
        + "systemProperties="
        + systemProperties
        + ", envVariables="
        + envVariables
        + ", buildEnvVariables="
        + buildEnvVariables
        + ", userPermissions="
        + userPermissions
        + ", appId='"
        + appId
        + '\''
        + ", majorVersionId='"
        + majorVersionId
        + '\''
        + ", runtime='"
        + runtime
        + '\''
        + ", service='"
        + service
        + '\''
        + ", instanceClass='"
        + instanceClass
        + '\''
        + ", automaticScaling="
        + automaticScaling
        + ", manualScaling="
        + manualScaling
        + ", basicScaling="
        + basicScaling
        + ", healthCheck="
        + healthCheck
        + ", livenesCheck="
        + livenessCheck
        + ", readinessCheck="
        + readinessCheck
        + ", resources="
        + resources
        + ", network="
        + network
        + ", sslEnabled="
        + sslEnabled
        + ", useSessions="
        + useSessions
        + ", asyncSessionPersistence="
        + asyncSessionPersistence
        + ", asyncSessionPersistenceQueueName='"
        + asyncSessionPersistenceQueueName
        + '\''
        + ", staticFileIncludes="
        + staticFileIncludes
        + ", staticFileExcludes="
        + staticFileExcludes
        + ", resourceFileIncludes="
        + resourceFileIncludes
        + ", resourceFileExcludes="
        + resourceFileExcludes
        + ", staticIncludePattern="
        + staticIncludePattern
        + ", staticExcludePattern="
        + staticExcludePattern
        + ", resourceIncludePattern="
        + resourceIncludePattern
        + ", resourceExcludePattern="
        + resourceExcludePattern
        + ", publicRoot='"
        + publicRoot
        + '\''
        + ", appRoot='"
        + appRoot
        + '\''
        + ", inboundServices="
        + inboundServices
        + ", precompilationEnabled="
        + precompilationEnabled
        + ", adminConsolePages="
        + adminConsolePages
        + ", errorHandlers="
        + errorHandlers
        + ", threadsafe="
        + threadsafe
        + ", threadsafeValueProvided="
        + threadsafeValueProvided
        + ", autoIdPolicy="
        + autoIdPolicy
        + ", codeLock="
        + codeLock
        + ", apiConfig="
        + apiConfig
        + ", apiEndpointIds="
        + apiEndpointIds
        + ", classLoaderConfig="
        + classLoaderConfig
        + ", urlStreamHandlerType="
        + (urlStreamHandlerType == null ? URL_HANDLER_URLFETCH : urlStreamHandlerType)
        + ", useGoogleConnectorJ="
        + useGoogleConnectorJ
        + ", vpcAccessConnector="
        + vpcAccessConnector
        + ", entrypoint="
        + entrypoint
        + ", runtimeChannel="
        + runtimeChannel
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AppEngineWebXml that = (AppEngineWebXml) o;

    return asyncSessionPersistence == that.asyncSessionPersistence
        && precompilationEnabled == that.precompilationEnabled
        && sslEnabled == that.sslEnabled
        && threadsafe == that.threadsafe
        && appEngineApis == that.appEngineApis
        && threadsafeValueProvided == that.threadsafeValueProvided
        && Objects.equals(autoIdPolicy, that.autoIdPolicy)
        && codeLock == that.codeLock
        && useSessions == that.useSessions
        && Objects.equals(adminConsolePages, that.adminConsolePages)
        && Objects.equals(appId, that.appId)
        && Objects.equals(entrypoint, that.entrypoint)
        && Objects.equals(runtimeChannel, that.runtimeChannel)
        && Objects.equals(majorVersionId, that.majorVersionId)
        && Objects.equals(service, that.service)
        && Objects.equals(instanceClass, that.instanceClass)
        && automaticScaling.equals(that.automaticScaling)
        && manualScaling.equals(that.manualScaling)
        && basicScaling.equals(that.basicScaling)
        && Objects.equals(appRoot, that.appRoot)
        && Objects.equals(asyncSessionPersistenceQueueName, that.asyncSessionPersistenceQueueName)
        && Objects.equals(envVariables, that.envVariables)
        && Objects.equals(buildEnvVariables, that.buildEnvVariables)
        && Objects.equals(errorHandlers, that.errorHandlers)
        && Objects.equals(inboundServices, that.inboundServices)
        && Objects.equals(majorVersionId, that.majorVersionId)
        && Objects.equals(runtime, that.runtime)
        && Objects.equals(publicRoot, that.publicRoot)
        && Objects.equals(resourceExcludePattern, that.resourceExcludePattern)
        && Objects.equals(resourceFileExcludes, that.resourceFileExcludes)
        && Objects.equals(resourceFileIncludes, that.resourceFileIncludes)
        && Objects.equals(resourceIncludePattern, that.resourceIncludePattern)
        && Objects.equals(staticExcludePattern, that.staticExcludePattern)
        && Objects.equals(staticFileExcludes, that.staticFileExcludes)
        && Objects.equals(staticFileIncludes, that.staticFileIncludes)
        && Objects.equals(staticIncludePattern, that.staticIncludePattern)
        && Objects.equals(systemProperties, that.systemProperties)
        && Objects.equals(betaSettings, that.betaSettings)
        && Objects.equals(healthCheck, that.healthCheck)
        && Objects.equals(livenessCheck, that.livenessCheck)
        && Objects.equals(readinessCheck, that.readinessCheck)
        && Objects.equals(resources, that.resources)
        && Objects.equals(network, that.network)
        && Objects.equals(userPermissions, that.userPermissions)
        && Objects.equals(apiConfig, that.apiConfig)
        && Objects.equals(apiEndpointIds, that.apiEndpointIds)
        && Objects.equals(classLoaderConfig, that.classLoaderConfig)
        && Objects.equals(vpcAccessConnector, that.vpcAccessConnector)
        && Objects.equals(serviceAccount, that.serviceAccount)
        && Objects.equals(urlStreamHandlerType, that.urlStreamHandlerType)
        && useGoogleConnectorJ == that.useGoogleConnectorJ;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        systemProperties,
        envVariables,
        buildEnvVariables,
        userPermissions,
        appId,
        majorVersionId,
        runtime,
        service,
        instanceClass,
        automaticScaling,
        manualScaling,
        basicScaling,
        sslEnabled,
        useSessions,
        asyncSessionPersistence,
        asyncSessionPersistenceQueueName,
        staticFileIncludes,
        staticFileExcludes,
        resourceFileIncludes,
        resourceFileExcludes,
        staticIncludePattern,
        staticExcludePattern,
        resourceIncludePattern,
        resourceExcludePattern,
        publicRoot,
        appRoot,
        inboundServices,
        precompilationEnabled,
        adminConsolePages,
        errorHandlers,
        threadsafe,
        appEngineApis,
        autoIdPolicy,
        threadsafeValueProvided,
        codeLock,
        apiConfig,
        apiEndpointIds,
        classLoaderConfig,
        vpcAccessConnector,
        serviceAccount,
        urlStreamHandlerType,
        useGoogleConnectorJ,
        betaSettings,
        healthCheck,
        livenessCheck,
        readinessCheck,
        resources,
        network,
        entrypoint,
        runtimeChannel);
  }

  public boolean includesResource(String path) {
    if (resourceIncludePattern == null) {
      if (resourceFileIncludes.size() == 0) {
        // if the user doesn't give any includes, we want everything
        resourceIncludePattern = Pattern.compile(".*");
      } else {
        resourceIncludePattern = Pattern.compile(makeRegexp(resourceFileIncludes));
      }
    }
    if (resourceExcludePattern == null && resourceFileExcludes.size() > 0) {
      resourceExcludePattern = Pattern.compile(makeRegexp(resourceFileExcludes));
    } else {
      // if there are no resourceFileExcludes, let the pattern stay NULL.
    }
    return includes(path, resourceIncludePattern, resourceExcludePattern);
  }

  public boolean includesStatic(String path) {
    if (staticIncludePattern == null) {
      if (staticFileIncludes.size() == 0) {
        // if the user doesn't give any includes, we want everything under
        // publicRoot
        String staticRoot;
        if (publicRoot.length() > 0) {
          staticRoot = publicRoot + "/**";
        } else {
          staticRoot = "**";
        }
        staticIncludePattern = Pattern.compile(
            makeRegexp(Collections.singletonList(staticRoot)));
      } else {
        List<String> patterns = new ArrayList<String>();
        for (StaticFileInclude include : staticFileIncludes) {
          patterns.add(include.getPattern());
        }
        staticIncludePattern = Pattern.compile(makeRegexp(patterns));
      }
    }
    if (staticExcludePattern == null && staticFileExcludes.size() > 0) {
      staticExcludePattern = Pattern.compile(makeRegexp(staticFileExcludes));
    } else {
      // if there are no staticFileExcludes, let the pattern stay NULL.
    }
    return includes(path, staticIncludePattern, staticExcludePattern);
  }

  /**
   * Tests whether {@code path} is covered by the pattern {@code includes}
   * while not being blocked by matching {@code excludes}.
   *
   * @param path a URL to test
   * @param includes a non-{@code null} pattern for included URLs
   * @param excludes a pattern for exclusion, or {@code null} to not exclude
   *    anything from the {@code includes} set.
   */
  public boolean includes(String path, Pattern includes, Pattern excludes) {
    assert(includes != null);
    if (!includes.matcher(path).matches()) {
      return false;
    }
    if (excludes != null && excludes.matcher(path).matches()) {
      return false;
    }
    return true;
  }

  public String makeRegexp(List<String> patterns) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String item : patterns) {
      if (first) {
        first = false;
      } else {
        builder.append('|');
      }

      // Trim any leading slashes from item.
      while (item.charAt(0) == '/') {
        item = item.substring(1);
      }

      builder.append('(');
      if (appRoot != null) {
        builder.append(makeFileRegex(appRoot));
      }
      builder.append("/");
      builder.append(makeFileRegex(item));
      builder.append(')');
    }
    return builder.toString();
  }

  /**
   * Helper method to translate from appengine-web.xml "file globs" to
   * proper regular expressions as used in app.yaml.
   *
   * @param fileGlob the glob to translate
   * @return the regular expression string matching the input {@code file} pattern.
   */
  static String makeFileRegex(String fileGlob) {
    // escape metacharacters, and replace '*' with regexp '[^/]*' and '**' with '.*'
    fileGlob = fileGlob.replaceAll("([^A-Za-z0-9\\-_/])", "\\\\$1");
    fileGlob = fileGlob.replaceAll("\\\\\\*\\\\\\*", ".*");
    fileGlob = fileGlob.replaceAll("\\\\\\*", "[^/]*");
    return fileGlob;
  }
  /**
   * Sets the application root directory, as a prefix for the regexps in
   * {@link #includeResourcePattern(String)} and friends.  This is needed
   * because we want to match complete filenames relative to root.
   *
   * @param appRoot
   */
  public void setSourcePrefix(String appRoot) {
    this.appRoot = appRoot;
    // Invalidate the pattern cache because the patterns
    // were generated with the previous root.
    this.resourceIncludePattern = null;
    this.resourceExcludePattern = null;
    this.staticIncludePattern = null;
    this.staticExcludePattern = null;
  }

  public String getSourcePrefix() {
    return this.appRoot;
  }

  private static String toNullIfEmptyOrWhitespace(String string) {
    if (string == null || CharMatcher.whitespace().matchesAllOf(string)) {
      return null;
    }
    return string;
  }

  /**
   * Represents a {@link java.security.Permission} that needs to be
   * granted to user code.
   */
  @AutoValue
  abstract static class UserPermission {
    abstract String getClassName();
    abstract String getName();
    @Nullable
    abstract String getActions();

    static UserPermission of(String className, String name, String actions) {
      return new AutoValue_AppEngineWebXml_UserPermission(className, name, actions);
    }
  }

  /**
   * Represents an {@code <include>} element within the {@code <static-files>} element. Currently
   * this includes both a pattern and an optional expiration time specification.
   */
  // TODO: convert to AutoValue. That means getting rid of the mutable Map field.
  public static class StaticFileInclude {
    private final String pattern;
    private final String expiration;
    private final Map<String, String> httpHeaders;

    public StaticFileInclude(String pattern, String expiration) {
      this.pattern = pattern;
      this.expiration = expiration;
      this.httpHeaders = new LinkedHashMap<>();
    }

    public String getPattern() {
      return pattern;
    }

    public Pattern getRegularExpression() {
      return Pattern.compile(makeFileRegex(pattern));
    }

    public String getExpiration() {
      return expiration;
    }

    public Map<String, String> getHttpHeaders() {
      return httpHeaders;
    }

    @Override
    public int hashCode() {
      return Objects.hash(pattern, expiration, httpHeaders);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof StaticFileInclude)) {
        return false;
      }

      StaticFileInclude other = (StaticFileInclude) obj;
      return Objects.equals(pattern, other.pattern)
          && Objects.equals(expiration, other.expiration)
          && Objects.equals(httpHeaders, other.httpHeaders);
    }
  }

  /** * Represents a {@code <page>} element within the {@code <admin-console>} element. */
  @AutoValue
  public abstract static class AdminConsolePage {
    public abstract String getName();
    public abstract String getUrl();

    public static AdminConsolePage of(String name, String url) {
      return new AutoValue_AppEngineWebXml_AdminConsolePage(name, url);
    }
  }

  /** Represents a {@code <vpc-access-connector>} element. Currently this includes only a name. */
  @AutoValue
  public abstract static class VpcAccessConnector {
    public abstract String getName();

    public abstract Optional<String> getEgressSetting();

    /** Returns a builder for a VpcAccessConnector with the given name. */
    public static Builder builderFor(String name) {
      return new AutoValue_AppEngineWebXml_VpcAccessConnector.Builder().setName(name);
    }

    /** Builder for this class. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setName(String name);
      public abstract Builder setEgressSetting(String egressSetting);

      public abstract VpcAccessConnector build();
    }
  }

  /**
   * Represents an {@code <error-handler>} element.  Currently this includes both
   * a file name and an optional error code.
   */
  @AutoValue
  public abstract static class ErrorHandler {
    public abstract String getFile();
    @Nullable
    public abstract String getErrorCode();

    public static ErrorHandler of(String file, String errorCode) {
      return new AutoValue_AppEngineWebXml_ErrorHandler(file, errorCode);
    }
  }

  /**
   * Represents an {@code <api-config>} element.  This is a singleton specifying
   * url-pattern and servlet-class for the api config server.
   */
  @AutoValue
  public abstract static class ApiConfig {
    public abstract String getServletClass();
    public abstract String getUrl();

    public static ApiConfig of(String servletClass, String url) {
      return new AutoValue_AppEngineWebXml_ApiConfig(servletClass, url);
    }
  }

  /**
   * Holder for automatic settings.
   */
  public static class AutomaticScaling {
    /*
     * AutomaticScaling with no fields set.
     *
     * Keep this private because AutomaticScaling is mutable.
     */
    private static final AutomaticScaling EMPTY_SETTINGS = new AutomaticScaling();

    public static final String AUTOMATIC = "automatic";
    private String minPendingLatency;
    private String maxPendingLatency;
    private String minIdleInstances;
    private String maxIdleInstances;
    private String maxConcurrentRequests;

    private Integer minNumInstances;
    private Integer maxNumInstances;
    private Integer coolDownPeriodSec;
    private CpuUtilization cpuUtilization;
    private List<CustomMetricUtilization> customMetrics = new ArrayList<>();

    private Integer targetNetworkSentBytesPerSec;
    private Integer targetNetworkSentPacketsPerSec;
    private Integer targetNetworkReceivedBytesPerSec;
    private Integer targetNetworkReceivedPacketsPerSec;
    private Integer targetDiskWriteBytesPerSec;
    private Integer targetDiskWriteOpsPerSec;
    private Integer targetDiskReadBytesPerSec;
    private Integer targetDiskReadOpsPerSec;
    private Integer targetRequestCountPerSec;
    private Integer targetConcurrentRequests;

    // new Fields for the new Standard Clone Scheduler:
    private Double targetCpuUtilization;
    private Double targetThroughputUtilization;
    private Integer minInstances;
    private Integer maxInstances;

    public String getMinPendingLatency() {
      return minPendingLatency;
    }

    /**
     * Sets minPendingLatency. Normalizes empty and null inputs to null.
     */
    public void setMinPendingLatency(String minPendingLatency) {
      this.minPendingLatency = toNullIfEmptyOrWhitespace(minPendingLatency);
    }

    public String getMaxPendingLatency() {
      return maxPendingLatency;
    }

    /**
     * Sets maxPendingLatency. Normalizes empty and null inputs to null.
     */
    public void setMaxPendingLatency(String maxPendingLatency) {
      this.maxPendingLatency =  toNullIfEmptyOrWhitespace(maxPendingLatency);
    }

    public String getMinIdleInstances() {
      return minIdleInstances;
    }

    /**
     * Sets minIdleInstances. Normalizes empty and null inputs to null.
     */
    public void setMinIdleInstances(String minIdleInstances) {
      this.minIdleInstances =  toNullIfEmptyOrWhitespace(minIdleInstances);
    }

    public String getMaxIdleInstances() {
      return maxIdleInstances;
    }

    /**
     * Sets maxIdleInstances. Normalizes empty and null inputs to null.
     */
    public void setMaxIdleInstances(String maxIdleInstances) {
      this.maxIdleInstances =  toNullIfEmptyOrWhitespace(maxIdleInstances);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    public String getMaxConcurrentRequests() {
      return maxConcurrentRequests;
    }

    /**
     * Sets maxConcurrentRequests. Normalizes empty and null inputs to null.
     */
    public void setMaxConcurrentRequests(String maxConcurrentRequests) {
      this.maxConcurrentRequests =  toNullIfEmptyOrWhitespace(maxConcurrentRequests);
    }

    public Integer getMinNumInstances() {
      return minNumInstances;
    }

    public void setMinNumInstances(Integer minNumInstances) {
      this.minNumInstances = minNumInstances;
    }

    public Integer getMaxNumInstances() {
      return maxNumInstances;
    }

    public void setMaxNumInstances(Integer maxNumInstances) {
      this.maxNumInstances = maxNumInstances;
    }

    public Double getTargetCpuUtilization() {
      return targetCpuUtilization;
    }

    public void setTargetCpuUtilization(Double targetCpuUtilization) {
      this.targetCpuUtilization = targetCpuUtilization;
    }

    public Double getTargetThroughputUtilization() {
      return targetThroughputUtilization;
    }

    public void setTargetThroughputUtilization(Double targetThroughputUtilization) {
      this.targetThroughputUtilization = targetThroughputUtilization;
    }

    public Integer getMinInstances() {
      return minInstances;
    }

    public void setMinInstances(Integer minInstances) {
      this.minInstances = minInstances;
    }

    public Integer getMaxInstances() {
      return maxInstances;
    }

    public void setMaxInstances(Integer maxInstances) {
      this.maxInstances = maxInstances;
    }

    public Integer getCoolDownPeriodSec() {
      return coolDownPeriodSec;
    }

    public void setCoolDownPeriodSec(Integer coolDownPeriodSec) {
      this.coolDownPeriodSec = coolDownPeriodSec;
    }

    public CpuUtilization getCpuUtilization() {
      return cpuUtilization;
    }

    public void setCpuUtilization(CpuUtilization cpuUtilization) {
      this.cpuUtilization = cpuUtilization;
    }

    public Integer getTargetNetworkSentBytesPerSec() {
      return targetNetworkSentBytesPerSec;
    }

    public void setTargetNetworkSentBytesPerSec(Integer targetNetworkSentBytesPerSec) {
      this.targetNetworkSentBytesPerSec = targetNetworkSentBytesPerSec;
    }

    public Integer getTargetNetworkSentPacketsPerSec() {
      return targetNetworkSentPacketsPerSec;
    }

    public void setTargetNetworkSentPacketsPerSec(Integer targetNetworkSentPacketsPerSec) {
      this.targetNetworkSentPacketsPerSec = targetNetworkSentPacketsPerSec;
    }

    public Integer getTargetNetworkReceivedBytesPerSec() {
      return targetNetworkReceivedBytesPerSec;
    }

    public void setTargetNetworkReceivedBytesPerSec(Integer targetNetworkReceivedBytesPerSec) {
      this.targetNetworkReceivedBytesPerSec = targetNetworkReceivedBytesPerSec;
    }

    public Integer getTargetNetworkReceivedPacketsPerSec() {
      return targetNetworkReceivedPacketsPerSec;
    }

    public void setTargetNetworkReceivedPacketsPerSec(Integer targetNetworkReceivedPacketsPerSec) {
      this.targetNetworkReceivedPacketsPerSec = targetNetworkReceivedPacketsPerSec;
    }

    public Integer getTargetDiskWriteBytesPerSec() {
      return targetDiskWriteBytesPerSec;
    }

    public void setTargetDiskWriteBytesPerSec(Integer targetDiskWriteBytesPerSec) {
      this.targetDiskWriteBytesPerSec = targetDiskWriteBytesPerSec;
    }

    public Integer getTargetDiskWriteOpsPerSec() {
      return targetDiskWriteOpsPerSec;
    }

    public void setTargetDiskWriteOpsPerSec(Integer targetDiskWriteOpsPerSec) {
      this.targetDiskWriteOpsPerSec = targetDiskWriteOpsPerSec;
    }

    public Integer getTargetDiskReadBytesPerSec() {
      return targetDiskReadBytesPerSec;
    }

    public void setTargetDiskReadBytesPerSec(Integer targetDiskReadBytesPerSec) {
      this.targetDiskReadBytesPerSec = targetDiskReadBytesPerSec;
    }

    public Integer getTargetDiskReadOpsPerSec() {
      return targetDiskReadOpsPerSec;
    }

    public void setTargetDiskReadOpsPerSec(Integer targetDiskReadOpsPerSec) {
      this.targetDiskReadOpsPerSec = targetDiskReadOpsPerSec;
    }

    public Integer getTargetRequestCountPerSec() {
      return targetRequestCountPerSec;
    }

    public void setTargetRequestCountPerSec(Integer targetRequestCountPerSec) {
      this.targetRequestCountPerSec = targetRequestCountPerSec;
    }

    public Integer getTargetConcurrentRequests() {
      return targetConcurrentRequests;
    }

    public void setTargetConcurrentRequests(Integer targetConcurrentRequests) {
      this.targetConcurrentRequests = targetConcurrentRequests;
    }

    public List<CustomMetricUtilization> getCustomMetrics() {
      return customMetrics;
    }

    public void setCustomMetrics(List<CustomMetricUtilization> customMetrics) {
      this.customMetrics = customMetrics;
    }

    @Override
    public int hashCode() {
      return Objects.hash(maxPendingLatency, minPendingLatency, maxIdleInstances,
          minIdleInstances, maxConcurrentRequests, minNumInstances,
          maxNumInstances, coolDownPeriodSec, cpuUtilization, customMetrics,
          targetNetworkSentBytesPerSec, targetNetworkSentPacketsPerSec,
          targetNetworkReceivedBytesPerSec, targetNetworkReceivedPacketsPerSec,
          targetDiskWriteBytesPerSec, targetDiskWriteOpsPerSec,
          targetDiskReadBytesPerSec, targetDiskReadOpsPerSec,
          targetRequestCountPerSec, targetConcurrentRequests,
          targetCpuUtilization, targetThroughputUtilization,
          minInstances, maxInstances);
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
      AutomaticScaling other = (AutomaticScaling) obj;
      return Objects.equals(maxPendingLatency, other.maxPendingLatency)
          && Objects.equals(minPendingLatency, other.minPendingLatency)
          && Objects.equals(maxIdleInstances, other.maxIdleInstances)
          && Objects.equals(minIdleInstances, other.minIdleInstances)
          && Objects.equals(targetCpuUtilization, other.targetCpuUtilization)
          && Objects.equals(targetThroughputUtilization, other.targetThroughputUtilization)
          && Objects.equals(minInstances, other.minInstances)
          && Objects.equals(maxInstances, other.maxInstances)
          && Objects.equals(maxConcurrentRequests, other.maxConcurrentRequests)
          && Objects.equals(minNumInstances, other.minNumInstances)
          && Objects.equals(maxNumInstances, other.maxNumInstances)
          && Objects.equals(coolDownPeriodSec, other.coolDownPeriodSec)
          && Objects.equals(cpuUtilization, other.cpuUtilization)
          && Objects.equals(customMetrics, other.customMetrics)
          && Objects.equals(targetNetworkSentBytesPerSec, other.targetNetworkSentBytesPerSec)
          && Objects.equals(targetNetworkSentPacketsPerSec, other.targetNetworkSentPacketsPerSec)
          && Objects.equals(targetNetworkReceivedBytesPerSec,
              other.targetNetworkReceivedBytesPerSec)
          && Objects.equals(targetNetworkReceivedPacketsPerSec,
              other.targetNetworkReceivedPacketsPerSec)
          && Objects.equals(targetDiskWriteBytesPerSec, other.targetDiskWriteBytesPerSec)
          && Objects.equals(targetDiskWriteOpsPerSec, other.targetDiskWriteOpsPerSec)
          && Objects.equals(targetDiskReadBytesPerSec, other.targetDiskReadBytesPerSec)
          && Objects.equals(targetDiskReadOpsPerSec, other.targetDiskReadOpsPerSec)
          && Objects.equals(targetRequestCountPerSec, other.targetRequestCountPerSec)
          && Objects.equals(targetConcurrentRequests, other.targetConcurrentRequests);
    }

    @Override
    public String toString() {
      return "AutomaticScaling [minPendingLatency=" + minPendingLatency
          + ", maxPendingLatency=" + maxPendingLatency
          + ", minIdleInstances=" + minIdleInstances
          + ", maxIdleInstances=" + maxIdleInstances
          + ", minInstances=" + minInstances
          + ", maxInstances=" + maxInstances
          + ", maxConcurrentRequests=" + maxConcurrentRequests
          + ", minNumInstances=" + minNumInstances
          + ", maxNumInstances=" + maxNumInstances
          + ", coolDownPeriodSec=" + coolDownPeriodSec
          + ", cpuUtilization=" + cpuUtilization
          + ", customMetrics=" + customMetrics
          + ", targetNetworkSentBytesPerSec=" + targetNetworkSentBytesPerSec
          + ", targetNetworkSentPacketsPerSec=" + targetNetworkSentPacketsPerSec
          + ", targetNetworkReceivedBytesPerSec=" + targetNetworkReceivedBytesPerSec
          + ", targetNetworkReceivedPacketsPerSec=" + targetNetworkReceivedPacketsPerSec
          + ", targetDiskWriteBytesPerSec=" + targetDiskWriteBytesPerSec
          + ", targetDiskWriteOpsPerSec=" + targetDiskWriteOpsPerSec
          + ", targetDiskReadBytesPerSec=" + targetDiskReadBytesPerSec
          + ", targetDiskReadOpsPerSec=" + targetDiskReadOpsPerSec
          + ", targetRequestCountPerSec=" + targetRequestCountPerSec
          + ", targetConcurrentRequests=" + targetConcurrentRequests
          + ", targetCpuUtilization=" + targetCpuUtilization
          + ", targetThroughputUtilization=" + targetThroughputUtilization
          + "]";
    }
  }

  /**
   * Holder for CPU utilization.
   */
  public static class CpuUtilization {
    private static final CpuUtilization EMPTY_SETTINGS = new CpuUtilization();
    // The target of CPU utilization.
    private Double targetUtilization;
    // The number of seconds used to aggregate CPU usage.
    private Integer aggregationWindowLengthSec;

    public Double getTargetUtilization() {
      return targetUtilization;
    }

    public void setTargetUtilization(Double targetUtilization) {
      this.targetUtilization = targetUtilization;
    }

    public Integer getAggregationWindowLengthSec() {
      return aggregationWindowLengthSec;
    }

    public void setAggregationWindowLengthSec(Integer aggregationWindowLengthSec) {
      this.aggregationWindowLengthSec = aggregationWindowLengthSec;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(targetUtilization, aggregationWindowLengthSec);
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
      CpuUtilization other = (CpuUtilization) obj;
      return Objects.equals(targetUtilization, other.targetUtilization)
          && Objects.equals(aggregationWindowLengthSec, other.aggregationWindowLengthSec);
    }

    @Override
    public String toString() {
      return "CpuUtilization [targetUtilization=" + targetUtilization
          + ", aggregationWindowLengthSec=" + aggregationWindowLengthSec + "]";
    }
  }

  /**
   * Holder for custom autoscaling metrics.
   */
  public static class CustomMetricUtilization {
    /*
     * CustomMetricUtilization with no fields set.
     *
     * Keep this private because CustomMetricUtilization is mutable.
     */
    private static final CustomMetricUtilization EMPTY_SETTINGS = new CustomMetricUtilization();

    private String metricName;
    private String targetType;
    private Double targetUtilization;
    private Double singleInstanceAssignment;
    private String filter;

    public void setMetricName(String metricName) {
      this.metricName = metricName;
    }
    public String getMetricName() {
      return metricName;
    }
    public void setTargetType(String targetType) {
      this.targetType = targetType;
    }
    public String getTargetType() {
      return targetType;
    }
    public void setTargetUtilization(Double targetUtilization) {
      this.targetUtilization = targetUtilization;
    }
    public Double getTargetUtilization() {
      return targetUtilization;
    }
    public void setSingleInstanceAssignment(Double singleInstanceAssignment) {
      this.singleInstanceAssignment = singleInstanceAssignment;
    }
    public Double getSingleInstanceAssignment() {
      return singleInstanceAssignment;
    }
    public void setFilter(String filter) {
      this.filter = filter;
    }
    public String getFilter() {
      return filter;
    }

    @Override
    public int hashCode() {
      return Objects.hash(metricName, targetType, targetUtilization, singleInstanceAssignment,
          filter);
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
      CustomMetricUtilization other = (CustomMetricUtilization) obj;
      return Objects.equals(metricName, other.metricName)
          && Objects.equals(targetType, other.targetType)
          && Objects.equals(targetUtilization, other.targetUtilization)
          && Objects.equals(singleInstanceAssignment, other.singleInstanceAssignment)
          && Objects.equals(filter, other.filter);
    }

    @Override
    public String toString() {
      return "CustomMetricUtilization [metricName=" + metricName
          + ", targetType=" + targetType
          + ", targetUtilization=" + targetUtilization
          + ", singleInstanceAssignment=" + singleInstanceAssignment
          + ", filter=" + filter + "]";
    }
  }

  /**
   * Holder for health check.
   */
  public static class HealthCheck {
    /*
     * HealthCheck with no fields set.
     *
     * Keep this private because HealthCheck is mutable.
     */
    private static final HealthCheck EMPTY_SETTINGS = new HealthCheck();

    // Health check is enabled by default.
    private boolean enableHealthCheck = true;
    private Integer checkIntervalSec;
    private Integer timeoutSec;
    private Integer unhealthyThreshold;
    private Integer healthyThreshold;
    private Integer restartThreshold;
    private String host;

    public boolean getEnableHealthCheck() {
      return enableHealthCheck;
    }
    /**
     * Sets enableHealthCheck.
     */
    public void setEnableHealthCheck(boolean enableHealthCheck) {
      this.enableHealthCheck = enableHealthCheck;
    }

    public Integer getCheckIntervalSec() {
      return checkIntervalSec;
    }
    /**
     * Sets checkIntervalSec.
     */
    public void setCheckIntervalSec(Integer checkIntervalSec) {
      this.checkIntervalSec = checkIntervalSec;
    }

    public Integer getTimeoutSec() {
      return timeoutSec;
    }
    /**
     * Sets timeoutSec.
     */
    public void setTimeoutSec(Integer timeoutSec) {
      this.timeoutSec = timeoutSec;
    }

    public Integer getUnhealthyThreshold() {
      return unhealthyThreshold;
    }

    /**
     * Sets unhealthyThreshold.
     */
    public void setUnhealthyThreshold(Integer unhealthyThreshold) {
      this.unhealthyThreshold = unhealthyThreshold;
    }

    public Integer getHealthyThreshold() {
      return healthyThreshold;
    }

    /**
     * Sets healthyThreshold.
     */

    public void setHealthyThreshold(Integer healthyThreshold) {
      this.healthyThreshold = healthyThreshold;
    }

    public Integer getRestartThreshold() {
      return restartThreshold;
    }

    /**
     * Sets restartThreshold.
     */
    public void setRestartThreshold(Integer restartThreshold) {
      this.restartThreshold = restartThreshold;
    }

    public String getHost() {
      return host;
    }

    /**
     * Sets host. Normalizes empty and null inputs to null.
     */
    public void setHost(String host) {
      this.host = toNullIfEmptyOrWhitespace(host);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(enableHealthCheck, checkIntervalSec, timeoutSec, unhealthyThreshold,
                              healthyThreshold, restartThreshold, host);
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
      HealthCheck other = (HealthCheck) obj;
      return Objects.equals(enableHealthCheck, other.enableHealthCheck)
          && Objects.equals(checkIntervalSec, other.checkIntervalSec)
          && Objects.equals(timeoutSec, other.timeoutSec)
          && Objects.equals(unhealthyThreshold, other.unhealthyThreshold)
          && Objects.equals(healthyThreshold, other.healthyThreshold)
          && Objects.equals(restartThreshold, other.restartThreshold)
          && Objects.equals(host, other.host);
    }

    @Override
    public String toString() {
      return "HealthCheck [enableHealthCheck=" + enableHealthCheck
          + ", checkIntervalSec=" + checkIntervalSec
          + ", timeoutSec=" + timeoutSec
          + ", unhealthyThreshold=" + unhealthyThreshold
          + ", healthyThreshold=" + healthyThreshold
          + ", restartThreshold=" + restartThreshold
          + ", host=" + host + "]";
    }
  }

  /** Holder for liveness check. */
  public static class LivenessCheck {
    /*
     * LivenessCheck with no fields set.
     *
     * Keep this private because LivenessCheck is mutable.
     */
    private static final LivenessCheck EMPTY_SETTINGS = new LivenessCheck();

    private String path;
    private Integer checkIntervalSec;
    private Integer timeoutSec;
    private Integer failureThreshold;
    private Integer successThreshold;
    private Integer initialDelaySec;
    private String host;

    public String getPath() {
      return path;
    }

    /** Sets path. Normalizes empty and null inputs to null. */
    public void setPath(String path) {
      this.path = toNullIfEmptyOrWhitespace(path);
    }

    public Integer getCheckIntervalSec() {
      return checkIntervalSec;
    }

    /** Sets checkIntervalSec. */
    public void setCheckIntervalSec(Integer checkIntervalSec) {
      this.checkIntervalSec = checkIntervalSec;
    }

    public Integer getTimeoutSec() {
      return timeoutSec;
    }
    /** Sets timeoutSec. */
    public void setTimeoutSec(Integer timeoutSec) {
      this.timeoutSec = timeoutSec;
    }

    public Integer getFailureThreshold() {
      return failureThreshold;
    }

    /** Sets failureThreshold. */
    public void setFailureThreshold(Integer failureThreshold) {
      this.failureThreshold = failureThreshold;
    }

    public Integer getSuccessThreshold() {
      return successThreshold;
    }

    /** Sets successThreshold. */
    public void setSuccessThreshold(Integer successThreshold) {
      this.successThreshold = successThreshold;
    }

    public String getHost() {
      return host;
    }

    /** Sets host. Normalizes empty and null inputs to null. */
    public void setHost(String host) {
      this.host = toNullIfEmptyOrWhitespace(host);
    }

    public Integer getInitialDelaySec() {
      return initialDelaySec;
    }

    /** Sets initialDelaySec. */
    public void setInitialDelaySec(Integer initialDelaySec) {
      this.initialDelaySec = initialDelaySec;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          path,
          checkIntervalSec,
          timeoutSec,
          failureThreshold,
          successThreshold,
          initialDelaySec,
          host);
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
      LivenessCheck other = (LivenessCheck) obj;
      return Objects.equals(path, other.path)
          && Objects.equals(checkIntervalSec, other.checkIntervalSec)
          && Objects.equals(timeoutSec, other.timeoutSec)
          && Objects.equals(failureThreshold, other.failureThreshold)
          && Objects.equals(successThreshold, other.successThreshold)
          && Objects.equals(initialDelaySec, other.initialDelaySec)
          && Objects.equals(host, other.host);
    }

    @Override
    public String toString() {
      return "LivenessCheck [path="
          + path
          + ", checkIntervalSec="
          + checkIntervalSec
          + ", timeoutSec="
          + timeoutSec
          + ", failureThreshold="
          + failureThreshold
          + ", successThreshold="
          + successThreshold
          + ", initialDelaySec="
          + initialDelaySec
          + ", host="
          + host
          + "]";
    }
  }

  /** Holder for readiness check. */
  public static class ReadinessCheck {
    /*
     * ReadinessCheck with no fields set.
     *
     * Keep this private because ReadinessCheck is mutable.
     */
    private static final ReadinessCheck EMPTY_SETTINGS = new ReadinessCheck();

    private String path;
    private Integer checkIntervalSec;
    private Integer timeoutSec;
    private Integer failureThreshold;
    private Integer successThreshold;
    private Integer appStartTimeoutSec;
    private String host;

    public String getPath() {
      return path;
    }

    /** Sets path. Normalizes empty and null inputs to null. */
    public void setPath(String path) {
      this.path = toNullIfEmptyOrWhitespace(path);
    }

    public Integer getCheckIntervalSec() {
      return checkIntervalSec;
    }

    /** Sets checkIntervalSec. */
    public void setCheckIntervalSec(Integer checkIntervalSec) {
      this.checkIntervalSec = checkIntervalSec;
    }

    public Integer getTimeoutSec() {
      return timeoutSec;
    }
    /** Sets timeoutSec. */
    public void setTimeoutSec(Integer timeoutSec) {
      this.timeoutSec = timeoutSec;
    }

    public Integer getFailureThreshold() {
      return failureThreshold;
    }

    /** Sets failureThreshold. */
    public void setFailureThreshold(Integer failureThreshold) {
      this.failureThreshold = failureThreshold;
    }

    public Integer getSuccessThreshold() {
      return successThreshold;
    }

    /** Sets successThreshold. */
    public void setSuccessThreshold(Integer successThreshold) {
      this.successThreshold = successThreshold;
    }

    public String getHost() {
      return host;
    }

    /** Sets host. Normalizes empty and null inputs to null. */
    public void setHost(String host) {
      this.host = toNullIfEmptyOrWhitespace(host);
    }

    public Integer getAppStartTimeoutSec() {
      return appStartTimeoutSec;
    }

    /** Sets appStartTimeoutSec. */
    public void setAppStartTimeoutSec(Integer appStartTimeoutSec) {
      this.appStartTimeoutSec = appStartTimeoutSec;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          path,
          checkIntervalSec,
          timeoutSec,
          failureThreshold,
          successThreshold,
          appStartTimeoutSec,
          host);
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
      ReadinessCheck other = (ReadinessCheck) obj;
      return Objects.equals(path, other.path)
          && Objects.equals(checkIntervalSec, other.checkIntervalSec)
          && Objects.equals(timeoutSec, other.timeoutSec)
          && Objects.equals(failureThreshold, other.failureThreshold)
          && Objects.equals(successThreshold, other.successThreshold)
          && Objects.equals(appStartTimeoutSec, other.appStartTimeoutSec)
          && Objects.equals(host, other.host);
    }

    @Override
    public String toString() {
      return "ReadinessCheck [path="
          + path
          + ", checkIntervalSec="
          + checkIntervalSec
          + ", timeoutSec="
          + timeoutSec
          + ", failureThreshold="
          + failureThreshold
          + ", successThreshold="
          + successThreshold
          + ", appStartTimeoutSec="
          + appStartTimeoutSec
          + ", host="
          + host
          + "]";
    }
  }

  /**
   * Holder for Resources
   */
  public static class Resources {
    /*
     * Resources with no fields set.
     *
     * Keep this private because Resources is mutable.
     */
    private static final Resources EMPTY_SETTINGS = new Resources();

    private double cpu;

    public double getCpu() {
      return cpu;
    }

    public void setCpu(double cpu) {
      this.cpu = cpu;
    }

    private double memory_gb;

    public double getMemoryGb() {
      return memory_gb;
    }

    public void setMemoryGb(double memory_gb) {
      this.memory_gb = memory_gb;
    }

    private int disk_size_gb;

    public int getDiskSizeGb() {
      return disk_size_gb;
    }

    public void setDiskSizeGb(int disk_size_gb) {
      this.disk_size_gb = disk_size_gb;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(cpu, memory_gb, disk_size_gb);
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
      Resources other = (Resources) obj;
      return Objects.equals(cpu, other.cpu) &&
          Objects.equals(memory_gb, other.memory_gb) &&
          Objects.equals(disk_size_gb, other.disk_size_gb);
    }

    @Override
    public String toString() {
      return "Resources [" + "cpu=" + cpu +
          ", memory_gb=" + memory_gb +
          ", disk_size_gb=" + disk_size_gb + "]";
    }
  }

  /**
   * Holder for network.
   */
  public static class Network {
    /*
     * Network with no fields set.
     *
     * Keep this private because Network is mutable.
     */
    private static final Network EMPTY_SETTINGS = new Network();

    private String name;
    private String instanceTag;
    private boolean sessionAffinity;
    private String subnetworkName;

    private final List<String> forwardedPorts = Lists.newArrayList();

    public String getInstanceTag() {
      return instanceTag;
    }

    public void setInstanceTag(String instanceTag) {
      this.instanceTag = instanceTag;
    }

    public List<String> getForwardedPorts() {
      return Collections.unmodifiableList(forwardedPorts);
    }

    public void addForwardedPort(String forwardedPort) {
      forwardedPorts.add(forwardedPort);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getSubnetworkName() {
      return subnetworkName;
    }

    public void setSubnetworkName(String subnetworkName) {
      this.subnetworkName = subnetworkName;
    }

    public boolean getSessionAffinity() {
      return sessionAffinity;
    }

    public void setSessionAffinity(boolean sessionAffinity) {
      this.sessionAffinity = sessionAffinity;
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(forwardedPorts, instanceTag, name, subnetworkName, sessionAffinity);
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
      Network other = (Network) obj;
      return Objects.equals(forwardedPorts, other.forwardedPorts)
          && Objects.equals(instanceTag, other.instanceTag)
          && Objects.equals(name, other.name)
          && Objects.equals(subnetworkName, other.subnetworkName)
          && Objects.equals(sessionAffinity, other.sessionAffinity);
    }

    @Override
    public String toString() {
      return "Network [forwardedPorts=" + forwardedPorts + ", instanceTag=" + instanceTag
          + ", name=" + name + ", subnetworkName=" + subnetworkName + ", sessionAffinity="
          + sessionAffinity + "]";
    }
  }

  /**
   * Holder for manual settings.
   */
  public static class ManualScaling {
    /*
     * ManualScaling with no fields set.
     *
     * Keep this private because ManualScaling is mutable.
     */
    private static final ManualScaling EMPTY_SETTINGS = new ManualScaling();

    private String instances;

    public String getInstances() {
      return instances;
    }

    /**
     * Sets instances. Normalizes empty and null inputs to null.
     */
    public void setInstances(String instances) {
      this.instances = toNullIfEmptyOrWhitespace(instances);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(instances);
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
      ManualScaling other = (ManualScaling) obj;
      return Objects.equals(instances, other.instances);
    }

    @Override
    public String toString() {
      return "ManualScaling [" + "instances=" + instances + "]";
    }
  }

  /**
   * Holder for basic settings.
   */
  public static class BasicScaling {
    /*
     * BasicScaling with no fields set.
     *
     * Keep this private because BasicScaling is mutable.
     */
    private static final BasicScaling EMPTY_SETTINGS = new BasicScaling();

    private String maxInstances;
    private String idleTimeout;

    public String getMaxInstances() {
      return maxInstances;
    }

    public String getIdleTimeout() {
      return idleTimeout;
    }

    /**
     * Sets maxInstances. Normalizes empty and null inputs to null.
     */
    public void setMaxInstances(String maxInstances) {
      this.maxInstances = toNullIfEmptyOrWhitespace(maxInstances);
    }

    /**
     * Sets idleTimeout. Normalizes empty and null inputs to null.
     */
    public void setIdleTimeout(String idleTimeout) {
      this.idleTimeout = toNullIfEmptyOrWhitespace(idleTimeout);
    }

    public boolean isEmpty() {
      return this.equals(EMPTY_SETTINGS);
    }

    @Override
    public int hashCode() {
      return Objects.hash(maxInstances, idleTimeout);
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
      BasicScaling other = (BasicScaling) obj;
      return Objects.equals(maxInstances, other.maxInstances)
          && Objects.equals(idleTimeout, other.idleTimeout);
    }

    @Override
    public String toString() {
      return "BasicScaling [" + "maxInstances=" + maxInstances
          + ", idleTimeout=" + idleTimeout + "]";
    }
  }

  public static class ClassLoaderConfig {
    private final List<PrioritySpecifierEntry> entries = Lists.newArrayList();

    public void add(PrioritySpecifierEntry entry) {
      entries.add(entry);
    }

    public List<PrioritySpecifierEntry> getEntries() {
      return entries;
    }

    // Generated by eclipse.
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((entries == null) ? 0 : entries.hashCode());
      return result;
    }

    // Generated by eclipse.
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      ClassLoaderConfig other = (ClassLoaderConfig) obj;
      if (entries == null) {
        if (other.entries != null) return false;
      } else if (!entries.equals(other.entries)) return false;
      return true;
    }

    @Override
    public String toString() {
      return "ClassLoaderConfig{entries=\"" + entries + "\"}";
    }
  }

  public static class PrioritySpecifierEntry {
    private String filename;
    private Double priority;  // null means not present.  Default priority is 1.0.

    private void checkNotAlreadySet() {
      if (filename != null) {
        throw new AppEngineConfigException("Found more that one file name matching tag. "
            + "Only one of 'filename' attribute allowed.");
      }
    }

    public String getFilename() {
      return filename;
    }

    public void setFilename(String filename) {
      checkNotAlreadySet();
      this.filename = filename;
    }

    public Double getPriority() {
      return priority;
    }

    // Returns the priority of this specifier or the assumed default priority
    // value if it is not specified.
    public double getPriorityValue() {
      if (priority == null) {
        return 1.0d;
      }
      return priority;
    }

    public void setPriority(String priority) {
      if (this.priority != null) {
        throw new AppEngineConfigException("The 'priority' tag may only be specified once.");
      }

      if (priority == null) {
        this.priority = null;
        return;
      }

      this.priority = Double.parseDouble(priority);
    }

    // Check that this is a valid ClassLoaderConfig
    public void checkClassLoaderConfig() {
      if (filename == null) {
        throw new AppEngineConfigException("Must have a filename attribute.");
      }
    }

    // Generated by eclipse.
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((filename == null) ? 0 : filename.hashCode());
      result = prime * result + ((priority == null) ? 0 : priority.hashCode());
      return result;
    }

    // Generated by eclipse.
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      PrioritySpecifierEntry other = (PrioritySpecifierEntry) obj;
      if (filename == null) {
        if (other.filename != null) return false;
      } else if (!filename.equals(other.filename)) return false;
      if (priority == null) {
        if (other.priority != null) return false;
      } else if (!priority.equals(other.priority)) return false;
      return true;
    }

    @Override
    public String toString() {
      return "PrioritySpecifierEntry{filename=\"" + filename + "\", priority=\"" + priority + "\"}";
    }
  }
}
