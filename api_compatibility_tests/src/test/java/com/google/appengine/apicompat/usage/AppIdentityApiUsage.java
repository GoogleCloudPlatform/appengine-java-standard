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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.appidentity.AppIdentityServiceFailureException;
import com.google.appengine.api.appidentity.IAppIdentityServiceFactory;
import com.google.appengine.api.appidentity.IAppIdentityServiceFactoryProvider;
import com.google.appengine.api.appidentity.PublicCertificate;
import com.google.appengine.apicompat.ExhaustiveApiInterfaceUsage;
import com.google.appengine.apicompat.ExhaustiveApiUsage;
import com.google.appengine.spi.FactoryProvider;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

/** Exhaustive usage of the App Identity Api. Used for backward compatibility checks. */
public class AppIdentityApiUsage {

  /**
   * Exhaustive use of {@link AppIdentityServiceFactory}.
   */
  public static class AppIdentityServiceFactoryUsage
      extends ExhaustiveApiUsage<AppIdentityServiceFactory> {

    @Override
    public Set<Class<?>> useApi() {
      AppIdentityServiceFactory.getAppIdentityService();
      return classes(Object.class);
    }
  }

  /**
   * Exhaustive use of {@link IAppIdentityServiceFactory}.
   */
  public static class IAppIdentityServiceFactoryUsage
      extends ExhaustiveApiInterfaceUsage<IAppIdentityServiceFactory> {

    @Override
    protected Set<Class<?>> useApi(IAppIdentityServiceFactory iAppIdentityServiceFactory)
    {
      iAppIdentityServiceFactory.getAppIdentityService();
      return classes();
    }
  }

  /**
   * Exhaustive use of {@link IAppIdentityServiceFactoryProvider}.
   */
  public static class IAppIdentityServiceFactoryProviderUsage
      extends ExhaustiveApiUsage<IAppIdentityServiceFactoryProvider> {

    @Override
    public Set<Class<?>> useApi() {
      IAppIdentityServiceFactoryProvider iAppIdentityServiceFactoryProvider
          = new IAppIdentityServiceFactoryProvider();
      return classes(FactoryProvider.class, Comparable.class, Object.class);
    }
  }

  /**
   * Exhaustive use of {@link AppIdentityService}.
   */
  public static class AppIdentityServiceUsage
      extends ExhaustiveApiInterfaceUsage<AppIdentityService> {

    @Override
    protected Set<Class<?>> useApi(AppIdentityService appIdentityService) {
      AppIdentityService.GetAccessTokenResult getAccessTokenResult =
          appIdentityService.getAccessToken(Arrays.<String>asList());
      getAccessTokenResult = appIdentityService.getAccessTokenUncached(Arrays.<String>asList());
      Collection<PublicCertificate> publicCertsResult =
          appIdentityService.getPublicCertificatesForApp();
      String stringResult = appIdentityService.getServiceAccountName();
      stringResult = appIdentityService.getDefaultGcsBucketName();
      AppIdentityService.ParsedAppId parsedAppId = appIdentityService.parseFullAppId("yar");
      AppIdentityService.SigningResult signingResult =
          appIdentityService.signForApp("yar".getBytes(UTF_8));
      return classes();
    }
  }


  /**
   * Exhaustive use of {@link AppIdentityServiceFailureException}.
   */
  public static class AppIdentityServiceFailureExceptionUsage
      extends ExhaustiveApiUsage<AppIdentityServiceFailureException> {

    @Override
    public Set<Class<?>> useApi() {
      AppIdentityServiceFailureException ex = new AppIdentityServiceFailureException("boom");
      return classes(Object.class, RuntimeException.class, Exception.class, Throwable.class,
          Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link AppIdentityService.GetAccessTokenResult}.
   */
  public static class GetAccessTokenResultUsage
      extends ExhaustiveApiUsage<AppIdentityService.GetAccessTokenResult> {

    @Override
    public Set<Class<?>> useApi() {
      AppIdentityService.GetAccessTokenResult result =
          new AppIdentityService.GetAccessTokenResult("this", new Date());
      String token = result.getAccessToken();
      Date expTime = result.getExpirationTime();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link PublicCertificate}.
   */
  public static class PublicCertificateUsage extends ExhaustiveApiUsage<PublicCertificate> {

    @Override
    public Set<Class<?>> useApi() {
      PublicCertificate cert = new PublicCertificate("this", "that");
      String strVal = cert.getCertificateName();
      strVal = cert.getX509CertificateInPemFormat();
      return classes(Object.class, Serializable.class);
    }
  }

  /**
   * Exhaustive use of {@link AppIdentityService.ParsedAppId}.
   */
  public static class ParsedAppIdUsage extends ExhaustiveApiUsage<AppIdentityService.ParsedAppId> {

    @Override
    public Set<Class<?>> useApi() {
      LocalServiceTestHelper helper = new LocalServiceTestHelper();
      helper.setUp();
      try {
        AppIdentityService svc = AppIdentityServiceFactory.getAppIdentityService();
        AppIdentityService.ParsedAppId parsedAppId = svc.parseFullAppId("blar");
        String strVal = parsedAppId.getDomain();
        strVal = parsedAppId.getId();
        strVal = parsedAppId.getPartition();
        return classes(Object.class);
      } finally {
        helper.tearDown();
      }
    }
  }

  /**
   * Exhaustive use of {@link AppIdentityService.SigningResult}.
   */
  public static class SigningResultUsage
      extends ExhaustiveApiUsage<AppIdentityService.SigningResult> {

    @Override
    public Set<Class<?>> useApi() {
      AppIdentityService.SigningResult result =
          new AppIdentityService.SigningResult("yar", "yar".getBytes(UTF_8));
      String keyName = result.getKeyName();
      byte[] bytes = result.getSignature();
      return classes(Object.class);
    }
  }
}
