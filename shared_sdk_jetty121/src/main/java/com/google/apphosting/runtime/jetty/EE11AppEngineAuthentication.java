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
import com.google.apphosting.runtime.jetty.AppEngineAuthentication.AppEnginePrincipal;
import com.google.apphosting.runtime.jetty.AppEngineAuthentication.AppEngineUserIdentity;
import com.google.common.flogger.GoogleLogger;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Function;
import javax.security.auth.Subject;
import org.eclipse.jetty.ee11.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.security.AuthenticationState;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.security.authentication.LoginAuthenticator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Session;
import org.eclipse.jetty.util.Callback;

/**
 * {@code AppEngineAuthentication} is a utility class that can configure a Jetty {@link
 * SecurityHandler} to integrate with the App Engine authentication model.
 *
 * <p>Specifically, it registers a custom {@link Authenticator} instance that knows how to redirect
 * users to a login URL using the {@link UserService}, and a custom {@link UserIdentity} that is
 * aware of the custom roles provided by the App Engine.
 */
public class EE11AppEngineAuthentication {
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
  public static ConstraintSecurityHandler newSecurityHandler() {
    ConstraintSecurityHandler handler = new ConstraintSecurityHandler()
    {
      @Override
      protected Constraint getConstraint(String pathInContext, Request request) {
        if (request.getAttribute(SKIP_ADMIN_CHECK_ATTR) != null) {
          logger.atFine().log("Returning DeferredAuthentication because of SkipAdminCheck.");
          // Warning: returning ALLOWED here will bypass security restrictions!
          return Constraint.ALLOWED;
        }

        return super.getConstraint(pathInContext, request);
      }
    };

    AppEngineLoginService loginService = new AppEngineLoginService();
    AppEngineAuthenticator authenticator = new AppEngineAuthenticator();
    DefaultIdentityService identityService = new DefaultIdentityService();

    // Set allowed roles.
    handler.setRoles(new HashSet<>(Arrays.asList(USER_ROLE, ADMIN_ROLE)));
    handler.setLoginService(loginService);
    handler.setAuthenticator(authenticator);
    handler.setIdentityService(identityService);
    authenticator.setConfiguration(handler);
    return handler;
  }

  /**
   * {@code AppEngineAuthenticator} is a custom {@link LoginAuthenticator} that knows how to redirect the
   * current request to a login URL in order to authenticate the user.
   */
  private static class AppEngineAuthenticator extends LoginAuthenticator {
    /**
     * Checks if the request could go to the login page.
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

      // Check this before checking if there is a user logged in, so
      // that we can log out properly.  Specifically, watch out for
      // the case where the user logs in, but as a role that isn't
      // allowed to see /*.  They should still be able to log out.
      if (isLoginOrErrorPage(pathInContext)) {
        logger.atFine().log(
                "Got %s, returning DeferredAuthentication to imply authentication is in progress.",
                pathInContext);
        return Constraint.Authorization.ALLOWED;
      }

      return super.getConstraintAuthentication(pathInContext, existing, getSession);
    }

    /**
     * Validate a response. Compare to:
     * j.c.g.apphosting.utils.jetty.AppEngineAuthentication.AppEngineAuthenticator.authenticate().
     *
     * <p>If authentication is required but the request comes from an untrusted ip, 307s the request
     * back to the trusted appserver. Otherwise, it will auth the request and return a login url if
     * needed.
     *
     * <p>From org.eclipse.jetty.server.Authentication:
     *
     * @param req The request
     * @param res The response
     * @param cb The callback
     */
    @Override
    public AuthenticationState validateRequest(Request req, Response res, Callback cb) {
      UserService userService = UserServiceFactory.getUserService();
      // If the user is authenticated already, just create a
      // AppEnginePrincipal or AppEngineFederatedPrincipal for them.
      if (userService.isUserLoggedIn()) {
        UserIdentity user = _loginService.login(null, null, null, null);
        logger.atFine().log("authenticate() returning new principal for %s", user);
        if (user != null) {
          return new LoginAuthenticator.UserAuthenticationSucceeded(getAuthenticationType(), user);
        }
      }

      if (AuthenticationState.Deferred.isDeferred(res)) {
        return null;
      }

      try {
        logger.atFine().log(
            "Got %s but no one was logged in, redirecting.", req.getHttpURI().getPath());
        String url = userService.createLoginURL(HttpURI.build(req.getHttpURI()).asString());
        Response.sendRedirect(req, res, cb, url);
        // Tell Jetty that we've already committed a response here.
        return AuthenticationState.CHALLENGE;
      } catch (ApiProxy.ApiProxyException ex) {
        // If we couldn't get a login URL for some reason, return a 403 instead.
        logger.atSevere().withCause(ex).log("Could not get login URL:");
        Response.writeError(req, res, cb, HttpServletResponse.SC_FORBIDDEN);
        return AuthenticationState.SEND_FAILURE;
      }
    }
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
    private AppEngineUserIdentity  loadUser() {
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

    @Override
    public boolean validate(UserIdentity user) {
      logger.atInfo().log("validate(%s) throwing UnsupportedOperationException.", user);
      throw new UnsupportedOperationException();
    }
  }
}
