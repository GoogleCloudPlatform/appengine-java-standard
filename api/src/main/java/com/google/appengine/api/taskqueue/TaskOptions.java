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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.taskqueue.TaskQueuePb.TaskQueueAddRequest.RequestMethod;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Contains various options for a task following the builder pattern. Calls to {@link TaskOptions}
 * methods may be chained to specify multiple options in the one {@link TaskOptions} object.
 *
 * <p>taskOptions can have either {@link TaskOptions.Method} PULL or a PUSH-related method, e.g.
 * POST, GET, ... Tasks with PULL method can only be added into a PULL queue and PUSH tasks can only
 * be added into a PUSH queue.
 *
 * <p>Notes on usage:<br>
 * The recommended way to instantiate a {@link TaskOptions} object is to statically import {@link
 * Builder}.* and invoke a static creation method followed by an instance mutator (if needed):
 *
 * <pre>{@code
 * import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;
 *
 * ...
 * {@link QueueFactory#getDefaultQueue()}.add(withUrl(url).etaMillis(eta));
 * }</pre>
 *
 */
public final class TaskOptions implements Serializable {
  private static final long serialVersionUID = -2702019046191004750L;

  // The limits on dispatch deadline are consistent with Cloud Tasks API.
  // https://cloud.google.com/tasks/docs/reference/rpc/google.cloud.tasks.v2
  private static final Duration MAX_DISPATCH_DEADLINE = Duration.ofHours(24);
  private static final Duration MIN_DISPATCH_DEADLINE = Duration.ofSeconds(15);

  /**
   * Methods supported by {@link Queue}. Tasks added to pull queues must have the {@link #Method}
   * PULL. All other methods are appropriate for push queues. See {@link Queue} for more detail on
   * pull and push mode queues.
   */
  public enum Method {
    DELETE(RequestMethod.DELETE, false),
    GET(RequestMethod.GET, false),
    HEAD(RequestMethod.HEAD, false),
    POST(RequestMethod.POST, true),
    PUT(RequestMethod.PUT, true),
    // Sets PbMethod to null for PULL Method, since there's no corresponding
    // PbMethod for such Method. Tasks with PULL method will be translated into
    // an add request with pull mode.
    PULL(null, true);

    private final RequestMethod pbMethod;
    private final boolean isBodyMethod;

    Method(RequestMethod pbMethod, boolean isBodyMethod) {
      this.pbMethod = pbMethod;
      this.isBodyMethod = isBodyMethod;
    }

    RequestMethod getPbMethod() {
      return pbMethod;
    }

    boolean supportsBody() {
      return isBodyMethod;
    }
  }

  /**
   * Params are currently immutable and need to remain that way to avoid the need to clone them in
   * the TaskOptions copy constructor.
   */
  abstract static class Param implements Serializable {
    private static final long serialVersionUID = -4677920038530554173L;

    final String name;

    Param(String name) {
      if (name == null || name.length() == 0) {
        throw new IllegalArgumentException("name must not be null or empty");
      }
      this.name = name;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    static String encodeURLAsUtf8(String url) throws UnsupportedEncodingException {
      return URLEncoder.encode(url, "UTF-8");
    }

    // Force concrete subclasses to implement equals. This is required because this class defines
    // hashCode().
    @Override
    public abstract boolean equals(Object o);

    String getURLEncodedName() throws UnsupportedEncodingException {
      return encodeURLAsUtf8(name);
    }

    abstract String getURLEncodedValue() throws UnsupportedEncodingException;
  }

  static class StringValueParam extends Param {
    private static final long serialVersionUID = -2306561754387422446L;

    final String value;

    StringValueParam(String name, String value) {
      super(name);

      if (value == null) {
        throw new IllegalArgumentException("value must not be null");
      }
      this.value = value;
    }

    @Override
    public String toString() {
      return "StringParam(" + name + "=" + value + ")";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof StringValueParam)) {
        return false;
      }

      StringValueParam that = (StringValueParam) o;

      return value.equals(that.value) && name.equals(that.name);
    }

    @Override
    public String getURLEncodedValue() throws UnsupportedEncodingException {
      return encodeURLAsUtf8(value);
    }
  }

  static class ByteArrayValueParam extends Param {
    private static final long serialVersionUID = 1420600427192025644L;

    protected final byte[] value;

    ByteArrayValueParam(String name, byte[] value) {
      super(name);

      if (value == null) {
        throw new IllegalArgumentException("value must not be null");
      }
      this.value = value;
    }

    @Override
    public String toString() {
      return "ByteArrayParam(" + name + ": " + value.length + " bytes)";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof ByteArrayValueParam)) {
        return false;
      }

      ByteArrayValueParam that = (ByteArrayValueParam) o;

      return Arrays.equals(value, that.value) && name.equals(that.name);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnsupportedEncodingException
     */
    @Override
    public String getURLEncodedValue() throws UnsupportedEncodingException {
      // See RFC 2396, Section 2.4.1. for a description of escape encoding.
      // See RFC 2616, Section 3.2.3. for a description of the semantic equivalence of escape and
      // non-escape encoded characters.
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < value.length; i++) {
        // TODO - could optimize this to produce smaller payload.
        result.append("%");
        char character = Character.toUpperCase(Character.forDigit((value[i] >> 4) & 0xF, 16));
        result.append(character);
        character = Character.toUpperCase(Character.forDigit(value[i] & 0xF, 16));
        result.append(character);
      }
      return result.toString();
    }
  }

  private String taskName;
  private byte[] payload;
  private HashMap<String, List<String>> headers;
  private Method method;
  private List<Param> params;
  private String url;
  private Long countdownMillis;
  private Long etaMillis;
  private RetryOptions retryOptions;
  private byte[] tag;
  private @Nullable Duration dispatchDeadline;

  private TaskOptions() {
    this.method = Method.POST;
    this.headers = new LinkedHashMap<String, List<String>>();
    this.params = new ArrayList<Param>();
  }

  /** A copy constructor for {@link TaskOptions}. */
  public TaskOptions(TaskOptions options) {
    taskName = options.taskName;
    method = options.method;
    url = options.url;
    countdownMillis = options.countdownMillis;
    etaMillis = options.etaMillis;
    tag(options.tag);
    this.dispatchDeadline = options.dispatchDeadline;
    if (options.retryOptions != null) {
      retryOptions = new RetryOptions(options.retryOptions);
    } else {
      retryOptions = null;
    }
    if (options.getPayload() != null) {
      payload(options.getPayload());
    } else {
      payload = null;
    }
    this.headers = copyOfHeaders(options.getHeaders());
    this.params = new ArrayList<>(options.getParams());
  }

  private static LinkedHashMap<String, List<String>> copyOfHeaders(
      Map<String, List<String>> headers) {
    LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      copy.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
    }
    return copy;
  }

  /**
   * Sets the task name.
   *
   * @throws IllegalArgumentException The provided name is null, empty or doesn't match the regular
   *     expression {@link QueueConstants#TASK_NAME_REGEX}
   */
  public TaskOptions taskName(String taskName) {
    if (taskName != null && taskName.length() != 0) {
      TaskHandle.validateTaskName(taskName);
    }
    this.taskName = taskName;
    return this;
  }

  /** Returns the task name. */
  public String getTaskName() {
    return taskName;
  }

  /** Returns the live payload for the task, not a copy. May be {@code null}. */
  public byte[] getPayload() {
    return payload;
  }

  /**
   * Sets the payload directly without specifying the content-type. If this task is added to a push
   * queue, the content-type will be set to 'application/octet-stream' by default.
   *
   * @param payload The bytes representing the paylaod.
   * @return TaskOptions object for chaining.
   */
  public TaskOptions payload(byte[] payload) {
    // A header for content type set to "application/octet-stream" will be added in
    // taskqueue_service.cc for push tasks if content type is not set in API.
    this.payload = payload.clone();
    return this;
  }

  /**
   * Sets the payload to the serialized form of the deferredTask object. The payload will be
   * generated by serializing the deferredTask object using {@link
   * ObjectOutputStream#writeObject(Object)}. If the deferredTask's {@link Method} is not PULL, the
   * content type will be set to {@link DeferredTaskContext#RUNNABLE_TASK_CONTENT_TYPE}, the method
   * will be forced to {@link Method#POST} and if otherwise not specified, the url will be set to
   * {@link DeferredTaskContext#DEFAULT_DEFERRED_URL}; the {@link DeferredTask} servlet is, by
   * default, mapped to this url.
   *
   * <p>Note: While this may be a convenient API, it requires careful control of the serialization
   * compatibility of objects passed into {@link #payload(DeferredTask)} method as objects placed in
   * the task queue will survive revision updates of the application and hence may fail
   * deserialization when the task is decoded with new revisions of the application. In particular,
   * Java anonymous classes are convenient but may be particularly difficult to control or test for
   * serialization compatibility.
   *
   * @param deferredTask The object to serialize into the payload.
   * @throws DeferredTaskCreationException if there was an IOException serializing object.
   */
  public TaskOptions payload(DeferredTask deferredTask) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream(1024);
    ObjectOutputStream objectStream = null;
    try {
      objectStream = new ObjectOutputStream(stream);
      objectStream.writeObject(deferredTask);
    } catch (IOException e) {
      throw new DeferredTaskCreationException(e);
    }
    payload = stream.toByteArray();
    // Don't add header or url for pull tasks.
    if (getMethod() != Method.PULL) {
      header("content-type", DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE);
      method(Method.POST);
      if (getUrl() == null) {
        url(DeferredTaskContext.DEFAULT_DEFERRED_URL);
      }
    }
    return this;
  }

  /**
   * Sets the payload from a {@link String} given a specific character set.
   *
   * @throws UnsupportedTranslationException
   */
  public TaskOptions payload(String payload, String charset) {
    try {
      return payload(payload.getBytes(charset), "text/plain; charset=" + charset);
    } catch (UnsupportedEncodingException exception) {
      throw new UnsupportedTranslationException(
          "Unsupported charset '" + charset + "' requested.", exception);
    }
  }

  /**
   * Set the payload with the given content type.
   *
   * @param payload The bytes representing the paylaod.
   * @param contentType The content-type of the bytes.
   * @return TaskOptions object for chaining.
   */
  public TaskOptions payload(byte[] payload, String contentType) {
    return payload(payload).header("content-type", contentType);
  }

  /**
   * Set the payload by {@link String}. The charset to convert the String to will be UTF-8 unless
   * the method is PULL, in which case the String's bytes will be used directly.
   *
   * @param payload The String to be used.
   * @return TaskOptions object for chaining.
   */
  public TaskOptions payload(String payload) {
    if (getMethod() == Method.PULL) {
      return payload(payload.getBytes(Charset.defaultCharset()));
    }
    return payload(payload, "UTF-8");
  }

  /**
   * Returns a copy of the task's headers as a map from each header name to a list of values for
   * that header name.
   */
  public Map<String, List<String>> getHeaders() {
    return copyOfHeaders(headers);
  }

  /**
   * Replaces the existing headers with the provided header {@code name/value} mapping.
   *
   * @param headers The headers to copy.
   * @return TaskOptions object for chaining.
   */
  public TaskOptions headers(Map<String, String> headers) {
    this.headers = new LinkedHashMap<String, List<String>>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      List<String> values = new ArrayList<>(1);
      values.add(entry.getValue());
      this.headers.put(entry.getKey(), values);
    }
    return this;
  }

  /**
   * Adds a header {@code name/value} pair.
   *
   * @throws IllegalArgumentException
   */
  public TaskOptions header(String headerName, String value) {
    if (headerName == null || headerName.length() == 0) {
      throw new IllegalArgumentException("headerName must not be null or empty.");
    }
    if (value == null) {
      throw new IllegalArgumentException("header(name, <null>) is not allowed.");
    }

    headers.computeIfAbsent(headerName, unused -> new ArrayList<>()).add(value);
    return this;
  }

  /** Remove all headers with the given name. */
  public TaskOptions removeHeader(String headerName) {
    if (headerName == null || headerName.length() == 0) {
      throw new IllegalArgumentException("headerName must not be null or empty.");
    }
    headers.remove(headerName);
    return this;
  }

  /** Returns the method used for this task. */
  public Method getMethod() {
    return method;
  }

  /** Set the method used for this task. Defaults to {@link Method#POST}. */
  public TaskOptions method(Method method) {
    this.method = method;
    return this;
  }

  /**
   * Returns a copy of the task's string-valued parameters as a map from each parameter name to a
   * list of values for that name.
   */
  public Map<String, List<String>> getStringParams() {
    LinkedHashMap<String, List<String>> stringParams = new LinkedHashMap<>();
    for (Param param : params) {
      if (param instanceof StringValueParam) {
        if (!stringParams.containsKey(param.name)) {
          stringParams.put(param.name, new ArrayList<String>());
        }
        stringParams.get(param.name).add(((StringValueParam) param).value);
      }
    }
    return stringParams;
  }

  /**
   * Returns a copy of the task's byte-array-valued parameters as a map from each parameter name to
   * a list of values for that name.
   */
  public Map<String, List<byte[]>> getByteArrayParams() {
    LinkedHashMap<String, List<byte[]>> byteArrayParams = new LinkedHashMap<>();
    for (Param param : params) {
      if (param instanceof ByteArrayValueParam) {
        if (!byteArrayParams.containsKey(param.name)) {
          byteArrayParams.put(param.name, new ArrayList<byte[]>());
        }
        byte[] value = ((ByteArrayValueParam) param).value;
        byteArrayParams.get(param.name).add(Arrays.copyOf(value, value.length));
      }
    }
    return byteArrayParams;
  }

  List<Param> getParams() {
    return params;
  }

  TaskOptions param(Param param) {
    params.add(param);
    return this;
  }

  /** Clears the parameters. */
  public TaskOptions clearParams() {
    params.clear();
    return this;
  }

  /**
   * Add a named {@link String} parameter.
   *
   * @param name Name of the parameter. Must not be null or empty.
   * @param value The value of the parameter will undergo a "UTF-8" character encoding
   *     transformation upon being added to the queue. {@code value} must not be {@code null}.
   * @throws IllegalArgumentException
   */
  public TaskOptions param(String name, String value) {
    return param(new StringValueParam(name, value));
  }

  /**
   * Add a named {@code byte} array parameter.
   *
   * @param name Name of the parameter. Must not be null or empty.
   * @param value A byte array and encoded as-is (i.e. without character encoding transformations).
   *     {@code value} must not be {@code null}.
   * @throws IllegalArgumentException
   */
  public TaskOptions param(String name, byte[] value) {
    return param(new ByteArrayValueParam(name, value));
  }

  /**
   * Remove all parameters with the given name.
   *
   * @param paramName Name of the parameter. Must not be null or empty.
   */
  public TaskOptions removeParam(String paramName) {
    if (paramName == null || paramName.length() == 0) {
      throw new IllegalArgumentException("paramName must not be null or empty.");
    }

    Iterator<Param> paramsIter = params.iterator();
    while (paramsIter.hasNext()) {
      if (paramName.equals(paramsIter.next().name)) {
        paramsIter.remove();
      }
    }

    return this;
  }

  public String getUrl() {
    return url;
  }

  /**
   * Set the URL.
   *
   * <p>Default value is {@code null}.
   *
   * @param url String containing URL.
   */
  public TaskOptions url(String url) {
    if (url == null) {
      throw new IllegalArgumentException("null url is not allowed.");
    }
    this.url = url;
    return this;
  }

  /** Returns the delay to apply to the submitted time. May be {@code null}. */
  public Long getCountdownMillis() {
    return countdownMillis;
  }

  /** Set the number of milliseconds delay before execution of the task. */
  public TaskOptions countdownMillis(long countdownMillis) {
    this.countdownMillis = countdownMillis;
    return this;
  }

  /** Returns the specified ETA for a task. May be {@code null} if not specified. */
  public Long getEtaMillis() {
    return etaMillis;
  }

  /**
   * Sets the approximate absolute time to execute. (i.e. etaMillis is comparable with {@link
   * System#currentTimeMillis()}).
   */
  public TaskOptions etaMillis(long etaMillis) {
    this.etaMillis = etaMillis;
    return this;
  }

  /** Returns a copy of the retry options for a task. May be {@code null} if not specified. */
  public RetryOptions getRetryOptions() {
    return retryOptions == null ? null : new RetryOptions(retryOptions);
  }

  /**
   * Sets retry options for this task. Retry Options must be built with {@code
   * RetryOptions.Builder}.
   */
  public TaskOptions retryOptions(RetryOptions retryOptions) {
    this.retryOptions = retryOptions;
    return this;
  }

  /**
   * Returns the live tag bytes for a task, not a copy. May be {@code null} if tag is not specified.
   */
  public byte[] getTagAsBytes() {
    return tag;
  }

  /**
   * Returns the tag for a task. May be {@code null} if tag is not specified.
   *
   * @throws UnsupportedEncodingException
   */
  public String getTag() throws UnsupportedEncodingException {
    if (tag != null) {
      return new String(tag, UTF_8);
    }
    return null;
  }

  /**
   * Returns the dispatch deadline for a task. The dispatch deadline determines how long the of a
   * task should take. If a task exceeds its deadline, it will be canceled and retried based on
   * retry configurations of the task and/or queue.
   */
  public @Nullable Duration getDispatchDeadline() {
    return dispatchDeadline;
  }

  /** Sets the tag for a task. Ignores null or zero-length tags. */
  public TaskOptions tag(byte[] tag) {
    if (tag != null && tag.length > 0) {
      this.tag = tag.clone();
    } else {
      this.tag = null;
    }
    return this;
  }

  /** Sets the tag for a task. Ignores null or empty tags. */
  public TaskOptions tag(String tag) {
    if (tag != null) {
      return tag(tag.getBytes(UTF_8));
    }
    return this;
  }

  /**
   * Sets the dispatch deadline for a task. The dispatch deadline should be no smaller than
   * TaskOptions#MIN_DISPATCH_DEADLINE and no larger than TaskOptions#MAX_DISPATCH_DEADLINE.
   */
  public TaskOptions dispatchDeadline(Duration dispatchDeadline) {
    checkNotNull(dispatchDeadline);
    checkArgument(
        dispatchDeadline.compareTo(MIN_DISPATCH_DEADLINE) >= 0,
        "Dispatch deadline %s must be at least %s.",
        dispatchDeadline,
        MIN_DISPATCH_DEADLINE);
    checkArgument(
        dispatchDeadline.compareTo(MAX_DISPATCH_DEADLINE) <= 0,
        "Dispatch deadline %s must be at most %s.",
        dispatchDeadline,
        MAX_DISPATCH_DEADLINE);
    this.dispatchDeadline = dispatchDeadline;
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((countdownMillis == null) ? 0 : countdownMillis.hashCode());
    result = prime * result + ((etaMillis == null) ? 0 : etaMillis.hashCode());
    result = prime * result + ((headers == null) ? 0 : headers.hashCode());
    result = prime * result + ((method == null) ? 0 : method.hashCode());
    result = prime * result + ((params == null) ? 0 : params.hashCode());
    result = prime * result + Arrays.hashCode(payload);
    result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
    result = prime * result + ((url == null) ? 0 : url.hashCode());
    result = prime * result + ((retryOptions == null) ? 0 : retryOptions.hashCode());
    result = prime * result + Arrays.hashCode(tag);
    result = prime * result + ((dispatchDeadline == null) ? 0 : dispatchDeadline.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TaskOptions other = (TaskOptions) obj;
    if (countdownMillis == null) {
      if (other.countdownMillis != null) {
        return false;
      }
    } else if (!countdownMillis.equals(other.countdownMillis)) {
      return false;
    }
    if (etaMillis == null) {
      if (other.etaMillis != null) {
        return false;
      }
    } else if (!etaMillis.equals(other.etaMillis)) {
      return false;
    }
    if (headers == null) {
      if (other.headers != null) {
        return false;
      }
    } else if (!headers.equals(other.headers)) {
      return false;
    }
    if (method == null) {
      if (other.method != null) {
        return false;
      }
    } else if (!method.equals(other.method)) {
      return false;
    }
    if (params == null) {
      if (other.params != null) {
        return false;
      }
    } else if (!params.equals(other.params)) {
      return false;
    }
    if (!Arrays.equals(payload, other.payload)) {
      return false;
    }
    if (taskName == null) {
      if (other.taskName != null) {
        return false;
      }
    } else if (!taskName.equals(other.taskName)) {
      return false;
    }
    if (url == null) {
      if (other.url != null) {
        return false;
      }
    } else if (!url.equals(other.url)) {
      return false;
    }
    if (retryOptions == null) {
      if (other.retryOptions != null) {
        return false;
      }
    } else if (!retryOptions.equals(other.retryOptions)) {
      return false;
    }
    if (!Arrays.equals(tag, other.tag)) {
      return false;
    }
    return Objects.equals(dispatchDeadline, other.dispatchDeadline);
  }

  @Override
  public String toString() {
    String tagString = null;
    try {
      tagString = getTag();
    } catch (UnsupportedEncodingException e) {
      tagString = "not a utf-8 String";
    }
    return "TaskOptions[taskName="
        + taskName
        + ", headers="
        + headers
        + ", method="
        + method
        + ", params="
        + params
        + ", url="
        + url
        + ", countdownMillis="
        + countdownMillis
        + ", etaMillis="
        + etaMillis
        + ", retryOptions="
        + retryOptions
        + ", tag="
        + tagString
        + ", dispatchDeadline="
        + dispatchDeadline
        + "]";
  }

  /** Provides static creation methods for {@link TaskOptions}. */
  public static final class Builder {
    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#taskName(String)}. */
    public static TaskOptions withTaskName(String taskName) {
      return withDefaults().taskName(taskName);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#payload(byte[])}. */
    static TaskOptions withPayload(byte[] payload) {
      return withDefaults().payload(payload);
    }

    /**
     * Returns default {@link TaskOptions} and calls {@link TaskOptions#payload(String, String)}.
     */
    public static TaskOptions withPayload(String payload, String charset) {
      return withDefaults().payload(payload, charset);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#payload(DeferredTask)}. */
    public static TaskOptions withPayload(DeferredTask deferredTask) {
      return withDefaults().payload(deferredTask);
    }

    /**
     * Returns default {@link TaskOptions} and calls {@link TaskOptions#payload(byte[], String)}.
     */
    public static TaskOptions withPayload(byte[] payload, String contentType) {
      return withDefaults().payload(payload, contentType);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#payload(String)}. */
    public static TaskOptions withPayload(String payload) {
      return withDefaults().payload(payload);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#headers(Map)}. */
    public static TaskOptions withHeaders(Map<String, String> headers) {
      return withDefaults().headers(headers);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#header(String, String)}. */
    public static TaskOptions withHeader(String headerName, String value) {
      return withDefaults().header(headerName, value);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#method(Method)}. */
    public static TaskOptions withMethod(Method method) {
      return withDefaults().method(method);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#param(String, String)}. */
    public static TaskOptions withParam(String paramName, String value) {
      return withDefaults().param(paramName, value);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#param(String, byte[])}. */
    public static TaskOptions withParam(String paramName, byte[] value) {
      return withDefaults().param(paramName, value);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#url(String)}. */
    public static TaskOptions withUrl(String url) {
      return withDefaults().url(url);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#countdownMillis(long)}. */
    public static TaskOptions withCountdownMillis(long countdownMillis) {
      return withDefaults().countdownMillis(countdownMillis);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#etaMillis(long)}. */
    public static TaskOptions withEtaMillis(long etaMillis) {
      return withDefaults().etaMillis(etaMillis);
    }

    /**
     * Returns default {@link TaskOptions} and calls {@link TaskOptions#retryOptions(RetryOptions)}.
     */
    public static TaskOptions withRetryOptions(RetryOptions retryOptions) {
      return withDefaults().retryOptions(retryOptions);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#tag(byte[])}. */
    public static TaskOptions withTag(byte[] tag) {
      return withDefaults().tag(tag);
    }

    /** Returns default {@link TaskOptions} and calls {@link TaskOptions#tag(String)}. */
    public static TaskOptions withTag(String tag) {
      return withDefaults().tag(tag);
    }

    /** Returns default {@link TaskOptions} with default values. */
    public static TaskOptions withDefaults() {
      return new TaskOptions();
    }

    private Builder() {}
  }
}
