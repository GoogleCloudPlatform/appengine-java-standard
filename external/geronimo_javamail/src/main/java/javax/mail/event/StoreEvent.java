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

/**
 * Event representing motifications from the Store connection.
 *
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class StoreEvent extends MailEvent {
    /**
     * Indicates that this message is an alert.
     */
    public static final int ALERT = 1;

    /**
     * Indicates that this message is a notice.
     */
    public static final int NOTICE = 2;

    /**
     * The message type.
     */
    protected int type;

    /**
     * The text to be presented to the user.
     */
    protected String message;

    /**
     * Construct a new event.
     *
     * @param store   the Store that initiated the notification
     * @param type    the message type
     * @param message the text to be presented to the user
     */
    public StoreEvent(Store store, int type, String message) {
        super(store);
        this.type = type;
        this.message = message;
    }

    /**
     * Return the message type.
     *
     * @return the message type
     */
    public int getMessageType() {
        return type;
    }

    /**
     * Return the text to be displayed to the user.
     *
     * @return the text to be displayed to the user
     */
    public String getMessage() {
        return message;
    }

    public void dispatch(Object listener) {
        ((StoreListener) listener).notification(this);
    }
}
