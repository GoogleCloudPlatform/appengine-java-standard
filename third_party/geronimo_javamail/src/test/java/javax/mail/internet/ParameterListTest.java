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
public class ParameterListTest extends TestCase {
    public ParameterListTest(String arg0) {
        super(arg0);
    }
    public void testParameters() throws ParseException {
        ParameterList list =
            new ParameterList(";thing=value;thong=vulue;thung=git");
        assertEquals("value", list.get("thing"));
        assertEquals("vulue", list.get("thong"));
        assertEquals("git", list.get("thung"));
    }

    public void testQuotedParameter() throws ParseException {
        ParameterList list = new ParameterList(";foo=one;bar=\"two\"");
        assertEquals("one", list.get("foo"));
        assertEquals("two", list.get("bar"));
    }

    public void testEncodeDecode() throws Exception {

        System.setProperty("mail.mime.encodeparameters", "true");
        System.setProperty("mail.mime.decodeparameters", "true");

        String value = " '*% abc \u0081\u0082\r\n\t";
        String encodedTest = "; one*=UTF-8''%20%27%2A%25%20abc%20%C2%81%C2%82%0D%0A%09";

        ParameterList list = new ParameterList();
        list.set("one", value, "UTF-8");

        assertEquals(value, list.get("one"));

        String encoded = list.toString();

        assertEquals(encoded, encodedTest);

        ParameterList list2 = new ParameterList(encoded);
        assertEquals(value, list.get("one"));
        assertEquals(list2.toString(), encodedTest);
    }

}
