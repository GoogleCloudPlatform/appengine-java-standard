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
 * Provides facilities to check if a user has authenticated, retrieve their email address, and check
 * if they are an administrator for this application. It can also be used to construct a URL for
 * users to login or logout.
 *
 * <p>As an example, your application might, in a JSP file, have code like this:
 *
 * <pre>{@code
 * <%
 *   UserService userService = UserServiceFactory.getUserService();
 *   if (!userService.isUserLoggedIn()) {
 * %>
 *    Please {@code <a href="<%=userService.createLoginURL("/newlogin.jsp")>">log in</a>>}
 * <% } else { %>
 *    Welcome, <%= userService.getCurrentUser().getNickname(); %>!
 *      {@code (<a href="<%=userService.createLogoutURL("/")>">log out</a>>)}
 * <%
 *   }
 * %>
 * }</pre>
 *
 * @see com.google.appengine.api.users.UserService
 * @see <a href="http://cloud.google.com/appengine/docs/java/users/">The Users Java API in the
 *     <em>Google App Engine Developer's Guide</em></a>.
 */
package com.google.appengine.api.users;
