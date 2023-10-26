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

import com.google.common.base.Joiner;
import javax.annotation.concurrent.GuardedBy;

/**
 * Holder for the state of a module or backend instance.
 */
public class InstanceStateHolder {
  /*
   * A module instance goes though these states: SHUTDOWN -> INITIALIZING -> [STOPPED] ->
   * SLEEPING -> RUNNING_START_REQUEST -> RUNNING -> STOPPING -> SHUTDOWN
   *
   * The jetty server is running in all states except INITIALIZING and SHUTDOWN
   *
   * Incoming requests are handled differently depending on state:
   *
   * SHUTDOWN/INITIALIZING: Incoming request will timeout (there is nothing
   * listening on the port).
   *
   * SLEEPING: An incoming request will trigger a startup request and be queued
   * until the startup request completes.
   *
   * RUNNING_START_REQUEST: All incoming request will be queued until the start
   * request completes.
   *
   * RUNNING: Incoming requests are handled. If the instance is handling
   * "max_concurrent_requests" the behavior depends on the module type. If the
   * module type supports pending queues incoming requests are queued, otherwise
   * a 500 error response is sent back.
   *
   * STOPPED: Incoming requests get a 500 error response.
   */
  public static enum InstanceState {
    INITIALIZING, SLEEPING, RUNNING_START_REQUEST, RUNNING, STOPPED, SHUTDOWN;
  }

  private static final Joiner STATE_JOINER = Joiner.on("|");
  private final String moduleOrBackendName;
  private final int instance;
  @GuardedBy("this")
  private InstanceState currentState = InstanceState.SHUTDOWN;

  /**
   * Constructs an {@link InstanceStateHolder}.
   *
   * @param moduleOrBackendName For module instances the module name and for backend instances the
   *     backend name.
   * @param instance The instance number or -1 for load balancing instances and automatic module
   *     instances.
   */
  public InstanceStateHolder(String moduleOrBackendName, int instance) {
    this.moduleOrBackendName = moduleOrBackendName;
    this.instance = instance;
  }

  /**
   * Updates the current instance state and verifies that the previous state is what is expected.
   *
   * @param newState The new state to change to
   * @param acceptablePreviousStates Acceptable previous states
   * @throws IllegalStateException If the current state is not one of the acceptable previous states
   */
  public void testAndSet(InstanceState newState, InstanceState... acceptablePreviousStates)
      throws IllegalStateException {
    InstanceState invalidState =
        testAndSetIf(newState, acceptablePreviousStates);
    if (invalidState != null) {
      reportInvalidStateChange(moduleOrBackendName, instance, invalidState,
          newState, acceptablePreviousStates);
    }
  }

  /**
   * Reports an invalid state change attempt.
   */
  static void reportInvalidStateChange(String moduleOrBackendName, int instance,
      InstanceState currentState, InstanceState newState,
      InstanceState... acceptablePreviousStates) {
    StringBuilder error = new StringBuilder();
    error.append("Tried to change state to " + newState);
    error.append(" on module " + moduleOrBackendName + "." + instance);
    error.append(" but previous state is " + currentState);
    error.append(" and not ");
    error.append(STATE_JOINER.join(acceptablePreviousStates));
    throw new IllegalStateException(error.toString());
  }

  /**
   * Updates the instance state to the requested value and returns
   * null if the previous state is an acceptable value and if not leaves the
   * current module state unchanged and returns the current invalid state.
   */
  synchronized InstanceState testAndSetIf(InstanceState newState,
      InstanceState... acceptablePreviousStates) {
    InstanceState result = currentState;
    if (test(acceptablePreviousStates)) {
      result = null;
      currentState = newState;
    }
    return result;
  }

  /** Returns true if current state is one of the provided acceptable states. */
  public synchronized boolean test(InstanceState... acceptableStates) {
    for (InstanceState acceptable : acceptableStates) {
      if (currentState == acceptable) {
        return true;
      }
    }
    return false;
  }

  /**
   * Throws an IllegalStateException if the current state is not one of the
   * acceptable states for the designated operation.
   */
  synchronized void requireState(String operation, InstanceState... acceptableStates) {
    if (!test(acceptableStates)) {
      throw new IllegalStateException("Invalid current state operation=" + operation
          + " currentState=" + currentState
          + " acceptableStates=" + STATE_JOINER.join(acceptableStates));
    }
  }

  /**
   * Checks if the instance is in a state where it can accept incoming requests.
   *
   * @return true if the instance can accept incoming requests, false otherwise.
   */
  public synchronized boolean acceptsConnections() {
    return (currentState == InstanceState.RUNNING
        || currentState == InstanceState.RUNNING_START_REQUEST
        || currentState == InstanceState.SLEEPING);
  }

  /** Returns the display name for the current state. */
  public synchronized String getDisplayName() {
    return currentState.name().toLowerCase();
  }

  /**
   * Unconditionally sets the state.
   */
  synchronized void set(InstanceState newState) {
    currentState = newState;
  }
}
