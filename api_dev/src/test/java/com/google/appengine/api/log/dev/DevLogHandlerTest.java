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

package com.google.appengine.api.log.dev;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;

import com.google.appengine.api.testing.MockEnvironment;
import com.google.appengine.tools.development.LocalEnvironment;
import com.google.apphosting.api.ApiProxy;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class DevLogHandlerTest {
  // The maximum amount of useconds that we're okay with experiencing
  // between when we get the current time at the start of a test and
  // when the logging call gets the current time.
  //
  // It's really unlikely to actually take a full 1/4 of a second but that
  // is accurate enough for this test and makes it very unlikely for
  // unlucky scheduling to cause flakiness.
  //
  // TODO - mock out the static System.currentTimeMillis() so
  // we don't have to worry about the clock advancing.
  private static long allowedDelayUsecs = 250000;

  @Mock private LocalLogService mockLocalLogService;

  private MockEnvironment mockEnvironment;
  private DevLogHandler logHandler;
  private Logger logger;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    mockEnvironment = new MockEnvironment("", "");
    ApiProxy.setEnvironmentForCurrentThread(mockEnvironment);

    logHandler = new DevLogHandler(mockLocalLogService);

    logger = Logger.getLogger("DevLogHandlerTest");
    logger.addHandler(logHandler);
  }

  @After
  public void tearDown() {
    logger.removeHandler(logHandler);
  }

  private void setRequestId(String requestId) {
    mockEnvironment.getAttributes().put(LocalEnvironment.REQUEST_ID, requestId);
  }

  @Test
  public void testRequestId() {
    String requestId = "request-id-123";
    setRequestId(requestId);

    logger.info("whatever");
    verify(mockLocalLogService).addAppLogLine(eq(requestId), anyLong(), anyInt(), notNull());
  }

  @Test
  public void testTime() {
    long currentTimeUsecs = System.currentTimeMillis() * 1000;

    logger.info("whatever");
    verify(mockLocalLogService)
        .addAppLogLine(
            notNull(),
            longThat(t -> currentTimeUsecs <= t && t < currentTimeUsecs + allowedDelayUsecs),
            anyInt(),
            notNull());
  }

  @Test
  public void testLevelInfo() {
    logger.info("whatever");
    verify(mockLocalLogService).addAppLogLine(notNull(), anyLong(), eq(2), notNull());
  }

  @Test
  public void testLevelWarning() {
    logger.warning("whatever");
    verify(mockLocalLogService).addAppLogLine(notNull(), anyLong(), eq(3), notNull());
  }

  @Test
  public void testMessage() {
    logger.warning("Don't push me cause I'm close to the edge");
    verify(mockLocalLogService)
        .addAppLogLine(
            notNull(), anyLong(), anyInt(), eq("Don't push me cause I'm close to the edge"));
  }
}
