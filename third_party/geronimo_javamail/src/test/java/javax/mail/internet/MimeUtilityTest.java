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

package javax.mail.internet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Session;
import javax.mail.util.ByteArrayDataSource;

import junit.framework.TestCase;

public class MimeUtilityTest extends TestCase {

    private byte[] encodeBytes = new byte[] { 32, 104, -61, -87, 33, 32, -61, -96, -61, -88, -61, -76, 117, 32, 33, 33, 33 };

    public void testEncodeDecode() throws Exception {

        byte [] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)i;
        }

        // different lengths test boundary conditions
        doEncodingTest(data, 256, "uuencode");
        doEncodingTest(data, 255, "uuencode");
        doEncodingTest(data, 254, "uuencode");

        doEncodingTest(data, 256, "binary");
        doEncodingTest(data, 256, "7bit");
        doEncodingTest(data, 256, "8bit");
        doEncodingTest(data, 256, "base64");
        doEncodingTest(data, 255, "base64");
        doEncodingTest(data, 254, "base64");

        doEncodingTest(data, 256, "x-uuencode");
        doEncodingTest(data, 256, "x-uue");
        doEncodingTest(data, 256, "quoted-printable");
        doEncodingTest(data, 255, "quoted-printable");
        doEncodingTest(data, 254, "quoted-printable");
    }


    public void testFoldUnfold() throws Exception {
        doFoldTest(0, "This is a short string", "This is a short string");
        doFoldTest(0, "The quick brown fox jumped over the lazy dog. The quick brown fox jumped over the lazy dog. The quick brown fox jumped over the lazy dog.",
            "The quick brown fox jumped over the lazy dog. The quick brown fox jumped\r\n over the lazy dog. The quick brown fox jumped over the lazy dog.");
        doFoldTest(50, "The quick brown fox jumped over the lazy dog. The quick brown fox jumped over the lazy dog. The quick brown fox jumped over the lazy dog.",
            "The quick brown fox jumped\r\n over the lazy dog. The quick brown fox jumped over the lazy dog. The quick\r\n brown fox jumped over the lazy dog.");
        doFoldTest(20, "======================================================================================================================= break should be here",
            "=======================================================================================================================\r\n break should be here");
    }


    public void doEncodingTest(byte[] data, int length, String encoding) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream encoder = MimeUtility.encode(out, encoding);

        encoder.write(data, 0, length);
        encoder.flush();

        byte[] encodedData = out.toByteArray();

        ByteArrayInputStream in = new ByteArrayInputStream(encodedData);

        InputStream decoder = MimeUtility.decode(in, encoding);

        byte[] decodedData = new byte[length];

        int count = decoder.read(decodedData);

        assertEquals(length, count);

        for (int i = 0; i < length; i++) {
            assertEquals(data[i], decodedData[i]);
        }
    }


    public void doFoldTest(int used, String source, String folded) throws Exception {
        String newFolded = MimeUtility.fold(used, source);
        String newUnfolded = MimeUtility.unfold(newFolded);

        assertEquals(folded, newFolded);
        assertEquals(source, newUnfolded);
    }


    public void testEncodeWord() throws Exception {
        assertEquals("abc", MimeUtility.encodeWord("abc"));

        String encodeString = new String(encodeBytes, "UTF-8");
        // default code page dependent, hard to directly test the encoded results
        // The following disabled because it will not succeed on all locales because the
        // code points used in the test string won't round trip properly for all code pages.
        // assertEquals(encodeString, MimeUtility.decodeWord(MimeUtility.encodeWord(encodeString)));

        String encoded = MimeUtility.encodeWord(encodeString, "UTF-8", "Q");
        assertEquals("=?UTF-8?Q?_h=C3=A9!_=C3=A0=C3=A8=C3=B4u_!!!?=", encoded);
        assertEquals(encodeString, MimeUtility.decodeWord(encoded));

        encoded = MimeUtility.encodeWord(encodeString, "UTF-8", "B");
        assertEquals("=?UTF-8?B?IGjDqSEgw6DDqMO0dSAhISE=?=", encoded);
        assertEquals(encodeString, MimeUtility.decodeWord(encoded));
    }


    public void testEncodeText() throws Exception {
        assertEquals("abc", MimeUtility.encodeWord("abc"));

        String encodeString = new String(encodeBytes, "UTF-8");
        // default code page dependent, hard to directly test the encoded results
        // The following disabled because it will not succeed on all locales because the
        // code points used in the test string won't round trip properly for all code pages.
        // assertEquals(encodeString, MimeUtility.decodeText(MimeUtility.encodeText(encodeString)));

        String encoded = MimeUtility.encodeText(encodeString, "UTF-8", "Q");
        assertEquals("=?UTF-8?Q?_h=C3=A9!_=C3=A0=C3=A8=C3=B4u_!!!?=", encoded);
        assertEquals(encodeString, MimeUtility.decodeText(encoded));

        encoded = MimeUtility.encodeText(encodeString, "UTF-8", "B");
        assertEquals("=?UTF-8?B?IGjDqSEgw6DDqMO0dSAhISE=?=", encoded);
        assertEquals(encodeString, MimeUtility.decodeText(encoded));
        
        // this has multiple byte characters and is longer than the 76 character grouping, so this 
        // hits a lot of different boundary conditions 
        String subject = "\u03a0\u03a1\u03a2\u03a3\u03a4\u03a5\u03a6\u03a7 \u03a8\u03a9\u03aa\u03ab \u03ac\u03ad\u03ae\u03af\u03b0 \u03b1\u03b2\u03b3\u03b4\u03b5 \u03b6\u03b7\u03b8\u03b9\u03ba \u03bb\u03bc\u03bd\u03be\u03bf\u03c0 \u03c1\u03c2\u03c3\u03c4\u03c5\u03c6\u03c7 \u03c8\u03c9\u03ca\u03cb\u03cd\u03ce \u03cf\u03d0\u03d1\u03d2";
        encoded = MimeUtility.encodeText(subject, "utf-8", "Q"); 
        assertEquals(subject, MimeUtility.decodeText(encoded));
        
        encoded = MimeUtility.encodeText(subject, "utf-8", "B"); 
        assertEquals(subject, MimeUtility.decodeText(encoded));
    }


    public void testGetEncoding() throws Exception {
        ByteArrayDataSource source = new ByteArrayDataSource(new byte[] { 'a', 'b', 'c'}, "text/plain");

        assertEquals("7bit", MimeUtility.getEncoding(source));

        source = new ByteArrayDataSource(new byte[] { 'a', 'b', (byte)0x81}, "text/plain");

        assertEquals("quoted-printable", MimeUtility.getEncoding(source));

        source = new ByteArrayDataSource(new byte[] { 'a', (byte)0x82, (byte)0x81}, "text/plain");

        assertEquals("base64", MimeUtility.getEncoding(source));


        source = new ByteArrayDataSource(new byte[] { 'a', 'b', 'c'}, "application/binary");

        assertEquals("7bit", MimeUtility.getEncoding(source));

        source = new ByteArrayDataSource(new byte[] { 'a', 'b', (byte)0x81}, "application/binary");

        assertEquals("base64", MimeUtility.getEncoding(source));

        source = new ByteArrayDataSource(new byte[] { 'a', (byte)0x82, (byte)0x81}, "application/binary");

        assertEquals("base64", MimeUtility.getEncoding(source));
    }


    public void testQuote() throws Exception {
        assertEquals("abc", MimeUtility.quote("abc", "&*%"));
        assertEquals("\"abc&\"", MimeUtility.quote("abc&", "&*%"));
        assertEquals("\"abc\\\"\"", MimeUtility.quote("abc\"", "&*%"));
        assertEquals("\"abc\\\\\"", MimeUtility.quote("abc\\", "&*%"));
        assertEquals("\"abc\\\r\"", MimeUtility.quote("abc\r", "&*%"));
        assertEquals("\"abc\\\n\"", MimeUtility.quote("abc\n", "&*%"));
    }
}
