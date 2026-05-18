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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.appengine.api.EnvironmentProvider;
import io.grpc.CallCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GrpcImagesClientTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private EnvironmentProvider mockEnvironmentProvider;
  @Mock private CallCredentials mockCallCredentials;

  @Test
  public void constructor_validEndpointAndCreds_success() {
    when(mockEnvironmentProvider.getenv("IMAGES_SERVICE_ENDPOINT"))
        .thenReturn("https://my-service.run.app");

    GrpcImagesClient client = new GrpcImagesClient(mockEnvironmentProvider, mockCallCredentials);
    assertThat(client).isNotNull();
  }

  @Test
  public void constructor_endpointNotSet_throwsException() {
    when(mockEnvironmentProvider.getenv("IMAGES_SERVICE_ENDPOINT")).thenReturn(null);
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> new GrpcImagesClient(mockEnvironmentProvider, mockCallCredentials));
    assertThat(e).hasMessageThat().contains("IMAGES_SERVICE_ENDPOINT environment variable not set");
  }

  @Test
  public void constructor_invalidEndpoint_throwsException() {
    when(mockEnvironmentProvider.getenv("IMAGES_SERVICE_ENDPOINT")).thenReturn("://my-service");
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> new GrpcImagesClient(mockEnvironmentProvider, mockCallCredentials));
    assertThat(e).hasMessageThat().contains("Invalid URI in IMAGES_SERVICE_ENDPOINT");
  }

  @Test
  public void constructor_endpointMissingHost_throwsException() {
    when(mockEnvironmentProvider.getenv("IMAGES_SERVICE_ENDPOINT")).thenReturn("https://");
    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> new GrpcImagesClient(mockEnvironmentProvider, mockCallCredentials));
    assertThat(e).hasMessageThat().contains("Invalid URI in IMAGES_SERVICE_ENDPOINT");
  }

  @Test
  public void getBlockingStub_returnsStub() {
    when(mockEnvironmentProvider.getenv("IMAGES_SERVICE_ENDPOINT"))
        .thenReturn("https://my-service.run.app");
    GrpcImagesClient client = new GrpcImagesClient(mockEnvironmentProvider, mockCallCredentials);
    assertThat(client.getBlockingStub()).isNotNull();
  }
}
