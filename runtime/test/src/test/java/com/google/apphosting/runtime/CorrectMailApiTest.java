/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.runtime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.lenientFormat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.appengine.api.ApiJarPath;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Bytes;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test that verifies that appengine-api.jar includes expected implementation of JavaMail api.
 *
 * <p>We expect this implementation to be Geronimo JavaMail. Our heuristic is to check for expected
 * UTF-8 constants in javax/mail/internet/MimeMultipart.class.
 */
@RunWith(JUnit4.class)
public final class CorrectMailApiTest {

  public static final byte[] SESSION_UTIL_REF =
      utfBytes("org/apache/geronimo/mail/util/SessionUtil");
  public static final byte[] PROC_UTIL_REF = utfBytes("com/sun/mail/util/PropUtil");

  public static final String MIME_MULTIPART = "javax/mail/internet/MimeMultipart.class";

  @Test
  public void testContainsGeronimoMail() throws IOException {
    ZipFile zip = new ZipFile(ApiJarPath.getFile());
    ZipEntry mimeMultipartClass = zip.getEntry(MIME_MULTIPART);
    ByteSource byteSource = new ZipEntryByteSource(zip, mimeMultipartClass);
    byte[] classBytes = byteSource.read();
    // There should be a reference to SessionUtil from org/apache/geronimo
    assertWithMessage("Reference to org/apache/geronimo/mail/util/SessionUtil not found")
        .that(Bytes.indexOf(classBytes, SESSION_UTIL_REF))
        .isNotEqualTo(-1);
    // There should NOT be a reference to PropUtil from com/sun/mail.
    assertWithMessage("Unexpected reference to com/sun/mail/util/PropUtil found")
        .that(Bytes.indexOf(classBytes, PROC_UTIL_REF))
        .isEqualTo(-1);
  }

  // Returns the UTF-8 encoding of `s`, including the length.
  private static byte[] utfBytes(String s) {
    try {
      return utfBytesOrException(s);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static byte[] utfBytesOrException(String s) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(baos)) {
      dos.writeUTF(s);
    }
    return baos.toByteArray();
  }

  private static final class ZipEntryByteSource extends ByteSource {

    private final ZipFile file;
    private final ZipEntry entry;

    ZipEntryByteSource(ZipFile file, ZipEntry entry) {
      this.file = checkNotNull(file);
      this.entry = checkNotNull(entry);
    }

    @Override
    public InputStream openStream() throws IOException {
      InputStream result = file.getInputStream(entry);
      if (result == null) {
        throw new FileNotFoundException(
            lenientFormat("entry %s not found in file %s", entry, file));
      }
      return result;
    }

    // TODO: implement size() to try calling entry.getSize()?

    @Override
    public String toString() {
      return "ZipFiles.asByteSource(" + file + ", " + entry + ")";
    }
  }
}
