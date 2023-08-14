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

package com.google.apphosting.runtime.delegate;

import com.google.apphosting.runtime.delegate.api.DelegateExchange;
import com.google.apphosting.runtime.delegate.internal.DelegateConnection;
import com.google.apphosting.runtime.delegate.internal.DelegateConnectionFactory;
import com.google.apphosting.runtime.delegate.internal.DelegateEndpoint;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;

import java.io.IOException;

public class DelegateConnector extends AbstractConnector
{
    private final HttpConfiguration _httpConfiguration = new HttpConfiguration();

    public DelegateConnector(Server server)
    {
        this(server, null);
    }

    public DelegateConnector(Server server, String protocol)
    {
        super(server, null, null, null, 0, new DelegateConnectionFactory(protocol));
        _httpConfiguration.setSendDateHeader(false);
        _httpConfiguration.setSendServerVersion(false);
        _httpConfiguration.setSendXPoweredBy(false);
    }

    public HttpConfiguration getHttpConfiguration()
    {
        return _httpConfiguration;
    }

    public void service(DelegateExchange exchange) throws IOException
    {
        // TODO: recover existing endpoint and connection from WeakReferenceMap with request as key, or some other way of
        //  doing persistent connection. There is a proposal in the servlet spec to have connection IDs.
        DelegateEndpoint endPoint = new DelegateEndpoint(exchange);
        DelegateConnection connection = new DelegateConnection(this, endPoint);
        connection.handle();
    }

    @Override
    public Object getTransport()
    {
        return null;
    }

    @Override
    protected void accept(int acceptorID) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Accept not supported by this Connector");
    }

    public void run(Runnable runnable)
    {
        getExecutor().execute(runnable);
    }
}
