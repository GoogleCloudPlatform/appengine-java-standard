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

package com.google.apphosting.utils.servlet;

import com.google.appengine.tools.development.ModulesController;
import com.google.apphosting.api.ApiProxy;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handler for the modules viewer.
 * <p>
 * Shows a list of all configured modules and the state of each module instance.
 *
 */
@SuppressWarnings("serial")
public class ModulesServlet extends HttpServlet {

  private static final String AH_ADMIN_MODULES_PATH = "/_ah/admin/modules";

  private static final Logger logger = Logger.getLogger(ModulesServlet.class.getName());

  private static final String DEFAULT_MODULE_NAME = "defaultModuleName";

  private static final String APPLICATION_NAME = "applicationName";

  private static final String MODULES_STATE_INFO = "modulesStateInfo";

  // "ACTIONS" posted to this servlet
  private static final String ACTION_MODULE = "action:module";


  public ModulesServlet() {}

  private ModulesController getModulesController() {
    return (ModulesController)
        ApiProxy.getCurrentEnvironment()
            .getAttributes()
            .get(ModulesController.MODULES_CONTROLLER_ATTRIBUTE_KEY);
  }

  private ImmutableList<String> getAllInstanceHostnames(String moduleName, String version) {
    ImmutableList.Builder<String> hostnameListBuilder = ImmutableList.builder();
    for (int i = 0; i < getModulesController().getNumInstances(moduleName, version); i++) {
      hostnameListBuilder.add(getModulesController().getHostname(moduleName, version, i));
    }
    return hostnameListBuilder.build();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setAttribute(APPLICATION_NAME, ApiProxy.getCurrentEnvironment().getAppId());

    final ModulesController modulesController = getModulesController();
    req.setAttribute(DEFAULT_MODULE_NAME,
        Iterables.getFirst(modulesController.getModuleNames(), ""));

    Iterable<Map<String, Object>> modulesMap =
        Iterables.transform(
            modulesController.getModuleNames(),
            new Function<String, Map<String, Object>>() {
              @Override
              public Map<String, Object> apply(String moduleName) {
                String version = modulesController.getDefaultVersion(moduleName);
                if (version == null) {
                  version = "unknown";
                }
                ImmutableMap.Builder<String, Object> mapBuilder =
                    ImmutableMap.<String, Object>builder()
                        .put("name", moduleName)
                        .put("state", modulesController.getModuleState(moduleName).toString())
                        .put("version", version)
                        .put("hostname", modulesController.getHostname(moduleName, version, -1))
                        .put("type", modulesController.getScalingType(moduleName));

                if (modulesController.getScalingType(moduleName).startsWith("Manual")) {
                  mapBuilder.put("instances", getAllInstanceHostnames(moduleName, version));
                }

                return mapBuilder.buildOrThrow();
              }
            });

    req.setAttribute(MODULES_STATE_INFO, ImmutableList.copyOf(modulesMap));

    try {
      getServletContext()
          .getRequestDispatcher("/_ah/adminConsole?subsection=modules")
          .forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

    final String moduleName = req.getParameter("moduleName");
    final String moduleVersion = req.getParameter("moduleVersion");
    final String action = req.getParameter(ACTION_MODULE);

    if (action != null && moduleName != null && moduleVersion != null) {
      try {
        if (action.equals("Stop")) {
          getModulesController().stopModule(moduleName, moduleVersion);
        } else if (action.equals("Start")) {
          getModulesController().startModule(moduleName, moduleVersion);
        }
      } catch (Exception e) {
        logger.severe("Got error when performing a " + action + " of module : " + moduleName);
      }
    } else {
      logger.severe("The post method against the modules servlet was called without all of the " +
          "expected post parameters, we got [moduleName = " + moduleName + ", moduleVersion = " +
          moduleVersion + ", and action = " + action + "]");
    }
    resp.sendRedirect(AH_ADMIN_MODULES_PATH);
  }
}
