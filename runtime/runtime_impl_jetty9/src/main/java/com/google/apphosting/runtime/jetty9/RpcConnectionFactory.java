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

package com.google.apphosting.runtime.jetty9;

import java.util.Collections;
import java.util.List;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

/**
 * RPC Implementation for the Jetty ConnectionFactory interface, in charge of
 * translating runtime protocol buffers to HTTP requests/responses.
 */
public class RpcConnectionFactory extends AbstractLifeCycle implements ConnectionFactory {

  @Override
  public String getProtocol() {
    return "RPC";
  }

  @Override
  public List<String> getProtocols() {
    return Collections.singletonList("RPC");
  }

  @Override
  public Connection newConnection(Connector connector, EndPoint endPoint) {
    return new RpcConnection((RpcConnector) connector, (RpcEndPoint) endPoint);
  }
}
