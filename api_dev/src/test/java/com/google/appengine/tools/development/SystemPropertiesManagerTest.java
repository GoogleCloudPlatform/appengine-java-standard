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

import static com.google.common.base.StandardSystemProperty.FILE_SEPARATOR;
import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.utils.config.AppEngineConfigException;
import com.google.apphosting.utils.config.AppEngineWebXml;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;

/**
 * Unit tests for {@link SystemPropertiesManager}.
 *
 */
public class SystemPropertiesManagerTest extends TestCase {

  static final String PROPERTY1_NAME = "abc123Property1";
  static final String PROPERTY2_NAME = "abc123Property2";
  static final String PROPERTY3_NAME = "abc123Property3";
  static final String PROPERTY4_NAME = "abc123Property4";
  static final String PROPERTY5_NAME = "abc123Property5";
  static final File APPENGINE_WEB_XML1_NAME = new File("/a/b/ae1.xml");
  static final File APPENGINE_WEB_XML2_NAME = new File("/a/b/ae2.xml");
  static final String APPLICATION_ID = "aPpId1";
  static final String RELEASE = "rElEaSe1";
  static final String MAJOR_VERSION = "mAjOr VeRsIoN Id 1";
  static final String APPLICATION_VERSION = MAJOR_VERSION + ".1";

  private static final ImmutableMap<String, String> TEST_PROPERTIES = ImmutableMap.of(
      PROPERTY1_NAME, "p1v",
      PROPERTY2_NAME, "p2v",
      PROPERTY3_NAME, "p3v",
      PROPERTY4_NAME, "p4v",
      PROPERTY5_NAME, "p5v");

  private Map<String, String> expect;
  private AppEngineWebXml appEngineWebXml;
  private SystemPropertiesManager manager;

  @Override
  public void setUp() {
    for (String key : TEST_PROPERTIES.keySet()) {
      if (System.getProperties().get(key) != null) {
        fail("System.getProperties() cannont contain property " + key);
      }
    }
    expect = new HashMap<String, String>();
    appEngineWebXml = new AppEngineWebXml();
    manager = new SystemPropertiesManager();
  }

  @Override
  public void tearDown() {
    for (String key : TEST_PROPERTIES.keySet()) {
      System.clearProperty(key);
    }
  }

  public void testSetProperties() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    assertExpectedSystemProperties();
  }

  public void testSetProperties_twoModules() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    AppEngineWebXml appEngineWebXml2 = new AppEngineWebXml();
    addTestProperty(PROPERTY3_NAME, appEngineWebXml2);

    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    manager.setSystemProperties(appEngineWebXml2, APPENGINE_WEB_XML2_NAME);
    assertExpectedSystemProperties();
  }

  public void testSetProperties_twoModulesSamePropertySameValue() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    AppEngineWebXml appEngineWebXml2 = new AppEngineWebXml();
    addTestProperty(PROPERTY2_NAME, appEngineWebXml2);

    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    manager.setSystemProperties(appEngineWebXml2, APPENGINE_WEB_XML2_NAME);
    assertExpectedSystemProperties();
  }

  public void testSetProperties_twoModulesSamePropertyDifferentValues() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    AppEngineWebXml appEngineWebXml2 = new AppEngineWebXml();
    appEngineWebXml2.addSystemProperty(PROPERTY2_NAME, "not" + TEST_PROPERTIES.get(PROPERTY2_NAME));

    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    try {
      manager.setSystemProperties(appEngineWebXml2, APPENGINE_WEB_XML2_NAME);
      fail();
    } catch (AppEngineConfigException aece) {
      // Only test message on non Windows systems, as the file name on Windows looks
      // like C:\\a\\b\\... where C: is not predictable. As long at the try fails, we are fine.
      if (FILE_SEPARATOR.value().equals("/")) {
        assertThat(aece)
            .hasMessageThat()
            .isEqualTo(
                "Property abc123Property2 is defined in /a/b/ae2.xml and in "
                    + "/a/b/ae1.xml with different values. Currently Java Development "
                    + "Server requires matching values.");
      }
    }
    assertExpectedSystemProperties();
  }

  public void testSetProperties_twiceAddSamePropertyWithOldValue() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    assertExpectedSystemProperties();
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML2_NAME);
    assertExpectedSystemProperties();
  }

  public void testSetProperties_twiceSamePropertyWithNewValue() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    assertExpectedSystemProperties();
    String property2NewValue = "not" + TEST_PROPERTIES.get(PROPERTY2_NAME);
    appEngineWebXml.addSystemProperty(PROPERTY2_NAME, property2NewValue);
    expect.put(PROPERTY2_NAME, property2NewValue);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    assertExpectedSystemProperties();
  }

  public void testSetProperties_twiceAddNewProperty() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    assertExpectedSystemProperties();
    addTestProperty(PROPERTY3_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML2_NAME);
    assertExpectedSystemProperties();
  }

  public void testSetProperties_twiceAddNewPropertySkipOld() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    assertExpectedSystemProperties();
    appEngineWebXml = new AppEngineWebXml();
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY3_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML2_NAME);
    assertTrue(expect.containsKey(PROPERTY2_NAME));
    assertExpectedSystemProperties();
  }

  public void testRestoreSystemProperties() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    addTestProperty(PROPERTY2_NAME, appEngineWebXml);
    AppEngineWebXml appEngineWebXml2 = new AppEngineWebXml();
    addTestProperty(PROPERTY3_NAME, appEngineWebXml2);

    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    manager.setSystemProperties(appEngineWebXml2, APPENGINE_WEB_XML2_NAME);
    assertExpectedSystemProperties();

    manager.restoreSystemProperties();
    assertFalse(System.getProperties().containsKey(PROPERTY1_NAME));
    assertFalse(System.getProperties().containsKey(PROPERTY2_NAME));
    assertFalse(System.getProperties().containsKey(PROPERTY3_NAME));
  }

  public void testRestoreSystemProperties_restoreDeletedProperty() {
    System.setProperty(PROPERTY1_NAME, TEST_PROPERTIES.get(PROPERTY1_NAME));
    manager = new SystemPropertiesManager();
    assertEquals(TEST_PROPERTIES.get(PROPERTY1_NAME), System.getProperties().get(PROPERTY1_NAME));
    System.clearProperty(PROPERTY1_NAME);
    assertNull(System.getProperties().get(PROPERTY1_NAME));
    manager.restoreSystemProperties();
    assertEquals(TEST_PROPERTIES.get(PROPERTY1_NAME), System.getProperties().get(PROPERTY1_NAME));
  }

  public void testRestoreSystemProperties_restoreChangedProperty() {
    System.setProperty(PROPERTY1_NAME, TEST_PROPERTIES.get(PROPERTY1_NAME));
    manager = new SystemPropertiesManager();
    assertEquals(TEST_PROPERTIES.get(PROPERTY1_NAME), System.getProperties().get(PROPERTY1_NAME));
    String changedValue = "not" + TEST_PROPERTIES.get(PROPERTY1_NAME);
    System.setProperty(PROPERTY1_NAME, changedValue);
    assertEquals(changedValue, System.getProperties().get(PROPERTY1_NAME));
    manager.restoreSystemProperties();
    assertEquals(TEST_PROPERTIES.get(PROPERTY1_NAME), System.getProperties().get(PROPERTY1_NAME));
  }

  public void testRestoreSystemProperties_keepExtraProperty() {
    manager = new SystemPropertiesManager();
    System.setProperty(PROPERTY1_NAME, TEST_PROPERTIES.get(PROPERTY1_NAME));
    assertEquals(TEST_PROPERTIES.get(PROPERTY1_NAME), System.getProperties().get(PROPERTY1_NAME));
    manager.restoreSystemProperties();
    assertEquals(TEST_PROPERTIES.get(PROPERTY1_NAME), System.getProperties().get(PROPERTY1_NAME));
  }

  public void testRestoreSystemProperties_setAfter() {
    addTestProperty(PROPERTY1_NAME, appEngineWebXml);
    manager.setSystemProperties(appEngineWebXml, APPENGINE_WEB_XML1_NAME);
    assertExpectedSystemProperties();
    AppEngineWebXml appEngineWebXml2 = new AppEngineWebXml();
    String testProperty1NewValue = "not" + TEST_PROPERTIES.get(PROPERTY1_NAME);
    appEngineWebXml2.addSystemProperty(PROPERTY1_NAME, testProperty1NewValue);
    try {
      manager.setSystemProperties(appEngineWebXml2, APPENGINE_WEB_XML2_NAME);
      fail();
    } catch (AppEngineConfigException aece) {
      // Only test message on non Windows systems, as the file name on Windows looks
      // like C:\\a\\b\\... where C is not predictable. As long at the try fails, we are fine.
      if (FILE_SEPARATOR.value().equals("/")) {
        assertThat(aece)
            .hasMessageThat()
            .isEqualTo(
                "Property abc123Property1 is defined in /a/b/ae2.xml and in "
                    + "/a/b/ae1.xml with different values. Currently Java Development Server "
                    + "requires matching values.");
      }
    }
    assertExpectedSystemProperties();
    manager.restoreSystemProperties();
    assertNull(System.getProperty(PROPERTY1_NAME));
    manager.setSystemProperties(appEngineWebXml2, APPENGINE_WEB_XML2_NAME);
    assertEquals(testProperty1NewValue, System.getProperties().get(PROPERTY1_NAME));
  }

  public void testSetAppengineSystemProperties() {
    manager.setAppengineSystemProperties(RELEASE, APPLICATION_ID, MAJOR_VERSION);
    assertEquals(SystemProperty.Environment.Value.Development, SystemProperty.environment.value());
    assertEquals(RELEASE, SystemProperty.version.get());
    assertEquals(APPLICATION_ID, SystemProperty.applicationId.get());
    assertEquals(APPLICATION_VERSION, SystemProperty.applicationVersion.get());
  }

  public void testSetAppengineSystemProperties_nullRelease() {
    manager.setAppengineSystemProperties(null, APPLICATION_ID, MAJOR_VERSION);
    assertEquals(SystemProperty.Environment.Value.Development, SystemProperty.environment.value());
    assertEquals("null", SystemProperty.version.get());
    assertEquals(APPLICATION_ID, SystemProperty.applicationId.get());
    assertEquals(APPLICATION_VERSION, SystemProperty.applicationVersion.get());
  }

  public void testGetOriginalSystemProperties() {
    System.getProperties().setProperty(PROPERTY1_NAME, TEST_PROPERTIES.get(PROPERTY1_NAME));
    manager = new SystemPropertiesManager();
    System.getProperties().setProperty(PROPERTY1_NAME,
        "not" + TEST_PROPERTIES.get(PROPERTY1_NAME));
    System.getProperties().setProperty(PROPERTY1_NAME, TEST_PROPERTIES.get(PROPERTY1_NAME));
    assertEquals(TEST_PROPERTIES.get(PROPERTY1_NAME),
        manager.getOriginalSystemProperties().get(PROPERTY1_NAME));
    assertNull(manager.getOriginalSystemProperties().get(PROPERTY2_NAME));
  }

  private void addTestProperty(String key, AppEngineWebXml xml) {
    xml.addSystemProperty(key, TEST_PROPERTIES.get(key));
    expect.put(key, TEST_PROPERTIES.get(key));
  }

  private void assertExpectedSystemProperties() {
    // It would be nice to include all system properties in this check but that
    // does not work because running the test can change random system
    // properties as a side effect. For example logging seems to set timezone.
    Map<String, String> got = SystemPropertiesManager.copySystemProperties();
    for (Map.Entry<String, String> entry : expect.entrySet()) {
      assertEquals(entry.getValue(), got.get(entry.getKey()));
    }
  }
}
