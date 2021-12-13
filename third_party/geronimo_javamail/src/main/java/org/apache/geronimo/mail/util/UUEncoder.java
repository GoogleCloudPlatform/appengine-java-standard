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
import java.io.UnsupportedEncodingException;

public class UUEncoder implements Encoder {

    // this is the maximum number of chars allowed per line, since we have to include a uuencoded length at
    // the start of each line.
    static private final int MAX_CHARS_PER_LINE = 45;


    public UUEncoder()
    {
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
    public int encode(byte[] data, int off, int length, OutputStream out) throws IOException
    {
        int byteCount = 0;

        while (true) {
            // keep writing complete lines until we've exhausted the data.
            if (length > MAX_CHARS_PER_LINE) {
                // encode another line and adjust the length and position
                byteCount += encodeLine(data, off, MAX_CHARS_PER_LINE, out);
                length -= MAX_CHARS_PER_LINE;
                off += MAX_CHARS_PER_LINE;
            }
            else {
                // last line.  Encode the partial and quit
                byteCount += encodeLine(data, off, MAX_CHARS_PER_LINE, out);
                break;
            }
        }
        return byteCount;
    }


    /**
     * Encode a single line of data (less than or equal to 45 characters).
     *
     * @param data   The array of byte data.
     * @param off    The starting offset within the data.
     * @param length Length of the data to encode.
     * @param out    The output stream the encoded data is written to.
     *
     * @return The number of bytes written to the output stream.
     * @exception IOException
     */
    private int encodeLine(byte[] data, int offset, int length, OutputStream out) throws IOException {
        // write out the number of characters encoded in this line.
        out.write((byte)((length & 0x3F) + ' '));
        byte a;
        byte b;
        byte c;

        // count the bytes written...we add 2, one for the length and 1 for the linend terminator.
        int bytesWritten = 2;

        for (int i = 0; i < length;) {
            // set the padding defauls
            b = 1;
            c = 1;
            // get the next 3 bytes (if we have them)
            a = data[offset + i++];
            if (i < length) {
                b = data[offset + i++];
                if (i < length) {
                    c = data[offset + i++];
                }
            }

            byte d1 = (byte)(((a >>> 2) & 0x3F) + ' ');
            byte d2 = (byte)(((( a << 4) & 0x30) | ((b >>> 4) & 0x0F)) + ' ');
            byte d3 = (byte)((((b << 2) & 0x3C) | ((c >>> 6) & 0x3)) + ' ');
            byte d4 = (byte)((c & 0x3F) + ' ');

            out.write(d1);
            out.write(d2);
            out.write(d3);
            out.write(d4);

            bytesWritten += 4;
        }

        // terminate with a linefeed alone
        out.write('\n');

        return bytesWritten;
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
    public int decode(byte[] data, int off, int length, OutputStream out) throws IOException
    {
        int bytesWritten = 0;

        while (length > 0) {
            int lineOffset = off;

            // scan forward looking for a EOL terminator for the next line of data.
            while (length > 0 && data[off] != '\n') {
                off++;
                length--;
            }

            // go decode this line of data
            bytesWritten += decodeLine(data, lineOffset, off - lineOffset, out);

            // the offset was left pointing at the EOL character, so step over that one before
            // scanning again.
            off++;
            length--;
        }
        return bytesWritten;
    }


    /**
     * decode a single line of uuencoded byte data writing it to the given output stream
     *
     * @param data   The array of byte data to decode.
     * @param off    Starting offset within the array.
     * @param length The length of data to decode (length does NOT include the terminating new line).
     * @param out    The output stream used to return the decoded data.
     *
     * @return the number of bytes produced.
     * @exception IOException
     */
    private int decodeLine(byte[] data, int off, int length, OutputStream out) throws IOException {
        int count = data[off++];

        // obtain and validate the count
        if (count < ' ') {
            throw new IOException("Invalid UUEncode line length");
        }

        count = (count - ' ') & 0x3F;

        // get the rounded count of characters that should have been used to encode this.  The + 1 is for the
        // length encoded at the beginning
        int requiredLength = (((count * 8) + 5) / 6) + 1;
        if (length < requiredLength) {
            throw new IOException("UUEncoded data and length do not match");
        }

        int bytesWritten = 0;
        // now decode the bytes.
        while (bytesWritten < count) {
            // even one byte of data requires two bytes to encode, so we should have that.
            byte a = (byte)((data[off++] - ' ') & 0x3F);
            byte b = (byte)((data[off++] - ' ') & 0x3F);
            byte c = 0;
            byte d = 0;

            // do the first byte
            byte first = (byte)(((a << 2) & 0xFC) | ((b >>> 4) & 3));
            out.write(first);
            bytesWritten++;

            // still have more bytes to decode? do the second byte of the second.  That requires
            // a third byte from the data.
            if (bytesWritten < count) {
                c = (byte)((data[off++] - ' ') & 0x3F);
                byte second = (byte)(((b << 4) & 0xF0) | ((c >>> 2) & 0x0F));
                out.write(second);
                bytesWritten++;

                // need the third one?
                if (bytesWritten < count) {
                    d = (byte)((data[off++] - ' ') & 0x3F);
                    byte third = (byte)(((c << 6) & 0xC0) | (d & 0x3F));
                    out.write(third);
                    bytesWritten++;
                }
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
    public int decode(String data, OutputStream out) throws IOException
    {
        try {
            // just get the byte data and decode.
            byte[] bytes = data.getBytes("US-ASCII");
            return decode(bytes, 0, bytes.length, out);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("Invalid UUEncoding");
        }
    }
}


