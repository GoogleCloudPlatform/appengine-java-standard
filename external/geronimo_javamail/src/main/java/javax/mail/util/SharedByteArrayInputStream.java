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

package javax.mail.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.mail.internet.SharedInputStream;

public class SharedByteArrayInputStream extends ByteArrayInputStream implements SharedInputStream {

    /**
     * Position within shared buffer that this stream starts at.
     */
    protected int start;

    /**
     * Create a SharedByteArrayInputStream that shares the entire
     * buffer.
     *
     * @param buf    The input data.
     */
    public SharedByteArrayInputStream(byte[] buf) {
        this(buf, 0, buf.length);
    }


    /**
     * Create a SharedByteArrayInputStream using a subset of the
     * array data.
     *
     * @param buf    The source data array.
     * @param offset The starting offset within the array.
     * @param length The length of data to use.
     */
    public SharedByteArrayInputStream(byte[] buf, int offset, int length) {
        super(buf, offset, length);
        start = offset;
    }


    /**
     * Get the position within the output stream, adjusted by the
     * starting offset.
     *
     * @return The adjusted position within the stream.
     */
    public long getPosition() {
        return pos - start;
    }


    /**
     * Create a new input stream from this input stream, accessing
     * a subset of the data.  Think of it as a substring operation
     * for a stream.
     *
     * The starting offset must be non-negative.  The end offset can
     * by -1, which means use the remainder of the stream.
     *
     * @param offset The starting offset.
     * @param end    The end offset (which can be -1).
     *
     * @return An InputStream configured to access the indicated data subrange.
     */
    public InputStream newStream(long offset, long end) {
        if (offset < 0) {
            throw new IllegalArgumentException("Starting position must be non-negative");
        }
        if (end == -1) {
            end = count - start;
        }
        return new SharedByteArrayInputStream(buf, start + (int)offset, (int)(end - offset));
    }
}
