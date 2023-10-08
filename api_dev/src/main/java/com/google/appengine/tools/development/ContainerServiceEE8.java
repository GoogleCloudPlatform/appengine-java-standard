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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides the backing servlet container support for the {@link DevAppServer},
 * as discovered via {@link ServiceProvider}.
 * <p>
 * More specifically, this interface encapsulates the interactions between the
 * {@link DevAppServer} and the underlying servlet container, which by default
 * uses Jetty.
 *
 */
public interface ContainerServiceEE8 extends ContainerService {

  /**
   * Forwards an HttpRequest request to this container.
   */
  void forwardToServer(HttpServletRequest hrequest, HttpServletResponse hresponse)
      throws IOException, ServletException;

}
