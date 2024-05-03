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

import java.util.Arrays;
import javax.mail.Message;

/**
 * Term that implements a logical AND across terms.
 *
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public final class AndTerm extends SearchTerm {
    /**
     * Terms to which the AND operator should be applied.
     */
    protected SearchTerm[] terms;

    /**
     * Constructor for performing a binary AND.
     *
     * @param a the first term
     * @param b the second ter,
     */
    public AndTerm(SearchTerm a, SearchTerm b) {
        terms = new SearchTerm[]{a, b};
    }

    /**
     * Constructor for performing and AND across an arbitraty number of terms.
     * @param terms the terms to AND together
     */
    public AndTerm(SearchTerm[] terms) {
        this.terms = terms;
    }

    /**
     * Return the terms.
     * @return the terms
     */
    public SearchTerm[] getTerms() {
        return terms;
    }

    /**
     * Match by applying the terms, in order, to the Message and performing an AND operation
     * to the result. Comparision will stop immediately if one of the terms returns false.
     *
     * @param message the Message to apply the terms to
     * @return true if all terms match
     */
    public boolean match(Message message) {
        for (int i = 0; i < terms.length; i++) {
            SearchTerm term = terms[i];
            if (!term.match(message)) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(Object other) {
        if (other == this) return true;
        if (other instanceof AndTerm == false) return false;
        return Arrays.equals(terms, ((AndTerm) other).terms);
    }

    public int hashCode() {
        int hash = 0;
        for (int i = 0; i < terms.length; i++) {
            hash = hash * 37 + terms[i].hashCode();
        }
        return hash;
    }
}
