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

import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.base.protos.RuntimePb.UPResponse.RuntimeLogLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@code RuntimeLogSink} attaches a root {@link Handler} that records all log messages {@code
 * Level.INFO} or higher as a {@link RuntimeLogLine} attached to the current {@link UPResponse}.
 *
 * <p>TODO: This class is designed to be used in a single-threaded runtime. If multiple requests are
 * executing in a single process in parallel, their messages will currently overlap. If we want to
 * support this configuration in the future we should do something slightly smarter here (however,
 * we don't want to limit logs to only the thread serving the request).
 */
public class RuntimeLogSink {
  private static final Logger rootLogger = Logger.getLogger("");

  // Use the same format used by google3 C++ logging.
  private static final DateFormat DATE_FORMAT = new SimpleDateFormat("MMdd HH:mm:ss.SSS");

  private final Collection<RuntimeLogLine> pendingLogLines = new ArrayList<RuntimeLogLine>();
  // We keep for the current request the test of the record and its timestamp.
  // and the following map is used to only display a reference to the timestamp instead
  // of the content of the log record for duplicated exception records to save space.
  private final HashMap<String, String> mapExceptionDate = new HashMap<String, String>();
  private final long maxSizeBytes;
  private long currentSizeBytes = 0;

  public RuntimeLogSink(long maxSizeBytes) {
    this.maxSizeBytes = maxSizeBytes;
  }

  public synchronized void addHandlerToRootLogger() {
    RuntimeLogHandler handler = new RuntimeLogHandler();
    rootLogger.addHandler(handler);
  }

  synchronized void addLog(RuntimeLogLine logLine) {
    pendingLogLines.add(logLine);
  }

  public synchronized void flushLogs(GenericResponse response) {
    response.addAllRuntimeLogLine(pendingLogLines);
    pendingLogLines.clear();
    mapExceptionDate.clear();
    currentSizeBytes = 0;
  }

  boolean maxSizeReached() {
    return currentSizeBytes >= maxSizeBytes;
  }

  class RuntimeLogHandler extends Handler {
    RuntimeLogHandler() {
      setLevel(Level.INFO);
      setFilter(null);
      setFormatter(new CustomFormatter());
    }

    @Override
    public void publish(LogRecord record) {
      if (!isLoggable(record)) {
        return;
      }
      if (maxSizeReached()) {
        return;
      }

      String message;
      try {
        message = getFormatter().format(record);
      } catch (Exception ex) {
        // We don't want to throw an exception here, but we
        // report the exception to any registered ErrorManager.
        reportError(null, ex, ErrorManager.FORMAT_FAILURE);
        return;
      }
      currentSizeBytes += 2L * message.length();
      if (maxSizeReached()) {
        // It's OK to overflow the max with the size of this extra
        // message.
        // TODO work with the appserver team to autoflush the log
        // instead, or switch to a compact format when we're running low
        // on space.
        message = "Maximum runtime log size reached: " + maxSizeBytes;
      }
      RuntimeLogLine logLine = RuntimeLogLine.newBuilder()
          .setSeverity(convertSeverity(record.getLevel()))
          .setMessage(message)
          .build();
      addLog(logLine);
    }

    @Override
    public void flush() {
      // Nothing to do.
    }

    @Override
    public void close() {
      flush();
    }

    /**
     * Convert from {@link Level} to the integer constants defined in //base/log_severity.h
     */
    private int convertSeverity(Level level) {
      if (level.intValue() >= Level.SEVERE.intValue()) {
        return 2; // ERROR
      } else if (level.intValue() >= Level.WARNING.intValue()) {
        return 1; // WARNING
      } else {
        return 0; // INFO
      }
    }
  }

  private final class CustomFormatter extends Formatter {
    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    @Override
    public synchronized String format(LogRecord record) {
      StringBuilder sb = new StringBuilder();
      String date;
      synchronized (DATE_FORMAT) {
        // SimpleDateFormat is not threadsafe.  If we multithread the
        // runtime this may become a bottleneck, but at the moment
        // this lock will be uncontended and therefore very cheap.
        date = DATE_FORMAT.format(new Date());
      }
      sb.append(date);
      sb.append(": ");
      if (record.getSourceClassName() != null) {
        sb.append(record.getSourceClassName());
      } else {
        sb.append(record.getLoggerName());
      }
      if (record.getSourceMethodName() != null) {
        sb.append(" ");
        sb.append(record.getSourceMethodName());
      }
      sb.append(": ");
      String message = formatMessage(record);
      sb.append(message);
      sb.append("\n");
      if (record.getThrown() != null) {
        // See <internal19>
        // The log line is going to be truncated to some Kb by the App Server anyway.
        // We could be smart here to truncate as well, but this is an edge case.
        try {
          StringWriter sw = new StringWriter();
          try (PrintWriter pw = new PrintWriter(sw)) {
            record.getThrown().printStackTrace(pw);
          }
          String exceptionText = sw.toString();
          if (mapExceptionDate.containsKey(exceptionText)) {
            sb.append("See duplicated exception at date: " + mapExceptionDate.get(exceptionText));
          } else {
            sb.append(exceptionText);
            mapExceptionDate.put(exceptionText, date);
          }
        } catch (Exception ex) {
        }
      }
      return sb.toString();
    }
  }
}
