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

import static com.google.common.truth.Truth.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import junit.framework.TestCase;

/**
 * Unit tests for {@link TaskHandle}.
 *
 */
public class TaskHandleTest extends TestCase {
  public void testHashCode() {
    TaskOptions options1 = TaskOptions.Builder.withTaskName("foo");
    TaskOptions options2 = TaskOptions.Builder.withTaskName("bar");

    assertThat(new TaskHandle(options2, "queue", 0).hashCode())
        .isNotEqualTo(new TaskHandle(options1, "queue", 0).hashCode());
    assertThat(new TaskHandle(options1, "queue2", 0).hashCode())
        .isNotEqualTo(new TaskHandle(options1, "queue1", 0).hashCode());
    assertThat(new TaskHandle(options1, "queue", 1).hashCode())
        .isNotEqualTo(new TaskHandle(options1, "queue", 0).hashCode());

    long timeUsec = System.currentTimeMillis() * 1000;

    TaskHandle task1 = new TaskHandle(options1, "queue", 0).etaUsec(timeUsec);
    TaskHandle task2 = new TaskHandle(options1, "queue", 0).etaUsec(timeUsec + 1);
    assertThat(task2.hashCode()).isNotEqualTo(task1.hashCode());
  }

  public void testEquals() {
    TaskOptions options1 = TaskOptions.Builder.withTaskName("foo");
    TaskOptions options2 = TaskOptions.Builder.withTaskName("bar");

    TaskHandle task1 = new TaskHandle(options1, "queue", 30);
    TaskHandle task2 = new TaskHandle(options1, "queue", 30);
    TaskHandle task3 = new TaskHandle(options2, "queue", 30);
    TaskHandle task4 = new TaskHandle(options1, "queue2", 30);
    TaskHandle task5 = new TaskHandle(options1, "queue", 3);

    assertTrue(task1.equals(task2));
    assertFalse(task1.equals(task3));
    assertFalse(task1.equals(task4));
    assertFalse(task1.equals(task5));
    assertTrue(task2.equals(task1));
    assertFalse(task2.equals(task3));
    assertFalse(task2.equals(task4));
    assertFalse(task2.equals(task5));
    assertFalse(task3.equals(task1));
    assertFalse(task3.equals(task2));
    assertFalse(task3.equals(task4));
    assertFalse(task3.equals(task5));
    assertFalse(task4.equals(task1));
    assertFalse(task4.equals(task2));
    assertFalse(task4.equals(task3));
    assertFalse(task4.equals(task5));
    assertFalse(task5.equals(task1));
    assertFalse(task5.equals(task2));
    assertFalse(task5.equals(task3));
    assertFalse(task5.equals(task4));
  }

  // Returns content in the x-www-form-urlencoded format for parameters.
  String encodeParamsUrlEncoded(List<TaskOptions.Param> params) {
    StringBuilder result = new StringBuilder();
    try {
      String appender = "";
      for (TaskOptions.Param param : params) {
        result.append(appender);
        appender = "&";
        result.append(param.getURLEncodedName());
        result.append("=");
        result.append(param.getURLEncodedValue());
      }
    } catch (UnsupportedEncodingException exception) {
      throw new UnsupportedTranslationException(exception);
    }
    return result.toString();
  }

  public void testExtractParams() throws UnsupportedEncodingException {
    List<TaskHandle.KeyValuePair> kvList = new ArrayList<TaskHandle.KeyValuePair>();
    kvList.add(new TaskHandle.KeyValuePair("foo", "bar"));
    kvList.add(new TaskHandle.KeyValuePair("foo", "http://www.google.com"));
    kvList.add(new TaskHandle.KeyValuePair("foo2", "http://myapp.appspot.com/do?thing=this"));

    TaskOptions options = TaskOptions.Builder.withTaskName("task");
    for (TaskHandle.KeyValuePair kv : kvList) {
      options.param(kv.getKey(), kv.getValue());
    }
    options.payload(encodeParamsUrlEncoded(options.getParams()));

    TaskHandle taskHandle = new TaskHandle(options, "queue", 0);

    List<Map.Entry<String, String>> outKvList = taskHandle.extractParams();
    assertEquals(kvList.size(), outKvList.size());

    ListIterator<TaskHandle.KeyValuePair> i1 = kvList.listIterator();
    ListIterator<Map.Entry<String, String>> i2 = outKvList.listIterator();

    while (i1.hasNext() && i2.hasNext()) {
      TaskHandle.KeyValuePair kv1 = (TaskHandle.KeyValuePair) i1.next();
      @SuppressWarnings("unchecked")
      Map.Entry<String, String> kv2 = (Map.Entry<String, String>) i2.next();

      assertTrue(kv1.equals(kv2));
    }
  }
}
