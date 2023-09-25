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

package com.google.apphosting.runtime.http;
import static com.google.apphosting.runtime.http.HttpApiProxyImplTestBase.ECHO_METHOD;
import static com.google.apphosting.runtime.http.HttpApiProxyImplTestBase.ECHO_SERVICE;
import static com.google.apphosting.runtime.http.HttpApiProxyImplTestBase.FAKE_SECURITY_TICKET;
import static com.google.common.base.StandardSystemProperty.FILE_SEPARATOR;
import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertThrows;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.protos.api.RemoteApiPb;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.TraceWriter;
import com.google.apphosting.runtime.grpc.FakeApiProxyImplFactory;
import com.google.apphosting.testing.PortPicker;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ApiHostSuspendResumeTest {
  /**
   * Tests that we can make API calls, then disable ApiHostClient, and there are no connections to
   * the API server. When we reenable ApiHostClient we expect to be able to make API calls anew.
   */
  @Test
  public void suspendResume() throws IOException, InterruptedException {
    if (FILE_SEPARATOR.value().equals("\\")
        || Ascii.toLowerCase(OS_NAME.value()).startsWith("mac os x")) {
      // Test relies on /prof/self/* files not present on Windows or Mac systems.
      return;
    }

    int port = PortPicker.create().pickUnusedPort();
    FakeHttpApiHost fakeHttpApiHost = FakeHttpApiHost.create(port, new EchoHandler());
    try {
      doSuspendResume(fakeHttpApiHost, port);
    } finally {
      fakeHttpApiHost.stop();
    }
  }

  private void doSuspendResume(FakeHttpApiHost fakeHttpApiHost, int port) throws IOException {
    URL url = fakeHttpApiHost.getUrl();
    HttpApiHostClient apiHostClient =
        HttpApiHostClient.create(url.toString(), HttpApiHostClient.Config.builder().build());
    assertThat(connectionsTo(port)).isEmpty();

    ApiProxyImpl apiProxyImpl = FakeApiProxyImplFactory.newApiProxyImpl(apiHostClient);
    ApiProxyImpl.EnvironmentImpl environment = newEnvironmentImpl(apiProxyImpl);
    byte[] requestPayload = {1, 2, 3, 4};
    byte[] responsePayload =
        apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, ECHO_METHOD, requestPayload);
    assertThat(responsePayload).isEqualTo(requestPayload);
    assertThat(connectionsTo(port)).isNotEmpty();

    apiHostClient.disable();
    assertThat(connectionsTo(port)).isEmpty();
    assertThrows(
        ApiProxy.CancelledException.class,
        () -> apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, ECHO_METHOD, requestPayload));

    apiHostClient.enable();
    responsePayload =
        apiProxyImpl.makeSyncCall(environment, ECHO_SERVICE, ECHO_METHOD, requestPayload);
    assertThat(responsePayload).isEqualTo(requestPayload);
  }

  private static final ImmutableList<String> PROC_SELF_NET_TCP =
      ImmutableList.of("/proc/self/net/tcp", "/proc/self/net/tcp6");

  private static final ImmutableSet<String> LOCAL_ADDRESSES =
      ImmutableSet.of(
          "0100007F",
          "0000000000000000FFFF00000100007F",
          localHexAddress(),
          String.format("0000000000000000FFFF0000%s", localHexAddress()),
          // This is the IPv6 loopback address, ::1, as it appears in /proc/self/net/tcp.
          middleEndianHexAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}));

  // Figure out the connections to localhost:<port> by parsing /proc/self/net/tcp.
  // The poorly-documented format is here:
  //   https://www.kernel.org/doc/Documentation/networking/proc_net_tcp.txt
  // In summary, the space-separated fields are:
  //   0 entry number
  //   1 local address:port
  //   2 remote address:port
  //   3 connection state
  //   plus other fields we're not interested in.
  // The addresses and ports are in hex: four-digit hex for the two-byte port, and
  // 8-digit or 32-digit hex for the IPv4 or IPv6 address. There's some vagueness about the
  // endianness of those addresses. The values above were observed on an amd64 Ubuntu box, and
  // might need to be supplemented if this test is run on big-endian architectures.
  // The connection state is (I think) a bitwise OR of the values here:
  //   http://cs/kernel/include/net/tcp_states.h?l=17&rcl=47605a82a36d08ed0065af520b467964d872c94c
  // In practice, 01 means an established connection.
  private ImmutableList<String> connectionsTo(int port) throws IOException {
    String colonPort = Ascii.toUpperCase(String.format(":%04x", port));
    ImmutableList.Builder<String> connections = ImmutableList.builder();
    for (String tcp : PROC_SELF_NET_TCP) {
      Path path = Paths.get(tcp);
      for (String line : Files.readAllLines(path, US_ASCII)) {
        line = line.trim();
        List<String> fields = Splitter.on(' ').splitToList(line);
        String remoteAddress = fields.get(2);
        String state = fields.get(3);
        if (state.equals("01") && remoteAddress.endsWith(colonPort)) {
          int lastColon = remoteAddress.lastIndexOf(':');
          assertThat(lastColon).isGreaterThan(0);
          String remoteIp = remoteAddress.substring(0, lastColon);
          if (LOCAL_ADDRESSES.contains(remoteIp)) {
            connections.add(remoteIp);
          }
        }
      }
    }
    return connections.build();
  }

  // Return the local address as it appears in /proc/self/net/tcp.
  private static String localHexAddress() {
    InetAddress local;
    try {
      local = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new AssertionError(e);
    }
    return middleEndianHexAddress(local.getAddress());
  }

  // This funky middle-endian handling of IPv6 addresses is what we observe on little-endian boxes.
  // Also see
  // http://google3/java/com/google/common/unix/FileDescriptorUtils.java?l=204&rcl=144790929
  private static String middleEndianHexAddress(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bytes.length; i += 4) {
      for (int j = 3; j >= 0; j--) {
        sb.append(Ascii.toUpperCase(String.format("%02x", bytes[i + j] & 255)));
      }
    }
    return sb.toString();
  }

  private ApiProxyImpl.EnvironmentImpl newEnvironmentImpl(ApiProxyImpl apiProxyImpl) {
    Random random = new Random(1234);
    // Generate a traceId with the same size as the one generated by the Appserver.
    byte[] traceId = new byte[18];
    random.nextBytes(traceId);
    CloudTraceContext cloudTraceContext =
        new CloudTraceContext(traceId, /* spanId= */ random.nextLong(), /* traceMask= */ 1);
    return FakeApiProxyImplFactory.fakeEnvironment(
        apiProxyImpl,
        FAKE_SECURITY_TICKET,
        new TraceWriter(cloudTraceContext, new MutableUpResponse()));
  }

  private static class EchoHandler implements FakeHttpApiHost.ApiRequestHandler {
    @Override
    public RemoteApiPb.Response handle(RemoteApiPb.Request request) {
      if (!request.getServiceName().equals(ECHO_SERVICE)
          || !request.getMethod().equals(ECHO_METHOD)) {
        throw new IllegalArgumentException("Unexpected request: " + request);
      }
      return RemoteApiPb.Response.newBuilder().setResponse(request.getRequest()).build();
    }
  }
}
