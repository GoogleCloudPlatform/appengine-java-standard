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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LoggingTest {
  @Rule public TestName testName = new TestName();

  @Test
  public void logJsonToFile_defaultFormatter() throws IOException {
    Logger fakeRootLogger = new Logger("", null) {};
    try {
      fakeRootLogger.addHandler(mock(Handler.class)); // add a dummy handler

      File tempLog = File.createTempFile("LoggingTest", ".log");
      tempLog.deleteOnExit();
      Logging logging = new Logging(fakeRootLogger);
      logging.logJsonToFile("projectId", tempLog.toPath(), /* clearLogHandlers= */ true);

      // The above clears the dummy handler and adds the JSON handler:
      assertThat(fakeRootLogger.getHandlers()).hasLength(1);

      fakeRootLogger.log(
          Level.INFO, "Message with parameters {0} and {1}", new String[] {"foo", "bar"});
      List<String> logLines = Files.readAllLines(tempLog.toPath());
      assertThat(logLines).hasSize(1);
      String expectedPattern =
          "\\{\"severity\": \"INFO\", \"message\": \"Message"
              + " with parameters foo and bar\"\\}";
      assertThat(logLines.get(0)).matches(expectedPattern);
    } finally {
      for (Handler handler : fakeRootLogger.getHandlers()) {
        handler.close();
      }
    }
  }

  @Test
  public void logJsonToFile_doNotClearHandlers() throws IOException {
    Logger fakeRootLogger = new Logger("", null) {};
    try {
      fakeRootLogger.addHandler(mock(Handler.class)); // add a dummy handler

      File tempLog = File.createTempFile("LoggingTest", ".log");
      tempLog.deleteOnExit();
      Logging logging = new Logging(fakeRootLogger);
      logging.logJsonToFile("projectId", tempLog.toPath(), /* clearLogHandlers= */ false);

      // The above does NOT clears the dummy handler and adds the JSON handler:
      assertThat(fakeRootLogger.getHandlers()).hasLength(2);
    } finally {
      for (Handler handler : fakeRootLogger.getHandlers()) {
        handler.close();
      }
    }
  }
}
