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

import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.protos.TraceId.TraceIdProto;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.protobuf.ByteString;

/**
 * Helper functions for trace context. This object contains static functions for internal use only.
 * Functions used by external customers should be defined in {@link CloudTraceContext}.
 */
public class TraceContextHelper {
  private static final long INVALID_SPAN_ID = 0;
  private static final long MASK_TRACE_ENABLED = 0x01;
  private static final long MASK_STACK_TRACE_ENABLED = 0x02;

  private static final String OPTIONS_SEPARATOR_AND_VARIABLE = ";o=";
  private static final int TRACE_LENGTH = 32;

  /**
   * Convert the proto {@link TraceContextProto} to object {@link CloudTraceContext}.
   * @param proto the PB holding the trace context
   */
  static CloudTraceContext toObject(TraceContextProto proto) {
    return new CloudTraceContext(
        proto.getTraceId().toByteArray(), INVALID_SPAN_ID, proto.getSpanId(), proto.getTraceMask());
  }

  /**
   * Convert the object {@link CloudTraceContext} to proto {@link TraceContextProto}.
   *
   * <p>Note that parent span ID is discarded during the conversion. The proto is used to pass trace
   * context from one process to another, and the parent span ID is not useful. If the proto is
   * converted back to an object, the object doesn't have parent span ID.
   */
  static TraceContextProto toProto2(CloudTraceContext context) {
    return TraceContextProto.newBuilder()
        .setTraceId(ByteString.copyFrom(context.getTraceId()))
        .setSpanId(context.getSpanId())
        .setTraceMask((int) context.getTraceMask())
        .build();
  }

  static boolean needsTrace(TraceContextProto contextProto) {
    return needsTrace(contextProto.getTraceMask());
  }

  static boolean needsTrace(long traceMask) {
    return (traceMask & MASK_TRACE_ENABLED) != 0;
  }

  private static boolean needsStackTrace(long traceMask) {
    return (traceMask & MASK_STACK_TRACE_ENABLED) != 0;
  }

  static boolean needsStackTrace(TraceContextProto contextProto) {
    return needsStackTrace(contextProto.getTraceMask());
  }

  static boolean isStackTraceEnabled(CloudTraceContext context) {
    return needsStackTrace(context.getTraceMask());
  }

  /**
   * Create TraceContext from header, based on
   * <a href="http://google3/cloud/trace/client/trace_context_header.cc?rcl=270709913">C++ source</a>.
   *
   * <p>Examples of valid X-Cloud-Trace-Context headers:
   * <pre>
   * 000000000000007b00000000000001c8/789;o=1 (trace, span, and options)
   * 000000000000007b00000000000001c8/789;o= (trace and span, empty options)
   * 000000000000007b00000000000001c8/789 (trace and span, no options)
   * 000000000000007b00000000000001c8/;o=1 (trace and options, empty span)
   * 000000000000007b00000000000001c8/;o= (trace, empty span and options)
   * 000000000000007b00000000000001c8/ (trace, empty span, no options)
   * 000000000000007b00000000000001c8;o=1 (trace and options, no span)
   * 000000000000007b00000000000001c8;o= (trace, empty options, no span)
   * 000000000000007b00000000000001c8 (trace, no span, no options)
   * </pre>
   *
   * @param traceContextHeader a String representing the "X-Cloud-Trace-Context" which has the form
   *     "TRACE_ID/SPAN_ID;o=TRACE_TRUE" where TRACE_TRUE = 0 or 1.  See
   *     <a href="https://cloud.google.com/trace/docs/troubleshooting#force-trace">this link</a>
   *     for details.
   * @return TraceContextProto containing the trace id, span id, and trace mask
   * @throws NumberFormatException if the trace id is less than 32 characters, or the trace mask or
   *     span id are not numerical values
   */
  public static TraceContextProto parseTraceContextHeader(String traceContextHeader) {

    TraceContextProto.Builder protoBuilder = TraceContextProto.newBuilder();

    // Trace ID is up to the first slash, semicolon, or end of string, whatever comes first
    int slashStart = traceContextHeader.indexOf('/');
    int semiStart = traceContextHeader.indexOf(';');
    int traceEnd =
        slashStart >= 0
            ? slashStart
            : semiStart >= 0
                ? semiStart
                : traceContextHeader.length();
    String traceId = traceContextHeader.substring(0, traceEnd);

    protoBuilder.setTraceId(parseTraceId(traceId).toByteString());

    // Options is the last ';o=[0|1]' entry in the string; could be empty (";o=") as well
    int optionsStart = traceContextHeader.lastIndexOf(OPTIONS_SEPARATOR_AND_VARIABLE);
    if (optionsStart > 0) {
      optionsStart = optionsStart + OPTIONS_SEPARATOR_AND_VARIABLE.length();
      if (optionsStart < traceContextHeader.length()) {
        String options = traceContextHeader.substring(optionsStart);
        protoBuilder.setTraceMask(Integer.parseUnsignedInt(options));
      }
    }

    // Span ID starts after the first '/' up to ';' or the end; can be empty as well
    if (slashStart > 0) {
      int spanIdEnd = semiStart > slashStart ? semiStart : traceContextHeader.length();
      if (slashStart + 1 < spanIdEnd) {
        String spanId =
            traceContextHeader.substring(slashStart + 1, spanIdEnd);
        protoBuilder.setSpanId(Long.parseUnsignedLong(spanId));
      }
    }

    return protoBuilder.build();
  }

  private static TraceIdProto parseTraceId(String traceId) {
    if (traceId.length() < TRACE_LENGTH) {
      throw new NumberFormatException("Invalid TRACE ID: length too short");
    }

    String traceIdHi = traceId.substring(0, 16);
    String traceIdLo = traceId.substring(16, 32);

    return TraceIdProto.newBuilder()
        .setHi(Long.parseUnsignedLong(traceIdHi, 16))
        .setLo(Long.parseUnsignedLong(traceIdLo, 16))
        .build();
  }

  private TraceContextHelper() {}
}
