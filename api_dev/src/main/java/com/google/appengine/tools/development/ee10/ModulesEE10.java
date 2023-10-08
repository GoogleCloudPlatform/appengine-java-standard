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

import com.google.appengine.tools.development.InstanceHolder;
import com.google.appengine.tools.development.Module;
import com.google.appengine.tools.development.Modules;
import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Manager for {@link DevAppServer} servers.
 *
 */
public class ModulesEE10 extends Modules {

  protected ModulesEE10(List<Module> modules) {
      super(modules);
  }

  public void forwardToInstance(String requestedModule, int instance, HttpServletRequest hrequest,
      HttpServletResponse hresponse) throws IOException, ServletException {
    Module module = getModule(requestedModule);
    InstanceHolder instanceHolder = module.getInstanceHolder(instance);
    ((ContainerServiceEE10)instanceHolder.getContainerService()).forwardToServer(hrequest, hresponse);

  }

}
