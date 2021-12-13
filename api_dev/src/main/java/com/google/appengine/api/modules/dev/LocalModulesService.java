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

package com.google.appengine.api.modules.dev;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.modules.ModulesServicePb.GetDefaultVersionRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetDefaultVersionResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetHostnameRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetHostnameResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetModulesRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetModulesResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetNumInstancesRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetNumInstancesResponse;
import com.google.appengine.api.modules.ModulesServicePb.GetVersionsRequest;
import com.google.appengine.api.modules.ModulesServicePb.GetVersionsResponse;
import com.google.appengine.api.modules.ModulesServicePb.SetNumInstancesRequest;
import com.google.appengine.api.modules.ModulesServicePb.SetNumInstancesResponse;
import com.google.appengine.api.modules.ModulesServicePb.StartModuleRequest;
import com.google.appengine.api.modules.ModulesServicePb.StartModuleResponse;
import com.google.appengine.api.modules.ModulesServicePb.StopModuleRequest;
import com.google.appengine.api.modules.ModulesServicePb.StopModuleResponse;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.appengine.tools.development.ModulesController;
import com.google.auto.service.AutoService;
import java.util.Map;

/**
 * Java binding for the local ModulesService.
 */
@AutoService(LocalRpcService.class)
public class LocalModulesService extends AbstractLocalRpcService {
  // Keep this in sync with LocalEnvironment.MAIN_INSTANCE and
  // LocalModulesServiceTestConfig.MAIN_INSTANCE
  // TODO: Try to define this in one place only without violating dependency rules.
  private static final int MAIN_INSTANCE = -1;
  /**
   * The package name for this service.
   */
  public static final String PACKAGE = "modules";
  private ModulesController modulesController;
  private ModulesService modulesService;
  private String serverHostName;

  @Override
  public Double getDefaultDeadline(boolean isOfflineRequest) {
    // Local dev server default is 5, and not enough for our EAR unit tests, sometimes.
    return 20.0;
  }

  @Override
  public Double getMaximumDeadline(boolean isOfflineRequest) {
    // Local dev server default is 10, and not enough for our EAR unit tests, sometimes.
    return 40.0;
  }

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    super.init(context, properties);
    checkNotNull(context, "context must not be null");
    checkArgument(context.getLocalServerEnvironment() != null,
        "context.getLocalServerEnvironment() must not be null");
    checkArgument(context.getLocalServerEnvironment().getHostName() != null,
        "context.getLocalServerEnvironment() must not be null");
    serverHostName = context.getLocalServerEnvironment().getHostName();
  }

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  public GetModulesResponse getModules(Status status, GetModulesRequest request) {
    GetModulesResponse.Builder result = GetModulesResponse.newBuilder();
    for (String moduleName : modulesController.getModuleNames()) {
      result.addModule(moduleName);
    }
    status.setSuccessful(true);
    return result.build();
  }

  public GetVersionsResponse getVersions(Status status, GetVersionsRequest request) {
    status.setSuccessful(false);
    GetVersionsResponse.Builder result = GetVersionsResponse.newBuilder();
    String moduleName = getModuleOrCurrent(request.hasModule() ? request.getModule() : null);
    Iterable<String> versions = modulesController.getVersions(moduleName);
    for (String version : versions) {
      result.addVersion(version);
    }
    status.setSuccessful(true);
    return result.build();
  }

  public GetDefaultVersionResponse getDefaultVersion(Status status,
      GetDefaultVersionRequest request) {
    status.setSuccessful(false);
    GetDefaultVersionResponse.Builder result = GetDefaultVersionResponse.newBuilder();
    String moduleName = getModuleOrCurrent(request.hasModule() ? request.getModule() : null);
    String version = modulesController.getDefaultVersion(moduleName);
    result.setVersion(version);
    status.setSuccessful(true);
    return result.build();
  }

  public GetNumInstancesResponse getNumInstances(Status status,
      GetNumInstancesRequest request) {
    status.setSuccessful(false);
    GetNumInstancesResponse.Builder result = GetNumInstancesResponse.newBuilder();
    String moduleName = getModuleOrCurrent(request.hasModule() ? request.getModule() : null);
    String version = getVersionOrCurrent(request.hasVersion() ? request.getVersion() : null);
    int numInstances = modulesController.getNumInstances(moduleName, version);
    result.setInstances(numInstances);
    status.setSuccessful(true);
    return result.build();
  }

  public SetNumInstancesResponse setNumInstances(Status status,
      SetNumInstancesRequest request) {
    status.setSuccessful(false);
    SetNumInstancesResponse.Builder result = SetNumInstancesResponse.newBuilder();
    String moduleName = getModuleOrCurrent(request.hasModule() ? request.getModule() : null);
    String version = getVersionOrCurrent(request.hasVersion() ? request.getVersion() : null);
    int numInstances = (int) request.getInstances();
    modulesController.setNumInstances(moduleName, version, numInstances);
    status.setSuccessful(true);
    return result.build();
  }

  public StartModuleResponse startModule(Status status, StartModuleRequest request) {
    status.setSuccessful(false);
    StartModuleResponse.Builder result = StartModuleResponse.newBuilder();
    String moduleName = getModuleOrCurrent(request.hasModule() ? request.getModule() : null);
    String version = getVersionOrCurrent(request.hasVersion() ? request.getVersion() : null);
    modulesController.startModule(moduleName, version);
    status.setSuccessful(true);
    return result.build();
  }

  public StopModuleResponse stopModule(Status status, StopModuleRequest request) {
    status.setSuccessful(false);
    StopModuleResponse.Builder result = StopModuleResponse.newBuilder();
    String moduleName = getModuleOrCurrent(request.hasModule() ? request.getModule() : null);
    String version = getVersionOrCurrent(request.hasVersion() ? request.getVersion() : null);
    modulesController.stopModule(moduleName, version);
    status.setSuccessful(true);
    return result.build();
  }

  public GetHostnameResponse getHostname(Status status, GetHostnameRequest request) {
    status.setSuccessful(false);
    GetHostnameResponse.Builder result = GetHostnameResponse.newBuilder();
    String moduleName = getModuleOrCurrent(request.hasModule() ? request.getModule() : null);
    String version = getVersionOrCurrent(request.hasVersion() ? request.getVersion() : null);
    int instance = request.hasInstance() ? Integer.parseInt(request.getInstance()) : MAIN_INSTANCE;
    result.setHostname(modulesController.getHostname(moduleName, version, instance));
    status.setSuccessful(true);
    return result.build();
  }

  public void setModulesController(ModulesController modulesController) {
    this.modulesController = modulesController;
  }

  public String getServerHostname() {
    return serverHostName;
  }

  private synchronized ModulesService getModulesService() {
    if (this.modulesService == null) {
      this.modulesService = ModulesServiceFactory.getModulesService();
    }
    return modulesService;
  }

  private String getModuleOrCurrent(String module) {
    if (module == null) {
      module = getModulesService().getCurrentModule();
    }
    return module;
  }

  private String getVersionOrCurrent(String version) {
    if (version == null) {
      version = getModulesService().getCurrentVersion();
    }
    return version;
  }
}
