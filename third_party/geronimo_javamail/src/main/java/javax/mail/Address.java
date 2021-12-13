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

import java.io.Serializable;

/**
 * This abstract class models the addresses in a message.
 * Addresses are Serializable so that they may be serialized along with other search terms.
 *
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public abstract class Address implements Serializable {
    /**
     * Subclasses must provide a suitable implementation of equals().
     *
     * @param object the object to compare t
     * @return true if the subclass determines the other object is equal to this Address
     */
    public abstract boolean equals(Object object);

    /**
     * Return a String that identifies this address type.
     * @return the type of this address
     */
    public abstract String getType();

    /**
     * Subclasses must provide a suitable representation of their address.
     * @return a representation of an Address as a String
     */
    public abstract String toString();
}
