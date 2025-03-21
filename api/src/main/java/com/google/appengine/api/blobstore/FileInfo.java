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
import java.util.Date;
import org.jspecify.annotations.Nullable;

/**
 * {@code FileInfo} contains metadata about an uploaded file. This metadata is
 * gathered by parsing the HTTP headers included in the file upload.
 *
 * @see <a href="http://tools.ietf.org/html/rfc1867">RFC 1867</a> for
 * the specification of HTTP file uploads.
 *
 * @since 1.7.5
 */
public class FileInfo {
  private final String contentType;
  private final Date creation;
  private final String filename;
  private final long size;
  private final String md5Hash;
  private final @Nullable String gsObjectName;

  /**
   * Creates a {@code FileInfo} by providing the associated metadata.
   * This is done by the API on the developer's behalf.
   *
   * @param contentType  the MIME Content-Type provided in the HTTP header during upload of this
   *                     Blob.
   * @param creation     the time and date the blob was uploaded.
   * @param filename     the file included in the Content-Disposition HTTP header during upload of
   *                     this Blob.
   * @param size         the size in bytes of this Blob.
   * @param md5Hash      the md5Hash of this Blob.
   * @param gsObjectName the name of the file written to Google Cloud Storage or null if the file
   *                     was not uploaded to Google Cloud Storage.
   */
  public FileInfo(String contentType, Date creation, String filename, long size, String md5Hash,
                  @Nullable String gsObjectName) {
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

    this.contentType = contentType;
    this.creation = creation;
    this.filename = filename;
    this.size = size;
    this.md5Hash = md5Hash;
    this.gsObjectName = gsObjectName;
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
   * Returns the name of the file written to Google Cloud Storage or null if the file was not
   * uploaded to Google Cloud Storage. This property is only available for BlobInfos returned by
   * getUploadedBlobInfos(), as its value is not persisted in the Datastore. Any attempt to
   * access this property on other BlobInfos will return null.
   */
  public @Nullable String getGsObjectName() {
    return gsObjectName;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof FileInfo) {
      FileInfo fi = (FileInfo) obj;
      return contentType.equals(fi.contentType)
          && creation.equals(fi.creation)
          && filename.equals(fi.filename)
          && size == fi.size
          && md5Hash.equals(fi.md5Hash)
          && Objects.equal(gsObjectName, fi.gsObjectName);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(contentType, creation, filename, size, md5Hash, gsObjectName);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("<FileInfo:");
    builder.append(" contentType = ");
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
}
