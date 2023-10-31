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

package com.google.appengine.tools.development.ee10;

import com.google.appengine.tools.development.BackendServersBase;
import com.google.appengine.tools.development.DelegatingModulesFilterHelper;
import com.google.appengine.tools.development.Modules;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** */
public class DelegatingModulesFilterHelperEE10 extends DelegatingModulesFilterHelper
    implements ModulesFilterHelperEE10 {

  public DelegatingModulesFilterHelperEE10(BackendServersBase backendServers, Modules modules) {
    super(backendServers, modules);
  }

  @Override
  public void forwardToInstance(
      String moduleOrBackendName,
      int instance,
      HttpServletRequest hrequest,
      HttpServletResponse response)
      throws IOException, ServletException {
    if (isBackend(moduleOrBackendName)) {
      ((BackendServersEE10) backendServers)
          .forwardToServer(moduleOrBackendName, instance, hrequest, response);
    } else {
      ((ModulesEE10) modules).forwardToInstance(moduleOrBackendName, instance, hrequest, response);
    }
  }
}
