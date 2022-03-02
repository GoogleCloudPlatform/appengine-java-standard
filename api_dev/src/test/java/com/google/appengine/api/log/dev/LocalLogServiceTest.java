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

package com.google.appengine.api.log.dev;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogService;
import com.google.appengine.api.log.LogService.LogLevel;
import com.google.appengine.api.log.LogServiceFactory;
import com.google.appengine.api.log.RequestLogs;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.testing.LocalLogServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.logservice.LogServicePb.LogLine;
import com.google.apphosting.api.logservice.LogServicePb.LogOffset;
import com.google.apphosting.api.logservice.LogServicePb.LogReadRequest;
import com.google.apphosting.api.logservice.LogServicePb.RequestLog;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link LocalLogService}.
 *
 */
@RunWith(JUnit4.class)
public class LocalLogServiceTest {
  private LogService logService;
  private LocalServiceTestHelper helper;

  private final long defaultStartTime = 0;
  private final String defaultVersion = "1.54645646456";
  private final boolean defaultAppLogsDesired = false;
  private final boolean completeLogsRequested = true;

  private static final String APPLICATION = "app1";
  private static final String VERSION = "v1";
  private static final String REQUEST_ID = "r1";
  private static final long END_TIME_USEC = 1342848179788L * 1000; // [21/Jul/2012:05:22:59 -0000]
  private static final long START_TIME_USEC = END_TIME_USEC - 1 * 1000 * 1000;
  private static final String METHOD = "GET";
  private static final String RESOURCE = "/";
  private static final String HTTP_VERSION = "HTTP/1.1";
  private static final int STATUS = 200;
  private static final int RESPONSE_SIZE = 0;
  private static final String USER_AGENT =
      "Mozilla/5.0 (iPad; U; CPU OS 3_2_1 like Mac OS X; "
          + "en-us) AppleWebKit/531.21.10 (KHTML, like Gecko) Mobile/7B405";

  @Before
  public void setUp() throws Exception {
    helper = new LocalServiceTestHelper(new LocalLogServiceTestConfig());
    helper.setUp();
    logService = LogServiceFactory.getLogService();
  }

  @After
  public void tearDown() {
    helper.tearDown();
    logService = null;
  }

  RequestLog getLog(Long start, Long end, String version) {
    RequestLog.Builder rl =
        RequestLog.newBuilder()
            .setAppId(APPLICATION)
            .setIp("")
            .setLatency(0)
            .setMcycles(0)
            .setUrlMapEntry("")
            .setCombined("")
            .setMethod(METHOD)
            .setResource(RESOURCE)
            .setHttpVersion(HTTP_VERSION)
            .setStatus(STATUS)
            .setResponseSize(RESPONSE_SIZE)
            .setStartTime(start);
    if (end != null) {
      rl.setEndTime(end);
    }
    return rl.setVersionId(version).setRequestId(ByteString.copyFromUtf8(start.toString())).build();
  }

  List<RequestLog> getTestData(
      long start, String version, boolean appLogsDesired, boolean complete) {
    List<RequestLog> list = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      Long end;
      if (complete) {
        end = start + i + 1;
      } else {
        end = null;
      }

      RequestLog.Builder rl = getLog(start + i, end, version).toBuilder();

      if (appLogsDesired) {
        LogLine line =
            LogLine.newBuilder()
                .setTime(i)
                .setLevel(i % 5)
                .setLogMessage(Integer.toString(i))
                .build();
        rl.addLine(line);
      }

      list.add(rl.build());
    }

    return list;
  }

  LocalLogService getLocalLogService() {
    ApiProxyLocal proxy = (ApiProxyLocal) ApiProxy.getDelegate();
    return (LocalLogService) proxy.getService(LocalLogService.PACKAGE);
  }

  void writeTestData(List<RequestLog> data, boolean complete) {
    for (RequestLog log : data) {
      String requestId = Long.toString(log.getStartTime());

      LocalLogService logService = getLocalLogService();

      logService.registerResponseSize(log.getResponseSize());
      logService.addRequestInfo(
          log.getAppId(),
          log.getVersionId(),
          requestId,
          log.getIp(),
          log.getNickname(),
          log.getStartTime(),
          log.getEndTime(),
          log.getMethod(),
          log.getResource(),
          log.getHttpVersion(),
          log.getUserAgent(),
          complete,
          200,
          null);
      logService.clearResponseSize();

      for (LogLine line : log.getLineList()) {
        Level level = Level.parse(Integer.toString(line.getLevel()));
        logService.addAppLogLine(requestId, line.getTime(), level.intValue(), line.getLogMessage());
      }
    }
  }

  String joinLogStartTimes(List<Long> values) {
    StringBuilder result = new StringBuilder();
    for (Long value : values) {
      result.append(value).append(" ");
    }
    return result.toString();
  }

  String joinAppLogLevels(List<Integer> values) {
    StringBuilder result = new StringBuilder();
    for (Integer value : values) {
      result.append(value).append(" ");
    }
    return result.toString();
  }

  @Test
  public void testGetAllLogs() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    // Logs are stored reverse-chronologically
    List<Long> expectedLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(allLogs)) {
      expectedLogStartTimes.add(record.getStartTime());
    }

    LogQuery query = LogQuery.Builder.withDefaults();
    List<Long> actualLogStartTimes = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualLogStartTimes.add(record.getStartTimeUsec());
    }

    String expected = joinLogStartTimes(expectedLogStartTimes);
    String actual = joinLogStartTimes(actualLogStartTimes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testSetStartTime() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    long startTime = 4;

    // Logs are stored reverse-chronologically
    List<Long> expectedLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(allLogs)) {
      if (record.getEndTime() >= startTime) {
        expectedLogStartTimes.add(record.getStartTime());
      }
    }

    LogQuery query = LogQuery.Builder.withStartTimeUsec(startTime);
    List<Long> actualLogStartTimes = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualLogStartTimes.add(record.getStartTimeUsec());
    }

    String expected = joinLogStartTimes(expectedLogStartTimes);
    String actual = joinLogStartTimes(actualLogStartTimes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testSetEndTime() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    long endTime = 4;

    // Logs are stored reverse-chronologically
    List<Long> expectedLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(allLogs)) {
      if (record.getEndTime() < endTime) {
        expectedLogStartTimes.add(record.getStartTime());
      }
    }

    LogQuery query = LogQuery.Builder.withEndTimeUsec(endTime);
    List<Long> actualLogStartTimes = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualLogStartTimes.add(record.getStartTimeUsec());
    }

    String expected = joinLogStartTimes(expectedLogStartTimes);
    String actual = joinLogStartTimes(actualLogStartTimes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testSetStartAndEndTime() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    long startTime = 4;
    long endTime = 7;

    // Logs are stored reverse-chronologically
    List<Long> expectedLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(allLogs)) {
      if ((record.getEndTime() >= startTime) && (record.getEndTime() < endTime)) {
        expectedLogStartTimes.add(record.getStartTime());
      }
    }

    LogQuery query = LogQuery.Builder.withStartTimeUsec(startTime).endTimeUsec(endTime);
    List<Long> actualLogStartTimes = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualLogStartTimes.add(record.getStartTimeUsec());
    }

    String expected = joinLogStartTimes(expectedLogStartTimes);
    String actual = joinLogStartTimes(actualLogStartTimes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testIncludeIncomplete() throws Exception {
    long completeLogStartTime = 10;
    List<RequestLog> completeLogs =
        getTestData(
            completeLogStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(completeLogs, completeLogsRequested);

    long incompleteLogStartTime = 40;
    List<RequestLog> incompleteLogs =
        getTestData(
            incompleteLogStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    boolean incompleteLogsRequested = false;
    writeTestData(incompleteLogs, incompleteLogsRequested);

    List<Long> allLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(incompleteLogs)) {
      allLogStartTimes.add(record.getStartTime());
    }

    List<Long> completeLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(completeLogs)) {
      allLogStartTimes.add(record.getStartTime());
      completeLogStartTimes.add(record.getStartTime());
    }

    LogQuery query = LogQuery.Builder.withIncludeIncomplete(true);
    List<Long> actualStartTimes = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualStartTimes.add(record.getStartTimeUsec());
    }

    String expected = joinLogStartTimes(allLogStartTimes);
    String actual = joinLogStartTimes(actualStartTimes);
    assertThat(actual).isEqualTo(expected);

    query.includeIncomplete(false);
    List<Long> actualCompleteStartTimes = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualCompleteStartTimes.add(record.getStartTimeUsec());
    }

    String expectedIncomplete = joinLogStartTimes(completeLogStartTimes);
    String actualIncomplete = joinLogStartTimes(actualCompleteStartTimes);
    assertThat(actualIncomplete).isEqualTo(expectedIncomplete);
  }

  @Test
  public void testCombined() throws Exception {
    LocalLogService localLogService = getLocalLogService();
    String ip = "2620:0:10c2:102a:a800:1ff:fe00:4d7a";
    String nickname = "mitch";
    String resource = "/a/b/c/d";
    int responseSize = 123;
    Integer status = 201;
    String referrer = "http://a.b.c.org/a/b/c";
    localLogService.registerResponseSize(responseSize);
    localLogService.addRequestInfo(
        APPLICATION,
        VERSION,
        REQUEST_ID,
        ip,
        nickname,
        START_TIME_USEC,
        END_TIME_USEC,
        METHOD,
        resource,
        HTTP_VERSION,
        USER_AGENT,
        true,
        status,
        referrer);
    localLogService.clearResponseSize();
    RequestLogs logs = getSingletonLogRecord(VERSION);

    String expect =
        "2620:0:10c2:102a:a800:1ff:fe00:4d7a - mitch [21/Jul/2012:05:22:59 +0000]"
            + " \"GET /a/b/c/d HTTP/1.1\" 201 123 \"http://a.b.c.org/a/b/c\" \"Mozilla/5.0 (iPad; U"
            + "; CPU OS 3_2_1 like Mac OS X; en-us) AppleWebKit/531.21.10 (KHTML, like Gecko) "
            + "Mobile/7B405\"";
    assertThat(logs.getCombined()).isEqualTo(expect);
  }

  @Test
  public void testCombined_optionalEmpty() {
    LocalLogService localLogService = getLocalLogService();
    String ip = "";
    String nickname = "";
    String resource = "/a.b";
    int responseSize = 0;
    String userAgent = "";
    Integer status = 0;
    String referrer = "";
    localLogService.registerResponseSize(responseSize);
    localLogService.addRequestInfo(
        APPLICATION,
        VERSION,
        REQUEST_ID,
        ip,
        nickname,
        START_TIME_USEC,
        END_TIME_USEC,
        METHOD,
        resource,
        HTTP_VERSION,
        userAgent,
        true,
        status,
        referrer);
    localLogService.clearResponseSize();
    RequestLogs logs = getSingletonLogRecord(VERSION);

    String expect = "- - - [21/Jul/2012:05:22:59 +0000] \"GET /a.b HTTP/1.1\" 0 0 - -";
    assertThat(logs.getCombined()).isEqualTo(expect);
  }

  @Test
  public void testCombined_optionalNull() {
    LocalLogService localLogService = getLocalLogService();
    String ip = "cannotbenull";
    String nickname = null;
    String resource = "/a.b";
    String userAgent = null;
    Integer status = 0;
    String referrer = null;
    localLogService.registerResponseSize(0);
    localLogService.addRequestInfo(
        APPLICATION,
        VERSION,
        REQUEST_ID,
        ip,
        nickname,
        START_TIME_USEC,
        END_TIME_USEC,
        METHOD,
        resource,
        HTTP_VERSION,
        userAgent,
        true,
        status,
        referrer);
    localLogService.clearResponseSize();
    RequestLogs logs = getSingletonLogRecord(VERSION);

    String expect = "cannotbenull - - [21/Jul/2012:05:22:59 +0000] \"GET /a.b HTTP/1.1\" 0 0 - -";
    assertThat(logs.getCombined()).isEqualTo(expect);
  }

  private RequestLogs getSingletonLogRecord(String version) {
    LogQuery query = LogQuery.Builder.withBatchSize(1);
    List<String> justFirstVersionId = new ArrayList<>();
    justFirstVersionId.add(version);
    query.majorVersionIds(justFirstVersionId);
    RequestLogs logs = Iterables.getOnlyElement(logService.fetch(query));
    return logs;
  }

  @Test
  public void testGetAllLogsWithSmallLimit() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    // Logs are stored reverse-chronologically
    List<Long> expectedLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(allLogs)) {
      expectedLogStartTimes.add(record.getStartTime());
    }

    LogQuery query = LogQuery.Builder.withBatchSize(1);
    List<Long> actualLogStartTimes = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualLogStartTimes.add(record.getStartTimeUsec());
    }

    String expected = joinLogStartTimes(expectedLogStartTimes);
    String actual = joinLogStartTimes(actualLogStartTimes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testGetOneLog() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    Long expectedFirstLogStartTime = allLogs.get(0).getStartTime();

    LogQuery query = LogQuery.Builder.withBatchSize(1);
    Long actualFirstLogStartTime = (long) -1;
    for (RequestLogs record : logService.fetch(query)) {
      actualFirstLogStartTime = record.getStartTimeUsec();
    }

    assertThat(actualFirstLogStartTime).isEqualTo(expectedFirstLogStartTime);
  }

  @Test
  public void testGetByRequestId() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    String expectedId = allLogs.get(1).getRequestId().toStringUtf8();

    List<String> requestIds = new ArrayList<>();
    requestIds.add(expectedId);
    LogQuery query = LogQuery.Builder.withRequestIds(requestIds);

    List<String> actualRequestIds = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualRequestIds.add(record.getRequestId());
    }

    assertThat(actualRequestIds.size()).isEqualTo(1);
    assertThat(actualRequestIds.get(0)).isEqualTo(expectedId);
  }

  @Test
  public void testGetCertainVersionsOfData() throws Exception {
    long v1StartTime = 1;
    List<RequestLog> v1Data =
        getTestData(v1StartTime, "1", defaultAppLogsDesired, completeLogsRequested);
    writeTestData(v1Data, completeLogsRequested);

    long v2StartTime = 40;
    List<RequestLog> v2Data =
        getTestData(v2StartTime, "2", defaultAppLogsDesired, completeLogsRequested);
    writeTestData(v2Data, completeLogsRequested);

    List<Long> allLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(v2Data)) {
      allLogStartTimes.add(record.getStartTime());
    }

    List<Long> v1StartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(v1Data)) {
      allLogStartTimes.add(record.getStartTime());
      v1StartTimes.add(record.getStartTime());
    }

    List<String> bothVersionIds = new ArrayList<>();
    bothVersionIds.add("1");
    bothVersionIds.add("2");
    LogQuery query = LogQuery.Builder.withMajorVersionIds(bothVersionIds);

    List<Long> actualStartTimesBothVersions = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualStartTimesBothVersions.add(record.getStartTimeUsec());
    }

    assertThat(actualStartTimesBothVersions).isEqualTo(allLogStartTimes);

    List<String> justFirstVersionId = new ArrayList<>();
    justFirstVersionId.add("1");
    query.majorVersionIds(justFirstVersionId);

    List<Long> actualStartTimesV1 = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualStartTimesV1.add(record.getStartTimeUsec());
    }

    String expectedV1 = joinLogStartTimes(v1StartTimes);
    String actualV1 = joinLogStartTimes(actualStartTimesV1);
    assertThat(actualV1).isEqualTo(expectedV1);
  }

  @Test
  public void testIncludeAppLogs() throws Exception {
    boolean appLogsDesired = true;
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, appLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    // Try a test where we want app logs
    List<Integer> expectedLogLevels = new ArrayList<>();
    for (RequestLog item : Lists.reverse(allLogs)) {
      for (LogLine line : item.getLineList()) {
        expectedLogLevels.add(line.getLevel());
      }
    }

    LogQuery query = LogQuery.Builder.withIncludeAppLogs(appLogsDesired);
    List<Integer> actualLogLevels = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      for (AppLogLine line : record.getAppLogLines()) {
        actualLogLevels.add(line.getLogLevel().ordinal());
      }
    }

    String expectedLevels = joinAppLogLevels(expectedLogLevels);
    String actualLevels = joinAppLogLevels(actualLogLevels);

    assertThat(actualLevels).isEqualTo(expectedLevels);

    // Now try a test where we don't want app logs
    query.includeAppLogs(false);
    List<Integer> actualLogLevelsForExcludedQuery = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      for (AppLogLine line : record.getAppLogLines()) {
        actualLogLevelsForExcludedQuery.add(line.getLogLevel().ordinal());
      }
    }
    assertThat(actualLogLevelsForExcludedQuery.size()).isEqualTo(0);
  }

  @Test
  public void testMinLogLevelFiltering() throws Exception {
    boolean appLogsDesired = true;
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, appLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    List<Integer> allAppLogLevels = new ArrayList<>();
    for (RequestLog record : Lists.reverse(allLogs)) {
      for (LogLine line : record.getLineList()) {
        allAppLogLevels.add(line.getLevel());
      }
    }

    // Test filtering for all legitimate log level values
    for (LogLevel minLogLevel : LogLevel.values()) {
      int minLogLevelInt = minLogLevel.ordinal();
      List<Integer> expectedAppLogLevels = new ArrayList<>();
      for (Integer thisLevel : allAppLogLevels) {
        if (thisLevel >= minLogLevelInt) {
          expectedAppLogLevels.add(thisLevel);
        }
      }

      LogQuery query = LogQuery.Builder.withIncludeAppLogs(appLogsDesired).minLogLevel(minLogLevel);
      List<Integer> actualAppLogLevels = new ArrayList<>();
      for (RequestLogs record : logService.fetch(query)) {
        for (AppLogLine line : record.getAppLogLines()) {
          actualAppLogLevels.add(line.getLogLevel().ordinal());
        }
      }

      String expectedLevels = joinAppLogLevels(expectedAppLogLevels);
      String actualLevels = joinAppLogLevels(actualAppLogLevels);
      assertThat(actualLevels).isEqualTo(expectedLevels);
    }
  }

  @Test
  public void testNoLogs() throws Exception {
    boolean noAppLogsDesired = false;
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, noAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    // In this test we have request logs with no associated app logs - therefore,
    // a query with minimumLogLevel = 0 should exclude them all (returning no
    // logs).
    List<Long> expectedLogStartTimes = new ArrayList<Long>();

    LogQuery query = LogQuery.Builder.withMinLogLevel(LogLevel.DEBUG);
    List<Long> actualLogStartTimes = new ArrayList<Long>();
    for (RequestLogs record : logService.fetch(query)) {
      actualLogStartTimes.add(record.getStartTimeUsec());
    }

    String expected = joinLogStartTimes(expectedLogStartTimes);
    String actual = joinLogStartTimes(actualLogStartTimes);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testOffsets() throws Exception {
    List<RequestLog> allLogs =
        getTestData(defaultStartTime, defaultVersion, defaultAppLogsDesired, completeLogsRequested);
    writeTestData(allLogs, completeLogsRequested);

    // Logs are stored reverse-chronologically
    List<Long> expectedLogStartTimes = new ArrayList<>();
    for (RequestLog record : Lists.reverse(allLogs)) {
      expectedLogStartTimes.add(record.getStartTime());
    }

    LogQuery query = LogQuery.Builder.withDefaults();
    List<Long> actualLogStartTimes = new ArrayList<>();
    List<String> logOffsets = new ArrayList<>();
    for (RequestLogs record : logService.fetch(query)) {
      actualLogStartTimes.add(record.getStartTimeUsec());
      logOffsets.add(record.getOffset());
    }

    String expected = joinLogStartTimes(expectedLogStartTimes);
    String actual = joinLogStartTimes(actualLogStartTimes);
    assertThat(actual).isEqualTo(expected);

    for (int i = 0; i < 20; i++) {
      query = LogQuery.Builder.withOffset(logOffsets.get(i));
      actualLogStartTimes.clear();
      for (RequestLogs record : logService.fetch(query)) {
        actualLogStartTimes.add(record.getStartTimeUsec());
        logOffsets.add(record.getOffset());
      }

      expected =
          joinLogStartTimes(expectedLogStartTimes.subList(i + 1, expectedLogStartTimes.size()));
      actual = joinLogStartTimes(actualLogStartTimes);
      assertThat(actual).isEqualTo(expected);
    }
  }

  @Test
  public void testHexRequestIdOffsetHandling() {
    LogOffset offset =
        LogOffset.newBuilder()
            .setRequestId(ByteString.copyFromUtf8(String.format("%x", new BigInteger("12345"))))
            .build();
    LogReadRequest request = LogReadRequest.newBuilder().setAppId("").setOffset(offset).build();

    LocalLogService service = new LocalLogService();
    assertThat(service.read(null, request)).isNotNull();
  }
}
