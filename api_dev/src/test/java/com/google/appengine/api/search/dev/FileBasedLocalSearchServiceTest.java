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

package com.google.appengine.api.search.dev;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.Index;
import com.google.appengine.api.search.IndexSpec;
import com.google.appengine.api.search.SearchServiceFactory;
import com.google.appengine.api.search.StatusCode;
import com.google.appengine.tools.development.testing.LocalSearchServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link LocalSearchService} using file-based storage.
 *
 */
@RunWith(JUnit4.class)
public class FileBasedLocalSearchServiceTest {
  @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();

  @Test
  public void testFileBasedIndex() throws Exception {
    File genDir = tmpDir.getRoot();
    LocalSearchServiceTestConfig config1 =
        new LocalSearchServiceTestConfig()
            .setPersistent(true)
            .setStorageDirectory(genDir.toString());
    LocalServiceTestHelper localSearch1 = new LocalServiceTestHelper(config1);
    localSearch1.setUp();
    Index inboxIndex =
        SearchServiceFactory.getSearchService()
            .getIndex(IndexSpec.newBuilder().setName("inbox").build());
    Document d1 =
        Document.newBuilder().addField(Field.newBuilder().setName("foo").setText("bar")).build();
    assertThat(inboxIndex.put(d1).getResults().get(0).getCode()).isEqualTo(StatusCode.OK);
    assertThat(inboxIndex.search("foo:bar").getResults()).hasSize(1);
    assertThat(inboxIndex.search("foo:baz").getResults()).isEmpty();
    localSearch1.tearDown();
    LocalSearchServiceTestConfig config2 =
        new LocalSearchServiceTestConfig()
            .setPersistent(true)
            .setStorageDirectory(genDir.toString());
    LocalServiceTestHelper localSearch2 = new LocalServiceTestHelper(config2);
    localSearch2.setUp();
    assertThat(inboxIndex.search("foo:bar").getResults()).hasSize(1);
    assertThat(inboxIndex.search("foo:baz").getResults()).isEmpty();
    localSearch2.tearDown();
  }

  @Test
  public void testIndependentDocumentWithSameNameBackwardCompatibility() throws Exception {
    // golden_index.zip contains a golden index that was created by a version of the dev server
    // before this fix. It has two indexes:
    // index1: doc1(field:foo:bar),  doc2(field(foo,bar), field(jazz,12.5))
    // index2: doc2(field:foo2:bar), doc3(field(foo2,bar),field(jazz2,12.5))
    File genDir = tmpDir.getRoot();
    try (InputStream in = getClass().getResourceAsStream("golden_index.zip")) {
      unpackZip(in, genDir);
    }
    LocalSearchServiceTestConfig config =
        new LocalSearchServiceTestConfig()
            .setPersistent(true)
            .setStorageDirectory(genDir.toString());
    LocalServiceTestHelper localSearch = new LocalServiceTestHelper(config);
    localSearch.setUp();
    Index inboxIndex1 =
        SearchServiceFactory.getSearchService()
            .getIndex(IndexSpec.newBuilder().setName("inbox1").build());
    assertThat(inboxIndex1.search("foo:bar").getResults()).hasSize(2);
    assertThat(inboxIndex1.search("foo:baz").getResults()).isEmpty();
    Index inboxIndex2 =
        SearchServiceFactory.getSearchService()
            .getIndex(IndexSpec.newBuilder().setName("inbox2").build());
    assertThat(inboxIndex2.search("foo2:bar").getResults()).hasSize(2);
    assertThat(inboxIndex2.search("foo2:baz").getResults()).isEmpty();
    // The golden index has the problem of overwritten doc1 in index1, test that first
    Document doc = inboxIndex1.get("doc1");
    assertThat(doc).isNotNull();
    assertThat(doc.getFields("foo")).isNull();
    assertThat(doc.getFields("foo2")).isNotNull();
    doc = inboxIndex2.get("doc1");
    assertThat(doc).isNotNull();
    assertThat(doc.getFields("foo")).isNull();
    assertThat(doc.getFields("foo2")).isNotNull();
    // to fix this, we need to reindex doc1 in index1 and make sure it won't be overwrite index2's
    // doc1
    doc =
        Document.newBuilder()
            .setId("doc1")
            .addField(Field.newBuilder().setName("foo").setText("bar"))
            .build();
    assertThat(inboxIndex1.put(doc).getResults().get(0).getCode()).isEqualTo(StatusCode.OK);
    // now we should have two different versions of doc1 in two separate index
    doc = inboxIndex1.get("doc1");
    assertThat(doc).isNotNull();
    assertThat(doc.getFields("foo")).isNotNull();
    assertThat(doc.getFields("foo2")).isNull();
    doc = inboxIndex2.get("doc1");
    assertThat(doc).isNotNull();
    assertThat(doc.getFields("foo")).isNull();
    assertThat(doc.getFields("foo2")).isNotNull();
    localSearch.tearDown();
  }

  private static void unpackZip(InputStream in, File toDir) throws IOException {
    try (ZipInputStream zin = new ZipInputStream(in, UTF_8)) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        File f = new File(toDir, entry.getName());
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
          ByteStreams.copy(zin, out);
        }
        zin.closeEntry();
      }
    }
  }
}
