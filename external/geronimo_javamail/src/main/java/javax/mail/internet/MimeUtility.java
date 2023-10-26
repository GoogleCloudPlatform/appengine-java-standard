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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;

import org.apache.geronimo.mail.util.ASCIIUtil;
import org.apache.geronimo.mail.util.Base64;
import org.apache.geronimo.mail.util.Base64DecoderStream;
import org.apache.geronimo.mail.util.Base64Encoder;
import org.apache.geronimo.mail.util.Base64EncoderStream;
import org.apache.geronimo.mail.util.QuotedPrintableDecoderStream;
import org.apache.geronimo.mail.util.QuotedPrintableEncoderStream;
import org.apache.geronimo.mail.util.QuotedPrintableEncoder;
import org.apache.geronimo.mail.util.QuotedPrintable;
import org.apache.geronimo.mail.util.SessionUtil;
import org.apache.geronimo.mail.util.UUDecoderStream;
import org.apache.geronimo.mail.util.UUEncoderStream;

// encodings include "base64", "quoted-printable", "7bit", "8bit" and "binary".
// In addition, "uuencode" is also supported. The

/**
 * @version $Rev: 627556 $ $Date: 2008-02-13 12:27:22 -0600 (Wed, 13 Feb 2008) $
 */
public class MimeUtility {

    private static final String MIME_FOLDENCODEDWORDS = "mail.mime.foldencodedwords";
    private static final String MIME_DECODE_TEXT_STRICT = "mail.mime.decodetext.strict";
    private static final String MIME_FOLDTEXT = "mail.mime.foldtext";
    private static final int FOLD_THRESHOLD = 76;

    private MimeUtility() {
    }

    public static final int ALL = -1;

    private static String defaultJavaCharset;
    private static String escapedChars = "\"\\\r\n";
    private static String linearWhiteSpace = " \t\r\n";

    private static String QP_WORD_SPECIALS = "=_?\"#$%&'(),.:;<>@[\\]^`{|}~";
    private static String QP_TEXT_SPECIALS = "=_?";

    // the javamail spec includes the ability to map java encoding names to MIME-specified names.  Normally,
    // these values are loaded from a character mapping file.
    private static Map java2mime;
    private static Map mime2java;

    static {
        // we need to load the mapping tables used by javaCharset() and mimeCharset().
        loadCharacterSetMappings();
    }

    public static InputStream decode(InputStream in, String encoding) throws MessagingException {
        encoding = encoding.toLowerCase();

        // some encodies are just pass-throughs, with no real decoding.
        if (encoding.equals("binary") || encoding.equals("7bit") || encoding.equals("8bit")) {
            return in;
        }
        else if (encoding.equals("base64")) {
            return new Base64DecoderStream(in);
        }
        // UUEncode is known by a couple historical extension names too.
        else if (encoding.equals("uuencode") || encoding.equals("x-uuencode") || encoding.equals("x-uue")) {
            return new UUDecoderStream(in);
        }
        else if (encoding.equals("quoted-printable")) {
            return new QuotedPrintableDecoderStream(in);
        }
        else {
            throw new MessagingException("Unknown encoding " + encoding);
        }
    }

    /**
     * Decode a string of text obtained from a mail header into
     * it's proper form.  The text generally will consist of a
     * string of tokens, some of which may be encoded using
     * base64 encoding.
     *
     * @param text   The text to decode.
     *
     * @return The decoded test string.
     * @exception UnsupportedEncodingException
     */
    public static String decodeText(String text) throws UnsupportedEncodingException {
        // if the text contains any encoded tokens, those tokens will be marked with "=?".  If the
        // source string doesn't contain that sequent, no decoding is required.
        if (text.indexOf("=?") < 0) {
            return text;
        }

        // we have two sets of rules we can apply.
        if (!SessionUtil.getBooleanProperty(MIME_DECODE_TEXT_STRICT, true)) {
            return decodeTextNonStrict(text);
        }

        int offset = 0;
        int endOffset = text.length();

        int startWhiteSpace = -1;
        int endWhiteSpace = -1;

        StringBuffer decodedText = new StringBuffer(text.length());

        boolean previousTokenEncoded = false;

        while (offset < endOffset) {
            char ch = text.charAt(offset);

            // is this a whitespace character?
            if (linearWhiteSpace.indexOf(ch) != -1) {
                startWhiteSpace = offset;
                while (offset < endOffset) {
                    // step over the white space characters.
                    ch = text.charAt(offset);
                    if (linearWhiteSpace.indexOf(ch) != -1) {
                        offset++;
                    }
                    else {
                        // record the location of the first non lwsp and drop down to process the
                        // token characters.
                        endWhiteSpace = offset;
                        break;
                    }
                }
            }
            else {
                // we have a word token.  We need to scan over the word and then try to parse it.
                int wordStart = offset;

                while (offset < endOffset) {
                    // step over the white space characters.
                    ch = text.charAt(offset);
                    if (linearWhiteSpace.indexOf(ch) == -1) {
                        offset++;
                    }
                    else {
                        break;
                    }

                    //NB:  Trailing whitespace on these header strings will just be discarded.
                }
                // pull out the word token.
                String word = text.substring(wordStart, offset);
                // is the token encoded?  decode the word
                if (word.startsWith("=?")) {
                    try {
                        // if this gives a parsing failure, treat it like a non-encoded word.
                        String decodedWord = decodeWord(word);

                        // are any whitespace characters significant?  Append 'em if we've got 'em.
                        if (!previousTokenEncoded) {
                            if (startWhiteSpace != -1) {
                                decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                                startWhiteSpace = -1;
                            }
                        }
                        // this is definitely a decoded token.
                        previousTokenEncoded = true;
                        // and add this to the text.
                        decodedText.append(decodedWord);
                        // we continue parsing from here...we allow parsing errors to fall through
                        // and get handled as normal text.
                        continue;

                    } catch (ParseException e) {
                    }
                }
                // this is a normal token, so it doesn't matter what the previous token was.  Add the white space
                // if we have it.
                if (startWhiteSpace != -1) {
                    decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                    startWhiteSpace = -1;
                }
                // this is not a decoded token.
                previousTokenEncoded = false;
                decodedText.append(word);
            }
        }

        return decodedText.toString();
    }


    /**
     * Decode a string of text obtained from a mail header into
     * it's proper form.  The text generally will consist of a
     * string of tokens, some of which may be encoded using
     * base64 encoding.  This is for non-strict decoded for mailers that
     * violate the RFC 2047 restriction that decoded tokens must be delimited
     * by linear white space.  This will scan tokens looking for inner tokens
     * enclosed in "=?" -- "?=" pairs.
     *
     * @param text   The text to decode.
     *
     * @return The decoded test string.
     * @exception UnsupportedEncodingException
     */
    private static String decodeTextNonStrict(String text) throws UnsupportedEncodingException {
        int offset = 0;
        int endOffset = text.length();

        int startWhiteSpace = -1;
        int endWhiteSpace = -1;

        StringBuffer decodedText = new StringBuffer(text.length());

        boolean previousTokenEncoded = false;

        while (offset < endOffset) {
            char ch = text.charAt(offset);

            // is this a whitespace character?
            if (linearWhiteSpace.indexOf(ch) != -1) {
                startWhiteSpace = offset;
                while (offset < endOffset) {
                    // step over the white space characters.
                    ch = text.charAt(offset);
                    if (linearWhiteSpace.indexOf(ch) != -1) {
                        offset++;
                    }
                    else {
                        // record the location of the first non lwsp and drop down to process the
                        // token characters.
                        endWhiteSpace = offset;
                        break;
                    }
                }
            }
            else {
                // we're at the start of a word token.  We potentially need to break this up into subtokens
                int wordStart = offset;

                while (offset < endOffset) {
                    // step over the white space characters.
                    ch = text.charAt(offset);
                    if (linearWhiteSpace.indexOf(ch) == -1) {
                        offset++;
                    }
                    else {
                        break;
                    }

                    //NB:  Trailing whitespace on these header strings will just be discarded.
                }
                // pull out the word token.
                String word = text.substring(wordStart, offset);

                int decodeStart = 0;

                // now scan and process each of the bits within here.
                while (decodeStart < word.length()) {
                    int tokenStart = word.indexOf("=?", decodeStart);
                    if (tokenStart == -1) {
                        // this is a normal token, so it doesn't matter what the previous token was.  Add the white space
                        // if we have it.
                        if (startWhiteSpace != -1) {
                            decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                            startWhiteSpace = -1;
                        }
                        // this is not a decoded token.
                        previousTokenEncoded = false;
                        decodedText.append(word.substring(decodeStart));
                        // we're finished.
                        break;
                    }
                    // we have something to process
                    else {
                        // we might have a normal token preceeding this.
                        if (tokenStart != decodeStart) {
                            // this is a normal token, so it doesn't matter what the previous token was.  Add the white space
                            // if we have it.
                            if (startWhiteSpace != -1) {
                                decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                                startWhiteSpace = -1;
                            }
                            // this is not a decoded token.
                            previousTokenEncoded = false;
                            decodedText.append(word.substring(decodeStart, tokenStart));
                        }

                        // now find the end marker.
                        int tokenEnd = word.indexOf("?=", tokenStart);
                        // sigh, an invalid token.  Treat this as plain text.
                        if (tokenEnd == -1) {
                            // this is a normal token, so it doesn't matter what the previous token was.  Add the white space
                            // if we have it.
                            if (startWhiteSpace != -1) {
                                decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                                startWhiteSpace = -1;
                            }
                            // this is not a decoded token.
                            previousTokenEncoded = false;
                            decodedText.append(word.substring(tokenStart));
                            // we're finished.
                            break;
                        }
                        else {
                            // update our ticker
                            decodeStart = tokenEnd + 2;

                            String token = word.substring(tokenStart, tokenEnd);
                            try {
                                // if this gives a parsing failure, treat it like a non-encoded word.
                                String decodedWord = decodeWord(token);

                                // are any whitespace characters significant?  Append 'em if we've got 'em.
                                if (!previousTokenEncoded) {
                                    if (startWhiteSpace != -1) {
                                        decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                                        startWhiteSpace = -1;
                                    }
                                }
                                // this is definitely a decoded token.
                                previousTokenEncoded = true;
                                // and add this to the text.
                                decodedText.append(decodedWord);
                                // we continue parsing from here...we allow parsing errors to fall through
                                // and get handled as normal text.
                                continue;

                            } catch (ParseException e) {
                            }
                            // this is a normal token, so it doesn't matter what the previous token was.  Add the white space
                            // if we have it.
                            if (startWhiteSpace != -1) {
                                decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                                startWhiteSpace = -1;
                            }
                            // this is not a decoded token.
                            previousTokenEncoded = false;
                            decodedText.append(token);
                        }
                    }
                }
            }
        }

        return decodedText.toString();
    }

    /**
     * Parse a string using the RFC 2047 rules for an "encoded-word"
     * type.  This encoding has the syntax:
     *
     * encoded-word = "=?" charset "?" encoding "?" encoded-text "?="
     *
     * @param word   The possibly encoded word value.
     *
     * @return The decoded word.
     * @exception ParseException
     * @exception UnsupportedEncodingException
     */
    public static String decodeWord(String word) throws ParseException, UnsupportedEncodingException {
        // encoded words start with the characters "=?".  If this not an encoded word, we throw a
        // ParseException for the caller.

        if (!word.startsWith("=?")) {
            throw new ParseException("Invalid RFC 2047 encoded-word: " + word);
        }

        int charsetPos = word.indexOf('?', 2);
        if (charsetPos == -1) {
            throw new ParseException("Missing charset in RFC 2047 encoded-word: " + word);
        }

        // pull out the character set information (this is the MIME name at this point).
        String charset = word.substring(2, charsetPos).toLowerCase();

        // now pull out the encoding token the same way.
        int encodingPos = word.indexOf('?', charsetPos + 1);
        if (encodingPos == -1) {
            throw new ParseException("Missing encoding in RFC 2047 encoded-word: " + word);
        }

        String encoding = word.substring(charsetPos + 1, encodingPos);

        // and finally the encoded text.
        int encodedTextPos = word.indexOf("?=", encodingPos + 1);
        if (encodedTextPos == -1) {
            throw new ParseException("Missing encoded text in RFC 2047 encoded-word: " + word);
        }

        String encodedText = word.substring(encodingPos + 1, encodedTextPos);

        // seems a bit silly to encode a null string, but easy to deal with.
        if (encodedText.length() == 0) {
            return "";
        }

        try {
            // the decoder writes directly to an output stream.
            ByteArrayOutputStream out = new ByteArrayOutputStream(encodedText.length());

            byte[] encodedData = encodedText.getBytes("US-ASCII");

            // Base64 encoded?
            if (encoding.equals("B")) {
                Base64.decode(encodedData, out);
            }
            // maybe quoted printable.
            else if (encoding.equals("Q")) {
                QuotedPrintableEncoder dataEncoder = new QuotedPrintableEncoder();
                dataEncoder.decodeWord(encodedData, out);
            }
            else {
                throw new UnsupportedEncodingException("Unknown RFC 2047 encoding: " + encoding);
            }
            // get the decoded byte data and convert into a string.
            byte[] decodedData = out.toByteArray();
            return new String(decodedData, javaCharset(charset));
        } catch (IOException e) {
            throw new UnsupportedEncodingException("Invalid RFC 2047 encoding");
        }

    }

    /**
     * Wrap an encoder around a given output stream.
     *
     * @param out      The output stream to wrap.
     * @param encoding The name of the encoding.
     *
     * @return A instance of FilterOutputStream that manages on the fly
     *         encoding for the requested encoding type.
     * @exception MessagingException
     */
    public static OutputStream encode(OutputStream out, String encoding) throws MessagingException {
        // no encoding specified, so assume it goes out unchanged.
        if (encoding == null) {
            return out;
        }

        encoding = encoding.toLowerCase();

        // some encodies are just pass-throughs, with no real decoding.
        if (encoding.equals("binary") || encoding.equals("7bit") || encoding.equals("8bit")) {
            return out;
        }
        else if (encoding.equals("base64")) {
            return new Base64EncoderStream(out);
        }
        // UUEncode is known by a couple historical extension names too.
        else if (encoding.equals("uuencode") || encoding.equals("x-uuencode") || encoding.equals("x-uue")) {
            return new UUEncoderStream(out);
        }
        else if (encoding.equals("quoted-printable")) {
            return new QuotedPrintableEncoderStream(out);
        }
        else {
            throw new MessagingException("Unknown encoding " + encoding);
        }
    }

    /**
     * Wrap an encoder around a given output stream.
     *
     * @param out      The output stream to wrap.
     * @param encoding The name of the encoding.
     * @param filename The filename of the data being sent (only used for UUEncode).
     *
     * @return A instance of FilterOutputStream that manages on the fly
     *         encoding for the requested encoding type.
     * @exception MessagingException
     */
    public static OutputStream encode(OutputStream out, String encoding, String filename) throws MessagingException {
        encoding = encoding.toLowerCase();

        // some encodies are just pass-throughs, with no real decoding.
        if (encoding.equals("binary") || encoding.equals("7bit") || encoding.equals("8bit")) {
            return out;
        }
        else if (encoding.equals("base64")) {
            return new Base64EncoderStream(out);
        }
        // UUEncode is known by a couple historical extension names too.
        else if (encoding.equals("uuencode") || encoding.equals("x-uuencode") || encoding.equals("x-uue")) {
            return new UUEncoderStream(out, filename);
        }
        else if (encoding.equals("quoted-printable")) {
             return new QuotedPrintableEncoderStream(out);
        }
        else {
            throw new MessagingException("Unknown encoding " + encoding);
        }
    }


    public static String encodeText(String word) throws UnsupportedEncodingException {
        return encodeText(word, null, null);
    }

    public static String encodeText(String word, String charset, String encoding) throws UnsupportedEncodingException {
        return encodeWord(word, charset, encoding, false);
    }

    public static String encodeWord(String word) throws UnsupportedEncodingException {
        return encodeWord(word, null, null);
    }

    public static String encodeWord(String word, String charset, String encoding) throws UnsupportedEncodingException {
        return encodeWord(word, charset, encoding, true);
    }


    private static String encodeWord(String word, String charset, String encoding, boolean encodingWord) throws UnsupportedEncodingException {

        // figure out what we need to encode this.
        String encoder = ASCIIUtil.getTextTransferEncoding(word);
        // all ascii?  We can return this directly,
        if (encoder.equals("7bit")) {
            return word;
        }

        // if not given a charset, use the default.
        if (charset == null) {
            charset = getDefaultMIMECharset();
        }

        // sort out the encoder.  If not explicitly given, use the best guess we've already established.
        if (encoding != null) {
            if (encoding.equalsIgnoreCase("B")) {
                encoder = "base64";
            }
            else if (encoding.equalsIgnoreCase("Q")) {
                encoder = "quoted-printable";
            }
            else {
                throw new UnsupportedEncodingException("Unknown transfer encoding: " + encoding);
            }
        }

        try {
            
            // we'll format this directly into the string buffer 
            StringBuffer result = new StringBuffer(); 
            
            // this is the maximum size of a segment of encoded data, which is based off 
            // of a 75 character size limit and all of the encoding overhead elements.
            int sizeLimit = 75 - 7 - charset.length();
            
            // now do the appropriate encoding work 
            if (encoder.equals("base64")) {
                Base64Encoder dataEncoder = new Base64Encoder();
                // this may recurse on the encoding if the string is too long.  The left-most will not 
                // get a segment delimiter 
                encodeBase64(word, result, sizeLimit, charset, dataEncoder, true, SessionUtil.getBooleanProperty(MIME_FOLDENCODEDWORDS, false)); 
            }
            else {
                QuotedPrintableEncoder dataEncoder = new QuotedPrintableEncoder();
                encodeQuotedPrintable(word, result, sizeLimit, charset, dataEncoder, true, 
                    SessionUtil.getBooleanProperty(MIME_FOLDENCODEDWORDS, false), encodingWord ? QP_WORD_SPECIALS : QP_TEXT_SPECIALS); 
            }
            return result.toString();    
        } catch (IOException e) {
            throw new UnsupportedEncodingException("Invalid encoding");
        }
    }
    
    
    /**
     * Encode a string into base64 encoding, taking into 
     * account the maximum segment length. 
     * 
     * @param data      The string data to encode.
     * @param out       The output buffer used for the result.
     * @param sizeLimit The maximum amount of encoded data we're allowed
     *                  to have in a single encoded segment.
     * @param charset   The character set marker that needs to be added to the
     *                  encoding header.
     * @param encoder   The encoder instance we're using.
     * @param firstSegment
     *                  If true, this is the first (left-most) segment in the
     *                  data.  Used to determine if segment delimiters need to
     *                  be added between sections.
     * @param foldSegments
     *                  Indicates the type of delimiter to use (blank or newline sequence).
     */
    static private void encodeBase64(String data, StringBuffer out, int sizeLimit, String charset, Base64Encoder encoder, boolean firstSegment, boolean foldSegments) throws IOException
    {
        // this needs to be converted into the appropriate transfer encoding. 
        byte [] bytes = data.getBytes(javaCharset(charset)); 
        
        int estimatedSize = encoder.estimateEncodedLength(bytes); 
        
        // if the estimated encoding size is over our segment limit, split the string in half and 
        // recurse.  Eventually we'll reach a point where things are small enough.  
        if (estimatedSize > sizeLimit) {
            // the first segment indicator travels with the left half. 
            encodeBase64(data.substring(0, data.length() / 2), out, sizeLimit, charset, encoder, firstSegment, foldSegments);
            // the second half can never be the first segment 
            encodeBase64(data.substring(data.length() / 2), out, sizeLimit, charset, encoder, false, foldSegments);
        }
        else 
        {
            // if this is not the first sement of the encoding, we need to add either a blank or 
            // a newline sequence to the data 
            if (!firstSegment) {
                if (foldSegments) {
                    out.append("\r\n"); 
                }
                else {
                    out.append(' '); 
                }
            }
            // do the encoding of the segment.
            encoder.encodeWord(bytes, out, charset);
        }
    }
    
    
    /**
     * Encode a string into quoted printable encoding, taking into 
     * account the maximum segment length. 
     * 
     * @param data      The string data to encode.
     * @param out       The output buffer used for the result.
     * @param sizeLimit The maximum amount of encoded data we're allowed
     *                  to have in a single encoded segment.
     * @param charset   The character set marker that needs to be added to the
     *                  encoding header.
     * @param encoder   The encoder instance we're using.
     * @param firstSegment
     *                  If true, this is the first (left-most) segment in the
     *                  data.  Used to determine if segment delimiters need to
     *                  be added between sections.
     * @param foldSegments
     *                  Indicates the type of delimiter to use (blank or newline sequence).
     */
    static private void encodeQuotedPrintable(String data, StringBuffer out, int sizeLimit, String charset, QuotedPrintableEncoder encoder, 
        boolean firstSegment, boolean foldSegments, String specials)  throws IOException 
    {
        // this needs to be converted into the appropriate transfer encoding. 
        byte [] bytes = data.getBytes(javaCharset(charset)); 
        
        int estimatedSize = encoder.estimateEncodedLength(bytes, specials); 
        
        // if the estimated encoding size is over our segment limit, split the string in half and 
        // recurse.  Eventually we'll reach a point where things are small enough.  
        if (estimatedSize > sizeLimit) {
            // the first segment indicator travels with the left half. 
            encodeQuotedPrintable(data.substring(0, data.length() / 2), out, sizeLimit, charset, encoder, firstSegment, foldSegments, specials);
            // the second half can never be the first segment 
            encodeQuotedPrintable(data.substring(data.length() / 2), out, sizeLimit, charset, encoder, false, foldSegments, specials);
        }
        else 
        {
            // if this is not the first sement of the encoding, we need to add either a blank or 
            // a newline sequence to the data 
            if (!firstSegment) {
                if (foldSegments) {
                    out.append("\r\n"); 
                }
                else {
                    out.append(' '); 
                }
            }
            // do the encoding of the segment.
            encoder.encodeWord(bytes, out, charset, specials);
        }
    }


    /**
     * Examine the content of a data source and decide what type
     * of transfer encoding should be used.  For text streams,
     * we'll decided between 7bit, quoted-printable, and base64.
     * For binary content types, we'll use either 7bit or base64.
     *
     * @param handler The DataHandler associated with the content.
     *
     * @return The string name of an encoding used to transfer the content.
     */
    public static String getEncoding(DataHandler handler) {


        // if this handler has an associated data source, we can read directly from the
        // data source to make this judgment.  This is generally MUCH faster than asking the
        // DataHandler to write out the data for us.
        DataSource ds = handler.getDataSource();
        if (ds != null) {
            return getEncoding(ds);
        }

        try {
            // get a parser that allows us to make comparisons.
            ContentType content = new ContentType(handler.getContentType());

            // The only access to the content bytes at this point is by asking the handler to write
            // the information out to a stream.  We're going to pipe this through a special stream
            // that examines the bytes as they go by.
            ContentCheckingOutputStream checker = new ContentCheckingOutputStream();

            handler.writeTo(checker);

            // figure this out based on whether we believe this to be a text type or not.
            if (content.match("text/*")) {
                return checker.getTextTransferEncoding();
            }
            else {
                return checker.getBinaryTransferEncoding();
            }

        } catch (Exception e) {
            // any unexpected I/O exceptions we'll force to a "safe" fallback position.
            return "base64";
        }
    }


    /**
     * Determine the what transfer encoding should be used for
     * data retrieved from a DataSource.
     *
     * @param source The DataSource for the transmitted data.
     *
     * @return The string name of the encoding form that should be used for
     *         the data.
     */
    public static String getEncoding(DataSource source) {
        InputStream in = null;

        try {
            // get a parser that allows us to make comparisons.
            ContentType content = new ContentType(source.getContentType());

            // we're probably going to have to scan the data.
            in = source.getInputStream();

            if (!content.match("text/*")) {
                // Not purporting to be a text type?  Examine the content to see we might be able to
                // at least pretend it is an ascii type.
                return ASCIIUtil.getBinaryTransferEncoding(in);
            }
            else {
                return ASCIIUtil.getTextTransferEncoding(in);
            }
        } catch (Exception e) {
            // this was a problem...not sure what makes sense here, so we'll assume it's binary
            // and we need to transfer this using Base64 encoding.
            return "base64";
        } finally {
            // make sure we close the stream
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
            }
        }
    }


    /**
     * Quote a "word" value.  If the word contains any character from
     * the specified "specials" list, this value is returned as a
     * quoted strong.  Otherwise, it is returned unchanged (an "atom").
     *
     * @param word     The word requiring quoting.
     * @param specials The set of special characters that can't appear in an unquoted
     *                 string.
     *
     * @return The quoted value.  This will be unchanged if the word doesn't contain
     *         any of the designated special characters.
     */
    public static String quote(String word, String specials) {
        int wordLength = word.length();
        boolean requiresQuoting = false;
        // scan the string looking for problem characters
        for (int i =0; i < wordLength; i++) {
            char ch = word.charAt(i);
            // special escaped characters require escaping, which also implies quoting.
            if (escapedChars.indexOf(ch) >= 0) {
                return quoteAndEscapeString(word);
            }
            // now check for control characters or the designated special characters.
            if (ch < 32 || ch >= 127 || specials.indexOf(ch) >= 0) {
                // we know this requires quoting, but we still need to scan the entire string to
                // see if contains chars that require escaping.  Just go ahead and treat it as if it does.
                return quoteAndEscapeString(word);
            }
        }
        return word;
    }

    /**
     * Take a string and return it as a formatted quoted string, with
     * all characters requiring escaping handled properly.
     *
     * @param word   The string to quote.
     *
     * @return The quoted string.
     */
    private static String quoteAndEscapeString(String word) {
        int wordLength = word.length();
        // allocate at least enough for the string and two quotes plus a reasonable number of escaped chars.
        StringBuffer buffer = new StringBuffer(wordLength + 10);
        // add the leading quote.
        buffer.append('"');

        for (int i = 0; i < wordLength; i++) {
            char ch = word.charAt(i);
            // is this an escaped char?
            if (escapedChars.indexOf(ch) >= 0) {
                // add the escape marker before appending.
                buffer.append('\\');
            }
            buffer.append(ch);
        }
        // now the closing quote
        buffer.append('"');
        return buffer.toString();
    }

    /**
     * Translate a MIME standard character set name into the Java
     * equivalent.
     *
     * @param charset The MIME standard name.
     *
     * @return The Java equivalent for this name.
     */
    public static String javaCharset(String charset) {
        // nothing in, nothing out.
        if (charset == null) {
            return null;
        }

        String mappedCharset = (String)mime2java.get(charset.toLowerCase());
        // if there is no mapping, then the original name is used.  Many of the MIME character set
        // names map directly back into Java.  The reverse isn't necessarily true.
        return mappedCharset == null ? charset : mappedCharset;
    }

    /**
     * Map a Java character set name into the MIME equivalent.
     *
     * @param charset The java character set name.
     *
     * @return The MIME standard equivalent for this character set name.
     */
    public static String mimeCharset(String charset) {
        // nothing in, nothing out.
        if (charset == null) {
            return null;
        }

        String mappedCharset = (String)java2mime.get(charset.toLowerCase());
        // if there is no mapping, then the original name is used.  Many of the MIME character set
        // names map directly back into Java.  The reverse isn't necessarily true.
        return mappedCharset == null ? charset : mappedCharset;
    }


    /**
     * Get the default character set to use, in Java name format.
     * This either be the value set with the mail.mime.charset
     * system property or obtained from the file.encoding system
     * property.  If neither of these is set, we fall back to
     * 8859_1 (basically US-ASCII).
     *
     * @return The character string value of the default character set.
     */
    public static String getDefaultJavaCharset() {
        String charset = SessionUtil.getProperty("mail.mime.charset");
        if (charset != null) {
            return javaCharset(charset);
        }
        return SessionUtil.getProperty("file.encoding", "8859_1");
    }

    /**
     * Get the default character set to use, in MIME name format.
     * This either be the value set with the mail.mime.charset
     * system property or obtained from the file.encoding system
     * property.  If neither of these is set, we fall back to
     * 8859_1 (basically US-ASCII).
     *
     * @return The character string value of the default character set.
     */
    static String getDefaultMIMECharset() {
        // if the property is specified, this can be used directly.
        String charset = SessionUtil.getProperty("mail.mime.charset");
        if (charset != null) {
            return charset;
        }

        // get the Java-defined default and map back to a MIME name.
        return mimeCharset(SessionUtil.getProperty("file.encoding", "8859_1"));
    }


    /**
     * Load the default mapping tables used by the javaCharset()
     * and mimeCharset() methods.  By default, these tables are
     * loaded from the /META-INF/javamail.charset.map file.  If
     * something goes wrong loading that file, we configure things
     * with a default mapping table (which just happens to mimic
     * what's in the default mapping file).
     */
    static private void loadCharacterSetMappings() {
        java2mime = new HashMap();
        mime2java = new HashMap();


        // normally, these come from a character map file contained in the jar file.
        try {
            InputStream map = javax.mail.internet.MimeUtility.class.getResourceAsStream("/META-INF/javamail.charset.map");

            if (map != null) {
                // get a reader for this so we can load.
                BufferedReader reader = new BufferedReader(new InputStreamReader(map));

                readMappings(reader, java2mime);
                readMappings(reader, mime2java);
            }
        } catch (Exception e) {
        }

        // if any sort of error occurred reading the preferred file version, we could end up with empty
        // mapping tables.  This could cause all sorts of difficulty, so ensure they are populated with at
        // least a reasonable set of defaults.

        // these mappings echo what's in the default file.
        if (java2mime.isEmpty()) {
            java2mime.put("8859_1", "ISO-8859-1");
            java2mime.put("iso8859_1", "ISO-8859-1");
            java2mime.put("iso8859-1", "ISO-8859-1");

            java2mime.put("8859_2", "ISO-8859-2");
            java2mime.put("iso8859_2", "ISO-8859-2");
            java2mime.put("iso8859-2", "ISO-8859-2");

            java2mime.put("8859_3", "ISO-8859-3");
            java2mime.put("iso8859_3", "ISO-8859-3");
            java2mime.put("iso8859-3", "ISO-8859-3");

            java2mime.put("8859_4", "ISO-8859-4");
            java2mime.put("iso8859_4", "ISO-8859-4");
            java2mime.put("iso8859-4", "ISO-8859-4");

            java2mime.put("8859_5", "ISO-8859-5");
            java2mime.put("iso8859_5", "ISO-8859-5");
            java2mime.put("iso8859-5", "ISO-8859-5");

            java2mime.put ("8859_6", "ISO-8859-6");
            java2mime.put("iso8859_6", "ISO-8859-6");
            java2mime.put("iso8859-6", "ISO-8859-6");

            java2mime.put("8859_7", "ISO-8859-7");
            java2mime.put("iso8859_7", "ISO-8859-7");
            java2mime.put("iso8859-7", "ISO-8859-7");

            java2mime.put("8859_8", "ISO-8859-8");
            java2mime.put("iso8859_8", "ISO-8859-8");
            java2mime.put("iso8859-8", "ISO-8859-8");

            java2mime.put("8859_9", "ISO-8859-9");
            java2mime.put("iso8859_9", "ISO-8859-9");
            java2mime.put("iso8859-9", "ISO-8859-9");

            java2mime.put("sjis", "Shift_JIS");
            java2mime.put ("jis", "ISO-2022-JP");
            java2mime.put("iso2022jp", "ISO-2022-JP");
            java2mime.put("euc_jp", "euc-jp");
            java2mime.put("koi8_r", "koi8-r");
            java2mime.put("euc_cn", "euc-cn");
            java2mime.put("euc_tw", "euc-tw");
            java2mime.put("euc_kr", "euc-kr");
        }

        if (mime2java.isEmpty ()) {
            mime2java.put("iso-2022-cn", "ISO2022CN");
            mime2java.put("iso-2022-kr", "ISO2022KR");
            mime2java.put("utf-8", "UTF8");
            mime2java.put("utf8", "UTF8");
            mime2java.put("ja_jp.iso2022-7", "ISO2022JP");
            mime2java.put("ja_jp.eucjp", "EUCJIS");
            mime2java.put ("euc-kr", "KSC5601");
            mime2java.put("euckr", "KSC5601");
            mime2java.put("us-ascii", "ISO-8859-1");
            mime2java.put("x-us-ascii", "ISO-8859-1");
        }
    }


    /**
     * Read a section of a character map table and populate the
     * target mapping table with the information.  The table end
     * is marked by a line starting with "--" and also ending with
     * "--".  Blank lines and comment lines (beginning with '#') are
     * ignored.
     *
     * @param reader The source of the file information.
     * @param table  The mapping table used to store the information.
     */
    static private void readMappings(BufferedReader reader, Map table) throws IOException {
        // process lines to the EOF or the end of table marker.
        while (true) {
            String line = reader.readLine();
            // no line returned is an EOF
            if (line == null) {
                return;
            }

            // trim so we're not messed up by trailing blanks
            line = line.trim();

            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }

            // stop processing if this is the end-of-table marker.
            if (line.startsWith("--") && line.endsWith("--")) {
                return;
            }

            // we allow either blanks or tabs as token delimiters.
            StringTokenizer tokenizer = new StringTokenizer(line, " \t");

            try {
                String from = tokenizer.nextToken().toLowerCase();
                String to = tokenizer.nextToken();

                table.put(from, to);
            } catch (NoSuchElementException e) {
                // just ignore the line if invalid.
            }
        }
    }


    /**
     * Perform RFC 2047 text folding on a string of text.
     *
     * @param used   The amount of text already "used up" on this line.  This is
     *               typically the length of a message header that this text
     *               get getting added to.
     * @param s      The text to fold.
     *
     * @return The input text, with linebreaks inserted at appropriate fold points.
     */
    public static String fold(int used, String s) {
        // if folding is disable, unfolding is also.  Return the string unchanged.
        if (!SessionUtil.getBooleanProperty(MIME_FOLDTEXT, true)) {
            return s;
        }

        int end;

        // now we need to strip off any trailing "whitespace", where whitespace is blanks, tabs,
        // and line break characters.
        for (end = s.length() - 1; end >= 0; end--) {
            int ch = s.charAt(end);
            if (ch != ' ' && ch != '\t' ) {
                break;
            }
        }

        // did we actually find something to remove?  Shorten the String to the trimmed length
        if (end != s.length() - 1) {
            s = s.substring(0, end + 1);
        }

        // does the string as it exists now not require folding?  We can just had that back right off.
        if (s.length() + used <= FOLD_THRESHOLD) {
            return s;
        }

        // get a buffer for the length of the string, plus room for a few line breaks.
        // these are soft line breaks, so we generally need more that just the line breaks (an escape +
        // CR + LF + leading space on next line);
        StringBuffer newString = new StringBuffer(s.length() + 8);


        // now keep chopping this down until we've accomplished what we need.
        while (used + s.length() > FOLD_THRESHOLD) {
            int breakPoint = -1;
            char breakChar = 0;

            // now scan for the next place where we can break.
            for (int i = 0; i < s.length(); i++) {
                // have we passed the fold limit?
                if (used + i > FOLD_THRESHOLD) {
                    // if we've already seen a blank, then stop now.  Otherwise
                    // we keep going until we hit a fold point.
                    if (breakPoint != -1) {
                        break;
                    }
                }
                char ch = s.charAt(i);

                // a white space character?
                if (ch == ' ' || ch == '\t') {
                    // this might be a run of white space, so skip over those now.
                    breakPoint = i;
                    // we need to maintain the same character type after the inserted linebreak.
                    breakChar = ch;
                    i++;
                    while (i < s.length()) {
                        ch = s.charAt(i);
                        if (ch != ' ' && ch != '\t') {
                            break;
                        }
                        i++;
                    }
                }
                // found an embedded new line.  Escape this so that the unfolding process preserves it.
                else if (ch == '\n') {
                    newString.append('\\');
                    newString.append('\n');
                }
                else if (ch == '\r') {
                    newString.append('\\');
                    newString.append('\n');
                    i++;
                    // if this is a CRLF pair, add the second char also
                    if (i < s.length() && s.charAt(i) == '\n') {
                        newString.append('\r');
                    }
                }

            }
            // no fold point found, we punt, append the remainder and leave.
            if (breakPoint == -1) {
                newString.append(s);
                return newString.toString();
            }
            newString.append(s.substring(0, breakPoint));
            newString.append("\r\n");
            newString.append(breakChar);
            // chop the string
            s = s.substring(breakPoint + 1);
            // start again, and we've used the first char of the limit already with the whitespace char.
            used = 1;
        }

        // add on the remainder, and return
        newString.append(s);
        return newString.toString();
    }

    /**
     * Unfold a folded string.  The unfolding process will remove
     * any line breaks that are not escaped and which are also followed
     * by whitespace characters.
     *
     * @param s      The folded string.
     *
     * @return A new string with unfolding rules applied.
     */
    public static String unfold(String s) {
        // if folding is disable, unfolding is also.  Return the string unchanged.
        if (!SessionUtil.getBooleanProperty(MIME_FOLDTEXT, true)) {
            return s;
        }

        // if there are no line break characters in the string, we can just return this.
        if (s.indexOf('\n') < 0 && s.indexOf('\r') < 0) {
            return s;
        }

        // we need to scan and fix things up.
        int length = s.length();

        StringBuffer newString = new StringBuffer(length);

        // scan the entire string
        for (int i = 0; i < length; i++) {
            char ch = s.charAt(i);

            // we have a backslash.  In folded strings, escape characters are only processed as such if
            // they preceed line breaks.  Otherwise, we leave it be.
            if (ch == '\\') {
                // escape at the very end?  Just add the character.
                if (i == length - 1) {
                    newString.append(ch);
                }
                else {
                    int nextChar = s.charAt(i + 1);

                    // naked newline?  Add the new line to the buffer, and skip the escape char.
                    if (nextChar == '\n') {
                        newString.append('\n');
                        i++;
                    }
                    else if (nextChar == '\r') {
                        // just the CR left?  Add it, removing the escape.
                        if (i == length - 2 || s.charAt(i + 2) != '\r') {
                            newString.append('\r');
                            i++;
                        }
                        else {
                            // toss the escape, add both parts of the CRLF, and skip over two chars.
                            newString.append('\r');
                            newString.append('\n');
                            i += 2;
                        }
                    }
                    else {
                        // an escape for another purpose, just copy it over.
                        newString.append(ch);
                    }
                }
            }
            // we have an unescaped line break
            else if (ch == '\n' || ch == '\r') {
                // remember the position in case we need to backtrack.
                int lineBreak = i;
                boolean CRLF = false;

                if (ch == '\r') {
                    // check to see if we need to step over this.
                    if (i < length - 1 && s.charAt(i + 1) == '\n') {
                        i++;
                        // flag the type so we know what we might need to preserve.
                        CRLF = true;
                    }
                }

                // get a temp position scanner.
                int scan = i + 1;

                // does a blank follow this new line?  we need to scrap the new line and reduce the leading blanks
                // down to a single blank.
                if (scan < length && s.charAt(scan) == ' ') {
                    // add the character
                    newString.append(' ');

                    // scan over the rest of the blanks
                    i = scan + 1;
                    while (i < length && s.charAt(i) == ' ') {
                        i++;
                    }
                    // we'll increment down below, so back up to the last blank as the current char.
                    i--;
                }
                else {
                    // we must keep this line break.  Append the appropriate style.
                    if (CRLF) {
                        newString.append("\r\n");
                    }
                    else {
                        newString.append(ch);
                    }
                }
            }
            else {
                // just a normal, ordinary character
                newString.append(ch);
            }
        }
        return newString.toString();
    }
}


/**
 * Utility class for examining content information written out
 * by a DataHandler object.  This stream gathers statistics on
 * the stream so it can make transfer encoding determinations.
 */
class ContentCheckingOutputStream extends OutputStream {
    private int asciiChars = 0;
    private int nonAsciiChars = 0;
    private boolean containsLongLines = false;
    private boolean containsMalformedEOL = false;
    private int previousChar = 0;
    private int span = 0;

    ContentCheckingOutputStream() {
    }

    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            write(data[offset + i]);
        }
    }

    public void write(int ch) {
        // we found a linebreak.  Reset the line length counters on either one.  We don't
        // really need to validate here.
        if (ch == '\n' || ch == '\r') {
            // we found a newline, this is only valid if the previous char was the '\r'
            if (ch == '\n') {
                // malformed linebreak?  force this to base64 encoding.
                if (previousChar != '\r') {
                    containsMalformedEOL = true;
                }
            }
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
            if (!ASCIIUtil.isAscii(ch)) {
                nonAsciiChars++;
            }
            else {
                asciiChars++;
            }
        }
        previousChar = ch;
    }


    public String getBinaryTransferEncoding() {
        if (nonAsciiChars != 0 || containsLongLines || containsMalformedEOL) {
            return "base64";
        }
        else {
            return "7bit";
        }
    }

    public String getTextTransferEncoding() {
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
}
