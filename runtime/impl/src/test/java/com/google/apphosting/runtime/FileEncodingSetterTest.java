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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for FileEncodingSetterTest. */
@RunWith(JUnit4.class)
public class FileEncodingSetterTest {
  @Before
  public void setUp() {
    assumeTrue(Runtime.version().feature() <= 25);
    // Set to an unexpected value, just to ensure overwriting works:
    FileEncodingSetter.overwriteDefaultCharset(UTF_16);
  }

  @Test
  public void setUtf8() throws IOException {
    Map<String, String> sysProps = new HashMap<>();
    sysProps.put("appengine.file.encoding", "UTF-8");
    FileEncodingSetter.set(sysProps);
    assertThat(sysProps).containsEntry("file.encoding", "UTF-8");
    assertThat(Charset.defaultCharset()).isEqualTo(UTF_8);
  }

  @Test
  public void setUsAscii() throws IOException {
    Map<String, String> sysProps = new HashMap<>();
    sysProps.put("appengine.file.encoding", "US-ASCII");
    FileEncodingSetter.set(sysProps);
    assertThat(sysProps).containsEntry("file.encoding", "US-ASCII");
    assertThat(Charset.defaultCharset()).isEqualTo(US_ASCII);
  }

  @Test
  public void setAnsi() throws IOException {
    Map<String, String> sysProps = new HashMap<>();
    sysProps.put("appengine.file.encoding", "ANSI_X3.4-1968");
    FileEncodingSetter.set(sysProps);
    assertThat(sysProps).containsEntry("file.encoding", "ANSI_X3.4-1968");
    assertThat(Charset.defaultCharset()).isEqualTo(US_ASCII);
  }

  @Test
  public void setBogus() throws IOException {
    Map<String, String> sysProps = new HashMap<>();
    sysProps.put("appengine.file.encoding", "bogus-charset");
    FileEncodingSetter.set(sysProps);
    assertThat(sysProps).doesNotContainKey("file.encoding");
    assertThat(Charset.defaultCharset()).isEqualTo(UTF_16);
  }
}
