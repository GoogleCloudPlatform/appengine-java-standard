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

import com.google.protobuf.MessageLite;
import java.time.Duration;

/**
 * RPC-agnostic server-side RPC context.
 */
public interface AnyRpcServerContext {
  /**
   * Indicates that the RPC was handled successfully. Here "successfully" means that a normal
   * RPC response will be sent to the client. That response might still indicate an error.
   */
  void finishWithResponse(MessageLite response);

  /**
   * Indicates that the RPC was handled in a way that means an "application error" should be
   * signaled to the client. If the RPC layer is Stubby, this means the application error is
   * communicated by Stubby itself.
   */
  void finishWithAppError(int appErrorCode, String errorDetail);

  /**
   * Returns the remaining time for this call. This value decreases while the call is being handled.
   */
  Duration getTimeRemaining();

   /**
   * Returns a trace id for this request. Ideally provided by the RPC client so that the request
   * is tied to the client's trace span.
   */
  long getGlobalId();

  /**
   * Returns the time at which the RPC began, in milliseconds since midnight UTC on 1970-01-01.
   */
  long getStartTimeMillis();
}
