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
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

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
  }

  @Test
  public void testDoGet_executesWhenCronHeaderPresent() throws Exception {
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn("true");

    servlet.doGet(request, response);

    String output = responseWriter.toString();
    Assert.assertTrue(output.contains("Sweeper completed"));
  }

  @Test
  public void testDoGet_sweepsStalePendingTask() throws Exception {
    when(request.getHeader(SweeperServlet.HEADER_CRON)).thenReturn("true");

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Entity entity = new Entity(ENTITY_KIND_PENDING_TASK);
    entity.setProperty(PROPERTY_STATUS, STATUS_PENDING);
    // Created 2 minutes ago
    entity.setProperty(
        PROPERTY_CREATED,
        Date.from(Instant.now().minus(Duration.ofMinutes(2))));
    entity.setProperty(PROPERTY_QUEUE_NAME, "default");
    entity.setProperty(PROPERTY_CLOUD_TASK_NAME, "test-swept-task");
    entity.setProperty(PROPERTY_CLOUD_TASK_PAYLOAD, "{}");
    ds.put(entity);

    servlet.doGet(request, response);

    String output = responseWriter.toString();
    Assert.assertTrue(output.contains("Sweeper completed"));
  }
}
