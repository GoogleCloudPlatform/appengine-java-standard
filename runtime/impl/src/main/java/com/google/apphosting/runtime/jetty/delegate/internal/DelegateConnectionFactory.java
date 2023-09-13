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
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;

import java.util.Collections;
import java.util.List;

public class DelegateConnectionFactory implements ConnectionFactory
{
    private static final String DEFAULT_PROTOCOL = "jetty-delegate";
    private final String _protocol;

    public DelegateConnectionFactory()
    {
        this(null);
    }

    public DelegateConnectionFactory(String protocol)
    {
        _protocol = (protocol == null) ? DEFAULT_PROTOCOL : protocol;
    }

    @Override
    public String getProtocol()
    {
        return _protocol;
    }

    @Override
    public List<String> getProtocols()
    {
        return Collections.singletonList(_protocol);
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        return new DelegateConnection((DelegateConnector)connector, (DelegateEndpoint)endPoint);
    }
}
