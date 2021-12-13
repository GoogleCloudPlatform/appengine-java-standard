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

import java.util.HashMap;
import java.util.Map;

/**
 * Creates {@link Queue} objects. {@link QueueFactory} is thread safe.
 *
 */
final class QueueFactoryImpl implements IQueueFactory {
  private static final QueueApiHelper helper = new QueueApiHelper();
  // Access to queueMap must be synchronized.
  private static final Map<String, Queue> queueMap = new HashMap<String, Queue>();

  @Override
  public synchronized Queue getQueue(String queueName) {
    Queue queue = queueMap.get(queueName);
    if (queue == null) {
      queue = new QueueImpl(queueName, helper);
      queueMap.put(queueName, queue);
    }
    return queue;
  }
}
