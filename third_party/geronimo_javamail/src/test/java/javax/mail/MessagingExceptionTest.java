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

package javax.mail;

import junit.framework.TestCase;

/**
 * @version $Revision $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class MessagingExceptionTest extends TestCase {
    private RuntimeException d;
    private MessagingException c;
    private MessagingException b;
    private MessagingException a;
    public MessagingExceptionTest(String name) {
        super(name);
    }
    protected void setUp() throws Exception {
        super.setUp();
        a = new MessagingException("A");
        b = new MessagingException("B");
        c = new MessagingException("C");
        d = new RuntimeException("D");
    }
    public void testMessagingExceptionString() {
        assertEquals("A", a.getMessage());
    }
    public void testNextException() {
        assertTrue(a.setNextException(b));
        assertEquals(b, a.getNextException());
        assertTrue(a.setNextException(c));
        assertEquals(b, a.getNextException());
        assertEquals(c, b.getNextException());
        String message = a.getMessage();
        int ap = message.indexOf("A");
        int bp = message.indexOf("B");
        int cp = message.indexOf("C");
        assertTrue("A does not contain 'A'", ap != -1);
        assertTrue("B does not contain 'B'", bp != -1);
        assertTrue("C does not contain 'C'", cp != -1);
    }
    public void testNextExceptionWrong() {
        assertTrue(a.setNextException(d));
        assertFalse(a.setNextException(b));
    }
    public void testNextExceptionWrong2() {
        assertTrue(a.setNextException(d));
        assertFalse(a.setNextException(b));
    }
    public void testMessagingExceptionStringException() {
        MessagingException x = new MessagingException("X", a);
        assertEquals("X (javax.mail.MessagingException: A)", x.getMessage());
        assertEquals(a, x.getNextException());
        assertEquals(a, x.getCause());
    }
}
