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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import java.io.PrintStream;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;

/** A log handler that publishes log messages in a json format. */
public final class JsonLogHandler extends LogHandler {
  private static final String TRACE_KEY = "\"logging.googleapis.com/trace\": ";
  private static final String SOURCE_LOCATION_KEY = "\"logging.googleapis.com/sourceLocation\": ";
  private static final String SPAN_KEY = "\"logging.googleapis.com/spanId\": ";
  private static final String DEBUG = "DEBUG";
  private static final String INFO = "INFO";
  private static final String WARNING = "WARNING";
  private static final String ERROR = "ERROR";
  private static final String DEFAULT = "DEFAULT";

  static final Escaper ESCAPER =
      Escapers.builder()
          .addEscape('"', "\\\"")
          .addEscape('\\', "\\\\")
          .addEscape('\n', "\\n")
          .addEscape('\r', "\\r")
          .addEscape('\t', "\\t")
          .build();

  private final PrintStream out;
  private final boolean closePrintStreamOnClose;
  @Nullable private final String projectId;
  private final Formatter formatter;

  public JsonLogHandler(
      PrintStream out,
      boolean closePrintStreamOnClose,
      @Nullable String projectId,
      Formatter formatter) {
    this.out = out;
    this.closePrintStreamOnClose = closePrintStreamOnClose;
    this.projectId = projectId;
    this.formatter = checkNotNull(formatter);
  }

  @Override
  public void publish(LogRecord record) {
    // We avoid String.format and String.join even though they would simplify the code.
    // Logging code often shows up in profiling so we want to make this fast and StringBuilder is
    // more performant.
    StringBuilder json = new StringBuilder("{");
    appendTraceId(json);
    appendSpanId(json);
    appendSeverity(json, record);
    appendSourceLocation(json, record);
    appendMessage(json, record); // must be last, see appendMessage
    json.append("}");
    // We must output the log all at once (should only call println once per call to publish)
    out.println(json.toString());
  }

  private static void appendSpanId(StringBuilder json) {
    Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment instanceof ApiProxy.EnvironmentWithTrace) {
      ApiProxy.EnvironmentWithTrace environmentWithTrace =
          (ApiProxy.EnvironmentWithTrace) environment;
      environmentWithTrace
          .getSpanId()
          .ifPresent(id -> json.append(SPAN_KEY).append("\"").append(id).append("\", "));
    }
  }

  private void appendMessage(StringBuilder json, LogRecord record) {
    String message = formatter.formatMessage(record);
    if (message == null) {
      message = "";
    }

    // This must be the last item in the JSON object, because it has no trailing comma. JSON is
    // unforgiving about commas and you can't have one just before }.
    json.append("\"message\": \"").append(ESCAPER.escape(message));
    if (record.getThrown() != null) {
      json.append("\\n")
          .append(ESCAPER.escape(Throwables.getStackTraceAsString(record.getThrown())));
    }
    json.append("\"");
  }

  private void appendTraceId(StringBuilder json) {
    if (Strings.isNullOrEmpty(projectId)) {
      return;
    }

    Environment environment = ApiProxy.getCurrentEnvironment();
    if (environment instanceof ApiProxy.EnvironmentWithTrace) {
      ApiProxy.EnvironmentWithTrace environmentWithTrace =
          (ApiProxy.EnvironmentWithTrace) environment;
      environmentWithTrace
          .getTraceId()
          .ifPresent(
              id ->
                  json.append(TRACE_KEY)
                      .append("\"projects/")
                      .append(projectId)
                      .append("/traces/")
                      .append(id)
                      .append("\", "));
    }
  }

  private static void appendSeverity(StringBuilder json, LogRecord record) {
    json.append("\"severity\": \"").append(levelToSeverity(record.getLevel())).append("\", ");
  }

  private static void appendSourceLocation(StringBuilder json, LogRecord record) {
    if (record.getSourceClassName() != null && record.getSourceMethodName() != null) {
      StackTraceElement stackFrame =
          AppLogsWriter.findStackFrame(
              record.getSourceClassName(), record.getSourceMethodName(), new Throwable());
      String function = '"' + stackFrame.getClassName() + "." + stackFrame.getMethodName() + '"';
      json.append(SOURCE_LOCATION_KEY)
          .append('{')
          .append("\"function\": ")
          .append(function)
          .append(", \"file\": \"")
          .append(stackFrame.getFileName())
          .append("\", \"line\": \"")
          .append(stackFrame.getLineNumber())
          .append("\"}, ");
    }
  }

  private static String levelToSeverity(Level level) {
    int intLevel = (level == null) ? 0 : level.intValue();
    switch (intLevel) {
      case 300: // FINEST
      case 400: // FINER
      case 500: // FINE
        return DEBUG;
      case 700: // CONFIG
      case 800: // INFO
        // Java's CONFIG is lower than its INFO, while Stackdriver's NOTICE is greater than its
        // INFO. So despite the similarity, we don't try to use NOTICE for CONFIG.
        return INFO;
      case 900: // WARNING
        return WARNING;
      case 1000: // SEVERE
        return ERROR;
      default:
        return DEFAULT;
    }
  }

  @Override
  public void flush() {
    out.flush();
  }

  @Override
  public void close() throws SecurityException {
    if (closePrintStreamOnClose) {
      out.close();
    }
  }
}
