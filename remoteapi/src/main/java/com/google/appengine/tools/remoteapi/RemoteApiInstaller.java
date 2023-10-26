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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.EnvironmentFactory;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Installs and uninstalls the remote API. While the RemoteApi is installed, all App Engine calls
 * made by the same thread that performed the installation will be sent to a remote server.
 *
 * <p>Instances of this class can only be used on a single thread.
 */
// NOTE: This class must be kept in sync with
// InternalRemoteApiInstaller according to the very long comment in that class.
public class RemoteApiInstaller {
  // Matches entries in a YAML map of app properties returned by the server.
  // Currently, apps running on Googleplex return quoted values, and their IDs are more complex.
  //
  // Appspot Examples:         Googleplex Examples:
  // ~~~~~~~~~~~~~~~~~~~~      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // app_id: appengineapp      app_id: 's~google.com:googleplex_app_id'
  // rtok: 0                   rtok: '0'
  private static final Pattern PAIR_REGEXP =
      Pattern.compile("([a-z0-9_-]+): +(['\\\"]?)([:~.a-z0-9_-]+)\\2");

  /**
   * A key that can be put into {@link Environment#getAttributes()} to override the app id used by
   * the Datastore API. Note that this is copied from
   * com.google.appengine.api.datastore.DatastoreApiHelper to avoid a dependency on that class. It
   * must be kept in sync.
   */
  // TODO: Can we just depend on that class?
  static final String DATASTORE_APP_ID_OVERRIDE_KEY =
      "com.google.appengine.datastore.AppIdOverride";

  // Lazy load this because we might be running inside App Engine and this
  // class isn't on the allowlist.
  private static ConsoleHandler remoteMethodHandler;

  // TODO: Support something other than console handler for when we're
  // running in App Engine.
  private static synchronized StreamHandler getStreamHandler() {
    if (remoteMethodHandler == null) {
      remoteMethodHandler = new ConsoleHandler();
      remoteMethodHandler.setFormatter(new Formatter() {
        @Override
        public String format(LogRecord record) {
          return record.getMessage() + "\n";
        }
      });
      remoteMethodHandler.setLevel(Level.FINE);
    }
    return remoteMethodHandler;
  }

  private InstallerState installerState;
  private static boolean installedForAllThreads = false;

  /* @VisibleForTesting */
  void validateOptions(RemoteApiOptions options) {
    if (options.getHostname() == null) {
      throw new IllegalArgumentException("server not set in options");
    }
    if (options.getUserEmail() == null
        && options.getOAuthCredential() == null) {
      throw new IllegalArgumentException("credentials not set in options");
    }
  }

  private boolean installed() {
    return installerState != null || installedForAllThreads;
  }

  /**
   * Installs the remote API on all threads using the provided options. Logs
   * into the remote application using the credentials available via these
   * options.
   *
   * <p>Note that if installed using this method, the remote API cannot be
   * uninstalled.</p>
   *
   * @throws IllegalArgumentException if the server or credentials weren't provided.
   * @throws IllegalStateException if already installed
   * @throws LoginException if unable to log in.
   * @throws IOException if unable to connect to the remote API.
   */
  public void installOnAllThreads(RemoteApiOptions options) throws IOException {
    final RemoteApiOptions finalOptions = options.copy();
    validateOptions(finalOptions);

    // Synchronize on the class because we're manipulating global state (ApiProxy.Delegate).
    synchronized (getClass()) {
      if (installed()) {
        throw new IllegalStateException("remote API is already installed");
      }
      installedForAllThreads = true;

      final RemoteApiClient client = login(finalOptions);
      @SuppressWarnings("unchecked")
      Delegate<Environment> originalDelegate = ApiProxy.getDelegate();

      RemoteApiDelegate globalRemoteApiDelegate =
          createDelegate(finalOptions, client, originalDelegate);
      ApiProxy.setDelegate(globalRemoteApiDelegate);

      // Single-thread install has the ability to leave the existing environment
      // in place and simply override the app id. That functionality is not
      // supported here.
      ApiProxy.setEnvironmentFactory(new EnvironmentFactory() {
        @Override
        public Environment newEnvironment() {
          return createEnv(finalOptions, client);
        }
      });
    }
  }

  /**
   * Installs the remote API using the provided options.  Logs into the remote
   * application using the credentials available via these options.
   *
   * <p>Warning: This method only installs the remote API on the current
   * thread.  Do not share this instance across threads!</p>
   *
   * @throws IllegalArgumentException if the server or credentials weren't provided.
   * @throws IllegalStateException if already installed
   * @throws LoginException if unable to log in.
   * @throws IOException if unable to connect to the remote API.
   */
  public void install(RemoteApiOptions options) throws IOException {
    options = options.copy();
    validateOptions(options);

    // Synchronize on the class because we're manipulating global state (ApiProxy.Delegate).
    synchronized (getClass()) {
      if (installed()) {
        throw new IllegalStateException("remote API is already installed");
      }
      // Need to be careful about how we transition from not needing an uninstall
      // to needing an uninstall.  We want the installation to either completely
      // succeed or completely fail, so we'll gather up everything we need in
      // local vars, and then once we have everything we need we'll mutate our
      // state.  That way if anything goes wrong before we're not stuck in an
      // inconsistent state.
      @SuppressWarnings("unchecked")
      Delegate<Environment> originalDelegate = ApiProxy.getDelegate();
      Environment originalEnv = ApiProxy.getCurrentEnvironment();
      RemoteApiClient installedClient = login(options);
      RemoteApiDelegate remoteApiDelegate;
      if (originalDelegate instanceof ThreadLocalDelegate) {
        ThreadLocalDelegate<Environment> installedDelegate =
            (ThreadLocalDelegate<Environment>) originalDelegate;
        Delegate<Environment> globalDelegate = installedDelegate.getGlobalDelegate();
        remoteApiDelegate = createDelegate(options, installedClient, globalDelegate);
        // We've already got a ThreadLocalDelegate so just install the remote
        // api delegate for the current thread.
        if (installedDelegate.getDelegateForThread() != null) {
          throw new IllegalStateException("remote API is already installed");
        }
        installedDelegate.setDelegateForThread(remoteApiDelegate);
      } else {
        remoteApiDelegate = createDelegate(options, installedClient, originalDelegate);
        // Current delegate is not a ThreadLocalDelegate.
        ApiProxy.setDelegate(new ThreadLocalDelegate<Environment>(
            originalDelegate, remoteApiDelegate));
      }
      Environment installedEnv = null;
      String appIdOverrideToRestore = null;
      if (originalEnv == null) {
        installedEnv = createEnv(options, installedClient);
        ApiProxy.setEnvironmentForCurrentThread(installedEnv);
      } else {
        // Add a marker to the existing Environment to cause the Datastore api to use the remote app
        // id.  We'll stash the value that was there in order to restore it later.  (Although we
        // don't expect anyone other than the remote api to be using this unadvertised feature.)
        appIdOverrideToRestore =
            (String) originalEnv.getAttributes().get(DATASTORE_APP_ID_OVERRIDE_KEY);
        originalEnv.getAttributes().put(DATASTORE_APP_ID_OVERRIDE_KEY, installedClient.getAppId());
      }

      // Installation of the delegate is the point of no return
      installerState = new InstallerState(
          originalEnv,
          installedClient,
          remoteApiDelegate,
          installedEnv,
          appIdOverrideToRestore);
    }
  }

  /**
   * The state related to the installation of a {@link RemoteApiInstaller}.
   * It's just a struct, but it makes it easy for us to ensure that we don't
   * end up in an inconsistent state when installation fails part-way through.
   */
  private static class InstallerState {
    @Nullable private final Environment originalEnv;
    private final RemoteApiClient installedClient;
    private final RemoteApiDelegate remoteApiDelegate;
    @Nullable private final Environment installedEnv;
    @Nullable String appIdOverrideToRestore;

    InstallerState(
        Environment originalEnv,
        RemoteApiClient installedClient,
        RemoteApiDelegate remoteApiDelegate,
        Environment installedEnv,
        String appIdOverrideToRestore) {
      this.originalEnv = originalEnv;
      this.installedClient = installedClient;
      this.remoteApiDelegate = remoteApiDelegate;
      this.installedEnv = installedEnv;
      this.appIdOverrideToRestore = appIdOverrideToRestore;
    }
  }
  /**
   * Uninstalls the remote API. If any async calls are in progress, waits for
   * them to finish.
   *
   * <p>If the remote API isn't installed, this method has no effect.</p>
   */
  public void uninstall() {
    // Synchronize on the class because we're manipulating global state (ApiProxy.Delegate)
    synchronized (getClass()) {
      if (installedForAllThreads) {
        throw new IllegalArgumentException(
            "cannot uninstall the remote API after installing on all threads");
      }
      if (installerState == null) {
        throw new IllegalArgumentException("remote API is already uninstalled");
      }
      if (installerState.installedEnv != null
          && installerState.installedEnv != ApiProxy.getCurrentEnvironment()) {
        throw new IllegalStateException(
          "Can't uninstall because the current environment has been modified.");
      }
      ApiProxy.Delegate<?> currentDelegate = ApiProxy.getDelegate();
      if (!(currentDelegate instanceof ThreadLocalDelegate)) {
        throw new IllegalStateException(
            "Can't uninstall because the current delegate has been modified.");
      }
      // Just clear the delegate for the current thread.  We won't bother
      // uninstalling the ThreadLocalDelegate since it will just just delegate
      // to the original delegate that it wrapped once the threadlocal
      // value has been cleared.
      ThreadLocalDelegate<?> tld = (ThreadLocalDelegate<?>) currentDelegate;
      if (tld.getDelegateForThread() == null) {
        throw new IllegalArgumentException("remote API is already uninstalled");
      }
      tld.clearThreadDelegate();

      if (installerState.installedEnv != null) {
        // Note that originalEnv may be null.
        ApiProxy.setEnvironmentForCurrentThread(installerState.originalEnv);
      } else {
        // Remove any override we may have put on the existing Environment.  Restore a previous
        // value if one was present, or just remove it from the attributes altogether.
        if (installerState.appIdOverrideToRestore != null) {
          ApiProxy.getCurrentEnvironment().getAttributes().put(
              DATASTORE_APP_ID_OVERRIDE_KEY, installerState.appIdOverrideToRestore);
        } else {
          ApiProxy.getCurrentEnvironment().getAttributes().remove(DATASTORE_APP_ID_OVERRIDE_KEY);
        }
      }

      installerState.remoteApiDelegate.shutdown(); // waits for any async calls to drain
      installerState = null;
    }
  }

  /**
   * Returns a string containing the cookies associated with this
   * connection. The string can be used to create a new connection
   * without logging in again by using {@link RemoteApiOptions#reuseCredentials}.
   * By storing credentials to a file, we can avoid repeated password
   * prompts in command-line tools. (Note that the cookies will expire
   * based on the setting under Application Settings in the admin console.)
   *
   * <p>Beware: it's important to keep this string private, as it
   * allows admin access to the app as the current user.</p>
   */
  public String serializeCredentials() {
    return installerState.installedClient.serializeCredentials();
  }

  /**
   * Starts logging remote API method calls to the console. (Useful within tests.)
   */
  public void logMethodCalls() {
    Logger logger = Logger.getLogger(RemoteApiDelegate.class.getName());
    logger.setLevel(Level.FINE);
    if (!Arrays.asList(logger.getHandlers()).contains(getStreamHandler())) {
      logger.addHandler(getStreamHandler());
    }
  }

  public void resetRpcCount() {
    installerState.remoteApiDelegate.resetRpcCount();
  }

  /**
   * Returns the number of RPC calls made since the API was installed
   * or {@link #resetRpcCount} was called.
   */
  public int getRpcCount() {
    return installerState.remoteApiDelegate.getRpcCount();
  }

  // === subclass interface (to swap out the implementation for testing) ===

  RemoteApiClient login(RemoteApiOptions options) throws IOException {
    return loginImpl(options);
  }

  RemoteApiDelegate createDelegate(
      RemoteApiOptions options,
      RemoteApiClient client,
      @Nullable Delegate<Environment> originalDelegate) {
    return RemoteApiDelegate.newInstance(new RemoteRpc(client), options, originalDelegate);
  }

  Environment createEnv(RemoteApiOptions options, RemoteApiClient client) {
    return new ToolEnvironment(client.getAppId(), options.getUserEmail());
  }

  // === default implementation of logging in ===

  /**
   * Submits credentials and gets cookies for logging in to App Engine.
   * (Also downloads the appId from the remote API.)
   * @return an AppEngineClient containing credentials (if successful)
   * @throws LoginException for a login failure
   * @throws IOException for other connection failures
   */
  private RemoteApiClient loginImpl(RemoteApiOptions options) throws IOException {
    List<Cookie> authCookies;
    if (!authenticationRequiresCookies(options)) {
      authCookies = Collections.emptyList();
    } else if (options.getCredentialsToReuse() != null) {
      authCookies = parseSerializedCredentials(options.getUserEmail(), options.getHostname(),
          options.getCredentialsToReuse());
    } else if (options.getHostname().equals("localhost")) {
      authCookies = Collections.singletonList(
          makeDevAppServerCookie(options.getHostname(), options.getUserEmail()));
    } else if (ApiProxy.getCurrentEnvironment() != null) {
      authCookies = new HostedClientLogin().login(
          options.getHostname(), options.getUserEmail(), options.getPassword());
    } else {
      authCookies = new StandaloneClientLogin().login(
          options.getHostname(), options.getUserEmail(), options.getPassword());
    }

    String appId = getAppIdFromServer(authCookies, options);
    return createAppEngineClient(options, authCookies, appId);
  }

  /**
   * @return {@code true} if the authentication to support the {@link RemoteApiOptions} requires
   *         cookies, {@code false} otherwise
   */
  boolean authenticationRequiresCookies(final RemoteApiOptions options) {
    return options.getOAuthCredential() == null;
  }

  RemoteApiClient createAppEngineClient(
      RemoteApiOptions options, List<Cookie> authCookies, @Nullable String appId) {
    if (options.getOAuthCredential() != null) {
      return new OAuthClient(options, appId);
    }
    if (ApiProxy.getCurrentEnvironment() != null) {
      // We'll talk to the remote_api servlet using UrlFetch.
      return new HostedAppEngineClient(options, authCookies, appId);
    }
    // We'll talk to the remote_api servlet using HttpClient.
    return new StandaloneAppEngineClient(options, authCookies, appId);
  }

  public static Cookie makeDevAppServerCookie(String hostname, String email) {
    String cookieValue = email + ":true:" + LoginCookieUtils.encodeEmailAsUserId(email);
    BasicClientCookie cookie = new BasicClientCookie(LoginCookieUtils.COOKIE_NAME, cookieValue);
    cookie.setDomain(hostname);
    cookie.setPath("/");
    return cookie;
  }

  String getAppIdFromServer(List<Cookie> authCookies, RemoteApiOptions options)
      throws IOException {
    // Use a temporary RemoteApiClient to do an authenticated GET request.
    // (It's just like the real one except that appId is null.)
    RemoteApiClient tempClient = createAppEngineClient(options, authCookies, null);
    AppEngineClient.Response response = tempClient.get(options.getRemoteApiPath());
    int status = response.getStatusCode();
    if (status != 200) {
      if (response.getBodyAsBytes() == null) {
        throw new IOException("can't get appId from remote api; status code = " + status);
      } else {
        throw new IOException("can't get appId from remote api; status code = " + status
            + ", body: " + response.getBodyAsString());
      }
    }
    String body = response.getBodyAsString();
    Map<String, String> props = parseYamlMap(body);
    String appId = props.get("app_id");
    if (appId == null) {
      throw new IOException("unexpected response from remote api: " + body);
    }
    return appId;
  }

  /** Parses the response from the remote API as a YAML map. */
  static ImmutableMap<String, String> parseYamlMap(String input) {
    // NOTE: This method only supports a very limited subset of YAML.
    // It should handle adding another pair to the map, but not much else.

    Map<String, String> result = new LinkedHashMap<>();
    input = input.trim();
    if (!input.startsWith("{") || !input.endsWith("}")) {
      return ImmutableMap.of();
    }
    input = input.substring(1, input.length() - 1);

    String[] pairs = input.split(", +");
    for (String pair : pairs) {
      Matcher matcher = PAIR_REGEXP.matcher(pair);
      if (matcher.matches()) {
        result.put(matcher.group(1), matcher.group(3));
      }
    }
    return ImmutableMap.copyOf(result);
  }

  static List<Cookie> parseSerializedCredentials(String expectedEmail, String expectedHost,
      String serializedCredentials) throws IOException {

    Map<String, List<String>> props = parseProperties(serializedCredentials);
    checkOneProperty(props, "email");
    checkOneProperty(props, "host");
    String email = props.get("email").get(0);
    if (!expectedEmail.equals(email)) {
      throw new IOException("credentials don't match current user email");
    }
    String host = props.get("host").get(0);
    if (!expectedHost.equals(host)) {
      throw new IOException("credentials don't match current host");
    }

    List<Cookie> result = new ArrayList<Cookie>();
    for (String line : props.get("cookie")) {
      result.add(parseCookie(line, host));
    }
    return result;
  }

  private static Cookie parseCookie(String line, String host) throws IOException {
    int firstEqual = line.indexOf('=');
    if (firstEqual < 1) {
      throw new IOException("invalid cookie in credentials");
    }
    String key = line.substring(0, firstEqual);
    String value = line.substring(firstEqual + 1);
    BasicClientCookie cookie = new BasicClientCookie(key, value);
    cookie.setDomain(host);
    cookie.setPath("/");
    return cookie;
  }

  private static void checkOneProperty(Map<String, List<String>> props, String key)
      throws IOException {
    if (props.get(key).size() != 1) {
      String message = "invalid credential file (should have one property named '" + key + "')";
      throw new IOException(message);
    }
  }

  private static Map<String, List<String>> parseProperties(String serializedCredentials) {
    Map<String, List<String>> props = new LinkedHashMap<>();
    for (String line : serializedCredentials.split("\n")) {
      line = line.trim();
      if (!line.startsWith("#") && line.contains("=")) {
        int firstEqual = line.indexOf('=');
        String key = line.substring(0, firstEqual);
        String value = line.substring(firstEqual + 1);
        List<String> values = props.get(key);
        if (values == null) {
          values = new ArrayList<String>();
          props.put(key, values);
        }
        values.add(value);
      }
    }
    return props;
  }
}
