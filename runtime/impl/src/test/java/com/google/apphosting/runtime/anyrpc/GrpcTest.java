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

package com.google.apphosting.runtime.anyrpc;

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.runtime.grpc.GrpcClientContext;
import com.google.apphosting.runtime.grpc.GrpcPlugin;
import com.google.apphosting.testing.PortPicker;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

/** Loopback GRPC-to-GRPC test. */
@RunWith(JUnit4.class)
public class GrpcTest extends AbstractRpcCompatibilityTest {
  private static Logger grpcManagedChannelLogger;
  private static Logger grpcDnsNameResolverLogger;

  // Disable gRPC Logging for lost channels, and dns name resover.
  // Save a ref to avoid garbage collection.
  // Ignore automated suggests to make those fields be local variables in this method!
  @BeforeClass
  public static void beforeClass() {
    grpcManagedChannelLogger = Logger.getLogger("io.grpc.internal.ManagedChannelOrphanWrapper");
    grpcManagedChannelLogger.setLevel(Level.OFF);
    grpcDnsNameResolverLogger = Logger.getLogger("io.grpc.internal.DnsNameResolver");
    grpcDnsNameResolverLogger.setLevel(Level.OFF);
  }

  private GrpcPlugin rpcPlugin;

  @Override
  AnyRpcPlugin getClientPlugin() {
    return rpcPlugin;
  }

  @Override
  AnyRpcPlugin getServerPlugin() {
    return rpcPlugin;
  }

  @Override
  int getPacketSize() {
    return 65536;
  }

  @Before
  public void setUp() throws IOException, InterruptedException {
    MockitoAnnotations.initMocks(this);
    rpcPlugin = new GrpcPlugin();
    int serverPort = PortPicker.create().pickUnusedPort();
    rpcPlugin.initialize(serverPort);
  }

  @Override
  AnyRpcClientContextFactory newRpcClientContextFactory() {
    return () -> new GrpcClientContext(getClockHandler().clock);
  }

  @Override
  ClientInterfaces.EvaluationRuntimeClient newEvaluationRuntimeClient() {
    int serverPort = rpcPlugin.getServerPort();
    ManagedChannel channel =
        NettyChannelBuilder.forAddress("localhost", serverPort)
            .negotiationType(NegotiationType.PLAINTEXT)
            .build();
    return new GrpcClients.GrpcEvaluationRuntimeClient(channel);
  }

  @Override
  ClientInterfaces.CloneControllerClient newCloneControllerClient() {
    int serverPort = rpcPlugin.getServerPort();
    ManagedChannel channel =
        NettyChannelBuilder.forAddress("localhost", serverPort)
            .negotiationType(NegotiationType.PLAINTEXT)
            .build();
    return new GrpcClients.GrpcCloneControllerClient(channel);
  }

  @Override
  ClockHandler getClockHandler() {
    return new GrpcClockHandler(new FakeClock());
  }

  private static class GrpcClockHandler extends ClockHandler {
    GrpcClockHandler(Clock clock) {
      super(clock);
    }

    @Override
    void advanceClock() {
      ((FakeClock) clock).incrementTime(1000);
    }

    @Override
    void assertStartTime(long expectedStartTime, long reportedStartTime) {
      assertThat(reportedStartTime).isEqualTo(expectedStartTime);
    }
  }

  private static class FakeClock extends Clock {
    private final AtomicLong nowMillis = new AtomicLong(1000000000L);

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(nowMillis.get());
    }

    void incrementTime(long millis) {
      nowMillis.addAndGet(millis);
    }

    @Override
    public ZoneId getZone() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }
  }
}
