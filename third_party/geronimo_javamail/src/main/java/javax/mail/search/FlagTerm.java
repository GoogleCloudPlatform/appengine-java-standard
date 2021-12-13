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

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Term for matching message {@link Flags}.
 *
 * @version $Rev: 593593 $ $Date: 2007-11-09 11:04:20 -0600 (Fri, 09 Nov 2007) $
 */
public final class FlagTerm extends SearchTerm {
    /**
     * If true, test that all flags are set; if false, test that all flags are clear.
     */
    protected boolean set;
    /**
     * The flags to test.
     */
    protected Flags flags;

    /**
     * @param flags the flags to test
     * @param set test for set or clear; {@link #set}
     */
    public FlagTerm(Flags flags, boolean set) {
        this.set = set;
        this.flags = flags;
    }

    public Flags getFlags() {
        return flags;
    }

    public boolean getTestSet() {
        return set;
    }

    public boolean match(Message message) {
        try {
            Flags msgFlags = message.getFlags();
            if (set) {
                return msgFlags.contains(flags);
            } else {
                // yuk - I wish we could get at the internal state of the Flags
                Flags.Flag[] system = flags.getSystemFlags();
                for (int i = 0; i < system.length; i++) {
                    Flags.Flag flag = system[i];
                    if (msgFlags.contains(flag)) {
                        return false;
                    }
                }
                String[] user = flags.getUserFlags();
                for (int i = 0; i < user.length; i++) {
                    String flag = user[i];
                    if (msgFlags.contains(flag)) {
                        return false;
                    }
                }
                return true;
            }
        } catch (MessagingException e) {
            return false;
        }
    }

    public boolean equals(Object other) {
        if (other == this) return true;
        if (other instanceof FlagTerm == false) return false;
        final FlagTerm otherFlags = (FlagTerm) other;
        return otherFlags.set == this.set && otherFlags.flags.equals(flags);
    }

    public int hashCode() {
        return set ? flags.hashCode() : ~flags.hashCode();
    }
}
