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

package com.google.apphosting.runtime;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;

/**
 * A class in charge of compressing request responses at the HTTP protocol buffer level.
 *
 */
// the C++ equivalent is http_compression.cc in apphosting.
// Many comments come from the C++ implementation itself.
public class HttpCompression {

  /** CSS/JavaScript content-types that are allowed to be compressed. */
  private static final ImmutableSet<String> COMPRESSABLE_CSS_JS =
      ImmutableSet.of(
          "text/css",
          "text/javascript",
          "application/x-javascript",
          "application/javascript",
          "application/json");

  /**
   * Compress a byte buffer
   *
   * @param content the entry buffer
   * @return the compressed buffer
   */
  static byte[] compress(ByteString content) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream zos = new GZIPOutputStream(outputStream)) {
      content.writeTo(zos);
    }
    return outputStream.toByteArray();
  }

  String getHeader(List<HttpPb.ParsedHttpHeader> lrh, String key) {
    for (HttpPb.ParsedHttpHeader p : lrh) {
      if (p.getKey().equalsIgnoreCase(key)) {
        return p.getValue();
      }
    }
    return null;
  }

  // from HTTPUtils::IsContentGzipEncoded

  /**
   * Returns true iff the "content-encoding" header lists 'gzip' or 'x-gzip' as the final encoding
   * applied.  If the content has undergone other transformations after gzipping, then this API will
   * return false.
   *
   * Note: section 14.11 of http/1.1 RFC states that if multiple encodings are used, then they must
   * be listed in the order that they were applied.
   */
  boolean isContentGzipEncoded(String header) {
    if (header == null) {
      return false;
    }
    return header.toLowerCase().contains("gzip");
  }

  /**
   * Attempt to compress the HttpResponse, using trusted and untrusted headers
   * to determine if the client (or GFE) accepts compression for this response.
   * If the response can be compressed, the response buffer will be cleared and
   * replaced with the compressed response, and uncompressed_size and
   * uncompress_for_client fields will be set.  Otherwise, the response will be
   * left untouched.
   *
   * @param request
   * @param response
   * @return true if the response was successfully compressed.
   * @throws IOException
   */
  public boolean attemptCompression(
      RuntimePb.UPRequest request, MutableUpResponse response)
      throws IOException {
    if (!response.hasHttpResponse()) {
      return false;
    }

    if (response.getHttpResponseResponse().size() == 0) {
      return false;
    }

    // Verify the response isn't already compressed.
    String contentEncoding =
        getHeader(response.getRuntimeHeadersList(), HttpHeaders.CONTENT_ENCODING);

    if (isContentGzipEncoded(contentEncoding)) {
      // N.B.(jhaugh): We could examine the response body and look for a gzip
      // header and footer, and update uncompressed_size and
      // uncompress_for_client.  However, since header sanitization might not have
      // occurred yet, we might be looking at an untrusted response, and we cannot
      // trust the uncompressed_size stored in the final 4 bytes of the response.

      // N.B.(jhaugh): In the case of an app-compressed response, this is what
      // will happen:
      //
      //  1) app responds with: Content-Type: text/plain, Content-Encoding: gzip
      //  2) runtime doesn't compress, since the CE says it's already compressed
      //  3) SandboxRuntime doesn't compress, since it's already compressed
      //  4) appserver strips the CE in AppServerResponse::AddRuntimeHeaders
      //  5) appserver responds to PFE
      //  6) PFE doesn't disable compression, since the CE says it's not
      //     compressed
      //  7) XFE/HSR compresses, since the CT is text/plain
      //  8) now it's double-compressed, but at least the second compression is
      //     safe.
      //  9) the GFE returns the compressed response to the client
      //
      // To get this case right, we could detect CE:gzip when we strip, and add
      // X-Google-NoCompress header.  That would at least prevent double
      // compression.  But it's still a half-measure, since the CE will be wrong
      // and clients won't know to uncompress, so the response will look garbled.
      //
      // We could trust apps to compress, and check that the uncompressed_size is
      // something reasonable.  That would allow well-behaved apps to emit
      // compressed responses (e.g., if they're serving an object that's been
      // stored compressed) while being reasonably safe against bad guys.
      //
      // For now, though, we have better things to do with our time.  The bottom
      // line is that we're safe, we'll strip the CE but won't re-compress.

      return false;
    }

    // Check if we can compress it.
    String userAgent = getHeader(request.getRequest().getHeadersList(), HttpHeaders.USER_AGENT);

    if (request.getRequest().getGzipGfe()) {
      userAgent += ",gzip(gfe)";
    }
    String acceptEncoding = getHeader(request.getRuntimeHeadersList(), HttpHeaders.ACCEPT_ENCODING);

    String contentType = getHeader(response.getHttpOutputHeadersList(), HttpHeaders.CONTENT_TYPE);

    // Detect whether we should compress the response for GFE.  If the request was
    // proxied by GFE, it will have added gzip(gfe) to Accept-Encoding.  Detect
    // this, and compress the response if possible.
    boolean compressForGfe = shouldCompress(true, userAgent, acceptEncoding, contentType);

    // Detect whether the client supports compression for this response.  If so,
    // we should compress, even if GFE did not explicitly request compression,
    // since not all requests are proxied by GFE, for example, requests that
    // arrive via HTTPOverRPC.
    boolean compressForClient = shouldCompress(false, userAgent, acceptEncoding, contentType);

    if (!compressForGfe && !compressForClient) {
      return false;
    }

    ByteString responseBytes = response.getHttpResponseResponse();
    long uncompressedSize = responseBytes.size();
    // Compress the response.  Response buffer will be cleared and written to if
    // compression succeeds, otherwise it will be left untouched.

    response.setHttpResponseResponse(ByteString.copyFrom(compress(responseBytes)));

    response.setHttpUncompressedSize(uncompressedSize);
    response.setHttpUncompressForClient(!compressForClient);

    response.addRuntimeHeaders(
        HttpPb.ParsedHttpHeader.newBuilder()
            .setKey(HttpHeaders.CONTENT_ENCODING)
            .setValue("gzip"));

    return true;
  }

  boolean shouldCompress(
      boolean compressForGfe, String userAgent, String acceptEncoding, String contentType) {

    // N.B.(jhaugh): GFE will sometimes add "gzip(gfe)" to accept-encoding and
    // user-agent to indicate that it wants a compressed response, overriding the
    // usual can-compress logic, and will uncompress backend responses later if
    // necessary.  CanCompressFor takes an ignore_gfe parameter as its first arg
    // that will ignore the GFE's request and just look at the actual client
    // headers.
    return canCompressFor(!compressForGfe, userAgent, acceptEncoding, contentType);
  }

  // <internal>
  // and a mix of java
  // <internal>

  /**
   * Returns whether we can compress for the client based on the user-agent and the content-type.
   *
   * For more details on how this algorithm was arrived at: consult io/httpserverconnection.cc
   *
   * @param ignoreGfe if GFE asked for gzip
   * @param userAgent the user agent, or null
   * @param coding    the compression coding used (e.g. gzip), or null
   * @param type      the content type, or null
   * @return <code>true</code> if we can gzip/deflate the response.
   */
  private boolean canCompressFor(
      boolean ignoreGfe,
      @Nullable String userAgent,
      @Nullable String coding,
      @Nullable String type) {
    if ((userAgent == null) || userAgent.isEmpty()) {
      return false;
    }
    if ((coding == null) || coding.isEmpty()) {
      return false;
    }
    if ((type == null) || type.isEmpty()) {
      return false;
    }
    // 1st: if they don't ask for gzip don't give it to them
    if (!coding.startsWith("gzip")
        && // starts with "gzip"
        !coding.contains(" gzip")
        && // gzip is a word
        !coding.contains(",gzip")) {
      return false;
    } else if (ignoreGfe) {
      if (// GFE asked for gzip.
         coding.contains("gzip(gfe)")
         && // Client did not ask for gzip.
         !coding.replace("gzip(gfe)", "").contains("gzip")) {
        return false;
      }
    }
    // extract the actual type from the content type header
    try {
      MediaType mediaType = MediaType.parse(type);
      if (mediaType.type() != null && mediaType.subtype() != null) {
        type = mediaType.type() + "/" + mediaType.subtype();
      } else {
        type = "nodefaulttype";
      }
    } catch (IllegalArgumentException e) {
      type = "nodefaulttype";
    }

    // check for clients which handle compression properly
    if ((!userAgent.contains("Mozilla/") || userAgent.contains("Mozilla/4.0"))
        && !userAgent.contains(" MSIE ")
        && !userAgent.contains("Opera")
        && !isGoodGzipUserAgent(userAgent)) {
      // Check for override...
      int gzipPosition = userAgent.indexOf("gzip"); // how clients can insist
      if (gzipPosition != -1) {
        // but maybe ignore the override if it came from gfe.
        if (ignoreGfe && gzipPosition == userAgent.indexOf("gzip(gfe)")) {
          return false;
        }
      } else {
        return false;
      }
    }

    // Don't compress css/javascript for anything but browsers we
    // trust - currently IE, Opera, Mozilla, and safari.  This list
    // should be kept in sync with C++, net/httpserverconnection.cc
    if (COMPRESSABLE_CSS_JS.contains(type)
        && !userAgent.contains(" MSIE ")
        && !userAgent.contains("Opera")
        && !isGoodGzipUserAgent(userAgent)
        && !userAgent.contains("gzip")) {
      return false;
    }

    // otherwise, compress all text/ content types and
    // several application types that we allow to be compressed.
    return type.startsWith("text/")
        || COMPRESSABLE_CSS_JS.contains(type)
        || (type.startsWith("application/")
            && (type.endsWith("+xml") || type.endsWith("/xml") || type.endsWith("/csv")))
        ||
        // cloud printer raster format heavily compressible
        type.equals("image/pwg-raster");
  }

  /**
   * Returns whether we can compress for the client based on the user-agent.
   *
   * @param userAgent the user agent.
   */
  private boolean isGoodGzipUserAgent(String userAgent) {
    // Please keep this list in sync with the lists in:
    //     //net/http2/server/lib/internal/httpprocessing.cc
    //     //net/httpconnection/httpserverconnection.cc
    return userAgent.contains(" Gecko")
        ||
        // Matches Googlebot, Reader ("Feedfetcher-Google"),
        // AdSense ("Mediapartners-Google") and
        // cloud print ("GoogleCloudPrint").
        userAgent.contains("Google")
        || userAgent.contains(" Safari/")
        || userAgent.contains("msnbot")
        || userAgent.contains("Baiduspider");
  }
}
