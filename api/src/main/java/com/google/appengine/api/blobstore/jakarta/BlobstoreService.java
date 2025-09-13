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

package com.google.appengine.api.blobstore.jakarta;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.ByteRange;
import com.google.appengine.api.blobstore.FileInfo;
import com.google.appengine.api.blobstore.UploadOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@code BlobstoreService} allows you to manage the creation and
 * serving of large, immutable blobs to users.
 *
 */
public interface BlobstoreService {
  public static final int MAX_BLOB_FETCH_SIZE = (1 << 20) - (1 << 15);  // 1MB - 16K;

  /**
   * Create an absolute URL that can be used by a user to
   * asynchronously upload a large blob.  Upon completion of the
   * upload, a callback is made to the specified URL.
   *
   * @param successPath A relative URL which will be invoked
   * after the user successfully uploads a blob. Must start with a "/",
   * and must be URL-encoded.
   *
   * @throws IllegalArgumentException If successPath was not valid.
   * @throws BlobstoreFailureException If an error occurred while
   * communicating with the blobstore.
   */
  String createUploadUrl(String successPath);

  /**
   * Create an absolute URL that can be used by a user to
   * asynchronously upload a large blob.  Upon completion of the
   * upload, a callback is made to the specified URL.
   *
   * @param successPath A relative URL which will be invoked
   * after the user successfully uploads a blob. Must start with a "/".
   * @param uploadOptions Specific options applicable only for this
   * upload URL.
   *
   * @throws IllegalArgumentException If successPath was not valid.
   * @throws BlobstoreFailureException If an error occurred while
   * communicating with the blobstore.
   */
  String createUploadUrl(String successPath, UploadOptions uploadOptions);

  /**
   * Arrange for the specified blob to be served as the response
   * content for the current request.  {@code response} should be
   * uncommitted before invoking this method, and should be assumed to
   * be committed after invoking it.  Any content written before
   * calling this method will be ignored.  You may, however, append
   * custom headers before or after calling this method.
   *
   * <p>Range header will be automatically translated from the Content-Range
   * header in the response.
   *
   * @param blobKey Blob-key to serve in response.
   * @param response HTTP response object.
   *
   * @throws IOException If an I/O error occurred.
   * @throws IllegalStateException If {@code response} was already committed.
   */
  void serve(BlobKey blobKey, HttpServletResponse response) throws IOException;

  /**
   * Arrange for the specified blob to be served as the response
   * content for the current request.  {@code response} should be
   * uncommitted before invoking this method, and should be assumed to
   * be committed after invoking it.  Any content written before
   * calling this method will be ignored.  You may, however, append
   * custom headers before or after calling this method.
   *
   * <p>This method will set the App Engine blob range header to serve a
   * byte range of that blob.
   *
   * @param blobKey Blob-key to serve in response.
   * @param byteRange Byte range to serve in response.
   * @param response HTTP response object.
   *
   * @throws IOException If an I/O error occurred.
   * @throws IllegalStateException If {@code response} was already committed.
   */
  void serve(BlobKey blobKey, @Nullable ByteRange byteRange, HttpServletResponse response)
      throws IOException;

  /**
   * Arrange for the specified blob to be served as the response
   * content for the current request.  {@code response} should be
   * uncommitted before invoking this method, and should be assumed to
   * be committed after invoking it.  Any content written before
   * calling this method will be ignored.  You may, however, append
   * custom headers before or after calling this method.
   *
   * <p>This method will set the App Engine blob range header to the content
   * specified.
   *
   * @param blobKey Blob-key to serve in response.
   * @param rangeHeader Content for range header to serve.
   * @param response HTTP response object.
   *
   * @throws IOException If an I/O error occurred.
   * @throws IllegalStateException If {@code response} was already committed.
   */
  void serve(BlobKey blobKey, String rangeHeader, HttpServletResponse response)
      throws IOException;

  /**
   * Get byte range from the request.
   *
   * @param request HTTP request object.
   *
   * @return Byte range as parsed from the HTTP range header.  null if there is no header.
   *
   * @throws RangeFormatException Unable to parse header because of invalid format.
   * @throws UnsupportedRangeFormatException Header is a valid HTTP range header, the specific
   * form is not supported by app engine.  This includes unit types other than "bytes" and multiple
   * ranges.
   */
  @Nullable ByteRange getByteRange(HttpServletRequest request);

  /**
   * Permanently deletes the specified blobs.  Deleting unknown blobs is a
   * no-op.
   *
   * @throws BlobstoreFailureException If an error occurred while
   * communicating with the blobstore.
   */
  void delete(BlobKey... blobKeys);

  /**
   * Returns the {@link BlobKey} for any files that were uploaded, keyed by the
   * upload form "name" field.
   * <p>This method should only be called from within a request served by
   * the destination of a {@code createUploadUrl} call.
   *
   * @throws IllegalStateException If not called from a blob upload
   * callback request.
   *
   * @deprecated Use {@link #getUploads} instead. Note that getUploadedBlobs
   * does not handle cases where blobs have been uploaded using the
   * multiple="true" attribute of the file input form element.
   */
  @Deprecated Map<String, BlobKey> getUploadedBlobs(HttpServletRequest request);

  /**
   * Returns the {@link BlobKey} for any files that were uploaded, keyed by the
   * upload form "name" field.
   * This method should only be called from within a request served by
   * the destination of a {@link #createUploadUrl} call.
   *
   * @throws IllegalStateException If not called from a blob upload
   * callback request.
   * @see #getBlobInfos
   * @see #getFileInfos
   */
  Map<String, List<BlobKey>> getUploads(HttpServletRequest request);

  /**
   * Returns the {@link BlobInfo} for any files that were uploaded, keyed by the
   * upload form "name" field.
   * This method should only be called from within a request served by
   * the destination of a {@link #createUploadUrl} call.
   *
   * @throws IllegalStateException If not called from a blob upload
   * callback request.
   * @see #getFileInfos
   * @see #getUploads
   * @since 1.7.5
   */
  Map<String, List<BlobInfo>> getBlobInfos(HttpServletRequest request);

  /**
   * Returns the {@link FileInfo} for any files that were uploaded, keyed by the
   * upload form "name" field.
   * This method should only be called from within a request served by
   * the destination of a {@link #createUploadUrl} call.
   *
   * Prefer this method over {@link #getBlobInfos} or {@link #getUploads} if
   * uploading files to Cloud Storage, as the FileInfo contains the name of the
   * created filename in Cloud Storage.
   *
   * @throws IllegalStateException If not called from a blob upload
   * callback request.
   * @see #getBlobInfos
   * @see #getUploads
   * @since 1.7.5
   */
  Map<String, List<FileInfo>> getFileInfos(HttpServletRequest request);

  /**
   * Get fragment from specified blob.
   *
   * @param blobKey Blob-key from which to fetch data.
   * @param startIndex Start index of data to fetch.
   * @param endIndex End index (inclusive) of data to fetch.
   * @throws IllegalArgumentException If blob not found, indexes are negative, indexes are inverted
   *     or fetch size is too large.
   * @throws SecurityException If the application does not have access to the blob.
   * @throws BlobstoreFailureException If an error occurred while communicating with the blobstore.
   */
  byte[] fetchData(BlobKey blobKey, long startIndex, long endIndex);

  /**
   * Create a {@link BlobKey} for a Google Storage File.
   *
   * <p>The existence of the file represented by filename is not checked, hence a BlobKey can be
   * created for a file that does not currently exist.
   *
   * <p>You can safely persist the {@link BlobKey} generated by this function.
   *
   * <p>The created {@link BlobKey} can then be used as a parameter in API methods that can support
   * objects in Google Storage, for example {@link serve}.
   *
   * @param filename The Google Storage filename. The filename must be in the format
   *     "/gs/bucket_name/object_name".
   * @throws IllegalArgumentException If the filename does not have the prefix "/gs/".
   */
  BlobKey createGsBlobKey(String filename);
}
