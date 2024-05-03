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

import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class NewsAddressTest extends TestCase {
    public void testNewsAddress() throws AddressException {
        NewsAddress na = new NewsAddress("geronimo-dev", "news.apache.org");
        assertEquals("geronimo-dev", na.getNewsgroup());
        assertEquals("news.apache.org", na.getHost());
        assertEquals("news", na.getType());
        assertEquals("geronimo-dev", na.toString());
        NewsAddress[] nas =
            NewsAddress.parse(
                "geronimo-dev@news.apache.org, geronimo-user@news.apache.org");
        assertEquals(2, nas.length);
        assertEquals("geronimo-dev", nas[0].getNewsgroup());
        assertEquals("news.apache.org", nas[0].getHost());
        assertEquals("geronimo-user", nas[1].getNewsgroup());
        assertEquals("news.apache.org", nas[1].getHost());
    }
}
