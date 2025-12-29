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

import com.google.apphosting.api.ApiProxy;
import java.util.Locale;

/**
 * Modules query and control operations needed by the ModulesService.
 */
public interface ModulesController {

  public static final String MODULES_CONTROLLER_ATTRIBUTE_KEY =
      "com.google.appengine.dev.modules_controller";

  /**
   * Enum for tracking the state of a module (running/stopped).
   */
  public enum ModuleState {
    RUNNING,
    STOPPED;

    @Override
    public String toString() {
      return this.name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * Returns all the known module names.
   */
  Iterable<String> getModuleNames();

  /**
   * Returns all known versions of the requested module.
   *
   * @throws ApiProxy.ApplicationException with error code {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
   * if the requested module is not configured
   */
  Iterable<String> getVersions(String moduleName) throws ApiProxy.ApplicationException;

  /**
   * Returns the default version for a named module.
   *
   * @throws ApiProxy.ApplicationException with error code {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
   * if the requested module is not configured
   */
  String getDefaultVersion(String moduleName) throws ApiProxy.ApplicationException;

  /**
   * Returns the number of instances for the requested module version.
   *
   * @throws ApiProxy.ApplicationException with error code {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
   * if the requested module is not configured and {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_VERSION_VALUE}
   * if the requested version is not configured.
   */
  int getNumInstances(String moduleName, String version) throws ApiProxy.ApplicationException;

  /**
   * Sets the number of instances for the requested module version.
   *
   * @throws ApiProxy.ApplicationException with error code {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
   * if the requested module is not configured and {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_VERSION_VALUE}
   * if the requested version is not configured for setting instances {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_INSTANCES}
   * if numInstances is not a legal value.
   */
  void setNumInstances(String moduleName, String version, int numInstances)
    throws ApiProxy.ApplicationException;


  /**
   * Returns the host name of the requested module version instance.
   *
   * @param moduleName the moduleName whose host we return.
   * @param version the version whose host we return.
   * @param instance the instance whose host we return or {@link com.google.appengine.tools.development.LocalEnvironment#MAIN_INSTANCE}
   *
   * @throws ApiProxy.ApplicationException with error code {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
   * if the requested module is not configured and {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_VERSION_VALUE}
   * if the requested version is not configured and {@link
   * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_INSTANCES_VALUE}
   * if the requested instance is not configured.
   */
 String getHostname(String moduleName, String version, int instance)
     throws ApiProxy.ApplicationException;

 /**
  * Starts the requested module version.
  * @throws ApiProxy.ApplicationException {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
  * if the requested module is not a configured manual scaling module and {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_VERSION_VALUE}
  * if the requested version is not configured or is not a manual scaling module and {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#UNEXPECTED_STATE}
  * if the module instance is not stopped and ready to be started.
  */
 void startModule(String moduleName, String version) throws ApiProxy.ApplicationException;

 /**
  * Stops the requested module version.
  * @throws ApiProxy.ApplicationException {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
  * if the requested module is not a configured manual scaling module and {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_VERSION_VALUE}
  * if the requested version is not configured or is not a manual scaling module and {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#UNEXPECTED_STATE}
  * if the module instance is not running and ready to be stopped.
  */
 void stopModule(String moduleName, String version) throws ApiProxy.ApplicationException;

 /**
  * Returns the type of scaling in use for this module.
  *
  * @throws ApiProxy.ApplicationException with error code {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
  * if the requested module is not configured
  */
 String getScalingType(String moduleName) throws ApiProxy.ApplicationException;

 /**
  * Returns the current state of this module.
  *
  * @throws ApiProxy.ApplicationException with error code {@link
  * com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError.ErrorCode#INVALID_MODULE_VALUE}
  * if the requested module is not configured
  */
 ModuleState getModuleState(String moduleName) throws ApiProxy.ApplicationException;
}
