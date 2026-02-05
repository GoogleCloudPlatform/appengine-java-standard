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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.appengine.tools.remoteapi.testing.StubCredential;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RemoteApiOptions}. In particular this ensures that if we add a new option we
 * remember to update the copy method.
 *
 */
@RunWith(JUnit4.class)
public class RemoteApiOptionsTest {
  
  private static class StubEnvironment implements Environment {
    @Override
    public boolean isLoggedIn() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public boolean isAdmin() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public String getVersionId() {
      throw new UnsupportedOperationException();
    }
    
    @Deprecated
    @Override
    public String getRequestNamespace() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public long getRemainingMillis() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public String getModuleId() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public String getEmail() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public String getAuthDomain() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public Map<String, Object> getAttributes() {
      throw new UnsupportedOperationException();
    }
    
    @Override
    public String getAppId() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Return a list of RemoteApiOptions objects such that for any given property, at least one of
   * the objects has a non-default value for that property.
   * This will need to be updated every time a new property is added to the class.
   */
  private static ImmutableList<RemoteApiOptions> nonDefaultOptions() {
    RemoteApiOptions optionsWithPassword = new RemoteApiOptions()
        .credentials("foo@bar.com", "mysecurepassword")
        .datastoreQueryFetchSize(17)
        .maxConcurrentRequests(23)
        .maxHttpResponseSize(1729)
        .remoteApiPath("/path/to/remote/api")
        .server("somehostname", 8080);
    RemoteApiOptions optionsWithCredentials = optionsWithPassword.copy()
        .reuseCredentials("foo@bar.com", "myserializedcredentials");
    RemoteApiOptions optionsWithOAuthCredentials = optionsWithPassword.copy()
        .oauthCredential(new StubCredential())
        .httpTransport(new MockHttpTransport());
    return ImmutableList.of(optionsWithPassword, optionsWithCredentials,
        optionsWithOAuthCredentials);
  }

  /**
   * Test that {@link #nonDefaultOptions()} does return a non-default value somewhere for every
   * property. The validity of the tests for {@link RemoteApiOptions#copy} depends on this.
   */
  @Test
  public void testNonDefaultOptions() throws Exception {
    RemoteApiOptions defaultOptions = new RemoteApiOptions();
    for (Field field : RemoteApiOptions.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        boolean allSame = true;
        for (RemoteApiOptions nonDefaultOptions : nonDefaultOptions()) {
          allSame &= sameValueOfFieldIn(field, defaultOptions, nonDefaultOptions);
        }
        assertFalse(field.getName(), allSame);
      }
    }
  }

  @Test
  public void testDefaultCopy() {
    RemoteApiOptions defaultOptions = new RemoteApiOptions();
    assertOptionsEqual(defaultOptions, defaultOptions.copy());
  }

  @Test
  public void testCopyCopiesEverything() throws Exception {
    for (RemoteApiOptions nonDefaultOptions : nonDefaultOptions()) {
      RemoteApiOptions copy = nonDefaultOptions.copy();
      assertOptionsEqual(nonDefaultOptions, copy);
    }
  }

  @Test
  public void testOAuthCredentialsSupportedOnAppEngineClient() throws Exception {
    ApiProxy.setEnvironmentForCurrentThread(new StubEnvironment());
    URL url =
        this.getClass()
            .getClassLoader()
            .getResource("com/google/appengine/tools/remoteapi/testdata/test.pkcs12");
    if (url == null) {
      url =
          this.getClass()
              .getClassLoader()
              .getResource(
                  "src/test/resources/com/google/appengine/tools/remoteapi/testdata/test.pkcs12");
    }
    if (url == null) {
      url =
          this.getClass()
              .getClassLoader()
              .getResource(
                  "third_party/java_src/appengine_standard/remoteapi/src/test/resources/com/google/appengine/tools/remoteapi/testdata/test.pkcs12");
    }
    File tempFile = File.createTempFile("test", ".pkcs12");
    tempFile.deleteOnExit();
    try (InputStream in = url.openStream()) {
      Files.copy(in, tempFile.toPath(), REPLACE_EXISTING);
    }
    RemoteApiOptions options = new RemoteApiOptions();
    options.useServiceAccountCredential("foo@example.com", tempFile.getAbsolutePath());
  }

  private static void assertOptionsEqual(RemoteApiOptions x, RemoteApiOptions y) {
    assertNotSame(x, y);
    assertEquals(null, differingOption(x, y));
    // JUnit doesn't show the non-null value if assertNull fails. Grrr.
  }

  /**
   * Return the first option that differs between the two objects, or null if they have all the
   * same options.
   */
  private static String differingOption(RemoteApiOptions x, RemoteApiOptions y) {
    for (Field field : RemoteApiOptions.class.getDeclaredFields()) {
      if (!sameValueOfFieldIn(field, x, y)) {
        return field.getName();
      }
    }
    return null;
  }

  private static boolean sameValueOfFieldIn(Field field, RemoteApiOptions x, RemoteApiOptions y) {
    try {
      field.setAccessible(true);
      return Objects.deepEquals(field.get(x), field.get(y));
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
}
