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

import com.google.apphosting.utils.config.AppEngineWebXml;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import junit.framework.TestCase;

/**
 * Unit tests for {@link EnvironmentVariableChecker}
 */
public class EnvironmentVariableCheckerTest extends TestCase {
  private static final String MISSING_KEY1 = "MissINgKey5-4";
  private static final String MISSING_VALUE1 = "value 1";
  private static final String MISSING_KEY2 = "MissINgKey5-3";
  private static final String MISSING_VALUE2 = "value two";
  private static final String MISSING_KEY3 = "MissINgKey5-2";
  private static final String MISSING_VALUE3 = "value three";
  private static final File APPENGINE_WEB_XML_FILE1 = new File("/one/WEB-INF/appengine-web.xml");
  private static final File APPENGINE_WEB_XML_FILE2 = new File("/two/WEB-INF/appengine-web.xml");

  @Override
  public void setUp() {
    assertNull(System.getenv(MISSING_KEY1));
    assertNull(System.getenv(MISSING_KEY2));
  }

  public void testMissing() {
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY1, MISSING_VALUE1);
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY2, MISSING_VALUE2);
    addMatchingEnvironmentValues(appEngineWebXml, 2);
    EnvironmentVariableChecker checker = new EnvironmentVariableChecker(
        EnvironmentVariableChecker.MismatchReportingPolicy.EXCEPTION);
    checker.add(appEngineWebXml, APPENGINE_WEB_XML_FILE1);
    try {
      checker.check();
      fail();
    } catch (EnvironmentVariableChecker.IncorrectEnvironmentVariableException ieve) {
      assertTrue(ieve.getMessage().contains(MISSING_KEY1));
      assertTrue(ieve.getMessage().contains(MISSING_KEY2));
      EnvironmentVariableChecker.Mismatch mismatch1 = EnvironmentVariableChecker.Mismatch.of(
          MISSING_KEY1, null, MISSING_VALUE1, APPENGINE_WEB_XML_FILE1);
      EnvironmentVariableChecker.Mismatch mismatch2 = EnvironmentVariableChecker.Mismatch.of(
          MISSING_KEY2, null, MISSING_VALUE2, APPENGINE_WEB_XML_FILE1);
      assertThat(ieve.getMismatches()).containsExactly(mismatch1, mismatch2);
    }
  }

  public void testMissingLog() {
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY1, MISSING_VALUE1);
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY2, MISSING_VALUE2);

    Formatter formatter = new SimpleFormatter();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Handler handler = new StreamHandler(out, formatter);
    EnvironmentVariableChecker.LOGGER.addHandler(handler);
    try {
      addMatchingEnvironmentValues(appEngineWebXml, 2);
      EnvironmentVariableChecker checker = new EnvironmentVariableChecker(
          EnvironmentVariableChecker.MismatchReportingPolicy.LOG);
      checker.add(appEngineWebXml, APPENGINE_WEB_XML_FILE1);
      checker.check();
      handler.flush();
      String expect1 = "WARNING: One or more environment variables have been configured in "
          + "appengine-web.xml that have missing or different values in your local environment.";
      assertTrue(out.toString().contains(expect1));
      assertTrue(out.toString().contains(MISSING_KEY1));
      assertTrue(out.toString().contains(MISSING_KEY2));
    } finally {
      EnvironmentVariableChecker.LOGGER.removeHandler(handler);
    }
  }

  public void testMissingIgnore() {
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY1, MISSING_VALUE1);
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY2, MISSING_VALUE2);

    Formatter formatter = new SimpleFormatter();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Handler handler = new StreamHandler(out, formatter);
    EnvironmentVariableChecker.LOGGER.addHandler(handler);
    try {
      addMatchingEnvironmentValues(appEngineWebXml, 2);
      EnvironmentVariableChecker checker = new EnvironmentVariableChecker(
          EnvironmentVariableChecker.MismatchReportingPolicy.NONE);
      checker.add(appEngineWebXml, APPENGINE_WEB_XML_FILE1);
      checker.check();
      handler.flush();
      assertTrue(out.toString().isEmpty());
    } finally {
      EnvironmentVariableChecker.LOGGER.removeHandler(handler);
    }
  }

  public void testDifferent() {
    Iterator<Map.Entry<String, String>> iterator = System.getenv().entrySet().iterator();
    if (iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      String key = entry.getKey();
      String value = entry.getValue();
      String notValue = "not" + entry.getValue();
      AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
      appEngineWebXml.addEnvironmentVariable(key, notValue);
      EnvironmentVariableChecker checker = new EnvironmentVariableChecker(
          EnvironmentVariableChecker.MismatchReportingPolicy.EXCEPTION);
      checker.add(appEngineWebXml, APPENGINE_WEB_XML_FILE1);
      try {
        checker.check();
        fail();
      } catch (EnvironmentVariableChecker.IncorrectEnvironmentVariableException ieve) {
        assertTrue(ieve.getMessage().contains(key));
        assertTrue(ieve.getMessage().contains(value));
        assertTrue(ieve.getMessage().contains(notValue));
        EnvironmentVariableChecker.Mismatch mismatch =
            EnvironmentVariableChecker.Mismatch.of(key, value, notValue, APPENGINE_WEB_XML_FILE1);
        assertThat(ieve.getMismatches()).containsExactly(mismatch);
      }
    }
  }

  public void testTwoXmlsWithMissing() {
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY1, MISSING_VALUE1);
    appEngineWebXml.addEnvironmentVariable(MISSING_KEY2, MISSING_VALUE2);
    addMatchingEnvironmentValues(appEngineWebXml, 2);
    AppEngineWebXml appEngineWebXml2 = new AppEngineWebXml();
    appEngineWebXml2.addEnvironmentVariable(MISSING_KEY3, MISSING_VALUE3);
    addMatchingEnvironmentValues(appEngineWebXml, 3);

    EnvironmentVariableChecker checker = new EnvironmentVariableChecker(
        EnvironmentVariableChecker.MismatchReportingPolicy.EXCEPTION);
    checker.add(appEngineWebXml, APPENGINE_WEB_XML_FILE1);
    checker.add(appEngineWebXml2, APPENGINE_WEB_XML_FILE2);
    try {
      checker.check();
      fail();
    } catch (EnvironmentVariableChecker.IncorrectEnvironmentVariableException ieve) {
      assertTrue(ieve.getMessage().contains(MISSING_KEY1));
      assertTrue(ieve.getMessage().contains(MISSING_KEY2));
      assertTrue(ieve.getMessage().contains(APPENGINE_WEB_XML_FILE1.getPath()));
      assertTrue(ieve.getMessage().contains(MISSING_KEY3));
      assertTrue(ieve.getMessage().contains(APPENGINE_WEB_XML_FILE2.getPath()));
      EnvironmentVariableChecker.Mismatch mismatch1 = EnvironmentVariableChecker.Mismatch.of(
          MISSING_KEY1, null, MISSING_VALUE1, APPENGINE_WEB_XML_FILE1);
      EnvironmentVariableChecker.Mismatch mismatch2 = EnvironmentVariableChecker.Mismatch.of(
          MISSING_KEY2, null, MISSING_VALUE2, APPENGINE_WEB_XML_FILE1);
      EnvironmentVariableChecker.Mismatch mismatch3 =  EnvironmentVariableChecker.Mismatch.of(
          MISSING_KEY3, null, MISSING_VALUE3, APPENGINE_WEB_XML_FILE2);
      assertThat(ieve.getMismatches()).containsExactly(mismatch1, mismatch2, mismatch3);
    }
  }

  public void testValid() {
    AppEngineWebXml appEngineWebXml = new AppEngineWebXml();
    addMatchingEnvironmentValues(appEngineWebXml, 1);
    AppEngineWebXml appEngineWebXml2 = new AppEngineWebXml();
    addMatchingEnvironmentValues(appEngineWebXml2, 3);
    Formatter formatter = new SimpleFormatter();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Handler handler = new StreamHandler(out, formatter);
    EnvironmentVariableChecker.LOGGER.addHandler(handler);
    EnvironmentVariableChecker checker = new EnvironmentVariableChecker(
        EnvironmentVariableChecker.MismatchReportingPolicy.LOG);
    try {
      checker.check();
      handler.flush();
      assertTrue(out.toString().isEmpty());
    } finally {
      EnvironmentVariableChecker.LOGGER.removeHandler(handler);
    }
  }

  private void addMatchingEnvironmentValues(AppEngineWebXml appEngineWebXml, int count) {
    Iterator<Map.Entry<String, String>> iterator = System.getenv().entrySet().iterator();
    for (int ix = 0; ix < count; ix++) {
      if (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        appEngineWebXml.addEnvironmentVariable(entry.getKey(), entry.getValue());
      }
    }
  }
}
