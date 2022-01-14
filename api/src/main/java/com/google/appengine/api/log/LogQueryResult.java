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

import static com.google.common.io.BaseEncoding.base64;

import com.google.apphosting.api.logservice.LogServicePb.LogOffset;
import com.google.apphosting.api.logservice.LogServicePb.LogReadResponse;
import com.google.apphosting.api.logservice.LogServicePb.RequestLog;
import com.google.common.base.CharMatcher;
import com.google.common.collect.AbstractIterator;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An object that is the result of performing a LogService.fetch() operation. LogQueryResults
 * contain the logs from the user's query. Users of this service should use the {@link
 * LogQueryResult#iterator} provided by this class to retrieve their results.
 */
// Internally, LogQueryResults also contain a cursor that can be used
// to grab more results as needed.
// TODO: Implement Serializable properly (b/5681093).
public final class LogQueryResult implements Iterable<RequestLogs> {
  private final List<RequestLogs> logs;
  private final @Nullable String cursor;
  private final LogQuery query;

  protected LogQueryResult(LogReadResponse response, LogQuery originalQuery) {
    logs = new ArrayList<>();
    for (RequestLog log : response.getLogList()) {
      String offset = base64().encode(log.getOffset().toByteArray());
      logs.add(new RequestLogs(log, offset));
    }

    if (response.hasOffset()) {
      cursor = base64().encode(response.getOffset().toByteArray());
    } else {
      cursor = null;
    }

    // We can't trust that the user won't modify the parameters in
    // originalQuery, so since we need them, we make our own copy of it.
    query = originalQuery.clone();
  }

  /**
   * Returns a LogOffset parsed from the submitted String, which is assumed to be a Base64-encoded
   * offset produced by this class.
   *
   * @return A String to parse as a Base64-encoded LogOffset protocol buffer.
   */
  protected static LogOffset parseOffset(String offset) {
    LogOffset.Builder logOffset = LogOffset.newBuilder();
    try {

      logOffset.mergeFrom(
          base64().decode(CharMatcher.whitespace().removeFrom(offset)),
          ExtensionRegistry.getEmptyRegistry());

      if (!logOffset.isInitialized()) {
        throw new IllegalArgumentException();
      }
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Can not parse provided offset.");
    }
    return logOffset.build();
  }

  /**
   * Returns the list of logs internally kept by this class. The main user of
   * this method is iterator, who needs it to give the user logs as needed.
   *
   * @return A List of RequestLogs acquired from a fetch() request.
   */
  private List<RequestLogs> getLogs() {
    return Collections.unmodifiableList(logs);
  }

  /**
   * Returns the String version of the database cursor, which can be used to
   * tell subsequent fetch() requests where to start scanning from. The main
   * user of this method is iterator, who uses it to ensure that users get all
   * the logs they requested.
   *
   * @return A String representing the next location in the database to read
   *   from.
   */
  private @Nullable String getCursor() {
    return cursor;
  }

  /**
   * Returns an Iterator that will yield all of the logs the user has requested.
   * If the user has asked for more logs than a single request can accommodate
   * (which is LogService.MAX_ITEMS_PER_FETCH), then this iterator grabs
   * the first batch and returns them until they are exhausted. Once they are
   * exhausted, a fetch() call is made to get more logs and the process is
   * repeated until either all of the logs have been read or the user has
   * stopped asking for more logs.
   * 
   * @return An iterator that provides RequestLogs to the caller.
   */
  @Override
  public Iterator<RequestLogs> iterator() {    
    return new AbstractIterator<RequestLogs>() {
      List<RequestLogs> iterLogs = logs;
      @Nullable String iterCursor = cursor;
      int index = 0;
      int lengthLogs = iterLogs.size();
      
      @Override
      protected RequestLogs computeNext() {
        while (index >= lengthLogs) {
          if (iterCursor == null) {
            return endOfData();
          }
          
          // query has all the parameters from the initial request in it, so
          // just update it with the new cursor and grab more results.
          query.offset(iterCursor);

          LogQueryResult nextResults = new LogServiceImpl().fetch(query);
          iterLogs = nextResults.getLogs();
          iterCursor = nextResults.getCursor();
          lengthLogs = iterLogs.size();
          index = 0;
        }

        return iterLogs.get(index++);
      }
    };
  }
}
