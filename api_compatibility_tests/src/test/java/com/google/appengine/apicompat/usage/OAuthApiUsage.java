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

package com.google.appengine.apicompat.usage;

import static com.google.appengine.apicompat.Utils.classes;

import com.google.appengine.api.oauth.IOAuthServiceFactory;
import com.google.appengine.api.oauth.IOAuthServiceFactoryProvider;
import com.google.appengine.api.oauth.InvalidOAuthParametersException;
import com.google.appengine.api.oauth.InvalidOAuthTokenException;
import com.google.appengine.api.oauth.OAuthRequestException;
import com.google.appengine.api.oauth.OAuthService;
import com.google.appengine.api.oauth.OAuthServiceFactory;
import com.google.appengine.api.oauth.OAuthServiceFailureException;
import com.google.appengine.api.users.User;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import java.io.Serializable;
import java.util.Set;

/** Exhaustive usage of the OAuth Api. Used for backward compatibility checks. */
public class OAuthApiUsage {

  /**
   * Exhaustive use of {@link OAuthServiceFactory}.
   */
  public static class OAuthServiceFactoryUsage extends ExhaustiveApiUsage<OAuthServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      @SuppressWarnings("unused")
      OAuthService svc = OAuthServiceFactory.getOAuthService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IOAuthServiceFactory}.
   */
  public static class IOAuthServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IOAuthServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IOAuthServiceFactory iOAuthServiceFactory) {
      iOAuthServiceFactory.getOAuthService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IOAuthServiceFactoryProvider}.
   */
  public static class IOAuthServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IOAuthServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      @SuppressWarnings("unused")
      IOAuthServiceFactoryProvider iOAuthServiceFactoryProvider
          = new IOAuthServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }


  /**
   * Exhaustive use of {@link OAuthService}.
   */
  public static class OAuthServiceUsage extends ExhaustiveApiInterfaceUsage<OAuthService> {
    @Override
    protected Set<Class<?>> useApi(OAuthService oauthService) {
      try {
        @SuppressWarnings("unused")
        User user = oauthService.getCurrentUser();
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        User user = oauthService.getCurrentUser("yar");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        User user = oauthService.getCurrentUser("yar", "bor");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        User user = oauthService.getCurrentUser(new String[]{"yar", "bor"});
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String strVal = oauthService.getOAuthConsumerKey();
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String strVal = oauthService.getClientId("yar");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String strVal = oauthService.getClientId("yar", "bor");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String strVal = oauthService.getClientId(new String[]{"yar", "bor"});
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        boolean boolVal = oauthService.isUserAdmin();
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        boolean boolVal = oauthService.isUserAdmin("yar");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        boolean boolVal = oauthService.isUserAdmin("yar", "bor");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        boolean boolVal = oauthService.isUserAdmin(new String[]{"yar", "bor"});
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String[] scopes = oauthService.getAuthorizedScopes();
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String[] scopes = oauthService.getAuthorizedScopes("yar");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String[] scopes = oauthService.getAuthorizedScopes("yar", "bor");
      } catch (OAuthRequestException e) {
        // ok
      }
      try {
        @SuppressWarnings("unused")
        String[] scopes = oauthService.getAuthorizedScopes(new String[]{"yar", "bor"});
      } catch (OAuthRequestException e) {
        // ok
      }
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link InvalidOAuthParametersException}.
   */
  public static class InvalidOAuthParametersExceptionUsage
      extends ExhaustiveApiUsage<InvalidOAuthParametersException> {

    @Override
    public Set<Class<?>> useApi() {
      @SuppressWarnings("unused")
      InvalidOAuthParametersException ex = new InvalidOAuthParametersException("yar");
      return classes(Object.class, OAuthRequestException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link InvalidOAuthTokenException}.
   */
  public static class InvalidOAuthTokenExceptionUsage
      extends ExhaustiveApiUsage<InvalidOAuthTokenException> {

    @Override
    public Set<Class<?>> useApi() {
      @SuppressWarnings("unused")
      InvalidOAuthTokenException ex = new InvalidOAuthTokenException("yar");
      return classes(Object.class, OAuthRequestException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link OAuthRequestException}.
   */
  public static class OAuthRequestExceptionUsage extends ExhaustiveApiUsage<OAuthRequestException> {

    @Override
    public Set<Class<?>> useApi() {
      @SuppressWarnings("unused")
      OAuthRequestException ex = new OAuthRequestException("yar");
      return classes(Object.class, Exception.class, Throwable.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link OAuthServiceFailureException}.
   */
  public static class OAuthServiceFailureExceptionUsage
      extends ExhaustiveApiUsage<OAuthServiceFailureException> {

    @Override
    public Set<Class<?>> useApi() {
      OAuthServiceFailureException ex = new OAuthServiceFailureException("yar");
      @SuppressWarnings("unused")
      OAuthServiceFailureException ex2 = new OAuthServiceFailureException("yar", ex);
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }
}
