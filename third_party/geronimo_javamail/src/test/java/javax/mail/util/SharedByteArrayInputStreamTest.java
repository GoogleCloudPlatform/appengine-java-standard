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

import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class SharedByteArrayInputStreamTest extends TestCase {
    private String testString = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private byte[] testData = testString.getBytes();



    public SharedByteArrayInputStreamTest(String arg0) {
        super(arg0);
    }

    public void testInput() throws Exception {
        SharedByteArrayInputStream in = new SharedByteArrayInputStream(testData);

    assertEquals('0', in.read());

    assertEquals(1, in.getPosition());

        byte[] bytes = new byte[10];

    assertEquals(10, in.read(bytes));
    assertEquals("123456789a", new String(bytes));
    assertEquals(11, in.getPosition());

    assertEquals(5, in.read(bytes, 5, 5));
    assertEquals("12345bcdef", new String(bytes));
    assertEquals(16, in.getPosition());

    assertEquals(5, in.skip(5));
    assertEquals(21, in.getPosition());
    assertEquals('l', in.read());

        while (in.read() != 'Z') {
        }

    assertEquals(-1, in.read());
    }


    public void testNewStream() throws Exception {
        SharedByteArrayInputStream in = new SharedByteArrayInputStream(testData);

        SharedByteArrayInputStream sub = (SharedByteArrayInputStream)in.newStream(10, 10 + 26);

    assertEquals(0, sub.getPosition());

    assertEquals('0', in.read());
    assertEquals('a', sub.read());

        sub.skip(1);
    assertEquals(2, sub.getPosition());

        while (sub.read() != 'z') {
        }

    assertEquals(-1, sub.read());

        SharedByteArrayInputStream sub2 = (SharedByteArrayInputStream)sub.newStream(5, 10);

    assertEquals(0, sub2.getPosition());
    assertEquals('f', sub2.read());
    }
}
