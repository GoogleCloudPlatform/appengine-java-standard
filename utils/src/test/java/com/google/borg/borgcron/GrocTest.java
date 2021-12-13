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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import java.util.List;
import junit.framework.TestCase;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

/**
 * Tests for the Groc parser.
 *
 */
public class GrocTest extends TestCase {

  /** Tests that bad input produces the correct exception. */
  public void testBadInput() {
    // not an interval
    parseBadSchedule("every 2 feet");
    // total gibberish
    parseBadSchedule("this makes no sense, but you'll try to parse it anyway");
    // not a valid day of the week
    parseBadSchedule("1st funday of every month 10:12");
    // 24:00 isn't a valid time
    parseBadSchedule("every day 24:00");
    // 09:60 is bad. Note, technically 23:59:60 is valid, that's it for 60.
    // and I don't care about triggering jobs on a leapsecond.
    parseBadSchedule("every day 09:60");
    // Extra stuff hanging off the end.

    parseBadSchedule("every 24 hours 09:00");
    parseBadSchedule("every day 09:00 please");

    // Both "synchronized" and a range.
    parseBadSchedule("every 1 minutes from 09:00 to 12:00 synchronized");
    parseBadSchedule("every 1 minutes synchronized from 09:00 to 12:00");
    // Other errors involving "from...to" syntax.
    parseBadSchedule("every 1 minutes from 09:00");
    parseBadSchedule("every 1 minutes to 12:00");
    parseBadSchedule("every 1 minutes 09:00 to 12:00");
    parseBadSchedule("every 1 minutes from 09:00 12:00");
  }

  /**
   * Tests parsing of interval schedules.
   *
   * @throws Exception should not be thrown
   */
  public void testIntervalParsing() throws Exception {
    GrocParser parser;

    parser = createParser("every 20 mins");
    parser.timespec();
    assertEquals((Integer) 20, parser.getInterval());
    assertEquals("mins", parser.getIntervalPeriod());
    assertFalse(parser.getSynchronized());

    parser = createParser("every 2 minutes");
    parser.timespec();
    assertEquals((Integer) 2, parser.getInterval());
    assertEquals("mins", parser.getIntervalPeriod());
    assertFalse(parser.getSynchronized());

    parser = createParser("every 100 mins");
    parser.timespec();
    assertEquals((Integer) 100, parser.getInterval());
    assertEquals("mins", parser.getIntervalPeriod());
    assertFalse(parser.getSynchronized());

    parser = createParser("every 3 hours");
    parser.timespec();
    assertEquals((Integer) 3, parser.getInterval());
    assertEquals("hours", parser.getIntervalPeriod());
    assertFalse(parser.getSynchronized());

    parser = createParser("every 100 hours");
    parser.timespec();
    assertEquals((Integer) 100, parser.getInterval());
    assertEquals("hours", parser.getIntervalPeriod());
    assertFalse(parser.getSynchronized());

    parser = createParser("every 20 mins synchronized");
    parser.timespec();
    assertEquals((Integer) 20, parser.getInterval());
    assertEquals("mins", parser.getIntervalPeriod());
    assertTrue(parser.getSynchronized());
  }

  /**
   * Tests parsing of intervals with a specified time range.
   *
   * @throws Exception should not be thrown
   */
  public void testRangedIntervalParsing() throws Exception {
    GrocParser parser;
    parser = createParser("every 2 hours from 12:34 to 01:23");
    parser.timespec();
    assertEquals((Integer) 2, parser.getInterval());
    assertEquals("hours", parser.getIntervalPeriod());
    assertFalse(parser.getSynchronized());
    assertEquals("12:34", parser.getStartTime());
    assertEquals("01:23", parser.getEndTime());
  }

  /**
   * Tests explicit monthdays. "1,3,5 of jan 09:00"
   *
   * @throws Exception should not be thrown
   */
  public void testMonthdays() throws Exception {
    List<Integer> ordinals = ImmutableList.of();
    List<Integer> weekdays = ImmutableList.of();
    String time = "09:00";
    List<Integer> months;

    months = ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    checkParserResults("3,15,30 of month 09:00", ordinals, weekdays, months, time);
    months = ImmutableList.of(1);
    checkParserResults("03,15,30 of jan 09:00", ordinals, weekdays, months, time);
  }

  /**
   * Tests various shorthands. "every day 09:00", "every mon 09:00"
   *
   * @throws Exception should not be thrown
   */
  public void testShorthands() throws Exception {
    List<Integer> ordinals = ImmutableList.of(1, 2, 3, 4, 5);
    List<Integer> weekdays = ImmutableList.of(0, 1, 2, 3, 4, 5, 6);
    List<Integer> months = ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    String time = "09:00";
    checkParserResults("every day 09:00", ordinals, weekdays, months, time);
    weekdays = ImmutableList.of(1);
    checkParserResults("every mon 09:00", ordinals, weekdays, months, time);
  }

  /**
   * Tests various ways of spelling "all values".
   *
   * @throws Exception should not be thrown
   */
  public void testAllValues() throws Exception {
    List<Integer> ordinals = ImmutableList.of(1, 2, 3, 4, 5);
    List<Integer> weekdays = ImmutableList.of(0, 1, 2, 3, 4, 5, 6);
    List<Integer> months = ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    String time = "10:19";

    checkParserResults(
        "first,second,third,fourth,fifth "
            + "monday,tuesday,wednesday,thursday,friday,saturday,"
            + "sunday of month 10:19",
        ordinals,
        weekdays,
        months,
        time);
    checkParserResults(
        "1st,2nd,3rd,4th,5th "
            + "monday,tuesday,wednesday,thursday,friday,saturday,sunday "
            + "of month 10:19",
        ordinals,
        weekdays,
        months,
        time);
    checkParserResults(
        "1st,2nd,third,4th,fifth "
            + "monday,tuesday,wednesday,thursday,friday,saturday,sunday "
            + "of month 10:19",
        ordinals,
        weekdays,
        months,
        time);
    checkParserResults(
        "1st,2nd,third,4th,fifth "
            + "monday,tue,wednesday,thu,friday,saturday,sun "
            + "of month 10:19",
        ordinals,
        weekdays,
        months,
        time);
    checkParserResults(
        "1st,2nd,third,4th,fifth "
            + "monday,tue,wednesday,thu,friday,saturday,sun "
            + "of jan,feb,mar,apr,may,jun,jul,aug,sep,oct,nov,dec 10:19",
        ordinals,
        weekdays,
        months,
        time);
    checkParserResults(
        "1st,2nd,third,4th,fifth "
            + "monday,tue,wednesday,thu,friday,saturday,sun "
            + "of january,february,march,april,may,june,july,august,"
            + "september,october,november,december 10:19",
        ordinals,
        weekdays,
        months,
        time);
  }

  /**
   * Test the special handling of fourth vs fifth. This is needed because of the way ANTLR generates
   * it's lexer code.
   *
   * @throws Exception should not be thrown
   */
  public void testFourthOrFifth() throws Exception {
    List<Integer> ordinals = ImmutableList.of(4, 5);
    List<Integer> weekdays = ImmutableList.of(1, 2);
    List<Integer> months = ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    String time = "14:15";
    checkParserResults(
        "fourth,fifth monday,tuesday of month 14:15", ordinals, weekdays, months, time);
  }

  /**
   * Tests handling of quarters.
   *
   * @throws Exception should not be thrown
   */
  public void testQuarters() throws Exception {
    List<Integer> ordinals = ImmutableList.of(1);
    List<Integer> weekdays = ImmutableList.of(0);
    String time = "23:25";
    List<Integer> months;

    months = ImmutableList.of(1, 4, 7, 10);
    checkParserResults("1st sunday of quarter 23:25", ordinals, weekdays, months, time);

    months = ImmutableList.of(2, 5, 8, 11);
    checkParserResults(
        "1st sunday of second month of quarter 23:25", ordinals, weekdays, months, time);

    months = ImmutableList.of(2, 3, 5, 6, 8, 9, 11, 12);
    checkParserResults(
        "1st sunday of second,third month of quarter 23:25", ordinals, weekdays, months, time);
  }

  /**
   * Tests leading and trailing whitespace.
   *
   * @throws Exception should not be thrown
   */
  public void testWhitespace() throws Exception {
    GrocParser parser;

    parser = createParser(" \t\r\n every 7 mins");
    parser.timespec();
    assertEquals((Integer) 7, parser.getInterval());

    parser = createParser("every 22 mins \t\r\n ");
    parser.timespec();
    assertEquals((Integer) 22, parser.getInterval());
  }

  /**
   * Parse a schedule and check the results are what was expected.
   *
   * @param parseString the schedule to be parsed and checked
   * @param ordinals the expected list of ordinals
   * @param weekdays the expected list of weekdays
   * @param months the expected list of months
   * @param time the expected time
   * @throws RecognitionException if the schedule cannot be parsed
   */
  private void checkParserResults(
      String parseString,
      List<Integer> ordinals,
      List<Integer> weekdays,
      List<Integer> months,
      String time)
      throws RecognitionException {
    GrocParser parser;
    parser = createParser(parseString);
    parser.timespec();
    assertWithMessage("ordinals mismatched")
        .that(parser.getOrdinals())
        .containsExactlyElementsIn(ordinals);
    assertWithMessage("weekdays mismatched")
        .that(parser.getWeekdays())
        .containsExactlyElementsIn(weekdays);
    assertWithMessage("months mismatched")
        .that(parser.getMonths())
        .containsExactlyElementsIn(months);
    assertEquals(time, parser.getTime());
  }

  /**
   * Create a parser for the given schedule.
   *
   * @param schedule the schedule to be parsed
   * @return a new parser instance
   */
  private GrocParser createParser(String schedule) {
    GrocLexer lexer = new GrocLexer(new ANTLRStringStream(schedule));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    GrocParser parser = new GrocParser(tokens);
    parser.init();
    return parser;
  }

  /**
   * Parse a bad schedule and check that it fails.
   *
   * @param schedule the bad schedule input to be checked
   */
  private void parseBadSchedule(String schedule) {
    GrocParser parser = createParser(schedule);
    try {
      parser.timespec();
      fail("Should have failed to parse: " + schedule);
    } catch (RecognitionException e) {
      // expected fail.
    }
  }
}
