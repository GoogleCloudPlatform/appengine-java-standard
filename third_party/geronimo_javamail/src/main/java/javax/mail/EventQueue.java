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

//
// This source code implements specifications defined by the Java
// Community Process. In order to remain compliant with the specification
// DO NOT add / change / or delete method signatures!
//
package javax.mail;

import java.util.LinkedList;
import java.util.List;

import javax.mail.event.MailEvent;

/**
 * This is an event queue to dispatch javamail events on separate threads
 * from the main thread.  EventQueues are created by javamail Services 
 * (Transport and Store instances), as well as Folders created from Store 
 * instances.  Each entity will have its own private EventQueue instance, but 
 * will delay creating it until it has an event to dispatch to a real listener.
 * 
 * NOTE:  It would be nice to use the concurrency support in Java 5 to 
 * manage the queue, but this code needs to run on Java 1.4 still.  We also 
 * don't want to have dependencies on other packages with this, so no 
 * outside concurrency packages can be used either. 
 * @version $Rev: 582842 $ $Date: 2007-10-08 10:13:51 -0500 (Mon, 08 Oct 2007) $
 */
class EventQueue implements Runnable {
    /**
     * The dispatch thread that handles notification events. 
     */
    protected Thread dispatchThread; 
    
    /**
     * The dispatching queue for events. 
     */
    protected List eventQueue = new LinkedList(); 
    
    /**
     * Create a new EventQueue, including starting the new thread. 
     */
    public EventQueue() {
        dispatchThread = new Thread(this, "JavaMail-EventQueue"); 
        dispatchThread.setDaemon(true);  // this is a background server thread. 
        // start the thread up 
        dispatchThread.start(); 
    }
    
    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see     java.lang.Thread#run()
     */
    public void run() {
        try {
            while (true) {
                // get the next event 
                PendingEvent p = dequeueEvent(); 
                // an empty event on the queue means time to shut things down. 
                if (p.event == null) {
                    return; 
                }
                
                // and tap the listeners on the shoulder. 
                dispatchEvent(p.event, p.listeners); 
            }
        } catch (InterruptedException e) {
            // been told to stop, so we stop 
        }
    }
    
    
   /**
    * Stop the EventQueue.  This will terminate the dispatcher thread as soon 
    * as it can, so there may be undispatched events in the queue that will 
    * not get dispatched. 
    */
    public synchronized void stop() {
        // if the thread has not been stopped yet, interrupt it 
        // and clear the reference. 
        if (dispatchThread != null) {
            // push a dummy marker on to the event queue  
            // to force the dispatch thread to wake up. 
            queueEvent(null, null); 
            dispatchThread = null; 
        }
    }
    
    /**
     * Add a new event to the queue.  
     * 
     * @param event     The event to dispatch.
     * @param listeners The List of listeners to dispatch this to.  This is assumed to be a
     *                  static snapshot of the listeners that will not change between the time
     *                  the event is queued and the dispatcher thread makes the calls to the
     *                  handlers.
     */
    public synchronized void queueEvent(MailEvent event, List listeners) {
        // add an element to the list, then notify the processing thread. 
        // Note that we make a copy of the listeners list.  This ensures 
        // we're going to dispatch this to the snapshot of the listeners 
        PendingEvent p = new PendingEvent(event, listeners);
        eventQueue.add(p);         
        // wake up the dispatch thread 
        notify(); 
    }
    
    /**
     * Remove the next event from the message queue. 
     * 
     * @return The PendingEvent item from the queue. 
     */
    protected synchronized PendingEvent dequeueEvent() throws InterruptedException {
        // a little spin loop to wait for an event 
        while (eventQueue.isEmpty()) {
            wait(); 
        }
        
        // just remove the first element of this 
        return (PendingEvent)eventQueue.remove(0); 
    }
    
    
    /**
     * Dispatch an event to a list of listeners.  Any exceptions thrown by 
     * the listeners will be swallowed.
     * 
     * @param event     The event to dispatch.
     * @param listeners The list of listeners this gets dispatched to.
     */
    protected void dispatchEvent(MailEvent event, List listeners) {
        // iterate through the listeners list calling the handlers. 
        for (int i = 0; i < listeners.size(); i++) {
            try {
                event.dispatch(listeners.get(i)); 
            } catch (Throwable e) {
                // just eat these 
            }
        }
    }
        
        
    /**
     * Small helper class to give a single reference handle for a pending event. 
     */
    class PendingEvent {
        // the event we're broadcasting  
        MailEvent event;  
        // the list of listeners we send this to. 
        List listeners; 
            
        PendingEvent(MailEvent event, List listeners) {    
            this.event = event; 
            this.listeners = listeners; 
        }
    }
}
