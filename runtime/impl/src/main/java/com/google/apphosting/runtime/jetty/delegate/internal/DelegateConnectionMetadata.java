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

import com.google.apphosting.runtime.jetty.delegate.DelegateConnector;
import com.google.apphosting.runtime.jetty.delegate.api.DelegateExchange;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.HostPort;

import java.net.SocketAddress;

public class DelegateConnectionMetadata extends Attributes.Lazy implements ConnectionMetaData
{
    private final DelegateExchange _exchange;
    private final DelegateConnection _connection;
    private final String _connectionId;
    private final HttpConfiguration _httpConfiguration;
    private final DelegateConnector _connector;

    public DelegateConnectionMetadata(DelegateEndpoint delegateEndpoint, DelegateConnection delegateConnection, DelegateConnector delegateConnector)
    {
        _exchange = delegateEndpoint.getDelegateExchange();
        _connectionId = delegateConnection.getId();
        _connector = delegateConnector;
        _httpConfiguration = delegateConnector.getHttpConfiguration();
        _connection = delegateConnection;
    }

    @Override
    public String getId()
    {
        return _connectionId;
    }

    @Override
    public HttpConfiguration getHttpConfiguration()
    {
        return _httpConfiguration;
    }

    @Override
    public HttpVersion getHttpVersion()
    {
        return HttpVersion.fromString(_exchange.getProtocol());
    }

    @Override
    public String getProtocol()
    {
        return _exchange.getProtocol();
    }

    @Override
    public Connection getConnection()
    {
        return _connection;
    }

    @Override
    public Connector getConnector()
    {
        return _connector;
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public boolean isSecure()
    {
        return false;
    }

    @Override
    public SocketAddress getRemoteSocketAddress()
    {
        return _exchange.getRemoteAddr();
    }

    @Override
    public SocketAddress getLocalSocketAddress()
    {
        return _exchange.getLocalAddr();
    }
}
