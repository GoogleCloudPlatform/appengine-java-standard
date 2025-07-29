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

package com.google.apphosting.utils.servlet.ee11;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Query;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * This servlet is run to cleanup expired sessions.  Since our
 * sessions are clustered, no individual runtime knows when they expire (nor
 * do we guarantee that runtimes survive to do cleanup), so we have to push
 * this determination out to an external sweeper like cron.
 *
 */
public class SessionCleanupServlet extends HttpServlet {

  static final String SESSION_ENTITY_TYPE = "_ah_SESSION";
  static final String EXPIRES_PROP = "_expires";

  // N.B.: This must be less than 500, which is the maximum
  // number of entities that may occur in a single bulk delete call.
  static final int MAX_SESSION_COUNT = 100;

  private DatastoreService datastore;

  @Override
  public void init() {
    datastore = DatastoreServiceFactory.getDatastoreService();
  }

  @Override
  public void service(HttpServletRequest request, HttpServletResponse response) {
    if ("clear".equals(request.getQueryString())) {
      clearAll(response);
    } else {
      sendForm(request.getRequestURI() + "?clear", response);
    }
  }

  private void clearAll(HttpServletResponse response) {
    Query query = new Query(SESSION_ENTITY_TYPE);
    query.setKeysOnly();
    query.addFilter(EXPIRES_PROP, Query.FilterOperator.LESS_THAN,
        System.currentTimeMillis());
    ArrayList<Key> killList = new ArrayList<Key>();
    Iterable<Entity> entities = datastore.prepare(query).asIterable(
        FetchOptions.Builder.withLimit(MAX_SESSION_COUNT));
    for (Entity expiredSession : entities) {
      Key key = expiredSession.getKey();
      killList.add(key);
    }
    datastore.delete(killList);
    response.setStatus(HttpServletResponse.SC_OK);
    try {
      response.getWriter().println("Cleared " + killList.size() + " expired sessions.");
    } catch (IOException ex) {
      // We still did the work, and successfully... just send an empty body.
    }
  }

  private void sendForm(String actionUrl, HttpServletResponse response) {
    Query query = new Query(SESSION_ENTITY_TYPE);
    query.setKeysOnly();
    query.addFilter(EXPIRES_PROP, Query.FilterOperator.LESS_THAN,
        System.currentTimeMillis());
    int count = datastore.prepare(query).countEntities();

    response.setContentType("text/html");
    response.setCharacterEncoding("utf-8");
    try {
      PrintWriter writer = response.getWriter();
      writer.println("<html><head><title>Session Cleanup</title></head>");
      writer.println("<body>There are currently " + count + " expired sessions.");
      writer.println("<p><form method=\"POST\" action=\"" + actionUrl + "\">");
      writer.println("<input type=\"submit\" value=\"Delete Next 100\" >");
      writer.println("</form></body></html>");
    } catch (IOException ex) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      try {
        response.getWriter().println(ex);
      } catch (IOException innerEx) {
        // we lose notifying them what went wrong.
      }
    }
    response.setStatus(HttpServletResponse.SC_OK);
  }
}
