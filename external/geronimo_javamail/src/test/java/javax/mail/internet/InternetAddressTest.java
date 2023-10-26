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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.Session;

/**
 * @version $Rev: 669901 $ $Date: 2008-06-20 09:01:53 -0500 (Fri, 20 Jun 2008) $
 */
public class InternetAddressTest extends TestCase {
    private InternetAddress address;

    public void testQuotedLiterals() throws Exception {
        parseHeaderTest("\"Foo\t\n\\\\\\\"\" <foo@apache.org>", true, "foo@apache.org", "Foo\t\n\\\"", "\"Foo\t\n\\\\\\\"\" <foo@apache.org>", false);
        parseHeaderTest("<\"@,:;<>.[]()\"@apache.org>", true, "\"@,:;<>.[]()\"@apache.org", null, "<\"@,:;<>.[]()\"@apache.org>", false);
        parseHeaderTest("<\"\\F\\o\\o\"@apache.org>", true, "\"Foo\"@apache.org", null, "<\"Foo\"@apache.org>", false);
        parseHeaderErrorTest("\"Foo <foo@apache.org>", true);
        parseHeaderErrorTest("\"Foo\r\" <foo@apache.org>", true);
    }

    public void testDomainLiterals() throws Exception {
        parseHeaderTest("<foo@[apache].org>", true, "foo@[apache].org", null, "<foo@[apache].org>", false);
        parseHeaderTest("<foo@[@()<>.,:;\"\\\\].org>", true, "foo@[@()<>.,:;\"\\\\].org", null, "<foo@[@()<>.,:;\"\\\\].org>", false);
        parseHeaderTest("<foo@[\\[\\]].org>", true, "foo@[\\[\\]].org", null, "<foo@[\\[\\]].org>", false);
        parseHeaderErrorTest("<foo@[[].org>", true);
        parseHeaderErrorTest("<foo@[foo.org>", true);
        parseHeaderErrorTest("<foo@[\r].org>", true);
    }

    public void testComments() throws Exception {
        parseHeaderTest("Foo Bar (Fred) <foo@apache.org>", true, "foo@apache.org", "Foo Bar (Fred)", "\"Foo Bar (Fred)\" <foo@apache.org>", false);
        parseHeaderTest("(Fred) foo@apache.org", true, "foo@apache.org", "Fred", "Fred <foo@apache.org>", false);
        parseHeaderTest("(\\(Fred\\)) foo@apache.org", true, "foo@apache.org", "(Fred)", "\"(Fred)\" <foo@apache.org>", false);
        parseHeaderTest("(Fred (Jones)) foo@apache.org", true, "foo@apache.org", "Fred (Jones)", "\"Fred (Jones)\" <foo@apache.org>", false);
        parseHeaderErrorTest("(Fred foo@apache.org", true);
        parseHeaderErrorTest("(Fred\r) foo@apache.org", true);
    }

    public void testParseHeader() throws Exception {
        parseHeaderTest("<@apache.org,@apache.net:foo@apache.org>", false, "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        parseHeaderTest("<@apache.org:foo@apache.org>", false, "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        parseHeaderTest("Foo Bar:;", false, "Foo Bar:;", null, "Foo Bar:;", true);
        parseHeaderTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", false, "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        parseHeaderTest("\"Foo Bar\" <foo.bar@apache.org>", false, "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        parseHeaderTest("(Foo) (Bar) foo.bar@apache.org", false, "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        parseHeaderTest("<foo@apache.org>", false, "foo@apache.org", null, "foo@apache.org", false);
        parseHeaderTest("Foo Bar <foo.bar@apache.org>", false, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseHeaderTest("foo", false, "foo", null, "foo", false);
        parseHeaderTest("\"foo\"", false, "\"foo\"", null, "<\"foo\">", false);
        parseHeaderTest("foo@apache.org", false, "foo@apache.org", null, "foo@apache.org", false);
        parseHeaderTest("\"foo\"@apache.org", false, "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        parseHeaderTest("foo@[apache].org", false, "foo@[apache].org", null, "<foo@[apache].org>", false);
        parseHeaderTest("foo@[apache].[org]", false, "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        parseHeaderTest("foo.bar@apache.org", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseHeaderTest("(Foo Bar) <foo.bar@apache.org>", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseHeaderTest("(Foo) (Bar) <foo.bar@apache.org>", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseHeaderTest("\"Foo\" Bar <foo.bar@apache.org>", false, "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        parseHeaderTest("(Foo Bar) foo.bar@apache.org", false, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseHeaderTest("apache.org", false, "apache.org", null, "apache.org", false);
    }

    public void testValidate() throws Exception {
        validateTest("@apache.org,@apache.net:foo@apache.org");
        validateTest("@apache.org:foo@apache.org");
        validateTest("Foo Bar:;");
        validateTest("foo.bar@apache.org");
        validateTest("bar@apache.org");
        validateTest("foo");
        validateTest("foo.bar");
        validateTest("\"foo\"");
        validateTest("\"foo\"@apache.org");
        validateTest("foo@[apache].org");
        validateTest("foo@[apache].[org]");
    }

    public void testStrictParseHeader() throws Exception {
        parseHeaderTest("<@apache.org,@apache.net:foo@apache.org>", true, "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        parseHeaderTest("<@apache.org:foo@apache.org>", true, "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        parseHeaderTest("Foo Bar:;", true, "Foo Bar:;", null, "Foo Bar:;", true);
        parseHeaderTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", true, "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        parseHeaderTest("\"Foo Bar\" <foo.bar@apache.org>", true, "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        parseHeaderTest("(Foo) (Bar) foo.bar@apache.org", true, "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        parseHeaderTest("<foo@apache.org>", true, "foo@apache.org", null, "foo@apache.org", false);
        parseHeaderTest("Foo Bar <foo.bar@apache.org>", true, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseHeaderTest("foo", true, "foo", null, "foo", false);
        parseHeaderTest("\"foo\"", true, "\"foo\"", null, "<\"foo\">", false);
        parseHeaderTest("foo@apache.org", true, "foo@apache.org", null, "foo@apache.org", false);
        parseHeaderTest("\"foo\"@apache.org", true, "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        parseHeaderTest("foo@[apache].org", true, "foo@[apache].org", null, "<foo@[apache].org>", false);
        parseHeaderTest("foo@[apache].[org]", true, "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        parseHeaderTest("foo.bar@apache.org", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseHeaderTest("(Foo Bar) <foo.bar@apache.org>", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseHeaderTest("(Foo) (Bar) <foo.bar@apache.org>", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseHeaderTest("\"Foo\" Bar <foo.bar@apache.org>", true, "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        parseHeaderTest("(Foo Bar) foo.bar@apache.org", true, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseHeaderTest("apache.org", true, "apache.org", null, "apache.org", false);
    }

    public void testParse() throws Exception {
        parseTest("<@apache.org,@apache.net:foo@apache.org>", false, "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        parseTest("<@apache.org:foo@apache.org>", false, "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        parseTest("Foo Bar:;", false, "Foo Bar:;", null, "Foo Bar:;", true);
        parseTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", false, "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        parseTest("\"Foo Bar\" <foo.bar@apache.org>", false, "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        parseTest("(Foo) (Bar) foo.bar@apache.org", false, "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        parseTest("<foo@apache.org>", false, "foo@apache.org", null, "foo@apache.org", false);
        parseTest("Foo Bar <foo.bar@apache.org>", false, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseTest("foo", false, "foo", null, "foo", false);
        parseTest("\"foo\"", false, "\"foo\"", null, "<\"foo\">", false);
        parseTest("foo@apache.org", false, "foo@apache.org", null, "foo@apache.org", false);
        parseTest("\"foo\"@apache.org", false, "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        parseTest("foo@[apache].org", false, "foo@[apache].org", null, "<foo@[apache].org>", false);
        parseTest("foo@[apache].[org]", false, "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        parseTest("foo.bar@apache.org", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseTest("(Foo Bar) <foo.bar@apache.org>", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseTest("(Foo) (Bar) <foo.bar@apache.org>", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseTest("\"Foo\" Bar <foo.bar@apache.org>", false, "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        parseTest("(Foo Bar) foo.bar@apache.org", false, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseTest("apache.org", false, "apache.org", null, "apache.org", false);
    }

    public void testDefaultParse() throws Exception {
        parseDefaultTest("<@apache.org,@apache.net:foo@apache.org>", "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        parseDefaultTest("<@apache.org:foo@apache.org>", "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        parseDefaultTest("Foo Bar:;", "Foo Bar:;", null, "Foo Bar:;", true);
        parseDefaultTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        parseDefaultTest("\"Foo Bar\" <foo.bar@apache.org>", "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        parseDefaultTest("(Foo) (Bar) foo.bar@apache.org", "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        parseDefaultTest("<foo@apache.org>", "foo@apache.org", null, "foo@apache.org", false);
        parseDefaultTest("Foo Bar <foo.bar@apache.org>", "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseDefaultTest("foo", "foo", null, "foo", false);
        parseDefaultTest("\"foo\"", "\"foo\"", null, "<\"foo\">", false);
        parseDefaultTest("foo@apache.org", "foo@apache.org", null, "foo@apache.org", false);
        parseDefaultTest("\"foo\"@apache.org", "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        parseDefaultTest("foo@[apache].org", "foo@[apache].org", null, "<foo@[apache].org>", false);
        parseDefaultTest("foo@[apache].[org]", "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        parseDefaultTest("foo.bar@apache.org", "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseDefaultTest("(Foo Bar) <foo.bar@apache.org>", "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseDefaultTest("(Foo) (Bar) <foo.bar@apache.org>", "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseDefaultTest("\"Foo\" Bar <foo.bar@apache.org>", "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        parseDefaultTest("(Foo Bar) foo.bar@apache.org", "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseDefaultTest("apache.org", "apache.org", null, "apache.org", false);
    }

    public void testStrictParse() throws Exception {
        parseTest("<@apache.org,@apache.net:foo@apache.org>", true, "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        parseTest("<@apache.org:foo@apache.org>", true, "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        parseTest("Foo Bar:;", true, "Foo Bar:;", null, "Foo Bar:;", true);
        parseTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", true, "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        parseTest("\"Foo Bar\" <foo.bar@apache.org>", true, "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        parseTest("(Foo) (Bar) foo.bar@apache.org", true, "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        parseTest("<foo@apache.org>", true, "foo@apache.org", null, "foo@apache.org", false);
        parseTest("Foo Bar <foo.bar@apache.org>", true, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseTest("foo", true, "foo", null, "foo", false);
        parseTest("\"foo\"", true, "\"foo\"", null, "<\"foo\">", false);
        parseTest("foo@apache.org", true, "foo@apache.org", null, "foo@apache.org", false);
        parseTest("\"foo\"@apache.org", true, "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        parseTest("foo@[apache].org", true, "foo@[apache].org", null, "<foo@[apache].org>", false);
        parseTest("foo@[apache].[org]", true, "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        parseTest("foo.bar@apache.org", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseTest("(Foo Bar) <foo.bar@apache.org>", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseTest("(Foo) (Bar) <foo.bar@apache.org>", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        parseTest("\"Foo\" Bar <foo.bar@apache.org>", true, "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        parseTest("(Foo Bar) foo.bar@apache.org", true, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        parseTest("apache.org", true, "apache.org", null, "apache.org", false);
    }

    public void testConstructor() throws Exception {
        constructorTest("(Foo) (Bar) foo.bar@apache.org", false, "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        constructorTest("<@apache.org,@apache.net:foo@apache.org>", false, "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        constructorTest("<@apache.org:foo@apache.org>", false, "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        constructorTest("Foo Bar:;", false, "Foo Bar:;", null, "Foo Bar:;", true);
        constructorTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", false, "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        constructorTest("\"Foo Bar\" <foo.bar@apache.org>", false, "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        constructorTest("<foo@apache.org>", false, "foo@apache.org", null, "foo@apache.org", false);
        constructorTest("Foo Bar <foo.bar@apache.org>", false, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        constructorTest("foo", false, "foo", null, "foo", false);
        constructorTest("\"foo\"", false, "\"foo\"", null, "<\"foo\">", false);
        constructorTest("foo@apache.org", false, "foo@apache.org", null, "foo@apache.org", false);
        constructorTest("\"foo\"@apache.org", false, "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        constructorTest("foo@[apache].org", false, "foo@[apache].org", null, "<foo@[apache].org>", false);
        constructorTest("foo@[apache].[org]", false, "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        constructorTest("foo.bar@apache.org", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorTest("(Foo Bar) <foo.bar@apache.org>", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorTest("(Foo) (Bar) <foo.bar@apache.org>", false, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorTest("\"Foo\" Bar <foo.bar@apache.org>", false, "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        constructorTest("(Foo Bar) foo.bar@apache.org", false, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        constructorTest("apache.org", false, "apache.org", null, "apache.org", false);
    }

    public void testDefaultConstructor() throws Exception {
        constructorDefaultTest("<@apache.org,@apache.net:foo@apache.org>", "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        constructorDefaultTest("<@apache.org:foo@apache.org>", "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        constructorDefaultTest("Foo Bar:;", "Foo Bar:;", null, "Foo Bar:;", true);
        constructorDefaultTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        constructorDefaultTest("\"Foo Bar\" <foo.bar@apache.org>", "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        constructorDefaultTest("(Foo) (Bar) foo.bar@apache.org", "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        constructorDefaultTest("<foo@apache.org>", "foo@apache.org", null, "foo@apache.org", false);
        constructorDefaultTest("Foo Bar <foo.bar@apache.org>", "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        constructorDefaultTest("foo", "foo", null, "foo", false);
        constructorDefaultTest("\"foo\"", "\"foo\"", null, "<\"foo\">", false);
        constructorDefaultTest("foo@apache.org", "foo@apache.org", null, "foo@apache.org", false);
        constructorDefaultTest("\"foo\"@apache.org", "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        constructorDefaultTest("foo@[apache].org", "foo@[apache].org", null, "<foo@[apache].org>", false);
        constructorDefaultTest("foo@[apache].[org]", "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        constructorDefaultTest("foo.bar@apache.org", "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorDefaultTest("(Foo Bar) <foo.bar@apache.org>", "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorDefaultTest("(Foo) (Bar) <foo.bar@apache.org>", "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorDefaultTest("\"Foo\" Bar <foo.bar@apache.org>", "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        constructorDefaultTest("(Foo Bar) foo.bar@apache.org", "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        constructorDefaultTest("apache.org", "apache.org", null, "apache.org", false);
    }

    public void testStrictConstructor() throws Exception {
        constructorTest("<@apache.org,@apache.net:foo@apache.org>", true, "@apache.org,@apache.net:foo@apache.org", null, "<@apache.org,@apache.net:foo@apache.org>", false);
        constructorTest("<@apache.org:foo@apache.org>", true, "@apache.org:foo@apache.org", null, "<@apache.org:foo@apache.org>", false);
        constructorTest("Foo Bar:;", true, "Foo Bar:;", null, "Foo Bar:;", true);
        constructorTest("\"\\\"Foo Bar\" <foo.bar@apache.org>", true, "foo.bar@apache.org", "\"Foo Bar", "\"\\\"Foo Bar\" <foo.bar@apache.org>", false);
        constructorTest("\"Foo Bar\" <foo.bar@apache.org>", true, "foo.bar@apache.org", "Foo Bar",  "Foo Bar <foo.bar@apache.org>", false);
        constructorTest("(Foo) (Bar) foo.bar@apache.org", true, "foo.bar@apache.org", "Foo", "Foo <foo.bar@apache.org>", false);
        constructorTest("<foo@apache.org>", true, "foo@apache.org", null, "foo@apache.org", false);
        constructorTest("Foo Bar <foo.bar@apache.org>", true, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        constructorTest("foo", true, "foo", null, "foo", false);
        constructorTest("\"foo\"", true, "\"foo\"", null, "<\"foo\">", false);
        constructorTest("foo@apache.org", true, "foo@apache.org", null, "foo@apache.org", false);
        constructorTest("\"foo\"@apache.org", true, "\"foo\"@apache.org", null, "<\"foo\"@apache.org>", false);
        constructorTest("foo@[apache].org", true, "foo@[apache].org", null, "<foo@[apache].org>", false);
        constructorTest("foo@[apache].[org]", true, "foo@[apache].[org]", null, "<foo@[apache].[org]>", false);
        constructorTest("foo.bar@apache.org", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorTest("(Foo Bar) <foo.bar@apache.org>", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorTest("(Foo) (Bar) <foo.bar@apache.org>", true, "foo.bar@apache.org", null, "foo.bar@apache.org", false);
        constructorTest("\"Foo\" Bar <foo.bar@apache.org>", true, "foo.bar@apache.org", "\"Foo\" Bar", "\"\\\"Foo\\\" Bar\" <foo.bar@apache.org>", false);
        constructorTest("(Foo Bar) foo.bar@apache.org", true, "foo.bar@apache.org", "Foo Bar", "Foo Bar <foo.bar@apache.org>", false);
        constructorTest("apache.org", true, "apache.org", null, "apache.org", false);
    }

    public void testParseHeaderList() throws Exception {

        InternetAddress[] addresses = InternetAddress.parseHeader("foo@apache.org,bar@apache.org", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = InternetAddress.parseHeader("Foo <foo@apache.org>,,Bar <bar@apache.org>", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", "Foo", "Foo <foo@apache.org>", false);
        validateAddress(addresses[1], "bar@apache.org", "Bar", "Bar <bar@apache.org>", false);

        addresses = InternetAddress.parseHeader("foo@apache.org, bar@apache.org", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = InternetAddress.parseHeader("Foo <foo@apache.org>, Bar <bar@apache.org>", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", "Foo", "Foo <foo@apache.org>", false);
        validateAddress(addresses[1], "bar@apache.org", "Bar", "Bar <bar@apache.org>", false);


        addresses = InternetAddress.parseHeader("Foo <foo@apache.org>,(yada),Bar <bar@apache.org>", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", "Foo", "Foo <foo@apache.org>", false);
        validateAddress(addresses[1], "bar@apache.org", "Bar", "Bar <bar@apache.org>", false);
    }

    public void testParseHeaderErrors() throws Exception {
        parseHeaderErrorTest("foo@apache.org bar@apache.org", true);
        parseHeaderErrorTest("Foo foo@apache.org", true);
        parseHeaderErrorTest("Foo foo@apache.org", true);
        parseHeaderErrorTest("Foo <foo@apache.org", true);
        parseHeaderErrorTest("[foo]@apache.org", true);
        parseHeaderErrorTest("@apache.org", true);
        parseHeaderErrorTest("foo@[apache.org", true);
    }

    public void testValidateErrors() throws Exception {
        validateErrorTest("foo@apache.org bar@apache.org");
        validateErrorTest("Foo foo@apache.org");
        validateErrorTest("Foo foo@apache.org");
        validateErrorTest("Foo <foo@apache.org");
        validateErrorTest("[foo]@apache.org");
        validateErrorTest("@apache.org");
        validateErrorTest("foo@[apache.org");
    }

    public void testGroup() throws Exception {
        parseHeaderTest("Foo:foo@apache.org;", true, "Foo:foo@apache.org;", null, "Foo:foo@apache.org;", true);
        parseHeaderTest("Foo:foo@apache.org,bar@apache.org;", true, "Foo:foo@apache.org,bar@apache.org;", null, "Foo:foo@apache.org,bar@apache.org;", true);
        parseHeaderTest("Foo Bar:<foo@apache.org>,bar@apache.org;", true, "Foo Bar:<foo@apache.org>,bar@apache.org;", null, "Foo Bar:<foo@apache.org>,bar@apache.org;", true);
        parseHeaderTest("Foo Bar:Foo <foo@apache.org>,bar@apache.org;", true, "Foo Bar:Foo<foo@apache.org>,bar@apache.org;", null, "Foo Bar:Foo<foo@apache.org>,bar@apache.org;", true);
        parseHeaderTest("Foo:<foo@apache.org>,,bar@apache.org;", true, "Foo:<foo@apache.org>,,bar@apache.org;", null, "Foo:<foo@apache.org>,,bar@apache.org;", true);
        parseHeaderTest("Foo:foo,bar;", true, "Foo:foo,bar;", null, "Foo:foo,bar;", true);
        parseHeaderTest("Foo:;", true, "Foo:;", null, "Foo:;", true);
        parseHeaderTest("\"Foo\":foo@apache.org;", true, "\"Foo\":foo@apache.org;", null, "\"Foo\":foo@apache.org;", true);

        parseHeaderErrorTest("Foo:foo@apache.org,bar@apache.org", true);
        parseHeaderErrorTest("Foo:foo@apache.org,Bar:bar@apache.org;;", true);
        parseHeaderErrorTest(":foo@apache.org;", true);
        parseHeaderErrorTest("Foo Bar:<foo@apache.org,bar@apache.org;", true);
    }

    public void testGetGroup() throws Exception {
        InternetAddress[] addresses = getGroup("Foo:foo@apache.org;", true);
        assertTrue("Expecting 1 address", addresses.length == 1);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);

        addresses = getGroup("Foo:foo@apache.org,bar@apache.org;", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:<foo@apache.org>,bar@apache.org;", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:<foo@apache.org>,,bar@apache.org;", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:Foo <foo@apache.org>,bar@apache.org;", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", "Foo", "Foo <foo@apache.org>", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:Foo <@apache.org:foo@apache.org>,bar@apache.org;", true);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "@apache.org:foo@apache.org", "Foo", "Foo <@apache.org:foo@apache.org>", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);


        addresses = getGroup("Foo:;", true);
        assertTrue("Expecting 0 addresses", addresses.length == 0);

        addresses = getGroup("Foo:foo@apache.org;", false);
        assertTrue("Expecting 1 address", addresses.length == 1);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);

        addresses = getGroup("Foo:foo@apache.org,bar@apache.org;", false);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:<foo@apache.org>,bar@apache.org;", false);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:<foo@apache.org>,,bar@apache.org;", false);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", null, "foo@apache.org", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:Foo <foo@apache.org>,bar@apache.org;", false);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "foo@apache.org", "Foo", "Foo <foo@apache.org>", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);

        addresses = getGroup("Foo:Foo <@apache.org:foo@apache.org>,bar@apache.org;", false);
        assertTrue("Expecting 2 addresses", addresses.length == 2);
        validateAddress(addresses[0], "@apache.org:foo@apache.org", "Foo", "Foo <@apache.org:foo@apache.org>", false);
        validateAddress(addresses[1], "bar@apache.org", null, "bar@apache.org", false);


        addresses = getGroup("Foo:;", false);
        assertTrue("Expecting 0 addresses", addresses.length == 0);
    }


    public void testLocalAddress() throws Exception {
        System.getProperties().remove("user.name");

        assertNull(InternetAddress.getLocalAddress(null));
        System.setProperty("user.name", "dev");

        InternetAddress localHost = null;
        String user = null;
        String host = "localhost";
        try {
            user = System.getProperty("user.name");
            localHost = new InternetAddress(user + "@" + host);
        } catch (SecurityException e) {
            // ignore
        }

        assertEquals(InternetAddress.getLocalAddress(null), localHost);

        Properties props = new Properties();
        Session session = Session.getInstance(props, null);

        assertEquals(InternetAddress.getLocalAddress(session), localHost);

        props.put("mail.host", "apache.org");
        session = Session.getInstance(props, null);

        assertEquals(InternetAddress.getLocalAddress(session), new InternetAddress(user + "@apache.org"));

        props.put("mail.user", "user");
        props.remove("mail.host");

        session = Session.getInstance(props, null);
        assertEquals(InternetAddress.getLocalAddress(session), new InternetAddress("user@" + host));

        props.put("mail.host", "apache.org");
        session = Session.getInstance(props, null);

        assertEquals(InternetAddress.getLocalAddress(session), new InternetAddress("user@apache.org"));

        props.put("mail.from", "tester@incubator.apache.org");
        session = Session.getInstance(props, null);

        assertEquals(InternetAddress.getLocalAddress(session), new InternetAddress("tester@incubator.apache.org"));
    }

    private InternetAddress[] getGroup(String address, boolean strict) throws AddressException
    {
        InternetAddress group = new InternetAddress(address);
        return group.getGroup(strict);
    }


    protected void setUp() throws Exception {
        address = new InternetAddress();
    }

    private void parseHeaderTest(String address, boolean strict, String resultAddr, String personal, String toString, boolean group) throws Exception
    {
        InternetAddress[] addresses = InternetAddress.parseHeader(address, strict);
        assertTrue(addresses.length == 1);
        validateAddress(addresses[0], resultAddr, personal, toString, group);
    }

    private void parseHeaderErrorTest(String address, boolean strict) throws Exception
    {
        try {
            InternetAddress.parseHeader(address, strict);
            fail("Expected AddressException");
        } catch (AddressException e) {
        }
    }

    private void constructorTest(String address, boolean strict, String resultAddr, String personal, String toString, boolean group) throws Exception
    {
        validateAddress(new InternetAddress(address, strict), resultAddr, personal, toString, group);
    }

    private void constructorDefaultTest(String address, String resultAddr, String personal, String toString, boolean group) throws Exception
    {
        validateAddress(new InternetAddress(address), resultAddr, personal, toString, group);
    }

    private void constructorErrorTest(String address, boolean strict) throws Exception
    {
        try {
            InternetAddress foo = new InternetAddress(address, strict);
            fail("Expected AddressException");
        } catch (AddressException e) {
        }
    }

    private void parseTest(String address, boolean strict, String resultAddr, String personal, String toString, boolean group) throws Exception
    {
        InternetAddress[] addresses = InternetAddress.parse(address, strict);
        assertTrue(addresses.length == 1);
        validateAddress(addresses[0], resultAddr, personal, toString, group);
    }

    private void parseErrorTest(String address, boolean strict) throws Exception
    {
        try {
            InternetAddress.parse(address, strict);
            fail("Expected AddressException");
        } catch (AddressException e) {
        }
    }

    private void parseDefaultTest(String address, String resultAddr, String personal, String toString, boolean group) throws Exception
    {
        InternetAddress[] addresses = InternetAddress.parse(address);
        assertTrue(addresses.length == 1);
        validateAddress(addresses[0], resultAddr, personal, toString, group);
    }

    private void parseDefaultErrorTest(String address) throws Exception
    {
        try {
            InternetAddress.parse(address);
            fail("Expected AddressException");
        } catch (AddressException e) {
        }
    }

    private void validateTest(String address) throws Exception {
        InternetAddress test = new InternetAddress();
        test.setAddress(address);
        test.validate();
    }

    private void validateErrorTest(String address) throws Exception {
        InternetAddress test = new InternetAddress();
        test.setAddress(address);
        try {
            test.validate();
            fail("Expected AddressException");
        } catch (AddressException e) {
        }
    }


    private void validateAddress(InternetAddress a, String address, String personal, String toString, boolean group)
    {
        assertEquals("Invalid address:", a.getAddress(), address);
        if (personal == null) {
            assertNull("Personal must be null", a.getPersonal());
        }
        else {
            assertEquals("Invalid Personal:", a.getPersonal(), personal);
        }
        assertEquals("Invalid string value:", a.toString(), toString);
        assertTrue("Incorrect group value:", group == a.isGroup());
    }
}
