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

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.common.base.Utf8;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.net.HttpHeaders;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for HttpCompression The list of tests there is exactly as the C++ equivalent you can get at
 * http_compression_test.cc so the logic should be the same as C++ except the app_request tests that
 * are not relevant in this layer
 *
 */
@RunWith(JUnit4.class)
public class HttpCompressionTest {

  private RuntimePb.UPRequest upRequest;
  private MutableUpResponse upResponse;

  boolean alreadyCompressed;

  String userAgent;
  String acceptEncoding;
  String contentType;
  String responseBody;
  boolean noResponse;

  @Before
  public void setUp() throws Exception {
    upRequest = RuntimePb.UPRequest.getDefaultInstance();
    upResponse = new MutableUpResponse();
    alreadyCompressed = false;
    userAgent = "Mozilla/5.0";
    acceptEncoding = "gzip";
    contentType = "text/plain";
    responseBody = "foo";
    noResponse = false;

    upResponse.setError(RuntimePb.APIResponse.ERROR.OK_VALUE);
  }

  /** @return the actual compressed compressed buffer for further testing */
  private byte[] compressAndAssertRoundTrip(String input) throws IOException {
    byte[] compressedBytes = HttpCompression.compress(ByteString.copyFromUtf8(input));
    byte[] decompressedBytes = uncompress(compressedBytes);
    assertThat(decompressedBytes).hasLength(Utf8.encodedLength(input));
    return compressedBytes;
  }

  /**
   * Uncompress a compressed byte buffer
   *
   * @param content the compressed buffer
   * @return the buffer decompressed
   */
  private static byte[] uncompress(byte[] content) throws IOException {
    GZIPInputStream zis = new GZIPInputStream(new ByteArrayInputStream(content));
    try {
      return ByteStreams.toByteArray(zis);
    } finally {
      Closeables.closeQuietly(zis);
    }
  }

  private void initResponse() {
    // Setup the UPRequest/UPResponse headers.
    UPRequest.Builder upRequestBuilder = upRequest.toBuilder();
    upRequestBuilder.getRequestBuilder().addHeaders(header("user-agent", userAgent));
    upRequestBuilder.addRuntimeHeaders(header("accept-encoding", acceptEncoding));
    upRequest = upRequestBuilder.buildPartial();
    upResponse.addHttpOutputHeaders(header("content-type", contentType));
    upResponse.setHttpResponseCodeAndResponse(HttpURLConnection.HTTP_OK, responseBody);

    if (noResponse) {
      upResponse.clearHttpResponse();
    }
  }

  private void attemptCompression(boolean expectCompression) throws IOException {
    // By default, don't expect that we have to uncompress for clients.
    attemptCompression(expectCompression, false);
  }

  private void attemptCompression(boolean expectCompression, boolean uncompressForClient)
      throws IOException {
    initResponse();
    HttpCompression compression = new HttpCompression();

    // Verify that AttemptCompression() gives us the right answer.
    assertThat(upResponse.isInitialized()).isTrue();
    assertThat(compression.attemptCompression(upRequest, upResponse)).isEqualTo(expectCompression);

    assertThat(upResponse.isInitialized()).isTrue();

    if (expectCompression) {
      // Verify that the response was compressed.
      byte[] compressed = HttpCompression.compress(ByteString.copyFromUtf8(responseBody));
      assertThat(upResponse.getHttpResponse().getResponse().toByteArray()).isEqualTo(compressed);

      // Verify that the response headers were set.
      String contentEncoding =
          compression.getHeader(upResponse.getRuntimeHeadersList(), HttpHeaders.CONTENT_ENCODING);

      assertThat(compression.isContentGzipEncoded(contentEncoding)).isTrue();
      assertThat(contentEncoding).isEqualTo("gzip");

      String noCompress =
          compression.getHeader(upResponse.getRuntimeHeadersList(), "X-Google-NoCompress");
      assertThat(noCompress).isNull();

      assertThat(upResponse.getHttpResponse().getUncompressedSize())
          .isEqualTo(responseBody.length());

    } else if (!alreadyCompressed) {
      assertThat(upResponse.getHttpResponse().getResponse().toStringUtf8()).isEqualTo(responseBody);
      String contentEncoding =
          compression.getHeader(upResponse.getRuntimeHeadersList(), HttpHeaders.CONTENT_ENCODING);

      assertThat(contentEncoding).isNotEqualTo("gzip");
      assertThat(upResponse.getHttpResponse().hasUncompressedSize()).isFalse();
    }

    assertThat(upResponse.getHttpResponse().getUncompressForClient())
        .isEqualTo(uncompressForClient);
  }

  private void expectCompression(boolean expectCompression) throws IOException {
    shouldCompress(expectCompression);
    attemptCompression(expectCompression);
  }

  private void shouldCompress(boolean shouldCompress) {
    // By default, check logic for for HTTP clients, not Gfe.
    shouldCompress(shouldCompress, false);
  }

  private void shouldCompress(boolean shouldCompress, boolean compressForGfe) {
    HttpCompression compression = new HttpCompression();
    assertThat(compression.shouldCompress(compressForGfe, userAgent, acceptEncoding, contentType))
        .isEqualTo(shouldCompress);
  }

  // The tests now...
  @Test
  public void testShortString() throws IOException {
    compressAndAssertRoundTrip("short string");
  }

  @Test
  public void testLongStringCompressesWell() throws IOException {
    String input =
        "something that should compress reasonably well "
            + "something that should compress reasonably well "
            + "something that should compress reasonably well "
            + "something that should compress reasonably well ";
    byte[] output = compressAndAssertRoundTrip(input);
    assertThat(output.length).isLessThan(input.length());
  }

  @Test
  public void testTrivial() throws IOException {
    expectCompression(true);
  }

  @Test
  public void testEmptyResponse() throws IOException {
    // Test that we won't compress a size 0 response, even though we could.
    responseBody = "";
    shouldCompress(true);
    attemptCompression(false);
  }

  @Test
  public void testNoResponse() throws IOException {
    // Test that we won't compress a size 0 response, even though we could.
    responseBody = "";
    noResponse = true;
    shouldCompress(true);
    attemptCompression(false);
  }

  @Test
  public void testShortResponse() throws IOException {
    expectCompression(true);
    assertThat(responseBody.length()).isLessThan(upResponse.getHttpResponse().getResponse().size());
  }

  @Test
  public void testLongResponse() throws IOException {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 5000; i++) {
      sb.append("a");
    }
    responseBody = sb.toString();
    expectCompression(true);
    assertThat(responseBody.length())
        .isGreaterThan(upResponse.getHttpResponse().getResponse().size());
  }

  @Test
  public void testEmptyUserAgent() throws IOException {
    userAgent = "";
    expectCompression(false);
  }

  @Test
  public void testBadUserAgent() throws IOException {
    userAgent = "unrecognized";
    expectCompression(false);
  }

  @Test
  public void testBadUserAgent_gzipGfe() throws IOException {
    userAgent = "unrecognized,gzip(gfe)";
    shouldCompress(false, false);
    shouldCompress(true, true);
    attemptCompression(true, true);
  }

  @Test
  public void testUserAgent_gzipGfe_attemptCompression() throws IOException {
    userAgent = "unrecognized";
    attemptCompression(false, false);

    UPRequest.Builder upRequestBuilder = upRequest.toBuilder();
    upRequestBuilder.getRequestBuilder().setGzipGfe(true);
    upRequest = upRequestBuilder.buildPartial();
    attemptCompression(true, true);
  }

  @Test
  public void testUserAgent_gzip_gzipGfe() throws IOException {
    userAgent = "Foobar/1.2.3 (iPhone7,2; iOS 9.3.1; gzip),gzip(gfe)";
    shouldCompress(true, true);
    attemptCompression(true);
  }

  @Test
  public void testUserAgent_acceptEncoding_gzip_gzipGfe() throws IOException {
    acceptEncoding = "gzip,gzip(gfe)";
    userAgent = "Foobar/1.2.3 (iPhone7,2; iOS 9.3.1; gzip),gzip(gfe)";
    shouldCompress(true, true);
    attemptCompression(true);
  }

  @Test
  public void testUserAgent_acceptEncoding_gzip_gzipGfe_json() throws IOException {
    contentType = "application/json; charset=UTF-8";
    acceptEncoding = "gzip,gzip(gfe)";
    userAgent = "Foobar/1.2.3 (iPhone7,2; iOS 9.3.1; gzip),gzip(gfe)";
    shouldCompress(true, true);
    attemptCompression(true, false);
  }

  @Test
  public void testEmptyAcceptEncoding() throws IOException {
    acceptEncoding = "";
    expectCompression(false);
  }

  @Test
  public void testBadAcceptEncoding() throws IOException {
    acceptEncoding = "unrecognized";
    expectCompression(false);
  }

  @Test
  public void testEmptyContentType() throws IOException {
    contentType = "";
    expectCompression(false);
  }

  @Test
  public void testBadContentType() throws IOException {
    contentType = "unrecognized";
    expectCompression(false);
  }

  @Test
  public void testBadAcceptEncoding_gzipGfe() throws IOException {
    userAgent = "gzip(gfe)";
    contentType = "unrecognized";
    shouldCompress(false, false);
    shouldCompress(false, true);
    attemptCompression(false, false);
  }

  @Test
  public void testJPEGNotCompressed() throws IOException {
    contentType = "image/jpeg";
    expectCompression(false);
  }

  @Test
  public void testGIFNotCompressed() throws IOException {
    contentType = "image/gif";
    expectCompression(false);
  }

  @Test
  public void testAlreadyCompressed() throws IOException {
    upResponse.addRuntimeHeaders(header("content-encoding", "gzip"));
    shouldCompress(true);

    alreadyCompressed = true;
    attemptCompression(false);
    assertThat(upResponse.getHttpResponse().getResponse().toStringUtf8()).isEqualTo(responseBody);
  }

  private HttpPb.ParsedHttpHeader.Builder header(String key, String value) {
    return HttpPb.ParsedHttpHeader.newBuilder().setKey(key).setValue(value);
  }
}
