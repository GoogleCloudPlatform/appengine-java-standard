/*
 * Copyright 2022 Google LLC
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
package com.google.appengine.setup.testapps.jetty11.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;


public class DatastoreTestServlet extends HttpServlet {
    private static final Random random = new Random(System.currentTimeMillis());

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        Key key = putDatastoreEntity();
        String entity = null;
        try {
            entity = getDatastoreEntity(key);
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
        }
        out.println("Key - " + key);
        out.println("Entity - " + entity);
    }

    private Key putDatastoreEntity() {
        DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
        Entity car = new Entity("Car");
        car.setProperty("color", "blue " + random.nextInt());
        car.setProperty("brand", "tesla " + random.nextInt());
        return datastoreService.put(car);
    }
    private String getDatastoreEntity(Key key) throws EntityNotFoundException {
        DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
        Entity entity = datastoreService.get(key);
        return entity.getProperties().toString();
    }
}