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

import com.google.apphosting.utils.security.urlfetch.URLFetchServiceStreamHandler;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link URLStreamHandlerFactory} which installs
 * {@link URLStreamHandler URLStreamHandlers} that the
 * App Engine runtime needs to support. (For example,
 * for the "http" and "https" protocols).
 *
 */
public class StreamHandlerFactory implements URLStreamHandlerFactory {
  private final Map<String, URLStreamHandler> handlers = new HashMap<>();

  public StreamHandlerFactory() {
    URLFetchServiceStreamHandler httpHandler = new URLFetchServiceStreamHandler();
    handlers.put("http", httpHandler);
    handlers.put("https", httpHandler);
  }

  @Override
  public URLStreamHandler createURLStreamHandler(String protocol) {
    return handlers.get(protocol);
  }
}
