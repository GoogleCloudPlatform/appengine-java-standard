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

package com.google.apphosting.utils.servlet.ee10;

import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.capabilities.dev.LocalCapabilitiesService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.LocalCapabilitiesEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handler for the Capabilities status change on local console.
 *
 */
@SuppressWarnings("serial")
public class CapabilitiesStatusServlet extends HttpServlet {

  private static final String APPLICATION_NAME = "applicationName";

  private LocalCapabilitiesService localCapabilitiesService;
  private LocalCapabilitiesEnvironment localCapabilitiesEnvironment;

  // TODO move that in an official public place
  static final ImmutableMap<String, Capability> CAPABILITIES =
      new ImmutableMap.Builder<String, Capability>()
          .put("BLOBSTORE", Capability.BLOBSTORE)
          .put("DATASTORE_WRITE", Capability.DATASTORE_WRITE)
          .put("DATASTORE", Capability.DATASTORE)
          .put("IMAGES", Capability.IMAGES)
          .put("MAIL", Capability.MAIL)
          .put("MEMCACHE", Capability.MEMCACHE)
          .put("PROSPECTIVE_SEARCH", Capability.PROSPECTIVE_SEARCH)
          .put("TASKQUEUE", Capability.TASKQUEUE)
          .put("URL_FETCH", Capability.URL_FETCH)
          .buildOrThrow();

  private static final String CAPABILITIES_STATUS_ATTRIBUTE = "capabilities_status";

  @Override
  public void init() throws ServletException {
    super.init();
    ApiProxyLocal apiProxyLocal = (ApiProxyLocal) getServletContext().getAttribute(
        "com.google.appengine.devappserver.ApiProxyLocal");
    localCapabilitiesService =
        (LocalCapabilitiesService) apiProxyLocal.getService(LocalCapabilitiesService.PACKAGE);
    localCapabilitiesEnvironment = localCapabilitiesService.getLocalCapabilitiesEnvironment();

  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    req.setAttribute(APPLICATION_NAME, ApiProxy.getCurrentEnvironment().getAppId());

    List<CapabilityView> capStatus = new ArrayList<CapabilityView>();
    for (Map.Entry<String, Capability> entry : CAPABILITIES.entrySet()) {
      Capability cap = entry.getValue();
      CapabilityStatus status = localCapabilitiesEnvironment.getStatusFromCapabilityName(
          cap.getPackageName(), cap.getName());
      capStatus.add(new CapabilityView(entry.getKey(), status.name()));
    }

    req.setAttribute(CAPABILITIES_STATUS_ATTRIBUTE, capStatus);

    try {
      getServletContext().getRequestDispatcher(
          "/_ah/adminConsole?subsection=capabilitiesstatus").forward(req, resp);
    } catch (ServletException e) {
      throw new RuntimeException("Could not forward request", e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    @SuppressWarnings("unchecked")
    Map<String, String[]> params = req.getParameterMap();
    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      Capability cap = CAPABILITIES.get(entry.getKey());
      if (cap != null) {
        localCapabilitiesEnvironment.setCapabilitiesStatus(
            LocalCapabilitiesEnvironment.geCapabilityPropertyKey(
                cap.getPackageName(), cap.getName()),
            CapabilityStatus.valueOf(entry.getValue()[0])
        );
      }
    }
    doGet(req, resp);
  }

  /**
   * View of a {@link Capability} that lets us access the name and the status using
   * jstl.
   */
  public static class CapabilityView {

    String name;
    String status;

    public CapabilityView(String name, String status) {
      this.name = name;
      this.status = status;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }


    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }


  }
}
