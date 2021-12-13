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

package com.google.appengine.api.log;

import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

import com.google.appengine.api.log.LogQuery.Version;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.logservice.LogServicePb.LogModuleVersion;
import com.google.apphosting.api.logservice.LogServicePb.LogOffset;
import com.google.apphosting.api.logservice.LogServicePb.LogReadRequest;
import com.google.apphosting.api.logservice.LogServicePb.LogReadResponse;
import com.google.apphosting.api.logservice.LogServicePb.LogServiceError;
import com.google.apphosting.api.logservice.LogServicePb.RequestLog;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Tests for {@link LogServiceImpl}.
 *
 */
@RunWith(JUnit4.class)
public class LogServiceImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final LocalServiceTestHelper helper = new LocalServiceTestHelper();
  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;

  static final String APPLICATION_ID = "logs-test";
  static final String MAJOR_VERSION_ID = "1";
  static final String VERSION_ID = "1.554564564564564564";

  @Before
  public void setUp() throws Exception {
    helper.setEnvAppId(APPLICATION_ID);
    helper.setEnvVersionId(VERSION_ID);

    helper.setUp();

    ApiProxy.setDelegate(delegate);
  }

  public List<RequestLog> getTestData(int numVals) {
    ArrayList<RequestLog> list = new ArrayList<>();
    for (int i = 0; i < numVals; i++) {
      RequestLog rl =
          RequestLog.newBuilder()
              .setRequestId(ByteString.copyFromUtf8("dummy"))
              .setAppId("app")
              .setVersionId("v")
              .setIp("")
              .setStartTime(0)
              .setEndTime(0)
              .setLatency(0)
              .setMcycles(0)
              .setMethod("")
              .setResource("")
              .setHttpVersion("")
              .setStatus(0)
              .setResponseSize(0)
              .setUrlMapEntry("")
              .setCombined(Integer.toString(i))
              .build();
      list.add(rl);
    }

    return list;
  }

  public LogReadRequest createLogReadRequest(
      Long startTime,
      Long endTime,
      Integer batchSize,
      List<Version> versions,
      List<String> requestIds) {
    LogReadRequest.Builder request = LogReadRequest.newBuilder().setAppId(APPLICATION_ID);
    if (startTime != null) {
      request.setStartTime(startTime);
    }

    if (endTime != null) {
      request.setEndTime(endTime);
    }
    if (batchSize != null) {
      batchSize = 20;
    }

    if (versions != null) {
      for (Version version : versions) {
        LogModuleVersion.Builder requestModuleVersion = request.addModuleVersionBuilder();
        if (!version.getModuleId().equals("default")) {
          requestModuleVersion.setModuleId(version.getModuleId());
        }
        requestModuleVersion.setVersionId(version.getVersionId());
      }
    } else {
      request.addModuleVersionBuilder().setVersionId(LogServiceImplTest.MAJOR_VERSION_ID);
    }

    if (requestIds != null) {
      for (String requestId : requestIds) {
        request.addRequestId(ByteString.copyFromUtf8(requestId));
      }
    }

    if (batchSize == null) {
      batchSize = LogService.DEFAULT_ITEMS_PER_FETCH;
    }
    return request.setCount(batchSize).setIncludeIncomplete(false).setIncludeAppLogs(false).build();
  }

  public LogReadRequest createLogReadRequest(
      Long startTime, Long endTime, Integer batchSize, List<Version> versions) {
    return createLogReadRequest(startTime, endTime, batchSize, versions, null);
  }

  public LogReadResponse createLogReadResponse(List<RequestLog> logs) {
    LogReadResponse.Builder response = LogReadResponse.newBuilder();

    for (RequestLog rl : logs) {
      response.addLog(rl);
    }

    return response.build();
  }

  public String getJustCombinedRequestFields(List<RequestLog> data) {
    StringBuilder combinedFields = new StringBuilder("[ ");
    for (RequestLog datum : data) {
      combinedFields.append(datum.getCombined()).append(" ");
    }
    combinedFields.append("]");
    return combinedFields.toString();
  }

  public String getJustCombinedFields(List<RequestLogs> data) {
    StringBuilder combinedFields = new StringBuilder("[ ");
    for (RequestLogs datum : data) {
      combinedFields.append(datum.getCombined()).append(" ");
    }
    combinedFields.append("]");
    return combinedFields.toString();
  }

  public Long getTimeForNDaysAgo(Integer n, Long fromDay) {
    if (fromDay == null) {
      Instant i = Instant.now();
      fromDay = i.getEpochSecond() * 1000000 + i.getNano() / 1000;
    }
    int oneDay = 60 * 60 * 24;
    return fromDay - n * oneDay;
  }

  public void setupExpectations(LogReadRequest request, LogReadResponse response) throws Exception {
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();

    Future<byte[]> mockedFuture = immediateFuture(response.toByteArray());
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(LogServiceImpl.PACKAGE),
            eq(LogServiceImpl.READ_RPC_NAME),
            eq(request.toByteArray()),
            isA(apiConfig.getClass())))
        .thenReturn(mockedFuture);
  }

  @Test
  public void testGetAllLogs() throws Exception {
    int totalNumItems = LogService.DEFAULT_ITEMS_PER_FETCH * 2;
    List<RequestLog> expectedLogs = getTestData(totalNumItems);

    // The first request should have all the user-specified filters.
    LogReadRequest.Builder initialRequest = LogReadRequest.newBuilder().setAppId(APPLICATION_ID);
    initialRequest.addModuleVersionBuilder().setVersionId(MAJOR_VERSION_ID);
    initialRequest
        .setIncludeIncomplete(false)
        .setCount(LogService.DEFAULT_ITEMS_PER_FETCH)
        .setIncludeAppLogs(false);

    // The offset returned by the first request will be a reference to where the
    // next request should begin - here, since we ask for MAX_ITEMS, the offset
    // should be pointing at MAX_ITEMS.
    LogOffset offset =
        LogOffset.newBuilder()
            .setRequestId(
                ByteString.copyFromUtf8(Integer.toString(LogService.DEFAULT_ITEMS_PER_FETCH)))
            .build();

    // The first response will contain the first batch of logs and an offset for
    // the second (and last) batch of logs.
    LogReadResponse.Builder initialResponse = LogReadResponse.newBuilder();
    for (int i = 0; i < LogService.DEFAULT_ITEMS_PER_FETCH; i++) {
      initialResponse.addLog(expectedLogs.get(i));
    }
    initialResponse.setOffset(offset);

    // The second request should contain everything the first request had, but
    // with the offset given to us by the first response.
    LogReadRequest.Builder secondRequest = LogReadRequest.newBuilder().setAppId(APPLICATION_ID);
    secondRequest.addModuleVersionBuilder().setVersionId(MAJOR_VERSION_ID);
    secondRequest
        .setIncludeIncomplete(false)
        .setCount(LogService.DEFAULT_ITEMS_PER_FETCH)
        .setIncludeAppLogs(false)
        .setOffset(offset);

    // The second response contains the second batch of logs and a None pointer
    // in the offset, as there are no more logs after this.
    LogReadResponse.Builder secondResponse = LogReadResponse.newBuilder();
    for (int i = LogService.DEFAULT_ITEMS_PER_FETCH;
        i < LogService.DEFAULT_ITEMS_PER_FETCH * 2;
        i++) {
      secondResponse.addLog(expectedLogs.get(i));
    }

    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
    Future<byte[]> mockedFirstFuture = immediateFuture(initialResponse.build().toByteArray());
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(LogServiceImpl.PACKAGE),
            eq(LogServiceImpl.READ_RPC_NAME),
            eq(initialRequest.build().toByteArray()),
            isA(apiConfig.getClass())))
        .thenReturn(mockedFirstFuture);

    Future<byte[]> mockedSecondFuture = immediateFuture(secondResponse.build().toByteArray());
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(LogServiceImpl.PACKAGE),
            eq(LogServiceImpl.READ_RPC_NAME),
            eq(secondRequest.build().toByteArray()),
            isA(apiConfig.getClass())))
        .thenReturn(mockedSecondFuture);

    LogQuery query = LogQuery.Builder.withDefaults();
    List<RequestLogs> actualLogs = new ArrayList<>();
    for (RequestLogs record : new LogServiceImpl().fetch(query)) {
      actualLogs.add(record);
    }

    assertThat(actualLogs).hasSize(expectedLogs.size());
  }

  @Test
  public void testGetAllLogsForTwoAppVersions() throws Exception {
    List<Version> versions =
        ImmutableList.of(new Version("default", "1"), new Version("default", "2"));
    LogQuery query = LogQuery.Builder.withMajorVersionIds(Lists.newArrayList("1", "2"));
    getAllLogsForAppVersions(query, versions);
  }

  @Test
  public void testWithModuleVersions() throws Exception {
    List<Version> versions =
        ImmutableList.of(new Version("default", "1"), new Version("module2", "1"));
    LogQuery query = LogQuery.Builder.withVersions(versions);
    getAllLogsForAppVersions(query, versions);
  }

  private void getAllLogsForAppVersions(LogQuery query, List<Version> moduleVersions)
      throws Exception {
    List<RequestLog> expectedData = getTestData(LogService.DEFAULT_ITEMS_PER_FETCH);
    Long startTime = null;
    Long endTime = null;
    Integer batchSize = null;
    LogReadRequest request =
        createLogReadRequest(
            startTime, endTime,
            batchSize, moduleVersions);
    LogReadResponse response = createLogReadResponse(expectedData);

    setupExpectations(request, response);

    List<RequestLogs> actualData = new ArrayList<>();
    for (RequestLogs record : new LogServiceImpl().fetch(query)) {
      actualData.add(record);
    }

    String expectedLogs = getJustCombinedRequestFields(expectedData);
    String actualLogs = getJustCombinedFields(actualData);

    assertThat(actualLogs).isEqualTo(expectedLogs);
  }

  @Test
  public void testGetLogsForRequestIds() throws Exception {
    int numRequested = LogService.DEFAULT_ITEMS_PER_FETCH;
    List<RequestLog> expectedData = getTestData(numRequested);

    List<String> requestIds = new ArrayList<>();
    LogReadRequest.Builder request =
        createLogReadRequest(null, null, null, null, requestIds).toBuilder();
    LogReadResponse response = createLogReadResponse(expectedData);

    ArrayList<String> queriedIds = new ArrayList<>();
    //   List<ByteString> expectedIds = request.getRequestIdList();
    for (int i = 0; i < numRequested; i++) {
      String requestId = Integer.toString(i);
      queriedIds.add(requestId);
      //  expectedIds.add(ByteString.copyFromUtf8(requestId));
      request.addRequestId(ByteString.copyFromUtf8(requestId));
    }

    setupExpectations(request.build(), response);

    LogQuery query = LogQuery.Builder.withRequestIds(queriedIds);

    List<RequestLogs> actualData = new ArrayList<>();
    for (RequestLogs record : new LogServiceImpl().fetch(query)) {
      actualData.add(record);
    }

    String expectedLogs = getJustCombinedRequestFields(expectedData);
    String actualLogs = getJustCombinedFields(actualData);

    assertThat(actualLogs).isEqualTo(expectedLogs);
  }

  @Test
  public void testGetLogsForInvalidRequestIds() throws Exception {
    List<String> requestedIds = new ArrayList<>();
    requestedIds.add("2");
    requestedIds.add("2");
    IllegalArgumentException e1 =
        assertThrows(
            IllegalArgumentException.class, () -> LogQuery.Builder.withRequestIds(requestedIds));
    assertThat(e1).hasMessageThat().isEqualTo("requestIds must be unique.");
    requestedIds.clear();

    requestedIds.add("2G");
    IllegalArgumentException e2 =
        assertThrows(
            IllegalArgumentException.class, () -> LogQuery.Builder.withRequestIds(requestedIds));
    String message2 =
        "requestIds must only contain valid request ids. 2G is not a valid request id.";
    assertThat(e2).hasMessageThat().isEqualTo(message2);

    IllegalArgumentException e3 =
        assertThrows(
            IllegalArgumentException.class, () -> LogQuery.Builder.withRequestIds(requestedIds));
    String message3 =
        "requestIds must only contain valid request ids. 2G is not a valid request id.";
    assertThat(e3).hasMessageThat().isEqualTo(message3);
  }

  @Test
  public void testGetLogsForLast24Hours() throws Exception {
    List<RequestLog> expectedData = getTestData(LogService.DEFAULT_ITEMS_PER_FETCH);

    Long startTime = getTimeForNDaysAgo(1, null);
    Long endTime = null;
    Integer batchSize = null;
    LogReadRequest request = createLogReadRequest(startTime, endTime, batchSize, null);
    LogReadResponse response = createLogReadResponse(expectedData);

    setupExpectations(request, response);

    LogQuery query = LogQuery.Builder.withStartTimeUsec(startTime);
    List<RequestLogs> actualData = new ArrayList<>();
    for (RequestLogs record : new LogServiceImpl().fetch(query)) {
      actualData.add(record);
    }

    String expectedLogs = getJustCombinedRequestFields(expectedData);
    String actualLogs = getJustCombinedFields(actualData);

    assertThat(actualLogs).isEqualTo(expectedLogs);
  }

  @Test
  public void testGetLogsForTwoDaysAgo() throws Exception {
    List<RequestLog> expectedData = getTestData(LogService.DEFAULT_ITEMS_PER_FETCH);

    Long endTime = getTimeForNDaysAgo(1, null);
    Long startTime = getTimeForNDaysAgo(1, endTime);
    Integer batchSize = null;
    LogReadRequest request = createLogReadRequest(startTime, endTime, batchSize, null);
    LogReadResponse response = createLogReadResponse(expectedData);

    setupExpectations(request, response);

    LogQuery query = LogQuery.Builder.withStartTimeUsec(startTime).endTimeUsec(endTime);
    List<RequestLogs> actualData = new ArrayList<>();
    for (RequestLogs record : new LogServiceImpl().fetch(query)) {
      actualData.add(record);
    }

    String expectedLogs = getJustCombinedRequestFields(expectedData);
    String actualLogs = getJustCombinedFields(actualData);

    assertThat(actualLogs).isEqualTo(expectedLogs);
  }

  @Test
  public void testGetLogsForTwoDaysAgoMillis() throws Exception {
    List<RequestLog> expectedData = getTestData(LogService.DEFAULT_ITEMS_PER_FETCH);

    // Use millisecond-aligned microsecond timestamps.
    Long endTimeUsec = getTimeForNDaysAgo(1, null) / 1000 * 1000;
    Long startTimeUsec = getTimeForNDaysAgo(1, endTimeUsec) / 1000 * 1000;
    Long endTimeMillis = endTimeUsec / 1000;
    Long startTimeMillis = startTimeUsec / 1000;
    Integer batchSize = null;
    LogReadRequest request =
        createLogReadRequest(startTimeMillis * 1000, endTimeMillis * 1000, batchSize, null);
    LogReadResponse response = createLogReadResponse(expectedData);

    setupExpectations(request, response);

    LogQuery query =
        LogQuery.Builder.withStartTimeMillis(startTimeMillis).endTimeMillis(endTimeMillis);
    List<RequestLogs> actualData = new ArrayList<>();
    for (RequestLogs record : new LogServiceImpl().fetch(query)) {
      actualData.add(record);
    }

    String expectedLogs = getJustCombinedRequestFields(expectedData);
    String actualLogs = getJustCombinedFields(actualData);

    assertThat(actualLogs).isEqualTo(expectedLogs);

    // Test the Usec/Millis variants.
    assertThat(startTimeUsec).isEqualTo(query.getStartTimeUsec());
    assertThat(endTimeUsec).isEqualTo(query.getEndTimeUsec());
    assertThat(startTimeMillis).isEqualTo(query.getStartTimeMillis());
    assertThat(endTimeMillis).isEqualTo(query.getEndTimeMillis());

    query = LogQuery.Builder.withEndTimeMillis(endTimeMillis).startTimeMillis(startTimeMillis);
    assertThat(startTimeUsec).isEqualTo(query.getStartTimeUsec());
    assertThat(endTimeUsec).isEqualTo(query.getEndTimeUsec());
    assertThat(startTimeMillis).isEqualTo(query.getStartTimeMillis());
    assertThat(endTimeMillis).isEqualTo(query.getEndTimeMillis());

    query = LogQuery.Builder.withDefaults();
    assertThat(query.getStartTimeUsec()).isNull();
    assertThat(query.getEndTimeUsec()).isNull();
    assertThat(query.getStartTimeMillis()).isNull();
    assertThat(query.getEndTimeMillis()).isNull();
  }

  @Test
  public void testFutureInvalidRequest() throws Exception {
    LogReadRequest request = createLogReadRequest(null, null, null, null);
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();

    Future<byte[]> futureMock =
        immediateFailedFuture(
            new ApiProxy.ApplicationException(
                LogServiceError.ErrorCode.INVALID_REQUEST.getNumber(), "Test error detail"));
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(LogServiceImpl.PACKAGE),
            eq(LogServiceImpl.READ_RPC_NAME),
            eq(request.toByteArray()),
            isA(apiConfig.getClass())))
        .thenReturn(futureMock);

    InvalidRequestException e =
        assertThrows(
            InvalidRequestException.class,
            () -> new LogServiceImpl().fetch(LogQuery.Builder.withDefaults()));
    assertThat(e).hasMessageThat().isEqualTo("Test error detail");
  }

  @Test
  public void testFutureStorageError() throws Exception {
    LogReadRequest request = createLogReadRequest(null, null, null, null);
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();

    Future<byte[]> futureMock =
        immediateFailedFuture(
            new ApiProxy.ApplicationException(
                LogServiceError.ErrorCode.STORAGE_ERROR.getNumber(), "Test error detail"));
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(LogServiceImpl.PACKAGE),
            eq(LogServiceImpl.READ_RPC_NAME),
            eq(request.toByteArray()),
            isA(apiConfig.getClass())))
        .thenReturn(futureMock);

    LogServiceException e =
        assertThrows(
            LogServiceException.class,
            () -> new LogServiceImpl().fetch(LogQuery.Builder.withDefaults()));
    assertThat(e).hasMessageThat().isEqualTo("Test error detail");
  }

  @Test
  public void testFutureUnrecognizedError() throws Exception {
    LogReadRequest request = createLogReadRequest(null, null, null, null);
    ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();

    Future<byte[]> futureMock =
        immediateFailedFuture(
            new ApiProxy.ApplicationException(
                LogServiceError.ErrorCode.STORAGE_ERROR.getNumber(), "Test error detail"));
    when(delegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(LogServiceImpl.PACKAGE),
            eq(LogServiceImpl.READ_RPC_NAME),
            eq(request.toByteArray()),
            isA(apiConfig.getClass())))
        .thenReturn(futureMock);

    LogServiceException e =
        assertThrows(
            LogServiceException.class,
            () -> new LogServiceImpl().fetch(LogQuery.Builder.withDefaults()));
    assertThat(e).hasMessageThat().isEqualTo("Test error detail");
  }

  @Test
  public void testFetchWithBadVersionIds() throws Exception {
    List<String> versions = new ArrayList<>();
    versions.add(VERSION_ID);

    assertThrows(
        IllegalArgumentException.class, () -> LogQuery.Builder.withMajorVersionIds(versions));

    List<String> versions2 = new ArrayList<>();
    versions2.add("12.231412412414");

    assertThrows(
        IllegalArgumentException.class, () -> LogQuery.Builder.withMajorVersionIds(versions2));
  }

  @Test
  public void testFetchWithOffset() throws Exception {
    List<RequestLog> expectedData = getTestData(LogService.DEFAULT_ITEMS_PER_FETCH);

    LogReadRequest.Builder request = createLogReadRequest(null, null, null, null).toBuilder();
    LogOffset offset =
        LogOffset.newBuilder()
            .setRequestId(ByteString.copyFrom(new byte[] {(byte) 0xfe, (byte) 0xff, (byte) 0xcd}))
            .build();
    request.setOffset(offset);
    LogReadResponse response = createLogReadResponse(expectedData);

    setupExpectations(request.build(), response);

    LogQuery query = LogQuery.Builder.withDefaults();
    query.offset(base64().encode(offset.toByteArray()));
    new LogServiceImpl().fetch(query);

    // Test negative case.
    query.offset("!"); // Not parseable as Base64.
    assertThrows(IllegalArgumentException.class, () -> new LogServiceImpl().fetch(query));
  }
}
