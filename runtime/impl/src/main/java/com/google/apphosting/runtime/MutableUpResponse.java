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

import com.google.apphosting.base.protos.AppLogsPb;
import com.google.apphosting.base.protos.HttpPb;
import com.google.apphosting.base.protos.HttpPb.HttpResponse;
import com.google.apphosting.base.protos.RuntimePb;
import com.google.apphosting.base.protos.RuntimePb.UPResponse;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.protobuf.ByteString;
import java.util.Collection;
import java.util.List;

/**
 * A mutable object that exports an interface similar to {@link UPResponse.Builder} but that is
 * thread-safe.
 *
 */
public class MutableUpResponse {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private final UPResponse.Builder builder;

  public MutableUpResponse() {
    this.builder = UPResponse.newBuilder();
  }

  public synchronized UPResponse build() {
    if (builder.hasHttpResponse()) {
      // The http_response field is optional but, if present, its required fields must be set.
      HttpResponse httpResponse = builder.getHttpResponse();
      if (!httpResponse.hasResponse()) {
        setHttpResponseResponse(ByteString.EMPTY);
      }
      if (!httpResponse.hasResponsecode()) {
        logger.atWarning().log("UPResponse missing http_response.response_code");
        // Currently, specifying the more accurate 500 could lead to the instance being terminated
        // after several requests that return that value. That's probably not desirable.
        setHttpResponseCode(400);
      }
    }
    try {
      return builder.build();
    } catch (Throwable t) {
      logger.atWarning().withCause(t).log("Cannot build UPResponse");
      return builder.buildPartial();
    }
  }

  boolean isInitialized() {
    return builder.isInitialized();
  }

  public synchronized int getError() {
    return builder.getError();
  }

  public synchronized void setError(int error) {
    builder.setError(error);
  }

  public synchronized void setErrorMessage(String message) {
    builder.setErrorMessage(message);
  }

  synchronized boolean hasSerializedTrace() {
    return builder.hasSerializedTrace();
  }

  synchronized ByteString getSerializedTrace() {
    return builder.getSerializedTrace();
  }

  synchronized void setSerializedTrace(ByteString bytes) {
    builder.setSerializedTrace(bytes);
  }

  synchronized int getAppLogCount() {
    return builder.getAppLogCount();
  }

  @VisibleForTesting
  public AppLogsPb.AppLogLine getAppLog(int i) {
    return builder.getAppLog(i);
  }

  synchronized ImmutableList<AppLogsPb.AppLogLine> getAndClearAppLogList() {
    ImmutableList<AppLogsPb.AppLogLine> logList = ImmutableList.copyOf(builder.getAppLogList());
    builder.clearAppLog();
    return logList;
  }

  synchronized void addAppLog(AppLogsPb.AppLogLine line) {
    builder.addAppLog(line);
  }

  synchronized void addAppLog(AppLogsPb.AppLogLine.Builder line) {
    builder.addAppLog(line);
  }

  synchronized void setPendingCloudDebuggerActionBreakpointUpdates(boolean x) {
    builder.getPendingCloudDebuggerActionBuilder().setBreakpointUpdates(x);
  }

  synchronized void setPendingCloudDebuggerActionDebuggeeRegistration(boolean x) {
    builder.getPendingCloudDebuggerActionBuilder().setDebuggeeRegistration(x);
  }

  synchronized boolean hasPendingCloudDebuggerAction() {
    return builder.hasPendingCloudDebuggerAction();
  }

  synchronized RuntimePb.PendingCloudDebuggerAction getPendingCloudDebuggerAction() {
    return builder.getPendingCloudDebuggerAction();
  }

  synchronized void setUserMcycles(long cycles) {
    builder.setUserMcycles(cycles);
  }

  synchronized void addAllRuntimeLogLine(Collection<UPResponse.RuntimeLogLine> lines) {
    builder.addAllRuntimeLogLine(lines);
  }

  synchronized int getRuntimeLogLineCount() {
    return builder.getRuntimeLogLineCount();
  }

  synchronized UPResponse.RuntimeLogLine getRuntimeLogLine(int i) {
    return builder.getRuntimeLogLine(i);
  }

  synchronized boolean getTerminateClone() {
    return builder.getTerminateClone();
  }

  synchronized void setTerminateClone(boolean terminate) {
    builder.setTerminateClone(terminate);
  }

  synchronized boolean hasCloneIsInUncleanState() {
    return builder.hasCloneIsInUncleanState();
  }

  synchronized boolean getCloneIsInUncleanState() {
    return builder.getCloneIsInUncleanState();
  }

  synchronized void setCloneIsInUncleanState(boolean unclean) {
    builder.setCloneIsInUncleanState(unclean);
  }

  synchronized List<HttpPb.ParsedHttpHeader> getRuntimeHeadersList() {
    return builder.getRuntimeHeadersList();
  }

  synchronized void addRuntimeHeaders(HttpPb.ParsedHttpHeader.Builder header) {
    builder.addRuntimeHeaders(header);
  }

  public synchronized void clearHttpResponse() {
    builder.clearHttpResponse();
  }

  public synchronized boolean hasHttpResponse() {
    return builder.hasHttpResponse();
  }

  public synchronized HttpPb.HttpResponse getHttpResponse() {
    return builder.getHttpResponse();
  }

  public synchronized boolean hasHttpResponseResponse() {
    return builder.getHttpResponseBuilder().hasResponse();
  }
  
  public synchronized ByteString getHttpResponseResponse() {
    return builder.getHttpResponseBuilder().getResponse();
  }

  public synchronized void setHttpResponseResponse(ByteString string) {
    builder.getHttpResponseBuilder().setResponse(string);
  }

  public synchronized void setHttpResponseCodeAndResponse(int code, String string) {
    builder.getHttpResponseBuilder()
        .setResponsecode(code)
        .setResponse(ByteString.copyFromUtf8(string));
  }

  public synchronized void setHttpResponseCode(int code) {
    builder.getHttpResponseBuilder().setResponsecode(code);
  }

  synchronized void setHttpUncompressedSize(long size) {
    builder.getHttpResponseBuilder().setUncompressedSize(size);
  }

  synchronized void setHttpUncompressForClient(boolean uncompress) {
    builder.getHttpResponseBuilder().setUncompressForClient(uncompress);
  }

  public synchronized void addHttpResponseHeader(HttpPb.ParsedHttpHeader.Builder header) {
    builder.getHttpResponseBuilder().addOutputHeaders(header);
  }

  public synchronized List<HttpPb.ParsedHttpHeader> getHttpOutputHeadersList() {
    return builder.getHttpResponseBuilder().getOutputHeadersList();
  }

  public synchronized void addHttpOutputHeaders(HttpPb.ParsedHttpHeader.Builder header) {
    builder.getHttpResponseBuilder().addOutputHeaders(header);
  }
}
