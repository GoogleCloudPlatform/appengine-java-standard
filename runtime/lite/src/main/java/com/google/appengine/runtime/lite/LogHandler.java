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

import static com.google.common.base.Throwables.getStackTraceAsString;

import com.google.apphosting.api.ApiProxy;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LoggingHandler;
import com.google.cloud.logging.LoggingOptions;
import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A customization on c.g.cloud.logging.LoggingHandler with small improvements for the Lite App
 * Engine runtime.
 */
public class LogHandler extends LoggingHandler {

  static final String PROJECT_ID = System.getenv("GOOGLE_CLOUD_PROJECT");

  static final String EXCEPTION_LABEL = "exception";
  static final String THREAD_ID_LABEL = "threadId";
  static final String THREAD_NAME_LABEL = "threadName";

  static class PayloadFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
      StringBuilder buf = new StringBuilder();
      buf.append(formatMessage(record));

      Throwable thrown = record.getThrown();
      if (thrown != null) {
        buf.append('\n').append(getStackTraceAsString(thrown));
      }
      return buf.toString();
    }
  }

  public LogHandler() {
    this(/*options=*/ null);
  }

  public LogHandler(LoggingOptions options) {
    super(/*log=*/ null, options);

    // Lite runtime apps should always log to stderr:
    setLogTarget(LogTarget.STDERR);

    // c.g.cloud.logging.LoggingHandler sets its own level to INFO by default.
    // This is unlike the default java.util.logging.Loghandler which sets its level to ALL, and it's
    // redundant with the filtering done in the Logger class.
    // For comformity with usual JUL log handlers, and because generally this log handler is the
    // only one installed, we should emit all logs which make it through the Logger's filters:
    setLevel(Level.ALL);

    // c.g.cloud.logging.LoggingHandler uses a SimpleFormatter by default.
    // This will typically result in a lot of data in the payload which is redundant with the
    // structured log fields. Instead use an even simpler formatter which only records the things
    // which aren't recorded elsewhere in the LogEntry object:
    setFormatter(new PayloadFormatter());
  }

  // We override logEntryFor instead of using c.g.cloud.logging.LoggingEnhancer implementations
  // because we need access to the LogRecord.
  // See https://github.com/googleapis/java-logging/issues/32.
  @SuppressWarnings(
      "deprecation") // LogContext shouldn't be used for code logging with flogger. However, this
  // code is the backend for flogger.
  @Override
  protected LogEntry.Builder logEntryFor(LogRecord record) {
    LogEntry.Builder builder = super.logEntryFor(record);

    // Get trace context from the special App Engine SDK location:
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment instanceof ApiProxy.EnvironmentWithTrace) {
      ApiProxy.EnvironmentWithTrace environmentImpl = (ApiProxy.EnvironmentWithTrace) environment;
      environmentImpl
          .getTraceId()
          .ifPresent(x -> builder.setTrace("projects/" + PROJECT_ID + "/traces/" + x));
      environmentImpl.getSpanId().ifPresent(builder::setSpanId);
    }

    // Set some extra thread information:
    builder.addLabel(THREAD_ID_LABEL, Integer.toString(record.getThreadID()));
    builder.addLabel(THREAD_NAME_LABEL, Thread.currentThread().getName());

    // Set some extra exception information:
    Optional.ofNullable(record.getThrown())
        .ifPresent(x -> builder.addLabel(EXCEPTION_LABEL, x.getClass().getName()));

    return builder;
  }
}
