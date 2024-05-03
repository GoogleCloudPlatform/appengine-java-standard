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

import java.util.Vector;

import javax.mail.MessagingException;
import javax.mail.event.FolderEvent;          
import javax.mail.event.FolderListener;       

import junit.framework.TestCase;

/**
 * @version $Rev: 582780 $ $Date: 2007-10-08 06:17:15 -0500 (Mon, 08 Oct 2007) $
 */
public class EventQueueTest extends TestCase {
    protected EventQueue queue; 
    
    public void setUp() throws Exception {
        queue = new EventQueue();
    }
    
    public void tearDown() throws Exception {
        queue.stop(); 
    }
    
    public void testEvent() {
        doEventTests(FolderEvent.CREATED);
        doEventTests(FolderEvent.RENAMED);
        doEventTests(FolderEvent.DELETED);
    }
    
  @SuppressWarnings({"rawtypes", "unchecked"})
    private void doEventTests(int type) {
        
        // These tests are essentially the same as the 
        // folder event tests, but done using the asynchronous 
        // event queue.  
        FolderEvent event = new FolderEvent(this, null, type);
        assertEquals(this, event.getSource());
        assertEquals(type, event.getType());
        FolderListenerTest listener = new FolderListenerTest();
        Vector listeners = new Vector(); 
        listeners.add(listener); 
        queue.queueEvent(event, listeners);
        // we need to make sure the queue thread has a chance to dispatch 
        // this before we check. 
        try {
            Thread.currentThread().sleep(1000); 
        } catch (InterruptedException e ) {
        }
        assertEquals("Unexpcted method dispatched", type, listener.getState());
    }
    
    public static class FolderListenerTest implements FolderListener {
        private int state = 0;
        public void folderCreated(FolderEvent event) {
            if (state != 0) {
                fail("Recycled Listener");
            }
            state = FolderEvent.CREATED;
        }
        public void folderDeleted(FolderEvent event) {
            if (state != 0) {
                fail("Recycled Listener");
            }
            state = FolderEvent.DELETED;
        }
        public void folderRenamed(FolderEvent event) {
            if (state != 0) {
                fail("Recycled Listener");
            }
            state = FolderEvent.RENAMED;
        }
        public int getState() {
            return state;
        }
    }
}

