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

import java.io.IOException;


/**
 * A {@link ModulesFilterHelper} for delegating requests to either
 * {@link BackendServers} for backends or {@link Modules} for module instances.
 */
public class DelegatingModulesFilterHelper implements ModulesFilterHelper {

  protected final BackendServers backendServers;
  protected final Modules modules;

  public DelegatingModulesFilterHelper(BackendServers backendServers, Modules modules) {
    this.backendServers = backendServers;
    this.modules = modules;
  }

  @Override
  public boolean acquireServingPermit(
      String moduleOrBackendName, int instanceNumber, boolean allowQueueOnBackends) {
    if (isBackend(moduleOrBackendName)) {
     return backendServers.acquireServingPermit(moduleOrBackendName, instanceNumber,
         allowQueueOnBackends);
    } else {
     return modules.acquireServingPermit(moduleOrBackendName, instanceNumber, allowQueueOnBackends);
    }
  }

  @Override
  public int getAndReserveFreeInstance(String moduleOrBackendName) {
    if (isBackend(moduleOrBackendName)) {
      return backendServers.getAndReserveFreeInstance(moduleOrBackendName);
     } else {
       return modules.getAndReserveFreeInstance(moduleOrBackendName);
     }
  }

  @Override
  public void returnServingPermit(String moduleOrBackendName, int instance) {
    if (isBackend(moduleOrBackendName)) {
      backendServers.returnServingPermit(moduleOrBackendName, instance);
     } else {
       modules.returnServingPermit(moduleOrBackendName, instance);
     }
  }

  @Override
  public boolean checkInstanceExists(String moduleOrBackendName, int instance) {
    if (isBackend(moduleOrBackendName)) {
      return backendServers.checkInstanceExists(moduleOrBackendName, instance);
    } else {
      return modules.checkInstanceExists(moduleOrBackendName, instance);
    }
  }

  @Override
  public boolean checkModuleExists(String moduleOrBackendName) {
    if (isBackend(moduleOrBackendName)) {
      return backendServers.checkServerExists(moduleOrBackendName);
    } else {
     return modules.checkModuleExists(moduleOrBackendName);
    }
  }

  @Override
  public boolean checkModuleStopped(String moduleOrBackendName) {
    if (isBackend(moduleOrBackendName)) {
      return backendServers.checkServerStopped(moduleOrBackendName);
    } else {
      return modules.checkModuleStopped(moduleOrBackendName);
    }
  }

  @Override
  public boolean checkInstanceStopped(String moduleOrBackendName, int instance) {
    if (isBackend(moduleOrBackendName)) {
      return backendServers.checkInstanceStopped(moduleOrBackendName, instance);
    } else {
      return modules.checkInstanceStopped(moduleOrBackendName, instance);
    }
  }
     
  @Override
  public boolean isLoadBalancingInstance(String moduleOrBackendName, int instance) {
    if (isBackend(moduleOrBackendName)) {
      return false;
    } else {
      return modules.isLoadBalancingInstance(moduleOrBackendName, instance);
    }
  }

  protected boolean isBackend(String moduleOrBackendName) {
    return backendServers.checkServerExists(moduleOrBackendName);
  }

  @Override
  public boolean expectsGeneratedStartRequests(String moduleOrBackendName, int instance) {
    if (isBackend(moduleOrBackendName)) {
      return instance >= 0;
    } else {
      return modules.expectsGeneratedStartRequests(moduleOrBackendName, instance);
    }
  }

  @Override
  public int getPort(String moduleOrBackendName, int instance) {
    if (isBackend(moduleOrBackendName)) {
      return backendServers.getPort(moduleOrBackendName, instance);
    } else {
      return modules.getPort(moduleOrBackendName, instance);
    }
  }
}
