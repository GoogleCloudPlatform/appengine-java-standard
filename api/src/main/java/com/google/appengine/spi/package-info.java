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
 * Support for alternate implementations of Google App Engine services. <b><i>This package is not
 * intended for use by application code.</i></b>
 *
 * <p>If, for example, vendor X wanted to provide an alternate implementation of the
 * DatastoreService, they would have to provide an implementation of {@link
 * com.google.appengine.api.datastore.IDatastoreServiceFactory} that returns their implementation
 * for {@link com.google.appengine.api.datastore.DatastoreService}.
 *
 * <p>Factory implementations are acquired using a {@link com.google.appengine.spi.FactoryProvider
 * FactoryProvider} registered with {@link com.google.appengine.spi.ServiceFactoryFactory
 * ServiceFactoryFactory}. These providers are typically discovered using {@link
 * java.util.ServiceLoader}; see {@link com.google.appengine.spi.ServiceFactoryFactory} for details.
 *
 * <p>This package includes the utility ({@link com.google.appengine.spi.ServiceProvider
 * ServiceProvider} for inserting the appropriate "service" entries into a jar file.
 *
 */
package com.google.appengine.spi;
