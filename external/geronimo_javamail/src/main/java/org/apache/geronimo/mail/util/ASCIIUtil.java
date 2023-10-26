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

package org.apache.geronimo.mail.util;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * Set of utility classes for handling common encoding-related
 * manipulations.
 */
public class ASCIIUtil {

    /**
     * Test to see if this string contains only US-ASCII (i.e., 7-bit
     * ASCII) charactes.
     *
     * @param s      The test string.
     *
     * @return true if this is a valid 7-bit ASCII encoding, false if it
     *         contains any non-US ASCII characters.
     */
    static public boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isAscii(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test to see if a given character can be considered "valid" ASCII.
     * The excluded characters are the control characters less than
     * 32, 8-bit characters greater than 127, EXCEPT the CR, LF and
     * tab characters ARE considered value (all less than 32).
     *
     * @param ch     The test character.
     *
     * @return true if this character meets the "ascii-ness" criteria, false
     *         otherwise.
     */
    static public boolean isAscii(int ch) {
        // these are explicitly considered valid.
        if (ch == '\r' || ch == '\n' || ch == '\t') {
            return true;
        }

        // anything else outside the range is just plain wrong.
        if (ch >= 127 || ch < 32) {
            return false;
        }
        return true;
    }


    /**
     * Examine a stream of text and make a judgement on what encoding
     * type should be used for the text.  Ideally, we want to use 7bit
     * encoding to determine this, but we may need to use either quoted-printable
     * or base64.  The choice is made on the ratio of 7-bit characters to non-7bit.
     *
     * @param content     An input stream for the content we're examining.
     *
     * @exception IOException
     */
    public static String getTextTransferEncoding(InputStream content) throws IOException {

        // for efficiency, we'll read in blocks.
        BufferedInputStream in = new BufferedInputStream(content, 4096);

        int span = 0;            // span of characters without a line break.
        boolean containsLongLines = false;
        int asciiChars = 0;
        int nonAsciiChars = 0;

        while (true) {
            int ch = in.read();
            // if we hit an EOF here, go decide what type we've actually found.
            if (ch == -1) {
                break;
            }

            // we found a linebreak.  Reset the line length counters on either one.  We don't
            // really need to validate here.
            if (ch == '\n' || ch == '\r') {
                // hit a line end, reset our line length counter
                span = 0;
            }
            else {
                span++;
                // the text has long lines, we can't transfer this as unencoded text.
                if (span > 998) {
                    containsLongLines = true;
                }

                // non-ascii character, we have to transfer this in binary.
                if (!isAscii(ch)) {
                    nonAsciiChars++;
                }
                else {
                    asciiChars++;
                }
            }
        }

        // looking good so far, only valid chars here.
        if (nonAsciiChars == 0) {
            // does this contain long text lines?  We need to use a Q-P encoding which will
            // be only slightly longer, but handles folding the longer lines.
            if (containsLongLines) {
                return "quoted-printable";
            }
            else {
                // ideal!  Easiest one to handle.
                return "7bit";
            }
        }
        else {
            // mostly characters requiring encoding?  Base64 is our best bet.
            if (nonAsciiChars > asciiChars) {
                return "base64";
            }
            else {
                // Q-P encoding will use fewer bytes than the full Base64.
                return "quoted-printable";
            }
        }
    }


    /**
     * Examine a stream of text and make a judgement on what encoding
     * type should be used for the text.  Ideally, we want to use 7bit
     * encoding to determine this, but we may need to use either quoted-printable
     * or base64.  The choice is made on the ratio of 7-bit characters to non-7bit.
     *
     * @param content     A string for the content we're examining.
     */
    public static String getTextTransferEncoding(String content) {

        int asciiChars = 0;
        int nonAsciiChars = 0;

        for (int i = 0; i < content.length(); i++) {
            int ch = content.charAt(i);

            // non-ascii character, we have to transfer this in binary.
            if (!isAscii(ch)) {
                nonAsciiChars++;
            }
            else {
                asciiChars++;
            }
        }

        // looking good so far, only valid chars here.
        if (nonAsciiChars == 0) {
            // ideal!  Easiest one to handle.
            return "7bit";
        }
        else {
            // mostly characters requiring encoding?  Base64 is our best bet.
            if (nonAsciiChars > asciiChars) {
                return "base64";
            }
            else {
                // Q-P encoding will use fewer bytes than the full Base64.
                return "quoted-printable";
            }
        }
    }


    /**
     * Determine if the transfer encoding looks like it might be
     * valid ascii text, and thus transferable as 7bit code.  In
     * order for this to be true, all characters must be valid
     * 7-bit ASCII code AND all line breaks must be properly formed
     * (JUST '\r\n' sequences).  7-bit transfers also
     * typically have a line limit of 1000 bytes (998 + the CRLF), so any
     * stretch of charactes longer than that will also force Base64 encoding.
     *
     * @param content     An input stream for the content we're examining.
     *
     * @exception IOException
     */
    public static String getBinaryTransferEncoding(InputStream content) throws IOException {

        // for efficiency, we'll read in blocks.
        BufferedInputStream in = new BufferedInputStream(content, 4096);

        int previousChar = 0;
        int span = 0;            // span of characters without a line break.

        while (true) {
            int ch = in.read();
            // if we hit an EOF here, we've only found valid text so far, so we can transfer this as
            // 7-bit ascii.
            if (ch == -1) {
                return "7bit";
            }

            // we found a newline, this is only valid if the previous char was the '\r'
            if (ch == '\n') {
                // malformed linebreak?  force this to base64 encoding.
                if (previousChar != '\r') {
                    return "base64";
                }
                // hit a line end, reset our line length counter
                span = 0;
            }
            else {
                span++;
                // the text has long lines, we can't transfer this as unencoded text.
                if (span > 998) {
                    return "base64";
                }

                // non-ascii character, we have to transfer this in binary.
                if (!isAscii(ch)) {
                    return "base64";
                }
            }
            previousChar = ch;
        }
    }
}
