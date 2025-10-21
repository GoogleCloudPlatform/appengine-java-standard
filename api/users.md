<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Google App Engine Users API Documentation

*   [Users API](#users-api)
    *   [Overview](#overview)
    *   [User Authentication](#user-authentication)
    *   [User Objects and Datastore](#user-objects-and-datastore)
    *   [Enforcing Sign-In and Admin Access](#enforcing-sign-in-and-admin-access)
    *   [Authentication Options](#authentication-options)
    *   [Choosing an Authentication Option](#choosing-an-authentication-option)
    *   [Signing In and Out](#signing-in-and-out)
    *   [Accessing Account Information](#accessing-account-information)
    *   [Users and the Datastore](#users-and-the-datastore)
    *   [Google Accounts and the Development Server](#google-accounts-and-the-development-server)

## Users API

### Overview

The Users API allows applications to: - Detect whether the current user has
signed in - Redirect users to the appropriate sign-in page - Request that users
create a Google Account if needed - Access the user's email address while signed
in - Detect if the current user is an administrator

**Admin User Definition**: Any user with the Viewer, Editor, Owner, or App
Engine Admin role.

### User Authentication

Greet signed-in users with a personalized message and sign-out link. Offer a
sign-in link to unsigned users. Test if a user is signed in and get their email
using the servlet API:

```java
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(
    name = "UserAPI",
    description = "UserAPI: Login / Logout with UserService",
    urlPatterns = "/userapi")
public class UsersServlet extends HttpServlet {
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    UserService userService = UserServiceFactory.getUserService();
    String thisUrl = req.getRequestURI();
    resp.setContentType("text/html");
    if (req.getUserPrincipal() != null) {
      resp.getWriter()
          .println(
              "<p>Hello, "
                  + req.getUserPrincipal().getName()
                  + "! You can <a href=\""
                  + userService.createLogoutURL(thisUrl)
                  + "\">sign out</a>.</p>");
    } else {
      resp.getWriter()
          .println("<p>Please <a href=\"" + userService.createLoginURL(thisUrl) + "\">sign in</a>.</p>");
    }
  }
}
```

### User Objects and Datastore

The Users API returns user information as a `User` object. While `User` objects
can be stored in the datastore, this is **not recommended** because it includes
the email address along with the user's unique ID. If a user changes their email
address and you compare their old stored `User` object to the new one, they
won't match. Instead, use the User user ID as the stable unique identifier.

### Enforcing Sign-In and Admin Access

Pages requiring authentication can use security constraints in `web.xml`. If an
unauthenticated user accesses a URL with a security constraint, App Engine
redirects to the sign-in page, then directs back after successful sign-in.
Security constraints can also require users to be registered administrators
(Viewer, Editor, Owner, or App Engine Admin role), enabling easy
administrator-only sections without separate authorization mechanisms. See "The
Deployment Descriptor: Security and Authentication" documentation for `web.xml`
configuration details.

### Authentication Options

Apps can authenticate users with: - A Google Account - An account on a Google
Workspace domain

### Choosing an Authentication Option

After creating your app, you can select the authentication option. By default,
Google Accounts are used. To change: 1. Go to the settings page for your project
in the Google Cloud console 2. Click Edit 3. In the "Google authentication"
dropdown menu, select the desired type 4. Click Save

### Signing In and Out

Apps can detect whether a user has signed in using their chosen authentication
option. Unsigned users can be directed to Google Accounts to sign in or create a
new account. Get the sign-in URL by calling a Users API method. The app can
display this as a link or issue an HTTP redirect. If using Google Accounts or
Google Workspace, the application name appears on the sign-in page. This name is
specified on the Google Cloud console Credentials page. You can change it in the
"Application name" field. After signing in or creating an account, users are
redirected back to your application using the redirect URL you provided. The
Users API includes a method to generate a sign-out URL, which de-authenticates
the user and redirects without displaying anything. **Important**: Users are not
signed in until prompted by the app to enter their email and password, even if
they've signed into other applications with their Google Account.

### Accessing Account Information

While signed in, the app can access: - The user's email address for every
request - A unique user ID that remains constant even if the user changes their
email The app can also determine if the current user is an administrator,
enabling admin-only features. **Security note**: Every user has the same user ID
for all App Engine applications. If your app uses the user ID in public data
(e.g., URL parameters), use a hash algorithm with a "salt" value to obscure the
ID. Exposing raw IDs could allow association of a user's activity across apps or
email address discovery.

### Users and the Datastore

Avoid storing `User` objects in the datastore, as discussed above. Instead,
store and use the user ID as the stable unique identifier.

### Google Accounts and the Development Server

The development server simulates Google Accounts with a fake sign-in screen.
When your app calls the Users API for the sign-in URL, it returns a development
server URL prompting for an email address (no password required). You can enter
any email address, and the app behaves as if signed in with that address. The
fake sign-in screen includes a checkbox indicating whether the account is an
administrator. Checking it makes the app behave as if signed in with an admin
account. The Users API returns a sign-out URL that cancels the fake sign-in.
