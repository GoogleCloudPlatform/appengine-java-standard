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
import java.util.logging.ErrorManager;
import java.util.logging.LogRecord;

/**
 * {@code NullSandboxLogHandler} is installed on the root logger. It forwards all messages on to
 * {@code ApiProxy.log(ApiProxy.LogRecord)}, where they can be attached to the runtime response.
 *
 */
public final class NullSandboxLogHandler extends LogHandler {

  @Override
  public void publish(LogRecord record) {
    if (isLoggable(record)) {
      // The formatter isn't necessarily thread-safe, so we synchronize around it.
      String message = null;
      Exception exception = null;
      synchronized (this) {
        try {
          message = getFormatter().format(record);
        } catch (Exception ex) {
          exception = ex;
        }
      }
      // We don't want to throw an exception here, but we
      // report the exception to any registered ErrorManager.
      // This has to be done outside of the synchronized block to avoid deadlocks.
      if (exception != null) {
        reportError(null, exception, ErrorManager.FORMAT_FAILURE);
        return;
      }

      // message will always be non-null when exception is null
      if (message != null) {
        ApiProxy.log(convertLogRecord(record, message));
      }
    }
  }

  private ApiProxy.LogRecord convertLogRecord(LogRecord record, String message) {
    ApiProxy.LogRecord.Level level = AppLogsWriter.convertLogLevel(record.getLevel());
    long timestamp = record.getMillis() * 1000;
    return new ApiProxy.LogRecord(level, timestamp, message);
  }

  @Override
  public void flush() {
    ApiProxy.flushLogs();
  }

  @Override
  public void close() {
    flush();
  }
}
