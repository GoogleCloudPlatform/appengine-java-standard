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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.appengine.api.search.proto.SearchServicePb;
import com.google.appengine.api.search.proto.SearchServicePb.IndexSpec.Consistency;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.apache.lucene.store.Directory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link LuceneDirectoryMap}.
 *
 */
@RunWith(JUnit4.class)
public class LuceneDirectoryMapTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private File rootDir;
  private LuceneDirectoryMap fileMap;
  private SearchServicePb.IndexSpec perDocIndexSpec;
  private SearchServicePb.IndexSpec globalIndexSpec;
  private SearchServicePb.IndexSpec specialCharNameParams;

  private SearchServicePb.IndexSpec makeIndexSpec(
      String name, String namespace, Consistency consistency) {
    return SearchServicePb.IndexSpec.newBuilder()
        .setConsistency(consistency)
        .setName(name)
        .setNamespace(namespace)
        .build();
  }

  @Before
  public void setUp() {
    rootDir = temporaryFolder.getRoot();
    fileMap = new LuceneDirectoryMap.FileBased(rootDir);
    perDocIndexSpec = makeIndexSpec("x", "", Consistency.PER_DOCUMENT);
    globalIndexSpec = makeIndexSpec("x", "", Consistency.GLOBAL);
    specialCharNameParams = makeIndexSpec("$my*index?", "", Consistency.GLOBAL);
  }

  @Test
  public void testGetDirectory() throws Exception {
    Directory a = fileMap.getDirectory("a.apphosting.com", perDocIndexSpec);
    Directory b = fileMap.getDirectory("a.apphosting.com", perDocIndexSpec);
    assertThat(b).isSameInstanceAs(a);
  }

  @Test
  public void testConsistencyMayNotChange() throws Exception {
    try {
      Directory unused = fileMap.getDirectory("a.apphosting.com", perDocIndexSpec);
      unused = fileMap.getDirectory("a.apphosting.com", globalIndexSpec);
    } catch (IOException e) {
      // Success.
    }
  }

  @Test
  public void testSpecialNames() throws Exception {
    Directory a = fileMap.getDirectory("a.apphosting.com", specialCharNameParams);
    assertThat(a).isNotNull();
  }

  public void checkIndexesList(
      List<SearchServicePb.IndexMetadata.Builder> res, String... expectedNames) {
    checkIndexesList(res, ImmutableList.copyOf(expectedNames), null);
  }

  private static void checkIndexesList(
      List<SearchServicePb.IndexMetadata.Builder> res,
      ImmutableList<String> expectedNames,
      @Nullable ImmutableList<String> expectedNamespaces) {
    if (expectedNamespaces != null) {
      Preconditions.checkArgument(expectedNames.size() == expectedNamespaces.size());
    }
    StringBuilder msg = new StringBuilder("Received indexes:");
    for (SearchServicePb.IndexMetadata.Builder element : res) {
      msg.append(" ").append(element.getIndexSpec().getName());
    }
    assertWithMessage(msg.toString()).that(res).hasSize(expectedNames.size());
    for (int i = 0; i < expectedNames.size(); i++) {
      String expectedName = expectedNames.get(i);
      String name = res.get(i).getIndexSpec().getName();
      assertWithMessage(msg.toString()).that(name).isEqualTo(expectedName);
      if (expectedNamespaces != null) {
        String expectedNamespace = expectedNamespaces.get(i);
        String namespace = res.get(i).getIndexSpec().getNamespace();
        assertWithMessage(msg.toString()).that(namespace).isEqualTo(expectedNamespace);
      }
    }
  }

  @Test
  public void testListIndexesIsolation() throws Exception {
    Directory unused = fileMap.getDirectory("app", perDocIndexSpec);

    checkIndexesList(
        fileMap.listIndexes("app", SearchServicePb.ListIndexesParams.getDefaultInstance()), "x");
    checkIndexesList(
        fileMap.listIndexes("other", SearchServicePb.ListIndexesParams.getDefaultInstance()));
  }

  @Test
  public void testListIndexesAllNamespacesIsolation() throws Exception {
    Directory unused = fileMap.getDirectory("app", perDocIndexSpec);

    checkIndexesList(
        fileMap.listIndexes(
            "app", SearchServicePb.ListIndexesParams.newBuilder().setAllNamespaces(true).build()),
        "x");
    checkIndexesList(
        fileMap.listIndexes(
            "other",
            SearchServicePb.ListIndexesParams.newBuilder().setAllNamespaces(true).build()));
  }

  @Test
  public void testListIndexesNamespaceIsolation() throws Exception {
    Directory unused =
        fileMap.getDirectory("app", makeIndexSpec("x", "", Consistency.PER_DOCUMENT));
    unused = fileMap.getDirectory("app", makeIndexSpec("x2", "ns", Consistency.GLOBAL));
    unused = fileMap.getDirectory("app", makeIndexSpec("x", "ns", Consistency.GLOBAL));

    checkIndexesList(
        fileMap.listIndexes("app", SearchServicePb.ListIndexesParams.getDefaultInstance()), "x");

    checkIndexesList(
        fileMap.listIndexes(
            "app", SearchServicePb.ListIndexesParams.newBuilder().setNamespace("ns").build()),
        "x",
        "x2");
  }

  @Test
  public void testListIndexesAllNamespaces() throws Exception {
    Directory unused =
        fileMap.getDirectory("app", makeIndexSpec("x", "", Consistency.PER_DOCUMENT));
    unused = fileMap.getDirectory("app", makeIndexSpec("x2", "ns", Consistency.GLOBAL));
    unused = fileMap.getDirectory("app", makeIndexSpec("x", "ns", Consistency.GLOBAL));

    checkIndexesList(
        fileMap.listIndexes(
            "app", SearchServicePb.ListIndexesParams.newBuilder().setAllNamespaces(true).build()),
        ImmutableList.of("x", "x", "x2"),
        ImmutableList.of("", "ns", "ns"));
  }

  @Test
  public void testListIndexesPersistence() throws Exception {
    List<SearchServicePb.IndexMetadata.Builder> res =
        fileMap.listIndexes("a", SearchServicePb.ListIndexesParams.getDefaultInstance());
    assertThat(res).isEmpty();

    Directory unused = fileMap.getDirectory("a", perDocIndexSpec);

    res = fileMap.listIndexes("a", SearchServicePb.ListIndexesParams.getDefaultInstance());
    assertThat(res).hasSize(1);
    assertThat(res.get(0).getIndexSpec().getName()).isEqualTo("x");
    assertThat(res.get(0).getIndexSpec().getConsistency()).isEqualTo(Consistency.PER_DOCUMENT);

    // Check if the data survives restart of appserver
    fileMap.close();
    fileMap = new LuceneDirectoryMap.FileBased(rootDir);

    res = fileMap.listIndexes("a", SearchServicePb.ListIndexesParams.getDefaultInstance());
    assertThat(res).hasSize(1);
    assertThat(res.get(0).getIndexSpec().getName()).isEqualTo("x");
    assertThat(res.get(0).getIndexSpec().getConsistency()).isEqualTo(Consistency.PER_DOCUMENT);
  }

  @Test
  public void testListIndexesPaging() throws Exception {
    Directory unused =
        fileMap.getDirectory("app", makeIndexSpec("x", "", Consistency.PER_DOCUMENT));
    unused = fileMap.getDirectory("app", makeIndexSpec("x2", "ns", Consistency.GLOBAL));
    unused = fileMap.getDirectory("app", makeIndexSpec("x", "ns", Consistency.GLOBAL));
    unused = fileMap.getDirectory("app", makeIndexSpec("a", "ns", Consistency.PER_DOCUMENT));
    unused = fileMap.getDirectory("app", makeIndexSpec("b", "ns", Consistency.GLOBAL));
    unused = fileMap.getDirectory("app", makeIndexSpec("cd", "ns", Consistency.PER_DOCUMENT));
    unused = fileMap.getDirectory("app", makeIndexSpec("ca", "ns", Consistency.GLOBAL));
    unused = fileMap.getDirectory("app", makeIndexSpec("c", "ns", Consistency.GLOBAL));
    unused = fileMap.getDirectory("app", makeIndexSpec("Z", "ns", Consistency.GLOBAL));

    checkIndexesList(
        fileMap.listIndexes("app", SearchServicePb.ListIndexesParams.getDefaultInstance()), "x");
    checkIndexesList(
        fileMap.listIndexes(
            "app", SearchServicePb.ListIndexesParams.newBuilder().setNamespace("ns").build()),
        "Z",
        "a",
        "b",
        "c",
        "ca",
        "cd",
        "x",
        "x2");
    checkIndexesList(
        fileMap.listIndexes(
            "app",
            SearchServicePb.ListIndexesParams.newBuilder()
                .setNamespace("ns")
                .setLimit(3)
                .setOffset(2)
                .build()),
        "b",
        "c",
        "ca");
    checkIndexesList(
        fileMap.listIndexes(
            "app",
            SearchServicePb.ListIndexesParams.newBuilder()
                .setNamespace("ns")
                .setLimit(3)
                .setOffset(6)
                .build()),
        "x",
        "x2");
    checkIndexesList(
        fileMap.listIndexes(
            "app",
            SearchServicePb.ListIndexesParams.newBuilder()
                .setNamespace("ns")
                .setLimit(3)
                .setOffset(10)
                .build()));
    checkIndexesList(
        fileMap.listIndexes(
            "app",
            SearchServicePb.ListIndexesParams.newBuilder()
                .setNamespace("ns")
                .setIndexNamePrefix("c")
                .build()),
        "c",
        "ca",
        "cd");
    checkIndexesList(
        fileMap.listIndexes(
            "app",
            SearchServicePb.ListIndexesParams.newBuilder()
                .setNamespace("ns")
                .setIndexNamePrefix("c")
                .setLimit(2)
                .build()),
        "c",
        "ca");
    checkIndexesList(
        fileMap.listIndexes(
            "app",
            SearchServicePb.ListIndexesParams.newBuilder()
                .setNamespace("ns")
                .setStartIndexName("b")
                .setLimit(2)
                .build()),
        "b",
        "c");
    checkIndexesList(
        fileMap.listIndexes(
            "app",
            SearchServicePb.ListIndexesParams.newBuilder()
                .setNamespace("ns")
                .setStartIndexName("b")
                .setIncludeStartIndex(false)
                .setLimit(2)
                .build()),
        "c",
        "ca");
  }
}
