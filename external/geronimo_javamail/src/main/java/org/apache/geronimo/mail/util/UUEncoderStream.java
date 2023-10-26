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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * An implementation of a FilterOutputStream that encodes the
 * stream data in UUencoding format.  This version does the
 * encoding "on the fly" rather than encoding a single block of
 * data.  Since this version is intended for use by the MimeUtilty class,
 * it also handles line breaks in the encoded data.
 */
public class UUEncoderStream extends FilterOutputStream {

    // default values included on the "begin" prefix of the data stream.
    protected static final int DEFAULT_MODE = 644;
    protected static final String DEFAULT_NAME = "encoder.buf";

    protected static final int MAX_CHARS_PER_LINE = 45;

    // the configured name on the "begin" command.
    protected String name;
    // the configured mode for the "begin" command.
    protected int mode;

    // since this is a filtering stream, we need to wait until we have the first byte written for encoding
    // to write out the "begin" marker.  A real pain, but necessary.
    protected boolean beginWritten = false;


    // our encoder utility class.
    protected UUEncoder encoder = new UUEncoder();

    // Data is generally written out in 45 character lines, so we're going to buffer that amount before
    // asking the encoder to process this.

    // the buffered byte count
    protected int bufferedBytes = 0;

    // we'll encode this part once it is filled up.
    protected byte[] buffer = new byte[45];

    /**
     * Create a Base64 encoder stream that wraps a specifed stream
     * using the default line break size.
     *
     * @param out    The wrapped output stream.
     */
    public UUEncoderStream(OutputStream out) {
        this(out, DEFAULT_NAME, DEFAULT_MODE);
    }


    /**
     * Create a Base64 encoder stream that wraps a specifed stream
     * using the default line break size.
     *
     * @param out    The wrapped output stream.
     * @param name   The filename placed on the "begin" command.
     */
    public UUEncoderStream(OutputStream out, String name) {
        this(out, name, DEFAULT_MODE);
    }


    public UUEncoderStream(OutputStream out, String name, int mode) {
        super(out);
        // fill in the name and mode information.
        this.name = name;
        this.mode = mode;
    }


    private void checkBegin() throws IOException {
        if (!beginWritten) {
            // grumble...OutputStream doesn't directly support writing String data.  We'll wrap this in
            // a PrintStream() to accomplish the task of writing the begin command.

            PrintStream writer = new PrintStream(out);
            // write out the stream with a CRLF marker
            writer.print("begin " + mode + " " + name + "\r\n");
            writer.flush();
            beginWritten = true;
        }
    }

    private void writeEnd() throws IOException {
        PrintStream writer = new PrintStream(out);
        // write out the stream with a CRLF marker
        writer.print("\nend\r\n");
        writer.flush();
    }

    private void flushBuffer() throws IOException {
        // make sure we've written the begin marker first
        checkBegin();
        // ask the encoder to encode and write this out.
        if (bufferedBytes != 0) {
            encoder.encode(buffer, 0, bufferedBytes, out);
            // reset the buffer count
            bufferedBytes = 0;
        }
    }

    private int bufferSpace() {
        return MAX_CHARS_PER_LINE - bufferedBytes;
    }

    private boolean isBufferFull() {
        return bufferedBytes >= MAX_CHARS_PER_LINE;
    }


    // in order for this to work, we need to override the 3 different signatures for write

    public void write(int ch) throws IOException {
        // store this in the buffer.
        buffer[bufferedBytes++] = (byte)ch;

        // if we filled this up, time to encode and write to the output stream.
        if (isBufferFull()) {
            flushBuffer();
        }
    }

    public void write(byte [] data) throws IOException {
        write(data, 0, data.length);
    }

    public void write(byte [] data, int offset, int length) throws IOException {
        // first check to see how much space we have left in the buffer, and copy that over
        int copyBytes = Math.min(bufferSpace(), length);

        System.arraycopy(buffer, bufferedBytes, data, offset, copyBytes);
        bufferedBytes += copyBytes;
        offset += copyBytes;
        length -= copyBytes;

        // if we filled this up, time to encode and write to the output stream.
        if (isBufferFull()) {
            flushBuffer();
        }

        // we've flushed the leading part up to the line break.  Now if we have complete lines
        // of data left, we can have the encoder process all of these lines directly.
        if (length >= MAX_CHARS_PER_LINE) {
            int fullLinesLength = (length / MAX_CHARS_PER_LINE) * MAX_CHARS_PER_LINE;
            // ask the encoder to encode and write this out.
            encoder.encode(data, offset, fullLinesLength, out);
            offset += fullLinesLength;
            length -= fullLinesLength;
        }

        // ok, now we're down to a potential trailing bit we need to move into the
        // buffer for later processing.

        if (length > 0) {
            System.arraycopy(buffer, 0, data, offset, length);
            bufferedBytes += length;
            offset += length;
            length -= length;
        }
    }

    public void flush() throws IOException {
        // flush any unencoded characters we're holding.
        flushBuffer();
        // write out the data end marker
        writeEnd();
        // and flush the output stream too so that this data is available.
        out.flush();
    }

    public void close() throws IOException {
        // flush all of the streams and close the target output stream.
        flush();
        out.close();
    }

}


