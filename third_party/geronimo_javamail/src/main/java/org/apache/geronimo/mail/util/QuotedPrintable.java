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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class QuotedPrintable {
    // NOTE:  the QuotedPrintableEncoder class needs to keep some active state about what's going on with
    // respect to line breaks and significant white spaces.  This makes it difficult to keep one static
    // instance of the decode around for reuse.


    /**
     * encode the input data producing a Q-P encoded byte array.
     *
     * @return a byte array containing the Q-P encoded data.
     */
    public static byte[] encode(
        byte[]    data)
    {
        return encode(data, 0, data.length);
    }

    /**
     * encode the input data producing a Q-P encoded byte array.
     *
     * @return a byte array containing the Q-P encoded data.
     */
    public static byte[] encode(
        byte[]    data,
        int       off,
        int       length)
    {
        QuotedPrintableEncoder encoder = new QuotedPrintableEncoder();

        ByteArrayOutputStream    bOut = new ByteArrayOutputStream();

        try
        {
            encoder.encode(data, off, length, bOut);
        }
        catch (IOException e)
        {
            throw new RuntimeException("exception encoding Q-P encoded string: " + e);
        }

        return bOut.toByteArray();
    }

    /**
     * Q-P encode the byte data writing it to the given output stream.
     *
     * @return the number of bytes produced.
     */
    public static int encode(
        byte[]         data,
        OutputStream   out)
        throws IOException
    {
        QuotedPrintableEncoder encoder = new QuotedPrintableEncoder();

        return encoder.encode(data, 0, data.length, out);
    }

    /**
     * Q-P encode the byte data writing it to the given output stream.
     *
     * @return the number of bytes produced.
     */
    public static int encode(
        byte[]         data,
        int            off,
        int            length,
        OutputStream   out)
        throws IOException
    {
        QuotedPrintableEncoder encoder = new QuotedPrintableEncoder();
        return encoder.encode(data, 0, data.length, out);
    }

    /**
     * decode the Q-P encoded input data. It is assumed the input data is valid.
     *
     * @return a byte array representing the decoded data.
     */
    public static byte[] decode(
        byte[]    data)
    {
        ByteArrayOutputStream    bOut = new ByteArrayOutputStream();

        QuotedPrintableEncoder encoder = new QuotedPrintableEncoder();
        try
        {
            encoder.decode(data, 0, data.length, bOut);
        }
        catch (IOException e)
        {
            throw new RuntimeException("exception decoding Q-P encoded string: " + e);
        }

        return bOut.toByteArray();
    }

    /**
     * decode the UUEncided String data.
     *
     * @return a byte array representing the decoded data.
     */
    public static byte[] decode(
        String    data)
    {
        QuotedPrintableEncoder encoder = new QuotedPrintableEncoder();
        ByteArrayOutputStream    bOut = new ByteArrayOutputStream();

        try
        {
            encoder.decode(data, bOut);
        }
        catch (IOException e)
        {
            throw new RuntimeException("exception decoding Q-P encoded string: " + e);
        }

        return bOut.toByteArray();
    }

    /**
     * decode the Q-P encoded encoded String data writing it to the given output stream.
     *
     * @return the number of bytes produced.
     */
    public static int decode(
        String          data,
        OutputStream    out)
        throws IOException
    {
        QuotedPrintableEncoder encoder = new QuotedPrintableEncoder();
        return encoder.decode(data, out);
    }

    /**
     * decode the base Q-P encoded String data writing it to the given output stream,
     * whitespace characters will be ignored.
     *
     * @param data   The array data to decode.
     * @param out    The output stream for the data.
     *
     * @return the number of bytes produced.
     * @exception IOException
     */
    public static int decode(byte [] data, OutputStream out) throws IOException
    {
        QuotedPrintableEncoder encoder = new QuotedPrintableEncoder();
        return encoder.decode(data, 0, data.length, out);
    }
}

