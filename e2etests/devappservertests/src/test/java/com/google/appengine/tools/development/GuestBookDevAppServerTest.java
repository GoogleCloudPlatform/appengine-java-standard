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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GuestBookDevAppServerTest extends DevAppServerTestBase {

  @Parameterized.Parameters
  public static Collection EEVersion() {
    return Arrays.asList(new Object[][] {{"EE6"}, {"EE8"}, {"EE10"}});
  }

  public GuestBookDevAppServerTest(String EEVersion) {
    super(EEVersion);
  }

  @Override
  public File getAppDir() {
    return Boolean.getBoolean("appengine.use.EE10")
            ? createApp("guestbook_jakarta")
            : createApp("guestbook");
  }
    
  @Test
  public void viewServlet() throws Exception {
    executeHttpGetContains(
        "/view",
        "jetty",
        RESPONSE_200);
  }

    @Test
  public void viewMainJSP() throws Exception {
    executeHttpGetContains(
        "/",
        "guestbook",
        RESPONSE_200);
  }
}
