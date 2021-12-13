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

import com.google.apphosting.base.protos.RuntimePb.APIRequest;
import com.google.apphosting.base.protos.RuntimePb.APIResponse;

/**
 * RPC-agnostic equivalent of APIHost.ClientInterface, plus an RPC client context
 * factory method.
 *
 * <p>If you add methods to APIHost in apphosting/base/runtime.proto,
 * you need to remember to add them here too. Note that we omit the
 * blocking version of the methods, since they are currently unused.
 */
public interface APIHostClientInterface extends AnyRpcClientContextFactory {
  void call(AnyRpcClientContext ctx, APIRequest req, AnyRpcCallback<APIResponse> cb);

  /**
   * Closes any outgoing connections using this client and prevents new ones from being created.
   *
   * @throws UnsupportedOperationException if this implementation does not support disable/enable.
   */
  void disable();

  /**
   * Allows new connections to be created from this client. If a previous {@link #disable} call had
   * stopped their creation, this will allow it again. Otherwise this method has no effect.
   */
  void enable();
}
