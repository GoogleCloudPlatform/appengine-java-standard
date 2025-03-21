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

package com.google.appengine.api.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LogQuery}. */
@RunWith(JUnit4.class)
public class LogQueryTest {

  private static final ImmutableList<LogQuery.Version> VERSIONS =
      ImmutableList.of(new LogQuery.Version("m1", "v1"), new LogQuery.Version("m2", "v2"));
  private static final ImmutableList<LogQuery.Version> VERSIONS_2 =
      ImmutableList.of(new LogQuery.Version("m3", "v4"));
  private static final ImmutableList<String> MAJOR_VERSION_IDS = ImmutableList.of("v1");
  private static final ImmutableList<String> REQUEST_IDS = ImmutableList.of("1", "2");


  @Before public void setUp() {
  }

  @Test public void getMajorVersionIds() {
    LogQuery query = LogQuery.Builder.withMajorVersionIds(MAJOR_VERSION_IDS);
    assertEquals(MAJOR_VERSION_IDS, query.getMajorVersionIds());
    query.majorVersionIds(Lists.newArrayList("v2"));
    assertEquals(Lists.newArrayList("v2"), query.getMajorVersionIds());
  }

  @Test public void versions() {
    LogQuery query = LogQuery.Builder.withVersions(VERSIONS);
    assertEquals(VERSIONS, query.getVersions());

    // Verify that setting again resets the value.
    query.versions(VERSIONS_2);
    assertEquals(VERSIONS_2, query.getVersions());
  }

  @Test public void versions_badModuleId() {
    try {
      new LogQuery.Version("bad!", "good");
      fail();
    } catch (IllegalArgumentException iae) {
      assertEquals("Invalid module id 'bad!'", iae.getMessage());
    }
  }

  @Test public void versions_badVersionId() {
    try {
      new LogQuery.Version("good", "bad!");
      fail();
    } catch (IllegalArgumentException iae) {
      assertEquals("Invalid version id 'bad!'", iae.getMessage());
    }
  }

  @Test public void versions_thenMajorVersionIds() {
    LogQuery query = LogQuery.Builder.withVersions(VERSIONS);
    try {
      query.majorVersionIds(MAJOR_VERSION_IDS);
      fail();
    } catch (IllegalStateException ise) {
      assertEquals("LogQuery.majorVersionIds may not be called after LogQuery.versions.",
          ise.getMessage());
    }
  }

  @Test public void versions_thenModuleInfos() {
    LogQuery query = LogQuery.Builder.withMajorVersionIds(MAJOR_VERSION_IDS);
    try {
      query.versions(VERSIONS);
      fail();
    } catch (IllegalStateException ise) {
      assertEquals("LogQuery.versions may not be called after LogQuery.majorVersionIds.",
          ise.getMessage());
    }
  }

  @Test public void clone_withVersions() {
    LogQuery query = LogQuery.Builder
        .withVersions(VERSIONS)
        .batchSize(9)
        .endTimeMillis(1389824215000L)
        .startTimeMillis(1387065600000L)
        .includeAppLogs(true)
        .includeIncomplete(true)
        .minLogLevel(LogService.LogLevel.INFO)
        .offset("abc");
    LogQuery clone1 = query.clone();
    assertEquals(VERSIONS, clone1.getVersions());
    assertTrue(queryMatch(query, clone1));
    LogQuery clone2 = clone1.clone();
    assertEquals(VERSIONS, clone2.getVersions());
    assertTrue(queryMatch(clone1, clone2));
  }

  @Test public void clone_withMajorVersionIds() {
    LogQuery query = LogQuery.Builder
        .withMajorVersionIds(MAJOR_VERSION_IDS)
        .batchSize(9)
        .endTimeMillis(1389824215000L)
        .startTimeMillis(1387065600000L)
        .includeAppLogs(true)
        .includeIncomplete(true)
        .minLogLevel(LogService.LogLevel.INFO)
        .offset("abc");
    LogQuery clone1 = query.clone();
    assertEquals(MAJOR_VERSION_IDS, clone1.getMajorVersionIds());
    assertTrue(queryMatch(query, clone1));
    LogQuery clone2 = clone1.clone();
    assertEquals(MAJOR_VERSION_IDS, clone2.getMajorVersionIds());
    assertTrue(queryMatch(clone1, clone2));
  }

  @Test public void clone_withRequestIds() {
    LogQuery query = LogQuery.Builder
        .withRequestIds(REQUEST_IDS)
        .batchSize(9)
        .endTimeMillis(1389824215000L)
        .startTimeMillis(1387065600000L)
        .includeAppLogs(true)
        .includeIncomplete(true)
        .minLogLevel(LogService.LogLevel.INFO)
        .offset("abc");
    LogQuery clone1 = query.clone();
    assertEquals(REQUEST_IDS, clone1.getRequestIds());
    assertTrue(queryMatch(query, clone1));
    LogQuery clone2 = clone1.clone();
    assertEquals(REQUEST_IDS, clone2.getRequestIds());
    assertTrue(queryMatch(clone1, clone2));
  }

  private boolean queryMatch(@Nullable LogQuery query1, @Nullable LogQuery query2) {
    return Objects.equals(query1.getVersions(), query2.getVersions())
        && Objects.equals(query1.getBatchSize(), query2.getBatchSize())
        && Objects.equals(query1.getEndTimeMillis(), query2.getEndTimeMillis())
        && Objects.equals(query1.getIncludeAppLogs(), query2.getIncludeAppLogs())
        && Objects.equals(query1.getIncludeIncomplete(), query2.getIncludeIncomplete())
        && Objects.equals(query1.getMajorVersionIds(), query2.getMajorVersionIds())
        && Objects.equals(query1.getMinLogLevel(), query2.getMinLogLevel())
        && Objects.equals(query1.getRequestIds(), query2.getRequestIds())
        && query1.getOffset().equals(query2.getOffset());
  }
}
