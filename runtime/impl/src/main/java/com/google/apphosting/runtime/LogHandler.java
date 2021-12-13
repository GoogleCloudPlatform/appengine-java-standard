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

import com.google.common.base.Throwables;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@code LogHandler} is installed on the root logger. This parent class will filter all messages
 * specific to the runtime so they do not get sent to the customer. This class is meant to be
 * inherited to handle the filtered log messages appropriately.
 *
 */
public abstract class LogHandler extends Handler {
  private static final String[] RUNTIME_LOGGERS = {
      "com.google.apphosting.runtime",
      "com.google.common.stats",
      "com.google.net"
  };
  private static final List<Logger> runtimeLoggers = new ArrayList<>();

  LogHandler() {
    setLevel(Level.FINEST);
    setFilter(new ApiProxyLogFilter());
    setFormatter(new CustomFormatter());
  }

  /**
   * Initialize the {@code LogHandler} by installing it on the root logger. After this call, log
   * messages specific to the runtime will be filtered out from being sent to the customer.
   */
  public void init(Logger rootLogger) {
    Formatter fastFormatter = new FastFormatter();
    for (Handler handler : rootLogger.getHandlers()) {
      rootLogger.removeHandler(handler);
      for (String name : RUNTIME_LOGGERS) {
        Logger logger = Logger.getLogger(name);
        logger.addHandler(handler);
        runtimeLoggers.add(logger);
        // Make sure we keep a strong reference to this modified Logger, because otherwise it
        // could be GC'd and we'd lose our handler.
      }
      handler.setFormatter(fastFormatter);
    }
    for (String name : RUNTIME_LOGGERS) {
      Logger logger = Logger.getLogger(name);
      logger.setUseParentHandlers(false);
    }
    rootLogger.addHandler(this);
  }

  @Override
  public abstract void publish(LogRecord record);

  @Override
  public abstract void flush();

  @Override
  public abstract void close();

  /**
   * Filter used to exclude log entries that are private to the runtime.
   */
  private static final class ApiProxyLogFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
      String name = record.getLoggerName();
      if (name == null) {
        // Allow anonymous logger
        return true;
      }
      if (name.startsWith("com.google.apphosting.runtime.")) {
        return false;
      }
      if (name.startsWith("com.google.net.")
          || name.startsWith("com.google.common.stats.")
          || name.startsWith("io.netty.")
          || name.startsWith("io.grpc.netty.")) {
        return false;
      }
      return true;
    }

  }

  // See CustomFormatter in
  // java/com/google/apphosting/runtime/security/shared/intercept/java/util/logging/DefaultHandler.java.
  private static final class CustomFormatter extends Formatter {
    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    @Override
    public synchronized String format(LogRecord record) {
      StringBuilder sb = new StringBuilder();
      if (record.getSourceClassName() != null) {
        sb.append(record.getSourceClassName());
      } else if (record.getLoggerName() != null) {
        sb.append(record.getLoggerName());
      }
      if (record.getSourceMethodName() != null) {
        sb.append(" ");
        sb.append(record.getSourceMethodName());
      }
      sb.append(": ");
      sb.append(formatMessage(record));
      sb.append("\n");
      if (record.getThrown() != null) {
        sb.append(Throwables.getStackTraceAsString(record.getThrown()));
      }
      return sb.toString();
    }
  }

  // Formatter for our internal log messages. This doesn't have to conform to any existing layout,
  // so we choose one that can be computed quickly, much more so than the JDK's SimpleFormatter.
  private static final class FastFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
      Instant instant = Instant.ofEpochMilli(record.getMillis());
      ZonedDateTime time = instant.atZone(ZoneId.systemDefault());
      StringBuilder sb = new StringBuilder();
      sb.append(record.getLevel().getName().charAt(0));
      sb.append(' ');
      appendTwoDigits(sb, time.getHour());
      sb.append(':');
      appendTwoDigits(sb, time.getMinute());
      sb.append(':');
      appendTwoDigits(sb, time.getSecond());
      sb.append(' ');
      if (record.getSourceClassName() != null) {
        sb.append(record.getSourceClassName());
        if (record.getSourceMethodName() != null) {
          sb.append(" ").append(record.getSourceMethodName());
        }
      } else {
        sb.append(record.getLoggerName());
      }
      sb.append(' ');
      Object[] params = record.getParameters();
      String message =
          (params == null || params.length == 0)
              ? record.getMessage()
              : MessageFormat.format(record.getMessage(), params);
      sb.append(message);
      sb.append('\n');
      if (record.getThrown() != null) {
        sb.append(Throwables.getStackTraceAsString(record.getThrown()));
      }
      return sb.toString();
    }

    private static void appendTwoDigits(StringBuilder sb, int value) {
      if (value < 10) {
        sb.append('0');
      }
      sb.append(value);
    }
  }
}
