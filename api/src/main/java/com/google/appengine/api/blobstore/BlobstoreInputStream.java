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

import static java.lang.Integer.min;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * BlobstoreInputStream provides an InputStream view of a blob in
 * Blobstore.
 *
 * It is thread compatible but not thread safe: there is no static state, but
 * any multithreaded use must be externally synchronized.
 *
 */
public final class BlobstoreInputStream extends InputStream {

  /**
   * A subclass of {@link IOException } that indicates operations on a stream after
   * it is closed.
   */
  public static final class ClosedStreamException extends IOException {
    private static final long serialVersionUID = 3251292840204787108L;

    /**
     * Construct an exception with specified message. 
     */
    public ClosedStreamException(String message) {
      super(message);
    }

    /**
     * Construct exception with specified message and cause.
     */
    public ClosedStreamException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * A subclass of {@link IOException} that indicates that there was a problem
   * interacting with Blobstore.
   */
  public static final class BlobstoreIOException extends IOException {
    private static final long serialVersionUID = 3160441042922394772L;

    /**
     * Constructs a {@code BlobstoreIOException} with the specified detail
     * message.
     */
    public BlobstoreIOException(String message) {
      super(message);
    }

    /**
     * Constructs a {@code BlobstoreIOException} with the specified detail
     * message and cause.
     */
    public BlobstoreIOException(String message, Throwable cause) {
      super(message);
      initCause(cause);
    }
  }

  // Key for the blob underlying this stream.
  private final BlobKey blobKey;

  // Info for the blob underlying this stream.
  private final BlobInfo blobInfo;

  // Offset in the blob for our next read.
  private long blobOffset;

  // Buffer for blob data that we've received.
  private byte @Nullable [] buffer;

  // Offset of next byte in buffer to return.
  private int bufferOffset;

  // True if there has been a call to mark() that hasn't been followed by a
  // call to reset().
  private boolean markSet = false;

  // The offset in the blob at which the last call to mark() was made.
  private long markOffset;

  // Service used to fetch the actual data for the blob.
  private final BlobstoreService blobstoreService;

  // Stream closed or not.
  private boolean isClosed = false;

  /**
   * Creates a BlobstoreInputStream that reads data from the blob indicated by
   * blobKey, starting at offset.
   *
   * @param blobKey A valid BlobKey indicating the blob to read from.
   * @param offset An offset to start from.
   *
   * @throws BlobstoreIOException If the blobKey given is invalid.
   * @throws IllegalArgumentException If {@code offset} &lt; 0.
   */
  public BlobstoreInputStream(BlobKey blobKey, long offset) throws IOException {
    this(blobKey, offset, new BlobInfoFactory(), BlobstoreServiceFactory.getBlobstoreService());
  }

  /**
   * Creates a BlobstoreInputStream that reads data from the blob indicated by
   * blobKey, starting at the beginning of the blob.
   *
   * @param blobKey A valid BlobKey indicating the blob to read from.
   * @throws BlobstoreIOException If the blobKey given is invalid.
   * @throws IllegalArgumentException If {@code offset} &lt; 0.
   */
  public BlobstoreInputStream(BlobKey blobKey) throws IOException {
    this(blobKey, 0);
  }

  // VisibleForTesting
  BlobstoreInputStream(BlobKey blobKey,
                       long offset,
                       BlobInfoFactory blobInfoFactory,
                       BlobstoreService blobstoreService) throws IOException {
    if (offset < 0) {
      throw new IllegalArgumentException("Offset " + offset + " is less than 0");
    }

    this.blobKey = blobKey;
    this.blobOffset = offset;
    this.blobstoreService = blobstoreService;
    BlobInfo maybeBlobInfo = blobInfoFactory.loadBlobInfo(blobKey);
    if (maybeBlobInfo == null) {
      throw new BlobstoreIOException("BlobstoreInputStream received an invalid blob key: "
          + blobKey.getKeyString());
    }
    this.blobInfo = maybeBlobInfo;
  }

  /**
   * Check if we have entirely consumed the last buffer read from the blob.
   *
   * @returns true if we have consumed the last buffer or if no buffer has
   *          yet been read.
   */
  private boolean atEndOfBuffer() {
    Preconditions.checkState(buffer == null || bufferOffset <= buffer.length,
        "Buffer offset is past the end of the buffer. This should never happen.");
    return buffer == null || bufferOffset == buffer.length;
  }

  private void checkClosedStream() throws ClosedStreamException {
    if (isClosed) {
      throw new ClosedStreamException("Stream is closed");
    }
  }

  /**
   * @throws IOException - does not actually throw but as it's part of our public API and
   * removing it can cause compilation errors, leaving it in (and documenting to quiet Eclipse
   * warning).
   */
  @Override
  public void close() throws IOException {
    isClosed = true;
    buffer = null;
  }

  @Override
  public int read() throws IOException {
    checkClosedStream();

    if (!ensureDataInBuffer()) {
      return -1;
    }
    requireNonNull(buffer); // ensureDataInBuffer ensures this

    return buffer[bufferOffset++] & 0xff;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    // A bunch of checks to implement the read contract.
    checkClosedStream();
    Preconditions.checkNotNull(b);
    Preconditions.checkElementIndex(off, b.length);
    Preconditions.checkPositionIndex(off + len, b.length);
    if (len == 0) {
      return 0;
    }

    if (!ensureDataInBuffer()) {
      return -1;
    }
    requireNonNull(buffer); // ensureDataInBuffer ensures this

    // Copy the maximum we can from the preexisting input buffer.
    int amountToCopy = min(buffer.length - bufferOffset, len);
    System.arraycopy(buffer, bufferOffset, b, off, amountToCopy);
    bufferOffset += amountToCopy;
    return amountToCopy;
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public void mark(int readlimit) {
    markSet = true;
    markOffset = blobOffset;
    // If the buffer exists, then blobOffset points at the end of the buffer.
    if (buffer != null) {
      markOffset += bufferOffset - buffer.length;
    }
  }

  @Override
  public void reset() throws IOException {
    checkClosedStream();
    if (!markSet) {
      throw new IOException("Attempted to reset on un-mark()ed BlobstoreInputStream");
    }
    blobOffset = markOffset;
    buffer = null;
    bufferOffset = 0;
    markSet = false;
  }

  /**
   * Attempts to ensure that {@code buffer} contains unprocessed data from the
   * blob.
   *
   * @return {@code true} if the buffer now contains unprocessed data.
   * @throws BlobstoreIOException if there is a problem retrieving data from
   *         the blob.
   */
  private boolean ensureDataInBuffer() throws IOException {
    if (!atEndOfBuffer()) {
      return true;
    }

    long fetchSize = Math.min(
        blobInfo.getSize() - blobOffset,
        BlobstoreService.MAX_BLOB_FETCH_SIZE);
    if (fetchSize <= 0) {
      buffer = null;
      return false;
    }
    try {
      // -1 since end is inclusive
      buffer = blobstoreService.fetchData(blobKey, blobOffset, blobOffset + fetchSize - 1);
      blobOffset += buffer.length;
      bufferOffset = 0;
      return true;
    } catch (BlobstoreFailureException bfe) {
      throw new BlobstoreIOException("Error reading data from Blobstore", bfe);
    }
  }
}
