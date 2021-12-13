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

package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.SystemServicePb.StartBackgroundRequestRequest;
import com.google.apphosting.base.protos.SystemServicePb.StartBackgroundRequestResponse;
import com.google.apphosting.base.protos.SystemServicePb.SystemServiceError;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for the {@link SystemService} class.
 *
 */
@RunWith(JUnit4.class)
public class SystemServiceTest {
  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;
  @Mock private ApiProxy.Environment environment;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ApiProxy.setDelegate(delegate);
    ApiProxy.setEnvironmentForCurrentThread(environment);
  }

  @Test
  public void testStartBackgroundRequest() throws Exception {
    String expectedRequestId = "abcd";
    StartBackgroundRequestRequest requestProto = StartBackgroundRequestRequest.getDefaultInstance();

    StartBackgroundRequestResponse responseProto =
        StartBackgroundRequestResponse.newBuilder().setRequestId(expectedRequestId).build();

    when(delegate.makeSyncCall(
            ApiProxy.getCurrentEnvironment(),
            SystemService.PACKAGE,
            "StartBackgroundRequest",
            requestProto.toByteArray()))
        .thenReturn(responseProto.toByteArray());
    String requestId = new SystemService().startBackgroundRequest();

    assertThat(requestId).isEqualTo(expectedRequestId);
  }

  @Test
  public void testStartBackgroundRequest_InternalError() throws Exception {
    StartBackgroundRequestRequest requestProto = StartBackgroundRequestRequest.getDefaultInstance();

    when(delegate.makeSyncCall(
            ApiProxy.getCurrentEnvironment(),
            SystemService.PACKAGE,
            "StartBackgroundRequest",
            requestProto.toByteArray()))
        .thenThrow(
            new ApiProxy.ApplicationException(
                SystemServiceError.ErrorCode.INTERNAL_ERROR_VALUE));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new SystemService().startBackgroundRequest());
    assertThat(ex).hasMessageThat().startsWith("An internal error occurred");
  }

  @Test
  public void testStartBackgroundRequest_BackendRequired() throws Exception {
    StartBackgroundRequestRequest requestProto = StartBackgroundRequestRequest.getDefaultInstance();

    when(delegate.makeSyncCall(
            ApiProxy.getCurrentEnvironment(),
            SystemService.PACKAGE,
            "StartBackgroundRequest",
            requestProto.toByteArray()))
        .thenThrow(
            new ApiProxy.ApplicationException(
                SystemServiceError.ErrorCode.BACKEND_REQUIRED_VALUE));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new SystemService().startBackgroundRequest());
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("This feature is only available to backend instances.");
  }

  @Test
  public void testStartBackgroundRequest_LimitReached() throws Exception {
    StartBackgroundRequestRequest requestProto = StartBackgroundRequestRequest.getDefaultInstance();

    when(delegate.makeSyncCall(
            ApiProxy.getCurrentEnvironment(),
            SystemService.PACKAGE,
            "StartBackgroundRequest",
            requestProto.toByteArray()))
        .thenThrow(
            new ApiProxy.ApplicationException(
                SystemServiceError.ErrorCode.LIMIT_REACHED_VALUE));
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new SystemService().startBackgroundRequest());
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "Limit on the number of active background requests was reached for this app version.");
  }
}
