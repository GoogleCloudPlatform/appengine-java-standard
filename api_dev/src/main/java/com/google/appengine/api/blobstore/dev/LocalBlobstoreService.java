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

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServicePb.BlobstoreServiceError;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateEncodedGoogleStorageKeyResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.CreateUploadURLResponse;
import com.google.appengine.api.blobstore.BlobstoreServicePb.DeleteBlobRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataRequest;
import com.google.appengine.api.blobstore.BlobstoreServicePb.FetchDataResponse;
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.proto2api.ApiBasePb.VoidProto;
import com.google.apphosting.utils.config.GenerationDirectory;
import com.google.auto.service.AutoService;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of local blobstore service.
 *
 */
@AutoService(LocalRpcService.class)
public final class LocalBlobstoreService extends AbstractLocalRpcService {
  // Environment attribute key where the instance port is stored. This must match
  // com.google.appengine.tools.development.LocalEnvironment.PORT_ID_ENV_ATTRIBUTE but
  // cannot access that definition due to packaging for unit tests.
  private static final String PORT_ID_ENV_ATTRIBUTE = "com.google.appengine.instance.port";

  private static final Logger logger = Logger.getLogger(
      LocalBlobstoreService.class.getName());

  /**
   * Where to read/store the blobs from/to.
   */
  public static final String BACKING_STORE_PROPERTY = "blobstore.backing_store";

  /**
   * True to put the blobstore into "memory-only" mode.
   */
  public static final String NO_STORAGE_PROPERTY = "blobstore.no_storage";

  /**
   * The package name for this service.
   */
  public static final String PACKAGE = "blobstore";

  /**
   * The prefix we apply to encoded Google Storage BlobKeys
   */
  public static final String GOOGLE_STORAGE_KEY_PREFIX = "encoded_gs_key:";

  static final String UPLOAD_URL_PREFIX = "/_ah/upload/";

  private BlobStorage blobStorage;
  private BlobUploadSessionStorage uploadSessionStorage;

  private String serverHostName;

  @Override
  public String getPackage() {
    return PACKAGE;
  }

  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    uploadSessionStorage = new BlobUploadSessionStorage();

    String noStorage = properties.get(NO_STORAGE_PROPERTY);
    if (noStorage != null && Boolean.parseBoolean(noStorage)) {
      BlobStorageFactory.setMemoryBlobStorage();
    } else {
      String filePath = properties.get(BACKING_STORE_PROPERTY);
      File file;
      if (filePath != null) {
        logger.log(Level.INFO, "Creating blobstore backing store at " + filePath);
        file = new File(filePath);
      } else {
        file = GenerationDirectory.getGenerationDirectory(
            context.getLocalServerEnvironment().getAppDir());
      }
      file.mkdirs();
      if (!file.canWrite()) {
        logger.log(Level.WARNING, "Default blobstore file location is not writable, " +
            "creating a temporary directory. State will not be persisted between restarts.");
        file = Files.createTempDir();
      }
      BlobStorageFactory.setFileBlobStorage(file);
    }
    blobStorage = BlobStorageFactory.getBlobStorage();
    serverHostName = context.getLocalServerEnvironment().getHostName();
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    if (blobStorage instanceof MemoryBlobStorage) {
      ((MemoryBlobStorage) blobStorage).deleteAllBlobs();
    }
  }

  public CreateUploadURLResponse createUploadURL(Status status, CreateUploadURLRequest request) {
    BlobUploadSession session = new BlobUploadSession(request.getSuccessPath());
    if (request.hasMaxUploadSizePerBlobBytes()) {
      session.setMaxUploadSizeBytesPerBlob(request.getMaxUploadSizePerBlobBytes());
    }
    if (request.hasMaxUploadSizeBytes()) {
      session.setMaxUploadSizeBytes(request.getMaxUploadSizeBytes());
    }
    if (request.hasGsBucketName()) {
      session.setGoogleStorageBucketName(request.getGsBucketName());
    }
    String sessionId = uploadSessionStorage.createSession(session);

    CreateUploadURLResponse.Builder response = CreateUploadURLResponse.newBuilder();
    String url = String.format("http://%s:%s%s%s", serverHostName, getCurrentInstancePort(),
        UPLOAD_URL_PREFIX, sessionId);
    response.setUrl(url);

    return response.build();
  }

  public VoidProto deleteBlob(Status status, final DeleteBlobRequest request) {
    for (String blobKeyString : request.getBlobKeyList()) {
      BlobKey blobKey = new BlobKey(blobKeyString);
      if (blobStorage.hasBlob(blobKey)) {
        try {
          blobStorage.deleteBlob(blobKey);
        } catch (IOException ex) {
          logger.log(Level.WARNING, "Could not delete blob: " + blobKey, ex);
          throw new ApiProxy.ApplicationException(
              BlobstoreServiceError.ErrorCode.INTERNAL_ERROR_VALUE, ex.toString());
        }
      }
    }

    return VoidProto.getDefaultInstance();
  }

  public FetchDataResponse fetchData(Status status, final FetchDataRequest request) {
    if (request.getStartIndex() < 0) {
      throw new ApiProxy.ApplicationException(
          BlobstoreServiceError.ErrorCode.DATA_INDEX_OUT_OF_RANGE_VALUE,
          "Start index must be >= 0.");
    }

    if (request.getEndIndex() < request.getStartIndex()) {
      throw new ApiProxy.ApplicationException(
          BlobstoreServiceError.ErrorCode.DATA_INDEX_OUT_OF_RANGE_VALUE,
          "End index must be >= startIndex.");
    }

    long fetchSize = request.getEndIndex() - request.getStartIndex() + 1;
    if (fetchSize > BlobstoreService.MAX_BLOB_FETCH_SIZE) {
      throw new ApiProxy.ApplicationException(
          BlobstoreServiceError.ErrorCode.BLOB_FETCH_SIZE_TOO_LARGE_VALUE,
          "Blob fetch size too large.");
    }

    final FetchDataResponse.Builder response = FetchDataResponse.newBuilder();
    final BlobKey blobKey = new BlobKey(request.getBlobKey());
    BlobInfoStorage blobInfoStorage = new BlobInfoStorage();
    BlobInfo blobInfo = blobInfoStorage.loadBlobInfo(blobKey);
    if (blobInfo == null) {
      blobInfo = blobInfoStorage.loadGsFileInfo(blobKey);
    }
    if (blobInfo == null) {
      throw new ApiProxy.ApplicationException(
          BlobstoreServiceError.ErrorCode.BLOB_NOT_FOUND_VALUE, "Blob not found.");
    }

    final long endIndex;
    if (request.getEndIndex() > blobInfo.getSize() - 1) {
      endIndex = blobInfo.getSize() - 1;
    } else {
      endIndex = request.getEndIndex();
    }

    if (request.getStartIndex() > endIndex) {
      response.setData(ByteString.copyFromUtf8(""));
    } else {
      // Safe to cast because index will never be above MAX_BLOB_FETCH_SIZE.
      final byte[] data = new byte[(int) (endIndex - request.getStartIndex() + 1)];
      try {
        boolean swallowDueToThrow = true;
        InputStream stream = blobStorage.fetchBlob(blobKey);
        try {
          ByteStreams.skipFully(stream, request.getStartIndex());
          ByteStreams.readFully(stream, data);
          swallowDueToThrow = false;
        } finally {
          Closeables.close(stream, swallowDueToThrow);
        }
      } catch (IOException ex) {
        logger.log(Level.WARNING, "Could not fetch data: " + blobKey, ex);
        throw new ApiProxy.ApplicationException(
            BlobstoreServiceError.ErrorCode.INTERNAL_ERROR_VALUE, ex.toString());
      }

      response.setData(ByteString.copyFrom(data));
    }

    return response.build();
  }

  public CreateEncodedGoogleStorageKeyResponse createEncodedGoogleStorageKey(Status status,
      CreateEncodedGoogleStorageKeyRequest request) {
    // Response is the encoded string - Padding confuses the shoebox Regex pattern
    // so don't use it.
    String encoded = base64Url().omitPadding().encode(request.getFilename().getBytes());
    return CreateEncodedGoogleStorageKeyResponse.newBuilder()
        .setBlobKey(GOOGLE_STORAGE_KEY_PREFIX + encoded)
        .build();
  }

  private String getCurrentInstancePort() {
    Integer port =
        (Integer) ApiProxy.getCurrentEnvironment().getAttributes().get(PORT_ID_ENV_ATTRIBUTE);
    return port.toString();
  }
}
