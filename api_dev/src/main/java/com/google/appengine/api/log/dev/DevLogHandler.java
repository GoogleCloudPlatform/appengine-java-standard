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

import com.google.appengine.tools.development.LocalEnvironment;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.ApiProxy.Environment;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class DevLogHandler extends StreamHandler {
  LocalLogService service = null;
  
  /**
   * Creates a new LogHandler that writes log records to the LocalLogService.
   */
  public DevLogHandler(LocalLogService serviceToUse) {
    service = serviceToUse;
  }
  
  /**
   * Writes an application-level log to the LocalLogService.
   * 
   * @see StreamHandler#publish(LogRecord)
   * @param record The application-level log that should be recorded.
   */
  @Override
  public void publish(LogRecord record) {
    service.addAppLogLine(getRequestId(), record.getMillis() * 1000,
        convertLogLevel(record.getLevel()), record.getMessage());
    super.publish(record);
  }
  
  public int convertLogLevel(Level level) {
    if ((level == Level.FINEST) || (level == Level.FINER) || (level == Level.FINE)) {
      return 0;
    } else if (level == Level.CONFIG) {
      return 1;
    } else if (level == Level.INFO) {
      return 2;
    } else if (level == Level.WARNING) {
      return 3;
    } else { // level == Level.SEVERE
      return 4;
    }
  }
  
  public static String getRequestId() {
    Environment environment = ApiProxy.getCurrentEnvironment();

    // If the dev_appserver's environment hasn't been created yet, throw away
    // all logs it's trying to store by storing them under a sentinel value
    if (environment == null) {
      return "0";
    }

    Map<String, Object> attrs = environment.getAttributes();
    
    String internalRequestId = (String) attrs.get(LocalEnvironment.REQUEST_ID);
    
    if (internalRequestId == null) {
      return "0";
    }

    return internalRequestId;
  }
}
