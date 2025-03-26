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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.apphosting.api.CloudTraceContext;
import com.google.apphosting.base.protos.LabelsProtos.LabelProto;
import com.google.apphosting.base.protos.LabelsProtos.LabelsProto;
import com.google.apphosting.base.protos.RuntimePb.UPRequest;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.apphosting.base.protos.SpanDetails.SpanDetailsProto;
import com.google.apphosting.base.protos.SpanDetails.StackTraceDetails;
import com.google.apphosting.base.protos.SpanId.SpanIdProto;
import com.google.apphosting.base.protos.SpanKindOuterClass.SpanKind;
import com.google.apphosting.base.protos.TraceEvents.AnnotateSpanProto;
import com.google.apphosting.base.protos.TraceEvents.EndSpanProto;
import com.google.apphosting.base.protos.TraceEvents.EventDictionaryEntry;
import com.google.apphosting.base.protos.TraceEvents.SpanEventProto;
import com.google.apphosting.base.protos.TraceEvents.SpanEventsProto;
import com.google.apphosting.base.protos.TraceEvents.StartSpanProto;
import com.google.apphosting.base.protos.TraceEvents.TraceEventsProto;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.flogger.GoogleLogger;
import com.google.common.primitives.Ints;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Stores trace spans for a single request, and flushes them into {@link UPResponse}.
 */
public class TraceWriter {
  @VisibleForTesting
  public static final int DEFAULT_MAX_TRACE = 1000;

  @VisibleForTesting
  public static final String MAX_TRACE_PROPERTY = "com.google.appengine.max.trace.in.background";

  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();
  // Longest stack trace we record.
  static final int MAX_STACK_DEPTH = 128;
  // We only keep up to 1k unique stack traces in the dictionary. Other stack traces are discarded.
  static final int MAX_DICTIONARY_SIZE = 1024;

  private final CloudTraceContext context;
  private final ResponseAPIData upResponse;
  // Also used to synchronize any mutations to the trace and its spans contained in this builder and
  // in spanEventsMap.
  private final TraceEventsProto.Builder traceEventsBuilder;
  private final Map<Long, SpanEventsProto.Builder> spanEventsMap = Maps.newConcurrentMap();
  private final Set<Long> dictionaryKeys = Sets.newHashSet();
  private final int maxTraceSize;

  @Deprecated
  public TraceWriter(CloudTraceContext context, MutableUpResponse upResponse) {
    this(context, new UpResponseAPIData(upResponse), false);
  }

  public TraceWriter(CloudTraceContext context, ResponseAPIData upResponse) {
    this(context, upResponse, false);
  }

  private TraceWriter(CloudTraceContext context, ResponseAPIData upResponse, boolean background) {
    this.context = context;
    this.upResponse = upResponse;
    // TODO: Set trace id properly. This can't be done until we define a way to parse
    // trace id from string.
    this.traceEventsBuilder = TraceEventsProto.newBuilder();
    if (background) {
      String maxTraceProperty = System.getProperty(MAX_TRACE_PROPERTY);
      Integer maxTraceValue = (maxTraceProperty == null) ? null : Ints.tryParse(maxTraceProperty);
      this.maxTraceSize = (maxTraceValue == null) ? DEFAULT_MAX_TRACE : maxTraceValue;
    } else {
      this.maxTraceSize = Integer.MAX_VALUE;
    }
  }

  @Nullable
  public static TraceWriter getTraceWriterForRequest(
      UPRequest upRequest, MutableUpResponse upResponse) {
    return getTraceWriterForRequest(
        new UpRequestAPIData(upRequest), new UpResponseAPIData(upResponse));
  }

  @Nullable
  public static TraceWriter getTraceWriterForRequest(
      RequestAPIData request, ResponseAPIData response) {
    if (!TraceContextHelper.needsTrace(request.getTraceContext())) {
      return null;
    }
    CloudTraceContext traceContext =
        TraceContextHelper.toObject(request.getTraceContext()).createChildContext();
    boolean background = request.getRequestType().equals(UPRequest.RequestType.BACKGROUND);
    return new TraceWriter(traceContext, response, background);
  }

  /**
   * Gets the current trace context.
   * @return the current trace context.
   */
  public CloudTraceContext getTraceContext() {
    return context;
  }

  private static String createSpanName(String packageName, String methodName) {
    return '/' + packageName + '.' + methodName;
  }

  /**
   * Create a new span as {@link SpanEventsProto} with the start span populated.
   * @param context the trace context for the new span in {@link SpanEventsProto}
   * @param spanName the name of the new span
   * @param spanKind the kind of the new span
   * @return a {@link SpanEventsProto} with the start span populated
   */
  private SpanEventsProto.Builder createSpanEvents(
      CloudTraceContext context, String spanName, SpanKind spanKind) {
    StartSpanProto.Builder startSpan =
        StartSpanProto.newBuilder()
            .setKind(spanKind)
            .setName(spanName)
            .setParentSpanId(SpanIdProto.newBuilder().setId(context.getParentSpanId()));

    // Ignore automated suggestions to convert this to Instances.toEpochNanos(Instant.now()).
    // That's not currently available as open source.
    SpanEventProto spanEvent =
        SpanEventProto.newBuilder()
            .setTimestamp(MILLISECONDS.toNanos(System.currentTimeMillis()))
            .setStartSpan(startSpan)
            .build();

    SpanEventsProto.Builder spanEvents;
    synchronized (traceEventsBuilder) {
      spanEvents = addSpanEventsBuilder();
      spanEvents.setSpanId(SpanIdProto.newBuilder().setId(context.getSpanId())).addEvent(spanEvent);
    }

    return spanEvents;
  }

  /**
   * Start a request span as a child of the current span.
   * @param name the name of the request span
   */
  public void startRequestSpan(String name) {
    SpanEventsProto.Builder spanEvents = createSpanEvents(context, name, SpanKind.RPC_SERVER);
    spanEventsMap.put(context.getSpanId(), spanEvents);
  }

  /**
   * Start a API span as a child of the current span.
   * @param parentContext the parent context
   * @param packageName the name of the API package
   * @param methodName the name of the method within the API package
   * @return a child {@link CloudTraceContext}
   */
  public CloudTraceContext startApiSpan(
      @Nullable CloudTraceContext parentContext, String packageName, String methodName) {
    CloudTraceContext childContext =
        parentContext != null ? parentContext.createChildContext() : context.createChildContext();

    SpanEventsProto.Builder spanEvents =
        createSpanEvents(
            childContext, createSpanName(packageName, methodName), SpanKind.RPC_CLIENT);
    spanEventsMap.put(childContext.getSpanId(), spanEvents);

    return childContext;
  }

  /**
   * Start a new span as a child of the given context.
   * @param parentContext the parent context
   * @param name the name of the child span
   * @return a child {@link CloudTraceContext}
   */
  public CloudTraceContext startChildSpan(CloudTraceContext parentContext, String name) {
    CloudTraceContext childContext = parentContext.createChildContext();
    SpanEventsProto.Builder spanEvents =
        createSpanEvents(childContext, name, SpanKind.SPAN_DEFAULT);
    spanEventsMap.put(childContext.getSpanId(), spanEvents);
    return childContext;
  }

  /**
   * Set a label on the current span.
   * @param context the current context
   * @param key key of the label
   * @param value value of the label
   */
  public void setLabel(CloudTraceContext context, String key, String value) {
    SpanEventsProto.Builder currentSpanEvents = spanEventsMap.get(context.getSpanId());
    if (currentSpanEvents == null) {
      logger.atSevere().log("Span events must exist before setLabel is invoked.");
      return;
    }
    LabelProto.Builder label = LabelProto.newBuilder().setKey(key).setStrValue(value);
    LabelsProto.Builder labels = LabelsProto.newBuilder().addLabel(label);
    AnnotateSpanProto.Builder annotateSpan = AnnotateSpanProto.newBuilder().setLabels(labels);
    synchronized (traceEventsBuilder) {
      currentSpanEvents
          .addEventBuilder()
          .setTimestamp(MILLISECONDS.toNanos(System.currentTimeMillis()))
          .setAnnotateSpan(annotateSpan);
    }
  }

  /**
   * Add stack trace into the current span. The stack trace must be scrubbed for security before
   * being passed to this method.
   * @param context the current context
   * @param stackTrace stack trace to be added
   */
  public void addStackTrace(CloudTraceContext context, StackTraceElement[] stackTrace) {
    SpanEventsProto.Builder currentSpanEvents = spanEventsMap.get(context.getSpanId());
    if (currentSpanEvents == null) {
      logger.atSevere().log("Span events must exist before addStackTrace is invoked.");
      return;
    }
    StackTraceDetails.Builder stackTraceDetails = StackTraceDetails.newBuilder();
    int stackDepth = 0;
    long hashCode = 17;
    for (StackTraceElement element : stackTrace) {
      if (element.getFileName() != null && element.getLineNumber() > 0) {
        hashCode = 31 * hashCode + element.hashCode();
        // File name can be null and Line number can be negative
        stackTraceDetails
            .addStackFrameBuilder()
            .setClassName(element.getClassName())
            .setMethodName(element.getMethodName())
            .setLineNumber(element.getLineNumber())
            .setFileName(element.getFileName());
        stackDepth++;
        if (stackDepth >= MAX_STACK_DEPTH) {
          break;
        }
      }
    }

    // Early return if the stack trace is empty.
    if (stackDepth == 0) {
      return;
    }

    SpanDetailsProto.Builder spanDetails =
        SpanDetailsProto.newBuilder().setStackTraceHashId(hashCode);
    AnnotateSpanProto.Builder annotateSpan =
        AnnotateSpanProto.newBuilder().setSpanDetails(spanDetails);
    synchronized (traceEventsBuilder) {
      if (!dictionaryKeys.contains(hashCode)) {
        // Early return if the dictionary is full and the hash ID is new.
        if (dictionaryKeys.size() >= MAX_DICTIONARY_SIZE) {
          return;
        }
        dictionaryKeys.add(hashCode);
        addEventDictionaryBuilder()
            .setKey(hashCode)
            .setStackTraceValue(stackTraceDetails);
      }
      currentSpanEvents
          .addEventBuilder()
          .setTimestamp(MILLISECONDS.toNanos(System.currentTimeMillis()))
          .setAnnotateSpan(annotateSpan);
    }
  }

  /**
   * End the current span.
   * @param context the current context
   */
  public void endSpan(CloudTraceContext context) {
    SpanEventsProto.Builder currentSpanEvents = spanEventsMap.remove(context.getSpanId());
    if (currentSpanEvents == null) {
      logger.atSevere().log("Span events must exist before endSpan is invoked.");
      return;
    }
    EndSpanProto.Builder endSpan = EndSpanProto.newBuilder();
    synchronized (traceEventsBuilder) {
      currentSpanEvents
          .addEventBuilder()
          .setTimestamp(MILLISECONDS.toNanos(System.currentTimeMillis()))
          .setEndSpan(endSpan);
      // Mark this SpanEventsProto as it contains all events for this Span.
      currentSpanEvents.setFullSpan(true);
    }
  }

  /**
   * End the API span. TODO: Remove this function which is the same as endSpan.
   *
   * @param context the trace context of the API span
   */
  public void endApiSpan(CloudTraceContext context) {
    endSpan(context);
  }

  /**
   * End the request span.
   */
  public void endRequestSpan() {
    endSpan(this.context);
  }

  /**
   * Flush collected trace into {@link UPResponse}.
   */
  public void flushTrace() {
    synchronized (traceEventsBuilder) {
      try {
        upResponse.setSerializedTrace(traceEventsBuilder.build().toByteString());
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Exception in flushTrace");
      }
    }
  }

  /**
   * Adds a {@link SpanEventsProto} builder to {@link #traceEventsBuilder}, but only if we have not
   * already reached {@link #maxTraceSize}. Otherwise returns a new builder that is not connected
   * to anything and will be discarded after use.
   */
  private SpanEventsProto.Builder addSpanEventsBuilder() {
    synchronized (traceEventsBuilder) {
      if (traceEventsBuilder.getSpanEventsCount() < maxTraceSize) {
        return traceEventsBuilder.addSpanEventsBuilder();
      } else {
        return SpanEventsProto.newBuilder();
      }
    }
  }

  /**
   * Adds an {@link EventDictionaryEntry} builder to {@link #traceEventsBuilder}, but only if we
   * have not already reached {@link #maxTraceSize}. Otherwise returns a new builder that is not
   * connected to anything and will be discarded after use.
   */
  private EventDictionaryEntry.Builder addEventDictionaryBuilder() {
    synchronized (traceEventsBuilder) {
      if (traceEventsBuilder.getDictionaryEntriesCount() < maxTraceSize) {
        return traceEventsBuilder.addDictionaryEntriesBuilder();
      } else {
        return EventDictionaryEntry.newBuilder();
      }
    }
  }
}
