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

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.logservice.LogServicePb.FlushRequest;
import com.google.apphosting.base.protos.AppLogsPb.AppLogGroup;
import com.google.apphosting.base.protos.AppLogsPb.AppLogLine;
import com.google.apphosting.base.protos.SourcePb.SourceLocation;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ExtensionRegistry;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for the AppLogsWriter.
 *
 */
@RunWith(JUnit4.class)
public class AppLogsWriterTest {
  @Rule public MockitoRule rule = MockitoJUnit.rule();

  private static final long SMALL_FLUSH = 8192; // must be >= MAX_LOG_LINE
  private static final long STANDARD_FLUSH = 512 * 1024;
  private static final int DEFAULT_MAX_LOG_LINE = 8192;
  private static final String LOG_BLOCK = Strings.repeat("x", 8000);
  private static final long INVERSE_NANO = 1000 * 1000 * 1000;

  private @Mock ApiProxy.Delegate<ApiProxy.Environment> delegate;
  private @Mock ApiProxy.Environment environment;
  private MutableUpResponse response;

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);
    ApiProxy.setEnvironmentForCurrentThread(environment);
    response = new MutableUpResponse();
  }

  @After
  public void tearDown() throws Exception {
    delegate = null;
    environment = null;
    ApiProxy.setDelegate(null);
    ApiProxy.clearEnvironmentForCurrentThread();
    response = null;
  }

  @Test
  public void testAddRecord() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, DEFAULT_MAX_LOG_LINE, 0);
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "1"));
    verifyNoMoreInteractions(delegate, environment);
    // Check that the response contains our one log message.
    assertThat(response.getAppLogCount()).isEqualTo(1);
    assertThat(response.getAppLog(0)).isEqualTo(createLogLine("1"));
  }

  @Test
  public void testLongAddRecord() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, DEFAULT_MAX_LOG_LINE, 0);
    writer.addLogRecordAndMaybeFlush(
        new LogRecord(LogRecord.Level.info, 0, buildLongLogMessage(2000).trim()));
    verifyNoMoreInteractions(delegate, environment);
    // Check that the response contains our one log message.
    String prefix = AppLogsWriter.LOG_CONTINUATION_PREFIX;
    assertThat(response.getAppLogCount()).isEqualTo(2);
    assertThat(response.getAppLog(0).getMessage().substring(0, 2)).isEqualTo("a0");
    assertThat(response.getAppLog(1).getMessage().substring(0, prefix.length() + 2)).isEqualTo(
        prefix + "a1");
  }

  // Pretty much the same as testLongAddRecord except double the length of the
  // maximum log line so we should no longer wrap.
  @Test
  public void testLongAddRecordLongMaxLogLine() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, 2 * DEFAULT_MAX_LOG_LINE, 0);
    writer.addLogRecordAndMaybeFlush(
        new LogRecord(LogRecord.Level.info, 0, buildLongLogMessage(2000).trim()));
    verifyNoMoreInteractions(delegate, environment);
    // Check that the response contains our one log message.
    assertThat(response.getAppLogCount()).isEqualTo(1);
  }

  @Test
  public void testFlushDueToSize() throws Exception {
    Future<byte[]> flushResponse = immediateFuture(new byte[0]);
    ArgumentCaptor<byte[]> flushRequestBytes = ArgumentCaptor.forClass(byte[].class);
    when(delegate.makeAsyncCall(
            eq(environment), eq("logservice"), eq("Flush"), flushRequestBytes.capture(), notNull()))
        .thenReturn(flushResponse);
    AppLogsWriter writer = new AppLogsWriter(response, SMALL_FLUSH, DEFAULT_MAX_LOG_LINE, 0);
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "1"));
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "2"));
    // Messages 1 should have been sent to the Flush RPC:
    AppLogGroup.Builder group = AppLogGroup.newBuilder();
    FlushRequest.Builder flushRequest =
        FlushRequest.newBuilder()
            .mergeFrom(flushRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
    group.mergeFrom(flushRequest.getLogs(), ExtensionRegistry.getEmptyRegistry());
    assertThat(group.getLogLineCount()).isEqualTo(1);
    assertThat(group.getLogLine(0)).isEqualTo(createLogLine("1"));
    // And message 2 should still be in the response buffer.
    assertThat(response.getAppLogCount()).isEqualTo(1);
    assertThat(response.getAppLog(0)).isEqualTo(createLogLine("2"));
  }

  @Test
  public void testSecondFlushBlocksOnFirst() throws Exception {
    Future<byte[]> flushResponse = immediateFuture(new byte[0]);

    ArgumentCaptor<byte[]> flushRequestBytes = ArgumentCaptor.forClass(byte[].class);
    when(delegate.makeAsyncCall(
            eq(environment),
            eq("logservice"),
            eq("Flush"),
            flushRequestBytes.capture(),
            notNull()))
        .thenReturn(flushResponse);
    AppLogsWriter writer = new AppLogsWriter(response, SMALL_FLUSH, DEFAULT_MAX_LOG_LINE, 0);
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "1"));
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "2"));
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "3"));
    assertThat(flushRequestBytes.getAllValues()).hasSize(2);
    // The first flush should contain messages 1 and 2.
    {
      AppLogGroup.Builder group = AppLogGroup.newBuilder();
      FlushRequest.Builder flushRequest =
          FlushRequest.newBuilder()
              .mergeFrom(
                  flushRequestBytes.getAllValues().get(0), ExtensionRegistry.getEmptyRegistry());
      group.mergeFrom(flushRequest.getLogs(), ExtensionRegistry.getEmptyRegistry());
      assertThat(group.getLogLineCount()).isEqualTo(1);
      assertThat(group.getLogLine(0)).isEqualTo(createLogLine("1"));
    }
    // The second flush should contain messages 3 and 4.
    {
      AppLogGroup.Builder group = AppLogGroup.newBuilder();
      FlushRequest.Builder flushRequest =
          FlushRequest.newBuilder()
              .mergeFrom(
                  flushRequestBytes.getAllValues().get(1), ExtensionRegistry.getEmptyRegistry());
      group.mergeFrom(flushRequest.getLogs(), ExtensionRegistry.getEmptyRegistry());
      assertThat(group.getLogLineCount()).isEqualTo(1);
      assertThat(group.getLogLine(0)).isEqualTo(createLogLine("2"));
    }
    // And message 5 should still be in the response buffer.
    assertThat(response.getAppLogCount()).isEqualTo(1);
    assertThat(response.getAppLog(0)).isEqualTo(createLogLine("3"));
  }

  @Test
  public void testFlushAndWaitDoesNotBlockAsyncCall() throws Exception {
    final SettableFuture<byte[]> settableFuture = SettableFuture.create();
    final CountDownLatch inGet = new CountDownLatch(1);

    Future<byte[]> flushResponse = new AbstractFuture<byte[]>() {
      @Override
      public final byte[] get() throws InterruptedException, ExecutionException {
        inGet.countDown();

        return settableFuture.get();
      }
    };

    ArgumentCaptor<byte[]> flushRequestBytes = ArgumentCaptor.forClass(byte[].class);
    when(delegate.makeAsyncCall(
            eq(environment), eq("logservice"), eq("Flush"), flushRequestBytes.capture(), notNull()))
        .thenReturn(flushResponse);

    final AppLogsWriter writer = new AppLogsWriter(response, SMALL_FLUSH, DEFAULT_MAX_LOG_LINE, 0);

    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, "1"));

    Future<?> flushAndWaitFuture =
        newSingleThreadExecutor()
            .submit(
                () -> {
                  ApiProxy.setEnvironmentForCurrentThread(environment);
                  writer.flushAndWait();
                });

    inGet.await();

    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, "2"));
    assertThat(flushRequestBytes.getAllValues()).hasSize(1);
    // The first flush should contain message 1.
    {
      AppLogGroup.Builder group = AppLogGroup.newBuilder();
      FlushRequest.Builder flushRequest =
          FlushRequest.newBuilder()
              .mergeFrom(flushRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
      group.mergeFrom(flushRequest.getLogs(), ExtensionRegistry.getEmptyRegistry());
      assertThat(group.getLogLineCount()).isEqualTo(1);
      assertThat(group.getLogLine(0).getMessage()).isEqualTo("1");
    }
    assertThat(flushAndWaitFuture.isDone()).isFalse();
    // And message 2 should be in the response buffer before flushAndWait() returns.
    assertThat(response.getAppLogCount()).isEqualTo(1);
    assertThat(response.getAppLog(0).getMessage()).isEqualTo("2");

    settableFuture.set(new byte[0]);
    flushAndWaitFuture.get();

    // And message 2 should still be in the response buffer.
    assertThat(response.getAppLogCount()).isEqualTo(1);
    assertThat(response.getAppLog(0).getMessage()).isEqualTo("2");
  }

  @Test
  public void testFlushDueToTime() throws Exception {
    Future<byte[]> flushResponse = immediateFuture(new byte[0]);
    ArgumentCaptor<byte[]> flushRequestBytes = ArgumentCaptor.forClass(byte[].class);
    Ticker ticker = mock(Ticker.class);
    when(delegate.makeAsyncCall(
            eq(environment), eq("logservice"), eq("Flush"), flushRequestBytes.capture(), notNull()))
        .thenReturn(flushResponse);
    when(ticker.read()).thenReturn(0L).thenReturn(1 * INVERSE_NANO).thenReturn(60 * INVERSE_NANO);
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, DEFAULT_MAX_LOG_LINE, 60);
    writer.setStopwatch(Stopwatch.createUnstarted(ticker));
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "1"));
    writer.addLogRecordAndMaybeFlush(new LogRecord(LogRecord.Level.info, 0, LOG_BLOCK + "2"));
    // Messages 1 and 2 should have been sent to the Flush RPC:
    AppLogGroup.Builder group = AppLogGroup.newBuilder();
    FlushRequest.Builder flushRequest =
        FlushRequest.newBuilder()
            .mergeFrom(flushRequestBytes.getValue(), ExtensionRegistry.getEmptyRegistry());
    group.mergeFrom(flushRequest.getLogs(), ExtensionRegistry.getEmptyRegistry());
    assertThat(group.getLogLineCount()).isEqualTo(2);
    assertThat(group.getLogLine(0)).isEqualTo(createLogLine("1"));
    assertThat(group.getLogLine(1)).isEqualTo(createLogLine("2"));
    // And message 2 should still be in the response buffer.
    assertThat(response.getAppLogCount()).isEqualTo(0);
  }

  private AppLogLine createLogLine(String suffix) {
    return AppLogLine.newBuilder()
        .setLevel(1)
        .setTimestampUsec(0)
        .setMessage(LOG_BLOCK + suffix)
        .build();
  }

  /**
   * Run the tests for the method {@link AppLogsWriter#split(LogRecord)}
   * when splitting is enabled to ensure that:
   * <ol>
   * <li> the lengths of the component messages sum
   * to the length of the original message
   * <li> the length of each component message is not
   * more than the maximum allowed
   * <li> the original message is split at newlines.
   * </ol>
   * We also optionally print strings to standard
   * out for manual sanity checking.
   */
  void runTestLogMessageSplit(int maxLogLine) throws Exception {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, maxLogLine, 0);
    for (int i = 1; i <= 5; i++) {
      String message = buildLongLogMessage(i * 1000).trim();
      LogRecord record = new LogRecord(LogRecord.Level.debug, 0, message);
      List<LogRecord> list = writer.split(record);
      debugPrintln("message length = " + message.length());
      int listLength = list.size();
      if (message.length() <= maxLogLine) {
        assertThat(listLength).isEqualTo(1);
      }
      int cumulativeLength = 0;
      int recordIndex = -1;
      for (LogRecord rec : list) {
        recordIndex++;
        String s = rec.getMessage();
        int length = s.length();
        debugPrintln(s);
        debugPrintln("-------------------------------------------");
        debugPrintln("Above part's length: " + length);
        debugPrintln("-------------------------------------------");
        assertThat(s.length()).isAtMost(maxLogLine);
        if (recordIndex == listLength - 1) {
          assertThat(s).endsWith("x");
        } else {
          assertThat(s).endsWith(AppLogsWriter.LOG_CONTINUATION_SUFFIX);
          s = s.substring(0, length - AppLogsWriter.LOG_CONTINUATION_SUFFIX_LENGTH);
          assertThat(s).endsWith("x");
        }
        boolean removedSuffix = false;
        if (s.startsWith(AppLogsWriter.LOG_CONTINUATION_PREFIX)) {
          s = s.substring(AppLogsWriter.LOG_CONTINUATION_PREFIX_LENGTH);
          removedSuffix = true;
        }
        cumulativeLength += s.length();
        if (removedSuffix) {
          cumulativeLength++;
        }
      }
      assertThat(cumulativeLength).isEqualTo(message.length());
      debugPrintln("=============================================================");
    }
  }

  @Test
  public void testLogMessageSplitStandardSize() throws Exception {
    runTestLogMessageSplit(DEFAULT_MAX_LOG_LINE);
  }

  @Test
  public void testLogMessageSplitStandardLong() throws Exception {
    runTestLogMessageSplit(DEFAULT_MAX_LOG_LINE * 2);
  }

  @Test
  public void testLogMessageSplitStandardShort() throws Exception {
    runTestLogMessageSplit(DEFAULT_MAX_LOG_LINE / 2);
  }

  @Test
  public void testLogMessageSplitWithSurrogates() throws Exception {
    int maxLogLine = 1024;
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, maxLogLine, 0);
    assertThat(writer.getMaxLogMessageLength()).isEqualTo(maxLogLine);
    StringBuilder sb = new StringBuilder();
    sb.append('x');
    while (sb.length() < maxLogLine) {
      sb.appendCodePoint(0x1d11e); // 𝄞, which requires surrogate pairs to be encoded
    }
    assertThat(sb.length()).isGreaterThan(maxLogLine);

    int logCutLength = maxLogLine - AppLogsWriter.LOG_CONTINUATION_SUFFIX_LENGTH;
    assertThat(logCutLength % 2).isEqualTo(0);
    // If the cut length is even, that means that our initial 'x' will cause one of the following
    // sequence of surrogate pairs to span the logCutLength point. If we don't account for that,
    // we will end up splitting a surrogate pair.

    LogRecord record = new LogRecord(LogRecord.Level.debug, 0, sb.toString());
    List<LogRecord> list = writer.split(record);
    String expectFirst = sb.substring(0, logCutLength - 1) + AppLogsWriter.LOG_CONTINUATION_SUFFIX;
    String expectSecond = AppLogsWriter.LOG_CONTINUATION_PREFIX + sb.substring(logCutLength - 1);
    String actualFirst = list.get(0).getMessage();
    String actualSecond = list.get(1).getMessage();
    assertThat(ImmutableList.of(actualFirst, actualSecond))
        .containsExactly(expectFirst, expectSecond);
  }

  @Test
  public void testMinLength() {
    // Make sure that setting < MIN_MAX_LOG_MESSAGE_LENGTH causes the length
    // to be rounded up to MIN_MAX_LOG_MESSAGE_LENGTH.
    {
      AppLogsWriter writer =
          new AppLogsWriter(response, STANDARD_FLUSH, AppLogsWriter.MIN_MAX_LOG_MESSAGE_LENGTH / 2,
              0);
      assertThat(writer.getMaxLogMessageLength()).isEqualTo(AppLogsWriter.MIN_MAX_LOG_MESSAGE_LENGTH);
    }

    // And to be utterly sure, make sure that sizes > MIN_MAX_LOG_MESSAGE_LENGTH
    // are abided.
    {
      AppLogsWriter writer =
          new AppLogsWriter(response, STANDARD_FLUSH, AppLogsWriter.MIN_MAX_LOG_MESSAGE_LENGTH * 2,
              0);
      assertThat(writer.getMaxLogMessageLength()).isEqualTo(AppLogsWriter.MIN_MAX_LOG_MESSAGE_LENGTH * 2);
    }
  }

  /**
   * Builds a long log message with multiple lines that has
   * some similarity to a long Java stack trace. The message
   * consists of blocks of the form axxxxb where xxxx is a
   * 4-digit integer. This makes it easy to visually scan
   * the message and keep track of where you are. Each line
   * of the message ends with an "x";
   */
  private static String buildLongLogMessage(int numBlocks) {
    Random random = new Random();
    StringBuilder builder = new StringBuilder(numBlocks * 8);
    for (int i = 0; i < numBlocks; i++) {
      builder.append("a").append(String.format("%04d", i)).append("b");
      if (random.nextFloat() < 0.15) {
        builder.append("x\n");
      }
    }
    builder.append("a").append(String.format("%04d", numBlocks)).append("bx");
    return builder.toString();
  }

  /**
   * Makes sure that any leading whitespace in a line immediately after a split
   * point is preserved. This is useful for nicely formatting exceptions
   * as context lines have a leading tab.
   */
  @Test
  public void testPreserveLeadingSpaceAtSplit() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, 1024, 0);

    // Create a string that looks like an exception (the important issue
    // here is the context line starts with whitespace).
    StringBuilder messageBuilder = new StringBuilder();
    final String context = "\tat package.class (filename:line#)";
    messageBuilder.append("Caused by: java.lang.RuntimeException: message\n");
    for (int i = 0; i < 100; ++i) {
      messageBuilder.append(context);
      messageBuilder.append('\n');
    }

    // Split the record and make sure it yields multiple records.
    List<LogRecord> list =
        writer.split(new LogRecord(LogRecord.Level.debug, 0, messageBuilder.toString()));
    assertWithMessage("Splitting did not occur: message too short / max log message length too big")
        .that(list.size())
        .isGreaterThan(1);

    String[] lines = list.get(1).getMessage().split("\n");
    assertWithMessage("Internal error: continuation prefix/suffix missing")
        .that(lines.length)
        .isGreaterThan(2);
    assertThat(lines[0] + '\n').isEqualTo(AppLogsWriter.LOG_CONTINUATION_PREFIX);
    assertThat(lines[1]).isEqualTo(context);
  }

  @Test
  public void testFastStackTrace() {
    // N.B.(jmacd): The intent here is to avoid releasing an appengine
    // runtime that suddenly performs badly if for some reason the
    // lazy stacktrace mechanism becomes unavailable. Should this be
    // moved to AppLogsWriter to prevent the runtime from starting?
    assume().that(JAVA_SPECIFICATION_VERSION.value()).isEqualTo("1.8");
    assertThat(Throwables.lazyStackTraceIsLazy()).isTrue();
  }

  private static StackTraceElement makeFrame(String frameInfo) {
    List<String> parts = Splitter.on(':').splitToList(frameInfo);
    if (parts.size() != 4) {
      throw new IllegalArgumentException();
    }
    return new StackTraceElement(
        parts.get(0), parts.get(1), parts.get(2), Integer.parseInt(parts.get(3)));
  }

  @Test
  public void testSourceLocation1() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, 1024, 0);
    List<StackTraceElement> stack =
        ImmutableList.of(
            makeFrame(
                "com.google.apphosting.runtime.security.shared.intercept.java.util.logging."
                    + "DefaultHandler:convertLogRecord:Yadayada.java:59"),
            makeFrame(
                "com.google.apphosting.runtime.security.shared.intercept.java.util.logging."
                    + "DefaultHandler:publish:Yadayada.java:48"),
            makeFrame("java.util.logging.Logger:log:Yadayada.java:619"),
            makeFrame("java.util.logging.Logger:doLog:Yadayada.java:640"),
            makeFrame("java.util.logging.Logger:log:Yadayada.java:663"),
            makeFrame("java.util.logging.Logger:info:Yadayada.java:1181"),
            // (the following frame is the caller),
            makeFrame("com.google.shigeru.HelloAppEngineServlet:runLoggingTest:Yadayada.java:22"),
            makeFrame("com.google.shigeru.HelloAppEngineServlet:doGet:Yadayada.java:17"),
            makeFrame("javax.servlet.http.HttpServlet:service:Yadayada.java:617"),
            makeFrame("javax.servlet.http.HttpServlet:service:Yadayada.java:717"),
            makeFrame("org.mortbay.jetty.servlet.ServletHolder:handle:Yadayada.java:511"),
            makeFrame(
                "org.mortbay.jetty.servlet.ServletHandler$CachedChain:doFilter:"
                    + "Yadayada.java:1166"),
            makeFrame("java.lang.Thread:run:Yadayada.java:724"));

    SourceLocation source =
        writer.getSourceLocationProto(AppLogsWriter.getTopUserStackFrame(stack));
    assertThat(source.getFunctionName()).isEqualTo(
        "com.google.shigeru.HelloAppEngineServlet.runLoggingTest");
    assertThat(source.getFile()).isEqualTo("Yadayada.java");
    assertThat(source.getLine()).isEqualTo(22);
  }

  @Test
  public void testSourceLocation2() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, 1024, 0);
    List<StackTraceElement> stack =
        ImmutableList.of(
            makeFrame(
                "com.google.apphosting.runtime.security.shared.intercept.java.util.logging."
                    + "DefaultHandler:convertLogRecord:Yadayada.java:59"),
            makeFrame(
                "com.google.apphosting.runtime.security.shared.intercept.java.util.logging."
                    + "DefaultHandler:publish:Yadayada.java:48"),
            makeFrame("java.util.logging.Logger:log:Yadayada.java:619"),
            makeFrame("java.util.logging.Logger:doLog:Yadayada.java:640"),
            makeFrame("java.util.logging.Logger:log:Yadayada.java:663"),
            makeFrame("java.util.logging.Logger:info:Yadayada.java:1181"),
            // (the following frame is a reflection mirror),
            makeFrame(
                "com.google.apphosting.runtime.security.shared.intercept.java.lang.reflect.C:"
                    + "run:Generated.java:1"),
            // (the following frame is the caller),
            makeFrame("com.google.shigeru.HelloAppEngineServlet:runLoggingTest:Yadayada.java:22"),
            makeFrame("com.google.shigeru.HelloAppEngineServlet:doGet:Yadayada.java:17"),
            makeFrame("javax.servlet.http.HttpServlet:service:Yadayada.java:617"),
            makeFrame("javax.servlet.http.HttpServlet:service:Yadayada.java:717"),
            makeFrame("org.mortbay.jetty.servlet.ServletHolder:handle:Yadayada.java:511"),
            makeFrame(
                "org.mortbay.jetty.servlet.ServletHandler$CachedChain:doFilter:"
                    + "Yadayada.java:1166"),
            makeFrame("java.lang.Thread:run:Yadayada.java:724"));

    SourceLocation source =
        writer.getSourceLocationProto(AppLogsWriter.getTopUserStackFrame(stack));
    assertThat(source.getFunctionName()).isEqualTo(
        "com.google.shigeru.HelloAppEngineServlet.runLoggingTest");
    assertThat(source.getFile()).isEqualTo("Yadayada.java");
    assertThat(source.getLine()).isEqualTo(22);
  }

  @Test
  public void testSourceLocation3() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, 1024, 0);
    List<StackTraceElement> stack =
        ImmutableList.of(
            makeFrame("doesn.t.matter.because.haven.t.seen.log.yet.Class:method:file1.java:1"),
            makeFrame("java.util.logging.Logger:log:file2.java:2"),
            makeFrame("java.util.logging.Logger:info:file3.java:3"),
            // (the following frame is a reflection mirror),
            makeFrame("com.google.apphosting.runtime.security.anything.Class:method:file4.java:4"),
            makeFrame("java.lang.invoke.Cl1:M1:F1.java:1"),
            makeFrame("java.lang.reflect.Api:M1:F1.java:1"),
            makeFrame("sun.reflect.C2:M1:F2.java:2"),
            makeFrame("java.security.Checker:m:f.java:1"),
            // (the following frame is the caller),
            makeFrame("i.am.the.caller.Servlet:test:yada.java:2"));

    SourceLocation source =
        writer.getSourceLocationProto(AppLogsWriter.getTopUserStackFrame(stack));
    assertThat(source.getFunctionName()).isEqualTo("i.am.the.caller.Servlet.test");
    assertThat(source.getFile()).isEqualTo("yada.java");
    assertThat(source.getLine()).isEqualTo(2);
  }

  @Test
  public void testSourceLocation4() {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, 1024, 0);
    List<StackTraceElement> stack =
        ImmutableList.of(
            makeFrame("doesn.t.matter.because.haven.t.seen.log.yet.Class:method:file1.java:1"),
            makeFrame("java.util.logging.Logger:log:file2.java:2"),
            // (the following frame is a reflection mirror),
            makeFrame("java.security.Checker:m:f.java:1"));
    // (there is no valid caller)

    SourceLocation source =
        writer.getSourceLocationProto(AppLogsWriter.getTopUserStackFrame(stack));
    assertThat(source).isNull();
  }

  @Test
  public void testSourceLocation_NoFileName() throws Exception {
    AppLogsWriter writer = new AppLogsWriter(response, STANDARD_FLUSH, 1024, 0);
    StackTraceElement ste = new StackTraceElement(
        "com.google.foo.ClassWithoutSource", "method", null, 23);
    SourceLocation source = writer.getSourceLocationProto(ste);
    assertThat(source).isNull();
  }

  // Change to true for manual inspection of Strings
  // in testLogMessageSplit()
  private static final boolean DEBUG_LOG_STRINGS = false;

  private static void debugPrintln(String s) {
    if (DEBUG_LOG_STRINGS) {
      System.out.println(s);
    }
  }
}
