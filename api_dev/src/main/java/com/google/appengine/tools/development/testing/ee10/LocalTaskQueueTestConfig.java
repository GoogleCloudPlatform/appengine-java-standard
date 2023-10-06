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

package com.google.appengine.tools.development.testing.ee10;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.DeferredTaskContext;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueue;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueueCallback;
import com.google.appengine.api.urlfetch.URLFetchServicePb;
import com.google.appengine.api.urlfetch.URLFetchServicePb.URLFetchRequest.Header;
import com.google.appengine.tools.development.ApiProxyLocal;
import com.google.appengine.tools.development.testing.EnvSettingTaskqueueCallback;
import com.google.appengine.tools.development.testing.LocalServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Config for accessing the local task queue in tests. Default behavior is to
 * configure the local task queue to not automatically execute any tasks.
 * {@link #tearDown()} wipes out all in-memory state so all queues are empty at
 * the end of every test. LocalTaskQueue configuration are not restored.
 * {@link #tearDown()} does not restore default configuration values modified
 * using:
 * <ul>
 * <li>{@link #setDisableAutoTaskExecution()}</li>
 * <li>{@link #setQueueXmlPath()}</li>
 * <li>{@link #setCallbackClass()}</li>
 * <li>{@link #setShouldCopyApiProxyEnvironment()}</li>
 * <li>{@link #setTaskExecutionLatch()}</li>
 * </ul>
 *
 */
public final class LocalTaskQueueTestConfig implements LocalServiceTestConfig {
  private static final Logger logger = Logger.getLogger(LocalTaskQueueTestConfig.class.getName());
  private Boolean disableAutoTaskExecution = true;
  private String queueXmlPath;
  private String queueYamlPath;
  private Class<? extends LocalTaskQueueCallback> callbackClass;
  private boolean shouldCopyApiProxyEnvironment = false;
  private CountDownLatch taskExecutionLatch;

  /**
   * Disables/enables automatic task execution. If you enable automatic task
   * execution, keep in mind that the default behavior is to hit the url that
   * was provided when the {@link TaskOptions} was constructed. If you do not
   * have a servlet engine running, this will fail. As an alternative to
   * launching a servlet engine, instead consider providing a
   * {@link LocalTaskQueueCallback} via {@link #setCallbackClass(Class)} so that
   * you can assert on the properties of the URLFetchServicePb.URLFetchRequest.
   *
   * Once set, this value is persistent across tests. If this value needs to be
   * set for any one test, it should be appropriately configured in the setup
   * stage for all tests.
   *
   * @param disableAutoTaskExecution
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setDisableAutoTaskExecution(boolean disableAutoTaskExecution) {
    this.disableAutoTaskExecution = disableAutoTaskExecution;
    return this;
  }

  /**
   * Overrides the location of queue.xml. Must be a full path, e.g.
   * /usr/local/dev/myapp/test/queue.xml
   *
   * Once set, this value is persistent across tests. If this value needs to be
   * set for an operation specific to any one test, it should appropriately
   * configured in the setup stage for all tests.
   *
   * @param queueXmlPath
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setQueueXmlPath(String queueXmlPath) {
    this.queueXmlPath = queueXmlPath;
    return this;
  }

  /**
   * Overrides the location of queue.yaml. Must be a full path, e.g.
   * /usr/local/dev/myapp/test/queue.yaml
   *
   * <p>Once set, this value is persistent across tests. If this value needs to be set for an
   * operation specific to any one test, it should appropriately configured in the setup stage for
   * all tests.
   *
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setQueueYamlPath(String queueYamlPath) {
    this.queueYamlPath = queueYamlPath;
    return this;
  }

  /**
   * Overrides the callback implementation used by the local task queue for
   * async task execution.
   *
   * Once set, this value is persistent across tests. If this value needs to be
   * set for any one test, it should be appropriately configured in the setup
   * stage for all tests.
   *
   * @param callbackClass fully-qualified name of a class with a public, default
   *        constructor that implements {@link LocalTaskQueueCallback}.
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setCallbackClass(
      Class<? extends LocalTaskQueueCallback> callbackClass) {
    this.callbackClass = callbackClass;
    return this;
  }

  /**
   * Enables copying of the {@code ApiProxy.Environment} to task handler
   * threads. This setting is ignored unless both
   * <ol>
   * <li>a {@link #setCallbackClass(Class) callback} class has been set, and
   * <li>automatic task execution has been
   * {@link #setDisableAutoTaskExecution(boolean) enabled.}
   * </ol>
   * In this case tasks will be handled locally by new threads and it may be
   * useful for those threads to use the same environment data as the main test
   * thread. Properties such as the
   * {@link LocalServiceTestHelper#setEnvAppId(String) appID}, and the user
   * {@link LocalServiceTestHelper#setEnvEmail(String) email} will be copied
   * into the environment of the task threads. Be aware that
   * {@link LocalServiceTestHelper#setEnvAttributes(java.util.Map) attribute
   * map} will be shallow-copied to the task thread environents, so that any
   * mutable objects used as values of the map should be thread safe. If this
   * property is {@code false} then the task handler threads will have an empty
   * {@code ApiProxy.Environment}. This property is {@code false} by default.
   *
   * Once set, this value is persistent across tests. If this value needs to be
   * set for any one test, it should be appropriately configured in the setup
   * stage for all tests.
   *
   * @param b should the {@code ApiProxy.Environment} be pushed to task handler
   *        threads
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setShouldCopyApiProxyEnvironment(boolean b) {
    this.shouldCopyApiProxyEnvironment = b;
    return this;
  }

  /**
   * Sets a {@link CountDownLatch} that the thread executing the task will
   * decrement after a {@link LocalTaskQueueCallback} finishes execution.  This
   * makes it easy for tests to block until a task queue task runs.  Note that
   * the latch is only used when a callback class is provided (via
   * {@link #setCallbackClass(Class)}) and when automatic task execution is
   * enabled (via {@link #setDisableAutoTaskExecution(boolean)}).  Also note
   * that a {@link CountDownLatch} cannot be reused, so if you have a test that
   * requires the ability to "reset" a CountDownLatch you can pass an instance
   * of {@link TaskCountDownLatch}, which exposes additional methods that help
   * with this.
   *
   * Once set, this value is persistent across tests. If this value needs to be
   * set for any one test, it should be appropriately configured in the setup
   * stage for all tests.
   *
   * @param latch The latch.
   * @return {@code this} (for chaining)
   */
  public LocalTaskQueueTestConfig setTaskExecutionLatch(CountDownLatch latch) {
    this.taskExecutionLatch = latch;
    return this;
  }

  @Override
  public void setUp() {
    ApiProxyLocal proxy = LocalServiceTestHelper.getApiProxyLocal();
    proxy.setProperty(
        LocalTaskQueue.DISABLE_AUTO_TASK_EXEC_PROP, disableAutoTaskExecution.toString());
    if (queueXmlPath != null) {
      proxy.setProperty(LocalTaskQueue.QUEUE_XML_PATH_PROP, queueXmlPath);
    }
    if (queueYamlPath != null) {
      proxy.setProperty(LocalTaskQueue.QUEUE_YAML_PATH_PROP, queueYamlPath);
    }
    if (callbackClass != null) {
      String callbackName;
      if (!disableAutoTaskExecution) {
        EnvSettingTaskqueueCallback.setProxyProperties(
            proxy, callbackClass, shouldCopyApiProxyEnvironment);
        if (taskExecutionLatch != null) {
          EnvSettingTaskqueueCallback.setTaskExecutionLatch(taskExecutionLatch);
        }
        callbackName = EnvSettingTaskqueueCallback.class.getName();
      } else {
        // Automatic task execution is disabled so the task is being executed
        // manually.
        callbackName = callbackClass.getName();
      }
      proxy.setProperty(LocalTaskQueue.CALLBACK_CLASS_PROP, callbackName);
    }
  }



  @Override
  public void tearDown() {
    LocalTaskQueue ltq = getLocalTaskQueue();
    if (ltq != null) {
      for (String queueName : ltq.getQueueStateInfo().keySet()) {
        ltq.flushQueue(queueName);
      }
      ltq.stop();
    }
  }

  public static LocalTaskQueue getLocalTaskQueue() {
    return (LocalTaskQueue) LocalServiceTestHelper.getLocalService(LocalTaskQueue.PACKAGE);
  }

  /**
   * A {@link LocalTaskQueueCallback} implementation that automatically detects
   * and runs tasks with a {@link DeferredTask} payload.
   *
   * Requests with a payload that is not a {@link DeferredTask} are dispatched
   * to {@link #executeNonDeferredRequest}, which by default does nothing.
   * If you need to handle a payload like this you can extend the class and
   * override this method to do what you need.
   */
  public static class DeferredTaskCallback implements LocalTaskQueueCallback {
    private static final String CURRENT_NAMESPACE_HEADER = "X-AppEngine-Current-Namespace";

    @Override
    public void initialize(Map<String, String> properties) {
    }

    @Override
    public int execute(URLFetchServicePb.URLFetchRequest req) {
      String currentNamespace = NamespaceManager.get();
      String requestNamespace = null;
      ByteString payload = null;
      for (URLFetchServicePb.URLFetchRequest.Header header : req.getHeaderList()) {
        // See if this is a DeferredTask.
        if (header.getKey().equals("content-type") &&
            DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE.equals(header.getValue())) {
          payload = req.getPayload();
        } else if (CURRENT_NAMESPACE_HEADER.equals(header.getKey())) {
          requestNamespace = header.getValue();
        }
      }
      boolean namespacesDiffer =
          requestNamespace != null && !requestNamespace.equals(currentNamespace);
      if (namespacesDiffer) {
        NamespaceManager.set(requestNamespace);
      }

      try {
        if (payload != null) {
          // It is a DeferredTask, so deserialize and run.
          ByteArrayInputStream bais = new ByteArrayInputStream(payload.toByteArray());
          ObjectInputStream ois;
          try {
            ois = new ObjectInputStream(bais);
            DeferredTask deferredTask = (DeferredTask) ois.readObject();
            deferredTask.run();
            return 200;
          } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            return 500;
          }
        }
        return executeNonDeferredRequest(req);
      } finally {
        if (namespacesDiffer) {
          NamespaceManager.set(currentNamespace);
        }
      }
    }

    /**
     * Broken out to make it easy for subclasses to provide their own behavior
     * when the request payload is not a {@link DeferredTask}.
     */
    protected int executeNonDeferredRequest(URLFetchServicePb.URLFetchRequest req) {
      return 200;
    }
  }


  /**
   * A class to delegate incoming task queue callbacks to HttpServlets based on a provided mapping.
   */
  public abstract static class ServletInvokingTaskCallback extends DeferredTaskCallback {

    @Override
    public void initialize(Map<String, String> properties) {
    }

    /**
     * @return A mapping from url path to HttpServlet. Where url path is a string that looks like
     *         "/foo/bar" (It must start with a '/' and should not contain characters that are not
     *         allowed in the path portion of a url.)
     */
    protected abstract Map<String, ? extends HttpServlet> getServletMap();

    /**
     * @return A servlet that will be used if none of the ones from {@link #getServletMap()} match.
     */
    protected abstract HttpServlet getDefaultServlet();

    private static Map<String, String> extractParamValues(final String body) {
      Map<String, String> params = Maps.newHashMap();
      if (body.length() > 0) {
        for (String keyValue : body.split("&")) {
          String[] split = keyValue.split("=");
          try {
            params.put(split[0], URLDecoder.decode(split[1], "utf-8"));
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not decode param " + split[1]);
          }
        }
      }
      return params;
    }

    @Override
    protected int executeNonDeferredRequest(URLFetchServicePb.URLFetchRequest req) {
      try {
        FakeHttpServletResponse response = new FakeHttpServletResponse();
        response.setCharacterEncoding("utf-8");

        URL url = new URL(req.getUrl());
        FakeHttpServletRequest request = new FakeHttpServletRequest();
        request.setMethod(req.getMethod().name());
        request.setHostName(url.getHost());
        request.setPort(url.getPort());
        request.setParametersFromQueryString(url.getQuery());

        for (Header header : req.getHeaderList()) {
          request.setHeader(header.getKey(), header.getValue());
        }

        String payload = req.getPayload().toStringUtf8();
        for (Map.Entry<String, String> entry : extractParamValues(payload).entrySet()) {
          request.addParameter(entry.getKey(), entry.getValue());
        }
        String servletPath = null;
        HttpServlet servlet = null;
        for (Entry<String, ? extends HttpServlet> entry : getServletMap().entrySet()) {
          if (url.getPath().startsWith(entry.getKey())) {
            servletPath = entry.getKey();
            servlet = entry.getValue();
          }
        }
        if (servlet == null) {
          servlet = getDefaultServlet();
          request.setPathInfo(url.getPath());
        } else {
          int servletPathStart = servletPath.lastIndexOf('/');
          if (servletPathStart == -1) {
            throw new IllegalArgumentException("The servlet path was configured as: "
                + servletPath + " which does not contan a '/'");
          }
          request.setContextPath(servletPath.substring(0, servletPathStart));
          request.setSerletPath(servletPath.substring(servletPathStart));
          request.setPathInfo(url.getPath().substring(servletPath.length()));
        }
        servlet.service(request, response);
        int result = response.getStatus();
        return result;
      } catch (Exception ex) {
        logger.log(Level.WARNING, ex.getMessage(), ex);
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
      }
    }
  }

  /**
   * A {@link CountDownLatch} extension that can be reset.  Pass an instance of
   * this class to {@link LocalTaskQueueTestConfig#setTaskExecutionLatch)} when
   * you need to reuse the latch within or across tests.  Only one thread at a
   * time should ever call any of the {@link #await} or {@link #reset} methods.
   */
  // This is a bit odd - we're extending CountDownLatch so that instances of
  // this class can be used anywhere a CountDownLatch is required, but we're
  // overriding every public method in CountDownLatch, so that the state we
  // inherit is completely ignored.  At least the oddness isn't exposed to
  // users.
  public static final class TaskCountDownLatch extends CountDownLatch {
    private int initialCount;
    private CountDownLatch latch;

    public TaskCountDownLatch(int count) {
      super(count);
      reset(count);
    }

    // Delegation methods
    @Override
    public long getCount() {
      return latch.getCount();
    }

    @Override
    public String toString() {
      return latch.toString();
    }

    /** {@inheritDoc} Only one thread at a time should call this. */
    @Override
    public void await() throws InterruptedException {
      latch.await();
    }

    /** {@inheritDoc} Only one thread at a time should call this. */
    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      return latch.await(timeout, unit);
    }

    @Override
    public void countDown() {
      latch.countDown();
    }
    // End delegation methods

    /**
     * Shorthand for calling {@link #await()} followed by {@link #reset()}.
     * Only one thread at a time should call this.
     */
    public void awaitAndReset() throws InterruptedException {
      awaitAndReset(initialCount);
    }

    /**
     * Shorthand for calling {@link #await()} followed by {@link #reset(int)}.
     * Only one thread at a time should call this.
     */
    public void awaitAndReset(int count) throws InterruptedException {
      await();
      reset(count);
    }

    /**
     * Shorthand for calling {@link #await(long, java.util.concurrent.TimeUnit)} followed by
     * {@link #reset()}.  Only one thread at a time should call this.
     */
    public boolean awaitAndReset(long timeout, TimeUnit unit)
        throws InterruptedException {
      return awaitAndReset(timeout, unit, initialCount);
    }

    /**
     * Shorthand for calling {@link #await(long, java.util.concurrent.TimeUnit)} followed by
     * {@link #reset(int)}.  Only one thread at a time should call this.
     */
    public boolean awaitAndReset(long timeout, TimeUnit unit, int count)
        throws InterruptedException {
      boolean result = await(timeout, unit);
      reset(count);
      return result;
    }

    /**
     * Resets the latch to its most recent initial count.  Only one thread at a
     * time should call this.
     */
    public void reset() {
      reset(initialCount);
    }

    /**
     * Resets the latch to the provided count.  Only one thread at a time
     * should call this.
     */
    public void reset(int count) {
      this.initialCount = count;
      this.latch = new CountDownLatch(count);
    }
  }
}
