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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueAddRequest.Header;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueMode.Mode;
import com.google.appengine.api.taskqueue_bytes.TaskQueuePb.TaskQueueRetryParameters;
import com.google.appengine.tools.development.Clock;
import com.google.apphosting.utils.config.QueueXml.Entry;
import com.google.common.collect.ImmutableList;
import java.util.Date;
import java.util.List;

/**
 * Dev Server task queue state descriptor.
 *
 * <p>Used by JSP/JSTL functions to display UI components. Access through JSTL can be somewhat
 * awkward so a number of accessors in this class are made specifically for JSTL rendering. We've
 * encountered some seemingly random NPEs in the pb library code that generates property descriptors
 * for pbs that are used as java beans, and rather than trying to fight that battle we have instead
 * taken the approach of not exposing any pbs to the JSP. Instead, we wrap pbs in standard java
 * beans.
 */
public final class QueueStateInfo {
  /** Description of task state information. */
  public static final class TaskStateInfo {
    private final String taskName;
    private final long etaMillis;
    private final TaskQueueAddRequest.Builder addRequest;
    private final Clock clock;

    public TaskStateInfo(
        String taskName, long etaMillis, TaskQueueAddRequest.Builder addRequest, Clock clock) {
      this.taskName = taskName;
      this.etaMillis = etaMillis;
      this.addRequest = addRequest;
      this.clock = clock;
    }

    public String getTaskName() {
      return taskName;
    }

    public long getEtaMillis() {
      return etaMillis;
    }

    public Date getEta() {
      return new Date(etaMillis);
    }

    public double getEtaDelta() {
      double delta = etaMillis - clock.getCurrentTime();
      return delta / 1000;
    }

    public String getMethod() {
      return addRequest.getMethod().name();
    }

    public String getUrl() {
      return addRequest.getUrl().toStringUtf8();
    }

    public String getBody() {
      return addRequest.getBody().toStringUtf8();
    }

    public byte[] getBodyAsBytes() {
      return addRequest.getBody().toByteArray();
    }

    public ImmutableList<HeaderWrapper> getHeaders() {

      // Wrap the headers to avoid exposing pbs to jsp.
      return addRequest.getHeaderList().stream().map(HeaderWrapper::new).collect(toImmutableList());
    }

    public byte[] getTagAsBytes() {
      if (!addRequest.hasTag()) {
        return null;
      }
      return addRequest.getTag().toByteArray();
    }

    public TaskQueueRetryParameters getRetryParameters() {
      return addRequest.getRetryParameters();
    }

    // for testing
    TaskQueueAddRequest.Builder getAddRequest() {
      return addRequest;
    }
  }

  private final Entry entry;
  private final List<TaskStateInfo> taskInfo;

  public QueueStateInfo(Entry entry, List<TaskStateInfo> taskInfo) {
    this.entry = entry;
    this.taskInfo = taskInfo;
  }

  public Entry getEntry() {
    return entry;
  }

  public Mode getMode() {
    if ("pull".equals(entry.getMode())) {
      return Mode.PULL;
    } else {
      return Mode.PUSH;
    }
  }

  public int getBucketSize() {
    if (entry.getBucketSize() == null) {
      return DevPushQueue.DEFAULT_BUCKET_SIZE;
    }
    return entry.getBucketSize();
  }

  public List<TaskStateInfo> getTaskInfo() {
    return taskInfo;
  }

  public int getCountTasks() {
    return taskInfo.size();
  }

  public int getCountUnfinishedTasks() {
    return taskInfo.size();
  }

  public Date getOldestTaskEta() {
    if (taskInfo.isEmpty()) {
      return null;
    }
    return new Date(taskInfo.get(0).getEtaMillis());
  }

  /** Wrapper for a {@link Header} to avoid exposing pbs to jsp. */
  public static final class HeaderWrapper {
    private final Header delegate;

    private HeaderWrapper(Header delegate) {
      this.delegate = delegate;
    }

    public String getKey() {
      return delegate.getKey().toStringUtf8();
    }

    public String getValue() {
      return delegate.getValue().toStringUtf8();
    }
  }
}
