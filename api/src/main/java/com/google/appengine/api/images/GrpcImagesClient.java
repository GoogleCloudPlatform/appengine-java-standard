// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.appengine.api.images;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.appengine.api.EnvironmentProvider;
import com.google.appengine.api.SystemEnvironmentProvider;
import com.google.appengine.api.images.proto.ImagesServiceGrpc;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.common.annotations.VisibleForTesting;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/** Client for interacting with the gRPC based Images service. */
class GrpcImagesClient {

  private static final int MAX_MESSAGE_SIZE = 32 * 1024 * 1024; // 32MB
  private final ManagedChannel channel;
  private final EnvironmentProvider environmentProvider;
  private final CallCredentials callCredentials;

  // private ImagesServiceGrpc.ImagesServiceBlockingStub blockingStub;

  public GrpcImagesClient() {
    this(new SystemEnvironmentProvider(), getApplicationDefaultCredentials());
  }

  // Constructor for production
  GrpcImagesClient(EnvironmentProvider environmentProvider, GoogleCredentials googleCredentials) {
    this(environmentProvider, createOidcCredentials(environmentProvider, googleCredentials));
  }

  // Constructor for testing
  @VisibleForTesting
  GrpcImagesClient(EnvironmentProvider environmentProvider, CallCredentials callCredentials) {
    this.environmentProvider = environmentProvider;
    this.callCredentials = callCredentials;
    String target = getTarget();
    this.channel =
        NettyChannelBuilder.forTarget(target)
            .maxInboundMessageSize(MAX_MESSAGE_SIZE)
            .keepAliveTime(60, SECONDS)
            .useTransportSecurity()
            .build();
  }

  private static GoogleCredentials getApplicationDefaultCredentials() {
    try {
      return GoogleCredentials.getApplicationDefault();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get Application Default Credentials", e);
    }
  }

  private String getTarget() {
    String endpoint =
        environmentProvider.getenv(ImagesServiceFactoryImpl.IMAGES_SERVICE_ENDPOINT_ENV);
    checkState(
        !isNullOrEmpty(endpoint),
        ImagesServiceFactoryImpl.IMAGES_SERVICE_ENDPOINT_ENV + " environment variable not set.");
    try {
      URI uri = new URI(endpoint);
      String host = uri.getHost();
      checkState(
          host != null,
          "Invalid URI in %s: %s",
          ImagesServiceFactoryImpl.IMAGES_SERVICE_ENDPOINT_ENV,
          endpoint);
      return host + ":443";
    } catch (URISyntaxException e) {
      throw new IllegalStateException(
          "Invalid URI in "
              + ImagesServiceFactoryImpl.IMAGES_SERVICE_ENDPOINT_ENV
              + ": "
              + endpoint,
          e);
    }
  }

  private static CallCredentials createOidcCredentials(
      EnvironmentProvider environmentProvider, GoogleCredentials googleCredentials) {
    String endpoint =
        environmentProvider.getenv(ImagesServiceFactoryImpl.IMAGES_SERVICE_ENDPOINT_ENV);
    checkState(
        !isNullOrEmpty(endpoint),
        ImagesServiceFactoryImpl.IMAGES_SERVICE_ENDPOINT_ENV + " environment variable not set.");
    checkState(
        googleCredentials instanceof IdTokenProvider,
        "The Application Default Credentials do not support OIDC ID token generation.");
    IdTokenProvider idTokenProvider = (IdTokenProvider) googleCredentials;
    IdTokenCredentials idTokenCredentials =
        IdTokenCredentials.newBuilder()
            .setTargetAudience(endpoint)
            .setIdTokenProvider(idTokenProvider)
            .build();
    return MoreCallCredentials.from(idTokenCredentials);
  }

  public ImagesServiceGrpc.ImagesServiceBlockingStub getBlockingStub() {
    return ImagesServiceGrpc.newBlockingStub(channel).withCallCredentials(callCredentials);
  }

  public ImagesServiceGrpc.ImagesServiceFutureStub getFutureStub() {
    return ImagesServiceGrpc.newFutureStub(channel).withCallCredentials(callCredentials);
  }

  public void shutdown() {
    if (channel != null && !channel.isShutdown()) {
      try {
        channel.shutdown().awaitTermination(5, SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // Handle exception
      }
    }
  }
}
