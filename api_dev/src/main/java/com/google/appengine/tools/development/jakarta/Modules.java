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

package com.google.appengine.tools.development.jakarta;

import com.google.appengine.tools.development.InstanceHolder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/** Manager for {@link DevAppServer} servers. */
public class Modules extends com.google.appengine.tools.development.Modules {

  public Modules(List<com.google.appengine.tools.development.Module> modules) {
    super(modules);
  }

  public void forwardToInstance(
      String requestedModule,
      int instance,
      HttpServletRequest hrequest,
      HttpServletResponse hresponse)
      throws IOException, ServletException {
    com.google.appengine.tools.development.Module module = getModule(requestedModule);
    InstanceHolder instanceHolder = module.getInstanceHolder(instance);
    ((ContainerService) instanceHolder.getContainerService())
        .forwardToServer(hrequest, hresponse);
  }

}
