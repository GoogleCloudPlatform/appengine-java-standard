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

package com.google.appengine.api.search;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.RequestStatus;
import com.google.appengine.api.search.proto.SearchServicePb.SearchServiceError.ErrorCode;
import com.google.apphosting.base.protos.Codes.Code;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RequestStatusUtilTest {

  @Test
  public void toCanonicalCode() {
    assertThat(RequestStatusUtil.toCanonicalCode(ErrorCode.OK)).isEqualTo(Code.OK);
    assertThat(RequestStatusUtil.toCanonicalCode(ErrorCode.INVALID_REQUEST))
        .isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(RequestStatusUtil.toCanonicalCode(ErrorCode.TRANSIENT_ERROR))
        .isEqualTo(Code.UNAVAILABLE);
    assertThat(RequestStatusUtil.toCanonicalCode(ErrorCode.INTERNAL_ERROR))
        .isEqualTo(Code.INTERNAL);
    assertThat(RequestStatusUtil.toCanonicalCode(ErrorCode.PERMISSION_DENIED))
        .isEqualTo(Code.PERMISSION_DENIED);
    assertThat(RequestStatusUtil.toCanonicalCode(ErrorCode.TIMEOUT))
        .isEqualTo(Code.DEADLINE_EXCEEDED);
    assertThat(RequestStatusUtil.toCanonicalCode(ErrorCode.CONCURRENT_TRANSACTION))
        .isEqualTo(Code.ABORTED);
  }

  @Test
  public void newStatus() {
    SearchServicePb.RequestStatus status = RequestStatusUtil.newStatus(ErrorCode.OK);
    assertThat(status.getCode()).isEqualTo(ErrorCode.OK);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.OK.getNumber());
    assertThat(status.hasErrorDetail()).isFalse();

    status = RequestStatusUtil.newStatus(ErrorCode.INVALID_REQUEST, "an error message");
    assertThat(status.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.INVALID_ARGUMENT.getNumber());
    assertThat(status.getErrorDetail()).isEqualTo("an error message");
  }

  @Test
  public void reduceEmpty() {
    RequestStatus status = RequestStatusUtil.reduce(forErrorCodes());
    assertThat(status.getCode()).isEqualTo(ErrorCode.OK);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.OK_VALUE);
    assertThat(status.hasErrorDetail()).isFalse();
  }

  @Test
  public void reduceSingle() {
    RequestStatus status = RequestStatusUtil.reduce(forErrorCodes(ErrorCode.OK));
    assertThat(status.getCode()).isEqualTo(ErrorCode.OK);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.OK_VALUE);
    assertThat(status.hasErrorDetail()).isFalse();

    status = RequestStatusUtil.reduce(forErrorCodes(ErrorCode.INVALID_REQUEST));
    assertThat(status.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.INVALID_ARGUMENT_VALUE);
    assertThat(status.hasErrorDetail()).isTrue();
    assertThat(status.getErrorDetail()).isEqualTo(composeDetail(ErrorCode.INVALID_REQUEST));
  }

  @Test
  public void reduceHomogeneous() {
    RequestStatus status = RequestStatusUtil.reduce(forErrorCodes(ErrorCode.OK, ErrorCode.OK));
    assertThat(status.getCode()).isEqualTo(ErrorCode.OK);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.OK_VALUE);
    assertThat(status.hasErrorDetail()).isFalse();

    status =
        RequestStatusUtil.reduce(
            forErrorCodes(
                ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST));
    assertThat(status.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.INVALID_ARGUMENT_VALUE);
    assertThat(status.hasErrorDetail()).isTrue();
    assertThat(status.getErrorDetail()).isEqualTo(composeDetail(ErrorCode.INVALID_REQUEST));
  }

  /** PERMISSION_DENIED (4) wins over INVALID_REQUEST (1) */
  @Test
  public void reduceHeterogeneous() {
    RequestStatus status =
        RequestStatusUtil.reduce(
            forErrorCodes(ErrorCode.OK, ErrorCode.INVALID_REQUEST, ErrorCode.PERMISSION_DENIED));
    assertThat(status.getCode()).isEqualTo(ErrorCode.PERMISSION_DENIED);
    assertThat(status.getCanonicalCode()).isEqualTo(Code.PERMISSION_DENIED_VALUE);
    assertThat(status.hasErrorDetail()).isTrue();
    assertThat(status.getErrorDetail()).contains(composeDetail(ErrorCode.PERMISSION_DENIED));
  }

  /** Detail string stably based on status. */
  private String composeDetail(ErrorCode errorCode) {
    return String.format("%s (%d)", errorCode, errorCode.getNumber());
  }

  /** Makes RequestStatus, with detail from {@link #composeDetail} if not OK. */
  private ImmutableList<RequestStatus> forErrorCodes(ErrorCode... errorCodes) {
    ImmutableList.Builder<RequestStatus> builder = ImmutableList.builder();
    for (ErrorCode errorCode : errorCodes) {
      RequestStatus.Builder rsBuilder =
          RequestStatus.newBuilder()
              .setCode(errorCode)
              .setCanonicalCode(RequestStatusUtil.toCanonicalCode(errorCode).getNumber());
      if (errorCode != ErrorCode.OK) {
        rsBuilder.setErrorDetail(composeDetail(errorCode));
      }
      builder.add(rsBuilder.build());
    }
    return builder.build();
  }
}
