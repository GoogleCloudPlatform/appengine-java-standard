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

package com.google.apphosting.runtime.jetty94.delegate.impl;

import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.jetty94.delegate.api.DelegateExchange;
import com.google.protobuf.ByteString;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Callback;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public class DelegateRpcExchange implements DelegateExchange
{
    private static final Content.Chunk EOF = Content.Chunk.EOF;
    private final HttpPb.HttpRequest _request;
    private final AtomicReference<Content.Chunk> _content = new AtomicReference<>();
    private final MutableUpResponse _response;
    private final ByteBufferAccumulator accumulator = new ByteBufferAccumulator();
    private final CompletableFuture<Void> _completion = new CompletableFuture<>();

    public DelegateRpcExchange(RuntimePb.UPRequest request, MutableUpResponse response)
    {
        _request = request.getRequest();
        _response = response;
        _content.set(new ContentChunk(_request.getPostdata().toByteArray()));
    }

    @Override
    public String getRequestURI()
    {
        return _request.getUrl();
    }

    @Override
    public String getProtocol()
    {
        return _request.getHttpVersion();
    }

    @Override
    public String getMethod()
    {
        return _request.getOriginalRequestMethod();
    }

    @Override
    public HttpFields getHeaders()
    {
        HttpFields.Mutable httpFields = HttpFields.build();
        for (HttpPb.ParsedHttpHeader header : _request.getHeadersList())
        {
            httpFields.add(header.getKey(), header.getValue());
        }
        return httpFields.takeAsImmutable();
    }

    @Override
    public InetSocketAddress getRemoteAddr()
    {
        return InetSocketAddress.createUnresolved(_request.getUserIp(), 0);
    }

    @Override
    public InetSocketAddress getLocalAddr()
    {
        return InetSocketAddress.createUnresolved("0.0.0.0", 0);
    }

    @Override
    public Content.Chunk read()
    {
        return _content.getAndUpdate(chunk -> (chunk instanceof ContentChunk) ? EOF : chunk);
    }

    @Override
    public void demand(Runnable demandCallback)
    {
        demandCallback.run();
    }

    @Override
    public void fail(Throwable failure)
    {
        _content.set(Content.Chunk.from(failure));
    }

    @Override
    public void setStatus(int status)
    {
        _response.setHttpResponseCode(status);
    }

    @Override
    public void addHeader(String name, String value)
    {
        _response.addHttpOutputHeaders(HttpPb.ParsedHttpHeader.newBuilder()
                .setKey(name)
                .setValue(value));
    }

    @Override
    public void write(boolean last, ByteBuffer content, Callback callback)
    {
        accumulator.copyBuffer(content);
        callback.succeeded();
    }

    @Override
    public void succeeded()
    {
        _response.setHttpResponseResponse(ByteString.copyFrom(accumulator.takeByteBuffer()));
        _completion.complete(null);
    }

    @Override
    public void failed(Throwable x)
    {
        _completion.completeExceptionally(x);
    }

    public void awaitResponse() throws ExecutionException, InterruptedException
    {
        _completion.get();
    }
}
