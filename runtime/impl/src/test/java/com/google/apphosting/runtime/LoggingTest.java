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
import static org.mockito.Mockito.mock;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.AppLogsPb.AppLogLine;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.SourcePb.SourceLocation;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LoggingTest {
  @Rule public TestName testName = new TestName();

  private static final Logger rootLogger = Logger.getLogger("");

  /**
   * Tests that source-location logging works in the runtime. We expect that an application log
   * message will end up as an AppLogLine in the UPResponse, and that AppLogLine should have the
   * correct file name, function name, and line number.
   */
  @Test
  public void sourceLocation() {
    APIHostClientInterface apiHost = mock(APIHostClientInterface.class);
    ApiProxyImpl apiProxyImpl = ApiProxyImpl.builder().setApiHost(apiHost).build();
    AppVersion appVersion =
        AppVersion.builder()
            .setPublicRoot("/")
            .setAppInfo(AppInfo.getDefaultInstance())
            .build();
    UPRequest upRequest = UPRequest.getDefaultInstance();
    MutableUpResponse upResponse = new MutableUpResponse();
    ApiProxyImpl.EnvironmentImpl environmentImpl = apiProxyImpl.createEnvironment(
        appVersion,
        upRequest,
        upResponse,
        /* traceWriter= */ null,
        /* requestTimer= */ null,
        /* requestId= */ null,
        /* asyncFutures= */ null,
        /* outstandingApiRpcSemaphore= */ null,
        /* requestThreadGroup= */ null,
        /* requestState= */ null,
        /* millisUntilSoftDeadline= */ null);
    ApiProxy.setDelegate(apiProxyImpl);
    ApiProxy.setEnvironmentForCurrentThread(environmentImpl);

    // Configure log handlers so that application log records go to ApiProxy.log. The implementation
    // we have just defined should then convert them into AppLogLines in the UPresponse.
    new NullSandboxLogHandler().init(rootLogger);

    Logger logger = Logger.getLogger("com.example.SomeApplicationClass");
    // The following two lines must be kept together, because the line number in the log record
    // should be one more than the line number in the exception.
    Throwable lineNumberCheck = new Throwable();
    logger.info("hello world");

    ImmutableList<AppLogLine> appLogList = upResponse.getAndClearAppLogList();
    assertThat(appLogList).hasSize(1);
    AppLogLine appLogLine = appLogList.get(0);
    assertThat(appLogLine.getMessage()).contains("hello world");
    assertThat(appLogLine.getLevel()).isEqualTo(ApiProxy.LogRecord.Level.info.ordinal());
    SourceLocation sourceLocation = appLogLine.getSourceLocation();
    assertThat(sourceLocation.getFile()).isEqualTo("LoggingTest.java");
    assertThat(sourceLocation.getFunctionName())
        .isEqualTo(getClass().getName() + "." + testName.getMethodName());
    assertThat(sourceLocation.getLine())
        .isEqualTo(lineNumberCheck.getStackTrace()[0].getLineNumber() + 1);
  }

  @Test
  public void logJsonToFile_defaultFormatter() throws IOException {
    Logger fakeRootLogger = new Logger(/* name= */ "", /* resourceBundle= */ null) {};
    try {
      fakeRootLogger.addHandler(mock(Handler.class)); // add a dummy handler

      File tempLog = File.createTempFile("LoggingTest", ".log");
      tempLog.deleteOnExit();
      Logging logging = new Logging(fakeRootLogger);
      logging.logJsonToFile("projectId", tempLog.toPath(), /* clearLogHandlers= */ true);

      // The above clears the dummy handler and adds the JSON handler:
      assertThat(fakeRootLogger.getHandlers()).hasLength(1);

      fakeRootLogger.log(
          Level.INFO, "Message with parameters {0} and {1}", new String[] {"foo", "bar"});
      List<String> logLines = Files.readAllLines(tempLog.toPath());
      assertThat(logLines).hasSize(1);
      String expectedPattern =
          "\\{\"severity\": \"INFO\", \"logging.googleapis.com/sourceLocation\": \\{\"function\":"
              + " \"com.google.apphosting.runtime.LoggingTest.logJsonToFile_defaultFormatter\","
              + " \"file\": \"LoggingTest.java\", \"line\": \"\\d+\"\\}, \"message\": \"Message"
              + " with parameters foo and bar\"\\}";
      assertThat(logLines.get(0)).matches(expectedPattern);
    } finally {
      for (Handler handler : fakeRootLogger.getHandlers()) {
        handler.close();
      }
    }
  }

  @Test
  public void logJsonToFile_doNotClearHandlers() throws IOException {
    Logger fakeRootLogger = new Logger(/* name= */ "", /* resourceBundle= */ null) {};
    try {
      fakeRootLogger.addHandler(mock(Handler.class)); // add a dummy handler

      File tempLog = File.createTempFile("LoggingTest", ".log");
      tempLog.deleteOnExit();
      Logging logging = new Logging(fakeRootLogger);
      logging.logJsonToFile("projectId", tempLog.toPath(), /* clearLogHandlers= */ false);

      // The above does NOT clears the dummy handler and adds the JSON handler:
      assertThat(fakeRootLogger.getHandlers()).hasLength(2);
    } finally {
      for (Handler handler : fakeRootLogger.getHandlers()) {
        handler.close();
      }
    }
  }
}
