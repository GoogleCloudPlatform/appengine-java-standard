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

/**
 * A Term that provides matching criteria for Strings.
 *
 * @version $Rev: 593593 $ $Date: 2007-11-09 11:04:20 -0600 (Fri, 09 Nov 2007) $
 */
public abstract class StringTerm extends SearchTerm {
    /**
     * If true, case should be ignored during matching.
     */
    protected boolean ignoreCase;

    /**
     * The pattern associated with this term.
     */
    protected String pattern;

    /**
     * Constructor specifying a pattern.
     * Defaults to case insensitive matching.
     * @param pattern the pattern for this term
     */
    protected StringTerm(String pattern) {
        this(pattern, true);
    }

    /**
     * Constructor specifying pattern and case sensitivity.
     * @param pattern the pattern for this term
     * @param ignoreCase if true, case should be ignored during matching
     */
    protected StringTerm(String pattern, boolean ignoreCase) {
        this.pattern = pattern;
        this.ignoreCase = ignoreCase;
    }

    /**
     * Return the pattern associated with this term.
     * @return the pattern associated with this term
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Indicate if case should be ignored when matching.
     * @return if true, case should be ignored during matching
     */
    public boolean getIgnoreCase() {
        return ignoreCase;
    }

    /**
     * Determine if the pattern associated with this term is a substring of the
     * supplied String. If ignoreCase is true then case will be ignored.
     *
     * @param match the String to compare to
     * @return true if this patter is a substring of the supplied String
     */
    protected boolean match(String match) {
        int matchLength = pattern.length(); 
        int length = match.length() - matchLength;        
        
        for (int i = 0; i <= length; i++) {
            if (match.regionMatches(ignoreCase, i, pattern, 0, matchLength)) {
                return true; 
            }
        }
        return false;
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof StringTerm == false) return false;
        
        StringTerm term = (StringTerm)other; 
        
        if (ignoreCase) {
            return term.pattern.equalsIgnoreCase(pattern) && term.ignoreCase == ignoreCase; 
        }
        else {
            return term.pattern.equals(pattern) && term.ignoreCase == ignoreCase; 
        }
    }

    public int hashCode() {
        return pattern.hashCode() + (ignoreCase ? 32 : 79);
    }
}
