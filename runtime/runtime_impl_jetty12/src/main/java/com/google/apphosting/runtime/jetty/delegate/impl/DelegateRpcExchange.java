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

package com.google.apphosting.runtime.jetty.delegate.impl;

import static com.google.apphosting.runtime.AppEngineConstants.LEGACY_MODE;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.HttpPb.ParsedHttpHeader;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.jetty.delegate.api.DelegateExchange;
import com.google.common.base.Ascii;
import com.google.protobuf.ByteString;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.Callback;

public class DelegateRpcExchange implements DelegateExchange {
  private static final Content.Chunk EOF = Content.Chunk.EOF;
  private static final String X_GOOGLE_INTERNAL_SKIPADMINCHECK = "x-google-internal-skipadmincheck";
  private static final String SKIP_ADMIN_CHECK_ATTR = "com.google.apphosting.internal.SkipAdminCheck";

  private final HttpPb.HttpRequest _request;
  private final AtomicReference<Content.Chunk> _content = new AtomicReference<>();
  private final MutableUpResponse _response;
  private final ByteBufferAccumulator accumulator = new ByteBufferAccumulator();
  private final CompletableFuture<Void> _completion = new CompletableFuture<>();
  private final Attributes _attributes = new Attributes.Lazy();
  private final String _httpMethod;
  private final boolean _isSecure;

  public DelegateRpcExchange(RuntimePb.UPRequest request, MutableUpResponse response) {
    _request = request.getRequest();
    _response = response;
    _content.set(new ContentChunk(_request.getPostdata().toByteArray()));

    String protocol = _request.getProtocol();
    HttpMethod method =
        LEGACY_MODE ? HttpMethod.INSENSITIVE_CACHE.get(protocol) : HttpMethod.CACHE.get(protocol);
    _httpMethod = method != null ? method.asString() : protocol;

    final boolean skipAdmin = hasSkipAdminCheck(request);
    // Translate the X-Google-Internal-SkipAdminCheck to a servlet attribute.
    if (skipAdmin) {
      setAttribute(SKIP_ADMIN_CHECK_ATTR, true);

      // N.B.: If SkipAdminCheck is set, we're actually lying
      // to Jetty here to tell it that HTTPS is in use when it may not
      // be.  This is useful because we want to bypass Jetty's
      // transport-guarantee checks (to match Python, which bypasses
      // handler_security: for these requests), but unlike
      // authentication SecurityHandler does not provide an easy way to
      // plug in custom logic here.  I do not believe that our lie is
      // user-visible (ServletRequest.getProtocol() is unchanged).
      _isSecure = true;
    }
    else {
      _isSecure = _request.getIsHttps();
    }
  }

  private static boolean hasSkipAdminCheck(RuntimePb.UPRequest upRequest) {
    for (ParsedHttpHeader header : upRequest.getRuntimeHeadersList()) {
      if (Ascii.equalsIgnoreCase(X_GOOGLE_INTERNAL_SKIPADMINCHECK, header.getKey())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getRequestURI() {
    return _request.getUrl();
  }

  @Override
  public String getProtocol() {
    return _request.getHttpVersion();
  }

  @Override
  public String getMethod() {
    return _httpMethod;
  }

  @Override
  public HttpFields getHeaders() {
    HttpFields.Mutable httpFields = HttpFields.build();
    for (HttpPb.ParsedHttpHeader header : _request.getHeadersList()) {
      httpFields.add(header.getKey(), header.getValue());
    }
    return httpFields.asImmutable();
  }

  @Override
  public InetSocketAddress getRemoteAddr() {
    return InetSocketAddress.createUnresolved(_request.getUserIp(), 0);
  }

  @Override
  public InetSocketAddress getLocalAddr() {
    return InetSocketAddress.createUnresolved("0.0.0.0", 0);
  }

  @Override
  public boolean isSecure() {
    return _isSecure;
  }

  @Override
  public Content.Chunk read() {
    return _content.getAndUpdate(chunk -> (chunk instanceof ContentChunk) ? EOF : chunk);
  }

  @Override
  public void demand(Runnable demandCallback) {
    demandCallback.run();
  }

  @Override
  public void fail(Throwable failure) {
    _content.set(Content.Chunk.from(failure));
  }

  @Override
  public void setStatus(int status) {
    _response.setHttpResponseCode(status);
  }

  @Override
  public void addHeader(String name, String value) {
    _response.addHttpOutputHeaders(
        HttpPb.ParsedHttpHeader.newBuilder().setKey(name).setValue(value));
  }

  @Override
  public void write(boolean last, ByteBuffer content, Callback callback) {
    if (content != null) accumulator.copyBuffer(content);
    callback.succeeded();
  }

  @Override
  public void succeeded() {
    _response.setHttpResponseResponse(ByteString.copyFrom(accumulator.takeByteBuffer()));
    _response.setError(RuntimePb.UPResponse.ERROR.OK_VALUE);
    _completion.complete(null);
  }

  @Override
  public void failed(Throwable x) {
    _completion.completeExceptionally(x);
  }

  public void awaitResponse() throws ExecutionException, InterruptedException {
    _completion.get();
  }

  @Override
  public Object removeAttribute(String name) {
    return _attributes.removeAttribute(name);
  }

  @Override
  public Object setAttribute(String name, Object attribute) {
    return _attributes.setAttribute(name, attribute);
  }

  @Override
  public Object getAttribute(String name) {
    return _attributes.getAttribute(name);
  }

  @Override
  public Set<String> getAttributeNameSet() {
    return _attributes.getAttributeNameSet();
  }
}
