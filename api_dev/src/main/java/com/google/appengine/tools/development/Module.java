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
import java.util.Map;

/**
 * Holder for both configuration and runtime information for a single
 * {@link DevAppServer} module and all its instances.
 *
 */
public interface Module {
  /**
   * Configure this {@link Module}.
   * <p>
   * Note {@link #configure} fits into the {@link DevAppServer} startup
   * sequence. The user may adjust {@link DevAppServer#setServiceProperties}
   * values after construction and before calling {@link DevAppServer#start()}
   * which calls {@link #configure}. To retain compatibility operations that
   * make use of these user specified settings such as port selection must not
   * not be performed during construction.
   * @param containerConfigProperties container configuration properties.
   * @throws Exception
   */
  void configure(Map<String, Object>containerConfigProperties) throws Exception;

  /**
   * Sets the {@link com.google.apphosting.api.ApiProxy.Delegate}.
   */
  void setApiProxyDelegate(ApiProxy.Delegate<?> apiProxyDelegate);

  /**
   * Creates the network connections for this {@link Module}.
   * @throws Exception
   */
  void createConnection() throws Exception;

  /**
   * Starts all the instances for this {@link Module}. Once this returns the
   * {@link Module} can handle HTTP requests.
   * @throws Exception
   */
  void startup() throws Exception;

  /**
   * Stops all the instances for this {@link Module}. Once this returns the
   * {@link Module} cannot handle HTTP requests.
   * @throws Exception
   */
  void shutdown() throws Exception;

  /**
   * Simulates stopping the module in production.
   *
   * @throws UnsupportedOperationException if this is not a manual module.
   * @throws Exception
   */
  void stopServing() throws Exception;

  /**
   * Simulates starting the module in production.
   *
   * @throws UnsupportedOperationException if this is not a manual module.
   * @throws Exception
   */
  void startServing() throws Exception;

  /**
   * Returns the module name for this {@link Module}.
   */
  String getModuleName();

  /**
   * Returns the {@link ContainerService} for the primary instance for this
   * {@link Module}.
   */
  ContainerService getMainContainer();

  /**
   * Returns the {@link LocalServerEnvironment} for the primary instance for
   * this {@link Module}.
   */
  LocalServerEnvironment getLocalServerEnvironment();

  /**
   * Returns the host and port for the requested instance or null if the
   * instance does not exist.
   * @param instance The instance number or {@link LocalEnvironment#MAIN_INSTANCE}.
   */
  String getHostAndPort(int instance);

  /**
   * Returns the requested {@link InstanceHolder} or null if the instance does
   * not exist.
   * @param instance the instance number or {@link LocalEnvironment#MAIN_INSTANCE}.
   */
  InstanceHolder getInstanceHolder(int instance);

  /**
   * Returns the number of instances for this module. This will return 0 for
   * an {@link AutomaticModule}.
   */
  int getInstanceCount();

  /**
   * Acquires a serving permit and returns an {@link InstanceHolder} for an
   * instance which is available to handle a request or returns null if there
   * is no such instance.
   * <p>
   * throws {@link UnsupportedOperationException} unless this is a
   * {@link ManualModule}.
   */
  InstanceHolder getAndReserveAvailableInstanceHolder();
}
