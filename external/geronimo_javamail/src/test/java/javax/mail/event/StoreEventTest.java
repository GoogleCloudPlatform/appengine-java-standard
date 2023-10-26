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

import javax.mail.Store;
import javax.mail.TestData;
import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class StoreEventTest extends TestCase {
    public StoreEventTest(String name) {
        super(name);
    }
    public void testEvent() {
        doEventTests(StoreEvent.ALERT);
        doEventTests(StoreEvent.NOTICE);
        try {
            StoreEvent event = new StoreEvent(null, -12345, "Hello World");
            fail(
                "Expected exception due to invalid type "
                    + event.getMessageType());
        } catch (IllegalArgumentException e) {
        }
    }
    private void doEventTests(int type) {
        Store source = TestData.getTestStore();
        StoreEvent event = new StoreEvent(source, type, "Hello World");
        assertEquals(source, event.getSource());
        assertEquals("Hello World", event.getMessage());
        assertEquals(type, event.getMessageType());
        StoreListenerTest listener = new StoreListenerTest();
        event.dispatch(listener);
        assertEquals("Unexpcted method dispatched", type, listener.getState());
    }
    public static class StoreListenerTest implements StoreListener {
        private int state = 0;
        public void notification(StoreEvent event) {
            if (state != 0) {
                fail("Recycled Listener");
            }
            state = event.getMessageType();
        }
        public int getState() {
            return state;
        }
    }
}
