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

package com.google.apphosting.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.common.testing.GcFinalization;
import com.google.protobuf.ByteString;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TraceWriterMemoryTest {
  /**
   * Tests that a TraceWriter for a background request has a bound on the amount of memory it
   * can use.
   */
  @Test
  public void traceWriterDoesNotLeak() throws Exception {
    TraceWriter writer = createTraceWriter();
    Runtime runtime = Runtime.getRuntime();
    createManyApiSpans(writer);
    GcFinalization.awaitFullGc();
    createManyApiSpans(writer);
    GcFinalization.awaitFullGc();
    long freeAfterSecond = runtime.freeMemory();
    createManyApiSpans(writer);
    GcFinalization.awaitFullGc();
    long freeAfterThird = runtime.freeMemory();
    // TODO: why is it 100X bigger in Copybara than Blaze?
    assertThat(freeAfterThird - freeAfterSecond).isLessThan(400_000_000L);
  }

  private TraceWriter createTraceWriter() {
    UPRequest.Builder upRequest = UPRequest.newBuilder();
    upRequest.setRequestType(UPRequest.RequestType.BACKGROUND);
    TraceContextProto.Builder contextProto = upRequest.getTraceContextBuilder();
    contextProto.setTraceId(ByteString.copyFromUtf8("trace id"));
    contextProto.setSpanId(1L);
    contextProto.setTraceMask(1);
    MutableUpResponse upResponse = new MutableUpResponse();
    return TraceWriter.getTraceWriterForRequest(upRequest.buildPartial(), upResponse);
  }

  private void createManyApiSpans(TraceWriter writer) {
    Random random = new Random();
    writer.startRequestSpan("request " + random.nextInt(1_000_000));
    for (int i = 0; i < 1_000_000; i++) {
      CloudTraceContext apiContext = writer.startApiSpan(null, "package", "method");
      CloudTraceContext childContext =
          writer.startChildSpan(apiContext, "child " + random.nextInt(1_000_000));
      writer.endSpan(childContext);
      writer.endApiSpan(apiContext);
    }
    writer.endRequestSpan();
  }
}
