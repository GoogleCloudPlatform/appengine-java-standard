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
import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class ByteArrayDataSourceTest extends TestCase {
    public ByteArrayDataSourceTest(String arg0) {
        super(arg0);
    }

    public void testByteArray() throws Exception {
        doDataSourceTest(new ByteArrayDataSource("0123456789", "text/plain"), "text/plain");
        doDataSourceTest(new ByteArrayDataSource("0123456789".getBytes(), "text/xml"), "text/xml");
        ByteArrayInputStream in = new ByteArrayInputStream("0123456789".getBytes());

        doDataSourceTest(new ByteArrayDataSource(in, "text/html"), "text/html");

        try {
            ByteArrayDataSource source = new ByteArrayDataSource("01234567890", "text/plain");
            source.getOutputStream();
            fail();
        } catch (IOException e) {
        }

        ByteArrayDataSource source = new ByteArrayDataSource("01234567890", "text/plain");
    assertEquals("", source.getName());

        source.setName("fred");
    assertEquals("fred", source.getName());
    }


    private void doDataSourceTest(ByteArrayDataSource source, String type) throws Exception {
        assertEquals(type, source.getContentType());

        InputStream in = source.getInputStream();
        byte[] bytes = new byte[10];

        int count = in.read(bytes);

        assertEquals(count, bytes.length);
        assertEquals("0123456789", new String(bytes));
    }
}

