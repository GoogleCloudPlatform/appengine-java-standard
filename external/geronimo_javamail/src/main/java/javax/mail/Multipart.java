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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Vector;

/**
 * A container for multiple {@link BodyPart BodyParts}.
 *
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public abstract class Multipart {
    /**
     * Vector of sub-parts.
     */
    protected Vector parts = new Vector();

    /**
     * The content type of this multipart object; defaults to "multipart/mixed"
     */
    protected String contentType = "multipart/mixed";

    /**
     * The Part that contains this multipart.
     */
    protected Part parent;

    protected Multipart() {
    }

    /**
     * Initialize this multipart object from the supplied data source.
     * This adds any {@link BodyPart BodyParts} into this object and initializes the content type.
     *
     * @param mds the data source
     * @throws MessagingException
     */
    protected void setMultipartDataSource(MultipartDataSource mds) throws MessagingException {
        parts.clear();
        contentType = mds.getContentType();
        int size = mds.getCount();
        for (int i = 0; i < size; i++) {
            parts.add(mds.getBodyPart(i));
        }
    }

    /**
     * Return the content type.
     *
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Return the number of enclosed parts
     *
     * @return the number of parts
     * @throws MessagingException
     */
    public int getCount() throws MessagingException {
        return parts.size();
    }

    /**
     * Get the specified part; numbering starts at zero.
     *
     * @param index the part to get
     * @return the part
     * @throws MessagingException
     */
    public BodyPart getBodyPart(int index) throws MessagingException {
        return (BodyPart) parts.get(index);
    }

    /**
     * Remove the supplied part from the list.
     *
     * @param part the part to remove
     * @return true if the part was removed
     * @throws MessagingException
     */
    public boolean removeBodyPart(BodyPart part) throws MessagingException {
        return parts.remove(part);
    }

    /**
     * Remove the specified part; all others move down one
     *
     * @param index the part to remove
     * @throws MessagingException
     */
    public void removeBodyPart(int index) throws MessagingException {
        parts.remove(index);
    }

    /**
     * Add a part to the end of the list.
     *
     * @param part the part to add
     * @throws MessagingException
     */
    public void addBodyPart(BodyPart part) throws MessagingException {
        parts.add(part);
    }

    /**
     * Insert a part into the list at a designated point; all subsequent parts move down
     *
     * @param part the part to add
     * @param pos  the index of the new part
     * @throws MessagingException
     */
    public void addBodyPart(BodyPart part, int pos) throws MessagingException {
        parts.add(pos, part);
    }

    /**
     * Encode and write this multipart to the supplied OutputStream; the encoding
     * used is determined by the implementation.
     *
     * @param out the stream to write to
     * @throws IOException
     * @throws MessagingException
     */
    public abstract void writeTo(OutputStream out) throws IOException, MessagingException;

    /**
     * Return the Part containing this Multipart object or null if unknown.
     *
     * @return this Multipart's parent
     */
    public Part getParent() {
        return parent;
    }

    /**
     * Set the parent of this Multipart object
     *
     * @param part this object's parent
     */
    public void setParent(Part part) {
        parent = part;
    }

}
