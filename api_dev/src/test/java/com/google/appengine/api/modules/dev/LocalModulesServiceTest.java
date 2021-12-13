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

package com.google.appengine.api.modules.dev;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.modules.ModulesException;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import com.google.appengine.api.modules.ModulesServicePb.ModulesServiceError;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.appengine.tools.development.ModulesController;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.utils.config.WebModule;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test for {@link LocalModulesService}. */
@RunWith(JUnit4.class)
public class LocalModulesServiceTest {
  private LocalServiceTestHelper helper;
  private ModulesService api;
  @Mock ModulesController modulesController;

  private static final ApiProxy.ApplicationException MODULE_NOT_FOUND_EXCEPTION =
      new ApiProxy.ApplicationException(
          ModulesServiceError.ErrorCode.INVALID_MODULE_VALUE, "Module not found");

  private static final ApiProxy.ApplicationException VERSION_NOT_FOUND_EXCEPTION =
      new ApiProxy.ApplicationException(
          ModulesServiceError.ErrorCode.INVALID_VERSION_VALUE, "Version not found");

  private static final ApiProxy.ApplicationException INSTANCES_NOT_FOUND_EXCEPTION =
      new ApiProxy.ApplicationException(
          ModulesServiceError.ErrorCode.INVALID_INSTANCES_VALUE, "Instance not found");

  public void setUp(String module, String version, String instance) {
    helper = new LocalServiceTestHelper(new LocalModulesServiceTestConfig());
    if (module != null) {
      helper.setEnvModuleId(module);
    }
    if (version != null) {
      helper.setEnvVersionId(version);
    }
    if (instance != null) {
      helper.setEnvInstance(instance);
    }
    helper.setUp();
    api = ModulesServiceFactory.getModulesService();
    LocalModulesServiceTestConfig.getLocalModulesService().setModulesController(modulesController);
  }

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    setUp(null, null, null);
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void testGetCurrentModule_default() {
    assertThat(api.getCurrentModule()).isEqualTo(WebModule.DEFAULT_MODULE_NAME);
  }

  @Test
  public void testGetCurrentModule_notDefault() {
    setUp("module", "v1", null);
    assertThat(api.getCurrentModule()).isEqualTo("module");
  }

  @Test
  public void testGetCurrentVersion_defaultModule() {
    setUp(null, "v1", null);
    assertThat(api.getCurrentVersion()).isEqualTo("v1");
  }

  @Test
  public void testGetCurrentVersion_nonDefaultModule() {
    setUp("m1", "v1", null);
    assertThat(api.getCurrentVersion()).isEqualTo("v1");
  }

  @Test
  public void testGetCurrentInstanceId() {
    setUp(null, null, "123");
    assertThat(api.getCurrentInstanceId()).isEqualTo("123");
  }

  @Test
  public void testGetCurrentInstanceId_errorIfThereIsNoInstance() {
    ModulesException e = assertThrows(ModulesException.class, () -> api.getCurrentInstanceId());
    assertThat(e).hasMessageThat().contains("Instance id unavailable");
  }

  @Test
  public void testGetModules_multipleModules() {
    List<String> moduleNames = ImmutableList.of("abe", "zed");
    when(modulesController.getModuleNames()).thenReturn(moduleNames);
    assertThat(api.getModules()).containsExactlyElementsIn(moduleNames);
  }

  @Test
  public void testGetModules_noModules() {
    List<String> moduleNames = ImmutableList.of();
    when(modulesController.getModuleNames()).thenReturn(moduleNames);
    assertThat(api.getModules()).containsExactlyElementsIn(moduleNames);
  }

  @Test
  public void testGetVersions() {
    List<String> versions = ImmutableList.of("v1#cool");
    when(modulesController.getVersions("fred")).thenReturn(versions);
    assertThat(api.getVersions("fred")).containsExactlyElementsIn(versions);
  }

  @Test
  public void testGetVersions_current() {
    setUp("bob", null, null);
    List<String> versions = ImmutableList.of("v1#cool");
    when(modulesController.getVersions("bob")).thenReturn(versions);
    assertThat(api.getVersions(null)).containsExactlyElementsIn(versions);
  }

  @Test
  public void testGetVersions_invalidModuleError() {
    when(modulesController.getVersions("fred")).thenThrow(MODULE_NOT_FOUND_EXCEPTION);
    ModulesException e = assertThrows(ModulesException.class, () -> api.getVersions("fred"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetDefaultVersion() {
    when(modulesController.getDefaultVersion("fred")).thenReturn("v222");
    assertThat(api.getDefaultVersion("fred")).isEqualTo("v222");
  }

  @Test
  public void testGetDefaultVersion_current() {
    setUp("bob", null, null);
    when(modulesController.getDefaultVersion("bob")).thenReturn("v222");
    assertThat(api.getDefaultVersion(null)).isEqualTo("v222");
  }

  @Test
  public void testGetDefaultVersion_invalidModuleError() {
    when(modulesController.getDefaultVersion("fred")).thenThrow(MODULE_NOT_FOUND_EXCEPTION);
    ModulesException e = assertThrows(ModulesException.class, () -> api.getDefaultVersion("fred"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetNumInstances() {
    when(modulesController.getNumInstances("fred", "v2")).thenReturn(3);
    assertThat(api.getNumInstances("fred", "v2")).isEqualTo(3);
  }

  @Test
  public void testGetNumInstances_current() {
    setUp("bob", "v8", null);
    when(modulesController.getNumInstances("bob", "v8")).thenReturn(3);
    assertThat(api.getNumInstances(null, null)).isEqualTo(3);
  }

  @Test
  public void testGetNumInstances_invalidModuleError() {
    when(modulesController.getNumInstances("fred", "v2")).thenThrow(MODULE_NOT_FOUND_EXCEPTION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getNumInstances("fred", "v2"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetNumInstances_invalidVersionError() {
    when(modulesController.getNumInstances("fred", "v2")).thenThrow(VERSION_NOT_FOUND_EXCEPTION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getNumInstances("fred", "v2"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testGetVersionHostname() {
    when(modulesController.getHostname("fred", "v2", LocalEnvironment.MAIN_INSTANCE))
        .thenReturn("localhost:8080");
    assertThat(api.getVersionHostname("fred", "v2")).isEqualTo("localhost:8080");
  }

  @Test
  public void testGetVersionHostname_current() {
    setUp("bob", "v8", null);
    when(modulesController.getHostname("bob", "v8", LocalEnvironment.MAIN_INSTANCE))
        .thenReturn("localhost:8080");
    assertThat(api.getVersionHostname(null, null)).isEqualTo("localhost:8080");
  }

  @Test
  public void testGetVersionHostname_invalidModuleError() {
    when(modulesController.getHostname("fred", "v2", LocalEnvironment.MAIN_INSTANCE))
        .thenThrow(MODULE_NOT_FOUND_EXCEPTION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getVersionHostname("fred", "v2"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetVersionHostname_invalidVersionError() {
    when(modulesController.getHostname("fred", "v2", 2)).thenThrow(VERSION_NOT_FOUND_EXCEPTION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getInstanceHostname("fred", "v2", "2"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testGetInstanceHost() {
    when(modulesController.getHostname("fred", "v2", 2)).thenReturn("localhost:8080");
    assertThat(api.getInstanceHostname("fred", "v2", "2")).isEqualTo("localhost:8080");
  }

  @Test
  public void testGetInstanceHost_current() {
    setUp("bob", "v8", null);
    when(modulesController.getHostname("bob", "v8", 2)).thenReturn("localhost:8080");
    assertThat(api.getInstanceHostname(null, null, "2")).isEqualTo("localhost:8080");
  }

  @Test
  public void testGetInstanceHostname_invalidModule() {
    when(modulesController.getHostname("fred", "v2", 3)).thenThrow(MODULE_NOT_FOUND_EXCEPTION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getInstanceHostname("fred", "v2", "3"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module");
  }

  @Test
  public void testGetInstanceHostname_invalidVersion() {
    when(modulesController.getHostname("fred", "v2", 3)).thenThrow(VERSION_NOT_FOUND_EXCEPTION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getInstanceHostname("fred", "v2", "3"));
    assertThat(e).hasMessageThat().isEqualTo("Unknown module version");
  }

  @Test
  public void testGetInstanceHostname_invalidInstance() {
    when(modulesController.getHostname("fred", "v2", 3)).thenThrow(INSTANCES_NOT_FOUND_EXCEPTION);
    ModulesException e =
        assertThrows(ModulesException.class, () -> api.getInstanceHostname("fred", "v2", "3"));
    assertThat(e).hasMessageThat().isEqualTo("Invalid instance");
  }

  @Test
  public void testSetNumInstances() {
    api.setNumInstances("fred", "v2", 3);
    verify(modulesController).setNumInstances("fred", "v2", 3);
  }

  @Test
  public void testStartVersion() {
    api.startVersion("fred", "v2");
    verify(modulesController).startModule("fred", "v2");
  }

  @Test
  public void testStartVersion_nullModule() {
    assertThrows(NullPointerException.class, () -> api.startVersion(null, "v2"));
  }

  @Test
  public void testStartVersion_nullVersion() {
    assertThrows(NullPointerException.class, () -> api.startVersion("fred", null));
  }

  @Test
  public void testStopVersion() {
    api.stopVersion("fred", "v2");
    verify(modulesController).stopModule("fred", "v2");
  }

  @Test
  public void testStopVersion_current() {
    setUp("bob", "v8", "8");
    api.stopVersion(null, null);
    verify(modulesController).stopModule("bob", "v8");
  }
}
