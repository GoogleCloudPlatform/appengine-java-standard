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

import com.google.apphosting.api.AppEngineInternal;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A utility class that centralizes processing of dates.
 *
 */
@AppEngineInternal
public final class DateUtil {

  /**
   * The milliseconds in a day.
   */
  public static final int MILLISECONDS_IN_DAY = 24 * 60 * 60 * 1000;

  /**
   * The UTC time zone.
   */
  private static final ThreadLocal<TimeZone> UTC_TZ =
      new ThreadLocal<TimeZone>() {
        @Override protected TimeZone initialValue() {
          return TimeZone.getTimeZone("UTC");
        }
      };

  private static DateFormat getDateFormat(String formatString) {
    DateFormat format = new SimpleDateFormat(formatString, Locale.US);
    format.setTimeZone(UTC_TZ.get());
    return format;
  }

  private static final ThreadLocal<DateFormat> ISO8601_SIMPLE =
      new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
          return getDateFormat("yyyy-MM-dd");
        }
      };

  private static final ThreadLocal<DateFormat> ISO8601_DATE_TIME_SIMPLE =
      new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
          return getDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        }
      };

  private static final ThreadLocal<DateFormat> ISO8601_DATE_TIME_SIMPLE_ERA =
      new ThreadLocal<DateFormat>() {
        @Override protected DateFormat initialValue() {
          return getDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'G");
        }
      };

  private DateUtil() {
  }

  /**
   * Get a UTC calendar with Locale US.
   */
  private static Calendar getCalendarUTC() {
    return new GregorianCalendar(UTC_TZ.get(), Locale.US);
  }

  /**
   * Formats a date according ISO 8601 full date time format. Currently,
   * this is used to print dates for error messages.
   *
   * @param date the date to format as a string
   * @return a string representing the date in ISO 8601 format
   */
  public static String formatDateTime(Date date) {
    if (date == null) {
      return null;
    }
    return isBeforeCommonEra(date) ?
        ISO8601_DATE_TIME_SIMPLE_ERA.get().format(date) :
        ISO8601_DATE_TIME_SIMPLE.get().format(date);
  }

  /**
   * Returns true if the date is before the common era.
   */
  private static boolean isBeforeCommonEra(Date date) {
    Calendar cal = getCalendarUTC();
    cal.setTime(date);
    return cal.get(Calendar.ERA) == GregorianCalendar.BC;
  }

  /**
   * Parses an ISO 8601 into a {@link Date} object.
   *
   * This function is only used for deserializing legacy "yyyy-MM-dd"
   * formatted dates stored in backend. These are currently not supporting
   * BC Era dates, so neither will this function.
   *
   * @param dateString the ISO 8601 formatted string for a date
   * @return the {@link Date} parsed from the date string
   */
  private static Date parseDate(String dateString) {
    // We don't need to support parsing BCE dates since none have or
    // are being written to backend.
    ParsePosition pos = new ParsePosition(0);
    Date d = ISO8601_SIMPLE.get().parse(dateString, pos);
    if (pos.getIndex() < dateString.length()) {
      throw new IllegalArgumentException(
          String.format("Failed to parse date string \"%s\"", dateString));
    }
    return d;
  }

  /**
   * Converts date into a string containing the milliseconds since the UNIX Epoch.
   *
   * @param date the date to serialize as a string
   * @return a string representing the date as milliseconds since the UNIX Epoch
   */
  public static String serializeDate(Date date) {
    return date == null ? "" : Long.toString(date.getTime());
  }

  /**
   * Converts a string containing the milliseconds since the UNIX Epoch into a Date.
   *
   * Two formats of date string are supported: "yyyy-MM-dd" and a long. Eventually,
   * the "yyyy-MM-dd" format support will be removed.
   *
   * @param date the date string to deserialize into a date
   * @return a date
   */
  public static Date deserializeDate(String date) {
    if (date == null) {
      return null;
    }
    // TODO: remove this code to capture "yyyy-MM-dd" formatted dates when the API
    // and backend are both using long representation, and a map-reduce has been used
    // to replace the date formatted strings in backend.
    if (date.startsWith("-")) {
      if (date.length() > 1 && date.indexOf('-', 1) >= 0) {
        // This should not happen, since we have never written negative years correctly.
        return parseDate(date);
      }
    } else {
      if (date.indexOf('-') > 0) {
        return parseDate(date);
      }
    }
    return new Date(Long.parseLong(date));
  }

  /**
   * Constructs a {@link Date} set to the UNIX Epoch plus days plus milliseconds.
   *
   * @param days the number of days to add to the UNIX Epoch to
   *   get a Date
   * @param milliseconds the number of milliseconds to add to the date
   * @return the Date with number of days plus Epoch
   */
  public static final Date getEpochPlusDays(int days, int milliseconds) {
    Calendar cal = getCalendarUTC();
    cal.setTimeInMillis(0L);
    cal.add(Calendar.DATE, days);
    cal.add(Calendar.MILLISECOND, milliseconds);
    return cal.getTime();
  }
}
