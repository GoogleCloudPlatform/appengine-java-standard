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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One or more task names already exists in the queue.
 *
 */
public class TaskAlreadyExistsException extends RuntimeException {
  private static final long serialVersionUID = 2240328563189407018L;

  private final List<String> tasknames = new ArrayList<String>();

  public TaskAlreadyExistsException(String detail) {
    super(detail);
  }

  /**
   * Returns a list of the names of the tasks that already exist, in the same order as they were
   * given in the call to add(). Only some of the methods that throw a {@code
   * TaskAlreadyExistsException} will populate this list. Otherwise it will be an empty list.
   */
  public List<String> getTaskNames() {
    return Collections.unmodifiableList(tasknames);
  }

  /**
   * Appends "name" to the end of the list of names of tasks that could not be added because a task
   * with that name already exists in the specified queue.
   */
  void appendTaskName(String name) {
    tasknames.add(name);
  }
}
