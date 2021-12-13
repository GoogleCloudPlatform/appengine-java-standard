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

package com.google.appengine.api.log.dev;

import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.DevLogService;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.apphosting.api.logservice.LogServicePb.LogLine;
import com.google.apphosting.api.logservice.LogServicePb.LogModuleVersion;
import com.google.apphosting.api.logservice.LogServicePb.LogOffset;
import com.google.apphosting.api.logservice.LogServicePb.LogReadRequest;
import com.google.apphosting.api.logservice.LogServicePb.LogReadResponse;
import com.google.apphosting.api.logservice.LogServicePb.RequestLog;
import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Handler;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implementation of local log service.
 *
 */
@AutoService(LocalRpcService.class)
public class LocalLogService extends AbstractLocalRpcService implements DevLogService {
  private static final String DEFAULT_MODULE = "default";

  private static final ThreadLocal<Long> threadLocalResponseSize = new ThreadLocal<>();

  // The in-memory list of the most recent MAX_NUM_LOGS seen by this
  // application. Newest logs are stored at the beginning, to match the behavior
  // seen in production.
  // Lists do not automatically provide synchronized access - methods that
  // add, delete, or read items from it should be synchronized.
  private final List<RequestLog> logs = new ArrayList<>();

  // To conserve memory within the dev_appserver, we bound the number of logs
  // that we can store. This constant specifies that limit.
  private static final int MAX_NUM_LOGS = 1000;

  /**
   * @return The package name associated with this API.
   */
  @Override
  public String getPackage() {
    return PACKAGE;
  }

  /**
   * Reads log records from the in-memory log list and applies user-specified filters to the results
   * to return.
   *
   * @param request A set of parameters that indicate restrictions on the results that should be
   *     returned.
   * @return A set of logs matching the parameters given. If the number of logs returned exceed
   *     either the user-specified amount or the API-specified limit, then an offset is returned
   *     that has a reference to the next record to read from in subsequent requests.
   */
  public synchronized LogReadResponse read(Status status, LogReadRequest request) {
    LogReadResponse.Builder response = LogReadResponse.newBuilder();
    Integer index = 0;

    Set<ByteString> requestedIds = null;
    if (!request.getRequestIdList().isEmpty()) {
      requestedIds = new HashSet<>(request.getRequestIdList());
    }

    // If the user gave us a request ID to start with, look through our logs
    // until we find it. Since it may have been deleted between the time they
    // originally were given that value and now, any value with a timestamp
    // after this one is acceptable.
    if (request.hasOffset()) {
      index = null;
      BigInteger requestToFind =
          new BigInteger(request.getOffset().getRequestId().toStringUtf8(), 16);
      for (int i = 0; i < logs.size(); i++) {
        BigInteger thisRequestId = new BigInteger(logs.get(i).getRequestId().toStringUtf8(), 16);
        if (requestToFind.compareTo(thisRequestId) > 0) {
          index = i;
          break;
        }
      }

      // There is an unlikely scenario that can occur if the user is promised
      // more logs but before they ask for more, they are all deleted. In this
      // scenario, return a response to them with no logs and no offset, so
      // that they don't ask us for any more logs.
      if (index == null) {
        return response.build();
      }
    }

    int numResultsFetched = 0;
    for (int i = index; i < logs.size(); i++) {
      RequestLog thisLog = null;
      int j = 0;
      for (RequestLog log : logs) {
        if (i == j) {
          thisLog = log;
          break;
        }
        j++;
      }

      if (requestedIds != null &&
          !requestedIds.contains(thisLog.getRequestId())) {
        continue;
      }

      // We want to get all logs that have started within the bounds the user
      // has requested, so check the request's starting time (not its ending
      // time) against the provided start/end times.
      if (request.hasStartTime()) {
        if (request.getStartTime() > thisLog.getEndTime()) {
          continue;
        }
      }

      if (request.hasEndTime()) {
        if (request.getEndTime() <= thisLog.getEndTime()) {
          continue;
        }
      }

      // If the user doesn't want incomplete requests and this request is
      // incomplete, don't include it.
      if (!request.getIncludeIncomplete() && !thisLog.getFinished()) {
        continue;
      }

      if (!request.getVersionIdList().isEmpty()
          && !request.getVersionIdList().contains(thisLog.getVersionId())
          && thisLog.hasVersionId()) {
        continue;
      }

      if (!request.getModuleVersionList().isEmpty()
          && (thisLog.hasModuleId() || thisLog.hasVersionId())) {
        boolean moduleVersionMatch = false;
        for (LogModuleVersion moduleVersion : request.getModuleVersionList()) {
          if (thisLog.getModuleId().equals(moduleVersion.getModuleId())
              && thisLog.getVersionId().equals(moduleVersion.getVersionId())) {
            moduleVersionMatch = true;
          }
        }
        if (!moduleVersionMatch) {
          continue;
        }
      }

      if (request.hasMinimumLogLevel()) {
        // Find if there are any logs that meet or exceed minimumLogLevel.
        // If so (and if the user has specified that they want app logs), add
        // all the app logs to the response. If not, don't include this log in
        // the response.
        boolean logLevelMatched = false;

        for (LogLine line : thisLog.getLineList()) {
          if (line.getLevel() >= request.getMinimumLogLevel()) {
            logLevelMatched = true;
            break;
          }
        }

        if (!logLevelMatched) {
          continue;
        }
      }

      // At this point, the thisLog proto might only be partial, which is fine in dev mode
      // (difference between proto1 and proto2),
      // se we are filling mandatory fields with dummy data, so we do not need to change the
      // official
      // log service implementation.
      RequestLog.Builder logCopy = thisLog.toBuilder().clone();
      fillRequiredFields(logCopy);

      if (!request.getIncludeAppLogs()) {
        // If the user doesn't want app logs, make a copy of this log
        // that doesn't have that in it and give them that instead.
        logCopy.clearLine();
      }

      response.addLog(logCopy.build());

      numResultsFetched++;
      if (numResultsFetched >= request.getCount()) {
        // If there are more results, set the offset to the next result's
        // request id
        if (i + 1 < logs.size()) {
          ByteString nextOffset = logs.get(i).getRequestId();
          LogOffset offset = LogOffset.newBuilder().setRequestId(nextOffset).build();
          response.setOffset(offset);
        }

        break;
      }
    }
    return response.build();
  }

  private void fillRequiredFields(RequestLog.Builder logBuilder) {
    // At this point, logBuilder proto might only be partial, which is fine in dev mode
    // (difference between proto1 and proto2),
    // se we are filling required fields with dummy data, so we do not need to change the
    // official log service implementation.
    if (!logBuilder.hasAppId()) {
      logBuilder.setAppId("");
    }
    if (!logBuilder.hasVersionId()) {
      logBuilder.setVersionId("");
    }
    if (!logBuilder.hasIp()) {
      logBuilder.setIp("");
    }
    if (!logBuilder.hasStartTime()) {
      logBuilder.setStartTime(0);
    }
    if (!logBuilder.hasEndTime()) {
      logBuilder.setEndTime(0);
    }
    if (!logBuilder.hasLatency()) {
      logBuilder.setLatency(0);
    }
    if (!logBuilder.hasMcycles()) {
      logBuilder.setMcycles(0);
    }
    if (!logBuilder.hasMethod()) {
      logBuilder.setMethod("");
    }
    if (!logBuilder.hasResource()) {
      logBuilder.setResource("");
    }
    if (!logBuilder.hasHttpVersion()) {
      logBuilder.setHttpVersion("");
    }
    if (!logBuilder.hasResponseSize()) {
      logBuilder.setResponseSize(0);
    }
    if (!logBuilder.hasUrlMapEntry()) {
      logBuilder.setUrlMapEntry("");
    }
    if (!logBuilder.hasCombined()) {
      logBuilder.setCombined("");
    }
    if (!logBuilder.hasStatus()) {
      logBuilder.setStatus(0);
    }
  }

  /**
   * Registers the response size of a request for use by {@link #addRequestInfo}. This is helpful
   * because ResponseRewriterFilter computes the response length but has no direct way to convey the
   * information to JettyContainerService.ApiProxyHandler which calls {@link #addRequestInfo}
   */
  public synchronized void registerResponseSize(long responseSize) {
    threadLocalResponseSize.set(responseSize);
  }

  @VisibleForTesting
  public synchronized Long getResponseSize() {
    return threadLocalResponseSize.get() == null ? 0L : threadLocalResponseSize.get();
  }

  /**
   * Clears a response size previously registered by calling {@link
   * #registerResponseSize}.
   */
  public synchronized void clearResponseSize() {
    threadLocalResponseSize.remove();
  }

  public void addRequestInfo(
      String appId,
      String versionId,
      String requestId,
      @Nullable String ip,
      @Nullable String nickname,
      long startTimeUsec,
      long endTimeUsec,
      String method,
      String resource,
      String httpVersion,
      @Nullable String userAgent,
      boolean complete,
      @Nullable Integer status,
      @Nullable String referrer) {
    addRequestInfo(appId, DEFAULT_MODULE, versionId, requestId, ip, nickname,
                   startTimeUsec, endTimeUsec, method, resource, httpVersion,
                   userAgent, complete, status, referrer);
  }

  public synchronized void addRequestInfo(
      String appId,
      String moduleId,
      String versionId,
      String requestId,
      @Nullable String ip,
      @Nullable String nickname,
      long startTimeUsec,
      long endTimeUsec,
      String method,
      String resource,
      String httpVersion,
      @Nullable String userAgent,
      boolean complete,
      @Nullable Integer status,
      @Nullable String referrer) {

    // Find where log with given requestid is or create one.
    RequestLog existingOrNewLog = findLogInLogMapOrAddNewLog(requestId);
    int index = logs.indexOf(existingOrNewLog);

    RequestLog.Builder log = existingOrNewLog.toBuilder().setAppId(appId);

    // Set the version id to be just the major version id
    String majorVersionId = Splitter.on('.').splitToList(versionId).get(0);
    if (moduleId.equals(DEFAULT_MODULE)) {
      log.setModuleId(moduleId);
    }
    log.setVersionId(majorVersionId).setStartTime(startTimeUsec).setEndTime(endTimeUsec);
    if (ip != null) {
      log.setIp(ip);
    }

    if (nickname != null) {
      log.setNickname(nickname);
    }

    log.setLatency(endTimeUsec - startTimeUsec)
        .setMcycles(0L)
        .setMethod(method)
        .setResource(resource)
        .setHttpVersion(httpVersion);
    Long responseSize = getResponseSize();
    log.setResponseSize(responseSize).setStatus(status);
    if (referrer != null) {
      log.setReferrer(referrer);
    }

    log.setCombined(formatCombinedLog(ip, nickname, endTimeUsec, method,
        resource, httpVersion, status, responseSize, referrer, userAgent));

    if (userAgent != null) {
      log.setUserAgent(userAgent);
    }

    // Required proto2 fields...
    log.setUrlMapEntry("").setFinished(complete);

    // Replace element in the existing list, while keeping position.
    logs.set(index, log.build());
  }

  public synchronized void addAppLogLine(String requestId, long time, int level,
      String message) {
    if (message == null) {
      // ignore requests to log null messages
      return;
    }
    LogLine line =
        LogLine.newBuilder().setTime(time).setLevel(level).setLogMessage(message).build();
    int index = logs.indexOf(findLogInLogMapOrAddNewLog(requestId));

    RequestLog log = findLogInLogMapOrAddNewLog(requestId).toBuilder().addLine(line).buildPartial();
    // Replace element in the existing list, while keeping position.
    logs.set(index, log);
  }

  private synchronized RequestLog findLogInLogMapOrAddNewLog(String requestId) {
    if (requestId == null) {
      requestId = "null";
    }

    // TODO: This should be a LinkedHashMap, or we should at
    // least be using a binary search search here since they're
    // assumed to be sorted by requestId.
    for (int i = 0; i < logs.size(); i++) {
      RequestLog possibleLog = logs.get(i);
      if (possibleLog.getRequestId().toStringUtf8().equals(requestId)) {
        return possibleLog;
      }
    }

    /* Items are sorted in descending order w.r.t. request id. Since request ids
     * are monotonically increasing, this means that the new request id we're
     * about to generate will have the largest value ever seen in the log list,
     * so put it at the beginning of the list. Lists also are not
     * thread-safe for adds and deletes, so synchronize access to it. */
    // Fill with required fields with dummy data that will be replaced later anyway.
    RequestLog.Builder log =
        RequestLog.newBuilder().setRequestId(ByteString.copyFromUtf8(requestId)).setFinished(false);
    LogOffset offset =
        LogOffset.newBuilder().setRequestId(ByteString.copyFromUtf8(requestId)).build();
    RequestLog built = log.setOffset(offset).buildPartial();
    logs.add(0, built);

    // If there are too many logs stored, remove the oldest (last) one.
    if (logs.size() > MAX_NUM_LOGS) {
      logs.remove(logs.size() - 1);
    }

    return built;
  }

  /**
   * Returns a combined log record value.
   *
   * <p>See http://httpd.apache.org/docs/1.3/logs.html for details.
   *
   * @param ip The requestors IP address or null if not known.
   * @param nickname The requestors nickname or null if not known.
   * @param endTimeUsec The time the request completed.
   * @param method The HTTP method (GET, POST ...).
   * @param resource The Servlet resource for the request.
   * @param httpVersion The http version for the request or null if not known.
   * @param status The return status for the request or null if not known.
   * @param responseSize The number of bytes returned to the user or null if not known.
   * @param referrer The referrer of the request or null none available.
   * @param userAgent The user agent for the request or null if not know.
   * @return The formatted combined log record.
   */
  private String formatCombinedLog(
      @Nullable String ip,
      @Nullable String nickname,
      long endTimeUsec,
      String method,
      String resource,
      @Nullable String httpVersion,
      @Nullable Integer status,
      @Nullable Long responseSize,
      @Nullable String referrer,
      @Nullable String userAgent) {
    String result = String.format("%1$s - %2$s [%3$s] \"%4$s %5$s %6$s\" %7$s %8$s %9$s %10$s",
        formatOptionalString(ip),
        formatOptionalString(nickname),
        formatTime(endTimeUsec),
        method,
        resource,
        httpVersion,
        formatOptionalInteger(status),
        formatResponseSize(responseSize),
        formatOptionalQuotedString(referrer),
        formatOptionalQuotedString(userAgent));
    return result;
  }

  /**
   * Returns the passed time value in a format suitable for the combined log.
   *
   * @param timeUsec - a time since the epoch in units of 1e-6 seconds.
   */
  private String formatTime(long timeUsec) {
    SimpleDateFormat format = new SimpleDateFormat("d/MMM/yyyy:HH:mm:ss Z",
        Locale.ENGLISH);
    TimeZone zone = TimeZone.getTimeZone("UTC");
    format.setTimeZone(zone);
    return format.format(new Date(timeUsec / 1000));
  }

  /**
   * Returns the quoted String version of the passed in string value or
   * "-" if null or a zero length value is passed in.
   *
   * Note: this does not make special provisions for quotes in the passed in
   * value.
   */
  private String formatOptionalQuotedString(String value) {
    if (value == null || value.length() == 0) {
      return "-";
    } else {
      return "\"" + value + "\"";
    }
  }

  /**
   * Returns the passed in string value or "-" if null or a zero length value is
   * passed in.
   */
  private String formatOptionalString(String value) {
    if (value == null || value.length() == 0) {
      return "-";
    } else {
      return value;
    }
  }

  /**
   * Returns the String version of the passed in value or "-" if null is passed
   * in.
   */
  private String formatOptionalInteger(Integer value) {
    if (value == null) {
      return "-";
    } else {
      return value.toString();
    }
  }

  /** Returns the String version of responseSize. */
  private String formatResponseSize(long responseSize) {
    return Long.toString(responseSize);
  }

  @Override
  public Handler getLogHandler() {
    return new DevLogHandler(this);
  }

  /**
   * Clears out the internal logs stored.
   */
  public synchronized void clear() {
    logs.clear();
  }
}
