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

import java.net.MalformedURLException;
import java.net.URL;

import junit.framework.TestCase;

/**
 * @version $Rev: 593290 $ $Date: 2007-11-08 14:18:29 -0600 (Thu, 08 Nov 2007) $
 */
public class URLNameTest extends TestCase {
    public URLNameTest(String name) {
        super(name);
    }

    public void testURLNameString() {
        String s;
        URLName name;

        s = "http://www.apache.org";
        name = new URLName(s);
        assertEquals(s, name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL(s), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        s = "http://www.apache.org/file/file1#ref";
        name = new URLName(s);
        assertEquals(s, name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/file1", name.getFile());
        assertEquals("ref", name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL(s), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        s = "http://www.apache.org/file/";
        name = new URLName(s);
        assertEquals(s, name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/", name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL(s), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        s = "http://john@www.apache.org/file/";
        name = new URLName(s);
        assertEquals(s, name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/", name.getFile());
        assertNull(name.getRef());
        assertEquals("john", name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL(s), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        s = "http://john:doe@www.apache.org/file/";
        name = new URLName(s);
        assertEquals(s, name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/", name.getFile());
        assertNull(name.getRef());
        assertEquals("john", name.getUsername());
        assertEquals("doe", name.getPassword());
        try {
            assertEquals(new URL(s), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }
        
        s = "http://john%40gmail.com:doe@www.apache.org/file/";
        name = new URLName(s);
        assertEquals(s, name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/", name.getFile());
        assertNull(name.getRef());
        assertEquals("john@gmail.com", name.getUsername());
        assertEquals("doe", name.getPassword());
        try {
            assertEquals(new URL(s), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        s = "file/file2";
        name = new URLName(s);
        assertNull(name.getProtocol());
        assertNull(name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/file2", name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            name.getURL();
            fail();
        } catch (MalformedURLException e) {
            // OK
        }

        name = new URLName((String) null);
        assertNull( name.getProtocol());
        assertNull(name.getHost());
        assertEquals(-1, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            name.getURL();
            fail();
        } catch (MalformedURLException e) {
            // OK
        }

        name = new URLName("");
        assertNull( name.getProtocol());
        assertNull(name.getHost());
        assertEquals(-1, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            name.getURL();
            fail();
        } catch (MalformedURLException e) {
            // OK
        }
    }

    public void testURLNameAll() {
        URLName name;
        name = new URLName(null, null, -1, null, null, null);
        assertNull(name.getProtocol());
        assertNull(name.getHost());
        assertEquals(-1, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            name.getURL();
            fail();
        } catch (MalformedURLException e) {
            // OK
        }

        name = new URLName("", "", -1, "", "", "");
        assertNull(name.getProtocol());
        assertNull(name.getHost());
        assertEquals(-1, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            name.getURL();
            fail();
        } catch (MalformedURLException e) {
            // OK
        }

        name = new URLName("http", "www.apache.org", -1, null, null, null);
        assertEquals("http://www.apache.org", name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL("http://www.apache.org"), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        name = new URLName("http", "www.apache.org", 8080, "", "", "");
        assertEquals("http://www.apache.org:8080", name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(8080, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL("http://www.apache.org:8080"), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        name = new URLName("http", "www.apache.org", -1, "file/file2", "", "");
        assertEquals("http://www.apache.org/file/file2", name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/file2", name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL("http://www.apache.org/file/file2"), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        name = new URLName("http", "www.apache.org", -1, "file/file2", "john", "");
        assertEquals("http://john@www.apache.org/file/file2", name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/file2", name.getFile());
        assertNull(name.getRef());
        assertEquals("john", name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL("http://john@www.apache.org/file/file2"), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        name = new URLName("http", "www.apache.org", -1, "file/file2", "john", "doe");
        assertEquals("http://john:doe@www.apache.org/file/file2", name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/file2", name.getFile());
        assertNull(name.getRef());
        assertEquals("john", name.getUsername());
        assertEquals("doe", name.getPassword());
        try {
            assertEquals(new URL("http://john:doe@www.apache.org/file/file2"), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        name = new URLName("http", "www.apache.org", -1, "file/file2", "john@gmail.com", "doe");
        assertEquals("http://john%40gmail.com:doe@www.apache.org/file/file2", name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/file2", name.getFile());
        assertNull(name.getRef());
        assertEquals("john@gmail.com", name.getUsername());
        assertEquals("doe", name.getPassword());
        try {
            assertEquals(new URL("http://john%40gmail.com:doe@www.apache.org/file/file2"), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }

        name = new URLName("http", "www.apache.org", -1, "file/file2", "", "doe");
        assertEquals("http://www.apache.org/file/file2", name.toString());
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("file/file2", name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(new URL("http://www.apache.org/file/file2"), name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }
    }

    public void testURLNameURL() throws MalformedURLException {
        URL url;
        URLName name;

        url = new URL("http://www.apache.org");
        name = new URLName(url);
        assertEquals("http", name.getProtocol());
        assertEquals("www.apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertNull(name.getFile());
        assertNull(name.getRef());
        assertNull(name.getUsername());
        assertNull(name.getPassword());
        try {
            assertEquals(url, name.getURL());
        } catch (MalformedURLException e) {
            fail();
        }
    }

    public void testEquals() throws MalformedURLException {
        URLName name1 = new URLName("http://www.apache.org");
        assertEquals(name1, new URLName("http://www.apache.org"));
        assertEquals(name1, new URLName(new URL("http://www.apache.org")));
        assertEquals(name1, new URLName("http", "www.apache.org", -1, null, null, null));
        assertEquals(name1, new URLName("http://www.apache.org#foo")); // wierd but ref is not part of the equals contract
        assertTrue(!name1.equals(new URLName("http://www.apache.org:8080")));
        assertTrue(!name1.equals(new URLName("http://cvs.apache.org")));
        assertTrue(!name1.equals(new URLName("https://www.apache.org")));

        name1 = new URLName("http://john:doe@www.apache.org");
        assertEquals(name1, new URLName(new URL("http://john:doe@www.apache.org")));
        assertEquals(name1, new URLName("http", "www.apache.org", -1, null, "john", "doe"));
        assertTrue(!name1.equals(new URLName("http://john:xxx@www.apache.org")));
        assertTrue(!name1.equals(new URLName("http://xxx:doe@www.apache.org")));
        assertTrue(!name1.equals(new URLName("http://www.apache.org")));

        assertEquals(new URLName("http://john@www.apache.org"), new URLName("http", "www.apache.org", -1, null, "john", null));
        assertEquals(new URLName("http://www.apache.org"), new URLName("http", "www.apache.org", -1, null, null, "doe"));
    }

    public void testHashCode() {
        URLName name1 = new URLName("http://www.apache.org/file");
        URLName name2 = new URLName("http://www.apache.org/file#ref");
        assertTrue(name1.equals(name2));
        assertTrue(name1.hashCode() == name2.hashCode());
    }

    public void testNullProtocol() {
        URLName name1 = new URLName(null, "www.apache.org", -1, null, null, null);
        assertTrue(!name1.equals(name1));
    }

    public void testOpaqueSchemes() {
        String s;
        URLName name;

        // not strictly opaque but no protocol handler installed
        s = "foo://jdoe@apache.org/INBOX";
        name = new URLName(s);
        assertEquals(s, name.toString());
        assertEquals("foo", name.getProtocol());
        assertEquals("apache.org", name.getHost());
        assertEquals(-1, name.getPort());
        assertEquals("INBOX", name.getFile());
        assertNull(name.getRef());
        assertEquals("jdoe", name.getUsername());
        assertNull(name.getPassword());

        // TBD as I am not sure what other URL formats to use
    }
}
