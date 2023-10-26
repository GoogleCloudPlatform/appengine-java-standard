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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;

public class QuotedPrintableEncoder implements Encoder {

    static protected final byte[] encodingTable =
    {
        (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6', (byte)'7',
        (byte)'8', (byte)'9', (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F'
    };

    /*
     * set up the decoding table.
     */
    static protected final byte[] decodingTable = new byte[128];

    static {
        // initialize the decoding table
        for (int i = 0; i < encodingTable.length; i++)
        {
            decodingTable[encodingTable[i]] = (byte)i;
        }
    }


    // default number of characters we will write per line.
    static private final int DEFAULT_CHARS_PER_LINE = 76;

    // the output stream we're wrapped around
    protected OutputStream out;
    // the number of bytes written;
    protected int bytesWritten = 0;
    // number of bytes written on the current line
    protected int lineCount = 0;
    // line length we're dealing with
    protected int lineLength;
    // number of deferred whitespace characters in decode mode.
    protected int deferredWhitespace = 0;

    protected int cachedCharacter = -1;

    // indicates whether the last character was a '\r', potentially part of a CRLF sequence.
    protected boolean lastCR = false;
    // remember whether last character was a white space.
    protected boolean lastWhitespace = false;

    public QuotedPrintableEncoder() {
        this(null, DEFAULT_CHARS_PER_LINE);
    }

    public QuotedPrintableEncoder(OutputStream out) {
        this(out, DEFAULT_CHARS_PER_LINE);
    }

    public QuotedPrintableEncoder(OutputStream out, int lineLength) {
        this.out = out;
        this.lineLength = lineLength;
    }

    private void checkDeferred(int ch) throws IOException {
        // was the last character we looked at a whitespace?  Try to decide what to do with it now.
        if (lastWhitespace) {
            // if this whitespace is at the end of the line, write it out encoded
            if (ch == '\r' || ch == '\n') {
                writeEncodedCharacter(' ');
            }
            else {
                // we can write this out without encoding.
                writeCharacter(' ');
            }
            // we always turn this off.
            lastWhitespace = false;
        }
        // deferred carriage return?
        else if (lastCR) {
            // if the char following the CR was not a new line, write an EOL now.
            if (ch != '\n') {
                writeEOL();
            }
            // we always turn this off too
            lastCR = false;
        }
    }


    /**
     * encode the input data producing a UUEncoded output stream.
     *
     * @param data   The array of byte data.
     * @param off    The starting offset within the data.
     * @param length Length of the data to encode.
     *
     * @return the number of bytes produced.
     */
    public int encode(byte[] data, int off, int length) throws IOException {
        int endOffset = off + length;

        while (off < endOffset) {
            // get the character
            byte ch = data[off++];

            // handle the encoding of this character.
            encode(ch);
        }

        return bytesWritten;
    }


    public void encode(int ch) throws IOException {
        // make sure this is just a single byte value.
        ch = ch &0xFF;

        // see if we had to defer handling of a whitespace or '\r' character, and handle it if necessary.
        checkDeferred(ch);
        // different characters require special handling.
        switch (ch) {
            // spaces require special handling.  If the next character is a line terminator, then
            // the space needs to be encoded.
            case ' ':
            {
                // at this point, we don't know whether this needs encoding or not.  If the next
                // character is a linend, it gets encoded.  If anything else, we just write it as is.
                lastWhitespace = true;
                // turn off any CR flags.
                lastCR = false;
                break;
            }

            // carriage return, which may be part of a CRLF sequence.
            case '\r':
            {
                // just flag this until we see the next character.
                lastCR = true;
                break;
            }

            // a new line character...we need to check to see if it was paired up with a '\r' char.
            case '\n':
            {
                // we always write this out for a newline.  We defer CRs until we see if the LF follows.
                writeEOL();
                break;
            }

            // an '=' is the escape character for an encoded character, so it must also
            // be written encoded.
            case '=':
            {
                writeEncodedCharacter(ch);
                break;
            }

            // all other characters.  If outside the printable character range, write it encoded.
            default:
            {
                if (ch < 32 || ch >= 127) {
                    writeEncodedCharacter(ch);
                }
                else {
                    writeCharacter(ch);
                }
                break;
            }
        }
    }


    /**
     * encode the input data producing a UUEncoded output stream.
     *
     * @param data   The array of byte data.
     * @param off    The starting offset within the data.
     * @param length Length of the data to encode.
     *
     * @return the number of bytes produced.
     */
    public int encode(byte[] data, int off, int length, String specials) throws IOException {
        int endOffset = off + length;

        while (off < endOffset) {
            // get the character
            byte ch = data[off++];

            // handle the encoding of this character.
            encode(ch, specials);
        }

        return bytesWritten;
    }


    /**
     * encode the input data producing a UUEncoded output stream.
     *
     * @param data   The array of byte data.
     * @param off    The starting offset within the data.
     * @param length Length of the data to encode.
     *
     * @return the number of bytes produced.
     */
    public int encode(PushbackInputStream in, StringBuffer out, String specials, int limit) throws IOException {
        int count = 0;

        while (count < limit) {
            int ch = in.read();

            if (ch == -1) {
                return count;
            }
            // make sure this is just a single byte value.
            ch = ch &0xFF;

            // spaces require special handling.  If the next character is a line terminator, then
            // the space needs to be encoded.
            if (ch == ' ') {
                // blanks get translated into underscores, because the encoded tokens can't have embedded blanks.
                out.append('_');
                count++;
            }
            // non-ascii chars and the designated specials all get encoded.
            else if (ch < 32 || ch >= 127 || specials.indexOf(ch) != -1) {
                // we need at least 3 characters to write this out, so we need to
                // forget we saw this one and try in the next segment.
                if (count + 3 > limit) {
                    in.unread(ch);
                    return count;
                }
                out.append('=');
                out.append((char)encodingTable[ch >> 4]);
                out.append((char)encodingTable[ch & 0x0F]);
                count += 3;
            }
            else {
                // good character, just use unchanged.
                out.append((char)ch);
                count++;
            }
        }
        return count;
    }


    /**
     * Specialized version of the decoder that handles encoding of
     * RFC 2047 encoded word values.  This has special handling for
     * certain characters, but less special handling for blanks and
     * linebreaks.
     *
     * @param ch
     * @param specials
     *
     * @exception IOException
     */
    public void encode(int ch, String specials) throws IOException {
        // make sure this is just a single byte value.
        ch = ch &0xFF;

        // spaces require special handling.  If the next character is a line terminator, then
        // the space needs to be encoded.
        if (ch == ' ') {
            // blanks get translated into underscores, because the encoded tokens can't have embedded blanks.
            writeCharacter('_');
        }
        // non-ascii chars and the designated specials all get encoded.
        else if (ch < 32 || ch >= 127 || specials.indexOf(ch) != -1) {
            writeEncodedCharacter(ch);
        }
        else {
            // good character, just use unchanged.
            writeCharacter(ch);
        }
    }


    /**
     * encode the input data producing a UUEncoded output stream.
     *
     * @param data   The array of byte data.
     * @param off    The starting offset within the data.
     * @param length Length of the data to encode.
     * @param out    The output stream the encoded data is written to.
     *
     * @return the number of bytes produced.
     */
    public int encode(byte[] data, int off, int length, OutputStream out) throws IOException {
        // make sure we're writing to the correct stream
        this.out = out;
        bytesWritten = 0;

        // do the actual encoding
        return encode(data, off, length);
    }


    /**
     * decode the uuencoded byte data writing it to the given output stream
     *
     * @param data   The array of byte data to decode.
     * @param off    Starting offset within the array.
     * @param length The length of data to encode.
     * @param out    The output stream used to return the decoded data.
     *
     * @return the number of bytes produced.
     * @exception IOException
     */
    public int decode(byte[] data, int off, int length, OutputStream out) throws IOException {
        // make sure we're writing to the correct stream
        this.out = out;

        int endOffset = off + length;
        int bytesWritten = 0;

        while (off < endOffset) {
            byte ch = data[off++];

            // space characters are a pain.  We need to scan ahead until we find a non-space character.
            // if the character is a line terminator, we need to discard the blanks.
            if (ch == ' ') {
                int trailingSpaces = 1;
                // scan forward, counting the characters.
                while (off < endOffset && data[off] == ' ') {
                    // step forward and count this.
                    off++;
                    trailingSpaces++;
                }
                // is this a lineend at the current location?
                if (off >= endOffset || data[off] == '\r' || data[off] == '\n') {
                    // go to the next one
                    continue;
                }
                else {
                    // make sure we account for the spaces in the output count.
                    bytesWritten += trailingSpaces;
                    // write out the blank characters we counted and continue with the non-blank.
                    while (trailingSpaces-- > 0) {
                        out.write(' ');
                    }
                }
            }
            else if (ch == '=') {
                // we found an encoded character.  Reduce the 3 char sequence to one.
                // but first, make sure we have two characters to work with.
                if (off + 1 >= endOffset) {
                    throw new IOException("Invalid quoted printable encoding");
                }
                // convert the two bytes back from hex.
                byte b1 = data[off++];
                byte b2 = data[off++];

                // we've found an encoded carriage return.  The next char needs to be a newline
                if (b1 == '\r') {
                    if (b2 != '\n') {
                        throw new IOException("Invalid quoted printable encoding");
                    }
                    // this was a soft linebreak inserted by the encoding.  We just toss this away
                    // on decode.
                }
                else {
                    // this is a hex pair we need to convert back to a single byte.
                    b1 = decodingTable[b1];
                    b2 = decodingTable[b2];
                    out.write((b1 << 4) | b2);
                    // 3 bytes in, one byte out
                    bytesWritten++;
                }
            }
            else {
                // simple character, just write it out.
                out.write(ch);
                bytesWritten++;
            }
        }

        return bytesWritten;
    }

    /**
     * Decode a byte array of data.
     *
     * @param data   The data array.
     * @param out    The output stream target for the decoded data.
     *
     * @return The number of bytes written to the stream.
     * @exception IOException
     */
    public int decodeWord(byte[] data, OutputStream out) throws IOException {
        return decodeWord(data, 0, data.length, out);
    }


    /**
     * decode the uuencoded byte data writing it to the given output stream
     *
     * @param data   The array of byte data to decode.
     * @param off    Starting offset within the array.
     * @param length The length of data to encode.
     * @param out    The output stream used to return the decoded data.
     *
     * @return the number of bytes produced.
     * @exception IOException
     */
    public int decodeWord(byte[] data, int off, int length, OutputStream out) throws IOException {
        // make sure we're writing to the correct stream
        this.out = out;

        int endOffset = off + length;
        int bytesWritten = 0;

        while (off < endOffset) {
            byte ch = data[off++];

            // space characters were translated to '_' on encode, so we need to translate them back.
            if (ch == '_') {
                out.write(' ');
            }
            else if (ch == '=') {
                // we found an encoded character.  Reduce the 3 char sequence to one.
                // but first, make sure we have two characters to work with.
                if (off + 1 >= endOffset) {
                    throw new IOException("Invalid quoted printable encoding");
                }
                // convert the two bytes back from hex.
                byte b1 = data[off++];
                byte b2 = data[off++];

                // we've found an encoded carriage return.  The next char needs to be a newline
                if (b1 == '\r') {
                    if (b2 != '\n') {
                        throw new IOException("Invalid quoted printable encoding");
                    }
                    // this was a soft linebreak inserted by the encoding.  We just toss this away
                    // on decode.
                }
                else {
                    // this is a hex pair we need to convert back to a single byte.
                    byte c1 = decodingTable[b1];
                    byte c2 = decodingTable[b2];
                    out.write((c1 << 4) | c2);
                    // 3 bytes in, one byte out
                    bytesWritten++;
                }
            }
            else {
                // simple character, just write it out.
                out.write(ch);
                bytesWritten++;
            }
        }

        return bytesWritten;
    }


    /**
     * decode the UUEncoded String data writing it to the given output stream.
     *
     * @param data   The String data to decode.
     * @param out    The output stream to write the decoded data to.
     *
     * @return the number of bytes produced.
     * @exception IOException
     */
    public int decode(String data, OutputStream out) throws IOException {
        try {
            // just get the byte data and decode.
            byte[] bytes = data.getBytes("US-ASCII");
            return decode(bytes, 0, bytes.length, out);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("Invalid UUEncoding");
        }
    }

    private void checkLineLength(int required) throws IOException {
        // if we're at our line length limit, write out a soft line break and reset.
        if ((lineCount + required) >= lineLength ) {
            out.write('=');
            out.write('\r');
            out.write('\n');
            bytesWritten += 3;
            lineCount = 0;
        }
    }


    public void writeEncodedCharacter(int ch) throws IOException {
        // we need 3 characters for an encoded value
        checkLineLength(3);
        out.write('=');
        out.write(encodingTable[ch >> 4]);
        out.write(encodingTable[ch & 0x0F]);
        lineCount += 3;
        bytesWritten += 3;
    }


    public void writeCharacter(int ch) throws IOException {
        // we need 3 characters for an encoded value
        checkLineLength(1);
        out.write(ch);
        lineCount++;
        bytesWritten++;
    }


    public void writeEOL() throws IOException {
        out.write('\r');
        out.write('\n');
        lineCount = 0;
        bytesWritten += 3;
    }


    public int decode(InputStream in) throws IOException {

        // we potentially need to scan over spans of whitespace characters to determine if they're real
        // we just return blanks until the count goes to zero.
        if (deferredWhitespace > 0) {
            deferredWhitespace--;
            return ' ';
        }

        // we may have needed to scan ahead to find the first non-blank character, which we would store here.
        // hand that back once we're done with the blanks.
        if (cachedCharacter != -1) {
            int result = cachedCharacter;
            cachedCharacter = -1;
            return result;
        }

        int ch = in.read();

        // reflect back an EOF condition.
        if (ch == -1) {
            return -1;
        }

        // space characters are a pain.  We need to scan ahead until we find a non-space character.
        // if the character is a line terminator, we need to discard the blanks.
        if (ch == ' ') {
            // scan forward, counting the characters.
            while ((ch = in.read()) == ' ') {
                deferredWhitespace++;
            }

            // is this a lineend at the current location?
            if (ch == -1 || ch == '\r' || ch == '\n') {
                // those blanks we so zealously counted up don't really exist.  Clear out the counter.
                deferredWhitespace = 0;
                // return the real significant character now.
                return ch;
            }
                       // remember this character for later, after we've used up the deferred blanks.
            cachedCharacter = decodeNonspaceChar(in, ch);
            // return this space.  We did not include this one in the deferred count, so we're right in sync.
            return ' ';
        }
        return decodeNonspaceChar(in, ch);
    }

       private int decodeNonspaceChar(InputStream in, int ch) throws IOException {
               if (ch == '=') {
            int b1 = in.read();
            // we need to get two characters after the quotation marker
            if (b1 == -1) {
                throw new IOException("Truncated quoted printable data");
            }
            int b2 = in.read();
            // we need to get two characters after the quotation marker
            if (b2 == -1) {
                throw new IOException("Truncated quoted printable data");
            }

            // we've found an encoded carriage return.  The next char needs to be a newline
            if (b1 == '\r') {
                if (b2 != '\n') {
                    throw new IOException("Invalid quoted printable encoding");
                }
                // this was a soft linebreak inserted by the encoding.  We just toss this away
                // on decode.  We need to return something, so recurse and decode the next.
                return decode(in);
            }
            else {
                // this is a hex pair we need to convert back to a single byte.
                b1 = decodingTable[b1];
                b2 = decodingTable[b2];
                return (b1 << 4) | b2;
            }
        }
        else {
            return ch;
        }
    }


    /**
     * Perform RFC-2047 word encoding using Q-P data encoding.
     *
     * @param in       The source for the encoded data.
     * @param charset  The charset tag to be added to each encoded data section.
     * @param specials The set of special characters that we require to encoded.
     * @param out      The output stream where the encoded data is to be written.
     * @param fold     Controls whether separate sections of encoded data are separated by
     *                 linebreaks or whitespace.
     *
     * @exception IOException
     */
    public void encodeWord(InputStream in, String charset, String specials, OutputStream out, boolean fold) throws IOException
    {
        // we need to scan ahead in a few places, which may require pushing characters back on to the stream.
        // make sure we have a stream where this is possible.
        PushbackInputStream inStream = new PushbackInputStream(in);
        PrintStream writer = new PrintStream(out);

        // segments of encoded data are limited to 75 byes, including the control sections.
        int limit = 75 - 7 - charset.length();
        boolean firstLine = true;
        StringBuffer encodedString = new StringBuffer(76);

        while (true) {

            // encode another segment of data.
            encode(inStream, encodedString, specials, limit);
            // nothing encoded means we've hit the end of the data.
            if (encodedString.length() == 0) {
                break;
            }
            // if we have more than one segment, we need to insert separators.  Depending on whether folding
            // was requested, this is either a blank or a linebreak.
            if (!firstLine) {
                if (fold) {
                    writer.print("\r\n");
                }
                else {
                    writer.print(" ");
                }
            }

            // add the encoded word header
            writer.print("=?");
            writer.print(charset);
            writer.print("?Q?");
            // the data
            writer.print(encodedString.toString());
            // and the terminator mark
            writer.print("?=");
            writer.flush();

            // we reset the string buffer and reuse it.
            encodedString.setLength(0);
            // we need a delimiter between sections from this point on. 
            firstLine = false;
        }
    }


    /**
     * Perform RFC-2047 word encoding using Base64 data encoding.
     *
     * @param in      The source for the encoded data.
     * @param charset The charset tag to be added to each encoded data section.
     * @param out     The output stream where the encoded data is to be written.
     * @param fold    Controls whether separate sections of encoded data are separated by
     *                linebreaks or whitespace.
     *
     * @exception IOException
     */
    public void encodeWord(byte[] data, StringBuffer out, String charset, String specials) throws IOException
    {
        // append the word header 
        out.append("=?");
        out.append(charset);
        out.append("?Q?"); 
        // add on the encodeded data       
        encodeWordData(data, out, specials); 
        // the end of the encoding marker 
        out.append("?="); 
    }


    /**
     * Perform RFC-2047 word encoding using Q-P data encoding.
     *
     * @param in       The source for the encoded data.
     * @param charset  The charset tag to be added to each encoded data section.
     * @param specials The set of special characters that we require to encoded.
     * @param out      The output stream where the encoded data is to be written.
     * @param fold     Controls whether separate sections of encoded data are separated by
     *                 linebreaks or whitespace.
     *
     * @exception IOException
     */
    public void encodeWordData(byte[] data, StringBuffer out, String specials) throws IOException {
        for (int i = 0; i < data.length; i++) {
            int ch = data[i] & 0xff; ; 

            // spaces require special handling.  If the next character is a line terminator, then
            // the space needs to be encoded.
            if (ch == ' ') {
                // blanks get translated into underscores, because the encoded tokens can't have embedded blanks.
                out.append('_');
            }
            // non-ascii chars and the designated specials all get encoded.
            else if (ch < 32 || ch >= 127 || specials.indexOf(ch) != -1) {
                out.append('=');
                out.append((char)encodingTable[ch >> 4]);
                out.append((char)encodingTable[ch & 0x0F]);
            }
            else {
                // good character, just use unchanged.
                out.append((char)ch);
            }
        }
    }
    
    
    /**
     * Estimate the final encoded size of a segment of data. 
     * This is used to ensure that the encoded blocks do 
     * not get split across a unicode character boundary and 
     * that the encoding will fit within the bounds of 
     * a mail header line. 
     * 
     * @param data   The data we're anticipating encoding.
     * 
     * @return The size of the byte data in encoded form. 
     */
    public int estimateEncodedLength(byte[] data, String specials) 
    {
        int count = 0; 
        
        for (int i = 0; i < data.length; i++) {
            // make sure this is just a single byte value.
            int  ch = data[i] & 0xff;

            // non-ascii chars and the designated specials all get encoded.
            if (ch < 32 || ch >= 127 || specials.indexOf(ch) != -1) {
                // Q encoding translates a single char into 3 characters 
                count += 3; 
            }
            else {
                // non-encoded character 
                count++;
            }
        }
        return count; 
    }
}



