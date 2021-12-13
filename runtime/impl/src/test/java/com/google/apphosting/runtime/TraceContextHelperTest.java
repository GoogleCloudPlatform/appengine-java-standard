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
import static org.junit.Assert.assertThrows;

import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.protos.TraceId.TraceIdProto;
import com.google.apphosting.base.protos.TracePb.TraceContextProto;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TraceContextHelper.
 */
@RunWith(JUnit4.class)
public class TraceContextHelperTest {
  @Test
  public void testBasic() {
    TraceContextProto contextProto = TraceContextProto.newBuilder()
        .setTraceId(TraceIdProto.newBuilder()
          .setHi(0xffffffffffffffffL)
          .setLo(0x1333333301102030L)
          .build()
          .toByteString())
        .setSpanId(1L)
        .setTraceMask(3)
        .build();

    CloudTraceContext contextFromProto = TraceContextHelper.toObject(contextProto);

    assertThat(contextFromProto.getTraceId()).isEqualTo(contextProto.getTraceId().toByteArray());
    assertThat(contextFromProto.getSpanId()).isEqualTo(1L);
    assertThat(contextFromProto.getTraceMask()).isEqualTo(3L);

    assertThat(contextFromProto.isTraceEnabled()).isTrue();
    assertThat(TraceContextHelper.isStackTraceEnabled(contextFromProto)).isTrue();

    TraceContextProto protoFromContext = TraceContextHelper.toProto2(contextFromProto);
    assertThat(protoFromContext.getTraceId().toByteArray()).isEqualTo(contextFromProto.getTraceId());
    assertThat(protoFromContext.getSpanId()).isEqualTo(1L);
    assertThat(protoFromContext.getTraceMask()).isEqualTo(3L);
  }

  @Test
  public void testParseContextHeader() throws InvalidProtocolBufferException {

    TraceContextProto contextProto =
        TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8/789;o=1");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.getSpanId()).isEqualTo(789L);
    assertThat(contextProto.getTraceMask()).isEqualTo(1L);

    contextProto =
        TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8/789;o=");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.getSpanId()).isEqualTo(789L);
    assertThat(contextProto.hasTraceMask()).isFalse();

    contextProto =
        TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8/789");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.getSpanId()).isEqualTo(789L);
    assertThat(contextProto.hasTraceMask()).isFalse();

    contextProto =
        TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8/;o=1");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.hasSpanId()).isFalse();
    assertThat(contextProto.getTraceMask()).isEqualTo(1L);

    contextProto =
        TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8/;o=");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.hasSpanId()).isFalse();
    assertThat(contextProto.hasTraceMask()).isFalse();

    contextProto = TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8/");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.hasSpanId()).isFalse();
    assertThat(contextProto.hasTraceMask()).isFalse();

    contextProto =
        TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8;o=1");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.hasSpanId()).isFalse();
    assertThat(contextProto.getTraceMask()).isEqualTo(1L);

    contextProto =
        TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8;o=");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.hasSpanId()).isFalse();
    assertThat(contextProto.hasTraceMask()).isFalse();

    contextProto = TraceContextHelper.parseTraceContextHeader("000000000000007b00000000000001c8");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(contextProto.hasSpanId()).isFalse();
    assertThat(contextProto.hasTraceMask()).isFalse();

    contextProto =
        TraceContextHelper.parseTraceContextHeader(
            "000000000000007b00000000000001c8/18446744073709551615;o=");
    assertThat(getTraceId(contextProto)).isEqualTo("000000000000007b00000000000001c8");
    assertThat(Long.toUnsignedString(contextProto.getSpanId())).isEqualTo("18446744073709551615");
    assertThat(contextProto.hasTraceMask()).isFalse();

    contextProto =
        TraceContextHelper.parseTraceContextHeader(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF/18446744073709551615;o=4294967295");
    assertThat(getTraceId(contextProto)).isEqualTo("ffffffffffffffffffffffffffffffff");
    assertThat(Long.toUnsignedString(contextProto.getSpanId())).isEqualTo("18446744073709551615");
    assertThat(Integer.toUnsignedString(contextProto.getTraceMask())).isEqualTo("4294967295");

    NumberFormatException noTraceIdExpected =
        assertThrows(
            NumberFormatException.class,
            () -> TraceContextHelper.parseTraceContextHeader(""));
    assertThat(noTraceIdExpected).hasMessageThat().contains("length too short");

    NumberFormatException badTraceIdExpected =
        assertThrows(
            NumberFormatException.class,
            () -> TraceContextHelper.parseTraceContextHeader("000000007b0000001c8"));
    assertThat(badTraceIdExpected).hasMessageThat().contains("length too short");

    NumberFormatException badSpanIdExpected =
        assertThrows(
            NumberFormatException.class,
            () ->
                TraceContextHelper.parseTraceContextHeader(
                    "000000000000007b00000000000001c8/ab;o=1"));
    assertThat(badSpanIdExpected).hasMessageThat().contains("For input string: \"ab\"");

    NumberFormatException badTraceMaskIdExpected =
        assertThrows(
            NumberFormatException.class,
            () ->
                TraceContextHelper.parseTraceContextHeader(
                    "000000000000007b00000000000001c8/123;o=4294967296"));
    assertThat(badTraceMaskIdExpected)
        .hasMessageThat()
        .contains("String value 4294967296 exceeds range of unsigned int");
  }

  private static String getTraceId(TraceContextProto proto) throws InvalidProtocolBufferException {
    TraceIdProto traceIdProto =
        TraceIdProto.parseFrom(
            proto.getTraceId(),
            ExtensionRegistry.getEmptyRegistry());
    return String.format("%016x%016x", traceIdProto.getHi(), traceIdProto.getLo());
  }
}
