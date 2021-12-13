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

import com.google.appengine.api.log.LogService.LogLevel;
import com.google.appengine.api.testing.SerializationTestBase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.Serializable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Serialization tests for Log.
 *
 */
@RunWith(JUnit4.class)
public class SerializationTest extends SerializationTestBase {

  @Override
  protected Iterable<Serializable> getCanonicalObjects() {
    return ImmutableList.of(
      new InvalidRequestException(""),
      LogQuery.Builder.withDefaults(),
      new LogServiceException(""),
      LogServiceImpl.LogLevel.DEBUG,
      getCanonicalRequestLogs(),
      getCanonicalAppLogLine(),
      new LogQuery.Version("module1", "version1")
    );
  }

  @Override
  protected Class<?> getClassInApiJar() {
    return LogServiceFactory.class;
  }

  /**
   * Instructions for generating new golden files are in the BUILD file in this
   * directory.
   */
  public static void main(String[] args) throws IOException {
    SerializationTest st = new SerializationTest();
    st.writeCanonicalObjects();
  }

  private RequestLogs getCanonicalRequestLogs() {
    AppLogLine logLine = new AppLogLine(1377856859879L, LogLevel.DEBUG, "nothing to log");
    RequestLogs requestLogs = new RequestLogs();
    requestLogs.setAppId("TEST_APP");
    requestLogs.setModuleId("TEST_MODULE");
    requestLogs.setVersionId("TEST_VERSION");
    requestLogs.setRequestId("TEST_REQUEST");
    requestLogs.setOffset("/0/0");
    requestLogs.setIp("0.0.0.0");
    requestLogs.setNickname("TEST_NICKNAME");
    requestLogs.setStartTimeUsec(1335132000000L);
    requestLogs.setEndTimeUsec(1366668000000L);
    requestLogs.setLatency(3000);
    requestLogs.setMcycles(100L);
    requestLogs.setMethod("GET");
    requestLogs.setResource("/test");
    requestLogs.setHttpVersion("HTTP/1.1");
    requestLogs.setStatus(200);
    requestLogs.setResponseSize(5445433);
    requestLogs.setReferrer("TEST_REFERRER");
    requestLogs.setUserAgent("Chrome/18.0.1025.151");
    requestLogs.setUrlMapEntry("url-method");
    requestLogs.setCombined("TEST_COMBINED_LOGS");
    requestLogs.setApiMcycles(122221);
    requestLogs.setHost("0.0.0.0");
    requestLogs.setCost(22.0);
    requestLogs.setTaskName("TEST_TASK");
    requestLogs.setTaskQueueName("TEST_TASK_QUEUE");
    requestLogs.setPendingTime(0);
    requestLogs.setWasLoadingRequest(true);
    requestLogs.setFinished(true);
    requestLogs.setInstanceKey("TEST_INSTANCE");
    requestLogs.setAppLogLines(Lists.newArrayList(logLine));
    return requestLogs;
  }

  private AppLogLine getCanonicalAppLogLine() {
    return new AppLogLine(1377856859879L, LogLevel.ERROR, "nothing to log");
  }
}
