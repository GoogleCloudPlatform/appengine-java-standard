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

/**
 * An implementation of an OutputStream that writes the data directly
 * out to a StringBuffer object.  Useful for applications where an
 * intermediate ByteArrayOutputStream is required to append generated
 * characters to a StringBuffer;
 */
public class StringBufferOutputStream extends OutputStream {

    // the target buffer
    protected StringBuffer buffer;

    /**
     * Create an output stream that writes to the target StringBuffer
     *
     * @param out    The wrapped output stream.
     */
    public StringBufferOutputStream(StringBuffer out) {
        buffer = out;
    }


    // in order for this to work, we only need override the single character form, as the others
    // funnel through this one by default.
    public void write(int ch) throws IOException {
        // just append the character
        buffer.append((char)ch);
    }
}



