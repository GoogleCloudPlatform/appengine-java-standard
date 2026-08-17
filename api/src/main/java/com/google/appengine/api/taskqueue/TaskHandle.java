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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Created from {@link Queue#add(TaskOptions)}. Contains the task name (generated if otherwise
 * unspecified), task ETA (computed if not specified) and queue name. The queue name and task name
 * uniquely identify the task for an application.
 *
 */
public final class TaskHandle implements Serializable {
  private static final long serialVersionUID = -2578988193753847512L;
  private String taskName;
  private String queueName;
  // etaUsec is used to track the eta to the same precision that is associated
  // with the task in bigtable. Currently this is required when modifying the
  // task lease as there is a current owner check  that compares the
  // eta in bigtable with the eta associated with the task.
  // etaMillis is deprecated and is instead stored in TaskOptions. etaUsec is not
  // stored in task options as it is only required for tasks that have been
  // retrieved from the queue - and these tasks are represented by TaskHandles.
  private long etaUsec;
  private long etaMillis;
  private Integer retryCount;
  private @Nullable TaskOptions options;

  TaskHandle(TaskOptions options, String queueName, @Nullable Integer retryCount) {
    validateTaskName(options.getTaskName());
    QueueApiHelper.validateQueueName(queueName);
    this.queueName = queueName;
    this.retryCount = retryCount;
    this.taskName = null; // We now use taskName inside options.
    this.etaMillis = 0; // Ditto for etaMillis.
    this.options = new TaskOptions(options); // Defensive copy
    setEtaUsecFromOptions(this.options);
  }

  public TaskHandle(TaskOptions options, String queueName) {
    this(options, queueName, 0);
  }

  /** @deprecated Use {@link TaskHandle#TaskHandle(TaskOptions, String)} */
  @Deprecated
  public TaskHandle(String name, String queueName, long etaMillis) {
    this(TaskOptions.Builder.withTaskName(name).etaMillis(etaMillis), queueName, null);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    if (options == null) {
      // Legacy serialized TaskHandle objects do not have options.
      // We need to create them.
      options = TaskOptions.Builder.withTaskName(taskName).etaMillis(etaMillis);
      taskName = null;
      etaMillis = 0;
    }
    if (etaUsec == 0 && options.getEtaMillis() != null) {
      setEtaUsecFromOptions(options);
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = options.hashCode();
    result = result * prime + queueName.hashCode();
    result = result * prime + (retryCount == null ? 0 : retryCount.intValue());
    result = result * prime + (int) (etaUsec ^ (etaUsec >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    TaskHandle other = (TaskHandle) obj;
    if (options == null) {
      if (other.options != null) return false;
    } else if (!options.equals(other.options)) return false;
    if (queueName == null) {
      if (other.queueName != null) return false;
    } else if (!queueName.equals(other.queueName)) return false;
    if (retryCount == null) {
      if (other.retryCount != null) return false;
    } else if (!retryCount.equals(other.retryCount)) return false;
    if (etaUsec != other.etaUsec) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "TaskHandle[options="
        + options
        + "queueName="
        + queueName
        + ", retryCount="
        + retryCount
        + ", etaMillis = "
        + etaMillis
        + ", etaUsec = "
        + etaUsec
        + "]";
  }

  /**
   * Checks the name of this task matches QueueConstants.TASK_NAME_PATTERN
   *
   * @throws IllegalArgumentException
   */
  static void validateTaskName(String taskName) {
    // Verify task name matches RE specification.
    if (taskName == null
        || taskName.length() == 0
        || !QueueConstants.TASK_NAME_PATTERN.matcher(taskName).matches()) {
      throw new IllegalArgumentException(
          "Task name does not match expression "
              + QueueConstants.TASK_NAME_REGEX
              + "; given taskname: '"
              + taskName
              + "'");
    }
  }

  /**
   * Returns the name of this task. This may have been generated by a call to {@link Queue#add()} if
   * the name was not otherwise specified.
   */
  public String getName() {
    return options.getTaskName();
  }

  /** Returns the name of the queue that this task was submitted into. */
  public String getQueueName() {
    return queueName;
  }

  /**
   * Returns a time comparable to {@link System#currentTimeMillis()} when this task is scheduled for
   * execution.
   */
  public long getEtaMillis() {
    return options.getEtaMillis();
  }

  /**
   * Set the time comparable to {@link System#currentTimeMillis()} when this task is scheduled for
   * execution. For pull tasks this value specifies the lease expiration time.
   */
  void etaMillis(long etaMillis) {
    options.etaMillis(etaMillis);
    etaUsec = etaMillis * 1000;
  }

  /**
   * Returns the time when the task is scheduled for execution to microsecond precision. For pull
   * tasks this value specifies the lease expiration time. Microsecond precision is required for the
   * lease verification check when modifying the task lease.
   */
  long getEtaUsec() {
    return etaUsec;
  }

  /**
   * Set the time for when this task is scheduled for execution to microsecond precision. For pull
   * tasks this value specifies the lease expiration time.
   */
  TaskHandle etaUsec(long etaUsec) {
    this.etaUsec = etaUsec;
    options.etaMillis(etaUsec / 1000);
    return this;
  }

  /** Returns number of leases that had been performed on this task. Can return {@code null}. */
  public Integer getRetryCount() {
    return retryCount;
  }

  /** Returns binary payload data of this task. Can return {@code null}. */
  public byte[] getPayload() {
    return options.getPayload();
  }

  /**
   * Returns tag of this task. Can return {@code null}.
   *
   * @throws UnsupportedEncodingException
   */
  public String getTag() throws UnsupportedEncodingException {
    return options.getTag();
  }

  /** Returns tag of this task. Can return {@code null}. */
  public byte[] getTagAsBytes() {
    return options.getTagAsBytes();
  }

  private void setEtaUsecFromOptions(TaskOptions options) {
    if (options != null && options.getEtaMillis() != null) {
      etaUsec = options.getEtaMillis() * 1000;
    } else {
      etaUsec = 0;
    }
  }

  static final class KeyValuePair implements Map.Entry<String, String> {
    private final String key;
    private String value;

    public KeyValuePair(String key, String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public String setValue(String v) {
      String old = value;
      value = v;
      return old;
    }

    @Override
    public int hashCode() {
      int prime = 31;
      int result = 0;
      result = prime * result + key.hashCode();
      result = prime * result + value.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TaskHandle.KeyValuePair)) {
        return false;
      }
      KeyValuePair that = (KeyValuePair) o;
      return key.equals(that.key) && value.equals(that.value);
    }
  }

  /**
   * Attempts to decode the {@code payload} byte array in our {@code options} into a list of {@code
   * Map.Entry<String, String>}.
   *
   * @throws UnsupportedEncodingException if the payload cannot be decoded as a {@code
   *     application/x-www-form-urlencoded} string.
   * @throws UnsupportedOperationException if the {@code options} has no payload or the payload
   *     bytes could not be interpreted as application/x-www-form-urlencoded key-value pairs.
   */
  public List<Map.Entry<String, String>> extractParams()
      throws UnsupportedEncodingException, UnsupportedOperationException {
    String payload = new String(getPayload());
    String[] paramStrings = payload.split("&");

    List<Map.Entry<String, String>> result = new ArrayList<Map.Entry<String, String>>();
    for (String param : paramStrings) {
      String[] kv = param.split("=", 2);
      if (kv.length != 2) {
        throw new UnsupportedOperationException(
            "Payload "
                + payload
                + " failed to decode as application/x-www-form-urlencoded pairs. "
                + param
                + " Length"
                + kv.length);
      }
      result.add(
          new KeyValuePair(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8")));
    }
    return result;
  }
}
