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

package com.google.apphosting.runtime.jetty9;

import java.io.IOException;
import java.util.EventListener;
import javax.servlet.ServletException;

import com.google.apphosting.runtime.jetty94.AppEngineWebAppContext;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.webapp.WebAppContext;

/**
 * {@code RequestListener} is called for new request and request completion events. It is abstracted
 * away from Servlet and/or Jetty API so that behaviours can be registered independently of servlet
 * and/or jetty version. {@link AppEngineWebAppContext} is responsible for linking these callbacks
 * and may use different mechanisms in different versions (Eg eventually may use async onComplete
 * callbacks when async is supported).
 *
 */
public interface RequestListener extends EventListener {

  /**
   * Called when a new request is received and first dispatched to the AppEngine context. It is only
   * called once for any request, even if dispatched multiple times.
   *
   * @param context The jetty context of the request
   * @param request The jetty request object.
   * @throws IOException if a problem with IO
   * @throws ServletException for all other problems
   */
  void requestReceived(WebAppContext context, Request request)
      throws IOException, ServletException;

  /**
   * Called when a request exits the AppEngine context for the last time. It is only called once for
   * any request, even if dispatched multiple times.
   *
   * @param context The jetty context of the request
   * @param request The jetty request object.
   */
  void requestComplete(WebAppContext context, Request request);
}
