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

package org.apache.geronimo.mail.handlers;

import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeUtility;
import javax.mail.internet.ParseException;

public class TextHandler implements DataContentHandler {
    /**
     * Field dataFlavor
     */
    ActivationDataFlavor dataFlavor;

    public TextHandler(){
        dataFlavor = new ActivationDataFlavor(java.lang.String.class, "text/plain", "Text String");
    }

    /**
     * Constructor TextHandler
     *
     * @param dataFlavor
     */
    public TextHandler(ActivationDataFlavor dataFlavor) {
        this.dataFlavor = dataFlavor;
    }

    /**
     * Method getDF
     *
     * @return dataflavor
     */
    protected ActivationDataFlavor getDF() {
        return dataFlavor;
    }

    /**
     * Method getTransferDataFlavors
     *
     * @return dataflavors
     */
    public DataFlavor[] getTransferDataFlavors() {
        return (new DataFlavor[]{dataFlavor});
    }

    /**
     * Method getTransferData
     *
     * @param dataflavor
     * @param datasource
     * @return
     * @throws IOException
     */
    public Object getTransferData(DataFlavor dataflavor, DataSource datasource)
            throws IOException {
        if (getDF().equals(dataflavor)) {
            return getContent(datasource);
        }
        return null;
    }

    /**
     * Method getContent
     *
     * @param datasource
     * @return
     * @throws IOException
     */
    public Object getContent(DataSource datasource) throws IOException {
        InputStream is = datasource.getInputStream(); 
        ByteArrayOutputStream os = new ByteArrayOutputStream(); 
        
        int count;  
        byte[] buffer = new byte[1000]; 
            
        try {
            while ((count = is.read(buffer, 0, buffer.length)) > 0) {
                os.write(buffer, 0, count); 
            }
        } finally {
            is.close(); 
        }
        try {   
            return os.toString(getCharSet(datasource.getContentType())); 
        } catch (ParseException e) {
            throw new UnsupportedEncodingException(e.getMessage()); 
        }
    }

    
    /**
     * Write an object of "our" type out to the provided 
     * output stream.  The content type might modify the 
     * result based on the content type parameters. 
     * 
     * @param object The object to write.
     * @param contentType
     *               The content mime type, including parameters.
     * @param outputstream
     *               The target output stream.
     * 
     * @throws IOException
     */
    public void writeTo(Object object, String contentType, OutputStream outputstream)
            throws IOException {
        OutputStreamWriter os;
        try {
            String charset = getCharSet(contentType);
            os = new OutputStreamWriter(outputstream, charset);
        } catch (Exception ex) {
            throw new UnsupportedEncodingException(ex.toString());
        }
        String content = (String) object;
        os.write(content, 0, content.length());
        os.flush();
    }

    /**
     * get the character set from content type
     * @param contentType
     * @return
     * @throws ParseException
     */
    protected String getCharSet(String contentType) throws ParseException {
        ContentType type = new ContentType(contentType);
        String charset = type.getParameter("charset");
        if (charset == null) {
            charset = "us-ascii";
        }
        return MimeUtility.javaCharset(charset);
    }
}
