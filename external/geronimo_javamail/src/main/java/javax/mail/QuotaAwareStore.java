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
 * An interface for Store implementations to support the IMAP RFC 2087 Quota extension.
 *
 * @version $Rev: 581202 $ $Date: 2007-10-02 07:05:22 -0500 (Tue, 02 Oct 2007) $
 */
public interface QuotaAwareStore {
    
    /**
     * Get the quotas for the specified root element.
     *
     * @param root   The root name for the quota information.
     *
     * @return An array of Quota objects defined for the root.
     * @throws MessagingException if the quotas cannot be retrieved
     */
    public Quota[] getQuota(String root) throws javax.mail.MessagingException;

    /**
     * Set a quota item.  The root contained in the Quota item identifies
     * the quota target.
     *
     * @param quota  The source quota item.
     * @throws MessagingException if the quota cannot be set
     */
    public void setQuota(Quota quota) throws javax.mail.MessagingException;
}


