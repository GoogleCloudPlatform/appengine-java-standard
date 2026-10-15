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

package com.google.apphosting.runtime.jetty9;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.LogRecord;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Logger;

/**
 * {@code JettyLogger} is a extension for {@link org.eclipse.jetty.util.log.JavaUtilLog}
 *
 */
public class JettyLogger extends JavaUtilLog {

  private static final boolean LOG_TO_API_PROXY =
      Boolean.getBoolean("appengine.jetty.also_log_to_apiproxy");

  public JettyLogger() {
    this(null);
  }

  public JettyLogger(String name) {
    super("JettyLogger(" + name + ")");
  }

  @Override
  public void warn(String msg, Throwable th) {
    super.warn(msg, th);

    // N.B.: There are a number of cases where Jetty
    // swallows exceptions entirely, or at least stashes them away in
    // private fields.  To avoid these situations, we log all warning
    // exceptions to the user's app logs via ApiProxy, as long as we
    // have an environment set up.
    //
    // Note that we also only do this if there is a Throwable
    // provided.  Jetty logs some things that aren't very useful, and
    // we're really only worried that stack traces are preserved here.
    if (LOG_TO_API_PROXY && ApiProxy.getCurrentEnvironment() != null && th != null) {
      ApiProxy.log(createLogRecord(msg, th));
    }
  }

  /**
   * Create a Child Logger of this Logger.
   */
  @Override
  protected Logger newLogger(String name) {
    return new JettyLogger(name);
  }

  @Override
  public String toString() {
    return getName();
  }

  private LogRecord createLogRecord(String message, Throwable ex) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    printWriter.println(message);
    if (ex != null) {
      ex.printStackTrace(printWriter);
    }

    return new LogRecord(
        LogRecord.Level.warn, System.currentTimeMillis() * 1000, stringWriter.toString());
  }
}
