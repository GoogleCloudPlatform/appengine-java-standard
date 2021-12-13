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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

public class Base64Encoder
    implements Encoder
{
    protected final byte[] encodingTable =
        {
            (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F', (byte)'G',
            (byte)'H', (byte)'I', (byte)'J', (byte)'K', (byte)'L', (byte)'M', (byte)'N',
            (byte)'O', (byte)'P', (byte)'Q', (byte)'R', (byte)'S', (byte)'T', (byte)'U',
            (byte)'V', (byte)'W', (byte)'X', (byte)'Y', (byte)'Z',
            (byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f', (byte)'g',
            (byte)'h', (byte)'i', (byte)'j', (byte)'k', (byte)'l', (byte)'m', (byte)'n',
            (byte)'o', (byte)'p', (byte)'q', (byte)'r', (byte)'s', (byte)'t', (byte)'u',
            (byte)'v',
            (byte)'w', (byte)'x', (byte)'y', (byte)'z',
            (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6',
            (byte)'7', (byte)'8', (byte)'9',
            (byte)'+', (byte)'/'
        };

    protected byte    padding = (byte)'=';

    /*
     * set up the decoding table.
     */
    protected final byte[] decodingTable = new byte[256];

    protected void initialiseDecodingTable()
    {
        for (int i = 0; i < encodingTable.length; i++)
        {
            decodingTable[encodingTable[i]] = (byte)i;
        }
    }

    public Base64Encoder()
    {
        initialiseDecodingTable();
    }

    /**
     * encode the input data producing a base 64 output stream.
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
        int modulus = length % 3;
        int dataLength = (length - modulus);
        int a1, a2, a3;

        for (int i = off; i < off + dataLength; i += 3)
        {
            a1 = data[i] & 0xff;
            a2 = data[i + 1] & 0xff;
            a3 = data[i + 2] & 0xff;

            out.write(encodingTable[(a1 >>> 2) & 0x3f]);
            out.write(encodingTable[((a1 << 4) | (a2 >>> 4)) & 0x3f]);
            out.write(encodingTable[((a2 << 2) | (a3 >>> 6)) & 0x3f]);
            out.write(encodingTable[a3 & 0x3f]);
        }

        /*
         * process the tail end.
         */
        int    b1, b2, b3;
        int    d1, d2;

        switch (modulus)
        {
        case 0:        /* nothing left to do */
            break;
        case 1:
            d1 = data[off + dataLength] & 0xff;
            b1 = (d1 >>> 2) & 0x3f;
            b2 = (d1 << 4) & 0x3f;

            out.write(encodingTable[b1]);
            out.write(encodingTable[b2]);
            out.write(padding);
            out.write(padding);
            break;
        case 2:
            d1 = data[off + dataLength] & 0xff;
            d2 = data[off + dataLength + 1] & 0xff;

            b1 = (d1 >>> 2) & 0x3f;
            b2 = ((d1 << 4) | (d2 >>> 4)) & 0x3f;
            b3 = (d2 << 2) & 0x3f;

            out.write(encodingTable[b1]);
            out.write(encodingTable[b2]);
            out.write(encodingTable[b3]);
            out.write(padding);
            break;
        }

        return (dataLength / 3) * 4 + ((modulus == 0) ? 0 : 4);
    }

    private boolean ignore(
        char    c)
    {
        return (c == '\n' || c =='\r' || c == '\t' || c == ' ');
    }

    /**
     * decode the base 64 encoded byte data writing it to the given output stream,
     * whitespace characters will be ignored.
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
        byte    b1, b2, b3, b4;
        int        outLen = 0;

        int        end = off + length;

        while (end > 0)
        {
            if (!ignore((char)data[end - 1]))
            {
                break;
            }

            end--;
        }

        int  i = off;
        int  finish = end - 4;

        while (i < finish)
        {
            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b1 = decodingTable[data[i++]];

            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b2 = decodingTable[data[i++]];

            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b3 = decodingTable[data[i++]];

            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b4 = decodingTable[data[i++]];

            out.write((b1 << 2) | (b2 >> 4));
            out.write((b2 << 4) | (b3 >> 2));
            out.write((b3 << 6) | b4);

            outLen += 3;
        }

        if (data[end - 2] == padding)
        {
            b1 = decodingTable[data[end - 4]];
            b2 = decodingTable[data[end - 3]];

            out.write((b1 << 2) | (b2 >> 4));

            outLen += 1;
        }
        else if (data[end - 1] == padding)
        {
            b1 = decodingTable[data[end - 4]];
            b2 = decodingTable[data[end - 3]];
            b3 = decodingTable[data[end - 2]];

            out.write((b1 << 2) | (b2 >> 4));
            out.write((b2 << 4) | (b3 >> 2));

            outLen += 2;
        }
        else
        {
            b1 = decodingTable[data[end - 4]];
            b2 = decodingTable[data[end - 3]];
            b3 = decodingTable[data[end - 2]];
            b4 = decodingTable[data[end - 1]];

            out.write((b1 << 2) | (b2 >> 4));
            out.write((b2 << 4) | (b3 >> 2));
            out.write((b3 << 6) | b4);

            outLen += 3;
        }

        return outLen;
    }

    /**
     * decode the base 64 encoded String data writing it to the given output stream,
     * whitespace characters will be ignored.
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

        while (end > 0)
        {
            if (!ignore(data.charAt(end - 1)))
            {
                break;
            }

            end--;
        }

        int    i = 0;
        int   finish = end - 4;

        while (i < finish)
        {
            while ((i < finish) && ignore(data.charAt(i)))
            {
                i++;
            }

            b1 = decodingTable[data.charAt(i++)];

            while ((i < finish) && ignore(data.charAt(i)))
            {
                i++;
            }
            b2 = decodingTable[data.charAt(i++)];

            while ((i < finish) && ignore(data.charAt(i)))
            {
                i++;
            }
            b3 = decodingTable[data.charAt(i++)];

            while ((i < finish) && ignore(data.charAt(i)))
            {
                i++;
            }
            b4 = decodingTable[data.charAt(i++)];

            out.write((b1 << 2) | (b2 >> 4));
            out.write((b2 << 4) | (b3 >> 2));
            out.write((b3 << 6) | b4);

            length += 3;
        }

        if (data.charAt(end - 2) == padding)
        {
            b1 = decodingTable[data.charAt(end - 4)];
            b2 = decodingTable[data.charAt(end - 3)];

            out.write((b1 << 2) | (b2 >> 4));

            length += 1;
        }
        else if (data.charAt(end - 1) == padding)
        {
            b1 = decodingTable[data.charAt(end - 4)];
            b2 = decodingTable[data.charAt(end - 3)];
            b3 = decodingTable[data.charAt(end - 2)];

            out.write((b1 << 2) | (b2 >> 4));
            out.write((b2 << 4) | (b3 >> 2));

            length += 2;
        }
        else
        {
            b1 = decodingTable[data.charAt(end - 4)];
            b2 = decodingTable[data.charAt(end - 3)];
            b3 = decodingTable[data.charAt(end - 2)];
            b4 = decodingTable[data.charAt(end - 1)];

            out.write((b1 << 2) | (b2 >> 4));
            out.write((b2 << 4) | (b3 >> 2));
            out.write((b3 << 6) | b4);

            length += 3;
        }

        return length;
    }

    /**
     * decode the base 64 encoded byte data writing it to the provided byte array buffer.
     *
     * @return the number of bytes produced.
     */
    public int decode(byte[] data, int off, int length, byte[] out) throws IOException
    {
        byte[]    bytes;
        byte    b1, b2, b3, b4;
        int        outLen = 0;

        int        end = off + length;

        while (end > 0)
        {
            if (!ignore((char)data[end - 1]))
            {
                break;
            }

            end--;
        }

        int  i = off;
        int  finish = end - 4;

        while (i < finish)
        {
            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b1 = decodingTable[data[i++]];

            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b2 = decodingTable[data[i++]];

            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b3 = decodingTable[data[i++]];

            while ((i < finish) && ignore((char)data[i]))
            {
                i++;
            }

            b4 = decodingTable[data[i++]];

            out[outLen++] = (byte)((b1 << 2) | (b2 >> 4));
            out[outLen++] = (byte)((b2 << 4) | (b3 >> 2));
            out[outLen++] = (byte)((b3 << 6) | b4);
        }

        if (data[end - 2] == padding)
        {
            b1 = decodingTable[data[end - 4]];
            b2 = decodingTable[data[end - 3]];

            out[outLen++] = (byte)((b1 << 2) | (b2 >> 4));
        }
        else if (data[end - 1] == padding)
        {
            b1 = decodingTable[data[end - 4]];
            b2 = decodingTable[data[end - 3]];
            b3 = decodingTable[data[end - 2]];

            out[outLen++] = (byte)((b1 << 2) | (b2 >> 4));
            out[outLen++] = (byte)((b2 << 4) | (b3 >> 2));
        }
        else
        {
            b1 = decodingTable[data[end - 4]];
            b2 = decodingTable[data[end - 3]];
            b3 = decodingTable[data[end - 2]];
            b4 = decodingTable[data[end - 1]];

            out[outLen++] = (byte)((b1 << 2) | (b2 >> 4));
            out[outLen++] = (byte)((b2 << 4) | (b3 >> 2));
            out[outLen++] = (byte)((b3 << 6) | b4);
        }

        return outLen;
    }

    /**
     * Test if a character is a valid Base64 encoding character.  This
     * must be either a valid digit or the padding character ("=").
     *
     * @param ch     The test character.
     *
     * @return true if this is valid in Base64 encoded data, false otherwise.
     */
    public boolean isValidBase64(int ch) {
        // 'A' has the value 0 in the decoding table, so we need a special one for that
        return ch == padding || ch == 'A' || decodingTable[ch] != 0;
    }


    /**
     * Perform RFC-2047 word encoding using Base64 data encoding.
     *
     * @param in      The source for the encoded data.
     * @param charset The charset tag to be added to each encoded data section.
     * @param out     The output stream where the encoded data is to be written.
     * @param fold    Controls whether separate sections of encoded data are separated by
     *                linebreaks or whitespace.
     *
     * @exception IOException
     */
    public void encodeWord(InputStream in, String charset, OutputStream out, boolean fold) throws IOException
    {
        PrintStream writer = new PrintStream(out);

        // encoded words are restricted to 76 bytes, including the control adornments.
        int limit = 75 - 7 - charset.length();
        boolean firstLine = true;
        StringBuffer encodedString = new StringBuffer(76);

        while (true) {
            // encode the next segment.
            encode(in, encodedString, limit);
            // if we're out of data, nothing will be encoded.
            if (encodedString.length() == 0) {
                break;
            }

            // if we have more than one segment, we need to insert separators.  Depending on whether folding
            // was requested, this is either a blank or a linebreak.
            if (!firstLine) {
                if (fold) {
                    writer.print("\r\n");
                }
                else {
                    writer.print(" ");
                }
            }

            // add the encoded word header
            writer.print("=?");
            writer.print(charset);
            writer.print("?B?");
            // the data
            writer.print(encodedString.toString());
            // and the word terminator.
            writer.print("?=");
            writer.flush();

            // reset our string buffer for the next segment.
            encodedString.setLength(0);
            // we need a delimiter after this 
            firstLine = false; 
        }
    }


    /**
     * Perform RFC-2047 word encoding using Base64 data encoding.
     *
     * @param in      The source for the encoded data.
     * @param charset The charset tag to be added to each encoded data section.
     * @param out     The output stream where the encoded data is to be written.
     * @param fold    Controls whether separate sections of encoded data are separated by
     *                linebreaks or whitespace.
     *
     * @exception IOException
     */
    public void encodeWord(byte[] data, StringBuffer out, String charset) throws IOException
    {
        // append the word header 
        out.append("=?");
        out.append(charset);
        out.append("?B?"); 
        // add on the encodeded data       
        encodeWordData(data, out); 
        // the end of the encoding marker 
        out.append("?="); 
    }
    
    /**
     * encode the input data producing a base 64 output stream.
     *
     * @return the number of bytes produced.
     */
    public void encodeWordData(byte[] data, StringBuffer out) 
    {
        int modulus = data.length % 3;
        int dataLength = (data.length - modulus);
        int a1, a2, a3;

        for (int i = 0; i < dataLength; i += 3)
        {
            a1 = data[i] & 0xff;
            a2 = data[i + 1] & 0xff;
            a3 = data[i + 2] & 0xff;
            
            out.append((char)encodingTable[(a1 >>> 2) & 0x3f]);
            out.append((char)encodingTable[((a1 << 4) | (a2 >>> 4)) & 0x3f]);
            out.append((char)encodingTable[((a2 << 2) | (a3 >>> 6)) & 0x3f]);
            out.append((char)encodingTable[a3 & 0x3f]);
        }

        /*
         * process the tail end.
         */
        int    b1, b2, b3;
        int    d1, d2;

        switch (modulus)
        {
        case 0:        /* nothing left to do */
            break;
        case 1:
            d1 = data[dataLength] & 0xff;
            b1 = (d1 >>> 2) & 0x3f;
            b2 = (d1 << 4) & 0x3f;

            out.append((char)encodingTable[b1]);
            out.append((char)encodingTable[b2]);
            out.append((char)padding);
            out.append((char)padding);
            break;
        case 2:
            d1 = data[dataLength] & 0xff;
            d2 = data[dataLength + 1] & 0xff;

            b1 = (d1 >>> 2) & 0x3f;
            b2 = ((d1 << 4) | (d2 >>> 4)) & 0x3f;
            b3 = (d2 << 2) & 0x3f;

            out.append((char)encodingTable[b1]);
            out.append((char)encodingTable[b2]);
            out.append((char)encodingTable[b3]);
            out.append((char)padding);
            break;
        }
    }
    

    /**
     * encode the input data producing a base 64 output stream.
     *
     * @return the number of bytes produced.
     */
    public void encode(InputStream in, StringBuffer out, int limit) throws IOException
    {
        int count = limit / 4;
        byte [] inBuffer = new byte[3];

        while (count-- > 0) {

            int readCount = in.read(inBuffer);
            // did we get a full triplet?  that's an easy encoding.
            if (readCount == 3) {
                int  a1 = inBuffer[0] & 0xff;
                int  a2 = inBuffer[1] & 0xff;
                int  a3 = inBuffer[2] & 0xff;
                
                out.append((char)encodingTable[(a1 >>> 2) & 0x3f]);
                out.append((char)encodingTable[((a1 << 4) | (a2 >>> 4)) & 0x3f]);
                out.append((char)encodingTable[((a2 << 2) | (a3 >>> 6)) & 0x3f]);
                out.append((char)encodingTable[a3 & 0x3f]);

            }
            else if (readCount <= 0) {
                // eof condition, don'e entirely.
                return;
            }
            else if (readCount == 1) {
                int  a1 = inBuffer[0] & 0xff;
                out.append((char)encodingTable[(a1 >>> 2) & 0x3f]);
                out.append((char)encodingTable[(a1 << 4) & 0x3f]);
                out.append((char)padding);
                out.append((char)padding);
                return;
            }
            else if (readCount == 2) {
                int  a1 = inBuffer[0] & 0xff;
                int  a2 = inBuffer[1] & 0xff;

                out.append((char)encodingTable[(a1 >>> 2) & 0x3f]);
                out.append((char)encodingTable[((a1 << 4) | (a2 >>> 4)) & 0x3f]);
                out.append((char)encodingTable[(a2 << 2) & 0x3f]);
                out.append((char)padding);
                return;
            }
        }
    }
    
    
    /**
     * Estimate the final encoded size of a segment of data. 
     * This is used to ensure that the encoded blocks do 
     * not get split across a unicode character boundary and 
     * that the encoding will fit within the bounds of 
     * a mail header line. 
     * 
     * @param data   The data we're anticipating encoding.
     * 
     * @return The size of the byte data in encoded form. 
     */
    public int estimateEncodedLength(byte[] data) 
    {
        return ((data.length + 2) / 3) * 4; 
    }
}
