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

/**
 * @version $Rev: 729233 $ $Date: 2008-12-23 23:08:45 -0600 (Tue, 23 Dec 2008) $
 */
public class HeaderTokenizer {
    public static class Token {
        // Constant values from J2SE 1.4 API Docs (Constant values)
        public static final int ATOM = -1;
        public static final int COMMENT = -3;
        public static final int EOF = -4;
        public static final int QUOTEDSTRING = -2;
        private int _type;
        private String _value;

        public Token(int type, String value) {
            _type = type;
            _value = value;
        }

        public int getType() {
            return _type;
        }

        public String getValue() {
            return _value;
        }
    }

    private static final Token EOF = new Token(Token.EOF, null);
    // characters not allowed in MIME
    public static final String MIME = "()<>@,;:\\\"\t []/?=";
    // charaters not allowed in RFC822
    public static final String RFC822 = "()<>@,;:\\\"\t .[]";
    private static final String WHITE = " \t\n\r";
    private String _delimiters;
    private String _header;
    private boolean _skip;
    private int pos;

    public HeaderTokenizer(String header) {
        this(header, RFC822);
    }

    public HeaderTokenizer(String header, String delimiters) {
        this(header, delimiters, true);
    }

    public HeaderTokenizer(String header,
                           String delimiters,
                           boolean skipComments) {
        _skip = skipComments;
        _header = header;
        _delimiters = delimiters;
    }

    public String getRemainder() {
        return _header.substring(pos);
    }

    public Token next() throws ParseException {
        return readToken();
    }

    public Token peek() throws ParseException {
        int start = pos;
        try {
            return readToken();
        } finally {
            pos = start;
        }
    }

    /**
     * Read an ATOM token from the parsed header.
     *
     * @return A token containing the value of the atom token.
     */
    private Token readAtomicToken() {
        // skip to next delimiter
        int start = pos;
        while (++pos < _header.length()) {
            // break on the first non-atom character.
            char ch = _header.charAt(pos);
            if (_delimiters.indexOf(_header.charAt(pos)) != -1 || ch < 32 || ch >= 127) {
                break;
            }
        }

        return new Token(Token.ATOM, _header.substring(start, pos));
    }

    /**
     * Read the next token from the header.
     *
     * @return The next token from the header.  White space is skipped, and comment
     *         tokens are also skipped if indicated.
     * @exception ParseException
     */
    private Token readToken() throws ParseException {
        if (pos >= _header.length()) {
            return EOF;
        } else {
            char c = _header.charAt(pos);
            // comment token...read and skip over this
            if (c == '(') {
                Token comment = readComment();
                if (_skip) {
                    return readToken();
                } else {
                    return comment;
                }
                // quoted literal
            } else if (c == '\"') {
                return readQuotedString();
            // white space, eat this and find a real token.
            } else if (WHITE.indexOf(c) != -1) {
                eatWhiteSpace();
                return readToken();
            // either a CTL or special.  These characters have a self-defining token type.
            } else if (c < 32 || c >= 127 || _delimiters.indexOf(c) != -1) {
                pos++;
                return new Token((int)c, String.valueOf(c));
            } else {
                // start of an atom, parse it off.
                return readAtomicToken();
            }
        }
    }

    /**
     * Extract a substring from the header string and apply any
     * escaping/folding rules to the string.
     *
     * @param start  The starting offset in the header.
     * @param end    The header end offset + 1.
     *
     * @return The processed string value.
     * @exception ParseException
     */
    private String getEscapedValue(int start, int end) throws ParseException {
        StringBuffer value = new StringBuffer();

        for (int i = start; i < end; i++) {
            char ch = _header.charAt(i);
            // is this an escape character?
            if (ch == '\\') {
                i++;
                if (i == end) {
                    throw new ParseException("Invalid escape character");
                }
                value.append(_header.charAt(i));
            }
            // line breaks are ignored, except for naked '\n' characters, which are consider
            // parts of linear whitespace.
            else if (ch == '\r') {
                // see if this is a CRLF sequence, and skip the second if it is.
                if (i < end - 1 && _header.charAt(i + 1) == '\n') {
                    i++;
                }
            }
            else {
                // just append the ch value.
                value.append(ch);
            }
        }
        return value.toString();
    }

    /**
     * Read a comment from the header, applying nesting and escape
     * rules to the content.
     *
     * @return A comment token with the token value.
     * @exception ParseException
     */
    private Token readComment() throws ParseException {
        int start = pos + 1;
        int nesting = 1;

        boolean requiresEscaping = false;

        // skip to end of comment/string
        while (++pos < _header.length()) {
            char ch = _header.charAt(pos);
            if (ch == ')') {
                nesting--;
                if (nesting == 0) {
                    break;
                }
            }
            else if (ch == '(') {
                nesting++;
            }
            else if (ch == '\\') {
                pos++;
                requiresEscaping = true;
            }
            // we need to process line breaks also
            else if (ch == '\r') {
                requiresEscaping = true;
            }
        }

        if (nesting != 0) {
            throw new ParseException("Unbalanced comments");
        }

        String value;
        if (requiresEscaping) {
            value = getEscapedValue(start, pos);
        }
        else {
            value = _header.substring(start, pos++);
        }
        return new Token(Token.COMMENT, value);
    }

    /**
     * Parse out a quoted string from the header, applying escaping
     * rules to the value.
     *
     * @return The QUOTEDSTRING token with the value.
     * @exception ParseException
     */
    private Token readQuotedString() throws ParseException {
        int start = pos+1;
        boolean requiresEscaping = false;

        // skip to end of comment/string
        while (++pos < _header.length()) {
            char ch = _header.charAt(pos);
            if (ch == '"') {
                String value;
                if (requiresEscaping) {
                    value = getEscapedValue(start, pos++);
                }
                else {
                    value = _header.substring(start, pos++);
                }
                return new Token(Token.QUOTEDSTRING, value);
            }
            else if (ch == '\\') {
                pos++;
                requiresEscaping = true;
            }
            // we need to process line breaks also
            else if (ch == '\r') {
                requiresEscaping = true;
            }
        }

        throw new ParseException("Missing '\"'");
    }

    /**
     * Skip white space in the token string.
     */
    private void eatWhiteSpace() {
        // skip to end of whitespace
        while (++pos < _header.length()
                && WHITE.indexOf(_header.charAt(pos)) != -1)
            ;
    }
}
