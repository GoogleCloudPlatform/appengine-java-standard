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
 * stream data in Q-P encoding format.  This version does the
 * decoding "on the fly" rather than decoding a single block of
 * data.  Since this version is intended for use by the MimeUtilty class,
 * it also handles line breaks in the encoded data.
 */
public class QuotedPrintableDecoderStream extends FilterInputStream {
    // our decoder for processing the data
    protected QuotedPrintableEncoder decoder;


    /**
     * Stream constructor.
     *
     * @param in     The InputStream this stream is filtering.
     */
    public QuotedPrintableDecoderStream(InputStream in) {
        super(in);
        decoder = new QuotedPrintableEncoder();
    }

    // in order to function as a filter, these streams need to override the different
    // read() signatures.


    /**
     * Read a single byte from the stream.
     *
     * @return The next byte of the stream.  Returns -1 for an EOF condition.
     * @exception IOException
     */
    public int read() throws IOException
    {
        // just get a single byte from the decoder
        return decoder.decode(in);
    }


    /**
     * Read a buffer of data from the input stream.
     *
     * @param buffer The target byte array the data is placed into.
     * @param offset The starting offset for the read data.
     * @param length How much data is requested.
     *
     * @return The number of bytes of data read.
     * @exception IOException
     */
    public int read(byte [] buffer, int offset, int length) throws IOException {

        for (int i = 0; i < length; i++) {
            int ch = decoder.decode(in);
            if (ch == -1) {
                return i == 0 ? -1 : i;
            }
            buffer[offset + i] = (byte)ch;
        }

        return length;
    }


    /**
     * Indicate whether this stream supports the mark() operation.
     *
     * @return Always returns false.
     */
    public boolean markSupported() {
        return false;
    }


    /**
     * Give an estimate of how much additional data is available
     * from this stream.
     *
     * @return Always returns -1.
     * @exception IOException
     */
    public int available() throws IOException {
        // this is almost impossible to determine at this point
        return -1;
    }
}


