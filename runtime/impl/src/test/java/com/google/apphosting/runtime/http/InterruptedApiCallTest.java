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

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.runtime.ApiProxyImpl;
import com.google.apphosting.runtime.MutableUpResponse;
import com.google.apphosting.runtime.TraceWriter;
import com.google.apphosting.runtime.grpc.FakeApiProxyImplFactory;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests interrupting API calls with {@code Thread.interrupt()}. */
@RunWith(Parameterized.class)
public class InterruptedApiCallTest extends HttpApiProxyImplTestBase {
  /**
   * Creates a thread that repeatedly does API calls, and sprays it with interrupts to verify that
   * it only gets expected exceptions.
   */
  @Test
  public void interruptedApiCall() {
    AtomicBoolean stop = new AtomicBoolean();
    try {
      interruptedApiCall(stop);
    } finally {
      stop.set(true);
    }
  }

  private void interruptedApiCall(AtomicBoolean stop) {
    HttpApiHostClient.Config interruptibleConfig =
        config.toBuilder()
            // With the Jetty client, this test occasionally gets ClosedChannelException. Since
            // we're
            // reluctant to consider that exception as meaning cancellation everywhere, this special
            // test flag means we will treat it so just here.
            .setTreatClosedChannelAsCancellation(true)
            .build();
    HttpApiHostClient apiHostClient =
        HttpApiHostClient.create(fakeHttpApiHost.getUrl().toString(), interruptibleConfig);
    ApiProxyImpl apiProxyImpl = FakeApiProxyImplFactory.newApiProxyImpl(apiHostClient);
    ApiProxyImpl.EnvironmentImpl environment =
        FakeApiProxyImplFactory.fakeEnvironment(
            apiProxyImpl,
            FAKE_SECURITY_TICKET,
            new TraceWriter(cloudTraceContext, new MutableUpResponse()));
    ApiClientTask apiClientTask = new ApiClientTask(stop, apiProxyImpl, environment);
    Thread apiClientThread = new Thread(apiClientTask);
    apiClientThread.start();
    for (int i = 0; i < 1000; i++) {
      apiClientThread.interrupt();
      while (apiClientThread.isInterrupted()) {
        Thread.yield();
      }
    }
    assertThat(apiClientTask.exceptionInMakeAsyncCall.get()).isNull();
    assertThat(apiClientTask.exceptionInFutureGet.get()).isNull();
  }

  private static class ApiClientTask implements Runnable {
    final AtomicBoolean stop;
    final ApiProxyImpl apiProxyImpl;
    final ApiProxyImpl.EnvironmentImpl environment;
    final AtomicReference<Exception> exceptionInMakeAsyncCall = new AtomicReference<>();
    final AtomicReference<Exception> exceptionInFutureGet = new AtomicReference<>();

    ApiClientTask(
        AtomicBoolean stop, ApiProxyImpl apiProxyImpl, ApiProxyImpl.EnvironmentImpl environment) {
      this.stop = stop;
      this.apiProxyImpl = apiProxyImpl;
      this.environment = environment;
    }

    @Override
    public void run() {
      ApiProxy.ApiConfig apiConfig = new ApiProxy.ApiConfig();
      apiConfig.setDeadlineInSeconds(1.0);
      byte[] payload = new byte[100];
      new Random().nextBytes(payload);
      while (!stop.get()
          && exceptionInMakeAsyncCall.get() == null
          && exceptionInFutureGet.get() == null) {
        Future<byte[]> future;
        try {
          future =
              apiProxyImpl.makeAsyncCall(
                  environment, ECHO_SERVICE, ECHO_METHOD, payload, apiConfig);
        } catch (Exception e) {
          // We don't expect makeAsyncCall to get any exceptions even if interrupts are flying.
          exceptionInMakeAsyncCall.set(e);
          break;
        }
        // Swallow the interrupt if it has already happened.
        Thread.interrupted();
        try {
          byte[] response = future.get();
          assertThat(response).isEqualTo(payload);
        } catch (InterruptedException e) {
          // OK: we were interrupted in Future.get while waiting for the result.
        } catch (ApiProxy.CancelledException e) {
          // OK: this is a reasonable exception to get for being cancelled.
        } catch (ExecutionException e) {
          if (!(e.getCause() instanceof ApiProxy.CancelledException)) {
            exceptionInFutureGet.set(e);
          }
        } catch (Exception e) {
          exceptionInFutureGet.set(e);
        }
      }
    }
  }
}
