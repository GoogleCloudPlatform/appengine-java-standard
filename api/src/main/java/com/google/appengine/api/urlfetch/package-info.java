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
 * Provides a service to make HTTP/S requests of other servers on the internet. The {@link
 * java.net.URLConnection} class can also be used to do this, and in App Engine is implemented by
 * using URL Fetch. Chunked and hanging requests, however, are not supported.
 *
 * <p>As is typical for App Engine services, the {@link URLFetchServiceFactory} returns a {@link
 * URLFetchService}, which is used to actually make requests of the service.
 *
 * @see com.google.appengine.api.urlfetch.URLFetchService
 * @see <a href="http://cloud.google.com/appengine/docs/java/urlfetch/">The URL Fetch Java API in
 *     the <em>Google App Engine Developer's Guide</em></a>.
 */
package com.google.appengine.api.urlfetch;
