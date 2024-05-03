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

import javax.mail.event.AllEventTests;
import javax.mail.internet.AllInternetTests;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @version $Revision $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class AllTests {
    public static Test suite() {
        TestSuite suite = new TestSuite("Test for javax.mail");
        //$JUnit-BEGIN$
        suite.addTest(new TestSuite(FlagsTest.class));
        suite.addTest(new TestSuite(HeaderTest.class));
        suite.addTest(new TestSuite(MessagingExceptionTest.class));
        suite.addTest(new TestSuite(URLNameTest.class));
        suite.addTest(new TestSuite(PasswordAuthenticationTest.class));
        suite.addTest(new TestSuite(SessionTest.class));
        suite.addTest(AllEventTests.suite());
        suite.addTest(AllInternetTests.suite());
        //$JUnit-END$
        return suite;
    }
}
