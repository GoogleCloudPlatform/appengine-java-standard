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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.compute.ComputeCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

/**
 * A mutable object containing settings for installing the remote API.
 *
 * <p>Example for connecting to a development app server:
 *
 * <pre>
 * RemoteApiOptions options = new RemoteApiOptions()
 *     .server("localhost", 8888),
 *     .useDevelopmentServerCredential();
 * </pre>
 *
 * <p>Example for connecting to a deployed app:
 *
 * <pre>
 * RemoteApiOptions options = new RemoteApiOptions()
 *     .server("myappid.appspot.com", 443),
 *     .useApplicationDefaultCredential();
 * </pre>
 *
 * <p>The options should be passed to {@link RemoteApiInstaller#install}.
 */
public class RemoteApiOptions {

  private static final ImmutableList<String> OAUTH_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/appengine.apis",
          "https://www.googleapis.com/auth/userinfo.email");

  private static final String LOCAL_USER = "test@example.com";
  private static final String LOCAL_PASSWORD = "";

  private String hostname;
  private int port;
  private String userEmail;
  private String password;
  private String credentialsToReuse;
  private String remoteApiPath = "/remote_api";
  private int maxConcurrentRequests = 5;
  private int datastoreQueryFetchSize = 500;
  private int maxHttpResponseSize = 33 * 1024 * 1024;

  // public methods that populate oauthCredential must also populate
  // httpTransport (preferably by calling getOrCreateHttpTransport()).
  private Credential oauthCredential;
  private HttpTransport httpTransport;

  public RemoteApiOptions() {}

  RemoteApiOptions(RemoteApiOptions original) {
    this.hostname = original.hostname;
    this.port = original.port;
    this.userEmail = original.userEmail;
    this.password = original.password;
    this.credentialsToReuse = original.credentialsToReuse;
    this.remoteApiPath = original.remoteApiPath;
    this.maxConcurrentRequests = original.maxConcurrentRequests;
    this.datastoreQueryFetchSize = original.datastoreQueryFetchSize;
    this.maxHttpResponseSize = original.maxHttpResponseSize;
    this.oauthCredential = original.oauthCredential;
    this.httpTransport = original.httpTransport;
  }

  /** Sets the host and port port where we will connect. */
  public RemoteApiOptions server(String newHostname, int newPort) {
    hostname = newHostname;
    port = newPort;
    return this;
  }

  /**
   * Sets a username and password to be used for logging in via the ClientLogin API. Overrides any
   * previously-provided credentials.
   *
   * @deprecated Use {@link #useApplicationDefaultCredential} or {@link useServiceAccountCredential}
   *     instead.
   */
  @Deprecated
  public RemoteApiOptions credentials(String newUserEMail, String newPassword) {
    userEmail = newUserEMail;
    password = newPassword;
    credentialsToReuse = null;
    oauthCredential = null;
    return this;
  }

  /**
   * Reuses credentials from another AppEngineClient. Credentials can only be reused from a client
   * with the same hostname and user. Overrides any previously-provided credentials.
   *
   * @param newUserEmail the email address of the user we want to log in as.
   * @param serializedCredentials a string returned by calling {@link
   *     AppEngineClient#serializeCredentials} on the previous client
   */
  public RemoteApiOptions reuseCredentials(String newUserEmail, String serializedCredentials) {
    userEmail = newUserEmail;
    password = null;
    credentialsToReuse = serializedCredentials;
    oauthCredential = null;
    return this;
  }

  /**
   * Use a Compute Engine credential for authentication. Overrides any previously-provided
   * credentials.
   *
   * @return this {@code RemoteApiOptions} instance
   * @deprecated Use {@link #useApplicationDefaultCredential}.
   */
  @Deprecated
  public RemoteApiOptions useComputeEngineCredential() {
    // Attempt to eagerly populate the OAuth credential. This simplifies the
    // subsequent process of constructing a client and means we fail fast
    // if there's a problem getting the credential.
    try {
      HttpTransport transport = getOrCreateHttpTransportForOAuth();
      // Try to connect using Google Compute Engine service account credentials.
      ComputeCredential credential =
          new ComputeCredential(transport, GsonFactory.getDefaultInstance());
      // Force token refresh to verify that we are running on Google Compute Engine.
      credential.refreshToken();
      setOAuthCredential(credential);
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to acquire Google Compute Engine credential.", e);
    }
    return this;
  }

  /**
   * Use a Google Application Default credential for authentication. Overrides any
   * previously-provided credentials.
   *
   * @return this {@code RemoteApiOptions} instance.
   * @see <a
   *     href="https://developers.google.com/identity/protocols/application-default-credentials">
   *     Application Default Credentials</a>
   */
  public RemoteApiOptions useApplicationDefaultCredential() {
    try {
      getOrCreateHttpTransportForOAuth(); // Necessary to populate the http transport.
      GoogleCredential credential = GoogleCredential.getApplicationDefault();
      credential = credential.createScoped(OAUTH_SCOPES);
      credential.refreshToken();
      setOAuthCredential(credential);
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to acquire Google Application Default credential.", e);
    }
    return this;
  }

  /**
   * Use a service account credential. Overrides any previously-provided credentials.
   *
   * @param serviceAccountId service account ID (typically an e-mail address)
   * @param p12PrivateKeyFile p12 file containing a private key to use with the service account
   * @return this {@code RemoteApiOptions} instance
   */
  public RemoteApiOptions useServiceAccountCredential(
      String serviceAccountId, String p12PrivateKeyFile) {
    // Attempt to eagerly populate the OAuth credential. This simplifies the
    // subsequent process of constructing a client and means we fail fast
    // if there's a problem getting the credential.
    try {
      Credential credential =
          getCredentialBuilder(serviceAccountId)
              .setServiceAccountPrivateKeyFromP12File(new File(p12PrivateKeyFile))
              .build();
      setOAuthCredential(credential);
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to build service account credential.", e);
    }
    return this;
  }

  /**
   * Use a service account credential. Overrides any previously-provided credentials.
   *
   * @param serviceAccountId service account ID (typically an e-mail address)
   * @param privateKey private key to use with the service account
   * @return this {@code RemoteApiOptions} instance
   */
  public RemoteApiOptions useServiceAccountCredential(
      String serviceAccountId, PrivateKey privateKey) {
    // Attempt to eagerly populate the OAuth credential. This simplifies the
    // subsequent process of constructing a client and means we fail fast
    // if there's a problem getting the credential.
    try {
      Credential credential =
          getCredentialBuilder(serviceAccountId).setServiceAccountPrivateKey(privateKey).build();
      setOAuthCredential(credential);
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to build service account credential.", e);
    }
    return this;
  }

  /**
   * Use an access token credential. Overrides any previously-provided credentials.
   *
   * @param accessToken the access token (generally from {@link GoogleCredential#getAccessToken})
   * @return this {@code RemoteApiOptions} instance
   */
  public RemoteApiOptions useAccessToken(String accessToken) {
    try {
      GoogleCredential credential = getCredentialBuilder().build().setAccessToken(accessToken);
      credential = credential.createScoped(OAUTH_SCOPES);
      credential.refreshToken();
      setOAuthCredential(credential);
    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to build access token credential.", e);
    }
    return this;
  }

  /**
   * Use credentials appropriate for talking to the Development Server. Overrides any
   * previously-provided credentials.
   *
   * @return this {@code RemoteApiOptions} instance
   */
  // NOTE: This method is a friendlier alternative to calling
  // credentials("test@example.com", "non-password") to talk to a devappserver.
  // It's especially helpful for developers using OAuth credentials in their
  // prod environment, as these developers will not know a password (or possibly
  // even the email address) for the service account they're using.
  //
  // We use cookie-based login rather than OAuth-based login because the latter
  // requires extra configuration when starting the devappserver (to force
  // OAuth requests to be treated as admin requests).
  public RemoteApiOptions useDevelopmentServerCredential() {
    credentials(LOCAL_USER, LOCAL_PASSWORD);
    return this;
  }

  private GoogleCredential.Builder getCredentialBuilder()
      throws GeneralSecurityException, IOException {
    HttpTransport transport = getOrCreateHttpTransportForOAuth();
    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    return new GoogleCredential.Builder().setTransport(transport).setJsonFactory(jsonFactory);
  }

  private GoogleCredential.Builder getCredentialBuilder(String serviceAccountId)
      throws GeneralSecurityException, IOException {
    return getCredentialBuilder()
        .setServiceAccountId(serviceAccountId)
        .setServiceAccountScopes(OAUTH_SCOPES);
  }

  // @VisibleForTesting
  // NOTE: This method only exists to allow tests to simulate
  // {@link #useApplicationDefaultCredential}.
  RemoteApiOptions useGoogleCredentialStream(InputStream stream) {
    try {
      getOrCreateHttpTransportForOAuth(); // Necessary to populate the http transport.
      GoogleCredential credential = GoogleCredential.fromStream(stream);
      credential = credential.createScoped(OAUTH_SCOPES);
      credential.refreshToken();
      setOAuthCredential(credential);
    } catch (IOException|GeneralSecurityException e) {
      throw new RuntimeException("Failed to acquire Google credential.", e);
    }
    return this;
  }

  // @VisibleForTesting
  // NOTE: This method might be convenient outside of tests, but
  // because Credential is repackaged in the SDK, it's not feasible to pass
  // one in from user code.
  RemoteApiOptions oauthCredential(Credential oauthCredential) {
    setOAuthCredential(oauthCredential);
    return this;
  }

  private void setOAuthCredential(Credential oauthCredential) {
    userEmail = null;
    password = null;
    credentialsToReuse = null;
    this.oauthCredential = oauthCredential;
  }

  // @VisibleForTesting
  RemoteApiOptions httpTransport(HttpTransport httpTransport) {
    this.httpTransport = httpTransport;
    return this;
  }

  /**
   * Sets the path used to access the remote API. If not set, the default
   * is /remote_api.
   */
  public RemoteApiOptions remoteApiPath(String newPath) {
    remoteApiPath = newPath;
    return this;
  }

  /**
   * This parameter controls the maximum number of async API requests that will be
   * in flight at once. Each concurrent request will likely be handled by a separate
   * <a href="http://cloud.google.com/appengine/docs/adminconsole/instances.html"
   * >instance</a> of your App. Having more instances increases throughput but may
   * result in errors due to exceeding quota. Defaults to 5.
   */
  public RemoteApiOptions maxConcurrentRequests(int newValue) {
    maxConcurrentRequests = newValue;
    return this;
  }

  /**
   * When executing a datastore query, this is the number of results to fetch
   * per HTTP request. Increasing this value will reduce the number of round trips
   * when running large queries, but too high a value can be wasteful when not
   * all results are needed. Defaults to 500.
   *
   * <p>(This value can be overridden by the code using the datastore API.)</p>
   */
  public RemoteApiOptions datastoreQueryFetchSize(int newValue) {
    datastoreQueryFetchSize = newValue;
    return this;
  }

  /**
   * When making a remote call, this is the maximum size of the HTTP response.
   * The default is 33M. Normally there's no reason to change this.  This
   * setting has no effect when running in an App Engine container.
   */
  public RemoteApiOptions maxHttpResponseSize(int newValue) {
    maxHttpResponseSize = newValue;
    return this;
  }

  public RemoteApiOptions copy() {
    return new RemoteApiOptions(this);
  }

  // === getters ===

  /**
   * Create an {@link HttpTransport} appropriate to this environment or return
   * the one that's already been created. This method ensures that the
   * determination of whether we're running in App Engine happens early
   * (specifically, before the Remote API has been installed) and that said
   * determination is remembered.
   */
  private HttpTransport getOrCreateHttpTransportForOAuth()
      throws IOException, GeneralSecurityException {
    if (httpTransport != null) {
      return httpTransport;
    }

    if (ApiProxy.getCurrentEnvironment() == null) {
      // Running outside of App Engine.
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } else {
      httpTransport = new UrlFetchTransport();
    }
    return httpTransport;
  }

  HttpTransport getHttpTransport() {
    return httpTransport;
  }

  public String getHostname() {
    return hostname;
  }

  public int getPort() {
    return port;
  }

  public String getUserEmail() {
    return userEmail;
  }

  public String getPassword() {
    return password;
  }

  public String getCredentialsToReuse() {
    return credentialsToReuse;
  }

  Credential getOAuthCredential() {
    return oauthCredential;
  }

  public String getRemoteApiPath() {
    return remoteApiPath;
  }

  public int getMaxConcurrentRequests() {
    return maxConcurrentRequests;
  }

  public int getDatastoreQueryFetchSize() {
    return datastoreQueryFetchSize;
  }

  public int getMaxHttpResponseSize() {
    return maxHttpResponseSize;
  }
}
