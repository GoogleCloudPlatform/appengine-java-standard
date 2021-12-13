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

package com.google.appengine.api.modules;

import java.util.Set;
import java.util.concurrent.Future;

/**
 * ModulesService allows the application to fetch information about its
 * own module and version information.  Additionally, the service has the
 * ability to start, stop and change the number of instances associated with a
 * module version.
 *
 */
public interface ModulesService {

  /**
   * Get the name of the current module. ("default" if modules are not enabled for this app)
   *
   * @return the name of the module
   */
  String getCurrentModule();

  /**
   * Get the name of the current version.
   *
   * @return the name of the version
   */
  String getCurrentVersion();

  /**
   * Get the id of the current instance.
   *
   * @return current instance id
   */
  String getCurrentInstanceId();

  /**
   * Get the set of modules that are available to the application.
   *
   * @return Set of modules available to the application
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  Set<String> getModules();

  /**
   * Returns the set of versions that are available to the given module.
   *
   * @param module the name of the module or null for the current module
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  Set<String> getVersions(String module);

  /**
   * Returns the name of the default version for the module.
   *
   * @param module the name of the module or null for the current module
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  String getDefaultVersion(String module);

  /**
   * Returns the number of instances that are available to the given
   * manual scaling module version.
   *
   * @param module the name of the module or null for the current module
   * @param version the name of the version or null for the current version
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  int getNumInstances(String module, String version);

  /**
   * Set the number of instances that are available to the given manual
   * scaling module version. Changing the number of instances is an
   * asynchronous process so this may return before added instances begin
   * serving or removed instances stop serving.
   *
   * @param module the name of the module or null for the current module
   * @param version the name of the version or null for the current version
   * @param instances the number of instances to set
   * @throws ModulesException when the requested number of instances
   * is not supported or {@link ModulesService} fails to perform the requested
   * operation for some other reason.
   */
  void setNumInstances(String module, String version, long instances);

  /**
   * Starts an asynchronous call to {@link #setNumInstances} and returns a {@link Future} to obtain
   * its eventual result. When the returned {@link Future} yields a successful result
   * {@link ModulesService} will have successfully initiated the process of setting the number of
   * instances. There may be some delay before added instances start serving or removed instances
   * stop serving.
   */
  Future<Void> setNumInstancesAsync(String module, String version, long instances);

  /**
   * Starts the given manual scaling or basic scaling module version. Starting a version is an
   * asynchronous process so this may return before the started version is serving. If the
   * requested module version is already started this returns without error.
   *
   * @param module the name of the module
   * @param version the name of the version
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  void startVersion(String module, String version);

  /**
   * Starts an asynchronous call to {@link #startVersion} and returns a {@link Future} to obtain its
   * eventual result. When the returned {@link Future} yields a successful result
   * {@link ModulesService} will have successfully initiated the process of starting the requested
   * version. There may be some delay before the version starts serving.
   */
  Future<Void> startVersionAsync(String module, String version);

  /**
   * Stops the given manual scaling or basic scaling module version. Stopping a version is an
   * asynchronous process so this may return before the stopped version stops serving.  If the
   * requested module version is already stopped this returns without error.
   *
   * @param module the name of the module or null for the current module
   * @param version the name of the version or null for the current version
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  void stopVersion(String module, String version);

  /**
   * Starts an asynchronous call to {@link #stopVersion} and returns a {@link Future} to obtain its
   * eventual result. When the returned {@link Future} yields a successful result
   * {@link ModulesService} will have successfully initiated the process of stopping the requested
   * version. There may be some delay before the version stops serving.
   */
  Future<Void> stopVersionAsync(String module, String version);

  /**
   * Returns a host name to use for the given module and version.
   *
   * @param module the name of the module or null to indicate the current module
   * @param version the name of the version or null to indicate the current version
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  String getVersionHostname(String module, String version);

  /**
   * Returns a host name to use for the given module, version and instance.
   *
   * @param module the name of the module or null to indicate the current module
   * @param version the name of the version or null to indicate the current version
   * @param instance the id of a particular instance to address
   * @return the hostname of the given instance
   * @throws ModulesException when {@link ModulesService} fails to perform the requested operation.
   */
  String getInstanceHostname(String module, String version, String instance);
}
