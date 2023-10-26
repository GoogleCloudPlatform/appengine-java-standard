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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

class AddressParser {

    // the validation strictness levels, from most lenient to most conformant.
    static public final int NONSTRICT = 0;
    static public final int PARSE_HEADER = 1;
    static public final int STRICT = 2;

    // different mailbox types
    static protected final int UNKNOWN = 0;
    static protected final int ROUTE_ADDR = 1;
    static protected final int GROUP_ADDR = 2;
    static protected final int SIMPLE_ADDR = 3;

    // constants for token types.
    static protected final int END_OF_TOKENS = '\0';
    static protected final int PERIOD = '.';
    static protected final int LEFT_ANGLE = '<';
    static protected final int RIGHT_ANGLE = '>';
    static protected final int COMMA = ',';
    static protected final int AT_SIGN = '@';
    static protected final int SEMICOLON = ';';
    static protected final int COLON = ':';
    static protected final int QUOTED_LITERAL = '"';
    static protected final int DOMAIN_LITERAL = '[';
    static protected final int COMMENT = '(';
    static protected final int ATOM = 'A';
    static protected final int WHITESPACE = ' ';


    // the string we're parsing
    private String addresses;
    // the current parsing position
    private int    position;
    // the end position of the string
    private int    end;
    // the strictness flag
    private int validationLevel;

    public AddressParser(String addresses, int validation) {
        this.addresses = addresses;
        validationLevel = validation;
    }


    /**
     * Parse an address list into an array of internet addresses.
     *
     * @return An array containing all of the non-null addresses in the list.
     * @exception AddressException
     *                   Thrown for any validation errors.
     */
    public InternetAddress[] parseAddressList() throws AddressException
    {
        // get the address as a set of tokens we can process.
        TokenStream tokens = tokenizeAddress();

        // get an array list accumulator.
        ArrayList addressList = new ArrayList();

        // we process sections of the token stream until we run out of tokens.
        while (true) {
            // parse off a single address.  Address lists can have null elements,
            // so this might return a null value.  The null value does not get added
            // to the address accumulator.
            addressList.addAll(parseSingleAddress(tokens, false));
            // This token should be either a "," delimiter or a stream terminator.  If we're
            // at the end, time to get out.
            AddressToken token = tokens.nextToken();
            if (token.type == END_OF_TOKENS) {
                break;
            }
        }

        return (InternetAddress [])addressList.toArray(new InternetAddress[0]);
    }


    /**
     * Parse a single internet address.  This must be a single address,
     * not an address list.
     *
     * @exception AddressException
     */
    public InternetAddress parseAddress() throws AddressException
    {
        // get the address as a set of tokens we can process.
        TokenStream tokens = tokenizeAddress();

        // parse off a single address.  Address lists can have null elements,
        // so this might return a null value.  The null value does not get added
        // to the address accumulator.
        List addressList = parseSingleAddress(tokens, false);
        // we must get exactly one address back from this.
        if (addressList.isEmpty()) {
            throw new AddressException("Null address", addresses, 0);
        }
        // this could be a simple list of blank delimited tokens.  Ensure we only got one back.
        if (addressList.size() > 1) {
            throw new AddressException("Illegal Address", addresses, 0);
        }

        // This token must be a stream stream terminator, or we have an error.
        AddressToken token = tokens.nextToken();
        if (token.type != END_OF_TOKENS) {
            illegalAddress("Illegal Address", token);
        }

        return (InternetAddress)addressList.get(0);
    }


    /**
     * Validate an internet address.  This must be a single address,
     * not a list of addresses.  The address also must not contain
     * and personal information to be valid.
     *
     * @exception AddressException
     */
    public void validateAddress() throws AddressException
    {
        // get the address as a set of tokens we can process.
        TokenStream tokens = tokenizeAddress();

        // parse off a single address.  Address lists can have null elements,
        // so this might return a null value.  The null value does not get added
        // to the address accumulator.
        List addressList = parseSingleAddress(tokens, false);
        if (addressList.isEmpty()) {
            throw new AddressException("Null address", addresses, 0);
        }

        // this could be a simple list of blank delimited tokens.  Ensure we only got one back.
        if (addressList.size() > 1) {
            throw new AddressException("Illegal Address", addresses, 0);
        }

        InternetAddress address = (InternetAddress)addressList.get(0);

        // validation occurs on an address that's already been split into personal and address
        // data.
        if (address.personal != null) {
            throw new AddressException("Illegal Address", addresses, 0);
        }
        // This token must be a stream stream terminator, or we have an error.
        AddressToken token = tokens.nextToken();
        if (token.type != END_OF_TOKENS) {
            illegalAddress("Illegal Address", token);
        }
    }


    /**
     * Extract the set of address from a group Internet specification.
     *
     * @return An array containing all of the non-null addresses in the list.
     * @exception AddressException
     */
    public InternetAddress[] extractGroupList() throws AddressException
    {
        // get the address as a set of tokens we can process.
        TokenStream tokens = tokenizeAddress();

        // get an array list accumulator.
        ArrayList addresses = new ArrayList();

        AddressToken token = tokens.nextToken();

        // scan forward to the ':' that starts the group list.  If we don't find one,
        // this is an exception.
        while (token.type != COLON) {
            if (token.type == END_OF_TOKENS) {
                illegalAddress("Missing ':'", token);
            }
            token = tokens.nextToken();
        }

        // we process sections of the token stream until we run out of tokens.
        while (true) {
            // parse off a single address.  Address lists can have null elements,
            // so this might return a null value.  The null value does not get added
            // to the address accumulator.
            addresses.addAll(parseSingleAddress(tokens, true));
            // This token should be either a "," delimiter or a group terminator.  If we're
            // at the end, this is an error.
            token = tokens.nextToken();
            if (token.type == SEMICOLON) {
                break;
            }
            else if (token.type == END_OF_TOKENS) {
                illegalAddress("Missing ';'", token);
            }
        }

        return (InternetAddress [])addresses.toArray(new InternetAddress[0]);
    }


    /**
     * Parse out a single address from a string from a string
     * of address tokens, returning an InternetAddress object that
     * represents the address.
     *
     * @param tokens The token source for this address.
     *
     * @return A parsed out and constructed InternetAddress object for
     *         the next address.  Returns null if this is an "empty"
     *         address in a list.
     * @exception AddressException
     */
    private List parseSingleAddress(TokenStream tokens, boolean inGroup) throws AddressException
    {
        List parsedAddresses = new ArrayList();

        // index markers for personal information
        AddressToken personalStart = null;
        AddressToken personalEnd = null;

        // and similar bits for the address information.
        AddressToken addressStart = null;
        AddressToken addressEnd = null;

        // there is a fall-back set of rules allowed that will parse the address as a set of blank delimited
        // tokens.  However, we do NOT allow this if we encounter any tokens that fall outside of these
        // rules.  For example, comment fields and quoted strings will disallow the very lenient rule set.
        boolean nonStrictRules = true;

        // we don't know the type of address yet
        int addressType = UNKNOWN;

        // the parsing goes in two stages.  Stage one runs through the tokens locating the bounds
        // of the address we're working on, resolving the personal information, and also validating
        // some of the larger scale syntax features of an address (matched delimiters for routes and
        // groups, invalid nesting checks, etc.).

        // get the next token from the queue and save this.  We're going to scan ahead a bit to
        // figure out what type of address we're looking at, then reset to do the actually parsing
        // once we've figured out a form.
        AddressToken first = tokens.nextToken();
        // push it back on before starting processing.
        tokens.pushToken(first);

        // scan ahead for a trigger token that tells us what we've got.
        while (addressType == UNKNOWN) {

            AddressToken token = tokens.nextToken();
            switch (token.type) {
                // skip these for now...after we've processed everything and found that this is a simple
                // address form, then we'll check for a leading comment token in the first position and use
                // if as personal information.
                case COMMENT:
                    // comments do, however, denote that this must be parsed according to RFC822 rules.
                    nonStrictRules = false;
                    break;

                // a semi-colon when processing a group is an address terminator.  we need to
                // process this like a comma then
                case SEMICOLON:
                    if (inGroup) {
                        // we need to push the terminator back on for the caller to see.
                        tokens.pushToken(token);
                        // if we've not tagged any tokens as being the address beginning, so this must be a
                        // null address.
                        if (addressStart == null) {
                            // just return the empty list from this.
                            return parsedAddresses;
                        }
                        // the end token is the back part.
                        addressEnd = tokens.previousToken(token);
                        // without a '<' for a route addr, we can't distinguish address tokens from personal data.
                        // We'll use a leading comment, if there is one.
                        personalStart = null;
                        // this is just a simple form.
                        addressType = SIMPLE_ADDR;
                        break;
                    }

                // NOTE:  The above falls through if this is not a group.

                // any of these tokens are a real token that can be the start of an address.  Many of
                // them are not valid as first tokens in this context, but we flag them later if validation
                // has been requested.  For now, we just mark these as the potential address start.
                case DOMAIN_LITERAL:
                case QUOTED_LITERAL:
                    // this set of tokens require fuller RFC822 parsing, so turn off the flag.
                    nonStrictRules = false;

                case ATOM:
                case AT_SIGN:
                case PERIOD:
                    // if we're not determined the start of the address yet, then check to see if we
                    // need to consider this the personal start.
                    if (addressStart == null) {
                        if (personalStart == null) {
                            personalStart = token;
                        }
                        // This is the first real token of the address, which at this point can
                        // be either the personal info or the first token of the address.  If we hit
                        // an address terminator without encountering either a route trigger or group
                        // trigger, then this is the real address.
                        addressStart = token;
                    }
                    break;

                // a LEFT_ANGLE indicates we have a full RFC822 mailbox form.  The leading phrase
                // is the personal info.  The address is inside the brackets.
                case LEFT_ANGLE:
                    // a route address automatically switches off the blank-delimited token mode.
                    nonStrictRules = false;
                    // this is a route address
                    addressType = ROUTE_ADDR;
                    // the address is placed in the InternetAddress object without the route
                    // brackets, so our start is one past this.
                    addressStart = tokens.nextRealToken();
                    // push this back on the queue so the scanner picks it up properly.
                    tokens.pushToken(addressStart);
                    // make sure we flag the end of the personal section too.
                    if (personalStart != null) {
                        personalEnd = tokens.previousToken(token);
                    }
                    // scan the rest of a route address.
                    addressEnd = scanRouteAddress(tokens, false);
                    break;

                // a COLON indicates this is a group specifier...parse the group.
                case COLON:
                    // Colons would not be valid in simple lists, so turn it off.
                    nonStrictRules = false;
                    // if we're scanning a group, we shouldn't encounter a ":".  This is a
                    // recursion error if found.
                    if (inGroup) {
                        illegalAddress("Nested group element", token);
                    }
                    addressType = GROUP_ADDR;
                    // groups don't have any personal sections.
                    personalStart = null;
                    // our real start was back at the beginning
                    addressStart = first;
                    addressEnd = scanGroupAddress(tokens);
                    break;

                // a semi colon can the same as a comma if we're processing a group.


                // reached the end of string...this might be a null address, or one of the very simple name
                // forms used for non-strict RFC822 versions.  Reset, and try that form
                case END_OF_TOKENS:
                    // if we're scanning a group, we shouldn't encounter an end token.  This is an
                    // error if found.
                    if (inGroup) {
                        illegalAddress("Missing ';'", token);
                    }

                    // NOTE:  fall through from above.

                // this is either a terminator for an address list or a a group terminator.
                case COMMA:
                    // we need to push the terminator back on for the caller to see.
                    tokens.pushToken(token);
                    // if we've not tagged any tokens as being the address beginning, so this must be a
                    // null address.
                    if (addressStart == null) {
                        // just return the empty list from this.
                        return parsedAddresses;
                    }
                    // the end token is the back part.
                    addressEnd = tokens.previousToken(token);
                    // without a '<' for a route addr, we can't distinguish address tokens from personal data.
                    // We'll use a leading comment, if there is one.
                    personalStart = null;
                    // this is just a simple form.
                    addressType = SIMPLE_ADDR;
                    break;

                // right angle tokens are pushed, because parsing of the bracketing is not necessarily simple.
                // we need to flag these here.
                case RIGHT_ANGLE:
                    illegalAddress("Unexpected '>'", token);

            }
        }

        String personal = null;

        // if we have personal data, then convert it to a string value.
        if (personalStart != null) {
            TokenStream personalTokens = tokens.section(personalStart, personalEnd);
            personal = personalToString(personalTokens);
        }
        // if we have a simple address, then check the first token to see if it's a comment.  For simple addresses,
        // we'll accept the first comment token as the personal information.
        else {
            if (addressType == SIMPLE_ADDR && first.type == COMMENT) {
                personal = first.value;
            }
        }

        TokenStream addressTokens = tokens.section(addressStart, addressEnd);

        // if this is one of the strictly RFC822 types, then we always validate the address.  If this is a
        // a simple address, then we only validate if strict parsing rules are in effect or we've been asked
        // to validate.
        if (validationLevel != PARSE_HEADER) {
            switch (addressType) {
                case GROUP_ADDR:
                    validateGroup(addressTokens);
                    break;

                case ROUTE_ADDR:
                    validateRouteAddr(addressTokens, false);
                    break;

                case SIMPLE_ADDR:
                    // this is a conditional validation
                    validateSimpleAddress(addressTokens);
                    break;
            }
        }

        // more complex addresses and addresses containing tokens other than just simple addresses
        // need proper handling.
        if (validationLevel != NONSTRICT || addressType != SIMPLE_ADDR || !nonStrictRules) {
            // we might have traversed this already when we validated, so reset the
            // position before using this again.
            addressTokens.reset();
            String address = addressToString(addressTokens);

            // get the parsed out sections as string values.
            InternetAddress result = new InternetAddress();
            result.setAddress(address);
            try {
                result.setPersonal(personal);
            } catch (UnsupportedEncodingException e) {
            }
            // even though we have a single address, we return this as an array.  Simple addresses
            // can be produce an array of items, so we need to return everything.
            parsedAddresses.add(result);
            return parsedAddresses;
        }
        else {
            addressTokens.reset();

            TokenStream nextAddress = addressTokens.getBlankDelimitedToken();
            while (nextAddress != null) {
                String address = addressToString(nextAddress);
                // get the parsed out sections as string values.
                InternetAddress result = new InternetAddress();
                result.setAddress(address);
                parsedAddresses.add(result);
                nextAddress = addressTokens.getBlankDelimitedToken();
            }
            return parsedAddresses;
        }
    }


    /**
     * Scan the token stream, parsing off a route addr spec.  This
     * will do some basic syntax validation, but will not actually
     * validate any of the address information.  Comments will be
     * discarded.
     *
     * @param tokens The stream of tokens.
     *
     * @return The last token of the route address (the one preceeding the
     *         terminating '>'.
     */
    private AddressToken scanRouteAddress(TokenStream tokens, boolean inGroup) throws AddressException {
        // get the first token and ensure we have something between the "<" and ">".
        AddressToken token = tokens.nextRealToken();
        // the last processed non-whitespace token, which is the actual address end once the
        // right angle bracket is encountered.

        AddressToken previous = null;

        // if this route-addr has route information, the first token after the '<' must be a '@'.
        // this determines if/where a colon or comma can appear.
        boolean inRoute = token.type == AT_SIGN;

        // now scan until we reach the terminator.  The only validation is done on illegal characters.
        while (true) {
            switch (token.type) {
                // The following tokens are all valid between the brackets, so just skip over them.
                case ATOM:
                case QUOTED_LITERAL:
                case DOMAIN_LITERAL:
                case PERIOD:
                case AT_SIGN:
                    break;

                case COLON:
                    // if not processing route information, this is illegal.
                    if (!inRoute) {
                        illegalAddress("Unexpected ':'", token);
                    }
                    // this is the end of the route information, the rules now change.
                    inRoute = false;
                    break;

                case COMMA:
                    // if not processing route information, this is illegal.
                    if (!inRoute) {
                        illegalAddress("Unexpected ','", token);
                    }
                    break;

                case RIGHT_ANGLE:
                    // if previous is null, we've had a route address which is "<>".  That's illegal.
                    if (previous == null) {
                        illegalAddress("Illegal address", token);
                    }
                    // step to the next token..this had better be either a comma for another address or
                    // the very end of the address list .
                    token = tokens.nextRealToken();
                    // if we're scanning part of a group, then the allowed terminators are either ',' or ';'.
                    if (inGroup) {
                        if (token.type != COMMA && token.type != SEMICOLON) {
                            illegalAddress("Illegal address", token);
                        }
                    }
                    // a normal address should have either a ',' for a list or the end.
                    else {
                        if (token.type != COMMA && token.type != END_OF_TOKENS) {
                            illegalAddress("Illegal address", token);
                        }
                    }
                    // we need to push the termination token back on.
                    tokens.pushToken(token);
                    // return the previous token as the updated position.
                    return previous;

                case END_OF_TOKENS:
                    illegalAddress("Missing '>'", token);

                // now for the illegal ones in this context.
                case SEMICOLON:
                    illegalAddress("Unexpected ';'", token);

                case LEFT_ANGLE:
                    illegalAddress("Unexpected '<'", token);
            }
            // remember the previous token.
            previous = token;
            token = tokens.nextRealToken();
        }
    }


    /**
     * Scan the token stream, parsing off a group address.  This
     * will do some basic syntax validation, but will not actually
     * validate any of the address information.  Comments will be
     * ignored.
     *
     * @param tokens The stream of tokens.
     *
     * @return The last token of the group address (the terminating ':").
     */
    private AddressToken scanGroupAddress(TokenStream tokens) throws AddressException {
        // A group does not require that there be anything between the ':' and ';".  This is
        // just a group with an empty list.
        AddressToken token = tokens.nextRealToken();

        // now scan until we reach the terminator.  The only validation is done on illegal characters.
        while (true) {
            switch (token.type) {
                // The following tokens are all valid in group addresses, so just skip over them.
                case ATOM:
                case QUOTED_LITERAL:
                case DOMAIN_LITERAL:
                case PERIOD:
                case AT_SIGN:
                case COMMA:
                    break;

                case COLON:
                     illegalAddress("Nested group", token);

                // route address within a group specifier....we need to at least verify the bracket nesting
                // and higher level syntax of the route.
                case LEFT_ANGLE:
                    scanRouteAddress(tokens, true);
                    break;

                // the only allowed terminator is the ';'
                case END_OF_TOKENS:
                    illegalAddress("Missing ';'", token);

                // now for the illegal ones in this context.
                case SEMICOLON:
                    // verify there's nothing illegal after this.
                    AddressToken next = tokens.nextRealToken();
                    if (next.type != COMMA && next.type != END_OF_TOKENS) {
                        illegalAddress("Illegal address", token);
                    }
                    // don't forget to put this back on...our caller will need it.
                    tokens.pushToken(next);
                    return token;

                case RIGHT_ANGLE:
                    illegalAddress("Unexpected '>'", token);
            }
            token = tokens.nextRealToken();
        }
    }


    /**
     * Parse the provided internet address into a set of tokens.  This
     * phase only does a syntax check on the tokens.  The interpretation
     * of the tokens is the next phase.
     *
     * @exception AddressException
     */
    private TokenStream tokenizeAddress() throws AddressException {

        // get a list for the set of tokens
        TokenStream tokens = new TokenStream();

        end = addresses.length();    // our parsing end marker

        // now scan along the string looking for the special characters in an internet address.
        while (moreCharacters()) {
            char ch = currentChar();

            switch (ch) {
                // start of a comment bit...ignore everything until we hit a closing paren.
                case '(':
                    scanComment(tokens);
                    break;
                // a closing paren found outside of normal processing.
                case ')':
                    syntaxError("Unexpected ')'", position);


                // start of a quoted string
                case '"':
                    scanQuotedLiteral(tokens);
                    break;
                // domain literal
                case '[':
                    scanDomainLiteral(tokens);
                    break;

                // a naked closing bracket...not valid except as part of a domain literal.
                case ']':
                    syntaxError("Unexpected ']'", position);

                // special character delimiters
                case '<':
                    tokens.addToken(new AddressToken(LEFT_ANGLE, position));
                    nextChar();
                    break;

                // a naked closing bracket...not valid without a starting one, but
                // we need to handle this in context.
                case '>':
                    tokens.addToken(new AddressToken(RIGHT_ANGLE, position));
                    nextChar();
                    break;
                case ':':
                    tokens.addToken(new AddressToken(COLON, position));
                    nextChar();
                    break;
                case ',':
                    tokens.addToken(new AddressToken(COMMA, position));
                    nextChar();
                    break;
                case '.':
                    tokens.addToken(new AddressToken(PERIOD, position));
                    nextChar();
                    break;
                case ';':
                    tokens.addToken(new AddressToken(SEMICOLON, position));
                    nextChar();
                    break;
                case '@':
                    tokens.addToken(new AddressToken(AT_SIGN, position));
                    nextChar();
                    break;

                // white space characters.  These are mostly token delimiters, but there are some relaxed
                // situations where they get processed, so we need to add a white space token for the first
                // one we encounter in a span.
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    // add a single white space token
                    tokens.addToken(new AddressToken(WHITESPACE, position));

                    nextChar();
                    // step over any space characters, leaving us positioned either at the end
                    // or the first
                    while (moreCharacters()) {
                        char nextChar = currentChar();
                        if (nextChar == ' ' || nextChar == '\t' || nextChar == '\r' || nextChar == '\n') {
                            nextChar();
                        }
                        else {
                            break;
                        }
                    }
                    break;

                // potentially an atom...if it starts with an allowed atom character, we
                // parse out the token, otherwise this is invalid.
                default:
                    if (ch < 040 || ch >= 0177) {
                        syntaxError("Illegal character in address", position);
                    }

                    scanAtom(tokens);
                    break;
            }
        }

        // for this end marker, give an end position.
        tokens.addToken(new AddressToken(END_OF_TOKENS, addresses.length()));
        return tokens;
    }


    /**
     * Step to the next character position while parsing.
     */
    private void nextChar() {
        position++;
    }


    /**
     * Retrieve the character at the current parsing position.
     *
     * @return The current character.
     */
    private char currentChar() {
        return addresses.charAt(position);
    }

    /**
     * Test if there are more characters left to parse.
     *
     * @return True if we've hit the last character, false otherwise.
     */
    private boolean moreCharacters() {
        return position < end;
    }


    /**
     * Parse a quoted string as specified by the RFC822 specification.
     *
     * @param tokens The TokenStream where the parsed out token is added.
     */
    private void scanQuotedLiteral(TokenStream tokens) throws AddressException {
        StringBuffer value = new StringBuffer();

        // save the start position for the token.
        int startPosition = position;
        // step over the quote delimiter.
        nextChar();

        while (moreCharacters()) {
            char ch = currentChar();

            // is this an escape char?
            if (ch == '\\') {
                // step past this, and grab the following character
                nextChar();
                if (!moreCharacters()) {
                    syntaxError("Missing '\"'", position);
                }
                value.append(currentChar());
            }
            // end of the string?
            else if (ch == '"') {
                // return the constructed string.
                tokens.addToken(new AddressToken(value.toString(), QUOTED_LITERAL, position));
                // step over the close delimiter for the benefit of the next token.
                nextChar();
                return;
            }
            // the RFC822 spec disallows CR characters.
            else if (ch == '\r') {
                syntaxError("Illegal line end in literal", position);
            }
            else
            {
                value.append(ch);
            }
            nextChar();
        }
        // missing delimiter
        syntaxError("Missing '\"'", position);
    }


    /**
     * Parse a domain literal as specified by the RFC822 specification.
     *
     * @param tokens The TokenStream where the parsed out token is added.
     */
    private void scanDomainLiteral(TokenStream tokens) throws AddressException {
        StringBuffer value = new StringBuffer();

        int startPosition = position;
        // step over the quote delimiter.
        nextChar();

        while (moreCharacters()) {
            char ch = currentChar();

            // is this an escape char?
            if (ch == '\\') {
                // because domain literals don't get extra escaping, we render them
                // with the escaped characters intact.  Therefore, append the '\' escape
                // first, then append the escaped character without examination.
                value.append(currentChar());
                // step past this, and grab the following character
                nextChar();
                if (!moreCharacters()) {
                    syntaxError("Missing '\"'", position);
                }
                value.append(currentChar());
            }
            // end of the string?
            else if (ch == ']') {
                // return the constructed string.
                tokens.addToken(new AddressToken(value.toString(), DOMAIN_LITERAL, startPosition));
                // step over the close delimiter for the benefit of the next token.
                nextChar();
                return;
            }
            // the RFC822 spec says no nesting
            else if (ch == '[') {
                syntaxError("Unexpected '['", position);
            }
            // carriage returns are similarly illegal.
            else if (ch == '\r') {
                syntaxError("Illegal line end in domain literal", position);
            }
            else
            {
                value.append(ch);
            }
            nextChar();
        }
        // missing delimiter
        syntaxError("Missing ']'", position);
    }

    /**
     * Scan an atom in an internet address, using the RFC822 rules
     * for atom delimiters.
     *
     * @param tokens The TokenStream where the parsed out token is added.
     */
    private void scanAtom(TokenStream tokens) throws AddressException {
        int start = position;
        nextChar();
        while (moreCharacters()) {

            char ch = currentChar();
            if (isAtom(ch)) {
                nextChar();
            }
            else {
                break;
            }
        }

        // return the scanned part of the string.
        tokens.addToken(new AddressToken(addresses.substring(start, position), ATOM, start));
    }


    /**
     * Parse an internet address comment field as specified by
     * RFC822.  Includes support for quoted characters and nesting.
     *
     * @param tokens The TokenStream where the parsed out token is added.
     */
    private void scanComment(TokenStream tokens) throws AddressException {
        StringBuffer value = new StringBuffer();

        int startPosition = position;
        // step past the start character
        nextChar();

        // we're at the top nesting level on the comment.
        int nest = 1;

        // scan while we have more characters.
        while (moreCharacters()) {
            char ch = currentChar();
            // escape character?
            if (ch == '\\') {
                // step over this...if escaped, we must have at least one more character
                // in the string.
                nextChar();
                if (!moreCharacters()) {
                    syntaxError("Missing ')'", position);
                }
                value.append(currentChar());
            }
            // nested comment?
            else if (ch == '(') {
                // step the nesting level...we treat the comment as a single unit, with the delimiters
                // for the nested comments embedded in the middle
                nest++;
                value.append(ch);
            }
            // is this the comment close?
            else if (ch == ')') {
                // reduce the nesting level.  If we still have more to process, add the delimiter character
                // and keep going.
                nest--;
                if (nest > 0) {
                    value.append(ch);
                }
                else {
                    // step past this and return.  The outermost comment delimiter is not included in
                    // the string value, since this is frequently used as personal data on the
                    // InternetAddress objects.
                    nextChar();
                    tokens.addToken(new AddressToken(value.toString(), COMMENT, startPosition));
                    return;
                }
            }
            else if (ch == '\r') {
                syntaxError("Illegal line end in comment", position);
            }
            else {
                value.append(ch);
            }
            // step to the next character.
            nextChar();
        }
        // ran out of data before seeing the closing bit, not good
        syntaxError("Missing ')'", position);
    }


    /**
     * Validate the syntax of an RFC822 group internet address specification.
     *
     * @param tokens The stream of tokens for the address.
     *
     * @exception AddressException
     */
    private void validateGroup(TokenStream tokens) throws AddressException {
        // we know already this is an address in the form "phrase:group;".  Now we need to validate the
        // elements.

        int phraseCount = 0;

        AddressToken token = tokens.nextRealToken();
        // now scan to the semi color, ensuring we have only word or comment tokens.
        while (token.type != COLON) {
            // only these tokens are allowed here.
            if (token.type != ATOM && token.type != QUOTED_LITERAL) {
                invalidToken(token);
            }
            phraseCount++;
            token = tokens.nextRealToken();
        }


        // RFC822 groups require a leading phrase in group specifiers.
        if (phraseCount == 0) {
            illegalAddress("Missing group identifier phrase", token);
        }

        // now we do the remainder of the parsing using the initial phrase list as the sink...the entire
        // address will be converted to a string later.

        // ok, we only know this has been valid up to the ":", now we have some real checks to perform.
        while (true) {
            // go scan off a mailbox.  if everything goes according to plan, we should be positioned at either
            // a comma or a semicolon.
            validateGroupMailbox(tokens);

            token = tokens.nextRealToken();

            // we're at the end of the group.  Make sure this is truely the end.
            if (token.type == SEMICOLON) {
                token = tokens.nextRealToken();
                if (token.type != END_OF_TOKENS) {
                    illegalAddress("Illegal group address", token);
                }
                return;
            }

            // if not a semicolon, this better be a comma.
            else if (token.type != COMMA) {
                illegalAddress("Illegal group address", token);
            }
        }
    }


    /**
     * Validate the syntax of single mailbox within a group address.
     *
     * @param tokens The stream of tokens representing the address.
     *
     * @exception AddressException
     */
    private void validateGroupMailbox(TokenStream tokens) throws AddressException {
        AddressToken first = tokens.nextRealToken();
        // is this just a null address in the list?  then push the terminator back and return.
        if (first.type == COMMA || first.type == SEMICOLON) {
            tokens.pushToken(first);
            return;
        }

        // now we need to scan ahead to see if we can determine the type.
        AddressToken token = first;


        // we need to scan forward to figure out what sort of address this is.
        while (first != null) {
            switch (token.type) {
                // until we know the context, these are all just ignored.
                case QUOTED_LITERAL:
                case ATOM:
                    break;

                // a LEFT_ANGLE indicates we have a full RFC822 mailbox form.  The leading phrase
                // is the personal info.  The address is inside the brackets.
                case LEFT_ANGLE:
                    tokens.pushToken(first);
                    validatePhrase(tokens, false);
                    validateRouteAddr(tokens, true);
                    return;

                // we've hit a period as the first non-word token.  This should be part of a local-part
                // of an address.
                case PERIOD:
                // we've hit an "@" as the first non-word token.  This is probably a simple address in
                // the form "user@domain".
                case AT_SIGN:
                    tokens.pushToken(first);
                    validateAddressSpec(tokens);
                    return;

                // reached the end of string...this might be a null address, or one of the very simple name
                // forms used for non-strict RFC822 versions.  Reset, and try that form
                case COMMA:
                // this is the end of the group...handle it like a comma for now.
                case SEMICOLON:
                    tokens.pushToken(first);
                    validateAddressSpec(tokens);
                    return;

                case END_OF_TOKENS:
                    illegalAddress("Missing ';'", token);

            }
            token = tokens.nextRealToken();
        }
    }


    /**
     * Utility method for throwing an AddressException caused by an
     * unexpected primitive token.
     *
     * @param token  The token causing the problem (must not be a value type token).
     *
     * @exception AddressException
     */
    private void invalidToken(AddressToken token) throws AddressException {
        illegalAddress("Unexpected '" + token.type + "'", token);
    }


    /**
     * Raise an error about illegal syntax.
     *
     * @param message  The message used in the thrown exception.
     * @param position The parsing position within the string.
     *
     * @exception AddressException
     */
    private void syntaxError(String message, int position) throws AddressException
    {
        throw new AddressException(message, addresses, position);
    }


    /**
     * Throw an exception based on the position of an invalid token.
     *
     * @param message The exception message.
     * @param token   The token causing the error.  This tokens position is used
     *                in the exception information.
     */
    private void illegalAddress(String message, AddressToken token) throws AddressException {
        throw new AddressException(message, addresses, token.position);
    }


    /**
     * Validate that a required phrase exists.
     *
     * @param tokens   The set of tokens to validate. positioned at the phrase start.
     * @param required A flag indicating whether the phrase is optional or required.
     *
     * @exception AddressException
     */
    private void validatePhrase(TokenStream tokens, boolean required) throws AddressException {
        // we need to have at least one WORD token in the phrase...everything is optional
        // after that.
        AddressToken token = tokens.nextRealToken();
        if (token.type != ATOM && token.type != QUOTED_LITERAL) {
            if (required) {
                illegalAddress("Missing group phrase", token);
            }
        }

        // now scan forward to the end of the phrase
        token = tokens.nextRealToken();
        while (token.type == ATOM || token.type == QUOTED_LITERAL) {
            token = tokens.nextRealToken();
        }
    }


    /**
     * validate a routeaddr specification
     *
     * @param tokens  The tokens representing the address portion (personal information
     *                already removed).
     * @param ingroup true indicates we're validating a route address inside a
     *                group list.  false indicates we're validating a standalone
     *                address.
     *
     * @exception AddressException
     */
    private void validateRouteAddr(TokenStream tokens, boolean ingroup) throws AddressException {
        // get the next real token.
        AddressToken token = tokens.nextRealToken();
        // if this is an at sign, then we have a list of domains to parse.
        if (token.type == AT_SIGN) {
            // push the marker token back in for the route parser, and step past that part.
            tokens.pushToken(token);
            validateRoute(tokens);
        }
        else {
            // we need to push this back on to validate the local part.
            tokens.pushToken(token);
        }

        // now we expect to see an address spec.
        validateAddressSpec(tokens);

        token = tokens.nextRealToken();
        if (ingroup) {
            // if we're validating within a group specification, the angle brackets are still there (and
            // required).
            if (token.type != RIGHT_ANGLE) {
                illegalAddress("Missing '>'", token);
            }
        }
        else {
            // the angle brackets were removed to make this an address, so we should be done.  Make sure we
            // have a terminator here.
            if (token.type != END_OF_TOKENS) {
                illegalAddress("Illegal Address", token);
            }
        }
    }



    /**
     * Validate a simple address in the form "user@domain".
     *
     * @param tokens The stream of tokens representing the address.
     */
    private void validateSimpleAddress(TokenStream tokens) throws AddressException {

        // the validation routines occur after addresses have been split into
        // personal and address forms.  Therefore, our validation begins directly
        // with the first token.
        validateAddressSpec(tokens);

        // get the next token and see if there is something here...anything but the terminator is an error
        AddressToken token = tokens.nextRealToken();
        if (token.type != END_OF_TOKENS) {
            illegalAddress("Illegal Address", token);
        }
    }

    /**
     * Validate the addr-spec portion of an address.  RFC822 requires
     * this be of the form "local-part@domain".  However, javamail also
     * allows simple address of the form "local-part".  We only require
     * the domain if an '@' is encountered.
     *
     * @param tokens
     */
    private void validateAddressSpec(TokenStream tokens) throws AddressException {
        // all addresses, even the simple ones, must have at least a local part.
        validateLocalPart(tokens);

        // now see if we have a domain portion to look at.
        AddressToken token = tokens.nextRealToken();
        if (token.type == AT_SIGN) {
            validateDomain(tokens);
        }
        else {
            // put this back for termination
            tokens.pushToken(token);
        }

    }


    /**
     * Validate the route portion of a route-addr.  This is a list
     * of domain values in the form 1#("@" domain) ":".
     *
     * @param tokens The token stream holding the address information.
     */
    private void validateRoute(TokenStream tokens) throws AddressException {
        while (true) {
            AddressToken token = tokens.nextRealToken();
            // if this is the first part of the list, go parse off a domain
            if (token.type == AT_SIGN) {
                validateDomain(tokens);
            }
            // another element in the list?  Go around again
            else if (token.type == COMMA) {
                continue;
            }
            // the list is terminated by a colon...stop this part of the validation once we hit one.
            else if (token.type == COLON) {
                return;
            }
            // the list is terminated by a colon.  If this isn't one of those, we have an error.
            else {
                illegalAddress("Missing ':'", token);
            }
        }
    }


    /**
     * Parse the local part of an address spec.  The local part
     * is a series of "words" separated by ".".
     */
    private void validateLocalPart(TokenStream tokens) throws AddressException {
        while (true) {
            // get the token.
            AddressToken token = tokens.nextRealToken();

            // this must be either an atom or a literal.
            if (token.type != ATOM && token.type != QUOTED_LITERAL) {
                illegalAddress("Invalid local part", token);
            }

            // get the next token (white space and comments ignored)
            token = tokens.nextRealToken();
            // if this is a period, we continue parsing
            if (token.type != PERIOD) {
                tokens.pushToken(token);
                // return the token
                return;
            }
        }
    }



    /**
     * Parse a domain name of the form sub-domain *("." sub-domain).
     * a sub-domain is either an atom or a domain-literal.
     */
    private void validateDomain(TokenStream tokens) throws AddressException {
        while (true) {
            // get the token.
            AddressToken token = tokens.nextRealToken();

            // this must be either an atom or a domain literal.
            if (token.type != ATOM && token.type != DOMAIN_LITERAL) {
                illegalAddress("Invalid domain", token);
            }

            // get the next token (white space is ignored)
            token = tokens.nextRealToken();
            // if this is a period, we continue parsing
            if (token.type != PERIOD) {
                // return the token
                tokens.pushToken(token);
                return;
            }
        }
    }

    /**
     * Convert a list of word tokens into a phrase string.  The
     * rules for this are a little hard to puzzle out, but there
     * is a logic to it.  If the list is empty, the phrase is
     * just a null value.
     *
     * If we have a phrase, then the quoted strings need to
     * handled appropriately.  In multi-token phrases, the
     * quoted literals are concatenated with the quotes intact,
     * regardless of content.  Thus a phrase that comes in like this:
     *
     * "Geronimo" Apache
     *
     * gets converted back to the same string.
     *
     * If there is just a single token in the phrase, AND the token
     * is a quoted string AND the string does not contain embedded
     * special characters ("\.,@<>()[]:;), then the phrase
     * is expressed as an atom.  Thus the literal
     *
     *    "Geronimo"
     *
     * becomes
     *
     *    Geronimo
     *
     * but
     *
     *    "(Geronimo)"
     *
     * remains
     *
     *    "(Geronimo)"
     *
     * Note that we're generating a canonical form of the phrase,
     * which removes comments and reduces linear whitespace down
     * to a single separator token.
     *
     * @param phrase An array list of phrase tokens (which may be empty).
     */
    private String personalToString(TokenStream tokens) {

        // no tokens in the stream?  This is a null value.
        AddressToken token = tokens.nextToken();

        if (token.type == END_OF_TOKENS) {
            return null;
        }

        AddressToken next = tokens.nextToken();

        // single element phrases get special treatment.
        if (next.type == END_OF_TOKENS) {
            // this can be used directly...if it contains special characters, quoting will be
            // performed when it's converted to a string value.
            return token.value;
        }

        // reset to the beginning
        tokens.pushToken(token);

        // have at least two tokens,
        StringBuffer buffer = new StringBuffer();

        // get the first token.  After the first, we add these as blank delimited values.
        token = tokens.nextToken();
        addTokenValue(token, buffer);

        token = tokens.nextToken();
        while (token.type != END_OF_TOKENS) {
            // add a blank separator
            buffer.append(' ');
            // now add the next tokens value
            addTokenValue(token, buffer);
            token = tokens.nextToken();
        }
        // and return the canonicalized value
        return buffer.toString();
    }


    /**
     * take a canonicalized set of address tokens and reformat it back into a string value,
     * inserting whitespace where appropriate.
     *
     * @param tokens The set of tokens representing the address.
     *
     * @return The string value of the tokens.
     */
    private String addressToString(TokenStream tokens) {
        StringBuffer buffer = new StringBuffer();

        // this flag controls whether we insert a blank delimiter between tokens as
        // we advance through the list.  Blanks are only inserted between consequtive value tokens.
        // Initially, this is false, then we flip it to true whenever we add a value token, and
        // back to false for any special character token.
        boolean spaceRequired = false;

        // we use nextToken rather than nextRealToken(), since we need to process the comments also.
        AddressToken token = tokens.nextToken();

        // now add each of the tokens
        while (token.type != END_OF_TOKENS) {
            switch (token.type) {
                // the word tokens are the only ones where we need to worry about adding
                // whitespace delimiters.
                case ATOM:
                case QUOTED_LITERAL:
                    // was the last token also a word?  Insert a blank first.
                    if (spaceRequired) {
                        buffer.append(' ');
                    }
                    addTokenValue(token, buffer);
                    // let the next iteration know we just added a word to the list.
                    spaceRequired = true;
                    break;

                // these special characters are just added in.  The constants for the character types
                // were carefully selected to be the character value in question.  This allows us to
                // just append the value.
                case LEFT_ANGLE:
                case RIGHT_ANGLE:
                case COMMA:
                case COLON:
                case AT_SIGN:
                case SEMICOLON:
                case PERIOD:
                    buffer.append((char)token.type);
                    // no spaces around specials
                    spaceRequired = false;
                    break;

                // Domain literals self delimiting...we can just append them and turn off the space flag.
                case DOMAIN_LITERAL:
                    addTokenValue(token, buffer);
                    spaceRequired = false;
                    break;

                // Comments are also self delimitin.
                case COMMENT:
                    addTokenValue(token, buffer);
                    spaceRequired = false;
                    break;
            }
            token = tokens.nextToken();
        }
        return buffer.toString();
    }


    /**
     * Append a value token on to a string buffer used to create
     * the canonicalized string value.
     *
     * @param token  The token we're adding.
     * @param buffer The target string buffer.
     */
    private void addTokenValue(AddressToken token, StringBuffer buffer) {
        // atom values can be added directly.
        if (token.type == ATOM) {
            buffer.append(token.value);
        }
        // a literal value?  Add this as a quoted string
        else if (token.type == QUOTED_LITERAL) {
            buffer.append(formatQuotedString(token.value));
        }
        // could be a domain literal of the form "[value]"
        else if (token.type == DOMAIN_LITERAL) {
            buffer.append('[');
            buffer.append(token.value);
            buffer.append(']');
        }
        // comments also have values
        else if (token.type == COMMENT) {
            buffer.append('(');
            buffer.append(token.value);
            buffer.append(')');
        }
    }



    private static final byte[] CHARMAP = {
        0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,  0x06, 0x02, 0x06, 0x02, 0x02, 0x06, 0x02, 0x02,
        0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,  0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
        0x04, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,  0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  0x00, 0x00, 0x01, 0x01, 0x01, 0x00, 0x01, 0x00,

        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  0x00, 0x00, 0x00, 0x01, 0x01, 0x01, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02,
    };

    private static final byte FLG_SPECIAL = 1;
    private static final byte FLG_CONTROL = 2;
    private static final byte FLG_SPACE = 4;

    private static boolean isSpace(char ch) {
        if (ch > '\u007f') {
            return false;
        } else {
            return (CHARMAP[ch] & FLG_SPACE) != 0;
        }
    }

    /**
     * Quick test to see if a character is an allowed atom character
     * or not.
     *
     * @param ch     The test character.
     *
     * @return true if this character is allowed in atoms, false for any
     *         control characters, special characters, or blanks.
     */
    public static boolean isAtom(char ch) {
        if (ch > '\u007f') {
            return false;
        }
        else if (ch == ' ') {
            return false;
        }
        else {
            return (CHARMAP[ch] & (FLG_SPECIAL | FLG_CONTROL)) == 0;
        }
    }

    /**
     * Tests one string to determine if it contains any of the
     * characters in a supplied test string.
     *
     * @param s      The string we're testing.
     * @param chars  The set of characters we're testing against.
     *
     * @return true if any of the characters is found, false otherwise.
     */
    public static boolean containsCharacters(String s, String chars)
    {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }


    /**
     * Tests if a string contains any non-special characters that
     * would require encoding the value as a quoted string rather
     * than a simple atom value.
     *
     * @param s      The test string.
     *
     * @return True if the string contains only blanks or allowed atom
     *         characters.
     */
    public static boolean containsSpecials(String s)
    {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // must be either a blank or an allowed atom char.
            if (ch == ' ' || isAtom(ch)) {
                continue;
            }
            else {
                return true;
            }
        }
        return false;
    }


    /**
     * Tests if a string contains any non-special characters that
     * would require encoding the value as a quoted string rather
     * than a simple atom value.
     *
     * @param s      The test string.
     *
     * @return True if the string contains only blanks or allowed atom
     *         characters.
     */
    public static boolean isAtom(String s)
    {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // must be an allowed atom character
            if (!isAtom(ch)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Apply RFC822 quoting rules to a literal string value.  This
     * will search the string to see if there are any characters that
     * require special escaping, and apply the escapes.  If the
     * string is just a string of blank-delimited atoms, the string
     * value is returned without quotes.
     *
     * @param s      The source string.
     *
     * @return A version of the string as a valid RFC822 quoted literal.
     */
    public static String quoteString(String s) {

        // only backslash and double quote require escaping.  If the string does not
        // contain any of these, then we can just slap on some quotes and go.
        if (s.indexOf('\\') == -1 && s.indexOf('"') == -1) {
            // if the string is an atom (or a series of blank-delimited atoms), we can just return it directly.
            if (!containsSpecials(s)) {
                return s;
            }
            StringBuffer buffer = new StringBuffer(s.length() + 2);
            buffer.append('"');
            buffer.append(s);
            buffer.append('"');
            return buffer.toString();
        }

        // get a buffer sufficiently large for the string, two quote characters, and a "reasonable"
        // number of escaped values.
        StringBuffer buffer = new StringBuffer(s.length() + 10);
        buffer.append('"');

        // now check all of the characters.
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // character requiring escaping?
            if (ch == '\\' || ch == '"') {
                // add an extra backslash
                buffer.append('\\');
            }
            // and add on the character
            buffer.append(ch);
        }
        buffer.append('"');
        return buffer.toString();
    }

    /**
     * Apply RFC822 quoting rules to a literal string value.  This
     * will search the string to see if there are any characters that
     * require special escaping, and apply the escapes.  The returned
     * value is enclosed in quotes.
     *
     * @param s      The source string.
     *
     * @return A version of the string as a valid RFC822 quoted literal.
     */
    public static String formatQuotedString(String s) {
        // only backslash and double quote require escaping.  If the string does not
        // contain any of these, then we can just slap on some quotes and go.
        if (s.indexOf('\\') == -1 && s.indexOf('"') == -1) {
            StringBuffer buffer = new StringBuffer(s.length() + 2);
            buffer.append('"');
            buffer.append(s);
            buffer.append('"');
            return buffer.toString();
        }

        // get a buffer sufficiently large for the string, two quote characters, and a "reasonable"
        // number of escaped values.
        StringBuffer buffer = new StringBuffer(s.length() + 10);
        buffer.append('"');

        // now check all of the characters.
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // character requiring escaping?
            if (ch == '\\' || ch == '"') {
                // add an extra backslash
                buffer.append('\\');
            }
            // and add on the character
            buffer.append(ch);
        }
        buffer.append('"');
        return buffer.toString();
    }

    public class TokenStream {
        // the set of tokens in the parsed address list, as determined by RFC822 syntax rules.
        private List tokens;

        // the current token position
        int currentToken = 0;


        /**
         * Default constructor for a TokenStream.  This creates an
         * empty TokenStream for purposes of tokenizing an address.
         * It is the creator's responsibility to terminate the stream
         * with a terminator token.
         */
        public TokenStream() {
            tokens = new ArrayList();
        }


        /**
         * Construct a TokenStream from a list of tokens.  A terminator
         * token is added to the end.
         *
         * @param tokens An existing token list.
         */
        public TokenStream(List tokens) {
            this.tokens = tokens;
            tokens.add(new AddressToken(END_OF_TOKENS, -1));
        }

        /**
         * Add an address token to the token list.
         *
         * @param t      The new token to add to the list.
         */
        public void addToken(AddressToken token) {
            tokens.add(token);
        }

        /**
         * Get the next token at the cursor position, advancing the
         * position accordingly.
         *
         * @return The token at the current token position.
         */
        public AddressToken nextToken() {
            AddressToken token = (AddressToken)tokens.get(currentToken++);
            // we skip over white space tokens when operating in this mode, so
            // check the token and iterate until we get a non-white space.
            while (token.type == WHITESPACE) {
                token = (AddressToken)tokens.get(currentToken++);
            }
            return token;
        }


        /**
         * Get the next token at the cursor position, without advancing the
         * position.
         *
         * @return The token at the current token position.
         */
        public AddressToken currentToken() {
            // return the current token and step the cursor
            return (AddressToken)tokens.get(currentToken);
        }


        /**
         * Get the next non-comment token from the string.  Comments are ignored, except as personal information
         * for very simple address specifications.
         *
         * @return A token guaranteed not to be a whitespace token.
         */
        public AddressToken nextRealToken()
        {
            AddressToken token = nextToken();
            if (token.type == COMMENT) {
                token = nextToken();
            }
            return token;
        }

        /**
         * Push a token back on to the queue, making the index of this
         * token the current cursor position.
         *
         * @param token  The token to push.
         */
        public void pushToken(AddressToken token) {
            // just reset the cursor to the token's index position.
            currentToken = tokenIndex(token);
        }

        /**
         * Get the next token after a given token, without advancing the
         * token position.
         *
         * @param token  The token we're retrieving a token relative to.
         *
         * @return The next token in the list.
         */
        public AddressToken nextToken(AddressToken token) {
            return (AddressToken)tokens.get(tokenIndex(token) + 1);
        }


        /**
         * Return the token prior to a given token.
         *
         * @param token  The token used for the index.
         *
         * @return The token prior to the index token in the list.
         */
        public AddressToken previousToken(AddressToken token) {
            return (AddressToken)tokens.get(tokenIndex(token) - 1);
        }


        /**
         * Retrieve a token at a given index position.
         *
         * @param index  The target index.
         */
        public AddressToken getToken(int index)
        {
            return (AddressToken)tokens.get(index);
        }


        /**
         * Retrieve the index of a particular token in the stream.
         *
         * @param token  The target token.
         *
         * @return The index of the token within the stream.  Returns -1 if this
         *         token is somehow not in the stream.
         */
        public int tokenIndex(AddressToken token) {
            return tokens.indexOf(token);
        }


        /**
         * Extract a new TokenStream running from the start token to the
         * token preceeding the end token.
         *
         * @param start  The starting token of the section.
         * @param end    The last token (+1) for the target section.
         *
         * @return A new TokenStream object for processing this section of tokens.
         */
        public TokenStream section(AddressToken start, AddressToken end) {
            int startIndex = tokenIndex(start);
            int endIndex = tokenIndex(end);

            // List.subList() returns a list backed by the original list.  Since we need to add a
            // terminator token to this list when we take the sublist, we need to manually copy the
            // references so we don't end up munging the original list.
            ArrayList list = new ArrayList(endIndex - startIndex + 2);

            for (int i = startIndex; i <= endIndex; i++) {
                list.add(tokens.get(i));
            }
            return new TokenStream(list);
        }


        /**
         * Reset the token position back to the beginning of the
         * stream.
         */
        public void reset() {
            currentToken = 0;
        }

        /**
         * Scan forward looking for a non-blank token.
         *
         * @return The first non-blank token in the stream.
         */
        public AddressToken getNonBlank()
        {
            AddressToken token = currentToken();
            while (token.type == WHITESPACE) {
                currentToken++;
                token = currentToken();
            }
            return token;
        }


        /**
         * Extract a blank delimited token from a TokenStream.  A blank
         * delimited token is the set of tokens up to the next real whitespace
         * token (comments not included).
         *
         * @return A TokenStream object with the new set of tokens.
         */
        public TokenStream getBlankDelimitedToken()
        {
            // get the next non-whitespace token.
            AddressToken first = getNonBlank();
            // if this is the end, we return null.
            if (first.type == END_OF_TOKENS) {
                return null;
            }

            AddressToken last = first;

            // the methods for retrieving tokens skip over whitespace, so we're going to process this
            // by index.
            currentToken++;

            AddressToken token = currentToken();
            while (true) {
                // if this is our marker, then pluck out the section and return it.
                if (token.type == END_OF_TOKENS || token.type == WHITESPACE) {
                    return section(first, last);
                }
                last = token;
                currentToken++;
                // we accept any and all tokens here.
                token = currentToken();
            }
        }

        /**
         * Return the index of the current cursor position.
         *
         * @return The integer index of the current token.
         */
        public int currentIndex() {
            return currentToken;
        }

        public void dumpTokens()
        {
            System.out.println(">>>>>>>>> Start dumping TokenStream tokens");
            for (int i = 0; i < tokens.size(); i++) {
                System.out.println("-------- Token: " + tokens.get(i));
            }

            System.out.println("++++++++ cursor position=" + currentToken);
            System.out.println(">>>>>>>>> End dumping TokenStream tokens");
        }
    }


    /**
     * Simple utility class for representing address tokens.
     */
    public class AddressToken {

        // the token type
        int type;

        // string value of the token (can be null)
        String value;

        // position of the token within the address string.
        int position;

        AddressToken(int type, int position)
        {
            this.type = type;
            this.value = null;
            this.position = position;
        }

        AddressToken(String value, int type, int position)
        {
            this.type = type;
            this.value = value;
            this.position = position;
        }

        public String toString()
        {
            if (type == END_OF_TOKENS) {
                return "AddressToken:  type=END_OF_TOKENS";
            }
            if (value == null) {
                return "AddressToken:  type=" + (char)type;
            }
            else {
                return "AddressToken:  type=" + (char)type + " value=" + value;
            }
        }
    }
}

