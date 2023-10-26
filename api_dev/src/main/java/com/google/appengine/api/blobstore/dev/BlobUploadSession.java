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

/**
 * {@code BlobUploadSession} is a simple data container that stores the state associated with an
 * in-progress upload.
 */
public class BlobUploadSession {
  private final String successPath;
  private Long maxUploadSizeBytesPerBlob;
  private Long maxUploadSizeBytes;
  private String googleStorageBucket;

  public BlobUploadSession(String successPath) {
    this.successPath = successPath;
  }

  public String getSuccessPath() {
    return successPath;
  }

  public void setMaxUploadSizeBytesPerBlob(long size) {
    maxUploadSizeBytesPerBlob = size;
  }

  public long getMaxUploadSizeBytesPerBlob() {
    if (maxUploadSizeBytesPerBlob == null) {
      throw new IllegalStateException("maxUploadSizeBytesPerBlob has not been set.");
    }
    return maxUploadSizeBytesPerBlob;
  }

  public boolean hasMaxUploadSizeBytesPerBlob() {
    return maxUploadSizeBytesPerBlob != null;
  }

  public void setMaxUploadSizeBytes(long size) {
    maxUploadSizeBytes = size;
  }

  public long getMaxUploadSizeBytes() {
    if (maxUploadSizeBytes == null) {
      throw new IllegalStateException("maxUploadSizeBytesPerBlob has not been set.");
    }
    return maxUploadSizeBytes;
  }

  public boolean hasMaxUploadSizeBytes() {
    return maxUploadSizeBytes != null;
  }

  public void setGoogleStorageBucketName(String bucketName) {
    googleStorageBucket = bucketName;
  }

  public String getGoogleStorageBucketName() {
    if (googleStorageBucket == null) {
      throw new IllegalStateException("googleStorageBucket has not been set.");
    }
    return googleStorageBucket;
  }

  public boolean hasGoogleStorageBucketName() {
    return googleStorageBucket != null;
  }
}
