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

package com.google.appengine.api.log;

import com.google.appengine.api.internal.Repackaged;
import com.google.appengine.api.log.LogService.LogLevel;
import com.google.apphosting.api.logservice.LogServicePb.LogLine;
import com.google.apphosting.api.logservice.LogServicePb.RequestLog;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * RequestLogs contain all the log information for a single request. This
 * includes the request log as well as any application logs (which may
 * correspond to logging statements in the user's code or messages we have
 * inserted to alert them to certain conditions we have noticed).
 * Additionally, we include information about this request outside of those
 * logs, such as how long the request took, the IP of the user performing the
 * request, and so on.
 *
 *
 */
// This class is a wrapper for the Protocol Buffer {@link RequestLog}.
public final class RequestLogs implements Serializable {
  private static final long serialVersionUID = 7380961939323935275L;

  private String appId;
  private String moduleId;
  private String versionId;
  private String requestId;
  private String offset;
  private String ip;
  private String nickname;
  private long startTimeUsec;
  private long endTimeUsec;
  private long latency;
  private long mcycles;
  private String method;
  private String resource;
  private String httpVersion;
  private int status;
  private long responseSize;
  private String referrer;
  private String userAgent;
  private String urlMapEntry;
  private String combined;
  private long apiMcycles;
  private String host;
  private double cost;
  private String taskQueueName;
  private String taskName;
  private boolean wasLoadingRequest;
  private long pendingTime;
  private int replicaIndex;
  private boolean finished;
  private String instanceKey;
  private List<AppLogLine> appLogLines = new ArrayList<AppLogLine>();
  private String appEngineRelease;

  /**
   * Default, zero-argument constructor for RequestLogs.
   */
  public RequestLogs() {}

  /**
   * Constructs a new (external-facing) RequestLogs from an (internal-facing)
   * RequestLog. We scrub out any fields that the Protocol Buffer specification
   * for {@link RequestLog} names as Google-only fields.
   *
   * @param requestLog The RequestLog returned by a Log Read RPC call.
   * @param offset A String containing an encoded offset from the RequestLog.
   */
  protected RequestLogs(RequestLog requestLog, String offset) {
    setAppId(requestLog.getAppId());
    setModuleId(requestLog.getModuleId());
    setVersionId(requestLog.getVersionId());
    setRequestId(requestLog.getRequestId().toStringUtf8());
    setOffset(offset);
    setIp(requestLog.getIp());
    setNickname(requestLog.getNickname());
    setStartTimeUsec(requestLog.getStartTime());
    setEndTimeUsec(requestLog.getEndTime());
    setLatency(requestLog.getLatency());
    setMcycles(requestLog.getMcycles());
    setMethod(requestLog.getMethod());
    setResource(requestLog.getResource());
    setHttpVersion(requestLog.getHttpVersion());
    setStatus(requestLog.getStatus());
    setResponseSize(requestLog.getResponseSize());
    setReferrer(requestLog.getReferrer());
    setUserAgent(requestLog.getUserAgent());
    setUrlMapEntry(requestLog.getUrlMapEntry());
    setCombined(requestLog.getCombined());
    setApiMcycles(requestLog.getApiMcycles());
    setHost(requestLog.getHost());
    setTaskQueueName(requestLog.getTaskQueueName());
    setTaskName(requestLog.getTaskName());
    setWasLoadingRequest(requestLog.getWasLoadingRequest());
    setPendingTime(requestLog.getPendingTime());
    setReplicaIndex(requestLog.getReplicaIndex());
    setFinished(requestLog.getFinished());
    setInstanceKey(requestLog.getCloneKey().toStringUtf8());
    setAppEngineRelease(requestLog.getAppEngineRelease().toStringUtf8());

    List<AppLogLine> appLogLines = getAppLogLines();
    for (LogLine logLine : requestLog.getLineList()) {
      LogLevel level = LogLevel.values()[logLine.getLevel()];

      appLogLines.add(new AppLogLine(logLine.getTime(), level,
            logLine.getLogMessage()));
    }
  }

  /**
   * @deprecated This value is no longer meaningful.
   * @return The number of machine cycles spent in API calls while processing
   *   this request.
   */
  @Deprecated
  public long getApiMcycles() {
    return apiMcycles;
  }

  /**
   * @return The application ID that handled this request.
   */
  public String getAppId() {
    return appId;
  }

  /**
   * @return A list of application logs associated with this request.
   */
  public List<AppLogLine> getAppLogLines() {
    return appLogLines;
  }

  /**
   * @return The Apache-format combined log entry for this request. While the
   *   information in this field can be constructed from the rest of this
   *   message, we include this method for convenience.
   */
  public String getCombined() {
    return combined;
  }

  /**
   * @return The estimated cost of this request, in dollars.
   */
  public double getCost() {
    return cost;
  }

  /**
   * @return The time at which the request was known to end processing, in
   *   microseconds since the Unix epoch.
   */
  public long getEndTimeUsec() {
    return endTimeUsec;
  }

  /**
   * @return The Internet host and port number of the resource being requested.
   */
  public String getHost() {
    return host;
  }

  /**
   * @return The HTTP version of this request.
   */
  public String getHttpVersion() {
    return httpVersion;
  }

  /**
   * @return Mostly-unique identifier for the instance that handled the
   * request, or the empty string.
   */
  public String getInstanceKey() {
    return instanceKey;
  }

  /**
   * @return The origin IP address of this request.
   * App Engine uses an origin IP address from the 0.0.0.0/8 range when the request
   * is to a web hook.
   * Some examples of web hooks are task queues, cron jobs and warming requests.
   */
  public String getIp() {
    return ip;
  }

  /**
   * @return The time required to process this request in microseconds.
   */
  public long getLatencyUsec() {
    return latency;
  }

  /**
   * @return The number of machine cycles used to process this request.
   */
  public long getMcycles() {
    return mcycles;
  }

  /**
   * @return The request's method (e.g., GET, PUT, POST).
   */
  public String getMethod() {
    return method;
  }

  /**
   * @return The nickname of the user that made the request. An empty string is
   *   returned if the user is not logged in.
   */
  public String getNickname() {
    return nickname;
  }

  /**
   * @return A Base64-encoded offset that may be used with a subsequent
   *   LogQuery to continue reading logs at the point in time immediately
   *   following this request.
   */
  public String getOffset() {
    return offset;
  }

  /**
   * @return The time, in microseconds, that this request spent in the pending
   *   request queue, if it was pending at all.
   */
  public long getPendingTimeUsec() {
    return pendingTime;
  }

  /**
   * @return The referrer URL of this request.
   */
  public String getReferrer() {
    return referrer;
  }

  /**
   * @return The backend replica that handled the request, or -1 if not
   * serviced by a backend.
   */
  public int getReplicaIndex() {
    return replicaIndex;
  }

  /**
   * @return A globally unique identifier for a request, based on the request's
   *   starting time.
   */
  public String getRequestId() {
    return requestId;
  }

  /**
   * @return The resource path on the server requested by the client. Contains
   *   only the path component of the request URL.
   */
  public String getResource() {
    return resource;
  }

  /**
   * @return The size (in bytes) sent back to the client by this request.
   */
  public long getResponseSize() {
    return responseSize;
  }

  /**
   * @return The time at which this request was known to have begun processing,
   *   in microseconds since the Unix epoch.
   */
  public long getStartTimeUsec() {
    return startTimeUsec;
  }

  /**
   * @return The HTTP response status of this request.
   */
  public int getStatus() {
    return status;
  }

  /**
   * @return The request's task name, if this request was generated via the
   *   Task Queue API.
   */
  public String getTaskName() {
    return taskName;
  }

  /**
   * @return The request's queue name, if this request was generated via the
   *   Task Queue API.
   */
  public String getTaskQueueName() {
    return taskQueueName;
  }

  /**
   * @return The file or class within the URL mapping used for this request.
   *   Useful for tracking down the source code which was responsible for
   *   managing the request, especially for multiply mapped handlers.
   */
  public String getUrlMapEntry() {
    return urlMapEntry;
  }

  /**
   * @return The user agent used to make this request.
   */
  public String getUserAgent() {
    return userAgent;
  }

  /**
   * @return The module of the application that handled this request.
   */
  public String getModuleId() {
    return moduleId;
  }

  /**
   * @return The version of the application that handled this request.
   */
  public String getVersionId() {
    return versionId;
  }

  /**
   * @return Whether or not this request has been finished. If not, this request
   *   is still active.
   */
  public boolean isFinished() {
    return finished;
  }

  /**
   * @return Whether or not this request was a loading request.
   */
  public boolean isLoadingRequest() {
    return wasLoadingRequest;
  }

  /**
   * @return App Engine Release, e.g. "1.7.5", or the empty string.
   */
  public String getAppEngineRelease() {
    return appEngineRelease;
  }

  public void setApiMcycles(long apiMcycles) {
    this.apiMcycles = apiMcycles;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public void setAppLogLines(List<AppLogLine> appLogLines) {
    this.appLogLines = Repackaged.copyIfRepackagedElseOriginal(appLogLines);
  }

  public void setCombined(String combined) {
    this.combined = combined;
  }

  public void setCost(double cost) {
    this.cost = cost;
  }

  public void setEndTimeUsec(long endTimeUsec) {
    this.endTimeUsec = endTimeUsec;
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setHttpVersion(String httpVersion) {
    this.httpVersion = httpVersion;
  }

  public void setInstanceKey(String instanceKey) {
    this.instanceKey = instanceKey;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public void setLatency(long latency) {
    this.latency = latency;
  }

  public void setMcycles(long mcycles) {
    this.mcycles = mcycles;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public void setOffset(String offset) {
    this.offset = offset;
  }

  public void setPendingTime(long pendingTime) {
    this.pendingTime = pendingTime;
  }

  public void setReferrer(String referrer) {
    this.referrer = referrer;
  }

  public void setReplicaIndex(int replicaIndex) {
    this.replicaIndex = replicaIndex;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public void setResource(String resource) {
    this.resource = resource;
  }

  public void setResponseSize(long responseSize) {
    this.responseSize = responseSize;
  }

  public void setStartTimeUsec(long startTimeUsec) {
    this.startTimeUsec = startTimeUsec;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public void setTaskName(String taskName) {
    this.taskName = taskName;
  }

  public void setTaskQueueName(String taskQueueName) {
    this.taskQueueName = taskQueueName;
  }

  public void setUrlMapEntry(String urlMapEntry) {
    this.urlMapEntry = urlMapEntry;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public void setModuleId(String moduleId) {
    this.moduleId = moduleId;
  }

  public void setVersionId(String versionId) {
    this.versionId = versionId;
  }

  public void setWasLoadingRequest(boolean wasLoadingRequest) {
    this.wasLoadingRequest = wasLoadingRequest;
  }

  public void setAppEngineRelease(String appEngineRelease) {
    this.appEngineRelease = appEngineRelease;
  }

  @Override
  public String toString() {
    return "RequestLogs{" +
        "appId='" + appId + '\'' +
        ", moduleId='" + moduleId + '\'' +
        ", versionId='" + versionId + '\'' +
        ", requestId='" + requestId + '\'' +
        ", ip='" + ip + '\'' +
        ", nickname='" + nickname + '\'' +
        ", startTimeUsec=" + startTimeUsec +
        ", endTimeUsec=" + endTimeUsec +
        ", latency=" + latency +
        ", mcycles=" + mcycles +
        ", method='" + method + '\'' +
        ", resource='" + resource + '\'' +
        ", httpVersion='" + httpVersion + '\'' +
        ", status=" + status +
        ", responseSize=" + responseSize +
        ", referrer='" + referrer + '\'' +
        ", userAgent='" + userAgent + '\'' +
        ", urlMapEntry='" + urlMapEntry + '\'' +
        ", combined='" + combined + '\'' +
        ", apiMcycles=" + apiMcycles +
        ", host='" + host + '\'' +
        ", taskQueueName='" + taskQueueName + '\'' +
        ", taskName='" + taskName + '\'' +
        ", wasLoadingRequest=" + wasLoadingRequest +
        ", pendingTime=" + pendingTime +
        ", replicaIndex=" + replicaIndex +
        ", finished=" + finished +
        ", instanceKey='" + instanceKey + '\'' +
        ", appLogLines=" + appLogLines +
        ", appEngineRelease='" + appEngineRelease + '\'' +
        '}';
  }
  

  @Override
  public int hashCode() {
    return Objects.hash(appId, moduleId, versionId, requestId, offset, ip,
        nickname, startTimeUsec, endTimeUsec, latency, mcycles, method,
        resource, httpVersion, status, responseSize, referrer, userAgent,
        urlMapEntry, combined, apiMcycles, host, taskQueueName, taskName,
        wasLoadingRequest, pendingTime, replicaIndex, finished, instanceKey,
        appLogLines, appEngineRelease);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    RequestLogs other = (RequestLogs) obj;
    return Objects.equals(appId, other.appId)
        && Objects.equals(moduleId, other.moduleId)
        && Objects.equals(versionId, other.versionId)
        && Objects.equals(requestId, other.requestId)
        && Objects.equals(offset, other.offset)
        && Objects.equals(ip, other.ip)        
        && Objects.equals(nickname, other.nickname)
        && (startTimeUsec == other.startTimeUsec)
        && (endTimeUsec == other.endTimeUsec)
        && (latency == other.latency)
        && (mcycles == other.mcycles)
        && Objects.equals(method, other.method)
        && Objects.equals(resource, other.resource)
        && Objects.equals(httpVersion, other.httpVersion)
        && (status == other.status)
        && (responseSize == other.responseSize)
        && Objects.equals(referrer, other.referrer)
        && Objects.equals(userAgent, other.userAgent)
        && Objects.equals(urlMapEntry, other.urlMapEntry)
        && Objects.equals(combined, other.combined)
        && (apiMcycles == other.apiMcycles)
        && Objects.equals(host, other.host)
        && Objects.equals(taskQueueName, other.taskQueueName)
        && Objects.equals(taskName, other.taskName)
        && (wasLoadingRequest == other.wasLoadingRequest)
        && (pendingTime == other.pendingTime)
        && (replicaIndex == other.replicaIndex)
        && (finished == other.finished)
        && Objects.equals(instanceKey, other.instanceKey)
        && Objects.equals(appLogLines , other.appLogLines)
        && Objects.equals(appEngineRelease, other.appEngineRelease);
  }
}
