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

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * An implementation of a FilterOutputStream that decodes the
 * stream data in UU encoding format.  This version does the
 * decoding "on the fly" rather than decoding a single block of
 * data.  Since this version is intended for use by the MimeUtilty class,
 * it also handles line breaks in the encoded data.
 */
public class UUDecoderStream extends FilterInputStream {
    // maximum number of chars that can appear in a single line
    protected static final int MAX_CHARS_PER_LINE = 45;

    // our decoder for processing the data
    protected UUEncoder decoder = new UUEncoder();

    // a buffer for one decoding unit's worth of data (45 bytes).
    protected byte[] decodedChars;
    // count of characters in the buffer
    protected int decodedCount = 0;
    // index of the next decoded character
    protected int decodedIndex = 0;

    // indicates whether we've already processed the "begin" prefix.
    protected boolean beginRead = false;


    public UUDecoderStream(InputStream in) {
        super(in);
    }


    /**
     * Test for the existance of decoded characters in our buffer
     * of decoded data.
     *
     * @return True if we currently have buffered characters.
     */
    private boolean dataAvailable() {
        return decodedCount != 0;
    }

    /**
     * Get the next buffered decoded character.
     *
     * @return The next decoded character in the buffer.
     */
    private byte getBufferedChar() {
        decodedCount--;
        return decodedChars[decodedIndex++];
    }

    /**
     * Decode a requested number of bytes of data into a buffer.
     *
     * @return true if we were able to obtain more data, false otherwise.
     */
    private boolean decodeStreamData() throws IOException {
        decodedIndex = 0;

        // fill up a data buffer with input data
        return fillEncodedBuffer() != -1;
    }


    /**
     * Retrieve a single byte from the decoded characters buffer.
     *
     * @return The decoded character or -1 if there was an EOF condition.
     */
    private int getByte() throws IOException {
        if (!dataAvailable()) {
            if (!decodeStreamData()) {
                return -1;
            }
        }
        decodedCount--;
        return decodedChars[decodedIndex++];
    }

    private int getBytes(byte[] data, int offset, int length) throws IOException {

        int readCharacters = 0;
        while (length > 0) {
            // need data?  Try to get some
            if (!dataAvailable()) {
                // if we can't get this, return a count of how much we did get (which may be -1).
                if (!decodeStreamData()) {
                    return readCharacters > 0 ? readCharacters : -1;
                }
            }

            // now copy some of the data from the decoded buffer to the target buffer
            int copyCount = Math.min(decodedCount, length);
            System.arraycopy(decodedChars, decodedIndex, data, offset, copyCount);
            decodedIndex += copyCount;
            decodedCount -= copyCount;
            offset += copyCount;
            length -= copyCount;
            readCharacters += copyCount;
        }
        return readCharacters;
    }

    /**
     * Verify that the first line of the buffer is a valid begin
     * marker.
     *
     * @exception IOException
     */
    private void checkBegin() throws IOException {
        // we only do this the first time we're requested to read from the stream.
        if (beginRead) {
            return;
        }

        // we might have to skip over lines to reach the marker.  If we hit the EOF without finding
        // the begin, that's an error.
        while (true) {
            String line = readLine();
            if (line == null) {
                throw new IOException("Missing UUEncode begin command");
            }

            // is this our begin?
            if (line.regionMatches(true, 0, "begin ", 0, 6)) {
                // This is the droid we're looking for.....
                beginRead = true;
                return;
            }
        }
    }


    /**
     * Read a line of data.  Returns null if there is an EOF.
     *
     * @return The next line read from the stream.  Returns null if we
     *         hit the end of the stream.
     * @exception IOException
     */
    protected String readLine() throws IOException {
        decodedIndex = 0;
        // get an accumulator for the data
        StringBuffer buffer = new StringBuffer();

        // now process a character at a time.
        int ch = in.read();
        while (ch != -1) {
            // a naked new line completes the line.
            if (ch == '\n') {
                break;
            }
            // a carriage return by itself is ignored...we're going to assume that this is followed
            // by a new line because we really don't have the capability of pushing this back .
            else if (ch == '\r') {
                ;
            }
            else {
                // add this to our buffer
                buffer.append((char)ch);
            }
            ch = in.read();
        }

        // if we didn't get any data at all, return nothing
        if (ch == -1 && buffer.length() == 0) {
            return null;
        }
        // convert this into a string.
        return buffer.toString();
    }


    /**
     * Fill our buffer of input characters for decoding from the
     * stream.  This will attempt read a full buffer, but will
     * terminate on an EOF or read error.  This will filter out
     * non-Base64 encoding chars and will only return a valid
     * multiple of 4 number of bytes.
     *
     * @return The count of characters read.
     */
    private int fillEncodedBuffer() throws IOException
    {
        checkBegin();
        // reset the buffer position
        decodedIndex = 0;

        while (true) {

            // we read these as character lines.  We need to be looking for the "end" marker for the
            // end of the data.
            String line = readLine();

            // this should NOT be happening....
            if (line == null) {
                throw new IOException("Missing end in UUEncoded data");
            }

            // Is this the end marker?  EOF baby, EOF!
            if (line.equalsIgnoreCase("end")) {
                // this indicates we got nuttin' more to do.
                return -1;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(MAX_CHARS_PER_LINE);

            byte [] lineBytes;
            try {
                lineBytes = line.getBytes("US-ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new IOException("Invalid UUEncoding");
            }

            // decode this line
            decodedCount = decoder.decode(lineBytes, 0, lineBytes.length, out);

            // not just a zero-length line?
            if (decodedCount != 0) {
                // get the resulting byte array
                decodedChars = out.toByteArray();
                return decodedCount;
            }
        }
    }


    // in order to function as a filter, these streams need to override the different
    // read() signature.

    public int read() throws IOException
    {
        return getByte();
    }


    public int read(byte [] buffer, int offset, int length) throws IOException {
        return getBytes(buffer, offset, length);
    }


    public boolean markSupported() {
        return false;
    }


    public int available() throws IOException {
        return ((in.available() / 4) * 3) + decodedCount;
    }
}

