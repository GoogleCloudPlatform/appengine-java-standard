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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

import javax.mail.MessagingException;

import junit.framework.TestCase;

/**
 * @version $Rev: 467553 $ $Date: 2006-10-24 23:01:51 -0500 (Tue, 24 Oct 2006) $
 */
public class PreencodedMimeBodyPartTest extends TestCase {

     public void testEncoding() throws Exception {
         PreencodedMimeBodyPart part = new PreencodedMimeBodyPart("base64");
         assertEquals("base64", part.getEncoding());
     }

     public void testUpdateHeaders() throws Exception {
         TestBodyPart part = new TestBodyPart("base64");

         part.updateHeaders();

         assertEquals("base64", part.getHeader("Content-Transfer-Encoding", null));
     }

     public void testWriteTo() throws Exception {
         PreencodedMimeBodyPart part = new PreencodedMimeBodyPart("binary");

         byte[] content = new byte[] { 81, 82, 83, 84, 85, 86 };

         part.setContent(new String(content, "UTF-8"), "text/plain; charset=\"UTF-8\"");

         ByteArrayOutputStream out = new ByteArrayOutputStream();

         part.writeTo(out);

         byte[] data = out.toByteArray();

         // we need to scan forward to the actual content and verify it has been written without additional
         // encoding.  Our marker is a "crlfcrlf" sequence.


         for (int i = 0; i < data.length; i++) {
             if (data[i] == '\r') {
                 if (data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                     for (int j = 0; j < content.length; j++) {
                         assertEquals(data[i + 4 + j], content[j]);
                     }

                 }
             }
         }
     }


     public class TestBodyPart extends PreencodedMimeBodyPart {

         public TestBodyPart(String encoding) {
             super(encoding);
         }

         public void updateHeaders() throws MessagingException {
             super.updateHeaders();
         }
     }
}


