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

import java.io.File;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class SharedFileInputStreamTest extends TestCase {

    File basedir = new File(System.getProperty("basedir", "."));
    File testInput = new File(basedir, "src/test/resources/test.dat");

    public SharedFileInputStreamTest(String arg0) {
        super(arg0);
    }

    public void testInput() throws Exception {
        doTestInput(new SharedFileInputStream(testInput));
        doTestInput(new SharedFileInputStream(testInput.getPath()));

        doTestInput(new SharedFileInputStream(testInput, 16));
        doTestInput(new SharedFileInputStream(testInput.getPath(), 16));
    }


    public void doTestInput(SharedFileInputStream in) throws Exception {
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

        while (in.read() != '\n' ) {
        }

    assertEquals(-1, in.read());

        in.close();
    }


    public void testNewStream() throws Exception {
        SharedFileInputStream in = new SharedFileInputStream(testInput);

        SharedFileInputStream sub = (SharedFileInputStream)in.newStream(10, 10 + 26);

    assertEquals(0, sub.getPosition());

    assertEquals('0', in.read());
    assertEquals('a', sub.read());

        sub.skip(1);
    assertEquals(2, sub.getPosition());

        while (sub.read() != 'z') {
        }

    assertEquals(-1, sub.read());

        SharedFileInputStream sub2 = (SharedFileInputStream)sub.newStream(5, 10);

        sub.close();    // should not close in or sub2

    assertEquals(0, sub2.getPosition());
    assertEquals('f', sub2.read());

    assertEquals('1', in.read()); // should still work

        sub2.close();

    assertEquals('2', in.read()); // should still work

        in.close();
    }


    public void testMark() throws Exception {
        doMarkTest(new SharedFileInputStream(testInput, 10));

        SharedFileInputStream in = new SharedFileInputStream(testInput, 10);

        SharedFileInputStream sub = (SharedFileInputStream)in.newStream(5, -1);
        doMarkTest(sub);
    }


    private void doMarkTest(SharedFileInputStream in) throws Exception {
         assertTrue(in.markSupported());

         byte[] buffer = new byte[60];

         in.read();
         in.read();
         in.mark(50);

         int markSpot = in.read();

         in.read(buffer, 0, 20);

         in.reset();

         assertEquals(markSpot, in.read());
         in.read(buffer, 0, 40);
         in.reset();
         assertEquals(markSpot, in.read());

         in.read(buffer, 0, 51);

         try {
             in.reset();
             fail();
         } catch (IOException e) {
         }
    }
}

