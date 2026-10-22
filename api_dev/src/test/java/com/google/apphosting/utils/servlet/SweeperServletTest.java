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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SweeperServlet}. */
@RunWith(JUnit4.class)
public class SweeperServletTest {
  private static final String ENTITY_KIND_PENDING_TASK = "_AE_PendingCloudTask";
  private static final String PROPERTY_QUEUE_NAME = "queue_name";
  private static final String PROPERTY_CLOUD_TASK_NAME = "cloud_task_name";
  private static final String PROPERTY_CLOUD_TASK_PAYLOAD = "cloud_task_payload";
  private static final String PROPERTY_CREATED = "created";
  private static final String PROPERTY_STATUS = "status";
  private static final String STATUS_PENDING = "PENDING";

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig().setNoIndexAutoGen(true));

  private SweeperServlet servlet;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private StringWriter responseWriter;

  @Before
  public void setUp() throws Exception {
    helper.setUp();
    servlet = new SweeperServlet();
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    responseWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
  }

  @After
  public void tearDown() {
    helper.tearDown();
  }

  @Test
  public void testDoGet_forbiddenWhenNotCronOrDev() throws Exception {
    System.clearProperty("com.google.appengine.runtime.environment");
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn(null);
    when(request.getHeader(SweeperServlet.HEADER_HTTP_CRON)).thenReturn(null);

    servlet.doGet(request, response);

    verify(response)
        .sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Access denied: endpoint only accessible via App Engine Cron.");

    HttpServletResponse response2 = mock(HttpServletResponse.class);
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn("false");
    when(request.getHeader(SweeperServlet.HEADER_HTTP_CRON)).thenReturn("false");

    servlet.doGet(request, response2);

    verify(response2)
        .sendError(
            HttpServletResponse.SC_FORBIDDEN,
            "Access denied: endpoint only accessible via App Engine Cron.");
  }

  @Test
  public void testDoGet_executesWhenCronHeaderOrDevEnvironmentPresent() throws Exception {
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn("true");

    servlet.doGet(request, response);

    String output = responseWriter.toString();
    Assert.assertTrue(output.contains("Sweeper completed. Processed 0 tasks."));

    // Verify Development environment allows execution without cron headers
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn(null);
    responseWriter.getBuffer().setLength(0);
    System.setProperty("com.google.appengine.runtime.environment", "Development");
    try {
      servlet.doGet(request, response);
      Assert.assertTrue(
          responseWriter.toString().contains("Sweeper completed. Processed 0 tasks."));
    } finally {
      System.clearProperty("com.google.appengine.runtime.environment");
    }

    // Verify appengine-local-runtime on java.class.path allows execution without cron headers
    responseWriter.getBuffer().setLength(0);
    String originalClassPath = System.getProperty("java.class.path", "");
    System.setProperty("java.class.path", originalClassPath + ":appengine-local-runtime");
    try {
      servlet.doGet(request, response);
      Assert.assertTrue(
          responseWriter.toString().contains("Sweeper completed. Processed 0 tasks."));
    } finally {
      System.setProperty("java.class.path", originalClassPath);
    }
  }

  @Test
  public void testDoGet_sweepsStalePendingTask() throws Exception {
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn("true");

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity entity = new Entity(ENTITY_KIND_PENDING_TASK);
    entity.setProperty(PROPERTY_STATUS, STATUS_PENDING);
    // Created 2 minutes ago
    entity.setProperty(PROPERTY_CREATED, Date.from(Instant.now().minus(Duration.ofMinutes(2))));
    entity.setProperty(PROPERTY_QUEUE_NAME, "default");
    entity.setProperty(PROPERTY_CLOUD_TASK_NAME, "test-swept-task");
    entity.setProperty(PROPERTY_CLOUD_TASK_PAYLOAD, "{}");
    ds.put(entity);

    servlet.doGet(request, response);

    String output = responseWriter.toString();
    Assert.assertTrue(output.contains("Sweeper completed. Processed 1 tasks."));
  }

  @Test
  public void testDoGet_nullContextClassLoaderFallsBackToClassForName() throws Exception {
    when(request.getHeader(SweeperServlet.HEADER_HTTP_CRON)).thenReturn("true");

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity entity = new Entity(ENTITY_KIND_PENDING_TASK);
    entity.setProperty(PROPERTY_STATUS, STATUS_PENDING);
    entity.setProperty(PROPERTY_CREATED, Date.from(Instant.now().minus(Duration.ofMinutes(2))));
    entity.setProperty(PROPERTY_QUEUE_NAME, "default");
    entity.setProperty(PROPERTY_CLOUD_TASK_NAME, "null-loader-task");
    entity.setProperty(PROPERTY_CLOUD_TASK_PAYLOAD, "{}");
    ds.put(entity);

    ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(null);
    try {
      servlet.doGet(request, response);
    } finally {
      Thread.currentThread().setContextClassLoader(originalLoader);
    }

    String output = responseWriter.toString();
    Assert.assertTrue(output.contains("Sweeper completed. Processed 1 tasks."));
  }

  @Test
  public void testDoGet_reflectionFailureReportsZeroProcessedTasks() throws Exception {
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn("true");

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity entity = new Entity(ENTITY_KIND_PENDING_TASK);
    entity.setProperty(PROPERTY_STATUS, STATUS_PENDING);
    entity.setProperty(PROPERTY_CREATED, Date.from(Instant.now().minus(Duration.ofMinutes(2))));
    entity.setProperty(PROPERTY_QUEUE_NAME, "default");
    entity.setProperty(PROPERTY_CLOUD_TASK_NAME, "broken-loader-task");
    entity.setProperty(PROPERTY_CLOUD_TASK_PAYLOAD, "{}");
    ds.put(entity);

    ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader failingLoader =
        new ClassLoader(null) {
          @Override
          public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (Objects.equals(name, "com.google.appengine.api.taskqueue.TaskProcessor")) {
              throw new ClassNotFoundException("Simulated missing TaskProcessor");
            }
            return super.loadClass(name);
          }
        };
    Thread.currentThread().setContextClassLoader(failingLoader);
    try {
      servlet.doGet(request, response);
    } finally {
      Thread.currentThread().setContextClassLoader(originalLoader);
    }

    String output = responseWriter.toString();
    Assert.assertTrue(output.contains("Sweeper completed. Processed 0 tasks."));
  }
}
