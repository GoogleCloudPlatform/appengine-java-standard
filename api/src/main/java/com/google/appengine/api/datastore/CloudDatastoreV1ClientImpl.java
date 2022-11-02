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

package com.google.appengine.api.datastore;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.compute.ComputeCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.datastore.v1.AllocateIdsRequest;
import com.google.datastore.v1.AllocateIdsResponse;
import com.google.datastore.v1.BeginTransactionRequest;
import com.google.datastore.v1.BeginTransactionResponse;
import com.google.datastore.v1.CommitRequest;
import com.google.datastore.v1.CommitResponse;
import com.google.datastore.v1.LookupRequest;
import com.google.datastore.v1.LookupResponse;
import com.google.datastore.v1.RollbackRequest;
import com.google.datastore.v1.RollbackResponse;
import com.google.datastore.v1.RunQueryRequest;
import com.google.datastore.v1.RunQueryResponse;
import com.google.datastore.v1.client.Datastore;
import com.google.datastore.v1.client.DatastoreException;
import com.google.datastore.v1.client.DatastoreFactory;
import com.google.datastore.v1.client.DatastoreOptions;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** A thread-safe {@link CloudDatastoreV1Client} that makes remote proto-over-HTTP calls. */
final class CloudDatastoreV1ClientImpl implements CloudDatastoreV1Client {

  private static final Logger logger = Logger.getLogger(CloudDatastoreV1ClientImpl.class.getName());

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private static final Map<DatastoreInstanceKey, Datastore> datastoreInstances = new HashMap<>();

  final Datastore datastore;
  private final int maxRetries;

  /**
   * Key for cached {@link Datastore} instances. This class need only include values from the {@link
   * DatastoreServiceConfig} that would affect the low-level client object (e.g. deadlines).
   */
  @AutoValue
  abstract static class DatastoreInstanceKey {
    @Nullable
    abstract Double deadline();

    static DatastoreInstanceKey create(DatastoreServiceConfig config) {
      return new AutoValue_CloudDatastoreV1ClientImpl_DatastoreInstanceKey(config.getDeadline());
    }
  }

  /* @VisibleForTesting */
  CloudDatastoreV1ClientImpl(Datastore datastore, int maxRetries) {
    this.datastore = checkNotNull(datastore);
    this.maxRetries = maxRetries;
  }

  /** Creates a {@link CloudDatastoreV1ClientImpl}. */
  static synchronized CloudDatastoreV1ClientImpl create(DatastoreServiceConfig config) {
    // Each new Datastore instance has to re-negotiate credentials (which is slow). To avoid this,
    // we share one (thread-safe) instance per value of the relevant options from
    // DatastoreServiceConfig. The constructor allows the Datastore instance to be provided (for
    // testing only).
    DatastoreInstanceKey key = DatastoreInstanceKey.create(config);
    Datastore datastore = datastoreInstances.get(key);
    if (datastore == null) {
      Preconditions.checkState(!DatastoreServiceGlobalConfig.getConfig().useApiProxy());
      String projectId =
          DatastoreApiHelper.toProjectId(
              DatastoreServiceGlobalConfig.getConfig().configuredAppId());
      DatastoreOptions options;
      try {
        options =
            createDatastoreOptions(
                projectId,
                config,
                DatastoreServiceGlobalConfig.getConfig().httpConnectTimeoutMillis());
      } catch (GeneralSecurityException | IOException e) {
        throw new RuntimeException("Could not get Cloud Datastore options from environment.", e);
      }
      datastore = DatastoreFactory.get().create(options);
      datastoreInstances.put(key, datastore);
    }
    return new CloudDatastoreV1ClientImpl(
        datastore, DatastoreServiceGlobalConfig.getConfig().maxRetries());
  }

  @Override
  public Future<BeginTransactionResponse> beginTransaction(final BeginTransactionRequest req) {
    return makeCall(
        new Callable<BeginTransactionResponse>() {
          @Override
          public BeginTransactionResponse call() throws DatastoreException {
            return datastore.beginTransaction(req);
          }
        });
  }

  @Override
  public Future<RollbackResponse> rollback(final RollbackRequest req) {
    return makeCall(
        new Callable<RollbackResponse>() {
          @Override
          public RollbackResponse call() throws DatastoreException {
            return datastore.rollback(req);
          }
        });
  }

  @Override
  public Future<RunQueryResponse> runQuery(final RunQueryRequest req) {
    return makeCall(
        new Callable<RunQueryResponse>() {
          @Override
          public RunQueryResponse call() throws DatastoreException {
            return datastore.runQuery(req);
          }
        });
  }

  @Override
  public Future<LookupResponse> lookup(final LookupRequest req) {
    return makeCall(
        new Callable<LookupResponse>() {
          @Override
          public LookupResponse call() throws DatastoreException {
            return datastore.lookup(req);
          }
        });
  }

  @Override
  public Future<AllocateIdsResponse> allocateIds(final AllocateIdsRequest req) {
    return makeCall(
        new Callable<AllocateIdsResponse>() {
          @Override
          public AllocateIdsResponse call() throws DatastoreException {
            return datastore.allocateIds(req);
          }
        });
  }

  private Future<CommitResponse> commit(final CommitRequest req) {
    return makeCall(
        new Callable<CommitResponse>() {
          @Override
          public CommitResponse call() throws DatastoreException {
            return datastore.commit(req);
          }
        });
  }

  @Override
  public Future<CommitResponse> rawCommit(byte[] bytes) {
    // TODO: Evaluate whether to keep this optimization.
    try {
      return commit(CommitRequest.parseFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A {@link Callable} that wraps another {@link Callable} in an exponential backoff retry loop.
   */
  private static class RetryingCallable<T> implements Callable<T> {
    private final Callable<T> callable;
    private final int maxRetries;

    public RetryingCallable(Callable<T> callable, int maxRetries) {
      this.callable = callable;
      this.maxRetries = maxRetries;
    }

    @Override
    public T call() throws Exception {
      int remainingTries = maxRetries + 1;
      // Use default exponential backoff settings from the API client.
      ExponentialBackOff backoff = new ExponentialBackOff();
      while (true) {
        remainingTries--;
        try {
          return callable.call();
        } catch (Exception e) {
          if (isRetryable(e) && remainingTries > 0) {
            logger.log(
                Level.FINE,
                String.format("Caught retryable exception; %d tries remaining", remainingTries),
                e);
            Thread.sleep(backoff.nextBackOffMillis());
          } else {
            throw e;
          }
        }
      }
    }

    private static boolean isRetryable(Exception e) {
      // ConnectException guarantees that the request was not received by Datastore, so it is
      // always safe to retry.
      return e instanceof DatastoreException && e.getCause() instanceof ConnectException;
    }
  }

  private <T extends Message> Future<T> makeCall(final Callable<T> oneAttempt) {
    // Note that there is some cost to capturing this stack trace and it can be disabled in
    // DatastoreServiceGlobalConfig
    final Exception stackTraceCapturer =
        DatastoreServiceGlobalConfig.getConfig().asyncStackTraceCaptureEnabled()
            ? new Exception()
            : null;
    return executor.submit(
        new Callable<T>() {
          @Override
          public T call() throws Exception {
            try {
              return new RetryingCallable<>(oneAttempt, maxRetries).call();
            } catch (DatastoreException e) {
              String message =
                  stackTraceCapturer != null
                      ? String.format(
                          "%s%nstack trace when async call was initiated: <%n%s>",
                          e.getMessage(), Throwables.getStackTraceAsString(stackTraceCapturer))
                      : String.format(
                          "%s%n(stack trace capture for async call is disabled)", e.getMessage());
              throw DatastoreApiHelper.createV1Exception(e.getCode(), message, e);
            }
          }
        });
  }

  private static DatastoreOptions createDatastoreOptions(
      String projectId, final DatastoreServiceConfig config, final int httpConnectTimeoutMillis)
      throws GeneralSecurityException, IOException {
    DatastoreOptions.Builder options = new DatastoreOptions.Builder();
    setProjectEndpoint(projectId, options);
    options.credential(getCredential());
    options.initializer(
        new HttpRequestInitializer() {
          @Override
          public void initialize(HttpRequest request) throws IOException {
            request.setConnectTimeout(httpConnectTimeoutMillis);
            if (config.getDeadline() != null) {
              request.setReadTimeout((int) (config.getDeadline() * 1000));
            }
          }
        });
    return options.build();
  }

  private static Credential getCredential() throws GeneralSecurityException, IOException {
    if (DatastoreServiceGlobalConfig.getConfig().emulatorHost() != null) {
      logger.log(Level.INFO, "Emulator host was provided. Not using credentials.");
      return null;
    }
    String serviceAccount = DatastoreServiceGlobalConfig.getConfig().serviceAccount();
    if (serviceAccount != null) {
      String privateKeyFile = DatastoreServiceGlobalConfig.getConfig().privateKeyFile();
      if (privateKeyFile != null) {
        logger.log(
            Level.INFO,
            "Service account and private key file were provided. "
                + "Using service account credential.");
        return getServiceAccountCredentialBuilder(serviceAccount)
            .setServiceAccountPrivateKeyFromP12File(new File(privateKeyFile))
            .build();
      }
      PrivateKey privateKey = DatastoreServiceGlobalConfig.getConfig().privateKey();
      if (privateKey != null) {
        logger.log(
            Level.INFO,
            "Service account and private key were provided. "
                + "Using service account credential.");
        return getServiceAccountCredentialBuilder(serviceAccount)
            .setServiceAccountPrivateKey(privateKey)
            .build();
      }
      throw new IllegalStateException(
          "Service account was provided without private key or private key file.");
    }
    if (DatastoreServiceGlobalConfig.getConfig().useComputeEngineCredential()) {
      // TODO: Remove this special case and defer to Application Default Credentials
      // See b/35156374.
      return new ComputeCredential(
          GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance());
    }
    if (DatastoreServiceGlobalConfig.getConfig().accessToken() != null) {
      GoogleCredential credential =
          getCredentialBuilder()
              .build()
              .setAccessToken(DatastoreServiceGlobalConfig.getConfig().accessToken())
              .createScoped(DatastoreOptions.SCOPES);
      credential.refreshToken();
      return credential;
    }
    return GoogleCredential.getApplicationDefault().createScoped(DatastoreOptions.SCOPES);
  }

  private static void setProjectEndpoint(String projectId, DatastoreOptions.Builder options) {
    if (DatastoreServiceGlobalConfig.getConfig().hostOverride() != null) {
      options.projectEndpoint(
          String.format(
              "%s/%s/projects/%s",
              DatastoreServiceGlobalConfig.getConfig().hostOverride(),
              DatastoreFactory.VERSION.toLowerCase(),
              projectId));
      return;
    }
    if (DatastoreServiceGlobalConfig.getConfig().emulatorHost() != null) {
      options.projectId(projectId);
      options.localHost(DatastoreServiceGlobalConfig.getConfig().emulatorHost());
      return;
    }
    options.projectId(projectId);
    return;
  }

  private static GoogleCredential.Builder getServiceAccountCredentialBuilder(String account)
      throws GeneralSecurityException, IOException {
    return getCredentialBuilder()
        .setServiceAccountId(account)
        .setServiceAccountScopes(DatastoreOptions.SCOPES);
  }

  private static GoogleCredential.Builder getCredentialBuilder()
      throws GeneralSecurityException, IOException {
    return new GoogleCredential.Builder()
        .setTransport(GoogleNetHttpTransport.newTrustedTransport())
        .setJsonFactory(GsonFactory.getDefaultInstance());
  }
}
