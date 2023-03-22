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

package com.google.appengine.runtime.lite;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.mockito.Mockito.when;

import com.google.apphosting.api.ApiProxy;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.SourceLocation;
import com.google.common.collect.Range;
import com.google.common.flogger.GoogleLogger;
import com.google.common.flogger.LogSite;
import com.google.common.flogger.backend.LogData;
import com.google.common.flogger.backend.Metadata;
import com.google.common.flogger.backend.system.SimpleLogRecord;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test for LogHandler. */
@RunWith(JUnit4.class)
public final class LogHandlerTest {

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private ApiProxy.EnvironmentWithTrace mockEnv;
  @Mock private LogData mockLogData;
  @Mock private LogSite mockLogSite;

  private static Instant now() {
    // Timestamps in logging have only millisecond precision.
    return Instant.now().truncatedTo(MILLIS);
  }

  /**
   * Tests our custom conversion of a java.util.logging.LogRecord to a c.g.cloud.logging.LogEntry.
   */
  @Test
  public void testLogEntryFor()
      throws UnsupportedEncodingException, InterruptedException, ExecutionException {
    when(mockEnv.getSpanId()).thenReturn(Optional.of("dummy_span_id"));
    when(mockEnv.getTraceId()).thenReturn(Optional.of("dummy_trace_id"));
    ApiProxy.setEnvironmentForCurrentThread(mockEnv);
    CompletableFuture<LogEntry.Builder> logEntryBuilderFuture = new CompletableFuture<>();
    // Make a spying handler to snag the unbuilt log entry for verification:
    LogHandler handler =
        new LogHandler(LoggingOptions.newBuilder().setProjectId("dummy_project_id").build()) {
          @Override
          protected LogEntry.Builder logEntryFor(LogRecord record) {
            LogEntry.Builder logEntryBuilder = super.logEntryFor(record);
            logEntryBuilderFuture.complete(logEntryBuilder);
            return logEntryBuilder;
          }
        };
    Logger rootLogger = Logger.getLogger("");
    rootLogger.addHandler(handler);

    // Actually generate a log entry!
    Instant before = now();
    logger.atSevere().withCause(new Exception("This is a simulated exception")).log("Failure!");
    Instant after = now();
    // Build the log entry just so we can see the fields the handler just set:
    LogEntry logEntry = logEntryBuilderFuture.get().build();

    assertThat((String) logEntry.getPayload().getData())
        .matches(
            "(?s)Failure!\\n"
                + "java.lang.Exception: This is a simulated exception\\n"
                + "\\tat.*");
    assertThat(logEntry.getInstantTimestamp()).isIn(Range.closed(before, after));
    Map<String, String> labels = logEntry.getLabels();
    assertThat(labels).containsEntry("threadId", threadId());
    assertThat(labels).containsEntry("threadName", threadName());
    assertThat(labels).containsEntry("exception", "java.lang.Exception");
    SourceLocation sourceLocation = logEntry.getSourceLocation();
    assertThat(sourceLocation.getFile()).isEqualTo("LogHandlerTest.java");
    assertThat(sourceLocation.getLine()).isGreaterThan(0);
    assertThat(sourceLocation.getFunction())
        .isEqualTo("com.google.appengine.runtime.lite.LogHandlerTest.testLogEntryFor");
    assertThat(logEntry.getTrace()).isEqualTo("projects/null/traces/dummy_trace_id");
    assertThat(logEntry.getSpanId()).isEqualTo("dummy_span_id");
  }

  private static String threadId() {
    return Long.toString(Thread.currentThread().getId());
  }

  private static String threadName() {
    return Thread.currentThread().getName();
  }

  @Test
  public void getSrcInfoFromFloggerMetadata_works() {
    when(mockLogSite.getClassName()).thenReturn("some.package.SomeClass");
    when(mockLogSite.getMethodName()).thenReturn("someMethod");
    when(mockLogSite.getFileName()).thenReturn("SomeFile.java");
    when(mockLogSite.getLineNumber()).thenReturn(1234);
    when(mockLogData.getLogSite()).thenReturn(mockLogSite);
    when(mockLogData.getLevel()).thenReturn(Level.INFO);
    when(mockLogData.getMetadata()).thenReturn(Metadata.empty());
    LogRecord logRecord = SimpleLogRecord.create(mockLogData, Metadata.empty());

    Optional<SourceLocation> ret = LogHandler.getSrcInfoFromFloggerMetadata(logRecord);

    assertThat(ret).isPresent();
    SourceLocation sourceLocation = ret.get();
    assertThat(sourceLocation.getFile()).isEqualTo("SomeFile.java");
    assertThat(sourceLocation.getFunction()).isEqualTo("some.package.SomeClass.someMethod");
    assertThat(sourceLocation.getLine()).isEqualTo(1234);
  }

  @Test
  public void getSrcInfoFromFloggerMetadata_failsGracefully() {
    LogRecord record = new LogRecord(Level.INFO, "a message");
    record.setSourceClassName("ThisDoesNotComeFromFlogger");
    record.setSourceMethodName("foobar");

    Optional<SourceLocation> ret = LogHandler.getSrcInfoFromFloggerMetadata(record);

    assertThat(ret).isEmpty();
  }

  @Test
  public void getSrcInfoFromStack_works() {
    LogRecord record = new LogRecord(Level.INFO, "a message");
    record.setSourceClassName("com.google.appengine.runtime.lite.LogHandlerTest");
    record.setSourceMethodName("getSrcInfoFromStack_works");

    Optional<SourceLocation> ret = LogHandler.getSrcInfoFromStack(record);

    assertThat(ret).isPresent();
    SourceLocation sourceLocation = ret.get();
    assertThat(sourceLocation.getFile()).isEqualTo("LogHandlerTest.java");
    assertThat(sourceLocation.getFunction())
        .isEqualTo("com.google.appengine.runtime.lite.LogHandlerTest.getSrcInfoFromStack_works");
    assertThat(sourceLocation.getLine()).isGreaterThan(100);
  }

  @Test
  public void getSrcInfoFromStack_failsGracefully() {
    LogRecord record = new LogRecord(Level.INFO, "a message");
    record.setSourceClassName("ThisClassDoesNotExist");
    record.setSourceMethodName("getSrcInfoFromStack_failsGracefully");

    Optional<SourceLocation> ret = LogHandler.getSrcInfoFromStack(record);

    assertThat(ret).isEmpty();
  }
}
