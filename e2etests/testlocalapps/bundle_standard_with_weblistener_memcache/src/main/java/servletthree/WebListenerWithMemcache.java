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

package servletthree;

import com.google.appengine.api.memcache.BaseMemcacheService;
import com.google.appengine.api.memcache.ErrorHandler;
import com.google.appengine.api.memcache.InvalidValueException;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/** Simple WebListener that depends on some GAE API (memcache) to reproduce b/120480580. */
@WebListener
public class WebListenerWithMemcache implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    BaseMemcacheService bms = MemcacheServiceFactory.getMemcacheService();
    bms.setErrorHandler(
        new ErrorHandler() {
          @Override
          public void handleDeserializationError(InvalidValueException e) {}

          @Override
          public void handleServiceError(MemcacheServiceException e) {}
        });
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {}
}
