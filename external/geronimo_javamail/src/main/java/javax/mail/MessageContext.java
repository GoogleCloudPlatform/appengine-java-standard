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

/**
 * The context in which a piece of message content is contained.
 *
 * @version $Rev: 578802 $ $Date: 2007-09-24 08:16:44 -0500 (Mon, 24 Sep 2007) $
 */
public class MessageContext {
    private final Part part;

    /**
     * Create a MessageContext object describing the context of the supplied Part.
     *
     * @param part the containing part
     */
    public MessageContext(Part part) {
        this.part = part;
    }

    /**
     * Return the {@link Part} that contains the content.
     *
     * @return the part
     */
    public Part getPart() {
        return part;
    }

    /**
     * Return the message that contains the content; if the Part is a {@link Multipart}
     * then recurse up the chain until a {@link Message} is found.
     *
     * @return
     */
    public Message getMessage() {
        return getMessageFrom(part);
    }

    /**
     * Return the session associated with the Message containing this Part.
     *
     * @return the session associated with this context's root message
     */
    public Session getSession() {
        Message message = getMessage();
        if (message == null) {
            return null;
        } else {
            return message.session;
        }
    }

    /**
     * recurse up the chain of MultiPart/BodyPart parts until we hit a message
     * 
     * @param p      The starting part.
     * 
     * @return The encountered Message or null if no Message parts
     *         are found.
     */
    private Message getMessageFrom(Part p) {
        while (p != null) {
            if (p instanceof Message) {
                return (Message) p;
            }
            Multipart mp = ((BodyPart) p).getParent();
            if (mp == null) {
                return null;
            }
            p = mp.getParent();
        }
        return null;
    }
}
