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

package com.google.apphosting.runtime.jetty;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.apphosting.api.ApiProxy;
import com.google.common.flogger.GoogleLogger;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Function;
import javax.security.auth.Subject;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.AuthenticationState;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Session;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.URIUtil;

/**
 * {@code AppEngineAuthentication} is a utility class that can configure a Jetty {@link
 * SecurityHandler} to integrate with the App Engine authentication model.
 *
 * <p>Specifically, it registers a custom {@link Authenticator} instance that knows how to redirect
 * users to a login URL using the {@link UserService}, and a custom {@link UserIdentity} that is
 * aware of the custom roles provided by the App Engine.
 */
public class EE10AppEngineAuthentication {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  /**
   * URLs that begin with this prefix are reserved for internal use by App Engine. We assume that
   * any URL with this prefix may be part of an authentication flow (as in the Dev Appserver).
   */
  private static final String AUTH_URL_PREFIX = "/_ah/";

  private static final String AUTH_METHOD = "Google Login";

  private static final String REALM_NAME = "Google App Engine";

  // Keep in sync with com.google.apphosting.runtime.jetty.JettyServletEngineAdapter.
  private static final String SKIP_ADMIN_CHECK_ATTR =
      "com.google.apphosting.internal.SkipAdminCheck";

  /**
   * Any authenticated user is a member of the {@code "*"} role, and any administrators are members
   * of the {@code "admin"} role. Any other roles will be logged and ignored.
   */
  private static final String USER_ROLE = "*";

  private static final String ADMIN_ROLE = "admin";

  /**
   * Inject custom {@link LoginService} and {@link Authenticator} implementations into the specified
   * {@link ConstraintSecurityHandler}.
   */
  public static void configureSecurityHandler(ConstraintSecurityHandler handler) {

    LoginService loginService = new AppEngineLoginService();
    LoginAuthenticator authenticator = new AppEngineAuthenticator();
    DefaultIdentityService identityService = new DefaultIdentityService();

    // Set allowed roles.
    handler.setRoles(new HashSet<>(Arrays.asList(USER_ROLE, ADMIN_ROLE)));
    handler.setLoginService(loginService);
    handler.setAuthenticator(authenticator);
    handler.setIdentityService(identityService);
    authenticator.setConfiguration(handler);
  }

  /**
   * {@code AppEngineAuthenticator} is a custom {@link Authenticator} that knows how to redirect the
   * current request to a login URL in order to authenticate the user.
   */
  private static class AppEngineAuthenticator extends LoginAuthenticator {

    /**
     * Checks if the request could to to the login page.
     *
     * @param uri The uri requested.
     * @return True if the uri starts with "/_ah/", false otherwise.
     */
    private static boolean isLoginOrErrorPage(String uri) {
      return uri.startsWith(AUTH_URL_PREFIX);
    }

    @Override
    public String getAuthenticationType() {
      return AUTH_METHOD;
    }

    @Override
    public Constraint.Authorization getConstraintAuthentication(
        String pathInContext,
        Constraint.Authorization existing,
        Function<Boolean, Session> getSession) {
      return super.getConstraintAuthentication(pathInContext, existing, getSession);
    }

    /**
     * Validate a response. Compare to:
     * j.c.g.apphosting.utils.jetty.AppEngineAuthentication.AppEngineAuthenticator.authenticate().
     *
     * <p>If authentication is required but the request comes from an untrusted ip, 307s the request
     * back to the trusted appserver. Otherwise it will auth the request and return a login url if
     * needed.
     *
     * <p>From org.eclipse.jetty.server.Authentication:
     *
     * @param servletRequest The request
     * @param servletResponse The response
     * @param mandatory True if authentication is mandatory.
     * @return An Authentication. If Authentication is successful, this will be a {@link
     *     Authentication.User}. If a response has been sent by the Authenticator (which can be done
     *     for both successful and unsuccessful authentications), then the result will implement
     *     {@link Authentication.ResponseSent}. If Authentication is not mandatory, then a {@link
     *     Authentication.Deferred} may be returned.
     * @throws ServerAuthException
     */
    @Override
    public AuthenticationState validateRequest(Request req, Response res, Callback cb)
        throws ServerAuthException {

      ServletContextRequest contextRequest = Request.as(req, ServletContextRequest.class);

      HttpServletRequest request = contextRequest.getServletApiRequest();
      HttpServletResponse response = contextRequest.getHttpServletResponse();

      // Trusted inbound ip, auth headers can be trusted.

      // Use the canonical path within the context for authentication and authorization
      // as this is what is used to generate response content
      String uri = URIUtil.addPaths(request.getServletPath(), request.getPathInfo());

      if (uri == null) {
        uri = "/";
      }
      // Check this before checking if there is a user logged in, so
      // that we can log out properly.  Specifically, watch out for
      // the case where the user logs in, but as a role that isn't
      // allowed to see /*.  They should still be able to log out.
      if (isLoginOrErrorPage(uri) && !AuthenticationState.Deferred.isDeferred(res)) {
        logger.atFine().log(
            "Got %s, returning DeferredAuthentication to imply authentication is in progress.",
            uri);
        return null;
      }

      if (request.getAttribute(SKIP_ADMIN_CHECK_ATTR) != null) {
        logger.atFine().log("Returning DeferredAuthentication because of SkipAdminCheck.");
        // Warning: returning DeferredAuthentication here will bypass security restrictions!
        return null;
      }

      if (response == null) {
        throw new ServerAuthException("validateRequest called with null response!!!");
      }

      try {
        UserService userService = UserServiceFactory.getUserService();
        // If the user is authenticated already, just create a
        // AppEnginePrincipal or AppEngineFederatedPrincipal for them.
        if (userService.isUserLoggedIn()) {
          UserIdentity user = _loginService.login(null, null, null, null);
          logger.atFine().log("authenticate() returning new principal for %s", user);
          if (user != null) {
            return new UserAuthenticationSent(getAuthenticationType(), user);
          }
        }

        if (AuthenticationState.Deferred.isDeferred(res)) {
          return null;
        }

        try {
          logger.atFine().log(
              "Got %s but no one was logged in, redirecting.", request.getRequestURI());
          String url = userService.createLoginURL(getFullURL(request));
          response.sendRedirect(url);
          // Tell Jetty that we've already committed a response here.
          return AuthenticationState.CHALLENGE;
        } catch (ApiProxy.ApiProxyException ex) {
          // If we couldn't get a login URL for some reason, return a 403 instead.
          logger.atSevere().withCause(ex).log("Could not get login URL:");
          response.sendError(HttpServletResponse.SC_FORBIDDEN);
          return AuthenticationState.SEND_FAILURE;
        }
      } catch (IOException ex) {
        throw new ServerAuthException(ex);
      }
    }
  }

  /** Returns the full URL of the specified request, including any query string. */
  private static String getFullURL(HttpServletRequest request) {
    StringBuffer buffer = request.getRequestURL();
    if (request.getQueryString() != null) {
      buffer.append('?');
      buffer.append(request.getQueryString());
    }
    return buffer.toString();
  }

  /**
   * {@code AppEngineLoginService} is a custom Jetty {@link LoginService} that is aware of the two
   * special role names implemented by Google App Engine. Any authenticated user is a member of the
   * {@code "*"} role, and any administrators are members of the {@code "admin"} role. Any other
   * roles will be logged and ignored.
   */
  private static class AppEngineLoginService implements LoginService {
    private IdentityService identityService;

    /**
     * @return Get the name of the login service (aka Realm name)
     */
    @Override
    public String getName() {
      return REALM_NAME;
    }

    @Override
    public UserIdentity login(
        String s, Object o, Request request, Function<Boolean, Session> function) {
      return loadUser();
    }

    /**
     * Creates a new AppEngineUserIdentity based on information retrieved from the Users API.
     *
     * @return A AppEngineUserIdentity if a user is logged in, or null otherwise.
     */
    private AppEngineUserIdentity loadUser() {
      UserService userService = UserServiceFactory.getUserService();
      User engineUser = userService.getCurrentUser();
      if (engineUser == null) {
        return null;
      }
      return new AppEngineUserIdentity(new AppEnginePrincipal(engineUser));
    }

    @Override
    public IdentityService getIdentityService() {
      return identityService;
    }

    @Override
    public void logout(UserIdentity user) {
      // Jetty calls this on every request -- even if user is null!
      if (user != null) {
        logger.atFine().log("Ignoring logout call for: %s", user);
      }
    }

    @Override
    public void setIdentityService(IdentityService identityService) {
      this.identityService = identityService;
    }

    /**
     * Validate a user identity. Validate that a UserIdentity previously created by a call to {@link
     * #login(String, Object, ServletRequest)} is still valid.
     *
     * @param user The user to validate
     * @return true if authentication has not been revoked for the user.
     */
    @Override
    public boolean validate(UserIdentity user) {
      logger.atInfo().log("validate(%s) throwing UnsupportedOperationException.", user);
      throw new UnsupportedOperationException();
    }
  }

  /**
   * {@code AppEnginePrincipal} is an implementation of {@link Principal} that represents a
   * logged-in Google App Engine user.
   */
  public static class AppEnginePrincipal implements Principal {
    private final User user;

    public AppEnginePrincipal(User user) {
      this.user = user;
    }

    public User getUser() {
      return user;
    }

    @Override
    public String getName() {
      if ((user.getFederatedIdentity() != null) && (user.getFederatedIdentity().length() > 0)) {
        return user.getFederatedIdentity();
      }
      return user.getEmail();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof AppEnginePrincipal) {
        return user.equals(((AppEnginePrincipal) other).user);
      } else {
        return false;
      }
    }

    @Override
    public String toString() {
      return user.toString();
    }

    @Override
    public int hashCode() {
      return user.hashCode();
    }
  }

  /**
   * {@code AppEngineUserIdentity} is an implementation of {@link UserIdentity} that represents a
   * logged-in Google App Engine user.
   */
  public static class AppEngineUserIdentity implements UserIdentity {

    private final AppEnginePrincipal userPrincipal;

    public AppEngineUserIdentity(AppEnginePrincipal userPrincipal) {
      this.userPrincipal = userPrincipal;
    }

    /*
     * Only used by jaas and jaspi.
     */
    @Override
    public Subject getSubject() {
      logger.atInfo().log("getSubject() throwing UnsupportedOperationException.");
      throw new UnsupportedOperationException();
    }

    @Override
    public Principal getUserPrincipal() {
      return userPrincipal;
    }

    @Override
    public boolean isUserInRole(String role) {
      UserService userService = UserServiceFactory.getUserService();
      logger.atFine().log("Checking if principal %s is in role %s", userPrincipal, role);
      if (userPrincipal == null) {
        logger.atInfo().log("isUserInRole() called with null principal.");
        return false;
      }

      if (USER_ROLE.equals(role)) {
        return true;
      }

      if (ADMIN_ROLE.equals(role)) {
        User user = userPrincipal.getUser();
        if (user.equals(userService.getCurrentUser())) {
          return userService.isUserAdmin();
        } else {
          // TODO: I'm not sure this will happen in
          // practice. If it does, we may need to pass an
          // application's admin list down somehow.
          logger.atSevere().log("Cannot tell if non-logged-in user %s is an admin.", user);
          return false;
        }
      } else {
        logger.atWarning().log("Unknown role: %s.", role);
        return false;
      }
    }

    @Override
    public String toString() {
      return AppEngineUserIdentity.class.getSimpleName() + "('" + userPrincipal + "')";
    }
  }
}
