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

public class XTextEncoder
    implements Encoder
{
    protected final byte[] encodingTable =
        {
            (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6', (byte)'7',
            (byte)'8', (byte)'9', (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F'
        };

    /*
     * set up the decoding table.
     */
    protected final byte[] decodingTable = new byte[128];

    protected void initialiseDecodingTable()
    {
        for (int i = 0; i < encodingTable.length; i++)
        {
            decodingTable[encodingTable[i]] = (byte)i;
        }
    }

    public XTextEncoder()
    {
        initialiseDecodingTable();
    }

    /**
     * encode the input data producing an XText output stream.
     *
     * @return the number of bytes produced.
     */
    public int encode(
        byte[]                data,
        int                    off,
        int                    length,
        OutputStream    out)
        throws IOException
    {
        int bytesWritten = 0;

        for (int i = off; i < (off + length); i++)
        {
            int    v = data[i] & 0xff;
            // character tha must be encoded?  Prefix with a '+' and encode in hex.
            if (v < 33 || v > 126 || v == '+' || v == '+') {
                out.write((byte)'+');
                out.write(encodingTable[(v >>> 4)]);
                out.write(encodingTable[v & 0xf]);
                bytesWritten += 3;
            }
            else {
                // add unchanged.
                out.write((byte)v);
                bytesWritten++;
            }
        }

        return bytesWritten;
    }


    /**
     * decode the xtext encoded byte data writing it to the given output stream
     *
     * @return the number of bytes produced.
     */
    public int decode(
        byte[]                data,
        int                    off,
        int                    length,
        OutputStream    out)
        throws IOException
    {
        byte[]    bytes;
        byte    b1, b2;
        int        outLen = 0;

        int        end = off + length;

        int i = off;
        while (i < end)
        {
            byte v = data[i++];
            // a plus is a hex character marker, need to decode a hex value.
            if (v == '+') {
                b1 = decodingTable[data[i++]];
                b2 = decodingTable[data[i++]];
                out.write((b1 << 4) | b2);
            }
            else {
                // copied over unchanged.
                out.write(v);
            }
            // always just one byte added
            outLen++;
        }

        return outLen;
    }

    /**
     * decode the xtext encoded String data writing it to the given output stream.
     *
     * @return the number of bytes produced.
     */
    public int decode(
        String                data,
        OutputStream    out)
        throws IOException
    {
        byte[]    bytes;
        byte    b1, b2, b3, b4;
        int        length = 0;

        int        end = data.length();

        int i = 0;
        while (i < end)
        {
            char v = data.charAt(i++);
            if (v == '+') {
                b1 = decodingTable[data.charAt(i++)];
                b2 = decodingTable[data.charAt(i++)];

                out.write((b1 << 4) | b2);
            }
            else {
                out.write((byte)v);
            }
            length++;
        }

        return length;
    }
}

