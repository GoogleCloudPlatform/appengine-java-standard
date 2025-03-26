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

import static java.util.Objects.requireNonNull;

import com.google.appengine.api.blobstore.BlobstoreServicePb.BlobstoreServiceError;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.DeleteBlobRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataResponse;
import com.google.apphosting.api.ApiProxy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

/**
 * {@code BlobstoreServiceImpl} is an implementation of {@link BlobstoreService} that makes API
 * calls to {@link ApiProxy}.
 *
 */
class BlobstoreServiceImpl implements BlobstoreService {
  static final String PACKAGE = "blobstore";
  static final String SERVE_HEADER = "X-AppEngine-BlobKey";
  static final String UPLOADED_BLOBKEY_ATTR = "com.google.appengine.api.blobstore.upload.blobkeys";
  static final String UPLOADED_BLOBINFO_ATTR =
      "com.google.appengine.api.blobstore.upload.blobinfos";
  static final String BLOB_RANGE_HEADER = "X-AppEngine-BlobRange";
  static final String CREATION_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

  @Override
  public String createUploadUrl(String successPath) {
    return createUploadUrl(successPath, UploadOptions.Builder.withDefaults());
  }

  @Override
  public String createUploadUrl(String successPath, UploadOptions uploadOptions) {
    if (successPath == null) {
      throw new NullPointerException("Success path must not be null.");
    }

    CreateUploadURLRequest.Builder request =
        CreateUploadURLRequest.newBuilder().setSuccessPath(successPath);

    if (uploadOptions.hasMaxUploadSizeBytesPerBlob()) {
      request.setMaxUploadSizePerBlobBytes(uploadOptions.getMaxUploadSizeBytesPerBlob());
    }

    if (uploadOptions.hasMaxUploadSizeBytes()) {
      request.setMaxUploadSizeBytes(uploadOptions.getMaxUploadSizeBytes());
    }

    if (uploadOptions.hasGoogleStorageBucketName()) {
      request.setGsBucketName(uploadOptions.getGoogleStorageBucketName());
    }

    byte[] responseBytes;
    try {
      responseBytes =
          ApiProxy.makeSyncCall(PACKAGE, "CreateUploadURL", request.build().toByteArray());
    } catch (ApiProxy.ApplicationException ex) {
      switch (BlobstoreServiceError.ErrorCode.forNumber(ex.getApplicationError())) {
        case URL_TOO_LONG:
          throw new IllegalArgumentException("The resulting URL was too long.");
        case INTERNAL_ERROR:
          throw new BlobstoreFailureException("An internal blobstore error occurred.");
        default:
          throw new BlobstoreFailureException("An unexpected error occurred.", ex);
      }
    }

    try {
      CreateUploadURLResponse response =
          CreateUploadURLResponse.parseFrom(
              responseBytes, ExtensionRegistry.getEmptyRegistry());
      if (!response.isInitialized()) {
        throw new BlobstoreFailureException("Could not parse CreateUploadURLResponse");
      }
      return response.getUrl();

    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public void serve(BlobKey blobKey, HttpServletResponse response) {
    serve(blobKey, (ByteRange) null, response);
  }

  @Override
  public void serve(BlobKey blobKey, String rangeHeader, HttpServletResponse response) {
    serve(blobKey, ByteRange.parse(rangeHeader), response);
  }

  @Override
  public void serve(BlobKey blobKey, @Nullable ByteRange byteRange, HttpServletResponse response) {
    if (response.isCommitted()) {
      throw new IllegalStateException("Response was already committed.");
    }

    // N.B.(gregwilkins): Content-Length is not needed by blobstore and causes error in jetty94
    response.setContentLength(-1);

    // N.B.: Blobstore serving is only enabled for 200 responses.
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(SERVE_HEADER, blobKey.getKeyString());
    if (byteRange != null) {
      response.setHeader(BLOB_RANGE_HEADER, byteRange.toString());
    }
  }

  @Override
  public @Nullable ByteRange getByteRange(HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Enumeration<String> rangeHeaders = request.getHeaders("range");
    if (!rangeHeaders.hasMoreElements()) {
      return null;
    }

    String rangeHeader = rangeHeaders.nextElement();
    if (rangeHeaders.hasMoreElements()) {
      throw new UnsupportedRangeFormatException("Cannot accept multiple range headers.");
    }

    return ByteRange.parse(rangeHeader);
  }

  @Override
  public void delete(BlobKey... blobKeys) {
    DeleteBlobRequest.Builder request = DeleteBlobRequest.newBuilder();
    for (BlobKey blobKey : blobKeys) {
      request.addBlobKey(blobKey.getKeyString());
    }

    if (request.getBlobKeyCount() == 0) {
      return;
    }

    try {
      ApiProxy.makeSyncCall(PACKAGE, "DeleteBlob", request.build().toByteArray());
    } catch (ApiProxy.ApplicationException ex) {
      switch (BlobstoreServiceError.ErrorCode.forNumber(ex.getApplicationError())) {
        case INTERNAL_ERROR:
          throw new BlobstoreFailureException("An internal blobstore error occurred.");
        default:
          throw new BlobstoreFailureException("An unexpected error occurred.", ex);
      }
    }
  }

  @Override
  @Deprecated
  public Map<String, BlobKey> getUploadedBlobs(HttpServletRequest request) {
    Map<String, List<BlobKey>> blobKeys = getUploads(request);
    Map<String, BlobKey> result = Maps.newHashMapWithExpectedSize(blobKeys.size());

    for (Map.Entry<String, List<BlobKey>> entry : blobKeys.entrySet()) {
      // In throery it is not possible for the value for an entry to be empty,
      // and the following check is simply defensive against a possible future
      // change to that assumption.
      if (!entry.getValue().isEmpty()) {
        result.put(entry.getKey(), entry.getValue().get(0));
      }
    }
    return result;
  }

  @Override
  public Map<String, List<BlobKey>> getUploads(HttpServletRequest request) {
    // N.B.: We're storing strings instead of BlobKey
    // objects in the request attributes to avoid conflicts between
    // the BlobKey classes loaded by the two classloaders in the
    // DevAppServer.  We convert back to BlobKey objects here.
    @SuppressWarnings("unchecked")
    Map<String, List<String>> attributes =
        (Map<String, List<String>>) request.getAttribute(UPLOADED_BLOBKEY_ATTR);
    if (attributes == null) {
      throw new IllegalStateException("Must be called from a blob upload callback request.");
    }
    Map<String, List<BlobKey>> blobKeys = Maps.newHashMapWithExpectedSize(attributes.size());
    for (Map.Entry<String, List<String>> attr : attributes.entrySet()) {
      List<BlobKey> blobs = new ArrayList<>(attr.getValue().size());
      for (String key : attr.getValue()) {
        blobs.add(new BlobKey(key));
      }
      blobKeys.put(attr.getKey(), blobs);
    }
    return blobKeys;
  }

  @Override
  public Map<String, List<BlobInfo>> getBlobInfos(HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, String>>> attributes =
        (Map<String, List<Map<String, String>>>) request.getAttribute(UPLOADED_BLOBINFO_ATTR);
    if (attributes == null) {
      throw new IllegalStateException("Must be called from a blob upload callback request.");
    }
    Map<String, List<BlobInfo>> blobInfos = Maps.newHashMapWithExpectedSize(attributes.size());
    for (Map.Entry<String, List<Map<String, String>>> attr : attributes.entrySet()) {
      List<BlobInfo> blobs = new ArrayList<>(attr.getValue().size());
      for (Map<String, String> info : attr.getValue()) {
        BlobKey key = new BlobKey(requireNonNull(info.get("key"), "Missing key attribute"));
        String contentType =
            requireNonNull(info.get("content-type"), "Missing content-type attribute");
        String creationDateAttribute =
            requireNonNull(info.get("creation-date"), "Missing creation-date attribute");
        Date creationDate =
            requireNonNull(
                parseCreationDate(creationDateAttribute),
                () -> "Bad creation-date attribute: " + creationDateAttribute);
        String filename = requireNonNull(info.get("filename"), "Missing filename attribute");
        int size = Integer.parseInt(requireNonNull(info.get("size"), "Missing size attribute"));
        String md5Hash = requireNonNull(info.get("md5-hash"), "Missing md5-hash attribute");
        String gsObjectName = info.get("gs-name");
        blobs.add(
            new BlobInfo(key, contentType, creationDate, filename, size, md5Hash, gsObjectName));
      }
      blobInfos.put(attr.getKey(), blobs);
    }
    return blobInfos;
  }

  @Override
  public Map<String, List<FileInfo>> getFileInfos(HttpServletRequest request) {
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, String>>> attributes =
        (Map<String, List<Map<String, String>>>) request.getAttribute(UPLOADED_BLOBINFO_ATTR);
    if (attributes == null) {
      throw new IllegalStateException("Must be called from a blob upload callback request.");
    }
    Map<String, List<FileInfo>> fileInfos = Maps.newHashMapWithExpectedSize(attributes.size());
    for (Map.Entry<String, List<Map<String, String>>> attr : attributes.entrySet()) {
      List<FileInfo> files = new ArrayList<>(attr.getValue().size());
      for (Map<String, String> info : attr.getValue()) {
        String contentType =
            requireNonNull(info.get("content-type"), "Missing content-type attribute");
        String creationDateAttribute =
            requireNonNull(info.get("creation-date"), "Missing creation-date attribute");
        Date creationDate =
            requireNonNull(
                parseCreationDate(creationDateAttribute),
                () -> "Invalid creation-date attribute " + creationDateAttribute);
        String filename = requireNonNull(info.get("filename"), "Missing filename attribute");
        long size = Long.parseLong(requireNonNull(info.get("size"), "Missing size attribute"));
        String md5Hash = requireNonNull(info.get("md5-hash"), "Missing md5-hash attribute");
        String gsObjectName = info.getOrDefault("gs-name", null);
        files.add(new FileInfo(contentType, creationDate, filename, size, md5Hash, gsObjectName));
      }
      fileInfos.put(attr.getKey(), files);
    }
    return fileInfos;
  }

  @VisibleForTesting
  protected static @Nullable Date parseCreationDate(String date) {
    Date creationDate = null;
    try {
      date = date.trim().substring(0, CREATION_DATE_FORMAT.length());
      SimpleDateFormat dateFormat = new SimpleDateFormat(CREATION_DATE_FORMAT);
      // Enforce strict adherence to the format
      dateFormat.setLenient(false);
      creationDate = dateFormat.parse(date);
    } catch (IndexOutOfBoundsException e) {
      // This should never happen. We got a date that is shorter than the format.
      // TODO: add log
    } catch (ParseException e) {
      // This should never happen. We got a date that does not match the format.
      // TODO: add log
    }
    return creationDate;
  }

  @Override
  public byte[] fetchData(BlobKey blobKey, long startIndex, long endIndex) {
    if (startIndex < 0) {
      throw new IllegalArgumentException("Start index must be >= 0.");
    }

    if (endIndex < startIndex) {
      throw new IllegalArgumentException("End index must be >= startIndex.");
    }

    // +1 since endIndex is inclusive
    long fetchSize = endIndex - startIndex + 1;
    if (fetchSize > MAX_BLOB_FETCH_SIZE) {
      throw new IllegalArgumentException(
          "Blob fetch size "
              + fetchSize
              + " is larger "
              + "than maximum size "
              + MAX_BLOB_FETCH_SIZE
              + " bytes.");
    }

    FetchDataRequest request =
        FetchDataRequest.newBuilder()
            .setBlobKey(blobKey.getKeyString())
            .setStartIndex(startIndex)
            .setEndIndex(endIndex)
            .build();

    byte[] responseBytes;
    try {
      responseBytes = ApiProxy.makeSyncCall(PACKAGE, "FetchData", request.toByteArray());
    } catch (ApiProxy.ApplicationException ex) {
      switch (BlobstoreServiceError.ErrorCode.forNumber(ex.getApplicationError())) {
        case PERMISSION_DENIED:
          throw new SecurityException("This application does not have access to that blob.");
        case BLOB_NOT_FOUND:
          throw new IllegalArgumentException("Blob not found.");
        case INTERNAL_ERROR:
          throw new BlobstoreFailureException("An internal blobstore error occurred.");
        default:
          throw new BlobstoreFailureException("An unexpected error occurred.", ex);
      }
    }

    try {
      FetchDataResponse response =
          FetchDataResponse.parseFrom(responseBytes, ExtensionRegistry.getEmptyRegistry());
      if (!response.isInitialized()) {
        throw new BlobstoreFailureException("Could not parse FetchDataResponse");
      }
      return response.getData().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public BlobKey createGsBlobKey(String filename) {

    if (!filename.startsWith("/gs/")) {
      throw new IllegalArgumentException(
          "Google storage filenames must be" + " prefixed with /gs/");
    }
    CreateEncodedGoogleStorageKeyRequest request =
        CreateEncodedGoogleStorageKeyRequest.newBuilder().setFilename(filename).build();

    byte[] responseBytes;
    try {
      responseBytes =
          ApiProxy.makeSyncCall(PACKAGE, "CreateEncodedGoogleStorageKey", request.toByteArray());
    } catch (ApiProxy.ApplicationException ex) {
      switch (BlobstoreServiceError.ErrorCode.forNumber(ex.getApplicationError())) {
        case INTERNAL_ERROR:
          throw new BlobstoreFailureException("An internal blobstore error occurred.");
        default:
          throw new BlobstoreFailureException("An unexpected error occurred.", ex);
      }
    }

    try {
      CreateEncodedGoogleStorageKeyResponse response =
          CreateEncodedGoogleStorageKeyResponse.parseFrom(
              responseBytes, ExtensionRegistry.getEmptyRegistry());
      if (!response.isInitialized()) {
        throw new BlobstoreFailureException(
            "Could not parse CreateEncodedGoogleStorageKeyResponse");
      }
      return new BlobKey(response.getBlobKey());
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
