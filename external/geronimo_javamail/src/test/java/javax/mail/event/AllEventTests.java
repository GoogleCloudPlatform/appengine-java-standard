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

package javax.mail.event;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class AllEventTests {
    public static Test suite() {
        TestSuite suite = new TestSuite("Test for javax.mail.event");
        //$JUnit-BEGIN$
        suite.addTest(new TestSuite(ConnectionEventTest.class));
        suite.addTest(new TestSuite(FolderEventTest.class));
        suite.addTest(new TestSuite(MessageChangedEventTest.class));
        suite.addTest(new TestSuite(StoreEventTest.class));
        suite.addTest(new TestSuite(MessageCountEventTest.class));
        suite.addTest(new TestSuite(TransportEventTest.class));
        //$JUnit-END$
        return suite;
    }
}
