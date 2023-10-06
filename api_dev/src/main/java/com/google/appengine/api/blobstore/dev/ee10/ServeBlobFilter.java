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

package com.google.appengine.api.blobstore.dev.ee10;

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.ByteRange;
import com.google.appengine.api.blobstore.RangeFormatException;
import com.google.appengine.api.blobstore.dev.BlobInfoStorage;
import com.google.appengine.api.blobstore.dev.BlobStorage;
import com.google.appengine.api.blobstore.dev.BlobStorageFactory;
import com.google.appengine.api.blobstore.dev.LocalBlobstoreService;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.common.io.Closeables;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * {@code ServeBlobFilter} implements the ability to serve a blob in
 * the development environment.  In production, the {@code
 * X-AppEngine-BlobKey} header is intercepted above the runtime and
 * turned into a streaming response.  However, in the development
 * environment we need to implement this in-process.
 *
 */
public final class ServeBlobFilter implements Filter {
  private static final Logger logger = Logger.getLogger(
      ServeBlobFilter.class.getName());

  static final String SERVE_HEADER = "X-AppEngine-BlobKey";
  static final String BLOB_RANGE_HEADER = "X-AppEngine-BlobRange";
  static final String CONTENT_RANGE_HEADER = "Content-range";
  static final String RANGE_HEADER = "Range";
  static final String CONTENT_TYPE_HEADER = "Content-type";
  static final String CONTENT_RANGE_FORMAT = "bytes %d-%d/%d";
  private static final int BUF_SIZE = 4096;

  private BlobStorage blobStorage;
  private BlobInfoStorage blobInfoStorage;
  private ApiProxyLocal apiProxyLocal;

  @Override
  public void init(FilterConfig config) {
    blobInfoStorage = BlobStorageFactory.getBlobInfoStorage();
    apiProxyLocal = (ApiProxyLocal) config.getServletContext().getAttribute(
        "com.google.appengine.devappserver.ApiProxyLocal");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    ResponseWrapper wrapper = new ResponseWrapper((HttpServletResponse) response);
    chain.doFilter(request, wrapper);

    BlobKey blobKey = wrapper.getBlobKey();
    if (blobKey != null) {
      serveBlob(blobKey, wrapper.hasContentType(), (HttpServletRequest)request, wrapper);
    }
  }

  @Override
  public void destroy() {
  }

  private BlobStorage getBlobStorage() {
    if (blobStorage == null) {
      // N.B.: We need to make sure that the blobstore stub
      // has been initialized and has had a chance to initialize
      // BlobStorageFactory using its properties.
      apiProxyLocal.getService(LocalBlobstoreService.PACKAGE);

      blobStorage = BlobStorageFactory.getBlobStorage();
    }
    return blobStorage;
  }

  private void calculateContentRange(BlobInfo blobInfo,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws RangeFormatException {
    ResponseWrapper responseWrapper = (ResponseWrapper) response;
    String contentRangeHeader = request.getHeader(CONTENT_RANGE_HEADER);
    long blobSize = blobInfo.getSize();
    String rangeHeader = responseWrapper.getBlobRangeHeader();
    if (rangeHeader != null) {
      if (rangeHeader.isEmpty()) {
        response.setHeader(BLOB_RANGE_HEADER, null);
        rangeHeader = null;
      }
    } else {
      rangeHeader = request.getHeader(RANGE_HEADER);
    }

    if (rangeHeader != null) {
      ByteRange byteRange = ByteRange.parse(rangeHeader);
      if (byteRange.hasEnd()) {
        contentRangeHeader = String.format(CONTENT_RANGE_FORMAT,
                                           byteRange.getStart(),
                                           byteRange.getEnd(),
                                           blobSize);
      } else {
        long contentRangeStart;
        if (byteRange.getStart() >= 0) {
          contentRangeStart = byteRange.getStart();
        } else {
          contentRangeStart = blobSize + byteRange.getStart();
        }
        contentRangeHeader = String.format(CONTENT_RANGE_FORMAT,
                                           contentRangeStart,
                                           blobSize - 1,
                                           blobSize);
      }
      response.setHeader(CONTENT_RANGE_HEADER, contentRangeHeader);
    }
  }

  private static void copy(InputStream from, OutputStream to, long size) throws IOException {
    byte[] buf = new byte[BUF_SIZE];
    while (size > 0) {
      int r = from.read(buf);
      if (r == -1) {
        return;
      }
      to.write(buf, 0, (int)Math.min(r, size));
      size -= r;
    }
  }

  private void serveBlob(BlobKey blobKey,
                         boolean hasContentType,
                         HttpServletRequest request,
                         HttpServletResponse response)
      throws IOException {
    if (response.isCommitted()) {
      logger.severe("Asked to send blob " + blobKey + " but response was already committed.");
      return;
    }

    // Data presence in info storage is the primary clue of whether this
    // is a valid blob.
    BlobInfo blobInfo = blobInfoStorage.loadBlobInfo(blobKey);
    if (blobInfo == null) {
      blobInfo = blobInfoStorage.loadGsFileInfo(blobKey);
    }
    if (blobInfo == null) {
      logger.severe("Could not find blob: " + blobKey);
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // And the blob missing from storage is redundant (although for file
    // storage could happen if the file was deleted).
    if (!getBlobStorage().hasBlob(blobKey)) {
      logger.severe("Blob " + blobKey + " missing. Did you delete the file?");
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (!hasContentType) {
      response.setContentType(getContentType(blobKey));
    }

    try {
      calculateContentRange(blobInfo, request, response);

      String contentRange = ((ResponseWrapper)response).getContentRangeHeader();
      long contentLength = blobInfo.getSize();
      long start = 0;
      if (contentRange != null) {
        ByteRange byteRange = ByteRange.parseContentRange(contentRange);
        start = byteRange.getStart();
        contentLength = byteRange.getEnd() - byteRange.getStart() + 1;
        response.setStatus(206);
      }
      response.setHeader("Content-Length", Long.toString(contentLength));

      boolean swallowDueToThrow = true;
      InputStream inStream = getBlobStorage().fetchBlob(blobKey);
      try {
        OutputStream outStream = response.getOutputStream();
        try {
          inStream.skip(start);
          copy(inStream, outStream, contentLength);
          swallowDueToThrow = false;
        } finally {
          Closeables.close(outStream, swallowDueToThrow);
        }
      } finally {
        Closeables.close(inStream, swallowDueToThrow);
      }
    } catch (RangeFormatException ex) {
      // Errors become 416, as in production.
      response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
      return;
    }

  }

  private String getContentType(BlobKey blobKey) {
    BlobInfo blobInfo = blobInfoStorage.loadBlobInfo(blobKey);
    if (blobInfo != null) {
      return blobInfo.getContentType();
    } else {
      return "application/octet-stream";
    }
  }

  public static class ResponseWrapper extends HttpServletResponseWrapper {
    private BlobKey blobKey;
    private boolean hasContentType;
    private String contentRangeHeader;
    private String blobRangeHeader;

    public ResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void setContentType(String contentType) {
      super.setContentType(contentType);
      hasContentType = true;
    }

    @Override
    public void addHeader(String name, String value) {
      if (name.equalsIgnoreCase(SERVE_HEADER)) {
        blobKey = new BlobKey(value);
      } else if( name.equalsIgnoreCase(CONTENT_RANGE_HEADER)) {
        contentRangeHeader = value;
        super.addHeader(name, value);
      } else if( name.equalsIgnoreCase(BLOB_RANGE_HEADER)) {
        blobRangeHeader = value;
        super.addHeader(name, value);
      } else if (name.equalsIgnoreCase(CONTENT_TYPE_HEADER)) {
        hasContentType = true;
        super.addHeader(name, value);
      } else {
        super.addHeader(name, value);
      }
    }

    @Override
    public void setHeader(String name, String value) {
      if (name.equalsIgnoreCase(SERVE_HEADER)) {
        blobKey = new BlobKey(value);
      } else if( name.equalsIgnoreCase(CONTENT_RANGE_HEADER)) {
        contentRangeHeader = value;
        super.setHeader(name, value);
      } else if( name.equalsIgnoreCase(BLOB_RANGE_HEADER)) {
        blobRangeHeader = value;
      } else if (name.equalsIgnoreCase(CONTENT_TYPE_HEADER)) {
        hasContentType = true;
        super.setHeader(name, value);
      } else {
        super.setHeader(name, value);
      }
    }

    @Override
    public boolean containsHeader(String name) {
      if (name.equals(SERVE_HEADER)) {
        return blobKey != null;
      } else {
        return super.containsHeader(name);
      }
    }

    public BlobKey getBlobKey() {
      return blobKey;
    }

    public boolean hasContentType() {
      return hasContentType;
    }

    public String getContentRangeHeader() {
      return contentRangeHeader;
    }

    public String getBlobRangeHeader() {
      return blobRangeHeader;
    }
  }
}
