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
import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.apphosting.base.protos.api_bytes.RemoteApiPb;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Test for {@link RemoteRpc}.
 *
 */
@RunWith(JUnit4.class)
public class RemoteRpcTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private AppEngineClient appEngineClient;

  /**
   * Test that every RPC has a request id and that these ids differ.
   */
  @Test
  public void testRequestId() throws IOException {
    RemoteRpc remoteRpc = new RemoteRpc(appEngineClient);
    byte[] dummyRequest = {1, 2, 3, 4};
    RemoteApiPb.Response responseProto = RemoteApiPb.Response.getDefaultInstance();
    AppEngineClient.Response dummyResponse =
        new AppEngineClient.Response(200, responseProto.toByteArray(), UTF_8);
    when(appEngineClient.getRemoteApiPath()).thenReturn("/path/one");
    when(appEngineClient
        .post(eq("/path/one"), eq("application/octet-stream"), any(byte[].class)))
        .thenReturn(dummyResponse);

    ArgumentCaptor<byte[]> byteArrayCaptor = ArgumentCaptor.forClass(byte[].class);

    remoteRpc.call("foo", "bar", "logSuffix", dummyRequest);

    verify(appEngineClient)
        .post(eq("/path/one"), eq("application/octet-stream"), byteArrayCaptor.capture());
    RemoteApiPb.Request request1 = parseRequest(byteArrayCaptor.getValue());
    assertThat(request1.getServiceName()).isEqualTo("foo");
    assertThat(request1.getMethod()).isEqualTo("bar");
    assertThat(request1.getRequest().toByteArray()).isEqualTo(dummyRequest);
    String id1 = request1.getRequestId();

    when(appEngineClient.getRemoteApiPath()).thenReturn("/path/two");
    when(appEngineClient
        .post(eq("/path/two"), eq("application/octet-stream"), any(byte[].class)))
        .thenReturn(dummyResponse);

    remoteRpc.call("foo", "bar", "logSuffix", dummyRequest);

    verify(appEngineClient)
        .post(eq("/path/two"), eq("application/octet-stream"), byteArrayCaptor.capture());
    RemoteApiPb.Request request2 = parseRequest(byteArrayCaptor.getValue());
    assertThat(request2.getServiceName()).isEqualTo("foo");
    assertThat(request2.getMethod()).isEqualTo("bar");
    assertThat(request2.getRequest().toByteArray()).isEqualTo(dummyRequest);
    String id2 = request2.getRequestId();
    assertWithMessage("Expected '%s' != '%s[", id1, id2).that(id1.equals(id2)).isFalse();
  }

  private static RemoteApiPb.Request parseRequest(byte[] bytes) {
    RemoteApiPb.Request.Builder parsedRequest = RemoteApiPb.Request.newBuilder();
    try {
      parsedRequest.mergeFrom(bytes, ExtensionRegistry.getEmptyRegistry());
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
    //  assertThat(parsedRequest.mergeFrom(bytes)).isTrue();
    return parsedRequest.build();
  }
}
