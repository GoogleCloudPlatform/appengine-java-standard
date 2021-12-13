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

package com.google.apphosting.runtime.grpc;

import com.google.apphosting.runtime.anyrpc.AnyRpcCallback;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

/**
 * gRPC client-side stream observer that converts the received RPC response into a call on the
 * supplied {@link AnyRpcCallback}.
 *
 * @param <ResponseT> The proto2 message that gRPC will receive as a successful response.
 *
 */
public class CallbackStreamObserver<ResponseT extends Message>
    implements StreamObserver<ResponseT> {

  private final GrpcClientContext clientContext;
  private final AnyRpcCallback<ResponseT> anyRpcCallback;

  private CallbackStreamObserver(
      GrpcClientContext clientContext,
      AnyRpcCallback<ResponseT> anyRpcCallback) {
    this.clientContext = clientContext;
    this.anyRpcCallback = anyRpcCallback;
  }

  /**
   * Returns a {@link StreamObserver} that will convert gRPC responses into calls on the given
   * {@code anyRpcCallback}.
   *
   * @param clientContext the context that will be updated with success or failure details when the
   *     RPC completes
   * @param anyRpcCallback the callback that will be invoked when the RPC completes
   */
  public static <ResponseT extends Message>
      CallbackStreamObserver<ResponseT> of(
          GrpcClientContext clientContext,
          AnyRpcCallback<ResponseT> anyRpcCallback) {
    return new CallbackStreamObserver<>(clientContext, anyRpcCallback);
  }

  @Override
  public void onNext(ResponseT grpcResponse) {
    anyRpcCallback.success(grpcResponse);
  }

  @Override
  public void onError(Throwable throwable) {
    clientContext.setException(throwable);
    anyRpcCallback.failure();
  }

  @Override
  public void onCompleted() {
  }
}
