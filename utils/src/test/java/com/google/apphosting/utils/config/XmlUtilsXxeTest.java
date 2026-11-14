/*
 * Copyright 2026 Google LLC
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

package com.google.apphosting.utils.config;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.w3c.dom.Document;

/** Verifies that {@link XmlUtils} does not resolve external entities (XXE). */
@RunWith(JUnit4.class)
public class XmlUtilsXxeTest {

  @Test
  public void parseXml_doesNotResolveExternalEntity() throws Exception {
    Path secret = Files.createTempFile("xxe-secret", ".txt");
    Files.writeString(secret, "TOP-SECRET-XXE-CANARY", UTF_8);
    String xml =
        "<?xml version=\"1.0\"?>\n"
            + "<!DOCTYPE q [<!ENTITY x SYSTEM \"file://"
            + secret.toAbsolutePath()
            + "\">]>\n"
            + "<queue-entries><name>&x;</name></queue-entries>";

    Document doc;
    try {
      doc = XmlUtils.parseXml(new ByteArrayInputStream(xml.getBytes(UTF_8)), "queue.xml");
    } catch (AppEngineConfigException e) {
      // Refusing the document outright is also an acceptable, safe outcome.
      return;
    }
    String name = doc.getElementsByTagName("name").item(0).getTextContent();
    assertThat(name).doesNotContain("TOP-SECRET-XXE-CANARY");
  }
}
