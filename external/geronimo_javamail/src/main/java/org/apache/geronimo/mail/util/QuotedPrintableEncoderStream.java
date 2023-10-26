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
 * stream data in Q-P encoding format.  This version does the
 * encoding "on the fly" rather than encoding a single block of
 * data.  Since this version is intended for use by the MimeUtilty class,
 * it also handles line breaks in the encoded data.
 */
public class QuotedPrintableEncoderStream extends FilterOutputStream {
    // our hex encoder utility class.
    protected QuotedPrintableEncoder encoder;

    // our default for line breaks
    protected static final int DEFAULT_LINEBREAK = 76;

    // the instance line break value
    protected int lineBreak;

    /**
     * Create a Base64 encoder stream that wraps a specifed stream
     * using the default line break size.
     *
     * @param out    The wrapped output stream.
     */
    public QuotedPrintableEncoderStream(OutputStream out) {
        this(out, DEFAULT_LINEBREAK);
    }


    public QuotedPrintableEncoderStream(OutputStream out, int lineBreak) {
        super(out);
        // lines are processed only in multiple of 4, so round this down.
        this.lineBreak = (lineBreak / 4) * 4 ;

        // create an encoder configured to this amount
        encoder = new QuotedPrintableEncoder(out, this.lineBreak);
    }


    public void write(int ch) throws IOException {
        // have the encoder do the heavy lifting here.
        encoder.encode(ch);
    }

    public void write(byte [] data) throws IOException {
        write(data, 0, data.length);
    }

    public void write(byte [] data, int offset, int length) throws IOException {
        // the encoder does the heavy lifting here.
        encoder.encode(data, offset, length);
    }

    public void close() throws IOException {
        out.close();
    }

    public void flush() throws IOException {
        out.flush();
    }
}


