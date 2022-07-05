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

package com.google.appengine.tools.admin;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

public class CronEntryTest extends TestCase {
  public void testSetGet() {
    CronEntry entry = new CronEntryImpl("http://some/url", null, "every 6 hours", null);
    assertEquals("http://some/url", entry.getUrl());
    assertEquals("every 6 hours", entry.getSchedule());
    assertEquals("UTC", entry.getTimezone());
    assertEquals(null, entry.getDescription());

    entry = new CronEntryImpl("http://some/url", "description", "every 6 hours", "EDT");
    assertEquals("http://some/url", entry.getUrl());
    assertEquals("every 6 hours", entry.getSchedule());
    assertEquals("EDT", entry.getTimezone());
    assertEquals("description", entry.getDescription());
  }

  public void testUpcomingTimes() throws Exception {
    CronEntryImpl entry =
        new CronEntryImpl("http://some/url", "description", "every 6 hours", "US/Eastern");
    Iterator<String> iter = entry.getNextTimesIterator(new Date(128 * 60 * 60 * 1000L));

    DateFormat format = new SimpleDateFormat("EEE MMM dd, yyyy HH:mm z (Z)");

    assertTrue(iter.hasNext());
    Date from = format.parse(iter.next());

    for(int i = 0 ; i < 4; i++) {
      assertTrue(iter.hasNext());
      Date to = format.parse(iter.next());

      long intervalMs = to.getTime() - from.getTime();
      assertTrue(intervalMs >= TimeUnit.HOURS.toMillis(6));
      from = to;
    }

    assertTrue(iter.hasNext());
  }
}
