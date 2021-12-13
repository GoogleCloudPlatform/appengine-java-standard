/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package javax.mail.internet;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formats ths date as specified by
 * draft-ietf-drums-msg-fmt-08 dated January 26, 2000
 * which supercedes RFC822.
 * <p/>
 * <p/>
 * The format used is <code>EEE, d MMM yyyy HH:mm:ss Z</code> and
 * locale is always US-ASCII.
 *
 * @version $Rev: 628009 $ $Date: 2008-02-15 04:53:02 -0600 (Fri, 15 Feb 2008) $
 */
public class MailDateFormat extends SimpleDateFormat {
    public MailDateFormat() {
        super("EEE, d MMM yyyy HH:mm:ss Z (z)", Locale.US);
    }

    public StringBuffer format(Date date, StringBuffer buffer, FieldPosition position) {
        return super.format(date, buffer, position);
    }

    /**
     * Parse a Mail date into a Date object.  This uses fairly 
     * lenient rules for the format because the Mail standards 
     * for dates accept multiple formats.
     * 
     * @param string   The input string.
     * @param position The position argument.
     * 
     * @return The Date object with the information inside. 
     */
    public Date parse(String string, ParsePosition position) {
        MailDateParser parser = new MailDateParser(string, position);
        try {
            return parser.parse(isLenient()); 
        } catch (ParseException e) {
            e.printStackTrace(); 
            // just return a null for any parsing errors 
            return null; 
        }
    }

    /**
     * The calendar cannot be set
     * @param calendar
     * @throws UnsupportedOperationException
     */
    public void setCalendar(Calendar calendar) {
        throw new UnsupportedOperationException();
    }

    /**
     * The format cannot be set
     * @param format
     * @throws UnsupportedOperationException
     */
    public void setNumberFormat(NumberFormat format) {
        throw new UnsupportedOperationException();
    }
    
    
    // utility class for handling date parsing issues 
    class MailDateParser {
        // our list of defined whitespace characters 
        static final String whitespace = " \t\r\n"; 
        
        // current parsing position 
        int current; 
        // our end parsing position 
        int endOffset; 
        // the date source string 
        String source; 
        // The parsing position. We update this as we move along and 
        // also for any parsing errors 
        ParsePosition pos; 
        
        public MailDateParser(String source, ParsePosition pos) 
        {
            this.source = source; 
            this.pos = pos; 
            // we start using the providing parsing index. 
            this.current = pos.getIndex(); 
            this.endOffset = source.length(); 
        }
        
        /**
         * Parse the timestamp, returning a date object. 
         * 
         * @param lenient The lenient setting from the Formatter object.
         * 
         * @return A Date object based off of parsing the date string.
         * @exception ParseException
         */
        public Date parse(boolean lenient) throws ParseException {
            // we just skip over any next date format, which means scanning ahead until we
            // find the first numeric character 
            locateNumeric(); 
            // the day can be either 1 or two digits 
            int day = parseNumber(1, 2); 
            // step over the delimiter 
            skipDateDelimiter(); 
            // parse off the month (which is in character format) 
            int month = parseMonth(); 
            // step over the delimiter 
            skipDateDelimiter(); 
            // now pull of the year, which can be either 2-digit or 4-digit 
            int year = parseYear(); 
            // white space is required here 
            skipRequiredWhiteSpace(); 
            // accept a 1 or 2 digit hour 
            int hour = parseNumber(1, 2);
            skipRequiredChar(':'); 
            // the minutes must be two digit 
            int minutes = parseNumber(2, 2);
            
            // the seconds are optional, but the ":" tells us if they are to 
            // be expected. 
            int seconds = 0; 
            if (skipOptionalChar(':')) {
                seconds = parseNumber(2, 2); 
            }
            // skip over the white space 
            skipWhiteSpace(); 
            // and finally the timezone information 
            int offset = parseTimeZone(); 
            
            // set the index of how far we've parsed this 
            pos.setIndex(current);
            
            // create a calendar for creating the date 
            Calendar greg = new GregorianCalendar(TimeZone.getTimeZone("GMT")); 
            // we inherit the leniency rules 
            greg.setLenient(lenient);
            greg.set(year, month, day, hour, minutes, seconds); 
            // now adjust by the offset.  This seems a little strange, but we  
            // need to negate the offset because this is a UTC calendar, so we need to 
            // apply the reverse adjustment.  for example, for the EST timezone, the offset 
            // value will be -300 (5 hours).  If the time was 15:00:00, the UTC adjusted time 
            // needs to be 20:00:00, so we subract -300 minutes. 
            greg.add(Calendar.MINUTE, -offset); 
            // now return this timestamp. 
            return greg.getTime(); 
        }
        
        
        /**
         * Skip over a position where there's a required value 
         * expected. 
         * 
         * @param ch     The required character.
         * 
         * @exception ParseException
         */
        private void skipRequiredChar(char ch) throws ParseException {
            if (current >= endOffset) {
                parseError("Delimiter '" + ch + "' expected"); 
            }
            if (source.charAt(current) != ch) {
                parseError("Delimiter '" + ch + "' expected"); 
            }
            current++; 
        }
        
        
        /**
         * Skip over a position where iff the position matches the
         * character
         * 
         * @param ch     The required character.
         * 
         * @return true if the character was there, false otherwise.
         * @exception ParseException
         */
        private boolean skipOptionalChar(char ch) {
            if (current >= endOffset) {
                return false; 
            }
            if (source.charAt(current) != ch) {
                return false; 
            }
            current++; 
            return true; 
        }
        
        
        /**
         * Skip over any white space characters until we find 
         * the next real bit of information.  Will scan completely to the 
         * end, if necessary. 
         */
        private void skipWhiteSpace() {
            while (current < endOffset) {
                // if this is not in the white space list, then success. 
                if (whitespace.indexOf(source.charAt(current)) < 0) {
                    return; 
                }
                current++; 
            }
            
            // everything used up, just return 
        }
        
        
        /**
         * Skip over any non-white space characters until we find 
         * either a whitespace char or the end of the data.
         */
        private void skipNonWhiteSpace() {
            while (current < endOffset) {
                // if this is not in the white space list, then success. 
                if (whitespace.indexOf(source.charAt(current)) >= 0) {
                    return; 
                }
                current++; 
            }
            
            // everything used up, just return 
        }
        
        
        /**
         * Skip over any white space characters until we find 
         * the next real bit of information.  Will scan completely to the 
         * end, if necessary. 
         */
        private void skipRequiredWhiteSpace() throws ParseException {
            int start = current; 
            
            while (current < endOffset) {
                // if this is not in the white space list, then success. 
                if (whitespace.indexOf(source.charAt(current)) < 0) {
                    // we must have at least one white space character 
                    if (start == current) {
                        parseError("White space character expected"); 
                    }
                    return; 
                }
                current++; 
            }
            // everything used up, just return, but make sure we had at least one  
            // white space
            if (start == current) {
                parseError("White space character expected"); 
            }
        }
        
        private void parseError(String message) throws ParseException {
            // we've got an error, set the index to the end. 
            pos.setErrorIndex(current);
            throw new ParseException(message, current); 
        }
        
        
        /**
         * Locate an expected numeric field. 
         * 
         * @exception ParseException
         */
        private void locateNumeric() throws ParseException {
            while (current < endOffset) {
                // found a digit?  we're done
                if (Character.isDigit(source.charAt(current))) {
                    return; 
                }
                current++; 
            }
            // we've got an error, set the index to the end. 
            parseError("Number field expected"); 
        }
        
        
        /**
         * Parse out an expected numeric field. 
         * 
         * @param minDigits The minimum number of digits we expect in this filed.
         * @param maxDigits The maximum number of digits expected.  Parsing will
         *                  stop at the first non-digit character.  An exception will
         *                  be thrown if the field contained more than maxDigits
         *                  in it.
         * 
         * @return The parsed numeric value. 
         * @exception ParseException
         */
        private int parseNumber(int minDigits, int maxDigits) throws ParseException {
            int start = current; 
            int accumulator = 0; 
            while (current < endOffset) {
                char ch = source.charAt(current); 
                // if this is not a digit character, then quit
                if (!Character.isDigit(ch)) {
                    break; 
                }
                // add the digit value into the accumulator 
                accumulator = accumulator * 10 + Character.digit(ch, 10); 
                current++; 
            }
            
            int fieldLength = current - start; 
            if (fieldLength < minDigits || fieldLength > maxDigits) {
                parseError("Invalid number field"); 
            }
            
            return accumulator; 
        }
        
        /**
         * Skip a delimiter between the date portions of the
         * string.  The IMAP internal date format uses "-", so 
         * we either accept a single "-" or any number of white
         * space characters (at least one required). 
         * 
         * @exception ParseException
         */
        private void skipDateDelimiter() throws ParseException {
            if (current >= endOffset) {
                parseError("Invalid date field delimiter"); 
            }
            
            if (source.charAt(current) == '-') {
                current++; 
            }
            else {
                // must be at least a single whitespace character 
                skipRequiredWhiteSpace(); 
            }
        }
        
        
        /**
         * Parse a character month name into the date month 
         * offset.
         * 
         * @return 
         * @exception ParseException
         */
        private int parseMonth() throws ParseException {
            if ((endOffset - current) < 3) {
                parseError("Invalid month"); 
            }
            
            int monthOffset = 0; 
            String month = source.substring(current, current + 3).toLowerCase();
            
            if (month.equals("jan")) {
                monthOffset = 0; 
            }
            else if (month.equals("feb")) {
                monthOffset = 1; 
            }
            else if (month.equals("mar")) {
                monthOffset = 2; 
            }
            else if (month.equals("apr")) {
                monthOffset = 3; 
            }
            else if (month.equals("may")) {
                monthOffset = 4; 
            }
            else if (month.equals("jun")) {
                monthOffset = 5; 
            }
            else if (month.equals("jul")) {
                monthOffset = 6; 
            }
            else if (month.equals("aug")) {
                monthOffset = 7; 
            }
            else if (month.equals("sep")) {
                monthOffset = 8; 
            }
            else if (month.equals("oct")) {
                monthOffset = 9; 
            }
            else if (month.equals("nov")) {
                monthOffset = 10; 
            }
            else if (month.equals("dec")) {
                monthOffset = 11; 
            }
            else {
                parseError("Invalid month"); 
            }
            
            // ok, this is valid.  Update the position and return it 
            current += 3;
            return monthOffset; 
        }
        
        /**
         * Parse off a year field that might be expressed as 
         * either 2 or 4 digits. 
         * 
         * @return The numeric value of the year. 
         * @exception ParseException
         */
        private int parseYear() throws ParseException {
            // the year is between 2 to 4 digits 
            int year = parseNumber(2, 4); 
            
            // the two digit years get some sort of adjustment attempted. 
            if (year < 50) {
                year += 2000; 
            }
            else if (year < 100) {
                year += 1990; 
            }
            return year; 
        }
        
        
        /**
         * Parse all of the different timezone options. 
         * 
         * @return The timezone offset.
         * @exception ParseException
         */
        private int parseTimeZone() throws ParseException {
            if (current >= endOffset) {
                parseError("Missing time zone"); 
            }
            
            // get the first non-blank. If this is a sign character, this 
            // is a zone offset.  
            char sign = source.charAt(current); 
            
            if (sign == '-' || sign == '+') {
                // need to step over the sign character 
                current++; 
                // a numeric timezone is always a 4 digit number, but 
                // expressed as minutes/seconds.  I'm too lazy to write a 
                // different parser that will bound on just a couple of characters, so 
                // we'll grab this as a single value and adjust     
                int zoneInfo = parseNumber(4, 4);
                
                int offset = (zoneInfo / 100) * 60 + (zoneInfo % 100); 
                // negate this, if we have a negativeo offset 
                if (sign == '-') {
                    offset = -offset; 
                }
                return offset; 
            }
            else {
                // need to parse this out using the obsolete zone names.  This will be 
                // either a 3-character code (defined set), or a single character military 
                // zone designation. 
                int start = current; 
                skipNonWhiteSpace(); 
                String name = source.substring(start, current).toUpperCase(); 
                
                if (name.length() == 1) {
                    return militaryZoneOffset(name); 
                }
                else if (name.length() <= 3) {
                    return namedZoneOffset(name); 
                }
                else {
                    parseError("Invalid time zone"); 
                }
                return 0; 
            }
        }
        
        
        /**
         * Parse the obsolete mail timezone specifiers. The
         * allowed set of timezones are terribly US centric. 
         * That's the spec.  The preferred timezone form is 
         * the +/-mmss form. 
         * 
         * @param name   The input name.
         * 
         * @return The standard timezone offset for the specifier.
         * @exception ParseException
         */
        private int namedZoneOffset(String name) throws ParseException {
            
            // NOTE:  This is "UT", NOT "UTC"
            if (name.equals("UT")) {
                return 0; 
            }
            else if (name.equals("GMT")) {
                return 0; 
            }
            else if (name.equals("EST")) {
                return -300; 
            }
            else if (name.equals("EDT")) {
                return -240; 
            }
            else if (name.equals("CST")) {
                return -360; 
            }
            else if (name.equals("CDT")) {
                return -300; 
            }
            else if (name.equals("MST")) {
                return -420; 
            }
            else if (name.equals("MDT")) {
                return -360; 
            }
            else if (name.equals("PST")) {
                return -480; 
            }
            else if (name.equals("PDT")) {
                return -420; 
            }
            else {
                parseError("Invalid time zone"); 
                return 0; 
            }
        }
        
        
        /**
         * Parse a single-character military timezone. 
         * 
         * @param name   The one-character name.
         * 
         * @return The offset corresponding to the military designation.
         */
        private int militaryZoneOffset(String name) throws ParseException {
            switch (Character.toUpperCase(name.charAt(0))) {
                case 'A':
                    return 60; 
                case 'B':
                    return 120; 
                case 'C':
                    return 180;
                case 'D':
                    return 240;
                case 'E':
                    return 300;
                case 'F':
                    return 360;
                case 'G':
                    return 420;
                case 'H':
                    return 480;
                case 'I':
                    return 540;
                case 'K':
                    return 600;
                case 'L':
                    return 660;
                case 'M':
                    return 720;
                case 'N':
                    return -60;
                case 'O':
                    return -120;
                case 'P':
                    return -180;
                case 'Q':
                    return -240;
                case 'R':
                    return -300;
                case 'S':
                    return -360;
                case 'T':
                    return -420;
                case 'U':
                    return -480;
                case 'V':
                    return -540;
                case 'W':
                    return -600;
                case 'X':
                    return -660;
                case 'Y':
                    return -720;
                case 'Z':
                    return 0;    
                default:
                    parseError("Invalid time zone");
                    return 0; 
            }
        }
    }
}
