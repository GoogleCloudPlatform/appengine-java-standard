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

package com.google.apphosting.api;

import static com.google.apphosting.api.ApiProxy.MAX_SAVED_LOG_RECORDS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.apphosting.api.ApiProxy.Delegate;
import com.google.apphosting.api.ApiProxy.Environment;
import com.google.apphosting.api.ApiProxy.LogRecord;
import com.google.apphosting.api.ApiProxy.LogRecord.Level;
import java.util.Collections;
import java.util.List;
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

@RunWith(JUnit4.class)
public class ApiProxyTest {
  private LogRecord record;
  @Mock private Delegate<Environment> delegate;

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    ApiProxy.setDelegate(delegate);
    ApiProxy.clearEnvironmentForCurrentThread();
    record = new LogRecord(Level.warn, 0, "log message here");
  }

  @After
  public void tearDown() {
    ApiProxy.clearEnvironmentForCurrentThread();
  }

  @Test
  public void deferredLog() {
    assertThat(ApiProxy.getCurrentEnvironment()).isNull();

    ApiProxy.log(record); // record should be stashed away and logged later.
    // Verify that no log was sent anywhere.
    verify(delegate, never()).log(any(), any());

    // Once the environment and delegates are set, we should see the one log call.
    Environment environment = mock(Environment.class);
    ApiProxy.setEnvironmentForCurrentThread(environment);
    verify(delegate).log(environment, record);

    reset(delegate);
    // This triggers the check for delayed logs - make sure we don't send the same logs twice.
    ApiProxy.clearEnvironmentForCurrentThread();
    ApiProxy.setEnvironmentForCurrentThread(environment);
    verify(delegate, never()).log(any(), any());
  }

  @Test
  public void deferredLogIsLimitedInSize() {
    assertThat(ApiProxy.getCurrentEnvironment()).isNull();

    // Try logging a large number of records with no Environment. They will be buffered in the
    // expectation that the Environment will be added later. When it is, they will all be returned
    // at once, but some of them will have been dropped. We expect that the first chunk and the last
    // chunk will be saved, and the ones in between will be replaced by a single LogRecord that
    // tells how many were dropped.
    assertThat(MAX_SAVED_LOG_RECORDS).isLessThan(10_000);
    for (int i = 0; i < 10_000; i++) {
      LogRecord recordI = new LogRecord(Level.warn, 0, "message " + i);
      ApiProxy.log(recordI);
    }
    verify(delegate, never()).log(any(), any());

    // Add the environment, which should cause the saved records to be logged now, except the ones
    // that were dropped.
    Environment environment = mock(Environment.class);
    ApiProxy.setEnvironmentForCurrentThread(environment);
    ArgumentCaptor<LogRecord> captor = ArgumentCaptor.forClass(LogRecord.class);
    verify(delegate, times(MAX_SAVED_LOG_RECORDS + 1)).log(same(environment), captor.capture());
    List<LogRecord> records = captor.getAllValues();
    assertThat(records).hasSize(MAX_SAVED_LOG_RECORDS + 1);
    for (int i = 0; i < MAX_SAVED_LOG_RECORDS / 2; i++) {
      LogRecord recordI = records.get(i);
      assertThat(recordI.getMessage()).isEqualTo("message " + i);
    }
    LogRecord dropped = records.get(MAX_SAVED_LOG_RECORDS / 2);
    int expectedDroppedCount = 10_000 - MAX_SAVED_LOG_RECORDS;
    String expectedMessage =
        "[" + expectedDroppedCount + " dropped records were logged between requests]";
    assertThat(dropped.getMessage()).isEqualTo(expectedMessage);
    records.subList(0, MAX_SAVED_LOG_RECORDS + 1).clear();
    Collections.reverse(records);
    for (int i = 0; i < records.size(); i++) {
      LogRecord recordI = records.get(i);
      assertThat(recordI.getMessage()).isEqualTo("message " + (9999 - i));
    }

    // Now we should be able to log normally, because we have an Environment.
    ApiProxy.log(record);
    verify(delegate).log(environment, record);
  }

  @Test
  public void immediateLog() {
    Environment environment = mock(Environment.class);
    ApiProxy.setEnvironmentForCurrentThread(environment);
    ApiProxy.log(record);
    verify(delegate).log(environment, record);
  }

  @Test
  public void environmentFactory() {
    // Set up the factory to return a default environment and verify it works.
    Environment environment = mock(Environment.class);
    ApiProxy.setEnvironmentFactory(() -> environment);
    assertThat(ApiProxy.getCurrentEnvironment()).isSameInstanceAs(environment);

    assertThrows(NullPointerException.class, () -> ApiProxy.setEnvironmentFactory(null));

    // Can only be set once.
    assertThrows(IllegalStateException.class, () -> ApiProxy.setEnvironmentFactory(() -> null));

    // Can be set again if cleared first.
    Environment environment2 = mock(Environment.class);
    ApiProxy.clearEnvironmentFactory();
    ApiProxy.setEnvironmentFactory(() -> environment2);
    // Old environment still present on thread.
    assertThat(ApiProxy.getCurrentEnvironment()).isSameInstanceAs(environment);
    // Clearing it and accessing it again causes the factory to be invoked.
    ApiProxy.clearEnvironmentForCurrentThread();
    assertThat(ApiProxy.getCurrentEnvironment()).isSameInstanceAs(environment2);
  }
}
