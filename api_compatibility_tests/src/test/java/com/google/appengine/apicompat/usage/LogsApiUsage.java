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
package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;

import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.ILogServiceFactory;
import com.google.appengine.api.log.ILogServiceFactoryProvider;
import com.google.appengine.api.log.InvalidRequestException;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogQuery.Version;
import com.google.appengine.api.log.LogQueryResult;
import com.google.appengine.api.log.LogService;
import com.google.appengine.api.log.LogServiceException;
import com.google.appengine.api.log.LogServiceFactory;
import com.google.appengine.api.log.RequestLogs;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import com.google.apphosting.api.logservice.LogServicePb;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Exhaustive usage of the Logs Api. Used for backward compatibility checks. */
@SuppressWarnings("unused")
public class LogsApiUsage {

  /**
   * Exhaustive use of {@link LogServiceFactory}.
   */
  public static class LogServiceFactoryUsage extends ExhaustiveApiUsage<LogServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      LogServiceFactory factory = new LogServiceFactory(); // ugh
      LogService logSvc = LogServiceFactory.getLogService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link ILogServiceFactory}.
   */
  public static class ILogServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<ILogServiceFactory> {

    @Override
    public Set<Class<?>> useApi(ILogServiceFactory iLogServiceFactory) {
      iLogServiceFactory.getLogService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link ILogServiceFactoryProvider}.
   */
  public static class ILogServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<ILogServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      ILogServiceFactoryProvider iLogServiceFactoryProvider
          = new ILogServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link LogService}.
   */
  public static class LogServiceUsage extends ExhaustiveApiInterfaceUsage<LogService> {
    int ___apiConstant_DEFAULT_ITEMS_PER_FETCH;

    @Override
    protected Set<Class<?>> useApi(LogService logSvc) {
      LogQuery query = null;
      Iterable<RequestLogs> result = logSvc.fetch(query);
      ___apiConstant_DEFAULT_ITEMS_PER_FETCH = LogService.DEFAULT_ITEMS_PER_FETCH;;
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link AppLogLine}.
   */
  public static class AppLogLineUsage extends ExhaustiveApiUsage<AppLogLine> {

    @Override
    public Set<Class<?>> useApi() {
      AppLogLine line = new AppLogLine();
      LogService.LogLevel level = line.getLogLevel();
      String strVal = line.getLogMessage();
      long longVal = line.getTimeUsec();
      strVal = line.toString();
      line.setLogLevel(level);
      line.setLogMessage("yar");
      line.setTimeUsec(30L);

      int unusedHashCode = line.hashCode();
      Object sentinel = null;
      boolean unusedEquals = line.equals(sentinel);
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link InvalidRequestException}.
   */
  public static class InvalidRequestExceptionUsage
      extends ExhaustiveApiUsage<InvalidRequestException> {

    @Override
    public Set<Class<?>> useApi() {
      InvalidRequestException ex = new InvalidRequestException("yar");
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link LogServiceException}.
   */
  public static class LogServiceExceptionUsage extends ExhaustiveApiUsage<LogServiceException> {

    @Override
    public Set<Class<?>> useApi() {
      LogServiceException ex = new LogServiceException("yar");
      ex = new LogServiceException("yar", new Throwable());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link LogQuery}.
   */
  public static class LogQueryUsage extends ExhaustiveApiUsage<LogQuery> {

    @Override
    public Set<Class<?>> useApi() {
      LogQuery query = new LogQuery();
      query = query.batchSize(10);
      query = query.endTimeUsec(30L);
      Integer integerVal = query.getBatchSize();
      Long longVal = query.getEndTimeUsec();
      Boolean booleanVal = query.getIncludeAppLogs();
      booleanVal = query.getIncludeIncomplete();
      List<String> strVals = query.getMajorVersionIds();
      LogService.LogLevel logLevel = query.getMinLogLevel();
      String strVal = query.getOffset();
      longVal = query.getStartTimeUsec();
      longVal = query.getEndTimeMillis();
      strVals = query.getRequestIds();
      longVal = query.getStartTimeMillis();
      query = query.includeAppLogs(true);
      query = query.includeIncomplete(true);
      query = query.majorVersionIds(strVals);
      query = query.minLogLevel(logLevel);
      query = query.offset("offset");
      query = query.startTimeUsec(30L);
      query = query.endTimeMillis(30L);
      query = query.startTimeMillis(30L);
      query = query.requestIds(Arrays.asList("1", "2"));
      LogQuery clone = query.clone();

      query = new LogQuery();
      List<Version> versions = query.getVersions();
      query.versions(versions);
      return classes(Object.class, Cloneable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link LogQuery.Builder}.
   */
  public static class LogQueryBuilderUsage extends ExhaustiveApiUsage<LogQuery.Builder> {

    @Override
    public Set<Class<?>> useApi() {
      LogQuery.Builder builder = new LogQuery.Builder(); // ugh
      LogQuery query = LogQuery.Builder.withBatchSize(30);
      query = LogQuery.Builder.withDefaults();
      query = LogQuery.Builder.withEndTimeUsec(30L);
      query = LogQuery.Builder.withBatchSize(12);
      query = LogQuery.Builder.withIncludeAppLogs(true);
      query = LogQuery.Builder.withIncludeIncomplete(true);
      List<String> strList = Arrays.asList("1", "2");
      query = LogQuery.Builder.withMajorVersionIds(strList);
      query = LogQuery.Builder.withMinLogLevel(LogService.LogLevel.DEBUG);
      query = LogQuery.Builder.withOffset("9");
      query = LogQuery.Builder.withStartTimeUsec(30L);
      query = LogQuery.Builder.withRequestIds(Arrays.asList("1", "2"));
      query = LogQuery.Builder.withEndTimeMillis(30L);
      query = LogQuery.Builder.withStartTimeMillis(30L);

      List<LogQuery.Version> versions = Lists.newArrayList(
          new LogQuery.Version("module1", "version1"),
          new LogQuery.Version("module2", "version1"));
      query = LogQuery.Builder.withVersions(versions);
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link LogQuery#versions(List)}.
   */
  public static class LogQueryVersionsUsage extends
      ExhaustiveApiUsage<Version> {

    @Override
    public Set<Class<?>> useApi() {
      Version version = new Version("module1", "version1");
      version.getModuleId();
      version.getVersionId();
      int unusedHashCode = version.hashCode();
      String unusedToString = version.toString();
      boolean unusedEquals = version.equals(version);
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link LogQueryResult}.
   */
  public static class LogQueryResultUsage extends ExhaustiveApiUsage<LogQueryResult> {

    @Override
    public Set<Class<?>> useApi() {
      try {
        // LogQueryResult is unused in our public API, the class is final, and the constructor is
        // protected. This means we have no means of constructing an instance of this class from
        // outside the package. We resort to reflection to get around this.
        Constructor<LogQueryResult> ctor =
            LogQueryResult.class.getDeclaredConstructor(
                LogServicePb.LogReadResponse.class, LogQuery.class);
        ctor.setAccessible(true);
        LogServicePb.LogReadResponse resp = LogServicePb.LogReadResponse.getDefaultInstance();
        LogQuery query = new LogQuery();
        LogQueryResult result = ctor.newInstance(resp, query);
        Iterator<RequestLogs> iter = result.iterator();
        return classes(Object.class, Iterable.class);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Exhaustive use of {@link LogService.LogLevel}.
   */
  public static class LogLevelUsage extends ExhaustiveApiUsage<LogService.LogLevel> {

    @Override
    public Set<Class<?>> useApi() {
      LogService.LogLevel level = LogService.LogLevel.DEBUG;
      level = LogService.LogLevel.ERROR;
      level = LogService.LogLevel.FATAL;
      level = LogService.LogLevel.INFO;
      level = LogService.LogLevel.WARN;
      level = LogService.LogLevel.valueOf("DEBUG");
      LogService.LogLevel[] values = LogService.LogLevel.values();
      return classes(Object.class, Enum.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link RequestLogs}.
   */
  public static class RequestLogsUsage extends ExhaustiveApiUsage<RequestLogs> {

    @Override
    public Set<Class<?>> useApi() {
      RequestLogs logs = new RequestLogs();
      long longVal = logs.getApiMcycles();
      String strVal = logs.getAppId();
      List<AppLogLine> logLines = logs.getAppLogLines();
      strVal = logs.getCombined();
      double doubleVal = logs.getCost();
      longVal = logs.getEndTimeUsec();
      strVal = logs.getHost();
      strVal = logs.getHttpVersion();
      strVal = logs.getInstanceKey();
      strVal = logs.getIp();
      longVal = logs.getLatencyUsec();
      longVal = logs.getMcycles();
      strVal = logs.getMethod();
      strVal = logs.getNickname();
      strVal = logs.getOffset();
      longVal = logs.getPendingTimeUsec();
      strVal = logs.getReferrer();
      int intVal = logs.getReplicaIndex();
      strVal = logs.getRequestId();
      strVal = logs.getResource();
      longVal = logs.getResponseSize();
      longVal = logs.getStartTimeUsec();
      intVal = logs.getStatus();
      strVal = logs.getTaskName();
      strVal = logs.getTaskQueueName();
      strVal = logs.getUrlMapEntry();
      strVal = logs.getUserAgent();
      strVal = logs.getModuleId();
      strVal = logs.getVersionId();
      boolean boolVal = logs.isFinished();
      boolVal = logs.isLoadingRequest();
      strVal = logs.getAppEngineRelease();
      strVal = logs.toString();

      logs.setApiMcycles(33L);
      logs.setAppId("yar");
      logs.setAppLogLines(logLines);
      logs.setCombined("yar");
      logs.setCost(23.4d);
      logs.setEndTimeUsec(33L);
      logs.setHost("yar");
      logs.setHttpVersion("yar");
      logs.setInstanceKey("yar");
      logs.setIp("yar");
      logs.setLatency(33L);
      logs.setMcycles(33L);
      logs.setMethod("yar");
      logs.setNickname("yar");
      logs.setOffset("yar");
      logs.setPendingTime(33L);
      logs.setReferrer("yar");
      logs.setReplicaIndex(9);
      logs.setRequestId("yar");
      logs.setResource("yar");
      logs.setResponseSize(23L);
      logs.setStartTimeUsec(33L);
      logs.setStatus(4);
      logs.setTaskName("yar");
      logs.setTaskQueueName("yar");
      logs.setUrlMapEntry("yar");
      logs.setUserAgent("yar");
      logs.setModuleId("far");
      logs.setVersionId("yar");
      logs.setFinished(true);
      logs.setWasLoadingRequest(true);
      logs.setAppEngineRelease("yar");

      int unusedHashCode = logs.hashCode();
      Object sentinel = null;
      boolean unusedEquals = logs.equals(sentinel);
      return classes(Object.class, Serializable.class);
    }
  }

}
