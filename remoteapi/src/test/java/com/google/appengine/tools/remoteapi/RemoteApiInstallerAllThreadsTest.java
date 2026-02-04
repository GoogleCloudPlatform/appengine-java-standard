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

package com.google.appengine.tools.remoteapi;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.google.apphosting.api.ApiProxy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.http.cookie.Cookie;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link RemoteApiInstaller#installOnAllThreads(RemoteApiOptions)}.
 * This method leaves behind static state in {@link ApiProxy} that can't be
 * changed, so it's run as a separate test.
 */
@RunWith(JUnit4.class)
public class RemoteApiInstallerAllThreadsTest {

  @Test
  public void testInstallOnAllThreads() throws Exception {
    // Confirm that we're starting with a clean state.
    assertNull(ApiProxy.getCurrentEnvironment());
    assertNull(ApiProxy.getDelegate());

    final RemoteApiInstaller installer = newInstaller("appId");
    installer.installOnAllThreads(createDummyOptions());

    // Inspect the ApiProxy from this thread.
    assertNotNull(ApiProxy.getDelegate());
    assertNotNull(ApiProxy.getCurrentEnvironment());

    ExecutorService executor = Executors.newSingleThreadExecutor();

    // Inspect the ApiProxy from another thread.
    executor.submit(() -> {
        assertNotNull(ApiProxy.getDelegate());
        assertNotNull(ApiProxy.getCurrentEnvironment());
    }).get();

    // Can't re-install on all threads.
    assertThrows(IllegalStateException.class, () -> installer.installOnAllThreads(createDummyOptions()));

    // Can't re-install on all threads from a separate thread.
    executor.submit(() -> {
        assertThrows(IllegalStateException.class, () -> installer.installOnAllThreads(createDummyOptions()));
    }).get();

    // Can't install for a single thread.
    assertThrows(IllegalStateException.class, () -> installer.install(createDummyOptions()));

    // Can't install for a single separate thread.
    executor.submit(() -> {
        assertThrows(IllegalStateException.class, () -> installer.install(createDummyOptions()));
    }).get();

    // Can't uninstall.
    assertThrows(IllegalArgumentException.class, () -> installer.uninstall());

    // Can't uninstall from a separate thread.
    executor.submit(() -> {
        assertThrows(IllegalArgumentException.class, () -> installer.uninstall());
    }).get();
  }

  private static RemoteApiOptions createDummyOptions() {
    return new RemoteApiOptions()
        .server("localhost", 8080)
        .credentials("this", "that");
  }

  private static RemoteApiInstaller newInstaller(final String remoteAppId) {
    return new RemoteApiInstaller() {
      @Override
      String getAppIdFromServer(List<Cookie> authCookies, RemoteApiOptions options)
          throws IOException {
        return remoteAppId;
      }
    };
  }
}
