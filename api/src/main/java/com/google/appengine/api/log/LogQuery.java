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

import com.google.appengine.api.internal.ImmutableCopy;
import com.google.appengine.api.internal.Repackaged;
import com.google.appengine.api.log.LogService.LogLevel;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Allows users to customize the behavior of {@link LogService#fetch(LogQuery)}.
 * <p>
 * {@code startTime} is the earliest request completion or last-update time
 * that results should be fetched for, in seconds since the Unix epoch. If
 * {@code null} then no requests will be excluded for ending too long ago.
 * <p>
 * {@code endTime} is the latest request completion or last-update time that
 * results should be fetched for, in seconds since the Unix epoch. If
 * {@code null} then no requests will be excluded for ending too recently.
 * <p>
 * {@code offset} is a cursor into the log stream retrieved from a previously
 * emitted {@link RequestLogs#getOffset}. This iterator will begin returning
 * logs immediately after the record from which the offset came. If
 * {@code null}, the query will begin at {@code startTime}.
 * <p>
 * {@code minLogLevel} is a {@link LogService.LogLevel} which serves as a
 * filter on the requests returned. Requests with no application logs at or
 * above the specified level will be omitted. Works even if
 * {@code includeAppLogs} is not True.
 * <p>
 * {@code includeIncomplete} selects whether requests that have started but not
 * yet finished should be included in the query. Defaults to False.
 * <p>
 * {@code includeAppLogs} specifies whether or not to include application logs
 * in the query results. Defaults to False.
 * <p>
 * {@code majorVersionIds} specifies versions of the application's default
 * module for which logs records should retrieved.
 * <p>
 * {@code versions} specifies module versions of the application for which
 * logs should be retrieved.
 * <p>
 * {@code requestIds}, if not {@code empty()}, indicates that instead of a
 * time-based scan, logs for the specified requests should be returned.
 * See the Request IDs section of <a
 * href="https://developers.google.com/appengine/docs/java/runtime#The_Environment">
 * the Java Servlet Environment documentation</a> for how to retrieve these IDs
 * at runtime. Malformed request IDs cause an exception and unrecognized request IDs
 * are ignored. This option may not be combined with other filtering options such as
 * startTime, endTime, offset, or minLogLevel. When {@code requestIds} is not {@code empty()},
 * {@code majorVersionIds} are ignored. Logs are returned in the order requested.
 * <p>
 * {@code batchSize} specifies the internal batching strategy of the returned
 * {@link java.lang.Iterable Iterable&lt;RequestLogs&gt;}. Has no impact on the
 * result of the query.
 * <p>
 * Notes on usage:<br>
 * The recommended way to instantiate a {@code LogQuery} object is to
 * statically import {@link Builder}.* and invoke a static
 * creation method followed by an instance mutator (if needed):
 *
 * <pre>{@code
 * import static com.google.appengine.api.log.LogQuery.Builder.*;
 *
 * ...
 *
 * // All requests, including application logs.
 * iter = logService.fetch(withIncludeAppLogs(true));
 *
 * // All requests ending in the past day (or still running) with an info log or higher.
 * Calendar cal = Calendar.getInstance();
 * cal.add(Calendar.DAY_OF_MONTH, -1);
 * iter = logService.fetch(withEndTimeMillis(cal.time())
 *     .includeIncomplete(true).minimumLogLevel(LogService.INFO));
 * }</pre>
 *
 * There are a couple of ways to configure {@link LogQuery} to limit
 * {@link LogService#fetch(LogQuery)} to only return log records for specific
 * module versions.
 * <ol>
 * <li><b>{@link #versions(List)}({@link Builder#withVersions(List)})</b> -
 * Includes designated module versions for the application.
 * <li><b>{@link #majorVersionIds(List)} ({@link Builder#withMajorVersionIds(List)})</b> -
 *  Includes designated versions of the default module for the application.
 * </ol>
 * For a particular {@link LogQuery} only one of these methods may be used. If neither is used,
 * {@link LogService#fetch(LogQuery)} results may include any module version.
 *
 * It is not allowed to call both {@link #versions(List)} ({@link Builder#withVersions(List)})
 * and {@link #requestIds(List)}({@link Builder#withRequestIds(List)} for the same {@link LogQuery}.
 *
 */
public final class LogQuery implements Cloneable, Serializable {
  private static final long serialVersionUID = 3660093076203855168L;
  static final Comparator<Version> VERSION_COMPARATOR = new VersionComparator();

  // The offset here is a String representation of the next location in the
  // logs stream where results should be returned from.
  @Nullable private String offset;

  @Nullable private Long startTimeUsec;

  @Nullable private Long endTimeUsec;

  private int batchSize = LogService.DEFAULT_ITEMS_PER_FETCH;

  @Nullable private LogLevel minLogLevel;
  private boolean includeIncomplete = false;
  private boolean includeAppLogs = false;
  private List<String> majorVersionIds = new ArrayList<String>();
  private List<Version> versions = new ArrayList<Version>();

  private List<String> requestIds = new ArrayList<String>();

  private static final String MAJOR_VERSION_ID_REGEX = "[a-z\\d][a-z\\d\\-]{0,99}";
  private static final Pattern VERSION_PATTERN = Pattern.compile(MAJOR_VERSION_ID_REGEX);
  private static final String REQUEST_ID_REGEX = "\\A\\p{XDigit}+\\z";
  private static final Pattern REQUEST_ID_PATTERN = Pattern.compile(REQUEST_ID_REGEX);

  /**
   * Specifies a version of a module.
   */
  public static final class Version implements Serializable {
    private static final long serialVersionUID = 3697597908142270764L;
    private static final String INVALID_MODULE_ID_MESSAGE_TEMPLATE = "Invalid module id '%s'";
    private static final String INVALID_VERSION_ID_MESSAGE_TEMPLATE = "Invalid version id '%s'";
    private final String moduleId;
    private final String versionId;

    /**
     * Constructs a {@link Version}.
     * @param moduleId A valid module id.
     * @param versionId A valid version id.
     */
    public Version(String moduleId, String versionId) {
      this.moduleId = verifyId(moduleId, INVALID_MODULE_ID_MESSAGE_TEMPLATE);
      this.versionId = verifyId(versionId, INVALID_VERSION_ID_MESSAGE_TEMPLATE);
    }

    /**
     * Returns the moduleId.
     */
    public String getModuleId() {
      return moduleId;
    }

    /**
     * Returns the version id.
     */
    public String getVersionId() {
      return versionId;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((moduleId == null) ? 0 : moduleId.hashCode());
      result = prime * result + ((versionId == null) ? 0 : versionId.hashCode());
      return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Version other = (Version) obj;
      if (moduleId == null) {
        if (other.moduleId != null) {
          return false;
        }
      } else if (!moduleId.equals(other.moduleId)) {
        return false;
    }

    if (versionId == null) {
      if (other.versionId != null) {
        return false;
      }
    } else if (!versionId.equals(other.versionId)) {
      return false;
    }
    return true;
  }

    @Override
    public String toString() {
      return "Version: moduleId=" + moduleId + " versionId=" + versionId;
    }

    /**
     * Verifies and returns a module id or version id.
     *
     * Note this verification uses the VERSION_PATTERN regular expression which will catch
     * many but not all syntactic errors.
     *
     * @param moduleOrVersionId The module or version id to verify.
     * @param messageTemplate A message template with a placeholder for the invalid value.
     * @return moduleOrVersionId
     * @throws IllegalArgumentException if moduleOrVersionId is not valid.
     */
    private static String verifyId(String moduleOrVersionId, String messageTemplate) {
      Matcher matcher = VERSION_PATTERN.matcher(moduleOrVersionId);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(String.format(messageTemplate, moduleOrVersionId));
      }
      return moduleOrVersionId;
    }
  }

  /**
   * Compares {@link Version} values by {@link Version#getModuleId()} and
   * breaks ties by comparing by {@link Version#getVersionId()}
   */
  private static class VersionComparator implements Comparator<Version> {
    @Override
    public int compare(Version version1, Version version2) {
      int result = version1.getModuleId().compareTo(version2.getModuleId());
      if (result == 0) {
        result = version1.getVersionId().compareTo(version2.getVersionId());
      }
      return result;
    }
  }

  /**
   * Contains static creation methods for {@link LogQuery}.
   */
  public static final class Builder {
    /**
     * Create a {@link LogQuery} with the given offset.
     * Shorthand for <code>LogQuery.Builder.withDefaults().offset(offset);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how offsets are used.
     * @param offset the offset to use.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withOffset(String offset) {
      return withDefaults().offset(offset);
    }

    /**
     * Create a {@link LogQuery} with the given start time.
     * Shorthand for <code>LogQuery.Builder.withDefaults().startTimeMillis(startTimeMillis);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how start time is used.
     * @param startTimeMillis the start time to use, in milliseconds.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withStartTimeMillis(long startTimeMillis) {
      return withDefaults().startTimeMillis(startTimeMillis);
    }

    /**
     * Create a {@link LogQuery} with the given start time.
     * Shorthand for <code>LogQuery.Builder.withDefaults().startTimeUsec(startTimeUsec);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how start time is used.
     * @param startTimeUsec the start time to use, in microseconds.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withStartTimeUsec(long startTimeUsec) {
      return withDefaults().startTimeUsec(startTimeUsec);
    }

    /**
     * Create a {@link LogQuery} with the given end time. Shorthand for <code>
     * LogQuery.Builder.withDefaults().endTimeMillis(endTimeMillis);</code>. Please read the {@link
     * LogQuery} class javadoc for an explanation of how end time is used.
     *
     * @param endTimeMillis the end time to use, in milliseconds.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withEndTimeMillis(long endTimeMillis) {
      return withDefaults().endTimeMillis(endTimeMillis);
    }

    /**
     * Create a {@link LogQuery} with the given end time.
     * Shorthand for <code>LogQuery.Builder.withDefaults().endTimeUsec(endTimeUsec);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how end time is used.
     * @param endTimeUsec the start time to use, in microseconds.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withEndTimeUsec(long endTimeUsec) {
      return withDefaults().endTimeUsec(endTimeUsec);
    }

    /**
     * Create a {@link LogQuery} with the given batch size.
     * Shorthand for <code>LogQuery.Builder.withDefaults().batchSize(batchSize);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how batch size is used.
     * @param batchSize the batch size to set.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withBatchSize(int batchSize) {
      return withDefaults().batchSize(batchSize);
    }

    /**
     * Create a {@link LogQuery} with the given minimum log level.
     * Shorthand for <code>LogQuery.Builder.withDefaults().minLogLevel(minLogLevel);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how minimum log level is used.
     * @param minLogLevel the minimum log level to set.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withMinLogLevel(LogLevel minLogLevel) {
      return withDefaults().minLogLevel(minLogLevel);
    }

    /**
     * Create a {@link LogQuery} with the given include incomplete setting.
     * Shorthand for
     * <code>LogQuery.Builder.withDefaults().includeIncomplete(includeIncomplete);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how include incomplete is used.
     * @param includeIncomplete the inclusion value to set.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withIncludeIncomplete(boolean includeIncomplete) {
      return withDefaults().includeIncomplete(includeIncomplete);
    }

    /**
     * Create a {@link LogQuery} with include application logs set.
     * Shorthand for <code>LogQuery.Builder.withDefaults().includeAppLogs(includeAppLogs);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * the include application logs setting.
     * @param includeAppLogs the inclusion value to set.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withIncludeAppLogs(boolean includeAppLogs) {
      return withDefaults().includeAppLogs(includeAppLogs);
    }

    /**
     * Create a {@link LogQuery} with the given major version IDs.
     * Shorthand for <code>LogQuery.Builder.withDefaults().majorVersionIds(versionIds);</code>.
     * Please read the {@link LogQuery} class javadoc for an explanation of
     * how the list of major version ids is used.
     * @param versionIds the major version id list to set.
     * @return The newly created LogQuery instance.
     */
    public static LogQuery withMajorVersionIds(List<String> versionIds) {
      return withDefaults().majorVersionIds(versionIds);
    }

    /**
     * Create a {@link LogQuery} with the given {@link Version} values.
     * Shorthand for
     * <code>LogQuery.Builder.withDefaults().versions(versions);</code>.
     * Please read the {@link LogQuery} class javadoc for usage information.
     * @param versions the list to set.
     * @return The newly created LogQuery instance.
     */
     public static LogQuery withVersions(List<Version> versions) {
      return withDefaults().versions(versions);
    }

    /**
     * Create a {@link LogQuery} with the given request IDs.
     * Shorthand for <code>LogQuery.Builder.withDefaults().requestIds(requestIds);</code>.
     * See the {@link LogQuery} class javadoc for an explanation of
     * how the list of request ids is used.
     * @param requestIds the request id list to set.
     * @return The newly created LogQuery instance.
     * @since App Engine 1.7.4.
     */
    public static LogQuery withRequestIds(List<String> requestIds) {
      return withDefaults().requestIds(requestIds);
    }

    /**
     * Helper method for creating a {@link LogQuery} instance with
     * default values. Please read the {@link LogQuery} class javadoc for an
     * explanation of the defaults.
     */
    public static LogQuery withDefaults() {
      return new LogQuery();
    }
  }

  /**
   * Makes a copy of a provided LogQuery.
   *
   * @return A new LogQuery whose fields are copied from the given LogQuery.
   */
  @Override
  public LogQuery clone() {
    LogQuery clone;
    try {
      clone = (LogQuery) super.clone();
    } catch (CloneNotSupportedException e) {
      // This shouldn't happen - just catching to avoid making callers catch it.
      throw new RuntimeException(e);
    }

    clone.majorVersionIds = new ArrayList<String>(majorVersionIds);
    clone.requestIds = new ArrayList<String>(requestIds);
    clone.versions = new ArrayList<Version>(versions);
    return clone;
  }

  /**
   * Sets the offset.  Please read the class javadoc for an explanation of
   * how offset is used.
   * @param offset The offset to set.
   * @return {@code this} (for chaining)
   */
  public LogQuery offset(String offset) {
    this.offset = offset;
    return this;
  }

  /**
   * Sets the start time to a value in milliseconds.  Please read the class
   * javadoc for an explanation of how start time is used.
   * @param startTimeMillis The start time to set, in milliseconds.
   * @return {@code this} (for chaining)
   */
  public LogQuery startTimeMillis(long startTimeMillis) {
    this.startTimeUsec = startTimeMillis * 1000;
    return this;
  }

  /**
   * Sets the start time to a value in microseconds.  Please read the class
   * javadoc for an explanation of how start time is used.
   * @param startTimeUsec The start time to set, in microseconds.
   * @return {@code this} (for chaining)
   */
  public LogQuery startTimeUsec(long startTimeUsec) {
    this.startTimeUsec = startTimeUsec;
    return this;
  }

  /**
   * Sets the end time to a value in milliseconds.  Please read the class
   * javadoc for an explanation of how end time is used.
   * @param endTimeMillis The end time to set, in milliseconds.
   * @return {@code this} (for chaining)
   */
  public LogQuery endTimeMillis(long endTimeMillis) {
    this.endTimeUsec = endTimeMillis * 1000;
    return this;
  }

  /**
   * Sets the end time to a value in microseconds.  Please read the class
   * javadoc for an explanation of how end time is used.
   * @param endTimeUsec The end time to set, in microseconds.
   * @return {@code this} (for chaining)
   */
  public LogQuery endTimeUsec(long endTimeUsec) {
    this.endTimeUsec = endTimeUsec;
    return this;
  }

  /**
   * Sets the batch size.  Please read the class javadoc for an explanation of
   * how batch size is used.
   * @param batchSize The batch size to set.  Must be greater than 0.
   * @return {@code this} (for chaining)
   */
  public LogQuery batchSize(int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be greater than zero");
    }

    this.batchSize = batchSize;
    return this;
  }

  /**
   * Sets the minimum log level.  Please read the class javadoc for an
   * explanation of how minimum log level is used.
   * @param minLogLevel The minimum log level to set.
   * @return {@code this} (for chaining)
   */
  public LogQuery minLogLevel(LogLevel minLogLevel) {
    this.minLogLevel = minLogLevel;
    return this;
  }

  /**
   * Sets include incomplete.  Please read the class javadoc for an
   * explanation of how include incomplete is used.
   * @param includeIncomplete The value to set.
   * @return {@code this} (for chaining)
   */
  public LogQuery includeIncomplete(boolean includeIncomplete) {
    this.includeIncomplete = includeIncomplete;
    return this;
  }

  /**
   * Sets include application logs.  Please read the class javadoc for an
   * explanation of how include application logs is used.
   * @param includeAppLogs The value to set.
   * @return {@code this} (for chaining)
   */
  public LogQuery includeAppLogs(boolean includeAppLogs) {
    this.includeAppLogs = includeAppLogs;
    return this;
  }

  /**
   * Sets the major version identifiers to query.  Please read the class
   * javadoc for an explanation of how major versions are used.
   * @param versionIds The major version identifier list to set.
   * @return {@code this} (for chaining)
   */
  public LogQuery majorVersionIds(List<String> versionIds) {
    if (!versions.isEmpty()) {
      throw new IllegalStateException(
          "LogQuery.majorVersionIds may not be called after LogQuery.versions.");
    }

    for (String versionId : versionIds) {
      Matcher matcher = VERSION_PATTERN.matcher(versionId);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(
            "versionIds must only contain valid "
                + "major version identifiers. Version "
                + versionId
                + " is not a valid "
                + "major version identifier.");
      }
    }

    this.majorVersionIds = Repackaged.copyIfRepackagedElseOriginal(versionIds);
    return this;
  }

  /**
   * Restricts the query to log records for the specified module versions.
   *
   * Please read the class javadoc for usage information.
   *
   * @param versions The list of module versions to query.
   * @return {@code this} (for chaining)
   */
  public LogQuery versions(List<Version> versions) {
    if (!this.majorVersionIds.isEmpty()) {
      throw new IllegalStateException(
          "LogQuery.versions may not be called after LogQuery.majorVersionIds.");
    }

    if (!requestIds.isEmpty()) {
      throw new IllegalStateException(
          "LogQuery.versions may not be called after LogQuery.requestIds.");
    }

    this.versions.clear();
    this.versions.addAll(versions);
    return this;
  }

  /**
   * Sets the list of request ids to query.  See the class javadoc for an
   * explanation of how request ids are used.
   * @param requestIds The request id list to set.
   * @return {@code this} (for chaining)
   */
  public LogQuery requestIds(List<String> requestIds) {
    if (!versions.isEmpty()) {
      throw new IllegalStateException(
          "LogQuery.requestIds may not be called after LogQuery.versions.");
    }

    Set<String> seen = new HashSet<String>();
    for (String requestId : requestIds) {
      if (!seen.add(requestId)) {
        throw new IllegalArgumentException("requestIds must be unique.");
      }

      Matcher matcher = REQUEST_ID_PATTERN.matcher(requestId);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(
            "requestIds must only contain valid "
                + "request ids. "
                + requestId
                + " is not a valid request id.");
      }
    }

    this.requestIds = Repackaged.copyIfRepackagedElseOriginal(requestIds);
    return this;
  }

  /**
   * @return The offset, or {@code null} if none was provided.
   */
  public @Nullable String getOffset() {
    return offset;
  }

  /**
   * @return The batch size, or {@code null} if none was provided.
   */
  public @Nullable Integer getBatchSize() {
    return batchSize;
  }

  /**
   * @return The end time in milliseconds, or {@code null} if none was provided.
   */
  public @Nullable Long getEndTimeMillis() {
    return endTimeUsec != null ? endTimeUsec / 1000 : null;
  }

  /**
   * @return The end time in microseconds, or {@code null} if none was provided.
   */
  public @Nullable Long getEndTimeUsec() {
    return endTimeUsec;
  }

  /**
   * @return Whether or not application logs should be returned.
   */
  public Boolean getIncludeAppLogs() {
    return includeAppLogs;
  }

  /**
   * @return Whether or not incomplete request logs should be returned.
   */
  public Boolean getIncludeIncomplete() {
    return includeIncomplete;
  }

  /**
   * @return The minimum log level, or {@code null} if none was provided.
   */
  public @Nullable LogLevel getMinLogLevel() {
    return minLogLevel;
  }

  /**
   * @return The start time in milliseconds, or {@code null} if none was provided.
   */
  public @Nullable Long getStartTimeMillis() {
    return startTimeUsec != null ? startTimeUsec / 1000 : null;
  }

  /**
   * @return The start time in microseconds, or {@code null} if none was provided.
   */
  public @Nullable Long getStartTimeUsec() {
    return startTimeUsec;
  }

  /**
   * @return The list of major app versions that should be queried over, or
   * an empty list if none were set.
   */
  public List<String> getMajorVersionIds() {
    return majorVersionIds;
  }

  /**
   * @return The list possibly empty list of module versions that should be queried over.
   */
  public List<Version> getVersions() {
    return ImmutableCopy.list(versions);
  }

  /**
   * @return The list of request ids that should be queried over, or
   * {@code null} if none were set.
   */
  public List<String> getRequestIds() {
    return requestIds;
  }
}
