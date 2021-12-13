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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 */
public class RequestState {
  private boolean allowNewRequestThreadCreation = true;
  private boolean softDeadlinePassed = false;
  private boolean hardDeadlinePassed = false;
  private final Set<Thread> requestThreads = new LinkedHashSet<>();

  public synchronized boolean getAllowNewRequestThreadCreation() {
    return allowNewRequestThreadCreation;
  }

  public synchronized void setAllowNewRequestThreadCreation(boolean allowNewRequestThreadCreation) {
    this.allowNewRequestThreadCreation = allowNewRequestThreadCreation;
  }

  public synchronized boolean hasSoftDeadlinePassed() {
    return softDeadlinePassed;
  }

  public synchronized void setSoftDeadlinePassed(boolean softDeadlinePassed) {
    this.softDeadlinePassed = softDeadlinePassed;
  }

  public synchronized boolean hasHardDeadlinePassed() {
    return hardDeadlinePassed;
  }

  public synchronized void setHardDeadlinePassed(boolean hardDeadlinePassed) {
    this.hardDeadlinePassed = hardDeadlinePassed;
  }

  /**
   * Records the given thread as belonging to this request. That means that it should terminate
   * when the request terminates.
   */
  public synchronized void recordRequestThread(Thread thread) {
    requestThreads.add(thread);
  }

  /**
   * Forgets the given thread relative to this request. Typically this happens just before the
   * thread terminates.
   */
  public synchronized void forgetRequestThread(Thread thread) {
    requestThreads.remove(thread);
  }

  /**
   * Returns a snapshot of the threads that belong to this request and that should terminate
   * when the request terminates.
   */
  public synchronized Set<Thread> requestThreads() {
    return new LinkedHashSet<>(requestThreads);
  }
}
