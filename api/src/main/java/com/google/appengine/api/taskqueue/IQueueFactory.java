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

package com.google.appengine.api.taskqueue;

/**
 * Creates {@link Queue} objects. {@link QueueFactory} is thread safe.
 *
 */
public interface IQueueFactory {

  /**
   * Returns the {@link Queue} by name.
   *
   * <p>The returned {@link Queue} object may not necessarily refer to an existing queue. Queues
   * must be configured before they may be used. Attempting to use a non-existing queue name may
   * result in errors at the point of use of the {@link Queue} object and not when calling {@link
   * #getQueue(String)}.
   */
  Queue getQueue(String queueName);
}
