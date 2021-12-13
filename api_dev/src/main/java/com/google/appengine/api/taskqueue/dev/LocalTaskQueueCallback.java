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

package com.google.appengine.api.taskqueue.dev;

import com.google.appengine.api.urlfetch.URLFetchServicePb;
import java.io.Serializable;
import java.util.Map;

/**
 * A callback that is asynchronously invoked by the local Task Queue. Even though the interface is
 * defined in terms of a url fetch and its response, implementors are free to execute the request in
 * any way they see fit.
 *
 */
public interface LocalTaskQueueCallback extends Serializable {

  /**
   * This method will be invoked from {@link
   * LocalTaskQueue#init(com.google.appengine.tools.development.LocalServiceContext, Map)}
   * forwarding the {@code properties} parameter.
   */
  void initialize(Map<String, String> properties);

  /**
   * Execute the provided url fetch request.
   *
   * @param req The url fetch request
   * @return The HTTP status code of the fetch.
   */
  int execute(URLFetchServicePb.URLFetchRequest req);
}
