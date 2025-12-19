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

package com.google.appengine.api.blobstore;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.blobstore.dev.BlobInfoStorage;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Iterator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link BlobInfoFactory}.
 *
 */
@RunWith(JUnit4.class)
public class BlobInfoFactoryTest {

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
  private BlobInfoFactory blobInfoFactory;
  private BlobInfoStorage blobInfoStorage;

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    blobInfoFactory = new BlobInfoFactory();
    blobInfoStorage = new BlobInfoStorage();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
    blobInfoFactory = null;
    blobInfoStorage = null;
    // This is a hack for the Maven environment. Some tests fail because they find the
    // blobstore or datastore files left behind by previous tests. Probably we need a cleaner
    // solution, maybe integrated with LocalServiceTestHelper.tearDown().
    Path appengineGenerated = Paths.get("WEB-INF", "appengine-generated");
    try {
      MoreFiles.deleteRecursively(appengineGenerated, RecursiveDeleteOption.ALLOW_INSECURE);
    } catch (IOException e) {
      // OK, there is no WEB-INF/appengine-generated.
    }
  }

  @Test
  public void testLoadBlobInfo() {
    BlobInfo expected =
        new BlobInfo(new BlobKey("foo"), "text/html", new Date(), "file.txt", 42, "abcdef");
    blobInfoStorage.saveBlobInfo(expected);
    String origNamespace = NamespaceManager.get();

    try {
      // Current namespace change should have zero effect on BlobInfo
      NamespaceManager.set("random");
      BlobInfo actual = blobInfoFactory.loadBlobInfo(new BlobKey("foo"));
      assertThat(actual).isEqualTo(expected);
    } finally {
      NamespaceManager.set(origNamespace);
    }
  }

  @Test
  public void testLoadBlobInfoWithGsObjectName() {
    BlobInfo expected =
        new BlobInfo(
            new BlobKey("foo"),
            "text/html",
            new Date(),
            "file.txt",
            42,
            "abcdef",
            "/bucket/object");
    blobInfoStorage.saveBlobInfo(expected);
    BlobInfo actual = blobInfoFactory.loadBlobInfo(new BlobKey("foo"));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testQueryBlobInfos() {
    BlobInfo blob1 =
        new BlobInfo(new BlobKey("foo1"), "text/html", new Date(), "file.txt", 42, "abcdef");
    BlobInfo blob2 =
        new BlobInfo(new BlobKey("foo2"), "text/xml", new Date(), "file.xml", 50, "123456");
    BlobInfo blob3 =
        new BlobInfo(
            new BlobKey("foo3"),
            "image/gif",
            new Date(),
            "file.png",
            2400,
            "a1b2c3",
            "/bucket/obj");

    blobInfoStorage.saveBlobInfo(blob2);
    blobInfoStorage.saveBlobInfo(blob1);
    blobInfoStorage.saveBlobInfo(blob3);

    Iterator<BlobInfo> it = blobInfoFactory.queryBlobInfos();
    assertThat(it.next()).isEqualTo(blob1);
    assertThat(it.next()).isEqualTo(blob2);
    assertThat(it.next()).isEqualTo(blob3);
    assertThat(it.hasNext()).isFalse();
  }

  @Test
  public void testQueryBlobInfosAfter() {
    BlobInfo blob1 =
        new BlobInfo(new BlobKey("foo1"), "text/html", new Date(), "file.txt", 42, "abcdef");
    BlobInfo blob2 =
        new BlobInfo(new BlobKey("foo2"), "text/xml", new Date(), "file.xml", 50, "123456");
    BlobInfo blob3 =
        new BlobInfo(
            new BlobKey("foo3"),
            "image/gif",
            new Date(),
            "file.png",
            2400,
            "a1b2c3",
            "/bucket/obj");

    blobInfoStorage.saveBlobInfo(blob2);
    blobInfoStorage.saveBlobInfo(blob1);
    blobInfoStorage.saveBlobInfo(blob3);
    String origNamespace = NamespaceManager.get();

    try {
      // Current namespace change should have zero effect on BlobInfo
      NamespaceManager.set("random");
      Iterator<BlobInfo> it = blobInfoFactory.queryBlobInfosAfter(new BlobKey("foo1"));
      assertThat(it.next()).isEqualTo(blob2);
      assertThat(it.next()).isEqualTo(blob3);
      assertThat(it.hasNext()).isFalse();
    } finally {
      NamespaceManager.set(origNamespace);
    }
  }

  @Test
  public void testQueryBlobInfosAfterAll() {
    BlobInfo blob1 =
        new BlobInfo(new BlobKey("foo1"), "text/html", new Date(), "file.txt", 42, "abcdef");
    BlobInfo blob2 =
        new BlobInfo(new BlobKey("foo2"), "text/xml", new Date(), "file.xml", 50, "123456");
    BlobInfo blob3 =
        new BlobInfo(
            new BlobKey("foo3"),
            "image/gif",
            new Date(),
            "file.png",
            2400,
            "a1b2c3",
            "/bucket/obj");

    blobInfoStorage.saveBlobInfo(blob2);
    blobInfoStorage.saveBlobInfo(blob1);
    blobInfoStorage.saveBlobInfo(blob3);

    Iterator<BlobInfo> it = blobInfoFactory.queryBlobInfosAfter(new BlobKey("foo3"));
    assertThat(it.hasNext()).isFalse();
  }
}
