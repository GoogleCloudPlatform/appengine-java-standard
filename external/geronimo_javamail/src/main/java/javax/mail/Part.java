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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import javax.activation.DataHandler;

/**
 * Note: Parts are used in Collections so implementing classes must provide
 * a suitable implementation of equals and hashCode.
 *
 * @version $Rev: 578802 $ $Date: 2007-09-24 08:16:44 -0500 (Mon, 24 Sep 2007) $
 */
public interface Part {
    /**
     * This part should be presented as an attachment.
     */
    public static final String ATTACHMENT = "attachment";
    /**
     * This part should be presented or rendered inline.
     */
    public static final String INLINE = "inline";

    /**
     * Add this value to the existing headers with the given name.  This method 
     * does not replace any headers that may already exist.
     * 
     * @param name   The name of the target header.
     * @param value  The value to be added to the header set.
     * 
     * @exception MessagingException
     */
    public abstract void addHeader(String name, String value) throws MessagingException;

    /**
     * Return all headers as an Enumeration of Header objects.
     * 
     * @return An Enumeration containing all of the current Header objects. 
     * @exception MessagingException
     */
    public abstract Enumeration getAllHeaders() throws MessagingException;

    /**
     * Return a content object for this Part.  The 
     * content object type is dependent upon the 
     * DataHandler for the Part.
     * 
     * @return A content object for this Part.
     * @exception IOException
     * @exception MessagingException
     */
    public abstract Object getContent() throws IOException, MessagingException;

    /**
     * Get the ContentType for this part, or null if the 
     * ContentType has not been set.  The ContentType 
     * is expressed using the MIME typing system.
     * 
     * @return The ContentType for this part. 
     * @exception MessagingException
     */
    public abstract String getContentType() throws MessagingException;

    /**
     * Returns a DataHandler instance for the content
     * with in the Part.  
     * 
     * @return A DataHandler appropriate for the Part content.
     * @exception MessagingException
     */
    public abstract DataHandler getDataHandler() throws MessagingException;

    /**
     * Returns a description string for this Part.  Returns
     * null if a description has not been set.
     * 
     * @return The description string. 
     * @exception MessagingException
     */
    public abstract String getDescription() throws MessagingException;

    /**
     * Return the disposition of the part.  The disposition 
     * determines how the part should be presented to the 
     * user.  Two common disposition values are ATTACHMENT
     * and INLINE. 
     * 
     * @return The current disposition value. 
     * @exception MessagingException
     */
    public abstract String getDisposition() throws MessagingException;

    /**
     * Get a file name associated with this part.  The 
     * file name is useful for presenting attachment 
     * parts as their original source.  The file names 
     * are generally simple names without containing 
     * any directory information.  Returns null if the 
     * filename has not been set. 
     * 
     * @return The string filename, if any. 
     * @exception MessagingException
     */
    public abstract String getFileName() throws MessagingException;

    /**
     * Get all Headers for this header name.  Returns null if no headers with 
     * the given name exist.
     * 
     * @param name   The target header name.
     * 
     * @return An array of all matching header values, or null if the given header 
     *         does not exist.
     * @exception MessagingException
     */
    public abstract String[] getHeader(String name) throws MessagingException;

    /**
     * Return an InputStream for accessing the Part 
     * content.  Any mail-related transfer encodings 
     * will be removed, so the data presented with 
     * be the actual part content. 
     * 
     * @return An InputStream for accessing the part content. 
     * @exception IOException
     * @exception MessagingException
     */
    public abstract InputStream getInputStream() throws IOException, MessagingException;

    /**
     * Return the number of lines in the content, or 
     * -1 if the line count cannot be determined.
     * 
     * @return The estimated number of lines in the content. 
     * @exception MessagingException
     */
    public abstract int getLineCount() throws MessagingException;

    /**
     * Return all headers that match the list of names as an Enumeration of 
     * Header objects.
     * 
     * @param names  An array of names of the desired headers.
     * 
     * @return An Enumeration of Header objects containing the matching headers.
     * @exception MessagingException
     */
    public abstract Enumeration getMatchingHeaders(String[] names) throws MessagingException;

    /**
     * Return an Enumeration of all Headers except those that match the names 
     * given in the exclusion list.
     * 
     * @param names  An array of String header names that will be excluded from the return
     *               Enumeration set.
     * 
     * @return An Enumeration of Headers containing all headers except for those named 
     *         in the exclusion list.
     * @exception MessagingException
     */
    public abstract Enumeration getNonMatchingHeaders(String[] names) throws MessagingException;

    /**
     * Return the size of this part, or -1 if the size
     * cannot be reliably determined.  
     * 
     * Note:  the returned size does not take into account 
     * internal encodings, nor is it an estimate of 
     * how many bytes are required to transfer this 
     * part across a network.  This value is intended
     * to give email clients a rough idea of the amount 
     * of space that might be required to present the
     * item.
     * 
     * @return The estimated part size, or -1 if the size 
     *         information cannot be determined.
     * @exception MessagingException
     */
    public abstract int getSize() throws MessagingException;

    /**
     * Tests if the part is of the specified MIME type.
     * Only the primaryPart and subPart of the MIME 
     * type are used for the comparison;  arguments are 
     * ignored.  The wildcard value of "*" may be used 
     * to match all subTypes.
     * 
     * @param mimeType The target MIME type.
     * 
     * @return true if the part matches the input MIME type, 
     *         false if it is not of the requested type.
     * @exception MessagingException
     */
    public abstract boolean isMimeType(String mimeType) throws MessagingException;

    /**
     * Remove all headers with the given name from the Part.
     * 
     * @param name   The target header name used for removal.
     * 
     * @exception MessagingException
     */
    public abstract void removeHeader(String name) throws MessagingException;

    public abstract void setContent(Multipart content) throws MessagingException;

    /**
     * Set a content object for this part.  Internally, 
     * the Part will use the MIME type encoded in the 
     * type argument to wrap the provided content object.
     * In order for this to work properly, an appropriate 
     * DataHandler must be installed in the Java Activation 
     * Framework.
     * 
     * @param content The content object.
     * @param type    The MIME type for the inserted content Object.
     * 
     * @exception MessagingException
     */
    public abstract void setContent(Object content, String type) throws MessagingException;

    /**
     * Set a DataHandler for this part that defines the 
     * Part content.  The DataHandler is used to access 
     * all Part content.
     * 
     * @param handler The DataHandler instance.
     * 
     * @exception MessagingException
     */
    public abstract void setDataHandler(DataHandler handler) throws MessagingException;

    /**
     * Set a descriptive string for this part.
     * 
     * @param description
     *               The new description.
     * 
     * @exception MessagingException
     */
    public abstract void setDescription(String description) throws MessagingException;

    /**
     * Set the disposition for this Part.
     * 
     * @param disposition
     *               The disposition string.
     * 
     * @exception MessagingException
     */
    public abstract void setDisposition(String disposition) throws MessagingException;

    /**
     * Set a descriptive file name for this part.  The 
     * name should be a simple name that does not include 
     * directory information.
     * 
     * @param name   The new name value.
     * 
     * @exception MessagingException
     */
    public abstract void setFileName(String name) throws MessagingException;

    /**
     * Sets a value for the given header.  This operation will replace all 
     * existing headers with the given name.
     * 
     * @param name   The name of the target header.
     * @param value  The new value for the indicated header.
     * 
     * @exception MessagingException
     */
    public abstract void setHeader(String name, String value) throws MessagingException;

    /**
     * Set the Part content as text.  This is a convenience method that sets 
     * the content to a MIME type of "text/plain".
     * 
     * @param content The new text content, as a String object.
     * 
     * @exception MessagingException
     */
    public abstract void setText(String content) throws MessagingException;

    /**
     * Write the Part content out to the provided OutputStream as a byte
     * stream using an encoding appropriate to the Part content.
     * 
     * @param out    The target OutputStream.
     * 
     * @exception IOException
     * @exception MessagingException
     */
    public abstract void writeTo(OutputStream out) throws IOException, MessagingException;
}
