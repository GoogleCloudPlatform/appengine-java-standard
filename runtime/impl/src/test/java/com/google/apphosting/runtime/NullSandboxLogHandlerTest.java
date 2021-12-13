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

import static com.google.common.base.StandardSystemProperty.FILE_SEPARATOR;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class NullSandboxLogHandlerTest {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final Logger rootLogger = Logger.getLogger("");
  private static final List<Handler> originalRootHandlers = new ArrayList<>();
  @SuppressWarnings("unchecked")
  private static final Delegate<Environment> mockDelegate = Mockito.mock(Delegate.class);
  private static final Map<Environment, List<ApiProxy.LogRecord>> logRecordMap =
      new ConcurrentHashMap<>();
  private static final ByteArrayOutputStream javaLogOutput = new ByteArrayOutputStream();

  @Rule
  public final TestName testName = new TestName();

  @BeforeClass
  public static void setUpClass() {
    // NullSandboxHandler.init() takes the handlers off the root logger and installs them on
    // the loggers for internal runtime classes. Then it switches the root logger to log via
    // ApiProxy. So we put our own logger on the root logger before that, so we can capture
    // the output that gets set there (by loggers for internal runtime classes).
    rootLogger.addHandler(new JavaLogHandler());
    Collections.addAll(originalRootHandlers, rootLogger.getHandlers());
    new NullSandboxLogHandler().init(rootLogger);

    // Arrange to capture ApiProxy logs.
    ApiProxy.setDelegate(mockDelegate);
    doAnswer(
            invocationOnMock -> {
              Environment environment = invocationOnMock.getArgument(0);
              ApiProxy.LogRecord logRecord = invocationOnMock.getArgument(1);
              List<ApiProxy.LogRecord> logRecords =
                  logRecordMap.computeIfAbsent(environment, unused -> new ArrayList<>());
              logRecords.add(logRecord);
              return null;
            })
        .when(mockDelegate)
        .log(any(), any());
  }

  private static class JavaLogHandler extends StreamHandler {
    JavaLogHandler() {
      setOutputStream(javaLogOutput);
      try {
        setEncoding("UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public synchronized void publish(LogRecord record) {
      super.publish(record);
      flush();
    }
  }

  @Before
  public void initMockEnvironment() {
    Environment mockEnvironment = Mockito.mock(Environment.class);
    ApiProxy.setEnvironmentForCurrentThread(mockEnvironment);
  }

  private static List<ApiProxy.LogRecord> apiProxyLogRecordsForCurrentThread() {
    return logRecordMap.getOrDefault(ApiProxy.getCurrentEnvironment(), ImmutableList.of());
  }

  private static String javaLogOutput() {
    try {
      return javaLogOutput.toString("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private static void clearJavaLogOutput() {
    javaLogOutput.reset();
  }

  @Test
  public void userLogsGoToApiProxy() {
    clearJavaLogOutput();
    Logger.getLogger("com.example.Foo").log(Level.WARNING, "Oops {0}", new Object[] {"!"});
    List<ApiProxy.LogRecord> logRecords = apiProxyLogRecordsForCurrentThread();
    assertThat(logRecords).hasSize(1);
    ApiProxy.LogRecord logRecord = logRecords.get(0);
    assertThat(logRecord.getLevel()).isEqualTo(ApiProxy.LogRecord.Level.warn);
    String expectedMessage = getClass().getName() + " " + testName.getMethodName() + ": Oops !\n";
    assertThat(logRecord.getMessage()).isEqualTo(expectedMessage);
    assertThat(javaLogOutput()).isEmpty();
  }

  @Test
  public void runtimeLogsGoToOriginalHandlers() {
    clearJavaLogOutput();
    logger.atWarning().log("Runtime log record %s", "!");
    assertThat(apiProxyLogRecordsForCurrentThread()).isEmpty();
    String output = javaLogOutput();
    // The messages from FastFormatter look like this:
    // W 18:15:49 <classname> <methodname> <message>
    // Contrast with the verbose messages from SimpleFormatter that look like this (two lines):
    // Nov 16, 2018 5:44:36 PM <classname> <methodname>
    // WARNING: <message>
    Pattern expectedOutputPattern = Pattern.compile(
        "W \\d{2}:\\d{2}:\\d{2} "
        + Pattern.quote(getClass().getName() + " " + testName.getMethodName()) + " "
        + "Runtime log record !\n");
    assertThat(output).matches(expectedOutputPattern);
  }

  @Test
  public void runtimeLogsGoToOriginalHandlers_exception() {
    clearJavaLogOutput();
    logger.atSevere().withCause(new VerifyError("oops")).log("Oops!");
    assertThat(apiProxyLogRecordsForCurrentThread()).isEmpty();
    String output = javaLogOutput();

    if (FILE_SEPARATOR.value().equals("/")) {
      // Pattern does not work on Windows. TODO: Add it.
      Pattern expectedOutputPattern =
          Pattern.compile(
              "S \\d{2}:\\d{2}:\\d{2} "
                  + Pattern.quote(getClass().getName() + " " + testName.getMethodName())
                  + " "
                  + "Oops!\n"
                  + Pattern.quote("java.lang.VerifyError: oops\n")
                  + "\\s+at "
                  + Pattern.quote(getClass().getName() + "." + testName.getMethodName())
                  + ".*",
              Pattern.DOTALL);
      assertThat(output).matches(expectedOutputPattern);
    }
  }

  @Test
  public void runtimeLogsWithJdkLogger() {
    clearJavaLogOutput();
    Logger.getLogger(getClass().getName()).log(Level.INFO, "Oops {0}", "!");
    assertThat(apiProxyLogRecordsForCurrentThread()).isEmpty();
    String output = javaLogOutput();
    Pattern expectedOutputPattern = Pattern.compile(
        "I \\d{2}:\\d{2}:\\d{2} "
            + Pattern.quote(getClass().getName() + " " + testName.getMethodName()) + " "
            + "Oops !\n");
    assertThat(output).matches(expectedOutputPattern);
  }

  @Test
  public void timestampFormat() {
    clearJavaLogOutput();
    LogRecord record = new LogRecord(Level.INFO, "foo");
    record.setMillis(1550271456774L);  // This is 2019-02-15 14:57:36 PST
    Logger.getLogger(getClass().getName()).log(record);
    String output = javaLogOutput();
    // Logger formats by timezone so drop the hours in the test
    assertThat(output).contains(":57:36");
  }
}
