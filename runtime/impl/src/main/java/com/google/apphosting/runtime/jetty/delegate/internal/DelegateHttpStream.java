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

package com.google.apphosting.runtime.jetty.delegate.internal;

import com.google.apphosting.runtime.jetty.delegate.api.DelegateExchange;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class DelegateHttpStream implements HttpStream
{
    private static final Logger LOG = LoggerFactory.getLogger(DelegateHttpStream.class);

    private final DelegateEndpoint _endpoint;
    private final DelegateConnection _connection;
    private final HttpChannel _httpChannel;
    private final long _nanoTimestamp = System.nanoTime();
    private final AtomicBoolean _committed = new AtomicBoolean(false);

    public DelegateHttpStream(DelegateEndpoint endpoint, DelegateConnection connection, HttpChannel httpChannel)
    {
        _endpoint = endpoint;
        _connection = connection;
        _httpChannel = httpChannel;
    }

    @Override
    public String getId()
    {
        return _connection.getId();
    }

    @Override
    public Content.Chunk read()
    {
        return _endpoint.getDelegateExchange().read();
    }

    @Override
    public void demand()
    {
        _endpoint.getDelegateExchange().demand(_httpChannel::onContentAvailable);
    }

    @Override
    public void prepareResponse(HttpFields.Mutable headers)
    {
        // Do nothing.
    }

    @Override
    public void send(MetaData.Request request, MetaData.Response response, boolean last, ByteBuffer content, Callback callback)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("send() {}, {}, last=={}", request, BufferUtil.toDetailString(content), last);
        _committed.set(true);

        DelegateExchange delegateExchange = _endpoint.getDelegateExchange();
        if (response != null)
        {
            delegateExchange.setStatus(response.getStatus());
            for (HttpField field : response.getHttpFields())
            {
                delegateExchange.addHeader(field.getName(), field.getValue());
            }
        }

        delegateExchange.write(last, content, callback);
    }

    @Override
    public void push(MetaData.Request request)
    {
        throw new UnsupportedOperationException("push not supported");
    }

    @Override
    public long getIdleTimeout() {
        return -1;
    }

    @Override
    public void setIdleTimeout(long idleTimeoutMs) {
    }

    @Override
    public boolean isCommitted()
    {
        return _committed.get();
    }

    @Override
    public Throwable consumeAvailable()
    {
        return HttpStream.consumeAvailable(this, _httpChannel.getConnectionMetaData().getHttpConfiguration());
    }

    @Override
    public void succeeded()
    {
        _endpoint.getDelegateExchange().succeeded();
    }

    @Override
    public void failed(Throwable x)
    {
        _endpoint.getDelegateExchange().failed(x);
    }
}