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

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Transport;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class TransportEvent extends MailEvent {
    /**
     * Indicates that the message has successfully been delivered to all
     * recipients.
     */
    public static final int MESSAGE_DELIVERED = 1;

    /**
     * Indicates that no messages could be delivered.
     */
    public static final int MESSAGE_NOT_DELIVERED = 2;

    /**
     * Indicates that some of the messages were successfully delivered
     * but that some failed.
     */
    public static final int MESSAGE_PARTIALLY_DELIVERED = 3;

    /**
     * The event type.
     */
    protected int type;

    /**
     * Addresses to which the message was successfully delivered.
     */
    protected transient Address[] validSent;

    /**
     * Addresses which are valid but to which the message was not sent.
     */
    protected transient Address[] validUnsent;

    /**
     * Addresses that are invalid.
     */
    protected transient Address[] invalid;

    /**
     * The message associated with this event.
     */
    protected transient Message msg;

    /**
     * Construct a new event,
     *
     * @param transport   the transport attempting to deliver the message
     * @param type        the event type
     * @param validSent   addresses to which the message was successfully delivered
     * @param validUnsent addresses which are valid but to which the message was not sent
     * @param invalid     invalid addresses
     * @param message     the associated message
     */
    public TransportEvent(Transport transport, int type, Address[] validSent, Address[] validUnsent, Address[] invalid, Message message) {
        super(transport);
        this.type = type;
        this.validSent = validSent;
        this.validUnsent = validUnsent;
        this.invalid = invalid;
        this.msg = message;
    }

    public Address[] getValidSentAddresses() {
        return validSent;
    }

    public Address[] getValidUnsentAddresses() {
        return validUnsent;
    }

    public Address[] getInvalidAddresses() {
        return invalid;
    }

    public Message getMessage() {
        return msg;
    }

    public int getType() {
        return type;
    }

    public void dispatch(Object listener) {
        TransportListener l = (TransportListener) listener;
        switch (type) {
        case MESSAGE_DELIVERED:
            l.messageDelivered(this);
            break;
        case MESSAGE_NOT_DELIVERED:
            l.messageNotDelivered(this);
            break;
        case MESSAGE_PARTIALLY_DELIVERED:
            l.messagePartiallyDelivered(this);
            break;
        default:
            throw new IllegalArgumentException("Invalid type " + type);
        }
    }
}
