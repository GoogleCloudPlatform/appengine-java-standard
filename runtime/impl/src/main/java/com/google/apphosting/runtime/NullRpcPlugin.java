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

package com.google.apphosting.runtime;

import com.google.apphosting.runtime.anyrpc.AnyRpcPlugin;
import com.google.apphosting.runtime.anyrpc.CloneControllerServerInterface;
import com.google.apphosting.runtime.anyrpc.EvaluationRuntimeServerInterface;

/**
 * An RPC plugin that does nothing. This is used for the case where the client for
 * {@link EvaluationRuntimeServerInterface} and {@link CloneControllerServerInterface} is actually
 * in the same process, so there is no RPC.
 */
public class NullRpcPlugin extends AnyRpcPlugin {
  public NullRpcPlugin() {
  }

  @Override
  public void initialize(int serverPort) {
  }

  @Override
  public void startServer(
      EvaluationRuntimeServerInterface evaluationRuntime,
      CloneControllerServerInterface cloneController) {
  }

  @Override
  public boolean serverStarted() {
    return true;
  }

  @Override
  public void blockUntilShutdown() {
  }

  @Override
  public void stopServer() {
  }

  @Override
  public void shutdown() {
  }

  @Override
  public Runnable traceContextPropagating(Runnable runnable) {
    // TODO: do we need to do something special?
    return runnable;
  }
}
