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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.appengine.api.blobstore.BlobstoreServicePb.BlobstoreServiceError;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.DeleteBlobRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataResponse;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.appengine.tools.development.testing.FakeHttpServletRequest;
import com.google.appengine.tools.development.testing.FakeHttpServletResponse;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Tests for {@link BlobstoreServiceImpl}. */
@RunWith(JUnit4.class)
public class BlobstoreServiceImplTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> delegate;

  // Some magic is happening because if this is instead a variable, the created Date
  // has a different timezone.
  private static Date getExpectedCreationDate() {
    // Ugly construction to pass the milliseconds
    return new Date(new GregorianCalendar(2008, 11 - 1, 12, 10, 40, 00).getTimeInMillis() + 20);
  }

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(delegate);
  }

  @After
  public void tearDown() throws Exception {
    ApiProxy.setDelegate(null);
  }

  @Test
  public void testCreateUploadUrl_success() throws Exception {
    CreateUploadURLRequest requestProto =
        CreateUploadURLRequest.newBuilder()
            .setSuccessPath("success url")
            .setMaxUploadSizeBytes(1024 * 1024)
            .setMaxUploadSizePerBlobBytes(1024)
            .setGsBucketName("foo")
            .build();

    CreateUploadURLResponse responseProto =
        CreateUploadURLResponse.newBuilder().setUrl("encrypted url").build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("CreateUploadURL"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    UploadOptions options =
        UploadOptions.Builder.withMaxUploadSizeBytes(1024 * 1024)
            .maxUploadSizeBytesPerBlob(1024)
            .googleStorageBucketName("foo");
    String url = new BlobstoreServiceImpl().createUploadUrl("success url", options);
    assertThat(url).isEqualTo("encrypted url");
  }

  @Test
  public void testCreateUploadUrl_noSuccessURL() throws Exception {
    assertThrows(
        NullPointerException.class, () -> new BlobstoreServiceImpl().createUploadUrl(null));
  }

  @Test
  public void testCreateUploadUrl_urlTooLong() throws Exception {
    CreateUploadURLRequest requestProto =
        CreateUploadURLRequest.newBuilder().setSuccessPath("success url").build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("CreateUploadURL"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(BlobstoreServiceError.ErrorCode.URL_TOO_LONG_VALUE));

    assertThrows(
        IllegalArgumentException.class,
        () -> new BlobstoreServiceImpl().createUploadUrl("success url"));
  }

  @Test
  public void testDelete_empty() throws Exception {
    new BlobstoreServiceImpl().delete();

    verifyNoMoreInteractions(delegate);
  }

  @Test
  public void testDelete_success() throws Exception {
    DeleteBlobRequest requestProto = DeleteBlobRequest.newBuilder().addBlobKey("_foo").build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("DeleteBlob"),
            eq(requestProto.toByteArray())))
        .thenReturn(new byte[0]);

    new BlobstoreServiceImpl().delete(new BlobKey("_foo"));
  }

  @Test
  public void testDelete_multiSuccess() throws Exception {
    DeleteBlobRequest requestProto =
        DeleteBlobRequest.newBuilder().addBlobKey("_foo1").addBlobKey("_foo2").build();

    when(delegate.makeSyncCall(any(), any(), any(), any())).thenReturn(new byte[0]);

    new BlobstoreServiceImpl().delete(new BlobKey("_foo1"), new BlobKey("_foo2"));
    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("DeleteBlob"),
            eq(requestProto.toByteArray())))
        .thenReturn(new byte[0]);

    verify(delegate)
        .makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("DeleteBlob"),
            eq(requestProto.toByteArray()));
  }

  @Test
  public void testDelete_nullKey() throws Exception {
    assertThrows(
        NullPointerException.class, () -> new BlobstoreServiceImpl().delete((BlobKey) null));
  }

  @Test
  public void testDelete_null() throws Exception {
    assertThrows(
        NullPointerException.class, () -> new BlobstoreServiceImpl().delete((BlobKey) null));
  }

  @Test
  public void testServe_success() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    FakeHttpServletResponse resp = new FakeHttpServletResponse(req);
    new BlobstoreServiceImpl().serve(new BlobKey("_foo"), resp);
    assertThat(resp.getHeader(BlobstoreServiceImpl.SERVE_HEADER)).isEqualTo("_foo");
    assertThat(resp.getHeader(BlobstoreServiceImpl.BLOB_RANGE_HEADER)).isEqualTo(null);
    assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void testServe_byteRange() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    FakeHttpServletResponse resp = new FakeHttpServletResponse(req);
    new BlobstoreServiceImpl().serve(new BlobKey("_foo"), new ByteRange(5, 10), resp);
    assertThat(resp.getHeader(BlobstoreServiceImpl.SERVE_HEADER)).isEqualTo("_foo");
    assertThat(resp.getHeader(BlobstoreServiceImpl.BLOB_RANGE_HEADER)).isEqualTo("bytes=5-10");
    assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
  }

  @Test
  public void testServe_alreadyCommitted() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    FakeHttpServletResponse resp = new FakeHttpServletResponse(req);
    resp.flushBuffer();
    assertThrows(
        IllegalStateException.class,
        () -> new BlobstoreServiceImpl().serve(new BlobKey("_foo"), resp));
  }

  @Test
  public void testGetByteRange() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();

    assertThat(new BlobstoreServiceImpl().getByteRange(req)).isEqualTo(null);

    req.setHeader("range", "bytes=0-");
    assertThat(new BlobstoreServiceImpl().getByteRange(req)).isEqualTo(new ByteRange(0));

    req.setHeader("range", "bytes=0-10");
    assertThat(new BlobstoreServiceImpl().getByteRange(req)).isEqualTo(new ByteRange(0, 10));
  }

  @Test
  public void testGetUploadedBlobs_normal() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBKEY_ATTR,
        Collections.singletonMap("field", ImmutableList.of("blob")));
    @SuppressWarnings("deprecation")
    Map<String, BlobKey> blobs = new BlobstoreServiceImpl().getUploadedBlobs(req);
    assertThat(blobs).hasSize(1);
    assertThat(blobs.get("field")).isEqualTo(new BlobKey("blob"));
  }

  @Test
  public void testGetUploadBlobs_multiple() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    List<String> keyList = new ArrayList<>();
    keyList.add("blob");
    keyList.add("blob2");
    req.setAttribute(BlobstoreServiceImpl.UPLOADED_BLOBKEY_ATTR, ImmutableMap.of("field", keyList));
    Map<String, List<BlobKey>> blobs = new BlobstoreServiceImpl().getUploads(req);
    assertThat(blobs).hasSize(1);
    List<BlobKey> keys = blobs.get("field");
    assertThat(keys).containsExactly(new BlobKey("blob"), new BlobKey("blob2")).inOrder();
  }

  @Test
  public void testGetUploadedBlobs_noBlobs() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBKEY_ATTR, new HashMap<String, List<String>>());
    @SuppressWarnings("deprecation")
    Map<String, BlobKey> blobs = new BlobstoreServiceImpl().getUploadedBlobs(req);
    assertThat(blobs).isEmpty();
  }

  @Test
  public void testGetUploadedBlobs_noAttribute() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    assertThrows(
        IllegalStateException.class, () -> new BlobstoreServiceImpl().getUploadedBlobs(req));
  }

  @Test
  public void testGetBlobInfos_normal() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    Map<String, String> info = Maps.newHashMapWithExpectedSize(6);
    info.put("key", "blobkey");
    info.put("content-type", "text/plain");
    info.put("creation-date", "2008-11-12 10:40:00.020000");
    info.put("filename", "foo.txt");
    info.put("size", "25");
    info.put("md5-hash", "md5hash");
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR,
        Collections.singletonMap("field", ImmutableList.of(info)));
    Map<String, List<BlobInfo>> blobs = new BlobstoreServiceImpl().getBlobInfos(req);
    assertThat(blobs).hasSize(1);

    BlobInfo expected =
        new BlobInfo(
            new BlobKey("blobkey"),
            "text/plain",
            getExpectedCreationDate(),
            "foo.txt",
            25,
            "md5hash");
    assertThat(blobs.get("field")).containsExactly(expected);
  }

  @Test
  public void testGetBlobInfos_normal_withCloudStorage() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    Map<String, String> info = Maps.newHashMapWithExpectedSize(6);
    info.put("key", "blobkey");
    info.put("content-type", "text/plain");
    info.put("creation-date", "2008-11-12 10:40:00.020000");
    info.put("filename", "foo.txt");
    info.put("size", "25");
    info.put("md5-hash", "md5hash");
    info.put("gs-name", "/bucket/filename");
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR,
        Collections.singletonMap("field", ImmutableList.of(info)));
    Map<String, List<BlobInfo>> blobs = new BlobstoreServiceImpl().getBlobInfos(req);
    assertThat(blobs).hasSize(1);

    BlobInfo expected =
        new BlobInfo(
            new BlobKey("blobkey"),
            "text/plain",
            getExpectedCreationDate(),
            "foo.txt",
            25,
            "md5hash",
            "/bucket/filename");
    assertThat(blobs.get("field")).containsExactly(expected);
  }

  @Test
  public void testGetBlobInfos_multiple() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    Map<String, String> info1 = Maps.newHashMapWithExpectedSize(6);
    info1.put("key", "blobkey1");
    info1.put("content-type", "text/plain");
    info1.put("creation-date", "2008-11-12 10:40:00.020000");
    info1.put("filename", "foo.txt");
    info1.put("size", "25");
    info1.put("md5-hash", "md5hash1");

    Map<String, String> info2 = Maps.newHashMapWithExpectedSize(6);
    info2.put("key", "blobkey2");
    info2.put("content-type", "text/plain");
    info2.put("creation-date", "2008-11-12 10:40:00.020000");
    info2.put("filename", "bar.txt");
    info2.put("size", "26");
    info2.put("md5-hash", "md5hash2");

    Map<String, String> info3 = Maps.newHashMapWithExpectedSize(6);
    info3.put("key", "blobkey3");
    info3.put("content-type", "text/plain");
    info3.put("creation-date", "2008-11-12 10:40:00.020000");
    info3.put("filename", "zoo.txt");
    info3.put("size", "27");
    info3.put("md5-hash", "md5hash3");
    info3.put("gs-name", "/bucket/filename");

    List<Map<String, String>> infoList = new ArrayList<>(2);
    infoList.add(info1);
    infoList.add(info2);

    Map<String, List<Map<String, String>>> infoMap = Maps.newHashMapWithExpectedSize(2);
    infoMap.put("field1", infoList);
    infoMap.put("field2", ImmutableList.of(info3));
    req.setAttribute(BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR, infoMap);

    Map<String, List<BlobInfo>> blobs = new BlobstoreServiceImpl().getBlobInfos(req);
    assertThat(blobs).hasSize(2);

    BlobInfo expected1 =
        new BlobInfo(
            new BlobKey("blobkey1"),
            "text/plain",
            getExpectedCreationDate(),
            "foo.txt",
            25,
            "md5hash1");
    BlobInfo expected2 =
        new BlobInfo(
            new BlobKey("blobkey2"),
            "text/plain",
            getExpectedCreationDate(),
            "bar.txt",
            26,
            "md5hash2");
    BlobInfo expected3 =
        new BlobInfo(
            new BlobKey("blobkey3"),
            "text/plain",
            getExpectedCreationDate(),
            "zoo.txt",
            27,
            "md5hash3",
            "/bucket/filename");

    assertThat(blobs.get("field1")).containsExactly(expected1, expected2).inOrder();
    assertThat(blobs.get("field2")).containsExactly(expected3);
  }

  @Test
  public void testGetBlobInfos_noBlobs() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR,
        new HashMap<String, List<Map<String, String>>>());
    Map<String, List<BlobInfo>> blobs = new BlobstoreServiceImpl().getBlobInfos(req);
    assertThat(blobs).isEmpty();
  }

  @Test
  public void testGetBlobInfos_noAttribute() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    assertThrows(IllegalStateException.class, () -> new BlobstoreServiceImpl().getBlobInfos(req));
  }

  @Test
  public void testGetFileInfos_normal() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    Map<String, String> info = Maps.newHashMapWithExpectedSize(6);
    info.put("key", "blobkey");
    info.put("content-type", "text/plain");
    info.put("creation-date", "2008-11-12 10:40:00.020000");
    info.put("filename", "foo.txt");
    info.put("size", "25");
    info.put("md5-hash", "md5hash");
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR,
        Collections.singletonMap("field", ImmutableList.of(info)));
    Map<String, List<FileInfo>> infos = new BlobstoreServiceImpl().getFileInfos(req);
    assertThat(infos).hasSize(1);

    FileInfo expected =
        new FileInfo("text/plain", getExpectedCreationDate(), "foo.txt", 25, "md5hash", null);
    assertThat(infos.get("field")).containsExactly(expected);
  }

  @Test
  public void testGetFileInfos_normal_withCloudStorage() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    Map<String, String> info = Maps.newHashMapWithExpectedSize(6);
    info.put("key", "blobkey");
    info.put("content-type", "text/plain");
    info.put("creation-date", "2008-11-12 10:40:00.020000");
    info.put("filename", "foo.txt");
    info.put("size", "25");
    info.put("md5-hash", "md5hash");
    info.put("gs-name", "/gs/bucket/filename");
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR,
        Collections.singletonMap("field", ImmutableList.of(info)));
    Map<String, List<FileInfo>> infos = new BlobstoreServiceImpl().getFileInfos(req);
    assertThat(infos).hasSize(1);

    FileInfo expected =
        new FileInfo(
            "text/plain",
            getExpectedCreationDate(),
            "foo.txt",
            25,
            "md5hash",
            "/gs/bucket/filename");
    assertThat(infos.get("field")).containsExactly(expected);
  }

  @Test
  public void testGetFileInfos_multiple() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    Map<String, String> info1 = Maps.newHashMapWithExpectedSize(6);
    info1.put("key", "blobkey1");
    info1.put("content-type", "text/plain");
    info1.put("creation-date", "2008-11-12 10:40:00.020000");
    info1.put("filename", "foo.txt");
    info1.put("size", "25");
    info1.put("md5-hash", "md5hash1");

    Map<String, String> info2 = Maps.newHashMapWithExpectedSize(6);
    info2.put("key", "blobkey2");
    info2.put("content-type", "text/plain");
    info2.put("creation-date", "2008-11-12 10:40:00.020000");
    info2.put("filename", "bar.txt");
    info2.put("size", "26");
    info2.put("md5-hash", "md5hash2");

    Map<String, String> info3 = Maps.newHashMapWithExpectedSize(6);
    info3.put("key", "blobkey3");
    info3.put("content-type", "text/plain");
    info3.put("creation-date", "2008-11-12 10:40:00.020000");
    info3.put("filename", "zoo.txt");
    info3.put("size", "27");
    info3.put("md5-hash", "md5hash3");

    List<Map<String, String>> infoList = new ArrayList<>(2);
    infoList.add(info1);
    infoList.add(info2);

    Map<String, List<Map<String, String>>> infoMap = Maps.newHashMapWithExpectedSize(2);
    infoMap.put("field1", infoList);
    infoMap.put("field2", ImmutableList.of(info3));
    req.setAttribute(BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR, infoMap);

    Map<String, List<FileInfo>> infos = new BlobstoreServiceImpl().getFileInfos(req);
    assertThat(infos).hasSize(2);

    FileInfo expected1 =
        new FileInfo("text/plain", getExpectedCreationDate(), "foo.txt", 25, "md5hash1", null);
    FileInfo expected2 =
        new FileInfo("text/plain", getExpectedCreationDate(), "bar.txt", 26, "md5hash2", null);
    FileInfo expected3 =
        new FileInfo("text/plain", getExpectedCreationDate(), "zoo.txt", 27, "md5hash3", null);

    assertThat(infos.get("field1")).containsExactly(expected1, expected2).inOrder();
    assertThat(infos.get("field2")).containsExactly(expected3);
  }

  @Test
  public void testGetFileInfos_noBlobs() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    req.setAttribute(
        BlobstoreServiceImpl.UPLOADED_BLOBINFO_ATTR,
        new HashMap<String, List<Map<String, String>>>());
    Map<String, List<FileInfo>> infos = new BlobstoreServiceImpl().getFileInfos(req);
    assertThat(infos).isEmpty();
  }

  @Test
  public void testGetFileInfos_noAttribute() throws Exception {
    FakeHttpServletRequest req = new FakeHttpServletRequest();
    assertThrows(IllegalStateException.class, () -> new BlobstoreServiceImpl().getFileInfos(req));
  }

  @Test
  public void testFetchDataSuccess() throws Exception {
    // Simple request.
    FetchDataRequest requestProto =
        FetchDataRequest.newBuilder().setBlobKey("a blob").setStartIndex(0).setEndIndex(1).build();

    FetchDataResponse responseProto =
        FetchDataResponse.newBuilder().setData(ByteString.copyFromUtf8("result1")).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("FetchData"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    assertThat(new String(new BlobstoreServiceImpl().fetchData(new BlobKey("a blob"), 0, 1), UTF_8))
        .isEqualTo("result1");
  }

  @Test
  public void testFetchDataEmptyRange() throws Exception {
    // It's not for the service layer to determine empty strings for the same index.
    FetchDataRequest requestProto =
        FetchDataRequest.newBuilder().setBlobKey("a blob").setStartIndex(1).setEndIndex(1).build();

    FetchDataResponse responseProto =
        FetchDataResponse.newBuilder().setData(ByteString.copyFromUtf8("result2")).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("FetchData"),
            eq(requestProto.toByteArray())))
        .thenReturn(responseProto.toByteArray());

    assertThat(new String(new BlobstoreServiceImpl().fetchData(new BlobKey("a blob"), 1, 1), UTF_8))
        .isEqualTo("result2");
  }

  @Test
  public void testFetchMaximumSize() throws Exception {
    // Maximum size request.
    FetchDataRequest.Builder requestProto = FetchDataRequest.newBuilder();
    requestProto.setBlobKey("a blob");
    requestProto.setStartIndex(1);
    // end is inclusive, so starting offset of 1 means ending offset
    // MAX_BLOB_FETCH_SIZE
    requestProto.setEndIndex(BlobstoreServiceImpl.MAX_BLOB_FETCH_SIZE);

    FetchDataResponse responseProto =
        FetchDataResponse.newBuilder().setData(ByteString.copyFromUtf8("result3")).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("FetchData"),
            eq(requestProto.build().toByteArray())))
        .thenReturn(responseProto.toByteArray());

    assertThat(
            new String(
                new BlobstoreServiceImpl()
                    .fetchData(new BlobKey("a blob"), 1, BlobstoreServiceImpl.MAX_BLOB_FETCH_SIZE),
                UTF_8))
        .isEqualTo("result3");
  }

  @Test
  public void testFetchDataNull() throws Exception {
    assertThrows(
        NullPointerException.class, () -> new BlobstoreServiceImpl().fetchData(null, 0, 1));
  }

  @Test
  public void testFetchDataNegativeIndex() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BlobstoreServiceImpl().fetchData(new BlobKey("a blob"), -1, 1));
  }

  @Test
  public void testFetchDataInvertedSelection() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BlobstoreServiceImpl().fetchData(new BlobKey("a blob"), 2, 1));
  }

  @Test
  public void testFetchDataFetchSizeTooLarge() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BlobstoreServiceImpl()
                .fetchData(new BlobKey("a blob"), 0, BlobstoreServiceImpl.MAX_BLOB_FETCH_SIZE));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BlobstoreServiceImpl()
                .fetchData(new BlobKey("a blob"), 1, BlobstoreServiceImpl.MAX_BLOB_FETCH_SIZE + 2));
  }

  @Test
  public void testFetchDataPermissionDenied() throws Exception {
    // Simple request.
    FetchDataRequest requestProto =
        FetchDataRequest.newBuilder().setBlobKey("a blob").setStartIndex(0).setEndIndex(1).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("FetchData"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                BlobstoreServiceError.ErrorCode.PERMISSION_DENIED_VALUE));

    assertThrows(
        SecurityException.class,
        () -> new BlobstoreServiceImpl().fetchData(new BlobKey("a blob"), 0, 1));
  }

  @Test
  public void testFetchDataBlobNotFound() throws Exception {
    // Simple request.
    FetchDataRequest requestProto =
        FetchDataRequest.newBuilder().setBlobKey("a blob").setStartIndex(0).setEndIndex(1).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("FetchData"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                BlobstoreServiceError.ErrorCode.BLOB_NOT_FOUND_VALUE));

    assertThrows(
        IllegalArgumentException.class,
        () -> new BlobstoreServiceImpl().fetchData(new BlobKey("a blob"), 0, 1));
  }

  @Test
  public void testFetchDataOther() throws Exception {
    // Simple request.
    FetchDataRequest requestProto =
        FetchDataRequest.newBuilder().setBlobKey("a blob").setStartIndex(0).setEndIndex(1).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("FetchData"),
            eq(requestProto.toByteArray())))
        .thenThrow(
            new ApiProxy.ApplicationException(
                BlobstoreServiceError.ErrorCode.DATA_INDEX_OUT_OF_RANGE_VALUE));

    assertThrows(
        BlobstoreFailureException.class,
        () -> new BlobstoreServiceImpl().fetchData(new BlobKey("a blob"), 0, 1));
  }

  @Test
  public void testCreateEncodedGoogleStorageKey_success() throws Exception {
    String filename = "/gs/some_bucket/some_file";
    String encodedGsKey = "encoded-blob-key";
    CreateEncodedGoogleStorageKeyRequest request =
        CreateEncodedGoogleStorageKeyRequest.newBuilder().setFilename(filename).build();
    CreateEncodedGoogleStorageKeyResponse response =
        CreateEncodedGoogleStorageKeyResponse.newBuilder().setBlobKey(encodedGsKey).build();

    when(delegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(BlobstoreServiceImpl.PACKAGE),
            eq("CreateEncodedGoogleStorageKey"),
            eq(request.toByteArray())))
        .thenReturn(response.toByteArray());

    BlobKey key = new BlobstoreServiceImpl().createGsBlobKey(filename);

    assertThat(key.getKeyString()).isEqualTo(encodedGsKey);
  }

  @Test
  public void testCreateEncodedGoogleStorageKey_badFilename() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BlobstoreServiceImpl().createGsBlobKey("some_bucket/some_file"));
  }

  @Test
  public void testParseCreationDate() {
    Date expectedDate = getExpectedCreationDate();
    assertThat(BlobstoreServiceImpl.parseCreationDate("2008-11-12 10:40:00.020123"))
        .isEqualTo(expectedDate);
    assertThat(BlobstoreServiceImpl.parseCreationDate("2008-11-12 10:40:00.020"))
        .isEqualTo(expectedDate);
    assertThat(BlobstoreServiceImpl.parseCreationDate("           2008-11-12 10:40:00.020123 "))
        .isEqualTo(expectedDate);
    assertThat(BlobstoreServiceImpl.parseCreationDate("2008-11-12 10:40:00.02")).isEqualTo(null);
    assertThat(BlobstoreServiceImpl.parseCreationDate("2008-11-12")).isEqualTo(null);
    assertThat(BlobstoreServiceImpl.parseCreationDate("-11-12 10:40:00.020")).isEqualTo(null);
  }
}
