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

/**
 * Holder for per module instance state.
 */
public interface InstanceHolder {

  /**
   * Returns the {@link ContainerService} for this instance.
   */
  ContainerService getContainerService();

  /**
   * Returns the id for this instance.
   */
  int getInstance();

  /**
   * Returns true if this is the main instance, meaning the load balancing
   * instance for a {@link ManualModule} and the only instance for an
   * {@link AutomaticModule}.
   */
  boolean isMainInstance();

  /**
   * Starts the instance.
   */
  void startUp() throws Exception;

  /**
   * Acquire a serving permit for this instance. This may block and have side effects such as
   * sending a startUp request.
   */
  boolean acquireServingPermit();

  /**
   * Returns true if this instance is a load balancing instance.
   */
  boolean isLoadBalancingInstance();

  /**
   * Returns true if this instance expects an internally generated
   * "_ah/start" requests to be sent.
   */
  boolean expectsGeneratedStartRequest();

  /**
   * Returns true if this instance is in the STOPPED state.
   */
  boolean isStopped();

  /**
   * Creates a network connection for this instance.
   * @throws Exception
   */
  public void createConnection() throws Exception;
}
