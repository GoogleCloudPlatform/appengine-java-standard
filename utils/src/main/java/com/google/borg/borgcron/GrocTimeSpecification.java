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

package com.google.borg.borgcron;

// This code is open sourced as part of the App Engine Java SDK. It must be
// kept clean of google3 code (for instance jcg.common or jcg.collect).

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

/**
 * Parses a schedule in Groc format determines the next time that a given schedule will come due.
 *
 */
public class GrocTimeSpecification {

  private final Set<Integer> months;
  private final Set<Integer> ordinals;
  private final Set<Integer> weekdays;
  private final Set<Integer> monthdays;
  private final Integer interval;
  private final String intervalPeriod;
  private final int hour;
  private final int minute;
  private final int seconds;
  private final IntegerPair startHourMinute;
  private final IntegerPair endHourMinute;
  private final TimeZone timezone;
  private static final TimeZone UTC_ZONE = TimeZone.getTimeZone("UTC");
  private static final long MS_PER_HOUR = 1000 * 60 * 60;
  private static final long MS_PER_MINUTE = 1000 * 60;
  private static final String[] ORDINALS = {"1st", "2nd", "3rd", "4th", "5th"};
  private static final String[] WEEKDAY_NAMES = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
  private static final String[] MONTH_NAMES = {
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
  };
  private static final IntegerPair START_OF_DAY = new IntegerPair(0, 0);
  private static final IntegerPair END_OF_DAY = new IntegerPair(23, 59);

  /**
   * Throws an exception if any integer in the given set lies outside the given lower and upper
   * limits.
   */
  private static void checkEntriesAreInRange(
      Set<Integer> inputSet, int lowerLimit, int upperLimit, String spec, String setName) {
    for (Integer value : inputSet) {
      if (value < lowerLimit || value > upperLimit) {
        throw new IllegalArgumentException(
            "Specification '"
                + spec
                + "': "
                + setName
                + " is out of range ["
                + lowerLimit
                + ".."
                + upperLimit
                + "]");
      }
    }
  }

  private static int getLastDayOfMonth(int month) {
    switch (month) {
      case 1:
      case 3:
      case 5:
      case 7:
      case 8:
      case 10:
      case 12:
        return 31;
      case 4:
      case 6:
      case 9:
      case 11:
        return 30;
      case 2:
        return 29; // Assume leap year.
      default:
        return 0;
    }
  }

  /**
   * Creates a {@link GrocTimeSpecification} for the given schedule and timezone, after calling the
   * ANTLR-generated parsing code.
   *
   * @param spec the schedule specification
   * @param timezone the timezone (or null for UTC)
   * @return a {@link GrocTimeSpecification} for the given schedule
   * @throws IllegalArgumentException if the specification is invalid
   */
  public static GrocTimeSpecification create(String spec, TimeZone timezone) {
    GrocLexer lexer = new GrocLexer(new ANTLRStringStream(spec));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    GrocParser parser = new GrocParser(tokens);
    parser.init();
    try {
      parser.timespec();
    } catch (RecognitionException e) {
      throw new IllegalArgumentException("Specification '" + spec + "' is invalid.", e);
    }
    if (timezone == null) {
      timezone = UTC_ZONE;
    }

    // Data needed by the timespec constructors.
    String period = parser.getIntervalPeriod();
    boolean hasPeriod = !parser.getIntervalPeriod().isEmpty();
    boolean synchronised = parser.getSynchronized();
    String startTime = parser.getStartTime();
    String endTime = parser.getEndTime();
    Integer interval = parser.getInterval();
    if (hasPeriod && (interval == null)) {
      throw new IllegalArgumentException(
          "Specification '"
              + spec
              + "', appears to be periodic ('every...')"
              + " but is missing an interval ('seconds', 'minutes), period='"
              + period
              + "'");
    }
    checkEntriesAreInRange(parser.getOrdinals(), 1, 5, spec, "ordinals");
    checkEntriesAreInRange(parser.getWeekdays(), 0, 6, spec, "weekdays");
    checkEntriesAreInRange(parser.getMonths(), 1, 12, spec, "months");
    checkEntriesAreInRange(parser.getMonthdays(), 1, 31, spec, "days of month");

    if (!parser.getMonths().isEmpty() && !parser.getMonthdays().isEmpty()) {
      boolean validDays = false;
      int lastMonth = -1;
      int minDayOfMonth = 32;
      for (Integer day : parser.getMonthdays()) {
        if (day < minDayOfMonth) {
          minDayOfMonth = day;
        }
      }
      for (Integer month : parser.getMonths()) {
        lastMonth = month;
        if (minDayOfMonth <= getLastDayOfMonth(month)) {
          validDays = true;
          break; // The specification is valid if at least one day is valid.
        }
      }
      if (!validDays) {
        throw new IllegalArgumentException(
            "Specification '"
                + spec
                + "': "
                + "invalid day of month, got day "
                + minDayOfMonth
                + " of month "
                + lastMonth);
      }
    }

    if (interval != null) {
      GrocTimeSpecification gts =
          new GrocTimeSpecification(
              interval, parser.getIntervalPeriod(), startTime, endTime, synchronised, timezone);
      return gts;
    } else {
      GrocTimeSpecification gts =
          new GrocTimeSpecification(
              parser.getOrdinals(),
              parser.getMonths(),
              parser.getWeekdays(),
              parser.getMonthdays(),
              parser.getTime(),
              timezone);
      return gts;
    }
  }

  /**
   * Creates a {@link GrocTimeSpecification} for the given schedule (in UTC), after calling the
   * ANTLR-generated parsing code.
   *
   * @param spec the schedule specification
   * @return a {@link GrocTimeSpecification} for the given schedule
   * @throws IllegalArgumentException if the specification is invalid
   */
  public static GrocTimeSpecification create(String spec) {
    return create(spec, UTC_ZONE);
  }

  /**
   * Creates a specification from the given ordinals, weekdays, months and time.
   *
   * @param ordinals a set of ordinals (1st, 2nd, &c)
   * @param months a set of months (jan=1, dec=12)
   * @param weekdays a set of weekdays (sun=0, sat=6)
   * @param monthdays a set of monthdays (1 - 31)
   * @param time a time, as "HH:MM"
   * @param timezone the timezone
   */
  public GrocTimeSpecification(
      Set<Integer> ordinals,
      Set<Integer> months,
      Set<Integer> weekdays,
      Set<Integer> monthdays,
      String time,
      TimeZone timezone) {
    this.timezone = timezone;
    this.ordinals = ordinals;
    this.months = months;
    this.weekdays = weekdays;
    this.monthdays = monthdays;
    if (!weekdays.isEmpty() && !monthdays.isEmpty()) {
      throw new IllegalArgumentException("cannot specify both monthdays and weekdays");
    }

    verifyWithinLimits(ordinals, 1, 5, "ordinals");
    verifyWithinLimits(weekdays, 0, 6, "weekdays");
    verifyWithinLimits(months, 1, 12, "months");
    verifyWithinLimits(monthdays, 1, 31, "day of month");

    if (!months.isEmpty() && !monthdays.isEmpty()) {
      Calendar calendar = Calendar.getInstance();
      boolean noValidDates = true;
      int lastMonth = -1;
      int minMonthday = 32;
      for (int day : monthdays) {
        if (day < minMonthday) {
          minMonthday = day;
        }
      }
      for (int month : months) {
        lastMonth = month;
        calendar.set(4, month - 1, 1); // Assume leap year.
        if (minMonthday <= calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
          noValidDates = false;
          break; // The specification is valid if at least one day is valid.
        }
      }
      if (noValidDates) {
        throw new IllegalArgumentException(
            "invalid day of month, got day " + minMonthday + " of month " + lastMonth);
      }
    }
    IntegerPair timeHourMinute = parseTime(time);
    hour = timeHourMinute.first;
    minute = timeHourMinute.second;
    interval = null;
    seconds = 0; // unused
    startHourMinute = null;
    endHourMinute = null;
    intervalPeriod = "";
  }

  /**
   * Creates a specification from a given period and interval.
   *
   * <p>startTime and endTime restrict intervals to a certain range of time. If either is nonempty
   * then both should be, and 'synchronize' should be false.
   *
   * @param interval an interval between times
   * @param period the units for the interval, "hours" or "minutes"
   * @param startTime a time, either empty or "HH:MM"
   * @param endTime a time, either empty or "HH:MM"
   * @param synchronize whether to lock to a fixed interval
   * @param timezone the timezone
   * @throws IllegalArgumentException if the interval is <= 0
   */
  public GrocTimeSpecification(
      int interval,
      String period,
      String startTime,
      String endTime,
      boolean synchronize,
      TimeZone timezone) {
    hour = 0;
    minute = 0;
    months = new HashSet<>();
    ordinals = new HashSet<>();
    weekdays = new HashSet<>();
    monthdays = new HashSet<>();
    this.timezone = timezone;
    if (interval <= 0) {
      throw new IllegalArgumentException("interval must be greater than zero");
    }
    this.interval = interval;
    this.intervalPeriod = period;
    if (this.intervalPeriod.equals("hours")) {
      this.seconds = this.interval * 3600;
    } else {
      this.seconds = this.interval * 60;
    }
    if (!startTime.isEmpty()) {
      startHourMinute = parseTime(startTime);
      endHourMinute = parseTime(endTime);
    } else if (synchronize) {
      if ((this.seconds > 86400) || (86400 % this.seconds) != 0) {
        throw new IllegalArgumentException(
            "can only use synchronized for " + "periods that divide evenly into " + "24 hours");
      }
      startHourMinute = START_OF_DAY;
      endHourMinute = END_OF_DAY;
    } else {
      startHourMinute = null;
      endHourMinute = null;
    }
  }

  /** Checks for invalid values in fields 'ordinals', 'weekdays', 'months'. */
  private static void verifyWithinLimits(
      Set<Integer> inputSet, int lowerLimit, int upperLimit, String setName) {
    for (int entry : inputSet) {
      if (entry < lowerLimit || entry > upperLimit) {
        throw new IllegalArgumentException(
            setName
                + " must be between "
                + lowerLimit
                + " and "
                + upperLimit
                + " inclusive, got "
                + inputSet);
      }
    }
  }

  /** Parses a string of the form "HH:MM" into an IntegerPair containing hour and minute. */
  private static IntegerPair parseTime(String timeString) {
    // We can safely assume the time string is in HH:MM format, because
    // otherwise it wouldn't have been parsed by the ANTLR parser.
    int colonIndex = timeString.indexOf(":");
    int hour = Integer.parseInt(timeString.substring(0, colonIndex));
    int minute = Integer.parseInt(timeString.substring(colonIndex + 1));
    return new IntegerPair(hour, minute);
  }

  /** Formats an hour and minute into a string of the form "HH:MM". */
  private static String formatTime(int hour, int minute) {
    return new Formatter(new StringBuilder(5), null).format("%d:%02d", hour, minute).toString();
  }

  /**
   * Gets a list of upcoming times that match the schedule.
   *
   * @param start start from this time - all matches will be after this
   * @param count the number of matches to return
   * @return a list of matching times
   */
  public List<Date> getMatches(Date start, int count) {
    List<Date> results = new ArrayList<>();
    Date next = start;
    for (int i = 0; i < count; i++) {
      next = getMatch(next);
      results.add(next);
    }
    return results;
  }

  /**
   * Gets the next time that matches the schedule.
   *
   * @param start find the next match after this time
   * @return the next match time
   */
  public Date getMatch(Date start) {
    if (interval != null && startHourMinute != null) {
      return getMatchRangedInterval(start);
    } else if (interval != null) {
      return getMatchInterval(start);
    } else {
      return getMatchSpecificTime(start);
    }
  }

  /**
   * Implements getMatch() for interval schedules that have a startHourMinute and endHourMinute
   * defined.
   */
  private Date getMatchRangedInterval(Date now) {
    // Get the beginning of the time range immediately preceding 'start'.
    Date startDate = getPreviousDate(now, startHourMinute);

    // Get the next time after 'now' that is an even multiple of 'seconds'
    // after startDate.
    long startDeltaMillis = now.getTime() - startDate.getTime();
    long millis = seconds * 1000L;
    long numIntervals = (startDeltaMillis + millis) / millis;
    long intervalMillis = startDate.getTime() + numIntervals * millis;
    Date intervalDate = new Date(intervalMillis);

    // If 'now' and intervalDate are contained in the same day's range, return
    // intervalDate.  Otherwise, return the start of the next range.
    Date nextStartDate = getNextDate(now, startHourMinute);
    if (timeIsInRange(now) && timeIsInRange(intervalDate) && intervalDate.before(nextStartDate)) {
      return intervalDate;
    } else {
      return nextStartDate;
    }
  }

  /*
   * Returns true if utcDate falls between this.startDate and this.endDate
   * inclusive, in this.timezone.
   */
  private boolean timeIsInRange(Date utcDate) {
    // Determine whether a start time or end time happened more recently before
    // utc_date.
    Date previousStartDate = getPreviousDate(utcDate, startHourMinute);
    Date previousEndDate = getPreviousDate(utcDate, endHourMinute);
    if (previousStartDate.after(previousEndDate)) {
      return true;
    } else {
      return utcDate.equals(previousEndDate);
    }
  }

  /*
   * Returns the latest Date before or equal to utcDate whose equivalent in
   * this.timezone has the given targetHourMinute.
   */
  private Date getPreviousDate(Date utcDate, IntegerPair targetHourMinute) {
    Calendar localDate = Calendar.getInstance(timezone);
    localDate.setTime(utcDate);

    // The result may be either on this day or a previous day.
    while (true) {
      Date result =
          combineDateAndTime(
              localDate.get(Calendar.YEAR),
              localDate.get(Calendar.MONTH),
              localDate.get(Calendar.DAY_OF_MONTH),
              targetHourMinute);
      if (!result.after(utcDate)) {
        return result;
      }
      localDate.add(Calendar.DAY_OF_MONTH, -1);
    }
  }

  /*
   * Returns the earliest Date after utcDate whose equivalent in this.timezone
   * has the given targetHourMinute.
   */
  private Date getNextDate(Date utcDate, IntegerPair targetHourMinute) {
    Calendar localDate = Calendar.getInstance(timezone);
    localDate.setTime(utcDate);

    // The result may be either on this day or a subsequent day.
    while (true) {
      Date result =
          combineDateAndTime(
              localDate.get(Calendar.YEAR),
              localDate.get(Calendar.MONTH),
              localDate.get(Calendar.DAY_OF_MONTH),
              targetHourMinute);
      if (result.after(utcDate)) {
        return result;
      }
      localDate.add(Calendar.DAY_OF_MONTH, 1);
    }
  }

  /*
   * Combines a date and time.
   *
   * The input arguments are interpreted in this.timezone.
   *
   * Unfortunately, this method is quite complicated.  As far as I can tell,
   * Java's time zone libraries don't provide an easy way to resolve the
   * ambiguous times and nonexistent times that can arise with daylight
   * savings.
   *
   * @return a Date in UTC.
   */
  private Date combineDateAndTime(int year, int month, int day, IntegerPair hourMinute) {
    // First, do the conversion in UTC.
    Calendar utcCalendar = Calendar.getInstance(UTC_ZONE);
    utcCalendar.set(year, month, day, hourMinute.first, hourMinute.second, 0);
    utcCalendar.set(Calendar.MILLISECOND, 0);
    long utcMillis = utcCalendar.getTimeInMillis();

    if (timezone.getDSTSavings() == 0) {
      // The easy path -- simply do the timezone conversion and return.
      return new Date(utcMillis - timezone.getRawOffset());
    }

    // Manually convert to both standard and daylight time.
    long standardMillis = utcMillis - timezone.getRawOffset();
    long daylightMillis = standardMillis - timezone.getDSTSavings();

    // Now we check the results by converting standardMillis and daylightMillis
    // back to the local timezone.
    Calendar standardCalendar = Calendar.getInstance(timezone);
    Calendar daylightCalendar = Calendar.getInstance(timezone);
    standardCalendar.setTimeInMillis(standardMillis);
    daylightCalendar.setTimeInMillis(daylightMillis);
    boolean standardMatches = timeEquals(standardCalendar, year, month, day, hourMinute);
    boolean daylightMatches = timeEquals(daylightCalendar, year, month, day, hourMinute);

    if (standardMatches && daylightMatches) {
      // This is an ambiguous time, e.g. 1:30 on a day when we "fall back" from
      // 2:00 to 1:00.  Return the earlier time.
      long resultMillis = Math.min(standardMillis, daylightMillis);
      return new Date(resultMillis);
    } else if (standardMatches) {
      return new Date(standardMillis);
    } else if (daylightMatches) {
      return new Date(daylightMillis);
    } else {
      // This is a nonexistent time, e.g. 2:30 on a day when we "spring
      // forward" from 2:00 to 3:00.  Round down to the nearest hour and do the
      // calculation in standard time.
      utcCalendar.set(Calendar.MINUTE, 0);
      return new Date(utcCalendar.getTimeInMillis() - timezone.getRawOffset());
    }
  }

  /** Determines whether the given Calendar's date and time match the remaining arguments. */
  private boolean timeEquals(
      Calendar calendar, int year, int month, int day, IntegerPair hourMinute) {
    if (calendar.get(Calendar.YEAR) != year) {
      return false;
    }
    if (calendar.get(Calendar.MONTH) != month) {
      return false;
    }
    if (calendar.get(Calendar.DAY_OF_MONTH) != day) {
      return false;
    }
    if (calendar.get(Calendar.HOUR_OF_DAY) != hourMinute.first) {
      return false;
    }
    if (calendar.get(Calendar.MINUTE) != hourMinute.second) {
      return false;
    }
    if (calendar.get(Calendar.SECOND) != 0) {
      return false;
    }
    return true;
  }

  /**
   * Get the next time that matches the schedule. Called when the schedule is an interval.
   *
   * @param start finds the next match after this time
   * @return the next match time
   */
  private Date getMatchInterval(Date start) {
    Date result;
    if (intervalPeriod.equals("hours")) {
      result = new Date(start.getTime() + (interval * MS_PER_HOUR));
    } else {
      result = new Date(start.getTime() + (interval * MS_PER_MINUTE));
    }
    result.setSeconds(0);
    return result;
  }

  /**
   * Get the next time that matches the schedule. Called for non-interval schedules.
   *
   * @param start finds the next match after this time
   * @return the next match time
   */
  private Date getMatchSpecificTime(Date start) {
    // Convert start to local time.
    Calendar calendar = Calendar.getInstance(timezone);
    calendar.setTime(start);
    int startYear = calendar.get(Calendar.YEAR);
    int startMonth = calendar.get(Calendar.MONTH) + 1; // Calendar is 0-based
    int startDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
    int startHour = calendar.get(Calendar.HOUR_OF_DAY);
    int startMinute = calendar.get(Calendar.MINUTE);
    int startDstOffset = calendar.get(Calendar.DST_OFFSET);

    // Consider at least 48 months before giving up on finding a matching date,
    // to ensure we consider a leap year.
    final int maxMonthsToConsider = 48;
    NextGenerator monthGen = new NextGenerator(startMonth, months);
    int monthsConsidered = 0;
    while (true) {
      // Get the next matching month - monthGen will produce an endless series
      // of months that match this specification and a count of year wraps.
      IntegerPair nextMonthWrapPair = monthGen.next();
      int nextMonth = nextMonthWrapPair.first;
      int yearWraps = nextMonthWrapPair.second;
      ++monthsConsidered;

      calendar.set(startYear + yearWraps, nextMonth - 1, 1);
      List<Integer> dayMatches = findDays(calendar);
      if (dayMatches.isEmpty()) {
        if (monthsConsidered >= maxMonthsToConsider) {
          throw new AssertionError("no matching days");
        } else {
          continue;
        }
      }
      if (calendar.get(Calendar.YEAR) == startYear && nextMonth == startMonth) {
        // we're working with the current month, remove any days earlier than
        // today
        while (!dayMatches.isEmpty() && dayMatches.get(0) < startDayOfMonth) {
          dayMatches.remove(0);
        }
        if (!dayMatches.isEmpty() && dayMatches.get(0) == startDayOfMonth) {
          // We're working with the current day: remove first entry if it's before the starting
          // hour.  Note that this may not work if we encounter a jurisdiction which uses a
          // 2-hour offset for daylight savings time (DST).
          if (startHour > hour) {
            dayMatches.remove(0);
          } else if (startHour == hour) {
            // We're working with the current hour, which *may* be the DST "fall back" hour
            // before we've fallen back (i.e., we're still in DST).  Remove first entry if it's
            // before the starting minute *unless* we're in the "fall back" hour (in which case
            // we leave the dayMatch alone, since it may match after we have fallen back to
            // standard time).  The behavior of java.util.Calendar currently seems to be to
            // prefer standard time over daylight savings so, by "resetting" the calendar to
            // the current time, it will switch from daylight to standard time if we are in the
            // "fall back" hour that gets repeated when we fall back.  (And, remember that
            // java.util.Calendar months are 0-based!)
            if (startMinute >= minute) {
              boolean inTheFallbackHour = false;
              if (startDstOffset > 0) { // Currently in DST.
                calendar.set(startYear, startMonth - 1, startDayOfMonth, startHour, startMinute, 0);
                if (calendar.get(Calendar.DST_OFFSET) == 0) { // We've fallen back to standard time.
                  inTheFallbackHour = true;
                }
              }
              if (!inTheFallbackHour) {
                // No in the "fall back" hour: drop this match, as we've already done it.
                dayMatches.remove(0);
              }
            }
          }
        }
      }
      while (!dayMatches.isEmpty()) {
        // Yay, we have a matching date and time.
        int candidateDay = dayMatches.get(0);
        dayMatches.remove(0);
        int beforeDstOffsetMillis = calendar.get(Calendar.DST_OFFSET);
        // Following will switch to standard time if it can; also it will convert a non-existent
        // 02:30 into a (sprung forward) 3:30...
        calendar.set(startYear + yearWraps, nextMonth - 1, candidateDay, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int dstOffsetDifferenceMillis = beforeDstOffsetMillis - calendar.get(Calendar.DST_OFFSET);

        // As mentioned above, java.util.Calendar's implementation appears to be aggressive
        // about "falling back" from daylight time to standard time: if 'start' is in daylight
        // time and our candidate time is in that "magic hour" that gets repeated when we fall
        // back (e.g., 01:30 on the last Sunday in October), it will switch to standard time
        // and deliver (only) the second occurrence of the target time.  If we were in daylight
        // time and discover that we've switched to standard time, we try backing up the clock
        // by the DST offset (a.k.a., one hour); if this switches us back to daylight time at
        // some point after 'start', we will go with that time.  Otherwise (e.g., backing up
        // stayed in standard time), move the time back forwards to where it was.
        if (dstOffsetDifferenceMillis > 0) { // We "fell back" between 'start' and now.
          calendar.add(Calendar.MILLISECOND, -dstOffsetDifferenceMillis); // Back up.
          if (calendar.get(Calendar.DST_OFFSET) == 0 || start.after(calendar.getTime())) {
            calendar.add(Calendar.MILLISECOND, +dstOffsetDifferenceMillis);
          }
        }

        int calhour = calendar.get(Calendar.HOUR_OF_DAY);
        if (calhour != hour) {
          continue;
        }

        return calendar.getTime();
      }
    }
  }

  /**
   * Finds days that match the current instance's specification for a given month. We look at the
   * current instance's list of ordinals (first, second and so on) and weekdays (sunday, monday and
   * so forth).
   *
   * @param candidate the candidate month in which to find days. The final value of this will be
   *     unchanged, but fields will be modified during this call. Clone your calendar if you care.
   * @return an ordered set of matching days.
   */
  List<Integer> findDays(Calendar candidate) {
    Set<Integer> outDays = new TreeSet<>();

    long original = candidate.getTimeInMillis();

    // We want "day of week for first of month;" changed from Java's 1=Sunday
    // to groc's 0=Sunday
    candidate.set(Calendar.DAY_OF_MONTH, 1);
    int firstWeekDay = candidate.get(Calendar.DAY_OF_WEEK) - 1;

    candidate.set(Calendar.MONTH, candidate.get(Calendar.MONTH) + 1);
    candidate.add(Calendar.DAY_OF_MONTH, -1);

    int lastDayOfMonth = candidate.get(Calendar.DAY_OF_MONTH);

    candidate.setTimeInMillis(original);

    if (monthdays.isEmpty()) {
      // Walk through each ordinal and each weekday.
      for (int ordinal : ordinals) {
        for (int weekday : weekdays) {
          // java modulo returns -ve numbers for -ve inputs, force to be +ve
          int day = ((7 + weekday - firstWeekDay) % 7) + 1;
          day += 7 * (ordinal - 1);
          if (day <= lastDayOfMonth) {
            outDays.add(day);
          }
        }
      }
    } else {
      for (int day : monthdays) {
        if (day <= lastDayOfMonth) {
          outDays.add(day);
        }
      }
    }
    return new ArrayList<>(outDays);
  }

  /**
   * A generator of results from set 'matches'.
   *
   * <p>Matches: must be >= 'start'. If none match, the wrap counter is incremented, and the result
   * set is reset to the full set, while start is reset to 0. Each time next() is called, it returns
   * a Pair of (match, wrapcount), removing the match from the result set.
   */
  static class NextGenerator {

    private int start;
    private final Set<Integer> matches;
    private List<Integer> remaining;
    private int wrapcount;

    /**
     * Constructor.
     *
     * @param start the initial starting value
     * @param matches a list of potential matches
     */
    NextGenerator(Integer start, Set<Integer> matches) {
      this.start = start;
      // Make sure they're sorted.
      this.matches = new TreeSet<>(matches);
      remaining = new ArrayList<>(this.matches);
      wrapcount = 0;
    }

    /**
     * Generates the next result from the result set.
     *
     * @return a Pair of result, wrapcount
     */
    public IntegerPair next() {
      while (!remaining.isEmpty() && remaining.get(0) < start) {
        remaining.remove(0);
      }
      if (remaining.isEmpty()) {
        remaining = new ArrayList<>(matches);
        wrapcount++;
      }
      start = remaining.get(0);
      remaining.remove(0);
      return new IntegerPair(start, wrapcount);
    }
  }

  /** A pair of integers */
  static class IntegerPair {

    public final int first;
    public final int second;

    IntegerPair(int first, int second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof IntegerPair
          && this.first == ((IntegerPair) other).first
          && this.second == ((IntegerPair) other).second;
    }

    @Override
    public int hashCode() {
      return first ^ second;
    }

    @Override
    public String toString() {
      return "<" + first + "," + second + ">";
    }
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof GrocTimeSpecification)) {
      return false;
    }
    GrocTimeSpecification that = (GrocTimeSpecification) other;
    if (this.seconds != 0) {
      if (this.startHourMinute != null) {
        return this.seconds == that.seconds
            && this.startHourMinute.equals(that.startHourMinute)
            && this.endHourMinute.equals(that.endHourMinute)
            && (this.timezone.equals(that.timezone)
                || (START_OF_DAY.equals(this.startHourMinute)
                    && END_OF_DAY.equals(this.endHourMinute)));
      } else {
        return that.startHourMinute == null && this.seconds == that.seconds;
      }
    } else {
      return that.interval == null
          && this.ordinals.equals(that.ordinals)
          && this.months.equals(that.months)
          && this.monthdays.equals(that.monthdays)
          && this.weekdays.equals(that.weekdays)
          && this.hour == that.hour
          && this.minute == that.minute
          && this.timezone.equals(that.timezone);
    }
  }

  @Override
  public int hashCode() {
    int hashCode = 1;
    if (seconds != 0) {
      if (startHourMinute != null) {
        hashCode = hashCode * 17 + startHourMinute.hashCode();
        hashCode = hashCode * 17 + endHourMinute.hashCode();
        if (!(startHourMinute.equals(START_OF_DAY) && endHourMinute.equals(END_OF_DAY))) {
          hashCode = hashCode * 17 + timezone.hashCode();
        }
      }
      hashCode = hashCode * 17 + seconds;
    } else {
      hashCode = hashCode * 17 + timezone.hashCode();
      hashCode = hashCode * 17 + ordinals.hashCode();
      hashCode = hashCode * 17 + months.hashCode();
      hashCode = hashCode * 17 + monthdays.hashCode();
      hashCode = hashCode * 17 + weekdays.hashCode();
      hashCode = hashCode * 17 + hour;
      hashCode = hashCode * 17 + minute;
    }
    return hashCode;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (interval != null) {
      sb.append("every ");
      sb.append(interval);
      sb.append(' ');
      sb.append(intervalPeriod);
      if (startHourMinute != null) {
        if (startHourMinute.first == 0
            && startHourMinute.second == 0
            && endHourMinute.first == 23
            && endHourMinute.second == 59) {
          sb.append(" synchronized");
        } else {
          sb.append(" from ");
          sb.append(formatTime(startHourMinute.first, startHourMinute.second));
          sb.append(" to ");
          sb.append(formatTime(endHourMinute.first, endHourMinute.second));
        }
      }
    } else {
      if (ordinals.size() == 5) {
        sb.append("every ");
      } else if (!ordinals.isEmpty()) {
        appendList(sb, ordinals, 1, ORDINALS);
        sb.append(' ');
      }
      if (!weekdays.isEmpty()) {
        if (weekdays.size() == 7) {
          sb.append("day ");
        } else {
          appendList(sb, weekdays, 0, WEEKDAY_NAMES);
          sb.append(' ');
        }
      }
      if (!monthdays.isEmpty()) {
        int n = monthdays.size();
        for (int i = 1; i <= 31; i++) {
          if (monthdays.contains(i)) {
            sb.append(i);
            if (--n > 0) {
              sb.append(',');
            }
          }
        }
        sb.append(' ');
      }
      if (months.size() < 12) {
        sb.append("of ");
        appendList(sb, months, 1, MONTH_NAMES);
        sb.append(' ');
      } else if (weekdays.isEmpty()) {
        sb.append("of month ");
      }
      sb.append(formatTime(hour, minute));
    }
    return sb.toString();
  }

  /**
   * Map the elements of the given Integer set to the given labels and append them to the given
   * StringBuilder separated by commas.
   *
   * @param builder the destination StringBuilder
   * @param set the set of values to append
   * @param offset the lower bound of the integer range, i.e. value corresponding to labels[0]
   * @param labels String labels for the numeric values in the given set
   */
  private void appendList(StringBuilder builder, Set<Integer> set, int offset, String... labels) {
    int n = set.size();
    for (int i = 0; i < labels.length; ++i) {
      if (set.contains(i + offset)) {
        builder.append(labels[i]);
        if (--n > 0) {
          builder.append(',');
        } else {
          break;
        }
      }
    }
  }
}
