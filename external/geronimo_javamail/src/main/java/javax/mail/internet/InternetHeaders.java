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

package javax.mail.internet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Header;
import javax.mail.MessagingException;

/**
 * Class that represents the RFC822 headers associated with a message.
 *
 * @version $Rev: 702234 $ $Date: 2008-10-06 14:25:41 -0500 (Mon, 06 Oct 2008) $
 */
public class InternetHeaders {
    // the list of headers (to preserve order);
    protected List headers = new ArrayList();

    private transient String lastHeaderName;

    /**
     * Create an empty InternetHeaders
     */
    public InternetHeaders() {
        // these are created in the preferred order of the headers.
        addHeader("Return-Path", null);
        addHeader("Received", null);
        addHeader("Resent-Date", null);
        addHeader("Resent-From", null);
        addHeader("Resent-Sender", null);
        addHeader("Resent-To", null);
        addHeader("Resent-Cc", null);
        addHeader("Resent-Bcc", null);
        addHeader("Resent-Message-Id", null);
        addHeader("Date", null);
        addHeader("From", null);
        addHeader("Sender", null);
        addHeader("Reply-To", null);
        addHeader("To", null);
        addHeader("Cc", null);
        addHeader("Bcc", null);
        addHeader("Message-Id", null);
        addHeader("In-Reply-To", null);
        addHeader("References", null);
        addHeader("Subject", null);
        addHeader("Comments", null);
        addHeader("Keywords", null);
        addHeader("Errors-To", null);
        addHeader("MIME-Version", null);
        addHeader("Content-Type", null);
        addHeader("Content-Transfer-Encoding", null);
        addHeader("Content-MD5", null);
        // the following is a special marker used to identify new header insertion points.
        addHeader(":", null);
        addHeader("Content-Length", null);
        addHeader("Status", null);
    }

    /**
     * Create a new InternetHeaders initialized by reading headers from the
     * stream.
     *
     * @param in
     *            the RFC822 input stream to load from
     * @throws MessagingException
     *             if there is a problem pasring the stream
     */
    public InternetHeaders(InputStream in) throws MessagingException {
        load(in);
    }

    /**
     * Read and parse the supplied stream and add all headers to the current
     * set.
     *
     * @param in
     *            the RFC822 input stream to load from
     * @throws MessagingException
     *             if there is a problem pasring the stream
     */
    public void load(InputStream in) throws MessagingException {
        try {
            StringBuffer buffer = new StringBuffer(128); 
            String line; 
            // loop until we hit the end or a null line 
            while ((line = readLine(in)) != null) {
                // lines beginning with white space get special handling 
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // this gets handled using the logic defined by 
                    // the addHeaderLine method.  If this line is a continuation, but 
                    // there's nothing before it, just call addHeaderLine to add it 
                    // to the last header in the headers list 
                    if (buffer.length() == 0) {
                        addHeaderLine(line); 
                    }
                    else {
                        // preserve the line break and append the continuation 
                        buffer.append("\r\n"); 
                        buffer.append(line); 
                    }
                }
                else {
                    // if we have a line pending in the buffer, flush it 
                    if (buffer.length() > 0) {
                        addHeaderLine(buffer.toString()); 
                        buffer.setLength(0); 
                    }
                    // add this to the accumulator 
                    buffer.append(line); 
                }
            }
            
            // if we have a line pending in the buffer, flush it 
            if (buffer.length() > 0) {
                addHeaderLine(buffer.toString()); 
            }
        } catch (IOException e) {
            throw new MessagingException("Error loading headers", e);
        }
    }
    
    
    /**
     * Read a single line from the input stream 
     * 
     * @param in     The source stream for the line
     * 
     * @return The string value of the line (without line separators)
     */
    private String readLine(InputStream in) throws IOException {
        StringBuffer buffer = new StringBuffer(128); 
        
        int c; 
        
        while ((c = in.read()) != -1) {
            // a linefeed is a terminator, always.  
            if (c == '\n') {
                break; 
            }
            // just ignore the CR.  The next character SHOULD be an NL.  If not, we're 
            // just going to discard this 
            else if (c == '\r') {
                continue; 
            }
            else {
                // just add to the buffer 
                buffer.append((char)c); 
            }
        }
        
        // no characters found...this was either an eof or a null line. 
        if (buffer.length() == 0) {
            return null; 
        }
        
        return buffer.toString(); 
    }


    /**
     * Return all the values for the specified header.
     *
     * @param name
     *            the header to return
     * @return the values for that header, or null if the header is not present
     */
    public String[] getHeader(String name) {
        List accumulator = new ArrayList();

        for (int i = 0; i < headers.size(); i++) {
            InternetHeader header = (InternetHeader)headers.get(i);
            if (header.getName().equalsIgnoreCase(name) && header.getValue() != null) {
                accumulator.add(header.getValue());
            }
        }

        // this is defined as returning null of nothing is found.
        if (accumulator.isEmpty()) {
            return null;
        }

        // convert this to an array.
        return (String[])accumulator.toArray(new String[accumulator.size()]);
    }

    /**
     * Return the values for the specified header as a single String. If the
     * header has more than one value then all values are concatenated together
     * separated by the supplied delimiter.
     *
     * @param name
     *            the header to return
     * @param delimiter
     *            the delimiter used in concatenation
     * @return the header as a single String
     */
    public String getHeader(String name, String delimiter) {
        // get all of the headers with this name
        String[] matches = getHeader(name);

        // no match?  return a null.
        if (matches == null) {
            return null;
        }

        // a null delimiter means just return the first one.  If there's only one item, this is easy too.
        if (matches.length == 1 || delimiter == null) {
            return matches[0];
        }

        // perform the concatenation
        StringBuffer result = new StringBuffer(matches[0]);

        for (int i = 1; i < matches.length; i++) {
            result.append(delimiter);
            result.append(matches[i]);
        }

        return result.toString();
    }


    /**
     * Set the value of the header to the supplied value; any existing headers
     * are removed.
     *
     * @param name
     *            the name of the header
     * @param value
     *            the new value
     */
    public void setHeader(String name, String value) {
        // look for a header match
        for (int i = 0; i < headers.size(); i++) {
            InternetHeader header = (InternetHeader)headers.get(i);
            // found a matching header
            if (name.equalsIgnoreCase(header.getName())) {
                // we update both the name and the value for a set so that 
                // the header ends up with the same case as what is getting set
                header.setValue(value);
                header.setName(name); 
                // remove all of the headers from this point
                removeHeaders(name, i + 1);
                return;
            }
        }

        // doesn't exist, so process as an add.
        addHeader(name, value);
    }


    /**
     * Remove all headers with the given name, starting with the
     * specified start position.
     *
     * @param name   The target header name.
     * @param pos    The position of the first header to examine.
     */
    private void removeHeaders(String name, int pos) {
        // now go remove all other instances of this header
        for (int i = pos; i < headers.size(); i++) {
            InternetHeader header = (InternetHeader)headers.get(i);
            // found a matching header
            if (name.equalsIgnoreCase(header.getName())) {
                // remove this item, and back up
                headers.remove(i);
                i--;
            }
        }
    }


    /**
     * Find a header in the current list by name, returning the index.
     *
     * @param name   The target name.
     *
     * @return The index of the header in the list.  Returns -1 for a not found
     *         condition.
     */
    private int findHeader(String name) {
        return findHeader(name, 0);
    }


    /**
     * Find a header in the current list, beginning with the specified
     * start index.
     *
     * @param name   The target header name.
     * @param start  The search start index.
     *
     * @return The index of the first matching header.  Returns -1 if the
     *         header is not located.
     */
    private int findHeader(String name, int start) {
        for (int i = start; i < headers.size(); i++) {
            InternetHeader header = (InternetHeader)headers.get(i);
            // found a matching header
            if (name.equalsIgnoreCase(header.getName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Add a new value to the header with the supplied name.
     *
     * @param name
     *            the name of the header to add a new value for
     * @param value
     *            another value
     */
    public void addHeader(String name, String value) {
        InternetHeader newHeader = new InternetHeader(name, value);

        // The javamail spec states that "Recieved" headers need to be added in reverse order.
        // Return-Path is permitted before Received, so handle it the same way.
        if (name.equalsIgnoreCase("Received") || name.equalsIgnoreCase("Return-Path")) {
            // see if we have one of these already
            int pos = findHeader(name);

            // either insert before an existing header, or insert at the very beginning
            if (pos != -1) {
                // this could be a placeholder header with a null value.  If it is, just update
                // the value.  Otherwise, insert in front of the existing header.
                InternetHeader oldHeader = (InternetHeader)headers.get(pos);
                if (oldHeader.getValue() == null) {
                    oldHeader.setValue(value);
                }
                else {
                    headers.add(pos, newHeader);
                }
            }
            else {
                // doesn't exist, so insert at the beginning
                headers.add(0, newHeader);
            }
        }
        // normal insertion
        else {
            // see if we have one of these already
            int pos = findHeader(name);

            // either insert before an existing header, or insert at the very beginning
            if (pos != -1) {
                InternetHeader oldHeader = (InternetHeader)headers.get(pos);
                // if the existing header is a place holder, we can just update the value
                if (oldHeader.getValue() == null) {
                    oldHeader.setValue(value);
                }
                else {
                    // we have at least one existing header with this name.  We need to find the last occurrance,
                    // and insert after that spot.

                    int lastPos = findHeader(name, pos + 1);

                    while (lastPos != -1) {
                        pos = lastPos;
                        lastPos = findHeader(name, pos + 1);
                    }

                    // ok, we have the insertion position
                    headers.add(pos + 1, newHeader);
                }
            }
            else {
                // find the insertion marker.  If that is missing somehow, insert at the end.
                pos = findHeader(":");
                if (pos == -1) {
                    pos = headers.size();
                }
                headers.add(pos, newHeader);
            }
        }
    }


    /**
     * Remove all header entries with the supplied name
     *
     * @param name
     *            the header to remove
     */
    public void removeHeader(String name) {
        // the first occurrance of a header is just zeroed out.
        int pos = findHeader(name);

        if (pos != -1) {
            InternetHeader oldHeader = (InternetHeader)headers.get(pos);
            // keep the header in the list, but with a null value
            oldHeader.setValue(null);
            // now remove all other headers with this name
            removeHeaders(name, pos + 1);
        }
    }


    /**
     * Return all headers.
     *
     * @return an Enumeration<Header> containing all headers
     */
    public Enumeration getAllHeaders() {
        List result = new ArrayList();

        for (int i = 0; i < headers.size(); i++) {
            InternetHeader header = (InternetHeader)headers.get(i);
            // we only include headers with real values, no placeholders
            if (header.getValue() != null) {
                result.add(header);
            }
        }
        // just return a list enumerator for the header list.
        return Collections.enumeration(result);
    }


    /**
     * Test if a given header name is a match for any header in the
     * given list.
     *
     * @param name   The name of the current tested header.
     * @param names  The list of names to match against.
     *
     * @return True if this is a match for any name in the list, false
     *         for a complete mismatch.
     */
    private boolean matchHeader(String name, String[] names) {
        // the list of names is not required, so treat this as if it 
        // was an empty list and we didn't get a match. 
        if (names == null) {
            return false; 
        }
        
        for (int i = 0; i < names.length; i++) {
            if (name.equalsIgnoreCase(names[i])) {
                return true;
            }
        }
        return false;
    }


    /**
     * Return all matching Header objects.
     */
    public Enumeration getMatchingHeaders(String[] names) {
        List result = new ArrayList();

        for (int i = 0; i < headers.size(); i++) {
            InternetHeader header = (InternetHeader)headers.get(i);
            // we only include headers with real values, no placeholders
            if (header.getValue() != null) {
                // only add the matching ones
                if (matchHeader(header.getName(), names)) {
                    result.add(header);
                }
            }
        }
        return Collections.enumeration(result);
    }


    /**
     * Return all non matching Header objects.
     */
    public Enumeration getNonMatchingHeaders(String[] names) {
        List result = new ArrayList();

        for (int i = 0; i < headers.size(); i++) {
            InternetHeader header = (InternetHeader)headers.get(i);
            // we only include headers with real values, no placeholders
            if (header.getValue() != null) {
                // only add the non-matching ones
                if (!matchHeader(header.getName(), names)) {
                    result.add(header);
                }
            }
        }
        return Collections.enumeration(result);
    }


    /**
     * Add an RFC822 header line to the header store. If the line starts with a
     * space or tab (a continuation line), add it to the last header line in the
     * list. Otherwise, append the new header line to the list.
     *
     * Note that RFC822 headers can only contain US-ASCII characters
     *
     * @param line
     *            raw RFC822 header line
     */
    public void addHeaderLine(String line) {
        // null lines are a nop
        if (line.length() == 0) {
            return;
        }

        // we need to test the first character to see if this is a continuation whitespace
        char ch = line.charAt(0);

        // tabs and spaces are special.  This is a continuation of the last header in the list.
        if (ch == ' ' || ch == '\t') {
            int size = headers.size(); 
            // it's possible that we have a leading blank line. 
            if (size > 0) {
                InternetHeader header = (InternetHeader)headers.get(size - 1);
                header.appendValue(line);
            }
        }
        else {
            // this just gets appended to the end, preserving the addition order.
            headers.add(new InternetHeader(line));
        }
    }


    /**
     * Return all the header lines as an Enumeration of Strings.
     */
    public Enumeration getAllHeaderLines() {
        return new HeaderLineEnumeration(getAllHeaders());
    }

    /**
     * Return all matching header lines as an Enumeration of Strings.
     */
    public Enumeration getMatchingHeaderLines(String[] names) {
        return new HeaderLineEnumeration(getMatchingHeaders(names));
    }

    /**
     * Return all non-matching header lines.
     */
    public Enumeration getNonMatchingHeaderLines(String[] names) {
        return new HeaderLineEnumeration(getNonMatchingHeaders(names));
    }


    /**
     * Set an internet header from a list of addresses.  The
     * first address item is set, followed by a series of addHeaders().
     *
     * @param name      The name to set.
     * @param addresses The list of addresses to set.
     */
    void setHeader(String name, Address[] addresses) {
        // if this is empty, then we need to replace this
        if (addresses.length == 0) {
            removeHeader(name);
        } else {
    
            // replace the first header
            setHeader(name, addresses[0].toString());
    
            // now add the rest as extra headers.
            for (int i = 1; i < addresses.length; i++) {
                Address address = addresses[i];
                addHeader(name, address.toString());
            }
        }
    }


    /**
     * Write out the set of headers, except for any 
     * headers specified in the optional ignore list. 
     * 
     * @param out    The output stream.
     * @param ignore The optional ignore list.
     * 
     * @exception IOException
     */
    void writeTo(OutputStream out, String[] ignore) throws IOException {
        if (ignore == null) {
            // write out all header lines with non-null values
            for (int i = 0; i < headers.size(); i++) {
                InternetHeader header = (InternetHeader)headers.get(i);
                // we only include headers with real values, no placeholders
                if (header.getValue() != null) {
                    header.writeTo(out);
                }
            }
        }
        else {
            // write out all matching header lines with non-null values
            for (int i = 0; i < headers.size(); i++) {
                InternetHeader header = (InternetHeader)headers.get(i);
                // we only include headers with real values, no placeholders
                if (header.getValue() != null) {
                    if (!matchHeader(header.getName(), ignore)) {
                        header.writeTo(out);
                    }
                }
            }
        }
    }

    protected static final class InternetHeader extends Header {

        public InternetHeader(String h) {
            // initialize with null values, which we'll update once we parse the string
            super("", "");
            int separator = h.indexOf(':');
            // no separator, then we take this as a name with a null string value.
            if (separator == -1) {
                name = h.trim();
            }
            else {
                name = h.substring(0, separator);
                // step past the separator.  Now we need to remove any leading white space characters.
                separator++;

                while (separator < h.length()) {
                    char ch = h.charAt(separator);
                    if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') {
                        break;
                    }
                    separator++;
                }

                value = h.substring(separator);
            }
        }

        public InternetHeader(String name, String value) {
            super(name, value);
        }


        /**
         * Package scope method for setting the header value.
         *
         * @param value  The new header value.
         */
        void setValue(String value) {
            this.value = value;
        }


        /**
         * Package scope method for setting the name value.
         *
         * @param name   The new header name   
         */
        void setName(String name) {
            this.name = name;     
        }

        /**
         * Package scope method for extending a header value.
         *
         * @param value  The appended header value.
         */
        void appendValue(String value) {
            if (this.value == null) {
                this.value = value;
            }
            else {
                this.value = this.value + "\r\n" + value;
            }
        }

        void writeTo(OutputStream out) throws IOException {
            out.write(name.getBytes());
            out.write(':');
            out.write(' ');
            out.write(value.getBytes());
            out.write('\r');
            out.write('\n');
        }
    }

    private static class HeaderLineEnumeration implements Enumeration {
        private Enumeration headers;

        public HeaderLineEnumeration(Enumeration headers) {
            this.headers = headers;
        }

        public boolean hasMoreElements() {
            return headers.hasMoreElements();
        }

        public Object nextElement() {
            Header h = (Header) headers.nextElement();
            return h.getName() + ": " + h.getValue();
        }
    }
}
