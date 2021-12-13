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

package com.google.apphosting.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

/**
 * Picks free server ports for use in tests. In Google's internal infrastructure, there can be
 * several unrelated tests running on the same machine, so in that environment there is a "port
 * server" which allocates ports in response to requests from the tests, and monitors the tests so
 * that those allocations can be freed when the tests exit. When we're not running in Google's
 * internal infrastructure, there probably aren't other tests running at the same time (or at least
 * not as much), so a more basic approach of just opening and closing an anonymous port is probably
 * good enough. Whatever the port number was of that port is almost certain to be free immediately
 * afterwards.
 */
public abstract class PortPicker {
  static final String PORT_SERVER_ENV_VAR = "PORTSERVER_PORT";

  public static PortPicker create() {
    return create(System.getenv());
  }

  static PortPicker create(Map<String, String> env) {
    String portServerPortString = env.get(PORT_SERVER_ENV_VAR);
    if (portServerPortString != null) {
      int portServerPort = Integer.parseInt(portServerPortString);
      int pid = getPid();
      return new ViaPortServer(pid, portServerPort);
    } else {
      return new ViaLocal();
    }
  }

  public abstract int pickUnusedPort();

  /**
   * Gets our pid so we can tell it to the port server. We're assuming the Google environment here,
   * so we know we can get the pid from /proc/self.
   */
  private static int getPid() {
    try {
      String pidString = new File("/proc/self").getCanonicalFile().getName();
      return Integer.parseInt(pidString);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static class ViaLocal extends PortPicker {
    @Override
    public int pickUnusedPort() {
      try (ServerSocket serverSocket = new ServerSocket(0)) {
        return serverSocket.getLocalPort();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static class ViaPortServer extends PortPicker {
    private final int pid;
    private final int portServerPort;

    ViaPortServer(int pid, int portServerPort) {
      this.pid = pid;
      this.portServerPort = portServerPort;
    }

    @Override
    public int pickUnusedPort() {
      try {
        return pickUnusedPortOrThrow();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private int pickUnusedPortOrThrow() throws IOException {
      String request = pid + "\n";
      try (Socket client = new Socket("localhost", portServerPort);
          OutputStream out = client.getOutputStream();
          InputStream in = client.getInputStream();
          ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
        // The protocol is really simple. We write our pid as a decimal integer followed by a
        // newline. The port server responds with another decimal integer followed by a newline,
        // which is the port we can use.
        out.write(request.getBytes(UTF_8));
        out.flush();
        int c;
        while ((c = in.read()) >= 0) {
          bout.write(c);
        }
        String portString = new String(bout.toByteArray(), UTF_8).trim();
        return Integer.parseInt(portString);
      }
    }
  }
}
