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

grammar Groc;

options {
  language=Java;
}

@lexer::header {
  // Copyright 2008 Google, Inc.  All rights reserved.
  package com.google.borg.borgcron;
}

@header {
  // Copyright 2008 Google, Inc.  All rights reserved.

  // Groc (Googley runner of commands) is a microlanguage that provides an
  // alternative to traditional cron syntax/semantics for specifying
  // recurrent events.  Syntactically, it is designed to be more readable
  // (more easily "grokked") than crontab language.  Groc forfeits certain
  // semantics found in crontab, in favor of readability; however,
  // certain timespecs which are awkward in crontab are much easier
  // to express in Groc (for example, the 3rd tuesday of the month).
  // It is these constructs to which Groc is best suited.
  //
  // Examples of valid Groc include:
  // "1st,3rd monday of month 15:30"
  // "every wed,fri of jan,jun 13:15"
  // "first sunday of quarter 00:00"
  //
  // FEATURES NOT YET IMPLEMENTED (in approx. order of priority):
  // - some way to specify multiple values for minutes/hours (definitely)
  // - "am/pm" (probably)
  // - other range/interval functionality (maybe)

  package com.google.borg.borgcron;

  import java.util.Set;
  import java.util.TreeSet;

}

@members {
  Set<Integer> allOrdinals = integerSetOf(1,2,3,4,5);

  private Set<Integer> months;
  private Set<Integer> ordinals;
  private Set<Integer> weekdays;
  private Set<Integer> monthdays;
  private String interval;
  private String period;
  private String time;
  private boolean synchronize;
  private String startTime;
  private String endTime;

  /**
   * Initialises the parser.
   */
  public void init() {
    ordinals = new TreeSet<Integer>();
    weekdays = new TreeSet<Integer>();
    months = new TreeSet<Integer>();
    monthdays = new TreeSet<Integer>();
    time = "";
    interval = "";
    period = "";
    synchronize = false;
    startTime = "";
    endTime = "";
  }

  public Set<Integer> getMonths() {
    return months;
  }

  public Set<Integer> getOrdinals() {
    return ordinals;
  }

  public Set<Integer> getWeekdays() {
    return weekdays;
  }

  public Set<Integer> getMonthdays() {
    return monthdays;
  }

  public String getTime() {
    return time;
  }

  public Integer getInterval() {
  if (interval == null || interval.equals("")) {
    return null;
  }
    return Integer.parseInt(interval);
  }

  public String getIntervalPeriod() {
    if (period.equals("minutes")) {
      return "mins";
    }
    return period;
  }

  public boolean getSynchronized() {
    return synchronize;
  }

  public String getStartTime() {
    return startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  // Convert date tokens to int representations of properties.
  static int valueOf(int token_type) {
    switch(token_type) {
      case SUNDAY:
        { return 0; }
      case FIRST:
      case MONDAY:
      case JANUARY:
        { return 1; }
      case TUESDAY:
      case SECOND:
      case FEBRUARY:
        { return 2; }
      case WEDNESDAY:
      case THIRD:
      case MARCH:
        { return 3; }
      case THURSDAY:
      case FOURTH:
      case APRIL:
        { return 4; }
      case FRIDAY:
      case FIFTH:
      case MAY:
        { return 5; }
      case SATURDAY:
      case JUNE:
        { return 6; }
      case JULY:
        { return 7; }
      case AUGUST:
        { return 8; }
      case SEPTEMBER:
        { return 9; }
      case OCTOBER:
        { return 10; }
      case NOVEMBER:
        { return 11; }
      case DECEMBER:
        { return 12; }
      default:
        { return -1; }
    }
  }

  // These three methods make any errors raise an exception immediately
  @Override
  public boolean mismatchIsUnwantedToken(IntStream input, int ttype) {
    return false;
  }

  @Override
  public boolean mismatchIsMissingToken(IntStream input, BitSet follow) {
    return false;
  }

  @Override
  public Object recoverFromMismatchedSet(IntStream input,
      RecognitionException e, BitSet follow) throws RecognitionException {
    throw e;
  }

  /** Returns a set of the given elements */
  private static Set<Integer> integerSetOf(Integer... elements) {
    Set<Integer> newSet = new TreeSet<Integer>();
    for (Integer element : elements) {
      newSet.add(element);
    }
    return newSet;
  }

}

@rulecatch {
  catch (RecognitionException e) {
    throw e;
  }
}

timespec
  : ( specifictime | interval ) EOF
  ;

specifictime
  : ((( ((ordinals weekdays)|monthdays) OF (monthspec|quarterspec) )
     | ( ordinals weekdays {
      Set<Integer> allMonths = integerSetOf(
          valueOf(JANUARY), valueOf(FEBRUARY), valueOf(MARCH), valueOf(APRIL),
          valueOf(MAY), valueOf(JUNE), valueOf(JULY), valueOf(AUGUST),
          valueOf(SEPTEMBER), valueOf(OCTOBER), valueOf(NOVEMBER),
          valueOf(DECEMBER));
      months.addAll(allMonths); } ))
        TIME { time = $TIME.text; } )
  ;

interval
  : ( EVERY
      intervalnum=(DIGIT | DIGITS) {
      interval = $intervalnum.text;
      }
      period {
      period = $period.text;
      }
      ( time_range |
        (SYNCHRONIZED { synchronize = true; })
      )? )
  ;

ordinals
  : ( EVERY
  | ( ordinal (COMMA ordinal)* ) )
  ;

ordinal
  : ord=( FIRST | SECOND | THIRD | FOURTH | FIFTH ) {
  ordinals.add(valueOf($ord.type));
  }
  ;

period
  : ( HOURS | MINUTES )
  ;

monthdays
  : ( monthday ( COMMA monthday )* )
  ;

monthday
  : day=( DIGIT | DIGITS ) {
    monthdays.add(Integer.parseInt($day.text)); }
  ;

weekdays
  : ( DAY {
        Set<Integer> allDays = integerSetOf(
            valueOf(MONDAY), valueOf(TUESDAY), valueOf(WEDNESDAY),
            valueOf(THURSDAY), valueOf(FRIDAY), valueOf(SATURDAY),
            valueOf(SUNDAY));
        if (ordinals.isEmpty()) {
          ordinals.addAll(allOrdinals);
          weekdays.addAll(allDays);
        } else {
          // <ordinal> day means <ordinal> day of the month,
          // not every day of the <ordinal> week.
          monthdays = ordinals;
          ordinals = new TreeSet<Integer>();
        }
      } | ( weekday (COMMA weekday)* {
        if (ordinals.isEmpty())
          ordinals.addAll(allOrdinals);
      }) )
  ;

weekday
  : dayname=( MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY | SATURDAY | SUNDAY ) {
    weekdays.add(valueOf($dayname.type));
    }
  ;

monthspec
  : ( MONTH {
      Set<Integer> allMonths = integerSetOf(
          valueOf(JANUARY), valueOf(FEBRUARY), valueOf(MARCH), valueOf(APRIL),
          valueOf(MAY), valueOf(JUNE), valueOf(JULY), valueOf(AUGUST),
          valueOf(SEPTEMBER), valueOf(OCTOBER), valueOf(NOVEMBER),
          valueOf(DECEMBER));
      months.addAll(allMonths);
      }
  |   months )
  ;

months
  : ( month (COMMA month)* )
  ;

month
  : monthname=( JANUARY | FEBRUARY | MARCH | APRIL | MAY | JUNE
  |   JULY | AUGUST | SEPTEMBER | OCTOBER | NOVEMBER | DECEMBER )
    { months.add(valueOf($monthname.type)); }
  ;

quarterspec
  : ( QUARTER {
      Set<Integer> quarterMonths = integerSetOf(
          valueOf(JANUARY), valueOf(APRIL), valueOf(JULY), valueOf(OCTOBER));
      months.addAll(quarterMonths); }
  |   ( quarter_ordinals MONTH OF QUARTER ) )
  ;

quarter_ordinals
  : ( month_of_quarter_ordinal (COMMA month_of_quarter_ordinal)* )
  ;

month_of_quarter_ordinal
  : offset=( FIRST | SECOND | THIRD ) {
      int jOffset = valueOf($offset.type) - 1;
      Set<Integer> offsetMonths = integerSetOf(
          jOffset + valueOf(JANUARY), jOffset + valueOf(APRIL),
          jOffset + valueOf(JULY), jOffset + valueOf(OCTOBER));
      months.addAll(offsetMonths); }
  ;

time_range
  : ( FROM (start_time = TIME { startTime = $start_time.text; })
      TO (end_time = TIME { endTime = $end_time.text; }) )
  ;


/* Time */
/* ANTLR does linear-approximate lookahead, not full LL(k).  This means that
 * if TIME were expressed like
 *   ( ( DIGIT  ':'  DIGIT DIGIT )
 *   | ( DIGIT DIGIT  ':'  DIGIT DIGIT ) )
 * then ANTLR would think that TIME might match the input
 *       DIGIT DIGIT DIGIT DIGIT
 * because each of the four input position matches at least one valid
 * alternative.  This introduces lexical non-determinism vs. DIGITS.
 *
 * To avoid this, TIME is split into two lexical rules, one of which gets its
 * type set manually.
 */
TIME      : ( DIGIT ':' '0'..'5' DIGIT );
TWO_DIGIT_HOUR_TIME
          : ( ( ( '0' DIGIT ) | ('1' DIGIT) | ('2' '0'..'3' ) )
              ':'
              ( '0'..'5' DIGIT )
              { $type = TIME; } );
SYNCHRONIZED : 'synchronized';

/* Ordinals */
FIRST     : ( '1st' | 'first' );
SECOND    : ( '2nd' | 'second' );
THIRD     : ( '3rd' | 'third' );
FOURTH    : ( '4th' );
FIFTH     : ( '5th' );

/* Special rule to avoid nondeterminism.  FOURTH_OR_FIFTH is never actually
 * returned but delegates to FOURTH or FIFTH.  For more info see:
 * http://big.corp.google.com/~dizer/blogger/2007/03/antlr-and-lexical-nondeterminism.html
 */
FOURTH_OR_FIFTH
          : ( ('fourth' { $type = FOURTH; })
          |   ('fifth'  { $type = FIFTH; }) );

/* Every day */
DAY       : 'day';
/* Weekdays */
MONDAY    : 'mon' ('day')? ;
TUESDAY   : 'tue' ('sday')? ;
WEDNESDAY : 'wed' ('nesday')? ;
THURSDAY  : 'thu' ('rsday')? ;
FRIDAY    : 'fri' ('day')? ;
SATURDAY  : 'sat' ('urday')? ;
SUNDAY    : 'sun' ('day')? ;

/* Months */
JANUARY   : 'jan' ('uary')? ;
FEBRUARY  : 'feb' ('ruary')? ;
MARCH     : 'mar' ('ch')? ;
APRIL     : 'apr' ('il')? ;
MAY       : 'may' ;
JUNE      : 'jun' ('e')? ;
JULY      : 'jul' ('y')? ;
AUGUST    : 'aug' ('ust')? ;
SEPTEMBER : 'sep' ('tember')? ;
OCTOBER   : 'oct' ('ober')? ;
NOVEMBER  : 'nov' ('ember')? ;
DECEMBER  : 'dec' ('ember')? ;

MONTH     : ( 'month' );
QUARTER   : ( 'quarter' );
EVERY     : ( 'every' );

HOURS     : ( 'hours' );
MINUTES   : ( 'mins' | 'minutes' );

/* Punctuation and helper words. */
COMMA     : ( ',' );
OF        : ( 'of' );
FROM      : ( 'from' );
TO        : ( 'to' );
WS        : ( ' ' | '\t' | '\n' | '\r' ) { $channel=HIDDEN; };

DIGIT  : ( '0'..'9' );
DIGITS    : ( ( DIGIT DIGIT DIGIT DIGIT DIGIT ) => ( DIGIT DIGIT DIGIT DIGIT DIGIT )
            | ( DIGIT DIGIT DIGIT DIGIT ) => ( DIGIT DIGIT DIGIT DIGIT )
            | ( DIGIT DIGIT DIGIT )
            | ( DIGIT DIGIT ) );

/* This turns lexer failures into parser failures.  This is necessary because
 * parser failures result in thrown exceptions, whereas lexer failures do not.
 */
UNKNOWN_TOKEN : ( . );
