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

package com.google.apphosting.utils.servlet;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withPayload;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.DeferredTaskContext;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.testing.MockEnvironment;
import com.google.apphosting.api.ApiProxy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeferredTaskServletTest {
  private static class DontSerialize {
    com.google.apphosting.utils.servlet.DeferredTaskServlet servlet =
        new DeferredTaskServlet() {
          @Override
          public void log(String msg) {}
        };

    HttpServletResponse resp;
    HttpServletRequest req;
    int runCount;

    DontSerialize() {
      runCount = 0;
      resp = mock(HttpServletResponse.class);
      req = mock(HttpServletRequest.class);
    }
  }

  private static DontSerialize testState;

  private DeferredTask deferredSuccess;
  private DeferredTask deferredFail;
  private DeferredTask deferredFailDoNotRetry;
  private DeferredTask deferredMarkForRetry;

  private static void initializeTasks(DeferredTaskServletTest testCase) {
    testCase.deferredSuccess =
        () -> {
          ++getState().runCount;
          assertEquals(getState().req, DeferredTaskContext.getCurrentRequest());
          assertEquals(getState().resp, DeferredTaskContext.getCurrentResponse());
          assertEquals(getState().servlet, DeferredTaskContext.getCurrentServlet());
        };

    testCase.deferredFail =
        () -> {
          ++getState().runCount;
          assertEquals(getState().req, DeferredTaskContext.getCurrentRequest());
          assertEquals(getState().resp, DeferredTaskContext.getCurrentResponse());
          assertEquals(getState().servlet, DeferredTaskContext.getCurrentServlet());
          throw new RuntimeException();
        };

    testCase.deferredMarkForRetry =
        () -> {
          ++getState().runCount;
          assertEquals(getState().req, DeferredTaskContext.getCurrentRequest());
          assertEquals(getState().resp, DeferredTaskContext.getCurrentResponse());
          assertEquals(getState().servlet, DeferredTaskContext.getCurrentServlet());
          DeferredTaskContext.markForRetry();
        };

    testCase.deferredFailDoNotRetry =
        () -> {
          ++getState().runCount;
          DeferredTaskContext.setDoNotRetry(true);
          assertEquals(getState().req, DeferredTaskContext.getCurrentRequest());
          assertEquals(getState().resp, DeferredTaskContext.getCurrentResponse());
          assertEquals(getState().servlet, DeferredTaskContext.getCurrentServlet());
          throw new RuntimeException();
        };
  }

  private static DontSerialize getState() {
    return testState;
  }

  @Before
  public void setUp() {
    ApiProxy.setEnvironmentForCurrentThread(new MockEnvironment("app-is", "v1"));
    testState = new DontSerialize();
    initializeTasks(this);
  }

  ServletInputStream mockStream(final byte[] bytes) {
    return new ServletInputStream() {
      InputStream stream = new ByteArrayInputStream(bytes);

      @Override
      public int read() throws IOException {
        return stream.read();
      }

      @Override
      public int read(byte[] b) throws IOException {
        return stream.read(b);
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        return stream.read(b, off, len);
      }

      @Override
      public long skip(long n) throws IOException {
        return stream.skip(n);
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public boolean isFinished() {
        return true;
      }
    };
  }

  private void initMocksWithOptions(final TaskOptions opts) throws IOException {
    when(getState().req.getHeader("content-type"))
        .thenReturn(opts.getHeaders().get("content-type").get(0));
    when(getState().req.getProtocol()).thenReturn("HTTP/1.1");
    when(getState().req.getInputStream()).thenReturn(mockStream(opts.getPayload()));
  }

  private void initPostAndQueueMocks() {
    when(getState().req.getMethod()).thenReturn("POST");
    when(getState().req.getHeader(DeferredTaskServlet.X_APPENGINE_QUEUENAME)).thenReturn("default");
  }
  // Initialize the mock request and response for the task given.
  private void initSuccessMocks(final DeferredTask deferredTask) throws IOException {
    initPostAndQueueMocks();
    initMocksWithOptions(withPayload(deferredTask));
  }

  @Test
  public void testReadRequest() throws DeferredTaskServlet.DeferredTaskException, IOException {
    initSuccessMocks(DEFERRED_TASK_1);
    final AtomicBoolean wasCalled = new AtomicBoolean();
    final ClassLoader prev = Thread.currentThread().getContextClassLoader();
    ClassLoader classLoader =
        new ClassLoader() {
          @Override
          public Class<?> loadClass(String name) throws ClassNotFoundException {
            wasCalled.set(true);
            return prev.loadClass(name);
          }
        };
    Thread.currentThread().setContextClassLoader(classLoader);
    try {
      Runnable deferredTask = getState().servlet.readRequest(getState().req, getState().resp);
      assertEquals(DEFERRED_TASK_1, deferredTask);
      assertTrue("Thread Context classloader was not called", wasCalled.get());
    } finally {
      Thread.currentThread().setContextClassLoader(prev);
    }
  }

  @Test
  public void testSuccessTask() throws ServletException, IOException {
    initSuccessMocks(deferredSuccess);
    getState().resp.setStatus(HttpURLConnection.HTTP_OK);
    getState().servlet.service(getState().req, getState().resp);
    assertEquals(1, getState().runCount);
  }

  @Test
  public void testFailTask() throws IOException {
    initSuccessMocks(deferredFail);
    try {
      getState().servlet.service(getState().req, getState().resp);
      fail("Expected a ServletException");
    } catch (ServletException e) {
      // good
    }
    assertEquals(1, getState().runCount);
  }

  @Test
  public void testDeferredMarkForRetry() throws ServletException, IOException {
    initSuccessMocks(deferredMarkForRetry);
    getState().resp.setStatus(HttpURLConnection.HTTP_INTERNAL_ERROR);
    getState().servlet.service(getState().req, getState().resp);
    assertEquals(1, getState().runCount);
  }

  @Test
  public void testDoNotRetry() throws ServletException, IOException {
    initSuccessMocks(deferredFailDoNotRetry);
    getState().resp.setStatus(HttpURLConnection.HTTP_NOT_AUTHORITATIVE);
    getState().servlet.service(getState().req, getState().resp);
    assertEquals(1, getState().runCount);
  }

  @Test
  public void testReadBadContentType() throws ServletException, IOException {
    initPostAndQueueMocks();
    TaskOptions opts = withPayload(deferredFailDoNotRetry);
    initMocksWithOptions(withPayload(opts.getPayload(), "Not the right type."));
    getState().resp.setStatus(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
    getState().servlet.service(getState().req, getState().resp);
    assertEquals(0, getState().runCount);
  }

  @Test
  public void testReadBadContent() throws ServletException, IOException {
    initPostAndQueueMocks();
    initMocksWithOptions(
        withPayload(
            "this is not a DeferredTask".getBytes(UTF_8),
            DeferredTaskContext.RUNNABLE_TASK_CONTENT_TYPE));
    getState().resp.setStatus(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
    getState().servlet.service(getState().req, getState().resp);
    assertEquals(0, getState().runCount);
  }

  @Test
  public void testWrongMethodTask() throws ServletException, IOException {
    when(getState().req.getMethod()).thenReturn("GET");
    when(getState().req.getProtocol()).thenReturn("HTTP/1.1");
    when(getState().req.getHeader(DeferredTaskServlet.X_APPENGINE_QUEUENAME)).thenReturn("default");
    getState()
        .resp
        .sendError(
            HttpURLConnection.HTTP_BAD_METHOD, "DeferredTaskServlet does not support method: GET");
    getState().servlet.service(getState().req, getState().resp);
  }

  @Test
  public void testNoQueueHeaderMethodTask() throws ServletException, IOException {
    when(getState().req.getHeader(DeferredTaskServlet.X_APPENGINE_QUEUENAME)).thenReturn(null);
    getState().resp.sendError(HttpURLConnection.HTTP_FORBIDDEN, "Not a taskqueue request.");
    getState().servlet.service(getState().req, getState().resp);
  }

  private static final DeferredTask DEFERRED_TASK_1 = new TestDeferredTask("task 1");

  /** A deferred task for testing. */
  private static class TestDeferredTask implements DeferredTask {
    private static final long serialVersionUID = 1;

    private final String compare;

    private TestDeferredTask(String compare) {
      this.compare = compare;
    }

    @Override
    public void run() {}

    @Override
    public int hashCode() {
      return Objects.hashCode(compare);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TestDeferredTask)) {
        return false;
      }
      TestDeferredTask other = (TestDeferredTask) obj;
      return Objects.equals(compare, other.compare);
    }
  }
}
