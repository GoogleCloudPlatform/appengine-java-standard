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

package com.google.appengine.init;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** */
public class AppEngineWebXmlInitialParseTest {

  /** Test of parse method, of class AppEngineWebXmlInitialParse. */
  @Test
  public void testParse() {
    String file = "appengine-web.xml";
    new AppEngineWebXmlInitialParse(file).handleRuntimeProperties();
    assertTrue(Boolean.getBoolean("appengine.use.EE11"));
    assertTrue(Boolean.getBoolean("appengine.use.HttpConnector"));
    assertTrue(Boolean.getBoolean("appengine.use.allheaders"));
  }
}
