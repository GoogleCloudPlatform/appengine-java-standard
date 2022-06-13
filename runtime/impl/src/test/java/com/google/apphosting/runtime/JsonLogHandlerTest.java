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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.base.protos.AppinfoPb.AppInfo;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.SpanId;
import com.google.apphosting.base.protos.TraceId;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.apphosting.runtime.anyrpc.APIHostClientInterface;
import com.google.common.base.Throwables;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class JsonLogHandlerTest {

  private LogRecord logRecord;

  @Test
  public void testPublish() throws Exception {
    PrintStream out = Mockito.mock(PrintStream.class);

    JsonLogHandler handler =
        new JsonLogHandler(out, true, "test-project-id", new SimpleFormatter());

    logRecord.setSourceMethodName("testPublish");
    logRecord.setThrown(new Throwable("test throwable"));
    // Publish twice to ensure the logger is not buffering messages. We need the next 3 lines to be
    // kept together so the assertion of line numbers is correct
    Throwable referenceLine = new Throwable();
    handler.publish(logRecord);
    handler.publish(logRecord);

    // The line numbers from the publish calls are being used as part of the test output, so we
    // calculate it to be used as part of the expected output
    int firstPublishLine = referenceLine.getStackTrace()[0].getLineNumber() + 1;
    int secondPublishLine = firstPublishLine + 1;
    String expectedJson =
        "{\"logging.googleapis.com/trace\":"
            + " \"projects/test-project-id/traces/01020304050607080910111213141516\","
            + " \"logging.googleapis.com/spanId\": \"000000000000004a\", \"severity\": \"INFO\","
            + " \"logging.googleapis.com/sourceLocation\": {\"function\":"
            + " \"com.google.apphosting.runtime.JsonLogHandlerTest.testPublish\", \"file\":"
            + " \"JsonLogHandlerTest.java\", \"line\": \""
            + firstPublishLine
            + "\"}, \"message\": \"This is a log"
            + " message that covers \\\"quotes\\\" \\n"
            + " newlines and \\\\ escaped characters.\\n"
            + JsonLogHandler.ESCAPER.escape(Throwables.getStackTraceAsString(logRecord.getThrown()))
            + "\"}";
    String expectedJson2 =
        "{\"logging.googleapis.com/trace\":"
            + " \"projects/test-project-id/traces/01020304050607080910111213141516\","
            + " \"logging.googleapis.com/spanId\": \"000000000000004a\", \"severity\": \"INFO\","
            + " \"logging.googleapis.com/sourceLocation\": {\"function\":"
            + " \"com.google.apphosting.runtime.JsonLogHandlerTest.testPublish\", \"file\":"
            + " \"JsonLogHandlerTest.java\", \"line\": \""
            + secondPublishLine
            + "\"}, \"message\": \"This is a log"
            + " message that covers \\\"quotes\\\" \\n"
            + " newlines and \\\\ escaped characters.\\n"
            + JsonLogHandler.ESCAPER.escape(Throwables.getStackTraceAsString(logRecord.getThrown()))
            + "\"}";
    verify(out, times(1)).println(expectedJson);
    verify(out, times(1)).println(expectedJson2);

    // Verify the escaper removes illegal characters from the JSON:
    assertThat(expectedJson2).doesNotContain("\t");
    assertThat(expectedJson2).doesNotContain("\r");
    assertThat(expectedJson2).doesNotContain("\n");
  }

  /** Verify the log handler still works if the project ID is null. */
  @Test
  public void testPublishNullProject() throws Exception {
    PrintStream out = Mockito.mock(PrintStream.class);

    JsonLogHandler handler = new JsonLogHandler(out, true, null, new SimpleFormatter());

    logRecord.setSourceMethodName("testPublishNullProject");

    // Publish twice to ensure the logger is not buffering messages. We need the next 3 lines to be
    // kept together so the assertion of line numbers is correct
    Throwable referenceLine = new Throwable();
    handler.publish(logRecord);
    handler.publish(logRecord);

    // The line numbers from the publish calls are being used as part of the test output, so we
    // calculate it to be used as part of the expected output
    int firstPublishLine = referenceLine.getStackTrace()[0].getLineNumber() + 1;
    int secondPublishLine = firstPublishLine + 1;
    String expectedJson =
        "{\"logging.googleapis.com/spanId\": \"000000000000004a\", \"severity\": \"INFO\","
            + " \"logging.googleapis.com/sourceLocation\": {\"function\":"
            + " \"com.google.apphosting.runtime.JsonLogHandlerTest.testPublishNullProject\""
            + ", \"file\": \"JsonLogHandlerTest.java\", \"line\": \""
            + firstPublishLine
            + "\"}, \"message\": \"This is a log"
            + " message that covers \\\"quotes\\\" \\n"
            + " newlines and \\\\ escaped characters.\"}";
    String expectedJson2 =
        "{\"logging.googleapis.com/spanId\": \"000000000000004a\", \"severity\": \"INFO\","
            + " \"logging.googleapis.com/sourceLocation\": {\"function\":"
            + " \"com.google.apphosting.runtime.JsonLogHandlerTest.testPublishNullProject\""
            + ", \"file\": \"JsonLogHandlerTest.java\", \"line\": \""
            + secondPublishLine
            + "\"}, \"message\": \"This is a log"
            + " message that covers \\\"quotes\\\" \\n"
            + " newlines and \\\\ escaped characters.\"}";
    verify(out, times(1)).println(expectedJson);
    verify(out, times(1)).println(expectedJson2);
  }

  /** Verify the log handler still works if the message is null. */
  @Test
  public void testPublishNullMessage() throws Exception {
    PrintStream out = Mockito.mock(PrintStream.class);

    JsonLogHandler handler = new JsonLogHandler(out, true, null, new SimpleFormatter());

    logRecord = new LogRecord(Level.INFO, null);
    logRecord.setSourceMethodName("testPublishNullMessage");

    handler.publish(logRecord);

    String expectedJson =
        "{\"logging.googleapis.com/spanId\": \"000000000000004a\", \"severity\": \"INFO\","
            + " \"message\": \"\"}";
    verify(out, times(1)).println(expectedJson);
  }

  @Test
  public void testMessageParameters() throws Exception {
    PrintStream out = Mockito.mock(PrintStream.class);

    JsonLogHandler handler =
        new JsonLogHandler(out, true, "test-project-id", new SimpleFormatter());

    Logger logger = Logger.getLogger("testMessageParameters");
    logger.addHandler(handler);
    logger.log(Level.INFO, "Message with parameters {0} and {1}", new String[] {"foo", "bar"});

    ArgumentCaptor<String> string = ArgumentCaptor.forClass(String.class);
    verify(out).println(string.capture());
    assertThat(string.getValue()).contains("Message with parameters foo and bar");
  }

  @Before
  public void setUp() {
    APIHostClientInterface apiHost = mock(APIHostClientInterface.class);
    ApiProxyImpl apiProxyImpl = ApiProxyImpl.builder().setApiHost(apiHost).build();
    AppVersion appVersion =
        AppVersion.builder()
            .setPublicRoot("/")
            .setAppInfo(AppInfo.getDefaultInstance())
            .build();

    // Test Trace ID must be 16 bytes (using 8a5711592032447e3c749a67493b9edb as example)
    TraceId.TraceIdProto traceId =
        TraceId.TraceIdProto.newBuilder()
            .setHi(0x0102030405060708L)
            .setLo(0x0910111213141516L)
            .build();

    SpanId.SpanIdProto spanId = SpanId.SpanIdProto.newBuilder().setId(0x000000000000004aL).build();

    TraceContextProto context =
        TraceContextProto.newBuilder()
            .setTraceId(traceId.toByteString())
            .setSpanId(spanId.getId())
            .build();
    UPRequest upRequest =
        UPRequest.newBuilder().setTraceContext(context).buildPartial();

    MutableUpResponse upResponse = new MutableUpResponse();
    ApiProxyImpl.EnvironmentImpl environmentImpl =
        apiProxyImpl.createEnvironment(
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
    ApiProxy.setEnvironmentForCurrentThread(environmentImpl);

    logRecord = new LogRecord(
            Level.INFO,
            "This is a log message that covers \"quotes\" \n newlines and \\ escaped characters.");

    // The classname and method name must match the names of the method and class of this call
    // because the way we receive the correct stack frame is by searching through the stack frame of
    // a Throwable created during the test.
    logRecord.setSourceClassName("com.google.apphosting.runtime.JsonLogHandlerTest");
  }
}
