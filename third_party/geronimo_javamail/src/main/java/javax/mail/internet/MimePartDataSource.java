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
import java.net.UnknownServiceException;
import javax.activation.DataSource;
import javax.mail.MessageAware;
import javax.mail.MessageContext;
import javax.mail.MessagingException;

/**
 * @version $Rev: 702432 $ $Date: 2008-10-07 06:18:08 -0500 (Tue, 07 Oct 2008) $
 */
public class MimePartDataSource implements DataSource, MessageAware {
    // the part that provides the data form this data source.
    protected MimePart part;

    public MimePartDataSource(MimePart part) {
        this.part = part;
    }

    public InputStream getInputStream() throws IOException {
        try {
            InputStream stream;
            if (part instanceof MimeMessage) {
                stream = ((MimeMessage) part).getContentStream();
            } else if (part instanceof MimeBodyPart) {
                stream = ((MimeBodyPart) part).getContentStream();
            } else {
                throw new MessagingException("Unknown part");
            }
            return checkPartEncoding(part, stream);
        } catch (MessagingException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }
    
    
    /**
     * For a given part, decide it the data stream requires
     * wrappering with a stream for decoding a particular 
     * encoding. 
     * 
     * @param part   The part we're extracting.
     * @param stream The raw input stream for the part.
     * 
     * @return An input stream configured for reading the 
     *         source part and decoding it into raw bytes.
     */
    private InputStream checkPartEncoding(MimePart part, InputStream stream) throws MessagingException {
        String encoding = part.getEncoding();
        // if nothing is specified, there's nothing to do 
        if (encoding == null) {
            return stream; 
        }
        // now screen out the ones that never need decoding 
        encoding = encoding.toLowerCase(); 
        if (encoding.equals("7bit") || encoding.equals("8bit") || encoding.equals("binary")) {
            return stream; 
        }
        // now we need to check the content type to prevent 
        // MultiPart types from getting decoded, since the part is just an envelope around other 
        // parts 
        String contentType = part.getContentType(); 
        if (contentType != null) {
            try {
                ContentType type = new ContentType(contentType); 
                // no decoding done here 
                if (type.match("multipart/*")) {
                    return stream; 
                }
            } catch (ParseException e) {
                // ignored....bad content type means we handle as a normal part 
            }
        }
        // ok, wrap this is a decoding stream if required 
        return MimeUtility.decode(stream, encoding);
    }
    

    public OutputStream getOutputStream() throws IOException {
        throw new UnknownServiceException();
    }

    public String getContentType() {
        try {
            return part.getContentType();
        } catch (MessagingException e) {
            return null;
        }
    }

    public String getName() {
        return "";
    }

    public synchronized MessageContext getMessageContext() {
        return new MessageContext(part);
    }
}
