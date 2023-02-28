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

/**
 * Provides persistent storage, also accessible via <a
 * href="http://www.oracle.com/technetwork/java/index-jsp-135919.html">JDO</a> or <a
 * href="http://www.oracle.com/technetwork/articles/javaee/jpa-137156.html">JPA</a> interfaces. It
 * provides redundant storage for fault-tolerance.
 *
 * <p>A common pattern of usage is:
 *
 * <pre>{@code
 * // Get a handle on the datastore itself
 * DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
 *
 * // Lookup data by known key name
 * Entity userEntity = datastore.get(KeyFactory.createKey("UserInfo", email));
 *
 * // Or perform a query
 * Query query = new Query("Task");
 * query.addFilter("dueDate", Query.FilterOperator.LESS_THAN, today);
 * for (Entity taskEntity : datastore.prepare(query).asIterable()) {
 *   if ("done".equals(taskEntity.getProperty("status"))) {
 *     datastore.delete(taskEntity);
 *   } else {
 *     taskEntity.setProperty("status", "overdue");
 *     datastore.put(taskEntity);
 *   }
 * }
 * }</pre>
 *
 * <p>This illustrates several basic points:
 *
 * <ul>
 *   <li>The actual datastore itself is accessed through a {@link
 *       com.google.appengine.api.datastore.DatastoreService} object, produced from a {@link
 *       com.google.appengine.api.datastore.DatastoreServiceFactory}.
 *   <li>The unit of storage is the {@link com.google.appengine.api.datastore.Entity} object, which
 *       are of named kinds ("UserInfo" and "Task" above).
 *   <li>Entities have a {@link com.google.appengine.api.datastore.Key} value, which can be created
 *       by a {@link com.google.appengine.api.datastore.KeyFactory} to retrieve a specific known
 *       entity. If the key is not readily determined, then {@link
 *       com.google.appengine.api.datastore.Query} objects can be used to retrieve one Entity,
 *       multiple as a list, {@link java.lang.Iterable}, or {@link java.util.Iterator}, or to
 *       retrieve the count of matching entities.
 *   <li>Entities have named properties, the values of which may be basic types or collections of
 *       basic types. Richer objects, of course, may be stored if serialized as byte arrays,
 *       although that may prevent effective querying by those properties.
 *   <li>Entities may be associated in a tree structure; the {@link
 *       com.google.appengine.api.datastore.Query} in the snippet above searches only for Task
 *       entities associated with a specific UserInfo entity, and then filters those for Tasks due
 *       before today.
 * </ul>
 *
 * <p>In production, non-trivial queries cannot be performed until one or more indexes have been
 * built to ensure that the individual queries can be processed efficiently. You can specify the set
 * of indexes your application requires in a {@code WEB-INF/datastore-indexes.xml} file, or they can
 * be generated automatically as you test your application in the Development Server. If a query
 * requires an index that cannot be found, a {@link
 * com.google.appengine.api.datastore.DatastoreNeedIndexException} will be thrown at runtime.
 *
 * <p>Although Google App Engine allows many versions of your application to be accessible, there is
 * only one datastore for your application, shared by all versions. Similarly, the set of indexes is
 * shared by all application versions.
 *
 * <p>Application authors may also consider using either of the provided JDO or JPA interfaces to
 * the datastore.
 *
 * @see com.google.appengine.api.datastore.DatastoreService
 * @see <a href="http://cloud.google.com/appengine/docs/java/datastore/">The Datastore Java API in
 *     the Google App Engine Developers Guide</a>
 * @see <a href="http://www.oracle.com/technetwork/java/index-jsp-135919.html">JDO API</a>
 * @see <a href="http://www.oracle.com/technetwork/articles/javaee/jpa-137156.html">JPA API</a>
 */
package com.google.appengine.api.datastore;
