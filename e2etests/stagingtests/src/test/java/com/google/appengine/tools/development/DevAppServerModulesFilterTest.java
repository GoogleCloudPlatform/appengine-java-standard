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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.backends.BackendService;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.appengine.tools.development.DevAppServerModulesFilter.RequestType;
import com.google.appengine.tools.development.testing.FakeHttpServletRequest;
import com.google.appengine.tools.development.testing.FakeHttpServletResponse;
import com.google.apphosting.api.ApiProxy;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the {@link DevAppServerModulesFilter} */
public class DevAppServerModulesFilterTest extends TestCase {
  private static final String MODULE1 = "module1";

  @Mock ModulesFilterHelper helper;
  @Mock BackendServers backends;
  @Mock ModulesService modulesService;

  private DevAppServerModulesFilter filter;
  private FakeHttpServletResponse response;
  private AlwaysOkFilterChain alwaysOkFilterChain;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    MockEnvironment environment = new MockEnvironment("testapp", "1");
    environment.getAttributes().put(DevAppServerImpl.MODULES_FILTER_HELPER_PROPERTY, helper);
    ApiProxy.setEnvironmentForCurrentThread(environment);
    filter = new DevAppServerModulesFilter(backends, modulesService);
    response = new FakeHttpServletResponse();
    alwaysOkFilterChain = new AlwaysOkFilterChain();
  }

  public void testDoFilter_directModule() throws Exception {
    // from doDirectServerRequest
    when(modulesService.getCurrentModule()).thenReturn(MODULE1);
    when(modulesService.getCurrentInstanceId()).thenReturn("-1");
    when(helper.checkInstanceExists(MODULE1, -1)).thenReturn(true);
    when(helper.checkInstanceStopped(MODULE1, -1)).thenReturn(false);
    when(helper.acquireServingPermit(MODULE1, -1, /* allowQueueOnBackends= */ true))
        .thenReturn(true);
    filter.doFilter(new FakeHttpServletRequest("http://localhost/something"), response,
        alwaysOkFilterChain);
    verify(modulesService, atLeast(2)).getCurrentModule();
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertEquals(LocalEnvironment.MAIN_INSTANCE, alwaysOkFilterChain.getInstance());
  }

  public void testDoFilter_startup() throws Exception {
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(1);
    when(helper.expectsGeneratedStartRequests(MODULE1, 1)).thenReturn(true);
    filter.doFilter(new FakeHttpServletRequest("http://localhost:8810/_ah/start"), response,
        alwaysOkFilterChain);
    verify(backends, atLeast(2)).getServerNameFromPort(8810);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertEquals(1, alwaysOkFilterChain.getInstance());
  }

  public void testDoFilter_forward() throws Exception {
    // from getRequestType
    FakeHttpServletRequest request = new FakeHttpServletRequest("http://localhost:881/something");
    when(backends.getServerNameFromPort(881)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(881)).thenReturn(-1);
    // from doRedirect
    when(backends.getServerNameFromPort(881)).thenReturn(MODULE1);
    when(helper.checkModuleExists(MODULE1)).thenReturn(true);
    when(helper.checkModuleStopped(MODULE1)).thenReturn(false);
    when(helper.getAndReserveFreeInstance(MODULE1)).thenReturn(2);
    filter.doFilter(request, response, alwaysOkFilterChain);
    //
    verify(helper).forwardToInstance(MODULE1, 2, request, response);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  public void testDoFilter_redirectedBackend() throws Exception {
    // from doRedirectedBackendRequest
    final Integer port = 8899;
    final Integer instance = 9;
    when (helper.getPort(MODULE1, instance)).thenReturn(port);
    FakeHttpServletRequest request =
        new FakeHttpServletRequest("http://localhost:8810/something");
    request.setAttribute(DevAppServerModulesFilter.BACKEND_REDIRECT_ATTRIBUTE, MODULE1);
    request.setAttribute(DevAppServerModulesFilter.BACKEND_INSTANCE_REDIRECT_ATTRIBUTE, instance);
    filter.doFilter(request, response, alwaysOkFilterChain);
    verify(helper).getPort(MODULE1, instance);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertEquals(port, LocalEnvironment.getCurrentPort());
  }

  public void testDoFilter_redirectedModule() throws Exception {
    // from doRedirectedModuleRequest
    final Integer port = 8899;
    final Integer instance = 9;
    when (helper.getPort(MODULE1, instance)).thenReturn(port);
    when(modulesService.getCurrentModule()).thenReturn(MODULE1);
    FakeHttpServletRequest request =
        new FakeHttpServletRequest("http://localhost:8810/something");
    request.setAttribute(DevAppServerModulesFilter.MODULE_INSTANCE_REDIRECT_ATTRIBUTE, instance);
    filter.doFilter(request, response, alwaysOkFilterChain);
    verify(helper).getPort(MODULE1, instance);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertEquals(port, LocalEnvironment.getCurrentPort());
  }

  public void testDoFilter_directBackend() throws Exception {
    // from getRequestType
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(2);

    // from doDirectModuleRequest
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(2);
    when(helper.checkInstanceExists(MODULE1, 2)).thenReturn(true);
    when(helper.checkInstanceStopped(MODULE1, 2)).thenReturn(false);
    when(helper.acquireServingPermit(MODULE1, 2, /* allowQueueOnBackends= */ true))
        .thenReturn(true);

    filter.doFilter(new FakeHttpServletRequest("http://localhost:8810/something"), response,
        alwaysOkFilterChain);
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertEquals(2, alwaysOkFilterChain.getInstance());
  }

  public void testDoFilter_directBusy() throws Exception {
    // from getRequestType
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(2);

    // from doDirectModuleRequest
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(2);
    when(helper.checkInstanceExists(MODULE1, 2)).thenReturn(true);
    when(helper.checkInstanceStopped(MODULE1, 2)).thenReturn(false);
    when(helper.acquireServingPermit(MODULE1, 2, /* allowQueueOnBackends= */ true))
        .thenReturn(false);
    filter.doFilter(new FakeHttpServletRequest("http://localhost:8810/something"), response,
        alwaysOkFilterChain);
    assertThat(response.getStatus()).isEqualTo(DevAppServerModulesFilter.INSTANCE_BUSY_ERROR_CODE);
    assertEquals(AlwaysOkFilterChain.INITIAL_INSTANCE, alwaysOkFilterChain.getInstance());
  }

  public void testDoFilter_redirectStopped() throws Exception {
    // from getRequestType
    when(backends.getServerNameFromPort(881)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(881)).thenReturn(-1);
    // from doModuleRedirect
    when(backends.getServerNameFromPort(881)).thenReturn(MODULE1);
    when(helper.checkModuleExists(MODULE1)).thenReturn(true);
    when(helper.checkModuleStopped(MODULE1)).thenReturn(true);
    filter.doFilter(new FakeHttpServletRequest("http://localhost:881/something"), response, null);
    assertThat(response.getStatus()).isEqualTo(DevAppServerModulesFilter.MODULE_STOPPED_ERROR_CODE);
    assertEquals(AlwaysOkFilterChain.INITIAL_INSTANCE, alwaysOkFilterChain.getInstance());
  }

  public void testDoFilter_directStopped() throws Exception {
    // from getRequestType
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(2);

    // from doDirectModuleRequest
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(2);
    when(helper.checkInstanceExists(MODULE1, 2)).thenReturn(true);
    when(helper.checkInstanceStopped(MODULE1, 2)).thenReturn(true);
     filter.doFilter(
        new FakeHttpServletRequest("http://localhost:8810/something"), response, null);
    assertThat(response.getStatus()).isEqualTo(DevAppServerModulesFilter.MODULE_STOPPED_ERROR_CODE);
    assertEquals(AlwaysOkFilterChain.INITIAL_INSTANCE, alwaysOkFilterChain.getInstance());
  }

  public void testDoFilter_missing() throws Exception {
    Map<String, String> headers = new HashMap<String, String>();
    headers.put(BackendService.REQUEST_HEADER_BACKEND_REDIRECT, "missing");
    filter.doFilter(new MoreFakeHttpServletRequest("http://localhost/something", headers),
        response, alwaysOkFilterChain);
    assertThat(response.getStatus()).isEqualTo(DevAppServerModulesFilter.MODULE_MISSING_ERROR_CODE);
    assertEquals(AlwaysOkFilterChain.INITIAL_INSTANCE, alwaysOkFilterChain.getInstance());
  }

  public void testGetRequestType_directModule() throws Exception {
    when(modulesService.getCurrentModule()).thenReturn(MODULE1);
    when(modulesService.getCurrentInstanceId()).thenReturn("1");
    assertEquals(RequestType.DIRECT_MODULE_REQUEST,
        filter.getRequestType(new FakeHttpServletRequest("http://localhost/something")));
  }

  public void testGetRequestType_startUp() throws Exception {
    when(backends.getServerNameFromPort(881)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(881)).thenReturn(1);
    when(helper.expectsGeneratedStartRequests(MODULE1, 1)).thenReturn(true);
    assertEquals(RequestType.STARTUP_REQUEST,
        filter.getRequestType(new FakeHttpServletRequest("http://localhost:881/_ah/start")));
  }

  public void testGetRequestType_redirectLoadMainModule() throws Exception {
    when(modulesService.getCurrentModule()).thenReturn("default");
    when(modulesService.getCurrentInstanceId()).thenReturn("-1");
    when(helper.isLoadBalancingInstance("default", -1)).thenReturn(true);
    assertEquals(RequestType.REDIRECT_REQUESTED,
        filter.getRequestType(new FakeHttpServletRequest("http://localhost:881/something")));
  }

  public void testGetRequestType_redirectLoadMainBackend() throws Exception {
    // from getRequestType
    when(backends.getServerNameFromPort(881)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(881)).thenReturn(-1);
    assertEquals(RequestType.REDIRECT_REQUESTED,
        filter.getRequestType(new FakeHttpServletRequest("http://localhost:881/something")));
  }

  public void testGetRequestType_redirectedBackendHeader() throws Exception {
    FakeHttpServletRequest request = new FakeHttpServletRequest("http://localhost/something");
    request.setAttribute(DevAppServerModulesFilter.BACKEND_REDIRECT_ATTRIBUTE, MODULE1);
    assertEquals(RequestType.REDIRECTED_BACKEND_REQUEST, filter.getRequestType(request));
  }

  public void testGetRequestType_redirectBackendHeader() throws Exception {
    Map<String, String> redirectHeader = new HashMap<String, String>();
    redirectHeader.put(BackendService.REQUEST_HEADER_BACKEND_REDIRECT, MODULE1);
    assertEquals(RequestType.REDIRECT_REQUESTED, filter.getRequestType(
        new MoreFakeHttpServletRequest("http://localhost/something", redirectHeader)));
  }

  public void testGetRequestType_redirectBackendInstanceHeader() throws Exception {
    Map<String, String> redirectHeader = new HashMap<>();
    redirectHeader.put(BackendService.REQUEST_HEADER_BACKEND_REDIRECT, MODULE1);
    redirectHeader.put(BackendService.REQUEST_HEADER_INSTANCE_REDIRECT, "" + 0);
    assertEquals(RequestType.REDIRECT_REQUESTED, filter.getRequestType(
        new MoreFakeHttpServletRequest("http://localhost/something", redirectHeader)));
  }

  public void testGetRequestType_redirectBackendInstanceQueryParam() throws Exception {
    assertEquals(RequestType.REDIRECT_REQUESTED,
        filter.getRequestType(
            new FakeHttpServletRequest(
                "http://localhost/something?" + BackendService.REQUEST_HEADER_BACKEND_REDIRECT
                    + "=module1")));
  }

  public void testGetRequestType_redirectedModuleInstanceHeader() throws Exception {
    FakeHttpServletRequest request = new FakeHttpServletRequest("http://localhost/something");
    request.setAttribute(DevAppServerModulesFilter.MODULE_INSTANCE_REDIRECT_ATTRIBUTE, 2);
    assertEquals(RequestType.REDIRECTED_MODULE_REQUEST, filter.getRequestType(request));
  }

  public void testGetRequestType_directBackend() throws Exception {
    when(backends.getServerNameFromPort(8810)).thenReturn(MODULE1);
    when(backends.getServerInstanceFromPort(8810)).thenReturn(3);
    assertEquals(RequestType.DIRECT_BACKEND_REQUEST,
        filter.getRequestType(new FakeHttpServletRequest("http://localhost:8810/something")));
  }

  public void testGetHeaderOrParameter() throws Exception {
    assertEquals(null, DevAppServerModulesFilter.getHeaderOrParameter(
        new FakeHttpServletRequest("http://localhost/something"),
        BackendService.REQUEST_HEADER_BACKEND_REDIRECT));

    Map<String, String> redirectHeader = new HashMap<>();
    String moduleName = MODULE1;
    redirectHeader.put(BackendService.REQUEST_HEADER_BACKEND_REDIRECT, moduleName);
    assertEquals(moduleName,
        DevAppServerModulesFilter.getHeaderOrParameter(
            new MoreFakeHttpServletRequest("http://localhost/something", redirectHeader),
            BackendService.REQUEST_HEADER_BACKEND_REDIRECT));

    assertEquals(moduleName,
        DevAppServerModulesFilter.getHeaderOrParameter(
            new FakeHttpServletRequest(
                "http://localhost/something?" + BackendService.REQUEST_HEADER_BACKEND_REDIRECT + "="
                    + moduleName), BackendService.REQUEST_HEADER_BACKEND_REDIRECT));
  }

  public void testGetInstanceIdFromRequest() throws Exception {
    assertEquals(-1, DevAppServerModulesFilter.getInstanceIdFromRequest(
        new FakeHttpServletRequest("http://localhost/something")));

    Map<String, String> redirectHeader = new HashMap<>();
    redirectHeader.put(BackendService.REQUEST_HEADER_INSTANCE_REDIRECT, "1");

    assertEquals(1, DevAppServerModulesFilter.getInstanceIdFromRequest(
        new MoreFakeHttpServletRequest("http://localhost/something", redirectHeader)));

    assertEquals(1, DevAppServerModulesFilter.getInstanceIdFromRequest(new FakeHttpServletRequest(
        "http://localhost/something?" + BackendService.REQUEST_HEADER_INSTANCE_REDIRECT + "=1")));
  }

  private class MoreFakeHttpServletRequest extends FakeHttpServletRequest {

    public MoreFakeHttpServletRequest(String url, Map<String, String> customHeaders)
        throws MalformedURLException {
      super(url);
      for (Entry<String, String> e : customHeaders.entrySet()) {
        setHeader(e.getKey(), e.getValue());
      }
    }
  }

  private class AlwaysOkFilterChain implements FilterChain {
    private static final int INITIAL_INSTANCE = -123;
    private int instance = INITIAL_INSTANCE;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
      HttpServletResponse r = (HttpServletResponse) response;
      instance = LocalEnvironment.getCurrentInstance();
      r.setStatus(HttpServletResponse.SC_OK);
    }

    int getInstance() {
      return instance;
    }
  }
}
