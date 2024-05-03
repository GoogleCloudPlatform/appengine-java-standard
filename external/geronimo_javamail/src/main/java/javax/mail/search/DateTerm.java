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

import java.util.Date;

/**
 * @version $Rev: 593593 $ $Date: 2007-11-09 11:04:20 -0600 (Fri, 09 Nov 2007) $
 */
public abstract class DateTerm extends ComparisonTerm {
    protected Date date;

    protected DateTerm(int comparison, Date date) {
        super();
        this.comparison = comparison;
        this.date = date;
    }

    public Date getDate() {
        return date;
    }

    public int getComparison() {
        return comparison;
    }

    protected boolean match(Date match) {
        long matchTime = match.getTime();
        long mytime = date.getTime();
        switch (comparison) {
        case EQ:
            return matchTime == mytime;
        case NE:
            return matchTime != mytime;
        case LE:
            return matchTime <= mytime;
        case LT:
            return matchTime < mytime;
        case GT:
            return matchTime > mytime;
        case GE:
            return matchTime >= mytime;
        default:
            return false;
        }
    }

    public boolean equals(Object other) {
        if (other == this) return true;
        if (other instanceof DateTerm == false) return false;
        final DateTerm term = (DateTerm) other;
        return this.comparison == term.comparison && this.date.equals(term.date);
    }

    public int hashCode() {
        return date.hashCode() + super.hashCode();
    }
}
