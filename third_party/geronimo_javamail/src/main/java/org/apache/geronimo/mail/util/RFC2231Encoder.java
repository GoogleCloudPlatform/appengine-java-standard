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
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import javax.mail.internet.MimeUtility;

/**
 * Encoder for RFC2231 encoded parameters
 *
 * RFC2231 string are encoded as
 *
 *    charset'language'encoded-text
 *
 * and
 *
 *    encoded-text = *(char / hexchar)
 *
 * where
 *
 *    char is any ASCII character in the range 33-126, EXCEPT
 *    the characters "%" and " ".
 *
 *    hexchar is an ASCII "%" followed by two upper case
 *    hexadecimal digits.
 */

public class RFC2231Encoder implements Encoder
{
    protected final byte[] encodingTable =
        {
            (byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6', (byte)'7',
            (byte)'8', (byte)'9', (byte)'A', (byte)'B', (byte)'C', (byte)'D', (byte)'E', (byte)'F'
        };

    protected String DEFAULT_SPECIALS = " *'%";
    protected String specials = DEFAULT_SPECIALS;

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

    public RFC2231Encoder()
    {
        this(null);
    }

    public RFC2231Encoder(String specials)
    {
        if (specials != null) {
            this.specials = DEFAULT_SPECIALS + specials;
        }
        initialiseDecodingTable();
    }


    /**
     * encode the input data producing an RFC2231 output stream.
     *
     * @return the number of bytes produced.
     */
    public int encode(byte[] data, int off, int length, OutputStream out) throws IOException {

        int bytesWritten = 0;
        for (int i = off; i < (off + length); i++)
        {
            int ch = data[i] & 0xff;
            // character tha must be encoded?  Prefix with a '%' and encode in hex.
            if (ch <= 32 || ch >= 127 || specials.indexOf(ch) != -1) {
                out.write((byte)'%');
                out.write(encodingTable[ch >> 4]);
                out.write(encodingTable[ch & 0xf]);
                bytesWritten += 3;
            }
            else {
                // add unchanged.
                out.write((byte)ch);
                bytesWritten++;
            }
        }

        return bytesWritten;
    }


    /**
     * decode the RFC2231 encoded byte data writing it to the given output stream
     *
     * @return the number of bytes produced.
     */
    public int decode(byte[] data, int off, int length, OutputStream out) throws IOException {
        int        outLen = 0;
        int        end = off + length;

        int i = off;
        while (i < end)
        {
            byte v = data[i++];
            // a percent is a hex character marker, need to decode a hex value.
            if (v == '%') {
                byte b1 = decodingTable[data[i++]];
                byte b2 = decodingTable[data[i++]];
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
     * decode the RFC2231 encoded String data writing it to the given output stream.
     *
     * @return the number of bytes produced.
     */
    public int decode(String data, OutputStream out) throws IOException
    {
        int        length = 0;
        int        end = data.length();

        int i = 0;
        while (i < end)
        {
            char v = data.charAt(i++);
            if (v == '%') {
                byte b1 = decodingTable[data.charAt(i++)];
                byte b2 = decodingTable[data.charAt(i++)];

                out.write((b1 << 4) | b2);
            }
            else {
                out.write((byte)v);
            }
            length++;
        }

        return length;
    }


    /**
     * Encode a string as an RFC2231 encoded parameter, using the
     * given character set and language.
     *
     * @param charset  The source character set (the MIME version).
     * @param language The encoding language.
     * @param data     The data to encode.
     *
     * @return The encoded string.
     */
    public String encode(String charset, String language, String data) throws IOException {

        byte[] bytes = null;
        try {
            // the charset we're adding is the MIME-defined name.  We need the java version
            // in order to extract the bytes.
            bytes = data.getBytes(MimeUtility.javaCharset(charset));
        } catch (UnsupportedEncodingException e) {
            // we have a translation problem here.
            return null;
        }

        StringBuffer result = new StringBuffer();

        // append the character set, if we have it.
        if (charset != null) {
            result.append(charset);
        }
        // the field marker is required.
        result.append("'");

        // and the same for the language.
        if (language != null) {
            result.append(language);
        }
        // the field marker is required.
        result.append("'");

        // wrap an output stream around our buffer for the decoding
        OutputStream out = new StringBufferOutputStream(result);

        // encode the data stream
        encode(bytes, 0, bytes.length, out);

        // finis!
        return result.toString();
    }


    /**
     * Decode an RFC2231 encoded string.
     *
     * @param data   The data to decode.
     *
     * @return The decoded string.
     * @exception IOException
     * @exception UnsupportedEncodingException
     */
    public String decode(String data) throws IOException, UnsupportedEncodingException {
        // get the end of the language field
        int charsetEnd = data.indexOf('\'');
        // uh oh, might not be there
        if (charsetEnd == -1) {
            throw new IOException("Missing charset in RFC2231 encoded value");
        }

        String charset = data.substring(0, charsetEnd);

        // now pull out the language the same way
        int languageEnd = data.indexOf('\'', charsetEnd + 1);
        if (languageEnd == -1) {
            throw new IOException("Missing language in RFC2231 encoded value");
        }

        String language = data.substring(charsetEnd + 1, languageEnd);

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length());

        // decode the data
        decode(data.substring(languageEnd + 1), out);

        byte[] bytes = out.toByteArray();
        // build a new string from this using the java version of the encoded charset.
        return new String(bytes, 0, bytes.length, MimeUtility.javaCharset(charset));
    }
}
