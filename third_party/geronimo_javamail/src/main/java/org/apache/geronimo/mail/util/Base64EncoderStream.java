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

import java.io.IOException;
import java.io.OutputStream;
import java.io.FilterOutputStream;

/**
 * An implementation of a FilterOutputStream that encodes the
 * stream data in BASE64 encoding format.  This version does the
 * encoding "on the fly" rather than encoding a single block of
 * data.  Since this version is intended for use by the MimeUtilty class,
 * it also handles line breaks in the encoded data.
 */
public class Base64EncoderStream extends FilterOutputStream {

    // our Filtered stream writes everything out as byte data.  This allows the CRLF sequence to
    // be written with a single call.
    protected static final byte[] CRLF = { '\r', '\n' };

    // our hex encoder utility class.
    protected Base64Encoder encoder = new Base64Encoder();

    // our default for line breaks
    protected static final int DEFAULT_LINEBREAK = 76;

    // Data can only be written out in complete units of 3 bytes encoded as 4.  Therefore, we need to buffer
    // as many as 2 bytes to fill out an encoding unit.

    // the buffered byte count
    protected int bufferedBytes = 0;

    // we'll encode this part once it is filled up.
    protected byte[] buffer = new byte[3];


    // the size we process line breaks at.  If this is Integer.MAX_VALUE, no line breaks are handled.
    protected int lineBreak;

    // the number of encoded characters we've written to the stream, which determines where we
    // insert line breaks.
    protected int outputCount;

    /**
     * Create a Base64 encoder stream that wraps a specifed stream
     * using the default line break size.
     *
     * @param out    The wrapped output stream.
     */
    public Base64EncoderStream(OutputStream out) {
        this(out, DEFAULT_LINEBREAK);
    }


    public Base64EncoderStream(OutputStream out, int lineBreak) {
        super(out);
        // lines are processed only in multiple of 4, so round this down.
        this.lineBreak = (lineBreak / 4) * 4 ;
    }

    // in order for this to work, we need to override the 3 different signatures for write

    public void write(int ch) throws IOException {
        // store this in the buffer.
        buffer[bufferedBytes++] = (byte)ch;
        // if the buffer is filled, encode these bytes
        if (bufferedBytes == 3) {
            // check for room in the current line for this character
            checkEOL(4);
            // write these directly to the stream.
            encoder.encode(buffer, 0, 3, out);
            bufferedBytes = 0;
            // and update the line length checkers
            updateLineCount(4);
        }
    }

    public void write(byte [] data) throws IOException {
        write(data, 0, data.length);
    }

    public void write(byte [] data, int offset, int length) throws IOException {
        // if we have something in the buffer, we need to write enough bytes out to flush
        // those into the output stream AND continue on to finish off a line.  Once we're done there
        // we can write additional data out in complete blocks.
        while ((bufferedBytes > 0 || outputCount > 0) && length > 0) {
            write(data[offset++]);
            length--;
        }

        if (length > 0) {
            // no linebreaks requested?  YES!!!!!, we can just dispose of the lot with one call.
            if (lineBreak == Integer.MAX_VALUE) {
                encoder.encode(data, offset, length, out);
            }
            else {
                // calculate the size of a segment we can encode directly as a line.
                int segmentSize = (lineBreak / 4) * 3;

                // write this out a block at a time, with separators between.
                while (length > segmentSize) {
                    // encode a segment
                    encoder.encode(data, offset, segmentSize, out);
                    // write an EOL marker
                    out.write(CRLF);
                    offset += segmentSize;
                    length -= segmentSize;
                }

                // any remainder we write out a byte at a time to manage the groupings and
                // the line count appropriately.
                if (length > 0) {
                    while (length > 0) {
                        write(data[offset++]);
                        length--;
                    }
                }
            }
        }
    }

    public void close() throws IOException {
        flush();
        out.close();
    }

    public void flush() throws IOException {
        if (bufferedBytes > 0) {
            encoder.encode(buffer, 0, bufferedBytes, out);
            bufferedBytes = 0;
        }
    }


    /**
     * Check for whether we're about the reach the end of our
     * line limit for an update that's about to occur.  If we will
     * overflow, then a line break is inserted.
     *
     * @param required The space required for this pending write.
     *
     * @exception IOException
     */
    private void checkEOL(int required) throws IOException {
        if (lineBreak != Integer.MAX_VALUE) {
            // if this write would exceed the line maximum, add a linebreak to the stream.
            if (outputCount + required > lineBreak) {
                out.write(CRLF);
                outputCount = 0;
            }
        }
    }

    /**
     * Update the counter of characters on the current working line.
     * This is conditional if we're not working with a line limit.
     *
     * @param added  The number of characters just added.
     */
    private void updateLineCount(int added) {
        if (lineBreak != Integer.MAX_VALUE) {
            outputCount += added;
        }
    }
}

