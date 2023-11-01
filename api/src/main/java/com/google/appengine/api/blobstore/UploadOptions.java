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

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Allows users to customize the behavior of a single upload to the
 * {@link BlobstoreService}.
 *
 */
public final class UploadOptions {

  @Nullable private Long maxUploadSizeBytesPerBlob;

  @Nullable private Long maxUploadSizeBytes;

  private String gsBucketName;

  private UploadOptions() {
  }

  /**
   * Sets the maximum size in bytes for any one blob in the upload. If any blob
   * in the upload exceeds this value then a 413 error will be returned to the
   * client.
   * @param maxUploadSizeBytesPerBlob The maximum size in bytes that any one
   * blob in the upload can be.
   * @return {@code this} (for chaining)
   */
  public UploadOptions maxUploadSizeBytesPerBlob(long maxUploadSizeBytesPerBlob) {
    if (maxUploadSizeBytesPerBlob < 1) {
      throw new IllegalArgumentException("maxUploadSizeBytesPerBlob must be positive.");
    }
    this.maxUploadSizeBytesPerBlob = maxUploadSizeBytesPerBlob;
    return this;
  }

  /** Determines if the maximum upload size per blob is set. */
  public boolean hasMaxUploadSizeBytesPerBlob() {
    return maxUploadSizeBytesPerBlob != null;
  }

  /**
   * @returns the maximum upload size per blob.
   */
  public long getMaxUploadSizeBytesPerBlob() {
    if (maxUploadSizeBytesPerBlob == null) {
      throw new IllegalStateException("maxUploadSizeBytesPerBlob has not been set.");
    }
    return maxUploadSizeBytesPerBlob;
  }

  /**
   * Sets the maximum size in bytes that for the total upload. If the upload
   * exceeds this value then a 413 error will be returned to the client.
   * @param maxUploadSizeBytes The maximum size in bytes for the upload.
   * @return {@code this} (for chaining)
   */
  public UploadOptions maxUploadSizeBytes(long maxUploadSizeBytes) {
    if (maxUploadSizeBytes < 1) {
      throw new IllegalArgumentException("maxUploadSizeBytes must be positive.");
    }
    this.maxUploadSizeBytes = maxUploadSizeBytes;
    return this;
  }

  /** Determines if the maximum size is set. */
  public boolean hasMaxUploadSizeBytes() {
    return maxUploadSizeBytes != null;
  }

  /**
   * @returns the maximum upload size.
   */
  public long getMaxUploadSizeBytes() {
    if (maxUploadSizeBytes == null) {
      throw new IllegalStateException("maxUploadSizeBytes has not been set.");
    }
    return maxUploadSizeBytes;
  }

  public UploadOptions googleStorageBucketName(String bucketName) {
    this.gsBucketName = bucketName;
    return this;
  }

  /** Determines if the storage bucket is set. */
  public boolean hasGoogleStorageBucketName() {
    return this.gsBucketName != null;
  }

  /**
   * @returns the storage bucket name.
   */
  public String getGoogleStorageBucketName() {
    if (gsBucketName == null) {
      throw new IllegalStateException("gsBucketName has not been set.");
    }
    return gsBucketName;
  }

  @Override
  public int hashCode() {
    int hash = 17;
    if (maxUploadSizeBytesPerBlob != null) {
      hash = hash * 37 + maxUploadSizeBytesPerBlob.hashCode();
    }
    if (maxUploadSizeBytes != null) {
      hash = hash * 37 + maxUploadSizeBytes.hashCode();
    }
    if (gsBucketName != null) {
      hash = hash * 37 + gsBucketName.hashCode();
    }
    return hash;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof UploadOptions) {
      UploadOptions key = (UploadOptions) object;
      return Objects.equals(maxUploadSizeBytesPerBlob, key.maxUploadSizeBytesPerBlob)
          && Objects.equals(maxUploadSizeBytes, key.maxUploadSizeBytes)
          && Objects.equals(gsBucketName, key.gsBucketName);
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder("UploadOptions: maxUploadSizeBytes=");
    if (maxUploadSizeBytes != null) {
      buffer.append(maxUploadSizeBytes);
    } else {
      buffer.append("unlimited");
    }
    buffer.append(", maxUploadSizeBytesPerBlob=");
    if (maxUploadSizeBytesPerBlob != null) {
      buffer.append(maxUploadSizeBytesPerBlob);
    } else {
      buffer.append("unlimited");
    }
    buffer.append(", gsBucketName=");
    if (gsBucketName != null) {
      buffer.append(gsBucketName);
    } else {
      buffer.append("None");
    }
    buffer.append(".");
    return buffer.toString();
  }

  /**
   * Contains static creation methods for {@link UploadOptions}.
   */
  public static final class Builder {
    /**
     * Returns default {@link UploadOptions} and calls
     * {@link UploadOptions#maxUploadSizeBytes(long)}.
     */
    public static UploadOptions withMaxUploadSizeBytes(long maxUploadSizeBytes) {
      return withDefaults().maxUploadSizeBytes(maxUploadSizeBytes);
    }

    /**
     * Returns default {@link UploadOptions} and calls
     * {@link UploadOptions#maxUploadSizeBytesPerBlob(long)}.
     */
    public static UploadOptions withMaxUploadSizeBytesPerBlob(long maxUploadSizeBytesPerBlob) {
      return withDefaults().maxUploadSizeBytesPerBlob(maxUploadSizeBytesPerBlob);
    }

    /**
     * Returns default {@link UploadOptions} and calls
     * {@link UploadOptions#googleStorageBucketName(String)}.
     */
    public static UploadOptions withGoogleStorageBucketName(String gsBucketName) {
      return withDefaults().googleStorageBucketName(gsBucketName);
    }

    /**
     * Returns default {@link UploadOptions} with default values.
     */
    public static UploadOptions withDefaults() {
      return new UploadOptions();
    }

    private Builder() {
    }
  }
}
