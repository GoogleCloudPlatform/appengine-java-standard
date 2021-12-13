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

package com.google.appengine.api.search;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Helper class for testing date related issues.
 */
public class DateTestHelper {

  // yyyy-MM-dd
  public static final DateTimeFormatter ISO8601_FORMATTER = ISODateTimeFormat
      .date().withZone(DateTimeZone.UTC).withLocale(Locale.US);
  // yyyy-MM-dd'T'HH
  public static final DateTimeFormatter ISO8601_H_FORMATTER = ISODateTimeFormat
      .dateHour().withZone(DateTimeZone.UTC).withLocale(Locale.US);
  // yyyy-MM-dd'T'HH:mm
  public static final DateTimeFormatter ISO8601_HM_FORMATTER = ISODateTimeFormat
      .dateHourMinute().withZone(DateTimeZone.UTC).withLocale(Locale.US);
  // yyyy-MM-dd'T'HH:mm:ss
  public static final DateTimeFormatter ISO8601_HMS_FORMATTER = ISODateTimeFormat
      .dateHourMinuteSecond().withZone(DateTimeZone.UTC).withLocale(Locale.US);
  // yyyy-MM-dd'T'HH:mm:ss.SSS
  public static final DateTimeFormatter ISO8601_HMSM_FORMATTER = ISODateTimeFormat
      .dateHourMinuteSecondMillis().withZone(DateTimeZone.UTC).withLocale(Locale.US);

  public static Date parseDate(String dateString) {
    return ISO8601_FORMATTER.parseDateTime(dateString).toDate();
  }

  public static final long MILLISECONDS_PER_DAY = 24L * 60 * 60 * 1000;

  public static int getDaysSinceEpoch(Date date) {
    return (int) (date.getTime() / (MILLISECONDS_PER_DAY));
  }

  public static String formatDate(Date date) {
    return ISO8601_FORMATTER.print(date.getTime());
  }

  public static DateFormat getDateFormat(String formatString) {
    DateFormat format = new SimpleDateFormat(formatString, Locale.US);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    return format;
  }

  public static DateFormat getDateFormat() {
    return getDateFormat("yyyy-MM-dd");
  }

  public static Calendar getCalendar() {
    return new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
  }

  public static DateTime getDateTimeUTC(Date date) {
    return getDateTimeUTC(date.getTime());
  }

  public static DateTime getDateTimeUTC(long date) {
    return new DateTime(date, DateTimeZone.UTC);
  }

  public static MutableDateTime getMutableDateTimeUTC(Date date) {
    return new MutableDateTime(date.getTime(), DateTimeZone.UTC);
  }

  private DateTestHelper() {
  }
}
