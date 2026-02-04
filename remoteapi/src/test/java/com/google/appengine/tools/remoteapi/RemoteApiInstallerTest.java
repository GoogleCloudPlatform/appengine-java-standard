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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.appengine.tools.remoteapi.testing.StubCredential;
import com.google.apphosting.api.ApiProxy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.http.cookie.Cookie;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Verifies some of the methods in {@link RemoteApiInstaller}.
 *
 */
@RunWith(JUnit4.class)
public class RemoteApiInstallerTest {

  @Before
  public void setUp() throws Exception {
    // Start with no environment (simulating a standalone setup). Individual tests may change this.
    ApiProxy.setEnvironmentForCurrentThread(null);
  }

  /**
   * Verifies that we can parse the YAML map returned by the RemoteApiServlet.
   * (This map contains the app id.)
   */
  @Test
  public void testParseYamlMap() throws Exception {
    Map<String, String> output = RemoteApiInstaller.parseYamlMap(
        "  {rtok:  null,  app_id: my-app-id}\n\n");

    assertEquals("null", output.get("rtok"));
    assertEquals("my-app-id", output.get("app_id"));
    assertThat(output).hasSize(2);

    // now try an HR app
    output = RemoteApiInstaller.parseYamlMap(
        "  {rtok:  null,  app_id: s~my-app-id}\n\n");

    assertEquals("null", output.get("rtok"));
    assertEquals("s~my-app-id", output.get("app_id"));
    assertThat(output).hasSize(2);

    // the fields may be quoted using double strings.
    output = RemoteApiInstaller.parseYamlMap(
        "{app_id: \"s~go-tee\", rtok: \"0\"}");

    assertEquals("s~go-tee", output.get("app_id"));
    assertEquals("0", output.get("rtok"));
    assertThat(output).hasSize(2);

    // mismatched quotes should fail to parse.
    output = RemoteApiInstaller.parseYamlMap(
        "{app_id: 's~go-tee\", rtok: \"0}");

    assertThat(output).isEmpty();

    output = RemoteApiInstaller.parseYamlMap(
        "{app_id: s~go-tee', rtok: \"0'}");

    assertThat(output).isEmpty();
  }

  /**
   * Verifies that we can parse the YAML map returned by the RemoteApiServlet
   * for an app running on internal infrastructure.
   * (This map contains the app id.)
   */
  @Test
  public void testParseYamlMapInternal() throws Exception {
    Map<String, String> output = RemoteApiInstaller.parseYamlMap(
        "  {rtok:  'null',  app_id: 'google.com:my-app-id'}\n\n");

    assertEquals("null", output.get("rtok"));
    assertEquals("google.com:my-app-id", output.get("app_id"));
    assertThat(output).hasSize(2);

    // now try an HR app
    output = RemoteApiInstaller.parseYamlMap(
        "  {rtok:  'null',  app_id: 's~google.com:my-app-id'}\n\n");

    assertEquals("null", output.get("rtok"));
    assertEquals("s~google.com:my-app-id", output.get("app_id"));
    assertThat(output).hasSize(2);
  }

  @Test
  public void testParseSerializedCredentials() throws Exception {
    String credentials =
        """

        # example credential file

        host=somehost.prom.corp.google.com
        email=foo@google.com

        cookie=SACSID=ASDFASDF
        cookie=SOMETHING=else
        """;

    List<Cookie> cookies = RemoteApiInstaller.parseSerializedCredentials("foo@google.com",
        "somehost.prom.corp.google.com", credentials);

    Cookie cookie = cookies.get(0);
    assertEquals("SACSID", cookie.getName());
    assertEquals("ASDFASDF", cookie.getValue());

    cookie = cookies.get(1);
    assertEquals("SOMETHING", cookie.getName());
    assertEquals("else", cookie.getValue());

    assertEquals(2, cookies.size());
  }

  @Test
  @SuppressWarnings("rawtypes") // ApiProxy.getDelegate() returns a raw type.
  public void testInstallationStandalone() throws Exception {
    assertNull(ApiProxy.getCurrentEnvironment());

    // On Install, it should set a ToolsEnvironment on the Thread.
    RemoteApiInstaller installer = install("yar");
    assertTrue(ToolEnvironment.class.equals(ApiProxy.getCurrentEnvironment().getClass()));
    assertThat(ApiProxy.getDelegate()).isInstanceOf(ThreadLocalDelegate.class);

    // Removes the Environment on uninstall.
    installer.uninstall();
    assertNull(ApiProxy.getCurrentEnvironment());
    assertThat(ApiProxy.getDelegate()).isInstanceOf(ThreadLocalDelegate.class);
    ThreadLocalDelegate tld = (ThreadLocalDelegate) ApiProxy.getDelegate();

    installer = install("yar");
    assertSame(tld, ApiProxy.getDelegate());
    installer.uninstall();
    assertSame(tld, ApiProxy.getDelegate());
  }

  @Test
  public void testInstallationHosted() throws Exception {
    // This is kind of cheating.  The RemoteApiInstaller checks for the presence of an Environment
    // to determine if it's running standalone or hosted.  We'll trick it by putting a
    // ToolsEnvironment in there.
    ApiProxy.setEnvironmentForCurrentThread(new ToolEnvironment("unused-appid", "unused-email"));

    RemoteApiInstaller installer = install("yar");

    // Should have left the existing Environment in place, but added an attribute to override the
    // app ids in Datastore keys.
    assertTrue(ToolEnvironment.class.equals(ApiProxy.getCurrentEnvironment().getClass()));
    assertEquals(
        "yar",
        ApiProxy.getCurrentEnvironment().getAttributes().get(
            RemoteApiInstaller.DATASTORE_APP_ID_OVERRIDE_KEY));

    // The app id override is removed after uninstalling.
    installer.uninstall();
    assertThat(ApiProxy.getCurrentEnvironment().getAttributes())
        .doesNotContainKey(RemoteApiInstaller.DATASTORE_APP_ID_OVERRIDE_KEY);
  }

  @Test
  public void testInstallationHostedWithExistingAppIdOverride() throws Exception {
    // This is kind of cheating.  The RemoteApiInstaller checks for the presence of an Environment
    // to determine if it's running standalone or hosted.  We'll trick it by putting a
    // ToolsEnvironment in there.
    ApiProxy.setEnvironmentForCurrentThread(new ToolEnvironment("unused-appid", "unused-email"));

    // Simulate there already being an override.  This is not something we really expect, but we'll
    // make sure it works.
    ApiProxy.getCurrentEnvironment().getAttributes().put(
        RemoteApiInstaller.DATASTORE_APP_ID_OVERRIDE_KEY, "somePreexistingOverride");

    RemoteApiInstaller installer = install("yar");

    // Should have left the existing Environment in place, but changed the app id override.
    assertTrue(ToolEnvironment.class.equals(ApiProxy.getCurrentEnvironment().getClass()));
    assertEquals(
        "yar",
        ApiProxy.getCurrentEnvironment().getAttributes().get(
            RemoteApiInstaller.DATASTORE_APP_ID_OVERRIDE_KEY));

    // The app id override is removed after uninstalling and should restore the old override.
    installer.uninstall();
    assertEquals(
        "somePreexistingOverride",
        ApiProxy.getCurrentEnvironment().getAttributes().get(
            RemoteApiInstaller.DATASTORE_APP_ID_OVERRIDE_KEY));
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void testInstallationOnDifferentThreads() throws Exception {
    // Install in the test thread.
    RemoteApiInstaller installer1 = install("yar");
    final ApiProxy.Delegate tld = ApiProxy.getDelegate();
    assertThat(tld).isInstanceOf(ThreadLocalDelegate.class);

    ExecutorService svc = Executors.newSingleThreadExecutor();

    // Install in an alternate thread.
    Callable<RemoteApiInstaller> callable = () -> {
        RemoteApiInstaller installer = install("yar");
        assertSame(tld, ApiProxy.getDelegate());
        assertNotNull(((ThreadLocalDelegate) ApiProxy.getDelegate()).getDelegateForThread());
        return installer;
    };
    final RemoteApiInstaller installer2 = svc.submit(callable).get();

    // Uninstall in the test thread.
    installer1.uninstall();
    assertSame(tld, ApiProxy.getDelegate());
    assertNull(((ThreadLocalDelegate) ApiProxy.getDelegate()).getDelegateForThread());

    // Uninstall in the alternate thread.
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        svc.submit(
            () -> {
              assertThat(((ThreadLocalDelegate) ApiProxy.getDelegate()).getDelegateForThread())
                  .isNotNull();
              installer2.uninstall();
              assertThat(((ThreadLocalDelegate) ApiProxy.getDelegate()).getDelegateForThread())
                  .isNull();
              return null;
            });
    assertSame(tld, ApiProxy.getDelegate());
    assertNull(((ThreadLocalDelegate) ApiProxy.getDelegate()).getDelegateForThread());
  }

  @Test
  public void testValidateOptionsNullHostname() {
    RemoteApiOptions options = new RemoteApiOptions()
        .server(null, 8080)
        .credentials("email", "password");
    assertThrows(IllegalArgumentException.class, () -> new RemoteApiInstaller().validateOptions(options));
  }

  @Test
  public void testValidateOptionsNoCredentials() {
    RemoteApiOptions options = new RemoteApiOptions()
        .server("hostname", 8080);
    assertThrows(IllegalArgumentException.class, () -> new RemoteApiInstaller().validateOptions(options));
  }

  @Test
  public void testValidateOptionsPasswordCredentials() {
    RemoteApiOptions options = new RemoteApiOptions()
        .server("hostname", 8080)
        .credentials("email", "password");
    new RemoteApiInstaller().validateOptions(options);
  }

  @Test
  public void testValidateOptionsDevAppServerCredentials() {
    RemoteApiOptions options = new RemoteApiOptions()
        .server("hostname", 8080)
        .useDevelopmentServerCredential();
    new RemoteApiInstaller().validateOptions(options);
  }

  @Test
  public void testValidateOptionsOAuthCredentials() {
    RemoteApiOptions options = new RemoteApiOptions()
        .server("hostname", 8080)
        .oauthCredential(new StubCredential());
    new RemoteApiInstaller().validateOptions(options);
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

  RemoteApiInstaller install(final String remoteAppId) {
    RemoteApiInstaller installer = newInstaller(remoteAppId);
    try {
      installer.install(createDummyOptions());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return installer;
  }
}
