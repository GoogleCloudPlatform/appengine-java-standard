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

import static com.google.apphosting.runtime.TraceWriter.MAX_DICTIONARY_SIZE;
import static com.google.common.truth.Truth.assertThat;

import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.SpanDetails.StackTraceDetails;
import com.google.apphosting.base.protos.SpanDetails.StackTraceDetails.StackFrame;
import com.google.apphosting.base.protos.SpanKindOuterClass;
import com.google.apphosting.base.protos.TraceEvents.SpanEventProto;
import com.google.apphosting.base.protos.TraceEvents.SpanEventsProto;
import com.google.apphosting.base.protos.TraceEvents.StartSpanProto;
import com.google.apphosting.base.protos.TraceEvents.TraceEventsProto;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TraceWriter.
 */
@RunWith(JUnit4.class)
public class TraceWriterTest {
  private MutableUpResponse upResponse;
  private TraceWriter writer;

  @Before
  public void setUp() throws Exception {
    UPRequest.Builder upRequest = UPRequest.newBuilder();
    TraceContextProto.Builder contextProto = upRequest.getTraceContextBuilder();
    contextProto.setTraceId(ByteString.copyFromUtf8("trace id"));
    contextProto.setSpanId(1L);
    contextProto.setTraceMask(1);
    upResponse = new MutableUpResponse();
    writer = TraceWriter.getTraceWriterForRequest(upRequest.buildPartial(), upResponse);
  }

  @Test
  public void testOneChild() throws InvalidProtocolBufferException {
    writer.startRequestSpan("http://foo.com/request");

    CloudTraceContext childContext = writer.startApiSpan(null, "package", "method");
    StackTraceElement[] stackTrace = new StackTraceElement[140];
    // Kept.
    stackTrace[0] = new StackTraceElement("class0", "method0", "file0", 1);
    // Discarded due to null file name.
    stackTrace[1] = new StackTraceElement("class1", "method1", null, 1);
    // Discarded due to non-positive line number.
    stackTrace[2] = new StackTraceElement("class1", "method2", "file2", 0);
    // Discarded due to non-positive line number.
    stackTrace[3] = new StackTraceElement("class1", "method3", "file3", -1);
    // Discarded due to null file name and non-positive line number.
    stackTrace[4] = new StackTraceElement("class1", "method4", null, 0);
    // Only up to MAX_STACK_DEPTH=128 in total will be kept.
    for (int i = 5; i < 140; i++) {
      stackTrace[i] = new StackTraceElement("class", "method", "file", i);
    }
    writer.addStackTrace(childContext, stackTrace);
    writer.endApiSpan(childContext);

    writer.endRequestSpan();
    writer.flushTrace();

    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsList()).hasSize(2);

    // Verify dictionary.
    assertThat(traceEvents.getDictionaryEntriesList()).hasSize(1);
    StackTraceDetails stackTraceDetails = traceEvents.getDictionaryEntries(0).getStackTraceValue();
    assertThat(stackTraceDetails.getStackFrameList()).hasSize(TraceWriter.MAX_STACK_DEPTH);
    StackFrame stackFrame = stackTraceDetails.getStackFrame(0);
    assertThat(stackFrame.getClassName()).isEqualTo("class0");
    assertThat(stackFrame.getMethodName()).isEqualTo("method0");
    assertThat(stackFrame.getFileName()).isEqualTo("file0");
    assertThat(stackFrame.getLineNumber()).isEqualTo(1);
    for (int i = 1; i < TraceWriter.MAX_STACK_DEPTH; i++) {
      StackFrame frame = stackTraceDetails.getStackFrame(i);
      assertThat(frame.getClassName()).isEqualTo("class");
      assertThat(frame.getMethodName()).isEqualTo("method");
      assertThat(frame.getFileName()).isEqualTo("file");
      assertThat(frame.getLineNumber()).isEqualTo(i + 4);
    }

    // Verify request span.
    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    long requestSpanId = spanEvents.getSpanId().getId();
    assertThat(spanEvents.getEventList()).hasSize(2);
    SpanEventProto startSpanEvent = spanEvents.getEvent(0);
    StartSpanProto startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_SERVER);
    assertThat(startSpan.getName()).isEqualTo("http://foo.com/request");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(1L);
    SpanEventProto endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
    assertThat(spanEvents.getFullSpan()).isTrue();

    // Verify API span.
    spanEvents = traceEvents.getSpanEvents(1);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(3);

    startSpanEvent = spanEvents.getEvent(0);
    startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/package.method");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);

    SpanEventProto annotateSpanEvent = spanEvents.getEvent(1);
    assertThat(annotateSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
    assertThat(annotateSpanEvent.getAnnotateSpan().getSpanDetails().hasStackTraceDetails())
        .isFalse();
    assertThat(annotateSpanEvent.getAnnotateSpan().getSpanDetails().getStackTraceHashId())
        .isEqualTo(traceEvents.getDictionaryEntries(0).getKey());

    endSpanEvent = spanEvents.getEvent(2);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(annotateSpanEvent.getTimestamp());

    assertThat(spanEvents.getFullSpan()).isTrue();
  }

  @Test
  public void testTwoChildren() throws InvalidProtocolBufferException {
    writer.startRequestSpan("http://foo.com/request");

    CloudTraceContext childContext1 = writer.startApiSpan(null, "package", "method1");
    CloudTraceContext childContext2 = writer.startApiSpan(null, "package", "method2");
    writer.endApiSpan(childContext1);
    writer.endApiSpan(childContext2);

    writer.endRequestSpan();
    writer.flushTrace();

    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(3);

    // Verify request span.
    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    long requestSpanId = spanEvents.getSpanId().getId();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    SpanEventProto startSpanEvent = spanEvents.getEvent(0);
    StartSpanProto startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_SERVER);
    assertThat(startSpan.getName()).isEqualTo("http://foo.com/request");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(1L);
    SpanEventProto endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
    assertThat(spanEvents.getFullSpan()).isTrue();

    // Verify API spans.
    spanEvents = traceEvents.getSpanEvents(1);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    startSpanEvent = spanEvents.getEvent(0);
    startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/package.method1");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);
    endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
    assertThat(spanEvents.getFullSpan()).isTrue();

    spanEvents = traceEvents.getSpanEvents(2);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    startSpanEvent = spanEvents.getEvent(0);
    startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.RPC_CLIENT);
    assertThat(startSpan.getName()).isEqualTo("/package.method2");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);
    endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());
    assertThat(spanEvents.getFullSpan()).isTrue();
  }

  @Test
  public void testTooManyStackTraces() throws InvalidProtocolBufferException {
    // Span 0.
    writer.startRequestSpan("http://foo.com/request");

    // Span 1 has the same stack trace as span 2.
    CloudTraceContext childContext1 = writer.startApiSpan(null, "package", "method1");
    StackTraceElement[] stackTrace1 = {new StackTraceElement("class1", "method1", "file1", 1)};
    writer.addStackTrace(childContext1, stackTrace1);
    writer.endApiSpan(childContext1);

    // Span 2 has the same stack trace as span 1. It will have the same hash ID as span 1.
    CloudTraceContext childContext2 = writer.startApiSpan(null, "package", "method2");
    writer.addStackTrace(childContext2, stackTrace1);
    writer.endApiSpan(childContext2);

    // Span 3 -> MAX_DICTIONARY_SIZE + 2
    for (int i = 3; i < MAX_DICTIONARY_SIZE + 3; i++) {
      CloudTraceContext childContext = writer.startApiSpan(null, "package", "method" + i);
      // Stack trace with line number MAX_DICTIONARY_SIZE is truncated due to dictionary size limit.
      StackTraceElement[] stackTrace = {new StackTraceElement(
          "class" + i, "method" + i, "file" + i, i)};
      writer.addStackTrace(childContext, stackTrace);
      writer.endApiSpan(childContext);
    }

    // The last span has the same stack trace as span 1. It will have the same hash ID as span 1.
    CloudTraceContext childContextLast = writer.startApiSpan(null, "package", "methodLast");
    writer.addStackTrace(childContextLast, stackTrace1);
    writer.endApiSpan(childContextLast);

    writer.endRequestSpan();
    writer.flushTrace();

    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());
    // 1 request span, 2 API spans before the loop, MAX_DICTIONARY_SIZE API spans in the loop,
    // and 1 API span after the loop.
    assertThat(traceEvents.getSpanEventsList()).hasSize(MAX_DICTIONARY_SIZE + 4);

    // Verify dictionary.
    assertThat(traceEvents.getDictionaryEntriesList()).hasSize(MAX_DICTIONARY_SIZE);

    // Verify request span.
    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getEventList()).hasSize(2);

    // Verify span 2 has the same stack trace hash ID as span 1.
    spanEvents = traceEvents.getSpanEvents(1);
    assertThat(spanEvents.getEventCount()).isEqualTo(3);
    long id1 = spanEvents.getEvent(1).getAnnotateSpan().getSpanDetails().getStackTraceHashId();
    spanEvents = traceEvents.getSpanEvents(2);
    assertThat(spanEvents.getEventCount()).isEqualTo(3);
    long id2 = spanEvents.getEvent(1).getAnnotateSpan().getSpanDetails().getStackTraceHashId();
    assertThat(id1).isNotEqualTo(0);
    assertThat(id2).isEqualTo(id1);

    // Verify span 3 to MAX_DICTIONARY_SIZE + 1.
    for (int i = 3; i < MAX_DICTIONARY_SIZE + 2; i++) {
      spanEvents = traceEvents.getSpanEvents(i);
      assertThat(spanEvents.getEventCount()).isEqualTo(3);
      assertThat(spanEvents.getEvent(1).getAnnotateSpan().getSpanDetails().hasStackTraceHashId())
          .isTrue();
    }

    // Verify the second last span doesn't have stack trace hash ID since its stack trace was
    // discarded due to dictionary size limit.
    spanEvents = traceEvents.getSpanEvents(MAX_DICTIONARY_SIZE + 2);
    assertThat(spanEvents.getEventList()).hasSize(2);

    // Verify the last span has the same stack trace hash ID as span 1.
    spanEvents = traceEvents.getSpanEvents(MAX_DICTIONARY_SIZE + 3);
    assertThat(spanEvents.getEventList()).hasSize(3);
    long idLast = spanEvents.getEvent(1).getAnnotateSpan().getSpanDetails().getStackTraceHashId();
    assertThat(idLast).isEqualTo(id1);
  }

  @Test
  public void testChildSpan() throws InvalidProtocolBufferException {
    writer.startRequestSpan("http://foo.com/request");

    CloudTraceContext childContext = writer.startChildSpan(writer.getTraceContext(), "/child");
    CloudTraceContext apiContext = writer.startApiSpan(childContext, "package", "method");
    writer.endApiSpan(apiContext);
    writer.endSpan(childContext);

    writer.endRequestSpan();
    writer.flushTrace();

    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(3);

    // Verify request span.
    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    long requestSpanId = spanEvents.getSpanId().getId();

    // Verify child spans.
    spanEvents = traceEvents.getSpanEvents(1);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    long childSpanId = spanEvents.getSpanId().getId();
    assertThat(spanEvents.getEventCount()).isEqualTo(2);
    SpanEventProto startSpanEvent = spanEvents.getEvent(0);
    StartSpanProto startSpan = startSpanEvent.getStartSpan();
    assertThat(startSpan.getKind()).isEqualTo(SpanKindOuterClass.SpanKind.SPAN_DEFAULT);
    assertThat(startSpan.getName()).isEqualTo("/child");
    assertThat(startSpan.getParentSpanId().getId()).isEqualTo(requestSpanId);
    SpanEventProto endSpanEvent = spanEvents.getEvent(1);
    assertThat(endSpanEvent.getTimestamp()).isAtLeast(startSpanEvent.getTimestamp());

    // Verify API spans.
    spanEvents = traceEvents.getSpanEvents(2);
    assertThat(spanEvents.getEvent(0).getStartSpan().getParentSpanId().getId())
        .isEqualTo(childSpanId);
  }

  @Test
  public void testTwoChildSpans() throws InvalidProtocolBufferException {
    writer.startRequestSpan("http://foo.com/request");

    CloudTraceContext childContext1 = writer.startChildSpan(writer.getTraceContext(), "/child1");
    CloudTraceContext childContext2 = writer.startChildSpan(childContext1, "/child2");
    writer.endSpan(childContext2);
    writer.endSpan(childContext1);

    writer.endRequestSpan();
    writer.flushTrace();

    TraceEventsProto traceEvents =
        TraceEventsProto.parser().parseFrom(upResponse.getSerializedTrace());

    assertThat(traceEvents.getSpanEventsCount()).isEqualTo(3);

    // Verify request span.
    SpanEventsProto spanEvents = traceEvents.getSpanEvents(0);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    long requestSpanId = spanEvents.getSpanId().getId();

    // Verify child span 1.
    spanEvents = traceEvents.getSpanEvents(1);
    assertThat(spanEvents.getSpanId().hasId()).isTrue();
    long childSpanId1 = spanEvents.getSpanId().getId();
    assertThat(spanEvents.getEvent(0).getStartSpan().getParentSpanId().getId())
        .isEqualTo(requestSpanId);

    // Verify child span 2.
    spanEvents = traceEvents.getSpanEvents(2);
    assertThat(spanEvents.getEvent(0).getStartSpan().getParentSpanId().getId())
        .isEqualTo(childSpanId1);
  }
}
