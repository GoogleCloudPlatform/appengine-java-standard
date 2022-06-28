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

package com.google.appengine.tools.development.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig.ServletInvokingTaskCallback;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;
import org.junit.Test;

public class ServletInvokingTaskCallbackTest extends TestCase {


  private final LocalTaskQueueTestConfig.TaskCountDownLatch latch =
      new LocalTaskQueueTestConfig.TaskCountDownLatch(1);

  private final
      LocalServiceTestHelper helper = new LocalServiceTestHelper(new LocalTaskQueueTestConfig()
          .setCallbackClass(TestServletInvokingCallback.class).setTaskExecutionLatch(latch)
          .setDisableAutoTaskExecution(false));


  static class TestServlet extends HttpServlet {
    List<HttpServletRequest> gets = new Vector<>();
    List<HttpServletRequest> posts = new Vector<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      gets.add(req);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
      posts.add(req);
    }
  }

  static class TestServletInvokingCallback extends ServletInvokingTaskCallback {

    static final HashMap<String, TestServlet> MAP = new HashMap<>();
    static TestServlet defaultServlet;
    static {
      reset();
    }

    @Override
    protected Map<String, TestServlet> getServletMap() {
      return MAP;
    }

    @Override
    protected HttpServlet getDefaultServlet() {
      return defaultServlet;
    }
    static void reset() {
      defaultServlet = new TestServlet();
      MAP.put("/a", new TestServlet());
      MAP.put("/b/c", new TestServlet());
    }
  }

  @Override
  public void setUp() throws Exception {
    // Sets up helper first, so that it won't overwrite the environment.
    helper.setUp();
    super.setUp();
    TestServletInvokingCallback.reset();
  }


  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    helper.tearDown();
  }

  public void testCallbacksAreInvoked() throws InterruptedException {
    latch.reset(3);
    Queue queue = QueueFactory.getDefaultQueue();
    queue.add(TaskOptions.Builder.withUrl("/a").method(TaskOptions.Method.GET));
    queue.add(TaskOptions.Builder.withUrl("/b/c").method(TaskOptions.Method.GET));
    queue.add(TaskOptions.Builder.withUrl("/c").method(TaskOptions.Method.GET));
    latch.await(5, TimeUnit.SECONDS);
    assertEquals(1, TestServletInvokingCallback.MAP.get("/a").gets.size());
    assertEquals(1, TestServletInvokingCallback.MAP.get("/b/c").gets.size());
    assertEquals(1, TestServletInvokingCallback.defaultServlet.gets.size());
    assertEquals(0, TestServletInvokingCallback.MAP.get("/a").posts.size());
    assertEquals(0, TestServletInvokingCallback.MAP.get("/b/c").posts.size());
    assertEquals(0, TestServletInvokingCallback.defaultServlet.posts.size());
  }

  private static class MyTask implements DeferredTask {
    private static boolean taskRan = false;

    @Override
    public void run() {
      taskRan = true;
    }
  }

  @Test
  public void testDeferedTasks() throws InterruptedException {
    latch.reset(1);
    QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withPayload(new MyTask()));
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    assertTrue(MyTask.taskRan);
  }
  
  @Test
  public void testParametersArePerserved() throws InterruptedException {
    latch.reset(3);
    Queue queue = QueueFactory.getDefaultQueue();
    queue.add(TaskOptions.Builder.withUrl("/a/b/c").method(TaskOptions.Method.POST)
        .header("foo", "bar").param("p1", "v1"));
    queue.add(TaskOptions.Builder.withUrl("/b/c").method(TaskOptions.Method.GET)
        .param("param", "value"));
    queue.add(TaskOptions.Builder.withUrl("/c").method(TaskOptions.Method.GET)
        .param("param1", "value1").param("param2", "value2"));
    latch.await(5, TimeUnit.SECONDS);
    assertEquals(1, TestServletInvokingCallback.MAP.get("/a").posts.size());
    HttpServletRequest request = TestServletInvokingCallback.MAP.get("/a").posts.get(0);
    assertEquals("bar", request.getHeader("foo"));
    assertEquals("POST", request.getMethod());
    assertEquals("http", request.getScheme());
    assertEquals("localhost", request.getServerName());
    assertThat(request.getContextPath()).isEmpty();
    assertEquals("/a", request.getServletPath());
    assertEquals("/b/c", request.getPathInfo());
    assertEquals("/a/b/c", request.getRequestURI());
    assertEquals("http://localhost:8080/a/b/c", request.getRequestURL().toString());
    assertEquals(null, request.getQueryString());
    assertEquals("v1", request.getParameter("p1"));
    
    assertEquals(1, TestServletInvokingCallback.MAP.get("/b/c").gets.size());
    request = TestServletInvokingCallback.MAP.get("/b/c").gets.get(0);
    assertEquals("value", request.getParameter("param"));
    assertEquals("GET", request.getMethod());
    assertEquals("http", request.getScheme());
    assertEquals("localhost", request.getServerName());
    assertEquals("/b", request.getContextPath());
    assertEquals("/c", request.getServletPath());
    assertEquals(null, request.getPathInfo());
    assertEquals("/b/c", request.getRequestURI());
    assertEquals("http://localhost:8080/b/c", request.getRequestURL().toString());
    assertEquals("param=value", request.getQueryString());
    assertEquals("value", request.getParameter("param"));
    
    assertEquals(1, TestServletInvokingCallback.defaultServlet.gets.size());
    request = TestServletInvokingCallback.defaultServlet.gets.get(0);
    assertEquals("value1", request.getParameter("param1"));
    assertEquals("value2", request.getParameter("param2"));
    assertEquals("GET", request.getMethod());
    assertEquals("http", request.getScheme());
    assertEquals("localhost", request.getServerName());
    assertThat(request.getContextPath()).isEmpty();
    assertThat(request.getServletPath()).isEmpty();
    assertEquals("/c", request.getPathInfo());
    assertEquals("/c", request.getRequestURI());
    assertEquals("http://localhost:8080/c", request.getRequestURL().toString());
    assertEquals("param1=value1&param2=value2", request.getQueryString());
    assertEquals("value1", request.getParameter("param1"));
    assertEquals("value2", request.getParameter("param2"));
  }

}
