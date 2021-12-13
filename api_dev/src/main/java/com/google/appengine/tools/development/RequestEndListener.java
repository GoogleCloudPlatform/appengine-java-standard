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

import com.google.apphosting.api.ApiProxy.Environment;

/**
 * A {@code RequestEndListener} is a hook that allows
 * local API services to be notified when a request
 * to the Dev App Server has completed.
 * <p>
 * A <i>local API service</i> is a class that implements
 * {@link com.google.appengine.tools.development.LocalRpcService}.
 * As described in that interface, a <i>service call</i> is
 * a method implemented by a local API service
 * that takes a request protocol buffer and returns a
 * response protocol buffer. During a service call
 * a local API service may register a {@code RequestEndListener} for the current request using
 * {@link RequestEndListenerHelper}.
 *
 */
public interface RequestEndListener {
  /**
   * This method is invoked on all registered {@code RequestEndListeners}
   * when the current request to the Dev App Server has completed.
   * @param environment Environment associated with the request. Among
   * other data contained in this environment is a unique request ID.
   * This may be obtained using the following code:
   *  <blockquote>
   * <pre>
   *  String requestID =
   *  (String) environment.getAttributes().get(LocalEnvironment.REQUEST_ID);
   * </pre>
   * </blockquote>
   */
  void onRequestEnd(Environment environment);
}
