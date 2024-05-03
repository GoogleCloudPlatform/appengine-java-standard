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

package javax.mail.search;

import javax.mail.Address;

/**
 * Term that compares two addresses.
 *
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public abstract class AddressTerm extends SearchTerm {
    /**
     * The address.
     */
    protected Address address;

    /**
     * Constructor taking the address for this term.
     * @param address the address
     */
    protected AddressTerm(Address address) {
        this.address = address;
    }

    /**
     * Return the address of this term.
     *
     * @return the addre4ss
     */
    public Address getAddress() {
        return address;
    }

    /**
     * Match to the supplied address.
     *
     * @param address the address to match with
     * @return true if the addresses match
     */
    protected boolean match(Address address) {
        return this.address.equals(address);
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof AddressTerm == false) return false;

        return address.equals(((AddressTerm) other).address);
    }

    public int hashCode() {
        return address.hashCode();
    }
}
