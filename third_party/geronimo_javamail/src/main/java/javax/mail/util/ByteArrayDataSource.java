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

package javax.mail.util;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;
import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;
import javax.mail.internet.MimeUtility;


/**
 * An activation DataSource object that sources the data from
 * a byte[] array.
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class ByteArrayDataSource implements DataSource {
    // the data source
    private byte[] source;
    // the content MIME type
    private String contentType;
    // the name information (defaults to a null string)
    private String name = "";


    /**
     * Create a ByteArrayDataSource from an input stream.
     *
     * @param in     The source input stream.
     * @param type   The MIME-type of the data.
     *
     * @exception IOException
     */
    public ByteArrayDataSource(InputStream in, String type) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        // ok, how I wish you could just pipe an input stream into an output stream :-)
        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = in.read(buffer)) > 0) {
            sink.write(buffer, 0, bytesRead);
        }

        source = sink.toByteArray();
        contentType = type;
    }


    /**
     * Create a ByteArrayDataSource directly from a byte array.
     *
     * @param data   The source byte array (not copied).
     * @param type   The content MIME-type.
     */
    public ByteArrayDataSource(byte[] data, String type) {
        source = data;
        contentType = type;
    }

    /**
     * Create a ByteArrayDataSource from a string value.  If the
     * type information includes a charset parameter, that charset
     * is used to extract the bytes.  Otherwise, the default Java
     * char set is used.
     *
     * @param data   The source data string.
     * @param type   The MIME type information.
     *
     * @exception IOException
     */
    public ByteArrayDataSource(String data, String type) throws IOException {
        String charset = null;
        try {
            // the charset can be encoded in the content type, which we parse using
            // the ContentType class.
            ContentType content = new ContentType(type);
            charset = content.getParameter("charset");
        } catch (ParseException e) {
            // ignored...just use the default if this fails
        }
        if (charset == null) {
            charset = MimeUtility.getDefaultJavaCharset();
        }
        else {
            // the type information encodes a MIME charset, which may need mapping to a Java one.
            charset = MimeUtility.javaCharset(charset);
        }

        // get the source using the specified charset
        source = data.getBytes(charset);
        contentType = type;
    }


    /**
     * Create an input stream for this data.  A new input stream
     * is created each time.
     *
     * @return An InputStream for reading the encapsulated data.
     * @exception IOException
     */
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(source);
    }


    /**
     * Open an output stream for the DataSource.  This is not
     * supported by this DataSource, so an IOException is always
     * throws.
     *
     * @return Nothing...an IOException is always thrown.
     * @exception IOException
     */
    public OutputStream getOutputStream() throws IOException {
        throw new IOException("Writing to a ByteArrayDataSource is not supported");
    }


    /**
     * Get the MIME content type information for this DataSource.
     *
     * @return The MIME content type string.
     */
    public String getContentType() {
        return contentType;
    }


    /**
     * Retrieve the DataSource name.  If not explicitly set, this
     * returns "".
     *
     * @return The currently set DataSource name.
     */
    public String getName() {
        return name;
    }


    /**
     * Set a new DataSource name.
     *
     * @param name   The new name.
     */
    public void setName(String name) {
        this.name = name;
    }
}

