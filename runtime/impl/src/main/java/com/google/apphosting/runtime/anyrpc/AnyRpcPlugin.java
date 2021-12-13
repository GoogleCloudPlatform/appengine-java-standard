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

package com.google.apphosting.runtime.anyrpc;

/**
 * Base class for RPC-specific plugins.
 */
public abstract class AnyRpcPlugin {
  protected AnyRpcPlugin() {}

  /**
   * Initializes the plugin, possibly creating any sockets/files/channels/connections
   * needed.
   *
   * @param serverPort the port to listen for RPCs on.
   */
  public abstract void initialize(int serverPort);

  /**
   * Starts the server using the two specified implementations of the
   * RPC-agnostic EvaluationRuntime and CloneController interfaces.
   *
   * @param evaluationRuntime the evaluation runtime service implementation
   * @param cloneController the clone controller service implementation
   */
  public abstract void startServer(
      EvaluationRuntimeServerInterface evaluationRuntime,
      CloneControllerServerInterface cloneController);

  /**
   * Returns true if the server has been started and has not stopped.
   */
  public abstract boolean serverStarted();

  /**
   * Runs the main loop for the server and waits for the server to shutdown.
   *
   * <p>This method MUST be called from the same thread that called
   * {@link #startServer(EvaluationRuntimeServerInterface, CloneControllerServerInterface)}.
   *
   * @throws AssertionError if {@code startServer} was not called or didn't complete
   *                        successfully
   */
  public abstract void blockUntilShutdown();

  /**
   * Stops the server.
   *
   * @throws AssertionError if {@code startServer} was not called or didn't complete
   *                        successfully
   */
  public abstract void stopServer();

  // These methods are Used by JavaRuntimeFactory to implement the CloneController.Callback
  // interface, which abstracts over some otherwise RPC-specific functionality.

  /**
   * Performs any actions that are necessary to shut down any clients or servers started from this
   * plugin.
   */
  public abstract void shutdown();

  /**
   * Wraps the provided {@code Runnable} so as to assure propagation of the tracing context.
   *
   * @param runnable the Runnable to wrap
   * @return a Runnable that sets up propagation of the tracing context and then invokes
   *     the original one
   */
  public abstract Runnable traceContextPropagating(Runnable runnable);
}
