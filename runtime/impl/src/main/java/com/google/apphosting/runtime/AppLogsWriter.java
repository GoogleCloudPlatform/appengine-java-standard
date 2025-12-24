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

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.logservice.LogServicePb.FlushRequest;
import com.google.apphosting.base.protos.AppLogsPb.AppLogGroup;
import com.google.apphosting.base.protos.AppLogsPb.AppLogLine;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;

/**
 * {@code AppsLogWriter} is responsible for batching application logs for a single request and
 * sending them back to the AppServer via the LogService.Flush API call and the final return from
 * the request RPC.
 *
 * <p>The current algorithm used to send logs is as follows:
 *
 * <ul>
 *   <li>Log messages are always appended to the current {@link UPResponse}, which is returned back
 *       to the AppServer when the request completes.
 *   <li>The code never allows more than {@code byteCountBeforeFlush} bytes of log data to
 *       accumulate in the {@link UPResponse}. If adding a new log line would exceed that limit, the
 *       current set of logs are removed from it and an asynchronous API call is started to flush
 *       the logs before buffering the new line.
 *   <li>If another flush occurs while a previous flush is still pending, the caller will block
 *       synchronously until the previous call completed.
 *   <li>When the overall request completes, the request will block until any pending flush is
 *       completed ({@link
 *       RequestManager#waitForPendingAsyncFutures(java.util.Collection<Future<?>>)}) and then
 *       return the final set of logs in {@link UPResponse}.
 * </ul>
 *
 * <p>This class is also responsible for splitting large log entries into smaller fragments, which
 * is unrelated to the batching mechanism described above but is necessary to prevent the AppServer
 * from truncating individual log entries.
 *
 * <p>TODO: In the future we may wish to initiate flushes from a scheduled future which would happen
 * in a background thread. In this case, we must pass the {@link ApiProxy.Environment} in explicitly
 * so API calls can be made on behalf of the original thread.
 */
public class AppLogsWriter {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  // (Some constants below package scope for testability)
  static final String LOG_CONTINUATION_SUFFIX = "\n<continued in next message>";
  static final int LOG_CONTINUATION_SUFFIX_LENGTH = LOG_CONTINUATION_SUFFIX.length();
  static final String LOG_CONTINUATION_PREFIX = "<continued from previous message>\n";
  static final int LOG_CONTINUATION_PREFIX_LENGTH = LOG_CONTINUATION_PREFIX.length();
  static final int MIN_MAX_LOG_MESSAGE_LENGTH = 1024;
  static final String LOG_TRUNCATED_SUFFIX = "\n<truncated>";
  static final int LOG_TRUNCATED_SUFFIX_LENGTH = LOG_TRUNCATED_SUFFIX.length();

  private final Object lock = new Object();

  private final int maxLogMessageLength;
  private final int logCutLength;
  private final int logCutLengthDiv10;

  @GuardedBy("lock")
  private final ResponseAPIData genericResponse;

  private final long maxBytesToFlush;
  @GuardedBy("lock")
  private long currentByteCount;
  private final int maxSecondsBetweenFlush;
  @GuardedBy("lock")
  private Future<byte[]> currentFlush;
  @GuardedBy("lock")
  private Stopwatch stopwatch;

  public AppLogsWriter(
      MutableUpResponse upResponse,
      long maxBytesToFlush,
      int maxLogMessageLength,
      int maxFlushSeconds) {
    this(new UpResponseAPIData(upResponse), maxBytesToFlush, maxLogMessageLength, maxFlushSeconds);
  }

  /**
   * Construct an AppLogsWriter instance.
   *
   * @param genericResponse The protobuf response instance that holds the return value for
   *     EvaluationRuntime.HandleRequest. This is used to return any logs that were not sent to the
   *     appserver with an intermediate flush when the request ends.
   * @param maxBytesToFlush The maximum number of bytes of log message to allow in a single flush.
   *     The code flushes any cached logs before reaching this limit. If this is 0, AppLogsWriter
   *     will not start an intermediate flush based on size.
   * @param maxLogMessageLength The maximum length of an individual log line. A single log line
   *     longer than this will be written as multiple log entries (with the continuation
   *     prefix/suffixes added to indicate this).
   * @param maxFlushSeconds The amount of time to allow a log line to sit cached before flushing.
   *     Once a log line has been sitting for more than the specified time, all currently cached
   *     logs are flushed. If this is 0, no time based flushing occurs. N.B. because we only check
   *     the time on a log call, it is possible for a log to stay cached long after the specified
   *     time has been reached. Consider this example (assume maxFlushSeconds=60): the app logs a
   *     message when the handler starts but then does not log another message for 10 minutes. The
   *     initial log will stay cached until the second message is logged.
   */
  public AppLogsWriter(
      ResponseAPIData genericResponse,
      long maxBytesToFlush,
      int maxLogMessageLength,
      int maxFlushSeconds) {
    this.genericResponse = genericResponse;
    this.maxSecondsBetweenFlush = maxFlushSeconds;

    if (maxLogMessageLength < MIN_MAX_LOG_MESSAGE_LENGTH) {
      String message =
          String.format(
              "maxLogMessageLength sillily small (%s); setting maxLogMessageLength to %s",
              maxLogMessageLength,
              MIN_MAX_LOG_MESSAGE_LENGTH);
      logger.atWarning().log("%s", message);
      this.maxLogMessageLength = MIN_MAX_LOG_MESSAGE_LENGTH;
    } else {
      this.maxLogMessageLength = maxLogMessageLength;
    }
    logCutLength = maxLogMessageLength - LOG_CONTINUATION_SUFFIX_LENGTH;
    logCutLengthDiv10 = logCutLength / 10;

    // This should never happen, but putting here just in case.
    if (maxBytesToFlush < this.maxLogMessageLength) {
      String message =
          String.format(
              "maxBytesToFlush (%s) smaller than  maxLogMessageLength (%s)",
              maxBytesToFlush,
              this.maxLogMessageLength);
      logger.atWarning().log("%s", message);
      this.maxBytesToFlush = this.maxLogMessageLength;
    } else {
      this.maxBytesToFlush = maxBytesToFlush;
    }

    // Always have a stopwatch even if we're not doing time based flushing
    // to keep code a bit simpler
    stopwatch = Stopwatch.createUnstarted();
  }

  /**
   * Add the specified LogRecord for the current request.  If
   * enough space (or in the future, time) has accumulated, an
   * asynchronous flush may be started.  If flushes are backed up,
   * this method may block.
   */
  public void addLogRecordAndMaybeFlush(ApiProxy.LogRecord fullRecord) {
    List<AppLogLine> appLogLines = new ArrayList<>();

    // Convert the ApiProxy.LogRecord into AppLogLine protos.
    for (ApiProxy.LogRecord record : split(fullRecord)) {
      AppLogLine.Builder logLineBuilder = AppLogLine.newBuilder()
          .setLevel(record.getLevel().ordinal())
          .setTimestampUsec(record.getTimestamp())
          .setMessage(record.getMessage());

      appLogLines.add(logLineBuilder.build());
    }

    synchronized (lock) {
      addLogLinesAndMaybeFlush(appLogLines);
    }
  }

  @GuardedBy("lock")
  private void addLogLinesAndMaybeFlush(Iterable<AppLogLine> appLogLines) {
    for (AppLogLine logLine : appLogLines) {
      int serializedSize = logLine.getSerializedSize();

      if (maxBytesToFlush > 0 && (currentByteCount + serializedSize) > maxBytesToFlush) {
        logger.atInfo().log("%d bytes of app logs pending, starting flush...", currentByteCount);
        waitForCurrentFlushAndStartNewFlush();
      }
      if (!stopwatch.isRunning()) {
        // We only want to flush once a log message has been around for
        // longer than maxSecondsBetweenFlush. So, we only start the timer
        // when we add the first message so we don't include time when
        // the queue is empty.
        stopwatch.start();
      }
      genericResponse.addAppLog(logLine);
      currentByteCount += serializedSize;
    }

    if (maxSecondsBetweenFlush > 0
        && stopwatch.elapsed().compareTo(Duration.ofSeconds(maxSecondsBetweenFlush)) >= 0) {
      waitForCurrentFlushAndStartNewFlush();
    }
  }

  /**
   * Starts an asynchronous flush.  This method may block if flushes
   * are backed up.
   */
  @GuardedBy("lock")
  private void waitForCurrentFlushAndStartNewFlush() {
    waitForCurrentFlush();
    if (genericResponse.getAppLogCount() > 0) {
      currentFlush = doFlush();
    }
  }

  /**
   * Initiates a synchronous flush.  This method will always block
   * until any pending flushes and its own flush completes.
   */
  public void flushAndWait() {
    Future<byte[]> flush = null;

    synchronized (lock) {
      waitForCurrentFlush();
      if (genericResponse.getAppLogCount() > 0) {
        flush = currentFlush = doFlush();
      }
    }

    // Wait for this flush outside the synchronized block to avoid unnecessarily blocking
    // addLogRecordAndMaybeFlush() calls when flushes are not backed up.
    if (flush != null) {
      waitForFlush(flush);
    }
  }

  /**
   * This method blocks until any outstanding flush is completed. This method
   * should be called prior to {@link #doFlush()} so that it is impossible for
   * the appserver to process logs out of order.
   */
  @GuardedBy("lock")
  private void waitForCurrentFlush() {
    if (currentFlush != null && !currentFlush.isDone() && !currentFlush.isCancelled()) {
      logger.atInfo().log("Previous flush has not yet completed, blocking.");
      waitForFlush(currentFlush);
    }
    currentFlush = null;
  }

  private void waitForFlush(Future<byte[]> flush) {
    try {
      flush.get();
    } catch (InterruptedException ex) {
      logger.atWarning().log(
          "Interrupted while blocking on a log flush, setting interrupt bit and "
              + "continuing.  Some logs may be lost or occur out of order!");
      Thread.currentThread().interrupt();
    } catch (ExecutionException ex) {
      logger.atWarning().withCause(ex).log(
          "A log flush request failed.  Log messages may have been lost!");
    }
  }

  @GuardedBy("lock")
  private Future<byte[]> doFlush() {
    AppLogGroup.Builder group = AppLogGroup.newBuilder();
    for (AppLogLine logLine : genericResponse.getAndClearAppLogList()) {
      group.addLogLine(logLine);
    }
    currentByteCount = 0;
    stopwatch.reset();
    FlushRequest request = FlushRequest.newBuilder().setLogs(group.build().toByteString()).build();
    // This assumes that we are always doing a flush from the request
    // thread.  See the TODO above.
    return ApiProxy.makeAsyncCall("logservice", "Flush", request.toByteArray());
  }

  /**
   * Because the App Server will truncate log messages that are too
   * long, we want to split long log messages into multiple messages.
   * This method returns a {@link List} of {@code LogRecord}s, each of
   * which have the same {@link ApiProxy.LogRecord#getLevel()} and
   * {@link ApiProxy.LogRecord#getTimestamp()} as
   * this one, and whose {@link ApiProxy.LogRecord#getMessage()} is short enough
   * that it will not be truncated by the App Server. If the
   * {@code message} of this {@code LogRecord} is short enough, the list
   * will contain only this  {@code LogRecord}. Otherwise the list will
   * contain multiple {@code LogRecord}s each of which contain a portion
   * of the {@code message}. Additionally, strings will be
   * prepended and appended to each of the {@code message}s indicating
   * that the message is continued in the following log message or is a
   * continuation of the previous log mesage.
   */
  @VisibleForTesting
  List<ApiProxy.LogRecord> split(ApiProxy.LogRecord aRecord) {
    String message = aRecord.getMessage();
    if (null == message || message.length() <= maxLogMessageLength) {
      return ImmutableList.of(aRecord);
    }
    List<ApiProxy.LogRecord> theList = new ArrayList<>();
    String remaining = message;
    while (remaining.length() > 0) {
      String nextMessage;
      if (remaining.length() <= maxLogMessageLength) {
        nextMessage = remaining;
        remaining = "";
      } else {
        int cutLength = logCutLength;
        boolean cutAtNewline = false;
        // Try to cut the string at a friendly point
        int friendlyCutLength = remaining.lastIndexOf('\n', logCutLength);
        // But only if that yields a message of reasonable length
        if (friendlyCutLength > logCutLengthDiv10) {
          cutLength = friendlyCutLength;
          cutAtNewline = true;
        } else if (Character.isHighSurrogate(remaining.charAt(cutLength - 1))) {
          // We're not cutting at a newline, so make sure we're not splitting a surrogate pair.
          --cutLength;
        }
        nextMessage = remaining.substring(0, cutLength) + LOG_CONTINUATION_SUFFIX;
        remaining = remaining.substring(cutLength + (cutAtNewline ? 1 : 0));
        // Only prepend the continuation prefix if doing so would not push
        // the length of the next message over the limit.
        if (remaining.length() > maxLogMessageLength
            || remaining.length() + LOG_CONTINUATION_PREFIX_LENGTH <= maxLogMessageLength) {
          remaining = LOG_CONTINUATION_PREFIX + remaining;
        }
      }
      theList.add(new ApiProxy.LogRecord(aRecord, nextMessage));
    }
    return ImmutableList.copyOf(theList);
  }

  /**
   * Sets the stopwatch used for time based flushing.
   *
   * This method is not simply visible for testing, it only exists for testing.
   *
   * @param stopwatch The {@link Stopwatch} instance to use.
   */
  @VisibleForTesting
  void setStopwatch(Stopwatch stopwatch) {
    synchronized (lock) {
      this.stopwatch = stopwatch;
    }
  }

  /**
   * Get the max length of an individual log message.
   *
   * This method is not simply visible for testing, it only exists for testing.
   */
  @VisibleForTesting
  int getMaxLogMessageLength() {
    return maxLogMessageLength;
  }

  /**
   * Get the maximum number of log bytes that can be sent at a single time.
   *
   * This code is not simply visible for testing, it only exists for testing.
   */
  @VisibleForTesting
  long getByteCountBeforeFlushing() {
    return maxBytesToFlush;
  }

  /**
   * Converts from a Java Logging level to an App Engine logging level.
   * SEVERE maps to error, WARNING to warn, INFO to info, and all
   * lower levels to debug.  We reserve the fatal level for exceptions
   * that propagated outside of user code and forced us to kill the
   * request.
   */
  public static ApiProxy.LogRecord.Level convertLogLevel(Level level) {
    long intLevel = level.intValue();

    if (intLevel >= Level.SEVERE.intValue()) {
      return ApiProxy.LogRecord.Level.error;
    } else if (intLevel >= Level.WARNING.intValue()) {
      return ApiProxy.LogRecord.Level.warn;
    } else if (intLevel >= Level.INFO.intValue()) {
      return ApiProxy.LogRecord.Level.info;
    } else {
      // There's no trace, so we'll map everything below this to
      // debug.
      return ApiProxy.LogRecord.Level.debug;
    }
  }

}
