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

package com.google.appengine.tools.development.testing;

import static com.google.appengine.tools.development.testing.LocalModulesServiceTestConfig.DEFAULT_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.appengine.api.modules.ModulesException;
import com.google.appengine.api.modules.ModulesService;
import com.google.appengine.api.modules.ModulesServiceFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link LocalModulesServiceTestConfig}.
 */
@RunWith(JUnit4.class)
public class LocalModulesServiceTestConfigTest {
  ModulesService modulesService;
  LocalModulesServiceTestConfig testConfig;
  LocalServiceTestHelper testHelper;
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() {
    testConfig = new LocalModulesServiceTestConfig();
    modulesService = ModulesServiceFactory.getModulesService();
  }

  @After
  public void tearDown() {
    testConfig = null;
    modulesService = null;
    testHelper = null;
  }

  @Test
  public void testSetup_duplicateModuleVersion() {
    String expectedMessage = "Module version module manual version v2 is already defined:"
        + " [ModuleVersion module=default version=1 scalingType=AUTOMATIC, ModuleVersion"
        + " module=manual version=v2 scalingType=MANUAL initialNumInstances=1]";
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(expectedMessage);
    testConfig.addDefaultModuleVersion()
      .addManualScalingModuleVersion("manual", "v2", 1)
      .addManualScalingModuleVersion("manual", "v2", 1);
  }

  @Test
  public void testSetup_dynamicInstanceCountForManual() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("instanceCount -1 <= 0");
    testConfig.addManualScalingModuleVersion("m", "v.1",
        LocalModulesServiceTestConfig.DYNAMIC_INSTANCE_COUNT);
  }

  @Test
  public void testSetup_dynamicInstanceCountForBasic() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("instanceCount -1 <= 0");
    testConfig.addBasicScalingModuleVersion("m", "v.1",
        LocalModulesServiceTestConfig.DYNAMIC_INSTANCE_COUNT);
  }

  @Test
  public void testSetup_missingDefaultModule() {
    String expectedMessage = "No version of the default module is configured:"
        + " moduleVersions=[ModuleVersion module=notdefault version=1 scalingType=AUTOMATIC]";
    testConfig.addAutomaticScalingModuleVersion(
        "not" + LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, DEFAULT_VERSION);
    newTestHelper();
    testHelper.setEnvModuleId("not" + LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(expectedMessage);
    testHelper.setUp();
  }

  @Test
  public void testSetup_missingModule() {
    String expectedMessage = "The LocalServiceTestHelper Environment module = 'notdefault'"
        + " version = '1' specifies a module version that has not been configured, either add the"
        + " needed module version (with one of"
        + " LocalModulesServiceTestConfig.addDefaultModuleVersion,"
        + " LocalModulesServiceTestConfig.addAutomaticScalingModuleeVersion,"
        + " LocalModulesServiceTestConfig.addBasicScalingModuleVersion,"
        + " or LocalModulesServiceTestConfig.addManualScalingModuleVersion) or correct the"
        + " Environment (with LocalServiceTestHelper.setEnvModuleId and"
        + " LocalServiceTestHelper.setEnvVersionId)";
    testConfig.addDefaultModuleVersion();
    newTestHelper();
    testHelper.setEnvModuleId("not" + LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(expectedMessage);
    testHelper.setUp();
  }

  @Test
  public void testSetup_missingVersion() {
    String expectedMessage = "The LocalServiceTestHelper Environment module = 'default' version ="
        + " 'not1' specifies a module version that has not been configured, either add the"
        + " needed module version (with one of"
        + " LocalModulesServiceTestConfig.addDefaultModuleVersion,"
        + " LocalModulesServiceTestConfig.addAutomaticScalingModuleeVersion,"
        + " LocalModulesServiceTestConfig.addBasicScalingModuleVersion,"
        + " or LocalModulesServiceTestConfig.addManualScalingModuleVersion) or correct the"
        + " Environment (with LocalServiceTestHelper.setEnvModuleId and"
        + " LocalServiceTestHelper.setEnvVersionId)";
    testConfig.addDefaultModuleVersion();
    newTestHelper();
    testHelper.setEnvVersionId("not1");
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(expectedMessage);
    testHelper.setUp();
  }

  @Test
  public void testSetup_instanceWithAutomatic() {
    String expectedMessage = "The requested module version module = 'default' version = '1' does"
        + " not support instances but the LocalServiceTestHelper environment has instances set."
        + " You can correct this issue by providing a matching manually scaling or basic scaling"
        + " module version to LocalModulesServiceTestConfig.setVersions or by calling"
        + " LocalServiceTestHelper.setEnvModuleInstance with"
        + " com.google.appengine.tools.development.LocalEnvironment.MAIN_INSTANCE (-1)"
        + " which is the default value";
    testConfig.addDefaultModuleVersion();
    newTestHelper();
    testHelper.setEnvInstance("2");
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(expectedMessage);
    testHelper.setUp();
  }

  @Test
  public void testSetup_instanceWithBasic(){
    testConfig.addDefaultModuleVersion().addBasicScalingModuleVersion("basic", "v2", 3);
    newTestHelper();
    testHelper.setEnvModuleId("basic");
    testHelper.setEnvVersionId("v2");
    testHelper.setEnvInstance("2");
    testHelper.setUp();
    assertEquals("basic", modulesService.getCurrentModule());
    assertEquals("v2", modulesService.getCurrentVersion());
    assertEquals("2", modulesService.getCurrentInstanceId());
  }

  @Test
  public void testSetup_instanceWithManual(){
    testConfig.addDefaultModuleVersion().addManualScalingModuleVersion("manual", "v2", 1);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setEnvInstance("0");
    testHelper.setUp();
    assertEquals("manual", modulesService.getCurrentModule());
    assertEquals("v2", modulesService.getCurrentVersion());
    assertEquals("0", modulesService.getCurrentInstanceId());
  }

  @Test
  public void testSetup_missingInstance(){
    String expectedMessage = "The requested module version module = 'manual' version = 'v2' has"
        + " only 1 instances and the LocalServiceTestHelper environment has envInstance set to 1"
        + " which is too big. You can correct this issue by defining more instances for the module"
        + " version with LocalModulesServiceTestConfig.setVersions or by calling"
        + " LocalServiceTestHelper.setEnvModuleInstance with a supported instance or"
        + " com.google.appengine.tools.development.LocalEnvironment.MAIN_INSTANCE (-1) which is"
        + " the default value";
    testConfig.addDefaultModuleVersion().addManualScalingModuleVersion("manual", "v2", 1);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setEnvInstance(DEFAULT_VERSION);
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(expectedMessage);
    testHelper.setUp();
  }

  @Test
  public void testSetup_manualZeroInstances(){
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("instanceCount 0 <= 0");
    testConfig.addManualScalingModuleVersion("manual", "v1", 0);
  }

  @Test
  public void testSetup_basicZeroInstances(){
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("instanceCount 0 <= 0");
    testConfig.addBasicScalingModuleVersion("basic", "v1", 0);
  }

  @Test
  public void testSetup_manualDefaultInstanceId(){
    testConfig.addDefaultModuleVersion().addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setEnvInstance(Integer.toString(LocalModulesServiceTestConfig.MAIN_INSTANCE));
    testHelper.setUp();
    assertEquals("manual", modulesService.getCurrentModule());
    assertEquals("v2", modulesService.getCurrentVersion());
    assertEquals("0", modulesService.getCurrentInstanceId());
  }


  @Test
  public void testSetup_basicDefaultInstanceId(){
    testConfig.addDefaultModuleVersion().addManualScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setEnvModuleId("basic");
    testHelper.setEnvVersionId("v2");
    testHelper.setEnvInstance(Integer.toString(LocalModulesServiceTestConfig.MAIN_INSTANCE));
    testHelper.setUp();
    assertEquals("basic", modulesService.getCurrentModule());
    assertEquals("v2", modulesService.getCurrentVersion());
    assertEquals("0", modulesService.getCurrentInstanceId());
  }

  @Test
  public void testGetModules_oneModule() {
    testConfig.addDefaultModuleVersion();
    newTestHelper();
    testHelper.setUp();
    assertThat(modulesService.getModules())
        .containsExactly(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
  }

  @Test
  public void testGetModules_twoModule() {
    testConfig.addAutomaticScalingModuleVersion("module2", "version1").addDefaultModuleVersion();
    newTestHelper();
    testHelper.setUp();
    assertThat(modulesService.getModules())
        .containsExactly(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, "module2");
  }

  @Test
  public void testClearModuleVersions() {
    testConfig.addAutomaticScalingModuleVersion("cleared", "version1").addDefaultModuleVersion();
    newTestHelper();
    testConfig.clearModuleVersions();
    testConfig.addAutomaticScalingModuleVersion("module2", "version1").addDefaultModuleVersion();
    testHelper.setUp();
    assertThat(modulesService.getModules())
        .containsExactly(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, "module2");
  }

  @Test
  public void testGetVersions_oneVersion() {
    testConfig.addDefaultModuleVersion();
    newTestHelper();
    testHelper.setUp();
    assertThat(modulesService.getVersions(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME))
        .containsExactly(DEFAULT_VERSION);
  }

  @Test
  public void testGetVersions_twoVersions() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    assertThat(modulesService.getVersions("basic")).containsExactly("v1", "v2");
  }

  @Test
  public void testGetVersions_notFound() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module");
    modulesService.getVersions("missing");
  }

  @Test
  public void testGetVersions_currentModuleDefault() {
    testConfig.addDefaultModuleVersion()
        .addAutomaticScalingModuleVersion(
            LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, "v2")
        .addAutomaticScalingModuleVersion(
            LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, "v1");
    newTestHelper();
    testHelper.setUp();
    assertThat(modulesService.getVersions(null)).containsExactly(DEFAULT_VERSION, "v1", "v2");
  }

  @Test
  public void testGetVersions_currentModuleManual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 2)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setUp();
    assertThat(modulesService.getVersions(null)).containsExactly("v1", "v2");
  }

  @Test
  public void testGetDefaultVersion_oneVersion() {
    testConfig.addDefaultModuleVersion().addBasicScalingModuleVersion("basic", "v1", 2);
    newTestHelper();
    testHelper.setUp();
    assertEquals("v1", modulesService.getDefaultVersion("basic"));
    assertEquals(DEFAULT_VERSION, modulesService.getDefaultVersion(
        LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME));
  }

  @Test
  public void testGetDefaultVersion_twoVersions() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 10)
        .addManualScalingModuleVersion("manual", "v1", 20);
    newTestHelper();
    testHelper.setUp();
    assertEquals("v2", modulesService.getDefaultVersion("manual"));
  }

  @Test
  public void testGetDefaultVersion_currentModuleDefault() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 10)
        .addManualScalingModuleVersion("manual", "v1", 20);
    newTestHelper();
    testHelper.setUp();
    assertEquals(DEFAULT_VERSION, modulesService.getDefaultVersion(null));
  }

  @Test
  public void testGetDefaultVersion_currentModuleBasic() {
    testConfig.addDefaultModuleVersion()
        .addAutomaticScalingModuleVersion("basic", "v2")
        .addAutomaticScalingModuleVersion("basic", "v1");
    newTestHelper();
    testHelper.setEnvModuleId("basic");
    testHelper.setEnvVersionId("v1");
    testHelper.setUp();
    assertEquals("v2", modulesService.getDefaultVersion(null));
  }

  @Test
  public void testGetDefaultVersion_notFound() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module");
    modulesService.getDefaultVersion("missing");
  }

  @Test
  public void testGetNumInstances_automatic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.getNumInstances(
        LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, DEFAULT_VERSION);
  }

  @Test
  public void testGetNumInstances_basic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.getNumInstances("basic", "v2");
  }

  @Test
  public void testGetNumInstances_manual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    assertEquals(2, modulesService.getNumInstances("manual", "v2"));
  }

  @Test
  public void testGetNumInstances_currentManual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 5)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setUp();
    assertEquals(5, modulesService.getNumInstances(null, null));
  }

  @Test
  public void testGetNumInstances_moduleNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.getNumInstances("notmanual", "v2");
  }

  @Test
  public void testGetNumInstances_instanceNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.getNumInstances("manual", "notv2");
  }

  @Test
  public void testSetNumInstances_automatic() {
    testConfig.addDefaultModuleVersion();
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    modulesService.setNumInstances(
        LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, DEFAULT_VERSION, 2);
  }

  @Test
  public void testSetNumInstances_basic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    modulesService.setNumInstances("basic", "v2", 8);
  }

  @Test
  public void testSetNumInstances_manual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    modulesService.setNumInstances("manual", "v2", 8);
    assertEquals(8, modulesService.getNumInstances("manual", "v2"));
  }

  @Test
  public void testSetNumInstances_zero() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("manual", "v1", 2)
        .addBasicScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    modulesService.setNumInstances("manual", "v2", 0);
  }


  @Test
  public void testSetNumInstances_negative() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("manual", "v1", 2)
        .addBasicScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    modulesService.setNumInstances("manual", "v2", -1);
  }

  @Test
  public void testSetNumInstances_currentManual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 5)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setUp();
    modulesService.setNumInstances(null, null, 4);
    assertEquals(4, modulesService.getNumInstances(null, null));
  }

  @Test
  public void testSetNumInstances_moduleNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    modulesService.setNumInstances("notmanual", "v2", 3);
  }

  @Test
  public void testSetNumInstances_instanceNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    modulesService.setNumInstances("manual", "notv2", 3);
  }

  @Test
  public void testStartVersion_automatic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.startVersion(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, DEFAULT_VERSION);
  }

  @Test
  public void testStartVersion_basic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    modulesService.startVersion("basic", "v2");
  }

  @Test
  public void testStartVersion_manual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    modulesService.startVersion("manual", "v2");
  }

  @Test
  public void testStartVersion_currentModuleManual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 5)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setUp();
    thrown.expect(NullPointerException.class);
    modulesService.startVersion(null, "v2");
  }

  @Test
  public void testStartVersion_currentVersionManual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 5)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setUp();
    thrown.expect(NullPointerException.class);
    modulesService.startVersion("manual", null);
  }

  @Test
  public void testStartVersion_moduleNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.startVersion("notmanual", "v2");
  }

  @Test
  public void testStartVersion_instanceNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.startVersion("manual", "notv2");
  }

  @Test
  public void testStopVersion_automatic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.stopVersion(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, DEFAULT_VERSION);
  }

  @Test
  public void testStopVersion_basic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    modulesService.stopVersion("basic", "v2");
  }

  @Test
  public void testStopVersion_manual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    modulesService.stopVersion("manual", "v2");
  }

  @Test
  public void testStopVersion_currentManual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 5)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setUp();
    modulesService.stopVersion(null, null);
  }

  @Test
  public void testStopVersion_moduleNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.stopVersion("notmanual", "v2");
  }

  @Test
  public void testStopVersion_instanceNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.stopVersion("manual", "notv2");
  }

  @Test
  public void testGetVersionHostname_automatic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    assertEquals("1.default.test.localhost", modulesService.getVersionHostname(
        LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, DEFAULT_VERSION));
  }

  @Test
  public void testGetVersionHostname_basic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    assertEquals("v1.basic.test.localhost", modulesService.getVersionHostname("basic", "v1"));
  }

  @Test
  public void testGetVersionHostname_manual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    assertEquals("v1.manual.test.localhost", modulesService.getVersionHostname("manual", "v1"));
  }

  @Test
  public void testGetVersionHostname_current() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 5)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2");
    testHelper.setUp();
    assertEquals("v2.manual.test.localhost", modulesService.getVersionHostname(null, null));
  }

  @Test
  public void testGetVersionHostname_moduleNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module");
    modulesService.getVersionHostname("notmanual", "v2");
  }

  @Test
  public void testGetVersionHostname_versionNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    assertEquals("notv2.manual.test.localhost",
        modulesService.getVersionHostname("manual", "notv2"));
  }

  @Test
  public void testGetInstanceHostname_automatic() {
    testConfig.addDefaultModuleVersion();
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.getInstanceHostname(
        LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, DEFAULT_VERSION, "1");
  }

  @Test
  public void testGetInstanceHostname_basic() {
    testConfig.addDefaultModuleVersion()
        .addBasicScalingModuleVersion("basic", "v1", 2)
        .addBasicScalingModuleVersion("basic", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    assertEquals("0.v1.basic.test.localhost",
        modulesService.getInstanceHostname("basic", "v1", "0"));
  }

  @Test
  public void testGetInstanceHostname_manual() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    assertEquals("1.v1.manual.test.localhost",
        modulesService.getInstanceHostname("manual", "v1", "1"));
  }

  @Test
  public void testGetInstanceHostname_current() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v2", 5)
        .addManualScalingModuleVersion("manual", "v1", 3);
    newTestHelper();
    testHelper.setEnvModuleId("manual");
    testHelper.setEnvVersionId("v2.123");
    testHelper.setEnvInstance("4");
    testHelper.setUp();
    assertEquals("4.v2.manual.test.localhost",
        modulesService.getInstanceHostname(null, null, "4"));
  }

  @Test
  public void testGetInstanceHostname_moduleNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module");
    modulesService.getInstanceHostname("notmanual", "v2", "0");
  }

  @Test
  public void testGetInstanceHostname_versionNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Unknown module version");
    modulesService.getInstanceHostname("manual", "notv2", "0");
  }

  @Test
  public void testGetVersionHostname_instanceNotFound() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(ModulesException.class);
    thrown.expectMessage("Invalid instance");
    modulesService.getInstanceHostname("manual", "v2", "2");
  }

  @Test
  public void testGetVersionHostname_instanceInvalid() {
    testConfig.addDefaultModuleVersion()
        .addManualScalingModuleVersion("manual", "v1", 2)
        .addManualScalingModuleVersion("manual", "v2", 2);
    newTestHelper();
    testHelper.setUp();
    thrown.expect(NumberFormatException.class);
    modulesService.getInstanceHostname("manual", "v2", "abc");
  }

  @Test
  public void testGenerate_default() {
    newTestHelper();
    testHelper.setUp();
    ModulesService modulesService = ModulesServiceFactory.getModulesService();
    assertThat(modulesService.getModules())
        .containsExactly(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
    assertEquals(
        LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, modulesService.getCurrentModule());
    assertEquals(LocalModulesServiceTestConfig.DEFAULT_VERSION, modulesService.getCurrentVersion());
    try {
      modulesService.getCurrentInstanceId();
      fail();
    } catch (ModulesException me) {
      assertThat(me).hasMessageThat().isEqualTo("Instance id unavailable");
    }
  }

  @Test
  public void testGenerate_defaultAutoScalingVersion() {
    newTestHelper();
    testHelper.setEnvVersionId("v9.9");
    testHelper.setUp();
    ModulesService modulesService = ModulesServiceFactory.getModulesService();
    assertThat(modulesService.getModules())
        .containsExactly(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
    assertEquals(
        "v9", modulesService.getDefaultVersion(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME));
    assertEquals(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME,
        modulesService.getCurrentModule());
    assertEquals("v9", modulesService.getCurrentVersion());
    try {
      modulesService.getCurrentInstanceId();
      fail();
    } catch (ModulesException me) {
      assertThat(me).hasMessageThat().isEqualTo("Instance id unavailable");
    }
  }

  @Test
  public void testGenerate_nonDefaultAutoScalingVersion() {
    newTestHelper();
    testHelper.setEnvModuleId("m1");
    testHelper.setEnvVersionId("v9.9");
    testHelper.setUp();
    ModulesService modulesService = ModulesServiceFactory.getModulesService();
    assertThat(modulesService.getModules())
        .containsExactly("m1", LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
    assertEquals(
        "1", modulesService.getDefaultVersion(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME));
    assertEquals("v9", modulesService.getDefaultVersion("m1"));
    assertEquals("m1", modulesService.getCurrentModule());
    assertEquals("v9", modulesService.getCurrentVersion());
    try {
      modulesService.getCurrentInstanceId();
      fail();
    } catch (ModulesException me) {
      assertThat(me).hasMessageThat().isEqualTo("Instance id unavailable");
    }
  }

  @Test
  public void testGenerate_defaultManualScaling() {
    newTestHelper();
    testHelper.setEnvVersionId("v9.9");
    testHelper.setEnvInstance("3");
    testHelper.setUp();
    ModulesService modulesService = ModulesServiceFactory.getModulesService();
    assertThat(modulesService.getModules())
        .containsExactly(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
    assertEquals(
        "v9", modulesService.getDefaultVersion(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME));
    assertEquals(4, modulesService.getNumInstances(
        LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME, "v9"));
    assertEquals(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME,
        modulesService.getCurrentModule());
    assertEquals("v9", modulesService.getCurrentVersion());
    assertEquals("3", modulesService.getCurrentInstanceId());
  }

  @Test
  public void testGenerate_nonDefaultManualScaling() {
    newTestHelper();
    testHelper.setEnvModuleId("m1");
    testHelper.setEnvVersionId("v9.9");
    testHelper.setEnvInstance("3");
    testHelper.setUp();
    ModulesService modulesService = ModulesServiceFactory.getModulesService();
    assertThat(modulesService.getModules())
        .containsExactly("m1", LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME);
    assertEquals(
        "1", modulesService.getDefaultVersion(LocalModulesServiceTestConfig.DEFAULT_MODULE_NAME));
    assertEquals("v9", modulesService.getDefaultVersion("m1"));
    assertEquals("m1", modulesService.getCurrentModule());
    assertEquals("v9", modulesService.getCurrentVersion());
    assertEquals("3", modulesService.getCurrentInstanceId());
  }

  private void newTestHelper() {
    testHelper = new LocalServiceTestHelper(testConfig);
  }
}
