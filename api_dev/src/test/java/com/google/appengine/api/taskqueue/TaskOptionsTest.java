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

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withCountdownMillis;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withDefaults;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withEtaMillis;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withHeader;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withMethod;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withParam;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withRetryOptions;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withTaskName;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;

/**
 * Unit tests for {@link TaskOptions}.
 *
 */
public class TaskOptionsTest extends TestCase {
  // A deferred task for testing.
  private static class TestDeferredTask implements DeferredTask {
    String compare;

    private TestDeferredTask(String compare) {
      super();
      this.compare = compare;
    }

    @Override
    public void run() {}

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((compare == null) ? 0 : compare.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      TestDeferredTask other = (TestDeferredTask) obj;
      if (compare == null) {
        if (other.compare != null) return false;
      } else if (!compare.equals(other.compare)) return false;
      return true;
    }
  }

  public static final DeferredTask DEFERRED_TASK_1 = new TestDeferredTask("task 1");

  public void testTagWithNonAsciiCharacter() throws Exception {
    String tagWithNonAsciiCharacter = "tag:\u00BD"; // "fraction one half" character

    TaskOptions taskOptionsWithTagSet = withDefaults().tag(tagWithNonAsciiCharacter);

    assertEquals(
        "non-ASCII character in the tag must not be garbled:",
        tagWithNonAsciiCharacter,
        taskOptionsWithTagSet.getTag());
  }

  public void testTaskOptions() throws Exception {
    assertEquals("/A/Url", withUrl("/A/Url").getUrl());
    assertEquals((Long) 345L, withCountdownMillis(345L).getCountdownMillis());
    assertEquals((Long) 345L, withEtaMillis(345L).getEtaMillis());
    assertEquals(TaskOptions.Method.POST, withDefaults().getMethod());
    assertEquals(TaskOptions.Method.HEAD, withMethod(TaskOptions.Method.HEAD).getMethod());
    assertEquals("ATaskName", withTaskName("ATaskName").getTaskName());
    assertEquals(Lists.newArrayList("BBB"), withHeader("AAA", "BBB").getHeaders().get("AAA"));
    assertTrue(null == withHeader("AAA", "BBB").removeHeader("AAA").getHeaders().get("AAA"));
    assertTrue(Arrays.equals(new byte[] {'B', 'B'}, withPayload("BB").getPayload()));

    RetryOptions retry = withTaskRetryLimit(10).taskAgeLimitSeconds(172800);
    assertEquals(
        retry,
        withRetryOptions(withTaskRetryLimit(10).taskAgeLimitSeconds(172800)).getRetryOptions());

    // Non-PULL tasks automatically set content-type header if the payload is String.
    assertEquals(
        Lists.newArrayList("text/plain; charset=UTF-8"),
        withPayload("foo").getHeaders().get("content-type"));

    // PULL tasks do not set headers if payload is String.
    assertTrue(withMethod(TaskOptions.Method.PULL).payload("foo").getHeaders().isEmpty());
    assertTrue(
        Arrays.equals(
            "tag".getBytes(UTF_8), withMethod(TaskOptions.Method.PULL).tag("tag").getTagAsBytes()));
    assertEquals("tag", withMethod(TaskOptions.Method.PULL).tag("tag").getTag());
    assertEquals(null, withMethod(TaskOptions.Method.PULL).tag((String) null).getTag());
    assertEquals(null, withMethod(TaskOptions.Method.PULL).tag((byte[]) null).getTag());
    // Empty tags are ignored.
    assertEquals(null, withMethod(TaskOptions.Method.PULL).tag("").getTag());
    assertEquals(null, withMethod(TaskOptions.Method.PULL).tag("".getBytes()).getTag());
  }

  public void testCopyConstructor() throws Exception {
    RetryOptions retry = withTaskRetryLimit(10).taskAgeLimitSeconds(86400);
    TaskOptions options1 =
        withUrl("/A/B")
            .header("Header", "HeaderValue")
            .method(TaskOptions.Method.HEAD)
            .param("Param1", "StringValue")
            .param("Param2", "ByteValue".getBytes(UTF_8))
            .payload("payload")
            .taskName("TaskName")
            .retryOptions(retry)
            .tag("tag");
    TaskOptions options2 = new TaskOptions(options1);

    assertStringMapsEqualIncludingOrder(options1.getHeaders(), options2.getHeaders());
    assertNotSame(options1.getHeaders(), options2.getHeaders());
    assertEquals(options1.getMethod(), options2.getMethod());
    assertEquals(options1.getParams(), options2.getParams());
    assertNotSame(options1.getParams(), options2.getParams());
    assertStringMapsEqualIncludingOrder(options1.getStringParams(), options2.getStringParams());
    assertNotSame(options1.getStringParams(), options2.getStringParams());
    assertByteArrayParametersEqualIncludingOrder(
        options1.getByteArrayParams(), options2.getByteArrayParams());
    assertNotSame(options1.getByteArrayParams(), options2.getByteArrayParams());
    assertTrue(Arrays.equals(options1.getPayload(), options2.getPayload()));
    assertNotSame(options1.getPayload(), options2.getPayload());
    assertEquals(options1.getTaskName(), options2.getTaskName());
    assertEquals(options1.getUrl(), options2.getUrl());
    assertEquals(options1.getRetryOptions(), options2.getRetryOptions());
    assertNotSame(options1.getRetryOptions(), options2.getRetryOptions());
    assertTrue(Arrays.equals(options1.getTagAsBytes(), options2.getTagAsBytes()));
    assertNotSame(options1.getTagAsBytes(), options2.getTagAsBytes());
    assertEquals(options1.hashCode(), options2.hashCode());
  }

  public void testAddHeader() throws Exception {
    TaskOptions options = withHeader("Animal", "Bat");
    options.header("Part", "Wings");
    options.header("Part", "Teeth");
    options.header("Food", "Bugs");

    assertEquals(Lists.newArrayList("Wings", "Teeth"), options.getHeaders().get("Part"));
    assertEquals(Lists.newArrayList("Bugs"), options.getHeaders().get("Food"));
  }

  public void testRemoveHeader() throws Exception {
    TaskOptions options = withHeader("Animal", "Bat");
    options.header("Part", "Wings");
    options.header("Part", "Teeth");
    options.header("Food", "Bugs");

    options.removeHeader("Part");

    assertEquals(null, options.getHeaders().get("Part"));
  }

  public void testGetHeaders() throws Exception {
    TaskOptions options = withHeader("Animal", "Bat");
    Map<String, List<String>> headersA = options.getHeaders();

    options.header("Part", "Wings");
    options.header("Part", "Teeth");
    options.header("Food", "Bugs");
    Map<String, List<String>> headersB = options.getHeaders();

    options.removeHeader("Part");
    Map<String, List<String>> headersC = options.getHeaders();

    // Check that snapshots of getHeaders() have not changed since they were created.
    assertStringMapsEqualIncludingOrder(ImmutableMap.of("Animal", Arrays.asList("Bat")), headersA);
    assertStringMapsEqualIncludingOrder(
        ImmutableMap.of(
            "Animal", Arrays.asList("Bat"),
            "Part", Arrays.asList("Wings", "Teeth"),
            "Food", Arrays.asList("Bugs")),
        headersB);

    // Check that mutating the returned object does not propagate back inside TaskOptions.
    options.getHeaders().put("Color", Arrays.asList("Brown"));
    options.getHeaders().get("Animal").add(0, "Koala");
    assertStringMapsEqualIncludingOrder(headersC, options.getHeaders());
  }

  public void testAddAndClearParams() throws Exception {
    TaskOptions options = withParam("Animal", "Bat");
    options.param("Part", "Wings");
    options.param("Part", "Teeth");
    options.param("Part", "Fur".getBytes());
    options.param("Food", "Bugs".getBytes());
    options.param("Food", "Fruit".getBytes());

    assertThat(options.getParams())
        .containsExactly(
            new TaskOptions.StringValueParam("Animal", "Bat"),
            new TaskOptions.StringValueParam("Part", "Wings"),
            new TaskOptions.StringValueParam("Part", "Teeth"),
            new TaskOptions.ByteArrayValueParam("Part", "Fur".getBytes()),
            new TaskOptions.ByteArrayValueParam("Food", "Bugs".getBytes()),
            new TaskOptions.ByteArrayValueParam("Food", "Fruit".getBytes()))
        .inOrder();

    assertStringMapsEqualIncludingOrder(
        ImmutableMap.of(
            "Animal", Arrays.asList("Bat"),
            "Part", Arrays.asList("Wings", "Teeth")),
        options.getStringParams());

    assertByteArrayParametersEqualIncludingOrder(
        ImmutableMap.of(
            "Part", Collections.singletonList("Fur".getBytes()),
            "Food", Arrays.asList("Bugs".getBytes(), "Fruit".getBytes())),
        options.getByteArrayParams());

    options.clearParams();
    assertEquals(Lists.<TaskOptions.Param>newArrayList(), options.getParams());
    assertThat(options.getStringParams()).isEmpty();
    assertThat(options.getByteArrayParams()).isEmpty();
  }

  public void testRemoveParam() throws Exception {
    TaskOptions options = withParam("Animal", "Bat");

    options.param("Part", "Wings");
    options.param("Part", "Teeth");
    options.param("Food", "Bugs");

    assertEquals(
        Lists.<TaskOptions.Param>newArrayList(
            new TaskOptions.StringValueParam("Animal", "Bat"),
            new TaskOptions.StringValueParam("Food", "Bugs")),
        options.removeParam("Part").getParams());

    assertEquals(
        Lists.<TaskOptions.Param>newArrayList(new TaskOptions.StringValueParam("Food", "Bugs")),
        options.removeParam("...Animal...".substring(3, 9)).getParams());
  }

  public void testGetStringParams() throws Exception {
    TaskOptions options = withParam("Animal", "Bat");
    Map<String, List<String>> paramsA = options.getStringParams();

    options.param("Part", "Wings");
    options.param("Part", "Teeth");
    options.param("Food", "Bugs");
    Map<String, List<String>> paramsB = options.getStringParams();

    options.removeParam("Part");
    Map<String, List<String>> paramsC = options.getStringParams();

    // Check that snapshots of getStringParams() have not changed since they were created.
    assertStringMapsEqualIncludingOrder(ImmutableMap.of("Animal", Arrays.asList("Bat")), paramsA);
    assertStringMapsEqualIncludingOrder(
        ImmutableMap.of(
            "Animal", Arrays.asList("Bat"),
            "Part", Arrays.asList("Wings", "Teeth"),
            "Food", Arrays.asList("Bugs")),
        paramsB);

    // Check that mutating the returned object does not propagate back inside TaskOptions.
    options.getStringParams().put("Color", Arrays.asList("Brown"));
    options.getStringParams().get("Animal").add(0, "Koala");
    assertStringMapsEqualIncludingOrder(paramsC, options.getStringParams());
  }

  public void testGetByteArrayParams() throws Exception {
    TaskOptions options = withParam("Animal", "Bat".getBytes());
    Map<String, List<byte[]>> paramsA = options.getByteArrayParams();

    options.param("Part", "Wings".getBytes());
    options.param("Animal", "Bird".getBytes());
    Map<String, List<byte[]>> paramsB = options.getByteArrayParams();

    options.removeParam("Part");
    Map<String, List<byte[]>> paramsC = options.getByteArrayParams();

    // Check that snapshots of getByteArrayParams() have not changed since they were created.
    assertByteArrayParametersEqualIncludingOrder(
        ImmutableMap.of("Animal", Collections.singletonList("Bat".getBytes())), paramsA);
    assertByteArrayParametersEqualIncludingOrder(
        ImmutableMap.of(
            "Animal", Arrays.asList("Bat".getBytes(), "Bird".getBytes()),
            "Part", Collections.singletonList("Wings".getBytes())),
        paramsB);

    // Check that mutating the returned object does not propagate back inside TaskOptions.
    options.getByteArrayParams().get("Animal").get(0)[0] = 0x00;
    options.getByteArrayParams().get("Animal").add(0, "Koala".getBytes());
    options.getByteArrayParams().put("Color", Collections.singletonList("Brown".getBytes()));
    assertByteArrayParametersEqualIncludingOrder(paramsC, options.getByteArrayParams());
  }

  public void testStringValueParamEquals() throws Exception {
    assertTrue(
        new TaskOptions.StringValueParam("Hello", "World")
            .equals(new TaskOptions.StringValueParam("Hello", "World")));

    assertFalse(
        new TaskOptions.StringValueParam("Hello", "World")
            .equals(new TaskOptions.StringValueParam("Hello!", "World")));
    assertFalse(
        new TaskOptions.StringValueParam("Hello", "World!")
            .equals(new TaskOptions.StringValueParam("Hello", "World")));
    assertFalse(
        new TaskOptions.StringValueParam("Hello", "World!")
            .equals(new TaskOptions.ByteArrayValueParam("Hello", "World".getBytes(UTF_8))));
  }

  public void testByteArrayValueParamEquals() throws Exception {
    assertTrue(
        new TaskOptions.ByteArrayValueParam("Hello", "World".getBytes(UTF_8))
            .equals(new TaskOptions.ByteArrayValueParam("Hello", "World".getBytes(UTF_8))));

    assertFalse(
        new TaskOptions.ByteArrayValueParam("Hello", "World".getBytes(UTF_8))
            .equals(new TaskOptions.ByteArrayValueParam("Hello!", "World".getBytes(UTF_8))));
    assertFalse(
        new TaskOptions.ByteArrayValueParam("Hello", "World".getBytes(UTF_8))
            .equals(new TaskOptions.ByteArrayValueParam("Hello", "World!".getBytes(UTF_8))));
    assertFalse(
        new TaskOptions.ByteArrayValueParam("Hello", "World".getBytes(UTF_8))
            .equals(new TaskOptions.StringValueParam("Hello", "World")));
  }

  public void testParamGetURLEncodedName() throws Exception {
    assertEquals("Hello", new TaskOptions.StringValueParam("Hello", "").getURLEncodedName());
    assertEquals(
        "Hello+World", new TaskOptions.StringValueParam("Hello World", "").getURLEncodedName());
  }

  public void testStringValueParamGetURLEncodedValue() throws Exception {
    assertEquals("World", new TaskOptions.StringValueParam("Hello", "World").getURLEncodedValue());
    assertEquals(
        "Hello+World", new TaskOptions.StringValueParam("Hi", "Hello World").getURLEncodedValue());
  }

  public void testByteArrayValueParamGetURLEncodedValue() throws Exception {
    assertEquals(
        "%57%6F%72%6C%64%00",
        new TaskOptions.ByteArrayValueParam("H", "World\0".getBytes(UTF_8)).getURLEncodedValue());
  }

  public void testParamBuilder() {
    assertEquals(
        Lists.<TaskOptions.Param>newArrayList(new TaskOptions.StringValueParam("Animal", "Bat")),
        withParam("Animal", "Bat").getParams());

    assertEquals(
        Lists.<TaskOptions.Param>newArrayList(
            new TaskOptions.ByteArrayValueParam("Letter", new byte[] {'B'})),
        withParam("Letter", new byte[] {'B'}).getParams());
  }

  public void testEquals() {
    RetryOptions retry = withTaskRetryLimit(10).taskAgeLimitSeconds(86400);
    TaskOptions first =
        withTaskName("someTask")
            .payload("Hello".getBytes(UTF_8))
            .header("Name", "Value")
            .method(TaskOptions.Method.POST)
            .param("PName", "PValue")
            .url("some/path")
            .countdownMillis(1234)
            .etaMillis(4321)
            .retryOptions(retry);
    TaskOptions second =
        withTaskName("someTask")
            .payload("Hello".getBytes(UTF_8))
            .header("Name", "Value")
            .method(TaskOptions.Method.POST)
            .param("PName", "PValue")
            .url("some/path")
            .countdownMillis(1234)
            .etaMillis(4321)
            .retryOptions(retry);

    assertEquals(first, second);
    assertEquals(second, first);

    TaskOptions third =
        withTaskName("someTask")
            .header("Name", "Value")
            .method(TaskOptions.Method.POST)
            .url("some/path");
    assertFalse(first.equals(third));
  }

  public void testToString() {
    // We don't test the actual content of the string, since the API doesn't really
    // define a contract about what it should be, and we don't want to make the tests that
    // fragile. But we'll make sure toString() doesn't npe in the presence of absent properties.
    withTaskName("someTask").toString();
    withMethod(TaskOptions.Method.POST).toString();

    // We also test that it contains parameter values (hopefully something like
    // params=[StringParam(foo=bar)] rather than
    // params=[com.google.appengine.api.taskqueue.TaskOptions$StringValueParam@609a998]).
    assertThat(withParam("foo", "bar").toString()).matches(".*foo=bar.*");
  }

  public void testDeferredTask() throws IOException, ClassNotFoundException {
    final TaskOptions opts = withPayload(DEFERRED_TASK_1);
    assertEquals(TaskOptions.Method.POST, opts.getMethod());
    assertEquals(DeferredTaskContext.DEFAULT_DEFERRED_URL, opts.getUrl());
    assertEquals(
        DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE,
        opts.getHeaders().get("content-type").iterator().next());

    ObjectInputStream objectStream =
        new ObjectInputStream(new ByteArrayInputStream(opts.getPayload()));
    DeferredTask deferredTask = (DeferredTask) objectStream.readObject();
    assertEquals(DEFERRED_TASK_1, deferredTask);
  }

  public void testDispatchDeadlineExceedsLowerLimit() throws Exception {
    TaskOptions options = withUrl("/A/B");
    Duration tooSoon = Duration.ofSeconds(10);
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> options.dispatchDeadline(tooSoon));
    assertThat(thrown)
        .hasMessageThat()
        .contains(String.format("Dispatch deadline %s must be at least", tooSoon));
  }

  public void testDispatchDeadlineExceedsUpperLimit() {
    TaskOptions options = withUrl("/A/B");
    Duration tooLate = Duration.ofHours(30);
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> options.dispatchDeadline(tooLate));
    assertThat(thrown)
        .hasMessageThat()
        .contains(String.format("Dispatch deadline %s must be at most", tooLate));
  }

  private static void assertStringMapsEqualIncludingOrder(
      Map<String, ? extends List<String>> expected, Map<String, ? extends List<String>> actual) {
    assertThat(actual.entrySet()).containsExactlyElementsIn(expected.entrySet()).inOrder();
  }

  private static void assertByteArrayParametersEqualIncludingOrder(
      Map<String, ? extends List<byte[]>> expected, Map<String, ? extends List<byte[]>> actual) {
    assertThat(actual.keySet()).containsExactlyElementsIn(expected.keySet()).inOrder();
    for (Map.Entry<String, ? extends List<byte[]>> actualEntry : actual.entrySet()) {
      List<byte[]> actualValues = actualEntry.getValue();
      List<byte[]> expectedValues = expected.get(actualEntry.getKey());
      assertThat(actualValues.size()).isEqualTo(expectedValues.size());
      for (int i = 0; i < actualValues.size(); i++) {
        assertThat(actualValues.get(i)).isEqualTo(expectedValues.get(i));
      }
    }
  }
}
