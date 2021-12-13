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

package com.google.appengine.api.blobstore.dev;

import static com.google.common.io.BaseEncoding.base64Url;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.blobstore.BlobstoreServicePb.BlobstoreServiceError;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataRequest;
import com.google.appengine.api.blobstore.UploadOptions;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.apphosting.api.ApiProxy;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Unit tests for {@link LocalBlobstoreService}.
 *
 */
@RunWith(Parameterized.class)
public class LocalBlobstoreServiceTest {

  @Parameters
  public static Object[] data() {
    return new Object[] {true, false};
  }

  private BlobstoreService blobstoreService;
  private BlobUploadSessionStorage blobUploadSessionStorage;
  private final LocalServiceTestHelper helper;

  public LocalBlobstoreServiceTest(boolean noStorage) {
    helper =
        new LocalServiceTestHelper(new LocalBlobstoreServiceTestConfig().setNoStorage(noStorage));
  }

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    blobUploadSessionStorage = new BlobUploadSessionStorage();
  }

  @After
  public void tearDown() throws Exception {
    helper.tearDown();
    blobstoreService = null;
    blobUploadSessionStorage = null;
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

  public void doFetchDataTest(long startIndex, long endIndex, String expectedData)
      throws IOException {
    BlobKey blobKey = new BlobKey("foo");
    blobstoreService.delete(blobKey);

    BlobInfo blobInfo = new BlobInfo(blobKey, "text/plain", new Date(), "test.txt", 6);
    new BlobInfoStorage().saveBlobInfo(blobInfo);

    BlobStorage blobStorage = BlobStorageFactory.getBlobStorage();
    try (OutputStream output = blobStorage.storeBlob(blobKey)) {
      output.write("abcdef".getBytes(UTF_8));
    }

    byte[] response = blobstoreService.fetchData(blobKey, startIndex, endIndex);
    assertThat(new String(response, UTF_8)).isEqualTo(expectedData);
  }

  public void doFetchDataErrorTest(
      String blobKeyString, long startIndex, long endIndex, BlobstoreServiceError.ErrorCode error)
      throws IOException {
    BlobKey blobKey = new BlobKey(blobKeyString);
    blobstoreService.delete(blobKey);

    BlobInfo blobInfo = new BlobInfo(blobKey, "text/plain", new Date(), "test.txt", 6);
    new BlobInfoStorage().saveBlobInfo(blobInfo);

    BlobStorage blobStorage = BlobStorageFactory.getBlobStorage();
    try (OutputStream output = blobStorage.storeBlob(blobKey)) {
      output.write("abcdef".getBytes(UTF_8));
    }

    FetchDataRequest request =
        FetchDataRequest.newBuilder()
            .setBlobKey("blob")
            .setStartIndex(startIndex)
            .setEndIndex(endIndex)
            .build();

    LocalBlobstoreService blobstore = LocalBlobstoreServiceTestConfig.getLocalBlobstoreService();

    ApiProxy.ApplicationException ex =
        assertThrows(ApiProxy.ApplicationException.class, () -> blobstore.fetchData(null, request));
    assertThat(ex.getApplicationError()).isEqualTo(error.getNumber());
  }

  @Test
  public void testCreateUploadURL() {
    String url = blobstoreService.createUploadUrl("/foo");
    int index = url.indexOf(LocalBlobstoreService.UPLOAD_URL_PREFIX);
    assertThat(index).isAtLeast(0);
    String hostPortPrefix = url.substring(0, index);
    assertThat(hostPortPrefix).isEqualTo("http://localhost:8080");

    String sessionId = url.substring(index + LocalBlobstoreService.UPLOAD_URL_PREFIX.length());
    BlobUploadSession session = blobUploadSessionStorage.loadSession(sessionId);
    assertThat(session.getSuccessPath()).isEqualTo("/foo");
    assertThat(session.hasMaxUploadSizeBytes()).isFalse();
    assertThat(session.hasMaxUploadSizeBytesPerBlob()).isFalse();
    assertThat(session.hasGoogleStorageBucketName()).isFalse();
  }

  @Test
  public void testCreateUploadURLWithOptions() {
    UploadOptions options =
        UploadOptions.Builder.withMaxUploadSizeBytes(100)
            .maxUploadSizeBytesPerBlob(200)
            .googleStorageBucketName("my_bucket");

    String url = blobstoreService.createUploadUrl("/foo", options);
    int index = url.indexOf(LocalBlobstoreService.UPLOAD_URL_PREFIX);
    assertThat(index).isAtLeast(0);
    String hostPortPrefix = url.substring(0, index);
    assertThat(hostPortPrefix).isEqualTo("http://localhost:8080");

    String sessionId = url.substring(index + LocalBlobstoreService.UPLOAD_URL_PREFIX.length());
    BlobUploadSession session = blobUploadSessionStorage.loadSession(sessionId);
    assertThat(session.getSuccessPath()).isEqualTo("/foo");
    assertThat(session.getMaxUploadSizeBytes()).isEqualTo(100);
    assertThat(session.getMaxUploadSizeBytesPerBlob()).isEqualTo(200);
    assertThat(session.getGoogleStorageBucketName()).isEqualTo("my_bucket");
  }

  @Test
  public void testDelete() throws Exception {
    BlobKey blobKey = new BlobKey("deleteFoo");

    BlobInfo blobInfo = new BlobInfo(blobKey, "text/plain", new Date(), "test.txt", 6);
    new BlobInfoStorage().saveBlobInfo(blobInfo);

    blobstoreService.delete(blobKey);
    BlobStorage blobStorage = BlobStorageFactory.getBlobStorage();
    assertThat(blobStorage.hasBlob(blobKey)).isFalse();
    blobStorage.storeBlob(blobKey).close();
    assertThat(blobStorage.hasBlob(blobKey)).isTrue();

    blobstoreService.delete(blobKey);
    assertThat(blobStorage.hasBlob(blobKey)).isFalse();
  }

  @Test
  public void testFetchDataSuccess() throws Exception {
    // Normal fetches.
    doFetchDataTest(0, 0, "a");
    doFetchDataTest(0, 1, "ab");
    doFetchDataTest(1, 1, "b");
    doFetchDataTest(1, 2, "bc");
    doFetchDataTest(5, 5, "f");
    doFetchDataTest(4, 5, "ef");

    // Partial fetches.
    doFetchDataTest(5, 6, "f");
    doFetchDataTest(4, 6, "ef");
    doFetchDataTest(0, 6, "abcdef");
    doFetchDataTest(0, BlobstoreService.MAX_BLOB_FETCH_SIZE - 1, "abcdef");
    doFetchDataTest(1, BlobstoreService.MAX_BLOB_FETCH_SIZE, "bcdef");

    // Empty fetches.
    doFetchDataTest(6, 6, "");
    doFetchDataTest(7, 7, "");
  }

  @Test
  public void testFetchDataNegativeIndexes() throws Exception {
    doFetchDataErrorTest("blob", -1, 1, BlobstoreServiceError.ErrorCode.DATA_INDEX_OUT_OF_RANGE);
  }

  @Test
  public void testFetchDataInvertedIndexes() throws Exception {
    doFetchDataErrorTest("blob", 2, 1, BlobstoreServiceError.ErrorCode.DATA_INDEX_OUT_OF_RANGE);
  }

  @Test
  public void testFetchDataBlobNotFound() throws Exception {
    doFetchDataErrorTest("no-blob", 0, 1, BlobstoreServiceError.ErrorCode.BLOB_NOT_FOUND);

    // Takes precedence over duplicate indexes.
    doFetchDataErrorTest("no-blob", 1, 1, BlobstoreServiceError.ErrorCode.BLOB_NOT_FOUND);
  }

  @Test
  public void testFetchDataTooLarge() throws Exception {
    doFetchDataErrorTest(
        "blob",
        0,
        BlobstoreService.MAX_BLOB_FETCH_SIZE,
        BlobstoreServiceError.ErrorCode.BLOB_FETCH_SIZE_TOO_LARGE);

    doFetchDataErrorTest(
        "blob",
        1,
        BlobstoreService.MAX_BLOB_FETCH_SIZE + 1,
        BlobstoreServiceError.ErrorCode.BLOB_FETCH_SIZE_TOO_LARGE);
  }

  @Test
  public void testFetchDataIOException() throws Exception {
    BlobKey blobKey = new BlobKey("blob");
    blobstoreService.delete(blobKey);

    BlobInfo blobInfo = new BlobInfo(blobKey, "text/plain", new Date(), "test.txt", 6);
    new BlobInfoStorage().saveBlobInfo(blobInfo);

    // Do not create the actual file causing IOException when attempting to read.

    FetchDataRequest request =
        FetchDataRequest.newBuilder().setBlobKey("blob").setStartIndex(0).setEndIndex(10).build();

    LocalBlobstoreService blobstore = LocalBlobstoreServiceTestConfig.getLocalBlobstoreService();

    ApiProxy.ApplicationException ex =
        assertThrows(ApiProxy.ApplicationException.class, () -> blobstore.fetchData(null, request));
    assertThat(ex.getApplicationError())
        .isEqualTo(BlobstoreServiceError.ErrorCode.INTERNAL_ERROR_VALUE);
  }

  @Test
  public void testCreateEncodedGsKey() throws Exception {
    String filename = "/gs/some_bucket/some_file";
    CreateEncodedGoogleStorageKeyRequest request =
        CreateEncodedGoogleStorageKeyRequest.newBuilder().setFilename(filename).build();
    LocalBlobstoreService blobstore = LocalBlobstoreServiceTestConfig.getLocalBlobstoreService();

    CreateEncodedGoogleStorageKeyResponse response =
        blobstore.createEncodedGoogleStorageKey(null, request);

    String key = response.getBlobKey();

    assertThat(key).startsWith(LocalBlobstoreService.GOOGLE_STORAGE_KEY_PREFIX);
    String encodedKey = key.substring(LocalBlobstoreService.GOOGLE_STORAGE_KEY_PREFIX.length());

    byte[] decodedKey = base64Url().decode(encodedKey);
    assertThat(new String(decodedKey, UTF_8)).isEqualTo(filename);
  }
}
