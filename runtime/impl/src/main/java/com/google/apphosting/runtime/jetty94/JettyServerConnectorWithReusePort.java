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

package com.google.apphosting.runtime.jetty94;

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.util.Objects;
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

  /**
   * Set SO_REUSEPORT via reflection. As of this writing, google3 is building for Java 8 but running
   * with a Java 11 JVM. Thus we have to use reflection to fish out the SO_REUSEPORT setting.
   */
  static void setReusePort(ServerSocketChannel serverChannel) throws IOException {
    if (Objects.equals(JAVA_SPECIFICATION_VERSION.value(), "1.8")) {
      throw new IOException("Cannot use SO_REUSEPORT with Java <9.");
    }

    Object o;
    try {
      Field f = StandardSocketOptions.class.getField("SO_REUSEPORT");
      o = f.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Could not set SO_REUSEPORT as requested", e);
    }

    @SuppressWarnings("unchecked") // safe by specification
    SocketOption<Boolean> so = (SocketOption<Boolean>) o;

    serverChannel.setOption(so, true);
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
