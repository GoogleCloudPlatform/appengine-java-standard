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

package com.google.appengine.api.backends;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.testing.MockEnvironment;
import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Testing the backends api
 *
 */
@RunWith(JUnit4.class)
public class BackendServiceImplTest {
  private static final String INSTANCE_ID_KEY = "com.google.appengine.instance.id";
  private static final String BACKEND_ID_KEY = "com.google.appengine.backend.id";

  private static final String LOCALHOST = "localhost";

  @Before
  public void setUp() throws Exception {
    MockEnvironment env = new MockEnvironment("testapp", "1");
    env.getAttributes().put(BackendServiceImpl.DEFAULT_VERSION_HOSTNAME, "testapp.appspot.com");
    ApiProxy.setEnvironmentForCurrentThread(env);
  }

  private void setDev() {
    SystemProperty.environment.set(SystemProperty.Environment.Value.Development);
  }

  private void setProd() {
    SystemProperty.environment.set(SystemProperty.Environment.Value.Production);
  }

  @Test
  public void testGetCurrentInstance() throws Exception {
    setProd();
    BackendService api = BackendServiceFactory.getBackendService();
    assertThat(api.getCurrentInstance()).isEqualTo(-1);

    setCurrentBackend("backend", 1);
    assertThat(api.getCurrentInstance()).isEqualTo(1);
  }

  @Test
  public void testGetCurrentBackend() throws Exception {
    setProd();
    BackendService api = BackendServiceFactory.getBackendService();
    assertThat(api.getCurrentBackend()).isNull();

    setCurrentBackend("backend", 3);
    assertThat(api.getCurrentBackend()).isEqualTo("backend");
  }

  @Test
  public void testGetPreferredDomain() throws Exception {
    {
      MockEnvironment env = new MockEnvironment("testapp", "1");
      env.getAttributes().put(BackendServiceImpl.DEFAULT_VERSION_HOSTNAME, "foo.bar.com");
      ApiProxy.setEnvironmentForCurrentThread(env);
      assertThat(BackendServiceImpl.getDefaultVersionHostname()).isEqualTo("foo.bar.com");
    }
    {
      MockEnvironment env = new MockEnvironment("testapp17", "22");
      env.getAttributes().put(BackendServiceImpl.DEFAULT_VERSION_HOSTNAME, "tubeify.appspot.com");
      ApiProxy.setEnvironmentForCurrentThread(env);
      assertThat(BackendServiceImpl.getDefaultVersionHostname()).isEqualTo("tubeify.appspot.com");
    }
  }

  @Test
  public void testGetBackendAddressProd() throws Exception {
    setProd();
    int port = 888;
    String srv = "srv";
    addBackend(srv, 4, port);
    BackendService api = BackendServiceFactory.getBackendService();
    assertThat(api.getBackendAddress("srv")).isEqualTo(srv + ".testapp.appspot.com");
    assertThat(api.getBackendAddress("srv", 0)).isEqualTo("0." + srv + ".testapp.appspot.com");
  }

  @Test
  public void testGetBackendAddressDev() throws Exception {
    setDev();
    int port = 888;
    addBackend("srv", 4, port);
    BackendService api = BackendServiceFactory.getBackendService();
    assertThat(api.getBackendAddress("srv")).isEqualTo(LOCALHOST + ":" + port);
    assertThat(api.getBackendAddress("srv", 0)).isEqualTo(LOCALHOST + ":" + (port + 1));
  }

  private void setCurrentBackend(String backend, int instance) {
    Environment env = ApiProxy.getCurrentEnvironment();
    env.getAttributes().put(INSTANCE_ID_KEY, String.valueOf(instance));
    env.getAttributes().put(BACKEND_ID_KEY, backend);
  }

  private void addBackend(String backendname, int numInstance, int firstPort) {
    Map<String, Object> attr = ApiProxy.getCurrentEnvironment().getAttributes();
    @SuppressWarnings("unchecked")
    HashMap<String, String> backendMap =
        (HashMap<String, String>) attr.get(BackendServiceImpl.DEVAPPSERVER_PORTMAPPING_KEY);
    if (backendMap == null) {
      backendMap = new HashMap<>();
      attr.put(BackendServiceImpl.DEVAPPSERVER_PORTMAPPING_KEY, backendMap);
    }

    backendMap.put(backendname, LOCALHOST + ":" + firstPort);
    for (int i = 0; i < numInstance; i++) {
      firstPort++;
      backendMap.put(i + "." + backendname, LOCALHOST + ":" + firstPort);
    }
  }
}
