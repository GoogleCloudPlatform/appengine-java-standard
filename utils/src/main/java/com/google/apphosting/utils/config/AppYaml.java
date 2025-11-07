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

import com.esotericsoftware.yamlbeans.YamlException;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.flogger.GoogleLogger;
import com.google.common.xml.XmlEscapers;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * JavaBean representation of the Java app.yaml file.
 *
 * <p>The methods of this class are mapped to YAML keys via method name reflection, which
 * is why some of them contain underscores.
 */
public class AppYaml {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * Plugin service to modify app.yaml with runtime-specific defaults.
   */
  public interface Plugin {
    AppYaml process(AppYaml yaml);
  }

  /**
   * A {@code Handler} element from app.yaml. Maps to {@code servlet}, {@code servlet-mapping},
   * {@code filter}, and {@code filter-mapping} elements in web.xml
   *
   */
  public static class Handler {

    /** Type of handler, deduced from the presence of various YAML elements. */
    public enum Type {SERVLET, JSP, FILTER, NONE}

    private String url;
    private String jsp;
    private String servlet;
    private String filter;
    private LoginType login;
    private Security secure;
    private ImmutableSortedMap<String, String> initParams;
    private String name;
    private boolean loadOnStartup;

    /** Login requirement type specified in app.yaml */
    public enum LoginType { admin, required, optional }
    public enum Security { always, optional, never }
    private boolean apiEndpoint = false;

    private boolean requireMatchingFile = false;
    private int redirectHttpResponseCode;
    private String mimeType;
    private String upload;
    private String staticFiles;
    private String staticDir;
    private String expiration;
    private Pattern urlPattern;
    private ImmutableSortedMap<String, String> httpHeaders;

    ////////////////////////////////////////////////
    // Plugin-Only Properties
    ////////////////////////////////////////////////

    // The following properties do not map directly to xml.
    // Instead they are consumed by runtime plugins.

    // A "script:" value in a handler is typical of Python's app.yaml
    // but it is not used by the Java SDK because "servlet:" is used instead.
    // This tag may be used by a runtime plugin to allow users to express
    // runtime-specific URL handling instructions.
    private String script;

    ////////////////////////////////////////////////
    // end: Plugin-Only Properties
    ////////////////////////////////////////////////

    private static final String MULTIPLE_HANDLERS = "Cannot set both %s and %s for the same url.";

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      YamlUtils.validateUrl(url);
      try {
        this.urlPattern = Pattern.compile(url);
      } catch (PatternSyntaxException e) {
        logger.atWarning().log(
            "Url is not a valid regex pattern: %s, exception: %s", url, e.getMessage());
      }
      this.url = url;
    }

    public Pattern getRegularExpression() {
      return urlPattern;
    }

    public String getJsp() {
      return jsp;
    }

    public void setJsp(String jsp) {
      this.jsp = jsp;
      checkHandlers();
    }

    public String getServlet() {
      return servlet;
    }

    public void setServlet(String servlet) {
      this.servlet = servlet;
      checkHandlers();
    }

    public String getFilter() {
      return filter;
    }

    public void setFilter(String filter) {
      this.filter = filter;
      checkHandlers();
    }

    public Type getType() {
      if (servlet != null) {
        return Type.SERVLET;
      }
      if (filter != null) {
        return Type.FILTER;
      }
      if (jsp != null) {
        return Type.JSP;
      }
      return Type.NONE;
    }

    public String getTarget() {
      if (servlet != null) {
        return servlet;
      }
      if (filter != null) {
        return filter;
      }
      if (jsp != null) {
        return jsp;
      }
      return null;
    }

    public void setScript(String script) {
      this.script = script;
    }

    public String getScript() {
      return this.script;
    }

    public LoginType getLogin() {
      return login;
    }

    public void setLogin(LoginType login) {
      this.login = login;
    }

    public Security getSecure() {
      return secure;
    }

    public void setSecure(Security secure) {
      if (secure == Security.never) {
        throw new AppEngineConfigException("Java does not support secure: never");
      }
      this.secure = secure;
    }

    public Map<String, String> getInit_params() {
      return initParams;
    }

    public void setInit_params(Map<String, String> initParams) {
      if (initParams == null) {
        this.initParams = null;
      } else {
        this.initParams = ImmutableSortedMap.copyOf(initParams);
      }
    }

    public String getName() {
      return (name == null ? getTarget() : name);
    }

    public void setLoad_on_startup(boolean loadOnStartup) {
      this.loadOnStartup = loadOnStartup;
    }

    public boolean getLoad_on_startup() {
      return this.loadOnStartup;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getApi_endpoint() {
      return Boolean.toString(this.apiEndpoint);
    }

    public void setApi_endpoint(String apiEndpoint) {
      this.apiEndpoint = YamlUtils.parseBoolean(apiEndpoint);
    }

    public boolean isApiEndpoint() {
      return this.apiEndpoint;
    }

    public boolean isRequire_matching_file() {
      return requireMatchingFile;
    }

    public void setRequire_matching_file(boolean requireMatchingFile) {
      this.requireMatchingFile = requireMatchingFile;
    }

    public int getRedirect_http_response_code() {
      return redirectHttpResponseCode;
    }

    public void setRedirect_http_response_code(int redirectHttpResponseCode) {
      this.redirectHttpResponseCode = redirectHttpResponseCode;
    }

    public String getMime_type() {
      return mimeType;
    }

    public void setMime_type(String mimeType) {
      this.mimeType = mimeType;
    }

    public String getUpload() {
      return upload;
    }

    public void setUpload(String upload) {
      this.upload = upload;
    }

    public String getStatic_files() {
      return staticFiles;
    }

    public void setStatic_files(String staticFiles) {
      this.staticFiles = staticFiles;
    }

    public String getStatic_dir() {
      return staticDir;
    }

    public void setStatic_dir(String staticDir) {
      this.staticDir = staticDir;
    }

    public String getExpiration() {
      return expiration;
    }

    public void setExpiration(String expiration) {
      this.expiration = expiration;
    }

    public Map<String, String> getHttp_headers() {
      return httpHeaders;
    }

    public void setHttp_headers(Map<String, String> httpHeaders) {
      this.httpHeaders = ImmutableSortedMap.copyOf(httpHeaders);
    }

    private void checkHandlers() {
      if (jsp != null && servlet != null) {
        throw new AppEngineConfigException(String.format(MULTIPLE_HANDLERS, "jsp", "servlet"));
      }
      if (jsp != null && filter != null) {
        throw new AppEngineConfigException(String.format(MULTIPLE_HANDLERS, "jsp", "filter"));
      }
      if (filter != null && servlet != null) {
        throw new AppEngineConfigException(String.format(MULTIPLE_HANDLERS, "filter", "servlet"));
      }
    }

    /**
     * Generates the {@code servlet} or {@code filter} element of web.xml
     * corresponding to this handler.
     */
    private void generateDefinitionXml(XmlWriter xml) {
      if (getServlet() != null || getJsp() != null) {
        generateServletDefinition(xml);
      } else if (getFilter() != null) {
        generateFilterDefinition(xml);
      }
    }

    private void generateMappingXml(XmlWriter xml) {
      if (getServlet() != null || getJsp() != null) {
        generateServletMapping(xml);
      } else if (getFilter() != null) {
        generateFilterMapping(xml);
      }
      generateSecurityConstraints(xml);
    }

    private void generateSecurityConstraints(XmlWriter xml) {
      if (secure == Security.always || login == LoginType.required || login == LoginType.admin) {
        xml.startElement("security-constraint");
        xml.startElement("web-resource-collection");
        xml.simpleElement("web-resource-name", "aname");
        xml.simpleElement("url-pattern", getUrl());
        xml.endElement("web-resource-collection");
        if (login == LoginType.required) {
          securityConstraint(xml, "auth", "role-name", "*");
        } else if (login == LoginType.admin) {
          securityConstraint(xml, "auth", "role-name", "admin");
        }
        if (secure == Security.always) {
          securityConstraint(xml, "user-data", "transport-guarantee", "CONFIDENTIAL");
        }
        xml.endElement("security-constraint");
      }
    }

    private void securityConstraint(XmlWriter xml, String type, String name, String value)  {
      type = type + "-constraint";
      xml.startElement(type);
      xml.simpleElement(name, value);
      xml.endElement(type);
    }

    /**
     * Generates a {@code filter} element of web.xml corresponding to this handler.
     */
    private void generateFilterDefinition(XmlWriter xml) {
      xml.startElement("filter");
      xml.simpleElement("filter-name", getName());
      xml.simpleElement("filter-class", getFilter());
      generateInitParams(xml);
      xml.endElement("filter");
    }

    /**
     *  Generates a {@code filter-mapping} element of web.xml corresponding to this handler.
     */
    private void generateFilterMapping(XmlWriter xml) {
      xml.startElement("filter-mapping");
      xml.simpleElement("filter-name", getName());
      xml.simpleElement("url-pattern", getUrl());
      xml.endElement("filter-mapping");
    }

    /**
     * Generates a {@code servlet} or {@code jsp-file} element of web.xml corresponding to this
     * handler.
     */
    private void generateServletDefinition(XmlWriter xml) {
      xml.startElement("servlet");
      xml.simpleElement("servlet-name", getName());
      if (getJsp() == null) {
        xml.simpleElement("servlet-class", getServlet());
      } else {
        xml.simpleElement("jsp-file", getJsp());
      }
      generateInitParams(xml);
      // The servlet spec says that the value of load-on-startup should be
      // a non-negative integer and at container startup time servlets with
      // this tag are loaded in increasing order of this value. Here we
      // only support it like a boolean. It's either not present or it's 1.
      if (loadOnStartup) {
        xml.simpleElement("load-on-startup", "1");
      }
      xml.endElement("servlet");
    }

    /**
     * Merges another handler into this handler, assuming that the other handler
     * has the same name, type and target. This operation is intended to be
     * used for generating a Servlet or Filter *definition* as opposed to a
     * mapping, and therefore the urls of this handler and the other handler
     * are not involved in the merge operation. The load_on_startup values
     * of the two handlers will be OR'd and the init_params will be unioned.
     */
    public void mergeDefinitions(Handler otherHandler) {
      Preconditions.checkArgument(
          this.getName().equals(otherHandler.getName()),
          "Cannot merge handler named %s with handler named %s",
          this.getName(),
          otherHandler.getName());
      Preconditions.checkArgument(
          this.getType() == otherHandler.getType(),
          "Cannot merge handler of type %s with handler of type %s",
          this.getType(),
          otherHandler.getType());
      Preconditions.checkArgument(
          this.getTarget().equals(otherHandler.getTarget()),
          "Cannot merge handler with target %s with handler with target %s",
          this.getTarget(),
          otherHandler.getTarget());
      this.loadOnStartup = this.loadOnStartup || otherHandler.loadOnStartup;
      Map<String, String> mergedInitParams = new LinkedHashMap<>();
      if (this.initParams != null) {
        mergedInitParams.putAll(this.initParams);
      }
      if (otherHandler.initParams != null) {
        for (String key : otherHandler.initParams.keySet()) {
          String thisValue = mergedInitParams.get(key);
          String otherValue = otherHandler.initParams.get(key);
          if (thisValue == null) {
            mergedInitParams.put(key, otherValue);
          } else if (!thisValue.equals(otherValue)) {
            throw new IllegalArgumentException(
                "Cannot merge handlers with conflicting values for the init_param: " + key + " : "
                + thisValue + " vs " + otherValue);
          }
        }
      }
      if (!mergedInitParams.isEmpty()) {
        this.initParams = ImmutableSortedMap.copyOf(mergedInitParams);
      }
    }

    /**
     * Generates a {@code servlet-mapping} element of web.xml corresponding to this handler.
     */
    private void generateServletMapping(XmlWriter xml) {
      if (isApiEndpoint()) {
        xml.startElement("servlet-mapping", "id", xml.nextApiEndpointId());
      } else {
        xml.startElement("servlet-mapping");
      }
      xml.simpleElement("servlet-name", getName());
      xml.simpleElement("url-pattern", getUrl());
      xml.endElement("servlet-mapping");
    }

    private void generateInitParams(XmlWriter xml) {
      if (initParams != null) {
        initParams.forEach(
            (name, value) -> {
              xml.startElement("init-param");
              xml.simpleElement("param-name", name);
              xml.simpleElement("param-value", value);
              xml.endElement("init-param");
            }
        );
      }
    }

    private void generateEndpointServletMappingId(XmlWriter xml) {
      if (isApiEndpoint()) {
        xml.simpleElement("endpoint-servlet-mapping-id", xml.nextApiEndpointId());
      }
    }
  }

  public static class ResourceFile {
    private static final String EMPTY_MESSAGE = "Missing include or exclude.";
    private static final String BOTH_MESSAGE = "Cannot specify both include and exclude.";

    String include;
    String exclude;
    ImmutableSortedMap<String, String> httpHeaders;

    public String getInclude() {
      if (exclude == null && include == null) {
        throw new AppEngineConfigException(EMPTY_MESSAGE);
      }
      return include;
    }

    public void setInclude(String include) {
      if (exclude != null) {
        throw new AppEngineConfigException(BOTH_MESSAGE);
      }
      this.include = include;
    }

    public String getExclude() {
      if (exclude == null && include == null) {
        throw new AppEngineConfigException(EMPTY_MESSAGE);
      }
      return exclude;
    }

    public void setExclude(String exclude) {
      if (include != null) {
        throw new AppEngineConfigException(BOTH_MESSAGE);
      }
      this.exclude = exclude;
    }

    // The reason for these wacky getter and setter method names is so that
    // yamlbeans will find these method.
    public Map<String, String> getHttp_headers() {
      if (include == null) {
        throw new AppEngineConfigException("Missing include.");
      }

      return httpHeaders;
    }

    public void setHttp_headers(Map<String, String> httpHeaders) {
      if (include == null) {
        throw new AppEngineConfigException("Missing include.");
      }

      this.httpHeaders = ImmutableSortedMap.copyOf(httpHeaders);
    }
  }

  public static class StaticFile extends ResourceFile {
    private static final String NO_INCLUDE = "Missing include.";
    private static final String INCLUDE_ONLY = "Expiration can only be specified with include.";
    private String expiration;

    public String getExpiration() {
      if (expiration != null && include == null) {
        throw new AppEngineConfigException(NO_INCLUDE);
      }
      return expiration;
    }

    public void setExpiration(String expiration) {
      if (exclude != null) {
        throw new AppEngineConfigException(INCLUDE_ONLY);
      }
      this.expiration = expiration;
    }

    @Override
    public void setExclude(String exclude) {
      if (expiration != null) {
        throw new AppEngineConfigException(INCLUDE_ONLY);
      }
      super.setExclude(exclude);
    }
  }

  public static class AdminConsole {
    private ImmutableList<AdminPage> pages;

    public List<AdminPage> getPages() {
      return pages;
    }

    public void setPages(List<AdminPage> pages) {
      this.pages = ImmutableList.copyOf(pages);
    }
  }

  public static class AdminPage {
    private String name;
    private String url;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }

  public static class AsyncSessionPersistence {
    private boolean enabled = false;
    private String queueName;

    public String getEnabled() {
      return Boolean.toString(enabled);
    }

    public void setEnabled(String enabled) {
      this.enabled = YamlUtils.parseBoolean(enabled);
    }

    public String getQueue_name() {
      return this.queueName;
    }

    public void setQueue_name(String queueName) {
      this.queueName = queueName;
    }
  }

  public static class ErrorHandler {
    private String file;
    private String errorCode;

    public String getFile() {
      return file;
    }

    public void setFile(String file) {
      this.file = file;
    }

    public String getError_code() {
      return errorCode;
    }

    public void setError_code(String errorCode) {
      this.errorCode = errorCode;
    }
  }

  /**
   * AutomaticScaling bean.
   */
  public static class AutomaticScaling {
    private String minPendingLatency;
    private String maxPendingLatency;
    private String minIdleInstances;
    private String maxIdleInstances;
    private String maxConcurrentRequests;

    // New Standard clone scheduler config:
    private String minInstances;
    private String maxInstances;
    private String targetCpuUtilization;
    private String targetThroughputUtilization;

    public String getMin_pending_latency() {
      return minPendingLatency;
    }

    public void setMin_pending_latency(String minPendingLatency) {
      this.minPendingLatency = minPendingLatency;
    }

    public String getMax_pending_latency() {
      return maxPendingLatency;
    }

    public void setMax_pending_latency(String maxPendingLatency) {
      this.maxPendingLatency = maxPendingLatency;
    }

    public String getMin_idle_instances() {
      return minIdleInstances;
    }

    public void setMin_idle_instances(String minIdleInstances) {
      this.minIdleInstances = minIdleInstances;
    }

    public String getMax_idle_instances() {
      return maxIdleInstances;
    }

    public void setMax_idle_instances(String maxIdleInstances) {
      this.maxIdleInstances = maxIdleInstances;
    }

    public String getMin_instances() {
      return minInstances;
    }

    public void setTarget_cpu_utilization(String targetCpuUtilization) {
      this.targetCpuUtilization = targetCpuUtilization;
    }

    public String getTarget_cpu_utilization() {
      return targetCpuUtilization;
    }

    public void setTarget_throughput_utilization(String targetThroughputUtilization) {
      this.targetThroughputUtilization = targetThroughputUtilization;
    }

    public String getTarget_throughput_utilization() {
      return targetThroughputUtilization;
    }

    public void setMin_instances(String minInstances) {
      this.minInstances = minInstances;
    }

    public String getMax_instances() {
      return maxInstances;
    }

    public void setMax_instances(String maxInstances) {
      this.maxInstances = maxInstances;
    }

    public String getMax_concurrent_requests() {
      return maxConcurrentRequests;
    }

    public void setMax_concurrent_requests(String maxConcurrentRequests) {
      this.maxConcurrentRequests = maxConcurrentRequests;
    }
  }

  /**
   * ManualScaling bean.
   */
  public static class ManualScaling {
    private String instances;

    public String getInstances() {
      return instances;
    }

    public void setInstances(String instances) {
      this.instances = instances;
    }
  }

  /**
   * BasicScaling bean.
   */
  public static class BasicScaling {
    private String maxInstances;
    private String idleTimeout;

    public String getMax_instances() {
      return maxInstances;
    }

    public void setMax_instances(String maxInstances) {
      this.maxInstances = maxInstances;
    }
    public String getIdle_timeout() {
      return idleTimeout;
    }

    public void setIdle_timeout(String idleTimeout) {
      this.idleTimeout = idleTimeout;
    }
  }

  private String application;

  private String version;
  private String service;
  private String instanceClass;
  private AutomaticScaling automaticScaling;
  private ManualScaling manualScaling;
  private BasicScaling basicScaling;
  private String runtime;
  private boolean appEngineApis;
  private ImmutableList<Handler> handlers;
  private String publicRoot;
  private ImmutableList<StaticFile> staticFiles;
  private ImmutableList<ResourceFile> resourceFiles;
  private boolean sslEnabled = true;
  private boolean precompilationEnabled = true;
  private boolean sessionsEnabled = false;
  private AsyncSessionPersistence asyncSessionPersistence;
  private boolean threadsafe = false;
  private String autoIdPolicy;
  private String apiVersion;
  private boolean threadsafeWasSet = false;
  private boolean codeLock = false;
  private ImmutableSortedMap<String, String> systemProperties;
  private ImmutableSortedMap<String, String> envVariables;
  private ImmutableSortedMap<String, String> buildEnvVariables;
  private ImmutableSortedMap<String, String> contextParams;
  private ImmutableSortedMap<String, String> betaSettings;
  private ImmutableList<String> welcomeFiles;
  private ImmutableList<String> listeners;
  private ImmutableList<String> inboundServices;
  private ImmutableSet<String> appEngineBundledServices;
  private ImmutableList<String> derivedFileType;
  private AdminConsole adminConsole;
  private ImmutableList<ErrorHandler> errorHandlers;
  private ApiConfig apiConfig;
  private String webXml;
  private String runtimeChannel;
  private String env;
  private ImmutableSortedMap<String, String> vpcAccessConnector;
  private String skipFiles;
  private String defaultExpiration;
  private String entrypoint;

  private static final String VALIDATION_VPC =
      "vpc_access_connect has to be like: "
          + "projects/[PROJECT_ID]/locations/[REGION]/connectors/[CONNECTOR_NAME]"
          + " but was '%s'.";

  public String getApplication() {
    if (application == null) {
      throw new AppEngineConfigException(
          String.format("Missing required element '%s'.", "application"));
    }
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public Map<String, String> getVpc_access_connector() {
    return vpcAccessConnector;
  }

  public void setVpc_access_connector(Map<String, String> vpcAccessConnectorMap) {
    String vpcAccessConnector = vpcAccessConnectorMap.get("name");
    List<String> parts = Splitter.on('/').splitToList(vpcAccessConnector);
    if (parts.size() != 6) {
      throw new AppEngineConfigException(String.format(VALIDATION_VPC, vpcAccessConnector));
    }
    if (!parts.get(0).equals("projects")
        || !parts.get(2).equals("locations")
        || !parts.get(4).equals("connectors")) {
      throw new AppEngineConfigException(String.format(VALIDATION_VPC, vpcAccessConnector));
    }
    this.vpcAccessConnector = ImmutableSortedMap.copyOf(vpcAccessConnectorMap);
  }

  public String getDefault_expiration() {
    return defaultExpiration;
  }

  public void setDefault_expiration(String defaultExpiration) {
    this.defaultExpiration = defaultExpiration;
  }

  public String getRuntime_channel() {
    return runtimeChannel;
  }

  public void setRuntime_channel(String runtimeChannel) {
    this.runtimeChannel = runtimeChannel;
  }

  public String getEnv() {
    return env;
  }

  public void setEnv(String env) {
    this.env = env;
  }

  public String getSkip_files() {
    return skipFiles;
  }

  public void setSkip_files(String skipFiles) {
    this.skipFiles = skipFiles;
  }

  public String getEntrypoint() {
    return entrypoint;
  }

  public void setEntrypoint(String application) {
    this.entrypoint = application;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getModule() {
    return service;
  }

  public void setModule(String module) {
    this.service = module;
  }

  public String getService() {
    return service;
  }

  public void setService(String service) {
    this.service = service;
  }

  public String getInstance_class() {
    return instanceClass;
  }

  public void setInstance_class(String instanceClass) {
    this.instanceClass = instanceClass;
  }

  public AutomaticScaling getAutomatic_scaling() {
    return automaticScaling;
  }

  public void setAutomatic_scaling(AutomaticScaling automaticScaling) {
    this.automaticScaling = automaticScaling;
  }

  public ManualScaling getManual_scaling() {
    return manualScaling;
  }

  public void setManual_scaling(ManualScaling manualScaling) {
    this.manualScaling = manualScaling;
  }

  public BasicScaling getBasic_scaling() {
    return basicScaling;
  }

  public void setBasic_scaling(BasicScaling basicScaling) {
    this.basicScaling = basicScaling;
  }

  public String getRuntime() {
    return runtime;
  }

  public void setRuntime(String runtime) {
    this.runtime = runtime;
  }

  public String getApp_engine_apis() {
    return Boolean.toString(appEngineApis);
  }

  public void setApp_engine_apis(String appEngineApis) {
    this.appEngineApis = YamlUtils.parseBoolean(appEngineApis);
  }

  public boolean isAppEngineApis() {
    return appEngineApis;
  }

  public List<Handler> getHandlers() {
    return handlers;
  }

  public void setHandlers(List<Handler> handlers) {
    this.handlers = ImmutableList.copyOf(handlers);
    if (this.apiConfig != null) {
      this.apiConfig.setHandlers(this.handlers);
    }
  }

  public String getPublic_root() {
    return publicRoot;
  }

  public void setPublic_root(String publicRoot) {
    this.publicRoot = publicRoot;
  }

  public List<StaticFile> getStatic_files() {
    return staticFiles;
  }

  public void setStatic_files(List<StaticFile> staticFiles) {
    this.staticFiles = ImmutableList.copyOf(staticFiles);
  }

  public List<ResourceFile> getResource_files() {
    return resourceFiles;
  }

  public void setResource_files(List<ResourceFile> resourceFiles) {
    this.resourceFiles = ImmutableList.copyOf(resourceFiles);
  }

  public String getSsl_enabled() {
    return Boolean.toString(sslEnabled);
  }

  public void setSsl_enabled(String sslEnabled) {
    this.sslEnabled = YamlUtils.parseBoolean(sslEnabled);
  }

  public boolean isSslEnabled() {
    return sslEnabled;
  }

  public String getPrecompilation_enabled() {
    return Boolean.toString(precompilationEnabled);
  }

  public boolean isPrecompilationEnabled() {
    return precompilationEnabled;
  }

  public void setPrecompilation_enabled(String precompilationEnabled) {
    this.precompilationEnabled = YamlUtils.parseBoolean(precompilationEnabled);
  }

  public String getSessions_enabled() {
    return Boolean.toString(sessionsEnabled);
  }

  public boolean isSessionsEnabled() {
    return sessionsEnabled;
  }

  public void setSessions_enabled(String sessionsEnabled) {
    this.sessionsEnabled = YamlUtils.parseBoolean(sessionsEnabled);
  }

  public AsyncSessionPersistence getAsync_session_persistence() {
    return asyncSessionPersistence;
  }

  public void setAsync_session_persistence(AsyncSessionPersistence asyncSessionPersistence) {
    this.asyncSessionPersistence = asyncSessionPersistence;
  }

  public String getThreadsafe() {
    return Boolean.toString(threadsafe);
  }

  public boolean isThreadsafeSet() {
    return threadsafeWasSet;
  }

  public void setThreadsafe(String threadsafe) {
    this.threadsafe = YamlUtils.parseBoolean(threadsafe);
    threadsafeWasSet = true;
  }

  public String getAuto_id_policy() {
    return autoIdPolicy;
  }

  public void setAuto_id_policy(String policy) {
    autoIdPolicy = policy;
  }

  public String getApi_version() {
    return apiVersion;
  }

  public void setApi_version(String version) {
    apiVersion = version;
  }

  public String getCode_lock() {
    return Boolean.toString(codeLock);
  }

  public void setCode_lock(String codeLock) {
    this.codeLock = YamlUtils.parseBoolean(codeLock);
  }

  public Map<String, String> getSystem_properties() {
    return systemProperties;
  }

  public void setSystem_properties(Map<String, String> systemProperties) {
    this.systemProperties = ImmutableSortedMap.copyOf(systemProperties);
  }

  public Map<String, String> getEnv_variables() {
    return envVariables;
  }

  public void setEnv_variables(Map<String, String> envVariables) {
    this.envVariables = ImmutableSortedMap.copyOf(envVariables);
  }

  public Map<String, String> getBuild_env_variables() {
    return buildEnvVariables;
  }

  public void setBuild_env_variables(Map<String, String> buildEnvVariables) {
    this.buildEnvVariables = ImmutableSortedMap.copyOf(buildEnvVariables);
  }

  public Map<String, String> getBeta_settings() {
    return betaSettings;
  }

  public void setBeta_settings(Map<String, String> betaSettings) {
    this.betaSettings = ImmutableSortedMap.copyOf(betaSettings);
  }

  public List<String> getWelcome_files() {
    return welcomeFiles;
  }

  public void setWelcome_files(List<String> welcomeFiles) {
    this.welcomeFiles = ImmutableList.copyOf(welcomeFiles);
  }

  public Map<String, String> getContext_params() {
    return contextParams;
  }

  public void setContext_params(Map<String, String> contextParams) {
    this.contextParams = ImmutableSortedMap.copyOf(contextParams);
  }

  public List<String> getListeners() {
    return listeners;
  }

  public void setListeners(List<String> listeners) {
    this.listeners = ImmutableList.copyOf(listeners);
  }

  public String getWeb_xml() {
    return webXml;
  }

  public void setWeb_xml(String webXml) {
    this.webXml = webXml;
  }

  public List<String> getInbound_services() {
    return inboundServices;
  }

  public void setInbound_services(List<String> inboundServices) {
    this.inboundServices = ImmutableList.copyOf(inboundServices);
  }

  public Set<String> getApp_engine_bundled_services() {
    return appEngineBundledServices;
  }

  public void setApp_engine_bundled_services(List<String> appEngineBundledServices) {
    this.appEngineBundledServices = ImmutableSet.copyOf(appEngineBundledServices);
  }

  public List<String> getDerived_file_type() {
    return derivedFileType;
  }

  public void setDerived_file_type(List<String> derivedFileType) {
    this.derivedFileType = ImmutableList.copyOf(derivedFileType);
  }

  public AdminConsole getAdmin_console() {
    return adminConsole;
  }

  public void setAdmin_console(AdminConsole adminConsole) {
    this.adminConsole = adminConsole;
  }

  public List<ErrorHandler> getError_handlers() {
    return errorHandlers;
  }

  public void setError_handlers(List<ErrorHandler> errorHandlers) {
    this.errorHandlers = ImmutableList.copyOf(errorHandlers);
  }

  public ApiConfig getApi_config() {
    return apiConfig;
  }

  public void setApi_config(ApiConfig apiConfig) {
    this.apiConfig = apiConfig;
    if (handlers != null) {
      this.apiConfig.setHandlers(handlers);
    }
  }

  public AppYaml applyPlugins() {
    AppYaml yaml = this;
    for (Plugin plugin : PluginLoader.loadPlugins(Plugin.class)) {
      AppYaml modified = plugin.process(yaml);
      if (modified != null) {
        yaml = modified;
      }
    }
    return yaml;
  }

  /**
   * Represents an api-config: top level app.yaml stanza
   * This is a singleton specifying url: and servlet: for the api config server.
   */
  public static class ApiConfig {
    private String url;
    private String servlet;
    private ImmutableList<Handler> handlers;

    public void setHandlers(List<Handler> handlers) {
      this.handlers = ImmutableList.copyOf(handlers);
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      YamlUtils.validateUrl(url);
      this.url = url;
    }

    public String getServlet() {
      return servlet;
    }

    public void setServlet(String servlet) {
      this.servlet = servlet;
    }

    private void generateXml(XmlWriter xml) {
      xml.startElement("api-config", "servlet-class", getServlet(), "url-pattern", getUrl());
      if (handlers != null) {
        for (Handler handler : handlers) {
          handler.generateEndpointServletMappingId(xml);
        }
      }
      xml.endElement("api-config");
    }
  }

  private static class XmlWriter {
    private static final String XML_HEADER = "<!-- Generated from app.yaml. Do not edit. -->";
    private final PrintWriter writer;
    private int indent = 0;
    // This must generate unique ids within each web.xml that is written.
    private int apiEndpointId = 0;

    public XmlWriter(Writer w) {
      writer = new PrintWriter(w);
      writer.println(XML_HEADER);
    }

    public void startElement(String name, String... attributes) {
      startElement(name, false, attributes);
      writer.println();
    }

    public void startElement(String name, boolean empty, String... attributes) {
      indent();
      writer.print("<");
      writer.print(name);
      for (int i = 0; i < attributes.length; i += 2) {
        String attributeName = attributes[i];
        String value = attributes[i + 1];
        if (value != null) {
          writer.printf(" %s='%s'", attributeName, escapeAttribute(value));
        }
      }
      if (empty) {
        writer.println("/>");
      } else {
        writer.print(">");
        indent += 2;
      }
    }

    public void endElement(String name) {
      endElement(name, true);
    }

    public void endElement(String name, boolean needIndent) {
      indent -= 2;
      if (needIndent) {
        indent();
      }
      writer.print("</");
      writer.print(name);
      writer.println(">");
    }

    public void emptyElement(String name, String... attributes) {
      startElement(name, true, attributes);
    }

    public void simpleElement(String name, String value, String... attributes) {
      startElement(name, false, attributes);
      writer.print(escapeContent(value));
      endElement(name, false);
    }

    public void writeUnescaped(String xmlContent) {
      writer.println(xmlContent);
    }

    private void indent() {
      for (int i = 0; i < indent; i++) {
        writer.print(" ");
      }
    }

    private String escapeContent(String value) {
      if (value == null) {
        return null;
      }
      return XmlEscapers.xmlContentEscaper().escape(value);
    }

    private String escapeAttribute(String value) {
      if (value == null) {
        return null;
      }
      return XmlEscapers.xmlAttributeEscaper().escape(value);
    }

    private String nextApiEndpointId() {
      return String.format("endpoint-%1$d", ++apiEndpointId);
    }
  }

  private void addOptionalElement(XmlWriter xml, String name, String value) {
    if (value != null) {
      xml.simpleElement(name, value);
    }
  }

  public void generateAppEngineWebXml(Writer writer) {
    XmlWriter xml = new XmlWriter(writer);
    xml.startElement("appengine-web-app", "xmlns", "http://appengine.google.com/ns/1.0");
    xml.simpleElement("application", getApplication());
    addOptionalElement(xml, "version", getVersion());
    addOptionalElement(xml, "runtime", getRuntime());
    addOptionalElement(xml, "module", getModule());
    addOptionalElement(xml, "instance-class", getInstance_class());
    addOptionalElement(xml, "public-root", publicRoot);
    addOptionalElement(xml, "auto-id-policy", getAuto_id_policy());
    if (automaticScaling != null) {
      xml.startElement("automatic-scaling");
      addOptionalElement(xml, "min-pending-latency", automaticScaling.getMin_pending_latency());
      addOptionalElement(xml, "max-pending-latency", automaticScaling.getMax_pending_latency());
      addOptionalElement(xml, "min-idle-instances", automaticScaling.getMin_idle_instances());
      addOptionalElement(xml, "max-idle-instances", automaticScaling.getMax_idle_instances());
      addOptionalElement(xml, "min-instances", automaticScaling.getMin_instances());
      addOptionalElement(xml, "max-instances", automaticScaling.getMax_instances());
      addOptionalElement(
          xml, "target-cpu-utilization", automaticScaling.getTarget_cpu_utilization());
      addOptionalElement(
          xml,
          "target-throughput-utilization",
          automaticScaling.getTarget_throughput_utilization());
      addOptionalElement(xml, "max-concurrent-requests",
          automaticScaling.getMax_concurrent_requests());
      xml.endElement("automatic-scaling");
    }
    if (manualScaling != null) {
      xml.startElement("manual-scaling");
      xml.simpleElement("instances", manualScaling.getInstances());
      xml.endElement("manual-scaling");
    }
    if (basicScaling != null) {
      xml.startElement("basic-scaling");
      xml.simpleElement("max-instances", basicScaling.getMax_instances());
      addOptionalElement(xml, "idle-timeout", basicScaling.getIdle_timeout());
      xml.endElement("basic-scaling");
    }
    xml.startElement("static-files");
    if (staticFiles != null) {
      for (StaticFile file : staticFiles) {
        if (file.getInclude() != null) {
          generateInclude(file, xml);
        } else {
          xml.emptyElement("exclude", /* attributes: */ "path", file.getExclude());
        }
      }
    }
    xml.endElement("static-files");
    xml.startElement("resource-files");
    if (resourceFiles != null) {
      for (ResourceFile file : resourceFiles) {
        String name;
        String path;
        if (file.getInclude() != null) {
          name = "include";
          path = file.getInclude();
        } else {
          name = "exclude";
          path = file.getExclude();
        }
        xml.emptyElement(name, "path", path);
      }
    }
    xml.endElement("resource-files");
    xml.simpleElement("ssl-enabled", getSsl_enabled());
    xml.simpleElement("precompilation-enabled", getPrecompilation_enabled());
    if (isThreadsafeSet()) {
      xml.simpleElement("threadsafe", getThreadsafe());
    }
    xml.simpleElement("code-lock", getCode_lock());
    xml.simpleElement("sessions-enabled", getSessions_enabled());
    if (asyncSessionPersistence != null) {
      xml.simpleElement("async-session-persistence", null,
          "enabled", getAsync_session_persistence().getEnabled(),
          "queue-name", getAsync_session_persistence().getQueue_name());
    }
    if (systemProperties != null) {
      xml.startElement("system-properties");
      systemProperties.forEach(
          (name, value) -> xml.emptyElement("property", "name", name, "value", value));
      xml.endElement("system-properties");
    }
    if (envVariables != null) {
      xml.startElement("env-variables");
      envVariables.forEach(
          (name, value) -> xml.emptyElement("env-var", "name", name, "value", value));
      xml.endElement("env-variables");
    }
    if (buildEnvVariables != null) {
      xml.startElement("build-env-variables");
      buildEnvVariables.forEach(
          (name, value) -> xml.emptyElement("build-env-var", "name", name, "value", value));
      xml.endElement("build-env-variables");
    }
    if (betaSettings != null) {
      xml.startElement("beta-settings");
      betaSettings.forEach(
          (name, value) ->
              xml.emptyElement("beta-setting", "name", name, "value", value));
      xml.endElement("beta-settings");
    }
    boolean warmupService = false;
    if (inboundServices != null) {
      xml.startElement("inbound-services");
      for (String service : inboundServices) {
        if (AppEngineWebXml.WARMUP_SERVICE.equals(service)) {
          warmupService = true;
        } else {
          xml.simpleElement("service", service);
        }
      }
      xml.endElement("inbound-services");
    }
    if (appEngineBundledServices != null) {
      xml.startElement("app-engine-bundled-services");
      for (String api : appEngineBundledServices) {
        xml.simpleElement("api", api);
      }
      xml.endElement("app-engine-bundled-services");
    }
    xml.simpleElement("warmup-requests-enabled", Boolean.toString(warmupService));
    if (adminConsole != null && adminConsole.getPages() != null) {
      xml.startElement("admin-console");
      for (AdminPage page : adminConsole.getPages()) {
        xml.emptyElement("page", "name", page.getName(), "url", page.getUrl());
      }
      xml.endElement("admin-console");
    }
    if (errorHandlers != null) {
      xml.startElement("static-error-handlers");
      for (ErrorHandler handler : errorHandlers) {
        xml.emptyElement("handler",
                         "file", handler.getFile(),
                         "error-code", handler.getError_code());
      }
      xml.endElement("static-error-handlers");
    }
    if (apiConfig != null) {
      apiConfig.generateXml(xml);
    }
    xml.endElement("appengine-web-app");
  }

  /**
   * Generates the {@code servlet}, {@code servlet-mapping}, {@code filter}, and
   * {@code filter-mapping} elements of web.xml corresponding to the {@link #handlers} list. There
   * may be multiple {@link Handler handlers} corresponding to the same servlet or filter, because a
   * single handler can only specify one URL pattern and the user may wish to map several URL
   * patterns to the same servlet or filter. In this case we want to have multiple
   * {@code servlet-mapping} or {@code filter-mapping} elements but only a single {@code servlet} or
   * {@code filter} element.
   */
  private void generateHandlerXml(XmlWriter xmlWriter) {
    if (handlers == null) {
      return;
    }
    // Merge handlers for the same servlet or the same filter
    Map<String, Handler> servletsByName = Maps.newLinkedHashMapWithExpectedSize(handlers.size());
    Map<String, Handler> filtersByName = Maps.newLinkedHashMapWithExpectedSize(handlers.size());
    for (Handler handler : handlers) {
      String name = handler.getName();
      if (name != null) {
        Handler.Type type = handler.getType();
        boolean isServlet = (type == Handler.Type.SERVLET || type == Handler.Type.JSP);
        boolean isFilter = (type == Handler.Type.FILTER);
        Handler existing = (isServlet ? servletsByName.get(name) : filtersByName.get(name));
        if (existing != null) {
          existing.mergeDefinitions(handler);
        } else {
          if (isServlet) {
            servletsByName.put(name, handler);
          }
          if (isFilter) {
            filtersByName.put(name, handler);
          }
        }
      }
    }
    for (Handler handler : servletsByName.values()) {
      handler.generateDefinitionXml(xmlWriter);
    }
    for (Handler handler : filtersByName.values()) {
      handler.generateDefinitionXml(xmlWriter);
    }
    for (Handler handler : handlers) {
      handler.generateMappingXml(xmlWriter);
    }
  }

  public void generateWebXml(Writer writer) {
    XmlWriter xml = new XmlWriter(writer);
    xml.startElement("web-app", "version", "2.5",
        "xmlns", "http://java.sun.com/xml/ns/javaee",
        "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance",
        "xsi:schemaLocation",
        "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
    );
    generateHandlerXml(xml);
    if (contextParams != null) {
      contextParams.forEach(
          (name, value) -> {
            xml.startElement("context-param");
            xml.simpleElement("param-name", name);
            xml.simpleElement("param-value", value);
            xml.endElement("context-param");
          }
      );
    }
    if (welcomeFiles != null) {
      xml.startElement("welcome-file-list");
      for (String file : welcomeFiles) {
        xml.simpleElement("welcome-file", file);
      }
      xml.endElement("welcome-file-list");
    }
    if (listeners != null) {
      for (String listener : listeners) {
        xml.startElement("listener");
        xml.simpleElement("listener-class", listener);
        xml.endElement("listener");
      }
    }
    if (webXml != null) {
      xml.writeUnescaped(webXml);
    }
    xml.endElement("web-app");
  }

  public static AppYaml parse(Reader reader) {
    try {
      AppYaml appYaml = YamlUtils.parse(reader, AppYaml.class);
      if (appYaml == null) {
        throw new YamlException("Unable to parse yaml file");
      }
      return appYaml.applyPlugins();
    } catch (YamlException e) {
      // If an inner exception is already an AppEngineConfigException then
      // just re-throw it without re-wrapping
      Throwable innerException = e.getCause();

      while (innerException != null) {
        if (innerException instanceof AppEngineConfigException) {
          throw (AppEngineConfigException) innerException;
        }
        innerException = innerException.getCause();
      }

      throw new AppEngineConfigException(e.getMessage(), e);
    }
  }

  public static AppYaml parse(String yaml) {
    return parse(new StringReader(yaml));
  }

  private void generateInclude(StaticFile include, XmlWriter xml) {
    Map<String, String> httpHeaders = include.getHttp_headers();
    if (httpHeaders == null || httpHeaders.isEmpty()) {
      xml.emptyElement(
          "include", "path", include.getInclude(), "expiration", include.getExpiration());
    } else {
      xml.startElement(
          "include",
          false, // not empty
          "path",
          include.getInclude(),
          "expiration",
          include.getExpiration());
      httpHeaders.forEach(
          (name, value) -> xml.emptyElement("http-header", "name", name, "value", value));
      xml.endElement("include");
    }
  }
}
