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

import com.google.apphosting.base.protos.Status.StatusProto;

/**
 * RPC-agnostic client-side RPC context. An instance of this class is used for one RPC call.
 */
public interface AnyRpcClientContext {
  int getApplicationError();

  String getErrorDetail();

  long getStartTimeMillis();

  StatusProto getStatus();

  Throwable getException();

  /**
   * Set the deadline that will be applied to the RPC call made using this context. If this method
   * is never called, there is no deadline.
   *
   * @throws IllegalArgumentException if the deadline is negative or too long. The maximum allowed
   *     deadline is unspecified, but is at least some number of years.
   */
  void setDeadline(double seconds);

  void startCancel();
}
