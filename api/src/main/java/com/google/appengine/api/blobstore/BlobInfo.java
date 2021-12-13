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

import com.google.common.base.Objects;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Date;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code BlobInfo} contains metadata about a blob. This metadata is gathered by
 * parsing the HTTP headers included in the blob upload.
 *
 * @see <a href="http://tools.ietf.org/html/rfc1867">RFC 1867</a> for
 * the specification of HTTP file uploads.
 */
public class BlobInfo implements Serializable {
  private static final long serialVersionUID = 4530115855912621409L;
  static final String DEFAULT_MD5_HASH = "";
  static final @Nullable String DEFAULT_GS_OBJECT_NAME = null;
  protected final BlobKey blobKey;
  protected final String contentType;
  protected final Date creation;
  protected final String filename;
  protected final long size;
  protected String md5Hash;
  protected final @Nullable String gsObjectName;

  /**
   * Creates a {@code BlobInfo} by providing the {@link BlobKey} and all
   * associated metadata. This is typically done by the API on the developer's
   * behalf.
   *
   * @param blobKey      the {@link BlobKey} of the Blob.
   * @param contentType  the MIME Content-Type provided in the HTTP header during upload of this
   *                     Blob.
   * @param creation     the time and date the blob was uploaded.
   * @param filename     the file included in the Content-Disposition HTTP header during upload of
   *                     this Blob.
   * @param size         the size in bytes of this Blob.
   * @param md5Hash      the md5Hash of this Blob.
   * @param gsObjectName the object name of this Blob, if it is stored on Google Cloud Storage,
   *                     null otherwise.
   */
  public BlobInfo(BlobKey blobKey, String contentType, Date creation, String filename,
                  long size, String md5Hash, @Nullable String gsObjectName) {
    if (blobKey == null) {
      throw new NullPointerException("blobKey must not be null");
    }
    if (contentType == null) {
      throw new NullPointerException("contentType must not be null");
    }
    if (creation == null) {
      throw new NullPointerException("creation must not be null");
    }
    if (filename == null) {
      throw new NullPointerException("filename must not be null");
    }
    if (md5Hash == null) {
      throw new NullPointerException("md5Hash must not be null");
    }
    if ("".equals(gsObjectName)) {
      throw new IllegalArgumentException("gsObjectName must not be an empty string");
    }

    this.blobKey = blobKey;
    this.contentType = contentType;
    this.creation = creation;
    this.filename = filename;
    this.size = size;
    this.md5Hash = md5Hash;
    this.gsObjectName = gsObjectName;
  }

  public BlobInfo(BlobKey blobKey, String contentType, Date creation, String filename,
                  long size, String md5Hash) {
    this(blobKey, contentType, creation, filename, size, md5Hash, DEFAULT_GS_OBJECT_NAME);
  }

  public BlobInfo(BlobKey blobKey, String contentType, Date creation, String filename, long size) {
    this(blobKey, contentType, creation, filename, size, DEFAULT_MD5_HASH, DEFAULT_GS_OBJECT_NAME);
  }

  /**
   * Returns the {@link BlobKey} of the Blob this {@code BlobInfo} describes.
   */
  public BlobKey getBlobKey() {
    return blobKey;
  }

  /**
   * Returns the MIME Content-Type provided in the HTTP header during upload of
   * this Blob.
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * Returns the time and date the blob was upload.
   */
  public Date getCreation() {
    return creation;
  }

  /**
   * Returns the file included in the Content-Disposition HTTP header during
   * upload of this Blob.
   */
  public String getFilename() {
    return filename;
  }

  /**
   * Returns the size in bytes of this Blob.
   */
  public long getSize() {
    return size;
  }

  /**
   * Returns the md5Hash of this Blob.
   */
  public String getMd5Hash() {
    return md5Hash;
  }

  /**
   * Returns the object name of this Blob, if it is stored in Google Cloud Storage,
   * in the form {@code /[bucket-name]/[object-name]}. Returns {@code null} if the Blob is stored
   * in Blobstorage.
   */
  @Nullable
  public String getGsObjectName() {
    return gsObjectName;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof BlobInfo) {
      BlobInfo bi = (BlobInfo) obj;
      return blobKey.equals(bi.blobKey)
        && contentType.equals(bi.contentType)
        && creation.equals(bi.creation)
        && filename.equals(bi.filename)
        && size == bi.size
        && md5Hash.equals(bi.md5Hash)
        && Objects.equal(gsObjectName, bi.gsObjectName);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(blobKey, contentType, creation, filename, size, md5Hash, gsObjectName);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("<BlobInfo: ");
    builder.append(blobKey);
    builder.append(", contentType = ");
    builder.append(contentType);
    builder.append(", creation = ");
    builder.append(creation);
    builder.append(", filename = ");
    builder.append(filename);
    builder.append(", size = ");
    builder.append(size);
    builder.append(", md5Hash = ");
    builder.append(md5Hash);
    if (gsObjectName != null) {
      builder.append(", gsObjectName = ");
      builder.append(gsObjectName);
    }
    builder.append(">");
    return builder.toString();
  }

  private void readObject(ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    // We don't want clients of BlobInfo to be concerned about checking for
    // a null md5Hash property on objects that were originally serialized before
    // this property was added. In this case, set the md5Hash to a default, not
    // null value.
    if (md5Hash == null) {
      md5Hash = DEFAULT_MD5_HASH;
    }
  }
}
