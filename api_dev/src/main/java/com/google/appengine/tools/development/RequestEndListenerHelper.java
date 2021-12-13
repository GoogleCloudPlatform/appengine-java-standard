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
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.Collection;

/**
 * Helper class for {@link RequestEndListener}. This class provides two alternate ways to register
 * a {@link RequestEndListener}. The first method works with classes that implement a {@link
 * RequestEndListener}:
 * <blockquote><pre>
 *   RequestEndListener listener = ...;
 *   RequestEndListenerHelper.register(listener);
 * </pre></blockquote>
 * The second method is for classes able to extend this class:
 * <blockquote><pre>
 *   RequestEndListenerHelper listener = ...;
 *   listener.register();
 * </pre></blockquote>
 */
public abstract class RequestEndListenerHelper implements RequestEndListener {
  /**
   * Register the current instance to be called when a request ends.
   */
  public void register() {
    register(this);
  }

  /**
   * Register a RequestEndListener to be called when a request ends.
   *
   * @param listener The listener to register.
   */
  public static void register(RequestEndListener listener) {
    getListeners().add(listener);
  }

  /**
   * Get the collection of all the registered RequestEndListeners. The collection is mutable
   * so the caller may add or remove listeners
   *
   * @return The collection of registered RequestEndListeners.
   */
  public static Collection<RequestEndListener> getListeners() {
    Environment environment = ApiProxy.getCurrentEnvironment();
    @SuppressWarnings("unchecked")
    Collection<RequestEndListener> listenerSet = (Collection<RequestEndListener>)
        environment.getAttributes().get(LocalEnvironment.REQUEST_END_LISTENERS);
    return listenerSet;
  }
}
