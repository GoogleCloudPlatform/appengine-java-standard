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

import com.google.appengine.api.users.IUserServiceFactory;
import com.google.appengine.api.users.IUserServiceFactoryProvider;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.api.users.UserServiceFailureException;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.Set;

/** Exhaustive usage of the Users Api. Used for backward compatibility checks. */
public class UsersApiUsage {

  /**
   * Exhaustive use of {@link UserServiceFactory}.
   */
  public static class UserServiceFactoryUsage extends ExhaustiveApiUsage<UserServiceFactory> {

    @Override
    @SuppressWarnings("unchecked")
    public Set<Class<?>> useApi() {
      UserServiceFactory.getUserService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IUserServiceFactory}.
   */
  public static class IUserServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IUserServiceFactory> {

    @Override
    public Set<Class<?>> useApi(IUserServiceFactory iUserServiceFactory) {
      iUserServiceFactory.getUserService();

      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IUserServiceFactoryProvider}.
   */
  public static class IUserServiceFactoryProviderUsage extends
  ExhaustiveApiUsage<IUserServiceFactoryProvider> {

    @Override
    @SuppressWarnings("unused")
    public Set<Class<?>> useApi() {
      IUserServiceFactoryProvider p = new IUserServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link UserService}.
   */
  public static class UserServiceUsage extends ExhaustiveApiInterfaceUsage<UserService> {

    @Override
    protected Set<Class<?>> useApi(UserService userService) {
      String str = null;
      userService.createLoginURL(str);
      userService.createLoginURL(str, str);
      userService.createLoginURL(str, str, str, Sets.<String>newHashSet());
      userService.isUserAdmin();
      userService.createLogoutURL(str);
      userService.createLogoutURL(str, str);
      userService.isUserLoggedIn();
      userService.getCurrentUser();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link User}.
   */
  public static class UserUsage extends ExhaustiveApiUsage<User> {

    @Override
    @SuppressWarnings({"unused", "SelfEquals"})
    public Set<Class<?>> useApi() {
      User user = new User("me", "you");
      user = new User("me", "you", "userId");
      user = new User("me", "you", "userId", "federated");
      User user2 = new User("me", "you", "userId", "federated");
      user.getNickname();
      String unusedToString = user.toString();
      user.getAuthDomain();
      user.getUserId();
      user.getFederatedIdentity();
      user.getEmail();
      boolean unusedEquals = user.equals(user);
      int unusedCompareTo = user.compareTo(user2);
      int unusedHashCode = user.hashCode();
      return classes(Object.class, Serializable.class, Comparable.class);
    }
  }

  /**
   * Exhaustive use of {@link UserServiceFailureException}.
   */
  public static class UserServiceFailureExceptionUsage extends
      ExhaustiveApiUsage<UserServiceFailureException> {

    @Override
    @SuppressWarnings({"unused", "ThrowableInstanceNeverThrown"})
    public Set<Class<?>> useApi() {
      UserServiceFailureException unused1 = new UserServiceFailureException("boom");
      UserServiceFailureException unused2 = new UserServiceFailureException("boom", new Error());
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

}
