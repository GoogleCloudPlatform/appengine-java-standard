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

import com.google.appengine.api.log.LogService.LogLevel;
import java.io.Serializable;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An AppLogLine contains all the information for a single application
 * log. Specifically, this information is: (1) the time at which the logged
 * event occurred, (2) the level that the event was logged at, and (3) the
 * message associated with this event. AppLogLines may be inserted by the user
 * via logging frameworks, or by App Engine itself if we wish to alert the user
 * that certain events have occurred.
 * 
 *
 */
public final class AppLogLine implements Serializable {
  private static final long serialVersionUID = 1352587017790884498L;

  private long timeUsec;
  private LogLevel logLevel;
  private String logMessage;
  
  /**
   * Default zero-argument constructor that creates an AppLogLine.
   */
  public AppLogLine() {
    
  }
  
  /**
   * Constructs a new application log.
   * 
   * @param newTimeUsec The time that the logged event has occurred at, in 
   *   microseconds since epoch.
   * @param newLogLevel The level that the event was logged at.
   * @param newLogMessage The message associated with this event.
   */
  AppLogLine(long newTimeUsec, LogLevel newLogLevel, String newLogMessage) {
    timeUsec = newTimeUsec;
    logLevel = newLogLevel;
    logMessage = newLogMessage;
  }

  public LogLevel getLogLevel() {
    return logLevel;
  }
  
  public String getLogMessage() {
    return logMessage;
  }
  
  public long getTimeUsec() {
    return timeUsec;
  }
  
  public void setLogLevel(LogLevel logLevel) {
    this.logLevel = logLevel;
  }
  
  public void setLogMessage(String logMessage) {
    this.logMessage = logMessage;
  }
  
  public void setTimeUsec(long timeUsec) {
    this.timeUsec = timeUsec;
  }

  @Override
  public String toString() {
    return "AppLogLine{" +
        "timeUsec=" + timeUsec +
        ", logLevel=" + logLevel +
        ", logMessage='" + logMessage + '\'' +
        '}';
  }

  @Override
  public int hashCode() {
    return Objects.hash(timeUsec, logLevel, logMessage);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    AppLogLine other = (AppLogLine) obj;
    return timeUsec == other.timeUsec
        && Objects.equals(logLevel, other.logLevel)
        && Objects.equals(logMessage, other.logMessage);
  }
}
