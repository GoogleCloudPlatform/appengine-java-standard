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

package com.google.apphosting.runtime.http;

import com.google.apphosting.base.protos.RuntimePb.APIRequest;
import com.google.apphosting.base.protos.RuntimePb.APIResponse;
import com.google.apphosting.base.protos.RuntimePb.APIResponse.ERROR;
import com.google.apphosting.base.protos.RuntimePb.APIResponse.RpcError;
import com.google.apphosting.base.protos.Status.StatusProto;
import com.google.apphosting.base.protos.api_bytes.RemoteApiPb;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.apphosting.runtime.anyrpc.AnyRpcCallback;
import com.google.apphosting.runtime.anyrpc.AnyRpcClientContext;
import com.google.apphosting.utils.runtime.ApiProxyUtils;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.UninitializedMessageException;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A client of the APIHost service over HTTP.
 *
 */
abstract class HttpApiHostClient implements APIHostClientInterface {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * Extra timeout that will be used for the HTTP request. If the API timeout is 5 seconds, the
   * HTTP request will have a timeout of 5 + {@value #DEFAULT_EXTRA_TIMEOUT_SECONDS} seconds.
   * Usually another timeout will happen first, either the API timeout on the server or the
   * TimedFuture timeout on the client, but this one enables us to clean up the HttpClient if the
   * server is unresponsive.
   */
  static final double DEFAULT_EXTRA_TIMEOUT_SECONDS = 2.0;

  static final ImmutableMap<String, String> HEADERS = ImmutableMap.of(
      "X-Google-RPC-Service-Endpoint", "app-engine-apis",
      "X-Google-RPC-Service-Method", "/VMRemoteAPI.CallRemoteAPI");
  static final String CONTENT_TYPE_VALUE = "application/octet-stream";
  static final String REQUEST_ENDPOINT = "/rpc_http";
  static final String DEADLINE_HEADER = "X-Google-RPC-Service-Deadline";

  private static final int UNKNOWN_ERROR_CODE = 1;

  // TODO: study the different limits that we have for different transports and
  // make them more consistent, as well as sharing definitions like this one.
  /** The maximum size in bytes that we will allow in a request or a response payload. */
  static final int MAX_PAYLOAD = 50 * 1024 * 1024;
  /**
   * Extra bytes that we allow in the HTTP content, basically to support serializing the other
   * proto fields besides the payload.
   */
  static final int EXTRA_CONTENT_BYTES = 4096;

  @AutoValue
  abstract static class Config {
    abstract double extraTimeoutSeconds();
    abstract OptionalInt maxConnectionsPerDestination();

    /** For testing that we handle missing Content-Length correctly. */
    abstract boolean ignoreContentLength();

    /**
     * Treat {@link java.nio.channels.ClosedChannelException} as indicating cancellation. We know
     * that this happens occasionally in a test that generates many interrupts. But we don't know if
     * there are other reasons for which it might arise, so for now we do not do this in production.
     *
     * <p>See <a href="http://b/70494739#comment31">this bug</a> for further background.
     */
    abstract boolean treatClosedChannelAsCancellation();

    static Builder builder() {
      return new AutoValue_HttpApiHostClient_Config.Builder()
          .setExtraTimeoutSeconds(DEFAULT_EXTRA_TIMEOUT_SECONDS)
          .setIgnoreContentLength(false)
          .setTreatClosedChannelAsCancellation(false);
    }

    abstract Builder toBuilder();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setMaxConnectionsPerDestination(OptionalInt value);
      abstract Builder setExtraTimeoutSeconds(double value);
      abstract Builder setIgnoreContentLength(boolean value);
      abstract Builder setTreatClosedChannelAsCancellation(boolean value);
      abstract Config build();
    }
  }

  private final Config config;

  HttpApiHostClient(Config config) {
    this.config = config;
  }

  Config config() {
    return config;
  }

  static HttpApiHostClient create(String url, Config config) {
    if (System.getenv("APPENGINE_API_CALLS_USING_JDK_CLIENT") != null) {
      logger.atInfo().log("Using JDK HTTP client for API calls");
      return JdkHttpApiHostClient.create(url, config);
    } else {
      return JettyHttpApiHostClient.create(url, config);
    }
  }

  static class Context implements AnyRpcClientContext {
    private final long startTimeMillis;

    private int applicationError;
    private String errorDetail;
    private StatusProto status;
    private Throwable exception;
    private Optional<Long> deadlineNanos = Optional.empty();

    Context() {
      this.startTimeMillis = System.currentTimeMillis();
    }

    @Override
    public int getApplicationError() {
      return applicationError;
    }

    void setApplicationError(int applicationError) {
      this.applicationError = applicationError;
    }

    @Override
    public String getErrorDetail() {
      return errorDetail;
    }

    void setErrorDetail(String errorDetail) {
      this.errorDetail = errorDetail;
    }

    @Override
    public Throwable getException() {
      return exception;
    }

    void setException(Throwable exception) {
      this.exception = exception;
    }

    @Override
    public long getStartTimeMillis() {
      return startTimeMillis;
    }

    @Override
    public StatusProto getStatus() {
      return status;
    }

    void setStatus(StatusProto status) {
      this.status = status;
    }

    @Override
    public void setDeadline(double seconds) {
      Preconditions.checkArgument(seconds >= 0);
      double nanos = 1_000_000_000 * seconds;
      Preconditions.checkArgument(nanos <= Long.MAX_VALUE);
      this.deadlineNanos = Optional.of((long) nanos);
    }

    Optional<Long> getDeadlineNanos() {
      return deadlineNanos;
    }

    @Override
    public void startCancel() {
      logger.atWarning().log("Canceling HTTP API call has no effect");
    }
  }

  @Override
  public Context newClientContext() {
    return new Context();
  }

  static void communicationFailure(
      Context context, String errorDetail, AnyRpcCallback<APIResponse> callback, Throwable cause) {
    context.setApplicationError(0);
    context.setErrorDetail(errorDetail);
    context.setStatus(
        StatusProto.newBuilder()
            .setSpace("RPC")
            .setCode(UNKNOWN_ERROR_CODE)
            .setCanonicalCode(UNKNOWN_ERROR_CODE)
            .setMessage(errorDetail)
            .build());
    context.setException(cause);
    callback.failure();
  }

  // This represents a timeout of our HTTP request. We don't usually expect this, because we
  // include a timeout in the API call which the server should respect. However, this fallback
  // logic ensures that we will get an appropriate and timely exception if the server is very slow
  // to respond for some reason.
  // ApiProxyImpl will normally have given up before this happens, so the main purpose of the
  // timeout is to free up resources from the failed HTTP request.
  static void timeout(AnyRpcCallback<APIResponse> callback) {
    APIResponse apiResponse =
        APIResponse.newBuilder()
            .setError(APIResponse.ERROR.RPC_ERROR_VALUE)
            .setRpcError(RpcError.DEADLINE_EXCEEDED)
            .build();
    callback.success(apiResponse);
    // This is "success" in the sense that we got back a response, but one that will provoke
    // an ApiProxy.ApiDeadlineExceededException.
  }

  static void cancelled(AnyRpcCallback<APIResponse> callback) {
    APIResponse apiResponse = APIResponse.newBuilder().setError(ERROR.CANCELLED_VALUE).build();
    callback.success(apiResponse);
    // This is "success" in the sense that we got back a response, but one that will provoke
    // an ApiProxy.CancelledException.
  }

  @Override
  public void call(AnyRpcClientContext ctx, APIRequest req, AnyRpcCallback<APIResponse> cb) {
    Context context = (Context) ctx;
    ByteString payload = req.getPb();
    if (payload.size() > MAX_PAYLOAD) {
      requestTooBig(cb);
      return;
    }
    RemoteApiPb.Request requestPb = RemoteApiPb.Request.newBuilder()
        .setServiceName(req.getApiPackage())
        .setMethod(req.getCall())
        .setRequest(payload)
        .setRequestId(req.getSecurityTicket())
        .setTraceContext(req.getTraceContext().toByteString())
        .build();
    send(requestPb.toByteArray(), context, cb);
  }

  static void receivedResponse(
      byte[] responseBytes,
      int responseLength,
      Context context,
      AnyRpcCallback<APIResponse> callback) {
    logger.atFine().log("Response size %d", responseLength);
    CodedInputStream input = CodedInputStream.newInstance(responseBytes, 0, responseLength);
    RemoteApiPb.Response responsePb;
    try {
      responsePb = RemoteApiPb.Response.parseFrom(input, ExtensionRegistry.getEmptyRegistry());
    } catch (UninitializedMessageException | IOException e) {
      String errorDetail = "Failed to parse RemoteApiPb.Response";
      logger.atWarning().withCause(e).log("%s", errorDetail);
      communicationFailure(context, errorDetail, callback, e);
      return;
    }

    if (responsePb.hasApplicationError()) {
      RemoteApiPb.ApplicationError applicationError = responsePb.getApplicationError();
      context.setApplicationError(applicationError.getCode());
      context.setErrorDetail(applicationError.getDetail());
      context.setStatus(StatusProto.getDefaultInstance());
      callback.failure();
      return;
    }

    APIResponse apiResponse =
        APIResponse.newBuilder()
            .setError(ApiProxyUtils.remoteApiErrorToApiResponseError(responsePb).getNumber())
            .setPb(responsePb.getResponse())
            .build();
    callback.success(apiResponse);
  }

  abstract void send(byte[] requestBytes, Context context, AnyRpcCallback<APIResponse> callback);

  private static void requestTooBig(AnyRpcCallback<APIResponse> cb) {
    APIResponse apiResponse =
        APIResponse.newBuilder().setError(ERROR.REQUEST_TOO_LARGE_VALUE).build();
    cb.success(apiResponse);
    // This is "success" in the sense that we got back a response, but one that will provoke
    // an ApiProxy.RequestTooLargeException.
  }

  static void responseTooBig(AnyRpcCallback<APIResponse> cb) {
    APIResponse apiResponse =
        APIResponse.newBuilder().setError(ERROR.RESPONSE_TOO_LARGE_VALUE).build();
    cb.success(apiResponse);
    // This is "success" in the sense that we got back a response, but one that will provoke
    // an ApiProxy.ResponseTooLargeException.
  }
}
