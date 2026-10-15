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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.IO;

/**
 * A wrapper for Jetty to add support for SO_REUSEPORT. (Jetty 9.x does not directly expose it as a
 * setting.) SO_REUSEPORT only works when running with a Java 9+ JDK.
 */
public class JettyServerConnectorWithReusePort extends ServerConnector {

  private final boolean reusePort;

  public JettyServerConnectorWithReusePort(Server server, boolean reusePort) {
    super(server);
    this.reusePort = reusePort;
  }

  /** Set SO_REUSEPORT. */
  static void setReusePort(ServerSocketChannel serverChannel) throws IOException {
    serverChannel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
  }

  @Override
  protected ServerSocketChannel openAcceptChannel() throws IOException {
    InetSocketAddress bindAddress =
        getHost() == null
            ? new InetSocketAddress(getPort())
            : new InetSocketAddress(getHost(), getPort());

    ServerSocketChannel serverChannel = ServerSocketChannel.open();

    if (reusePort) {
      setReusePort(serverChannel);
    }
    serverChannel.socket().setReuseAddress(getReuseAddress());

    try {
      serverChannel.socket().bind(bindAddress, getAcceptQueueSize());
    } catch (Throwable e) {
      IO.close(serverChannel);
      throw new IOException("Failed to bind to " + bindAddress, e);
    }

    return serverChannel;
  }
}
