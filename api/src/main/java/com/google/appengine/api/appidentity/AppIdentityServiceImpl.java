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

package com.google.appengine.api.appidentity;

import com.google.appengine.api.appidentity.AppIdentityServicePb.AppIdentityServiceError;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetAccessTokenRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetAccessTokenResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetDefaultGcsBucketNameRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetDefaultGcsBucketNameResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetPublicCertificateForAppRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetPublicCertificateForAppResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetServiceAccountNameRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.GetServiceAccountNameResponse;
import com.google.appengine.api.appidentity.AppIdentityServicePb.SignForAppRequest;
import com.google.appengine.api.appidentity.AppIdentityServicePb.SignForAppResponse;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.ApiProxy;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Implementation of the AppIdentityService interface. */
class AppIdentityServiceImpl implements AppIdentityService {

  public static final String PACKAGE_NAME = "app_identity_service";
  public static final String SIGN_FOR_APP_METHOD_NAME = "SignForApp";
  public static final String GET_SERVICE_ACCOUNT_NAME_METHOD_NAME = "GetServiceAccountName";
  public static final String GET_DEFAULT_GCS_BUCKET_NAME = "GetDefaultGcsBucketName";
  public static final String GET_CERTS_METHOD_NAME = "GetPublicCertificatesForApp";
  public static final String GET_ACCESS_TOKEN_METHOD_NAME = "GetAccessToken";
  public static final String MEMCACHE_NAMESPACE = "_ah_";
  public static final String MEMCACHE_KEY_PREFIX = "_ah_app_identity_";

  private static final char APP_PARTITION_SEPARATOR = '~';
  private static final char APP_DOMAIN_SEPARATOR = ':';
  private static final int TOKEN_EXPIRY_SAFETY_MARGIN_MILLIS = 300000;
  private static final int MAX_RANDOM_EXPIRY_DELTA_MILLIS = 60000;
  private static final int MEMCACHE_EXPIRATION_DELTA_MILLIS =
      TOKEN_EXPIRY_SAFETY_MARGIN_MILLIS + MAX_RANDOM_EXPIRY_DELTA_MILLIS;
  private static final int INSTANCE_CACHE_EXPIRATION_DELTA_MILLIS =
      TOKEN_EXPIRY_SAFETY_MARGIN_MILLIS + new Random().nextInt(MAX_RANDOM_EXPIRY_DELTA_MILLIS);
  private static final int MAX_INSTANCE_CACHE_ENTRIES = 100;
  private static final int MAX_CONCURRENT_LOAD_PER_KEY = 3;
  private static LoadingCache<String, CacheItem> cache =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_INSTANCE_CACHE_ENTRIES)
          .build(
              new CacheLoader<String, CacheItem>() {
                @Override
                public CacheItem load(String key) {
                  return new CacheItem();
                }
              });

  private final MemcacheService memcacheService;

  private static class CacheItem {

    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_LOAD_PER_KEY);
    private final AtomicReference<GetAccessTokenResult> result = new AtomicReference<>();

    public Semaphore getAccessSemaphore() {
      return semaphore;
    }

    public @Nullable GetAccessTokenResult get() {
      GetAccessTokenResult value = result.get();
      if (value != null) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MILLISECOND, INSTANCE_CACHE_EXPIRATION_DELTA_MILLIS);
        if (cal.getTime().before(value.getExpirationTime())) {
          return value;
        }
      }
      return null;
    }

    public void set(GetAccessTokenResult value) {
      result.set(value);
    }
  }

  AppIdentityServiceImpl() {
    memcacheService = MemcacheServiceFactory.getMemcacheService(MEMCACHE_NAMESPACE);
  }

  // Used for testing
  static void clearCache() {
    cache.invalidateAll();
  }

  private void handleApplicationError(ApiProxy.ApplicationException e) {
    AppIdentityServiceError.ErrorCode errorCode =
        AppIdentityServiceError.ErrorCode.forNumber(e.getApplicationError());
    if (errorCode == null) {
      throw new AppIdentityServiceFailureException(
          "The AppIdentity service threw an unexpected error. Details: " + e.getErrorDetail());
    }

    switch (errorCode) {
      case BLOB_TOO_LARGE:
        throw new AppIdentityServiceFailureException("The supplied blob was too long.");
      case NOT_A_VALID_APP:
        throw new AppIdentityServiceFailureException("The application is not valid.");
      case DEADLINE_EXCEEDED:
        throw new AppIdentityServiceFailureException("The deadline for the call was exceeded.");
      case UNKNOWN_ERROR:
        throw new AppIdentityServiceFailureException(
            "There was an unknown error using the AppIdentity service.");
      case UNKNOWN_SCOPE:
        throw new AppIdentityServiceFailureException("An unknown scope was supplied.");
      default:
        throw new AppIdentityServiceFailureException(
            "The AppIdentity service threw an unexpected error. Details: " + e.getErrorDetail());
    }
  }

  @Override
  public List<PublicCertificate> getPublicCertificatesForApp() {
    GetPublicCertificateForAppRequest.Builder requestBuilder =
        GetPublicCertificateForAppRequest.newBuilder();
    GetPublicCertificateForAppResponse.Builder responseBuilder =
        GetPublicCertificateForAppResponse.newBuilder();

    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              PACKAGE_NAME, GET_CERTS_METHOD_NAME, requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }
    GetPublicCertificateForAppResponse response = responseBuilder.build();

    List<PublicCertificate> certs = Lists.newArrayList();
    for (AppIdentityServicePb.PublicCertificate cert : response.getPublicCertificateListList()) {
      certs.add(new PublicCertificate(cert.getKeyName(), cert.getX509CertificatePem()));
    }
    return certs;
  }

  @Override
  public SigningResult signForApp(byte[] signBlob) {
    SignForAppRequest.Builder requestBuilder = SignForAppRequest.newBuilder();
    requestBuilder.setBytesToSign(ByteString.copyFrom(signBlob));
    SignForAppResponse.Builder responseBuilder = SignForAppResponse.newBuilder();
    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              PACKAGE_NAME, SIGN_FOR_APP_METHOD_NAME, requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    SignForAppResponse response = responseBuilder.build();
    return new SigningResult(response.getKeyName(), response.getSignatureBytes().toByteArray());
  }

  @Override
  public String getServiceAccountName() {
    GetServiceAccountNameRequest.Builder requestBuilder = GetServiceAccountNameRequest.newBuilder();
    GetServiceAccountNameResponse.Builder responseBuilder =
        GetServiceAccountNameResponse.newBuilder();
    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              getAccessTokenPackageName(),
              GET_SERVICE_ACCOUNT_NAME_METHOD_NAME,
              requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    GetServiceAccountNameResponse response = responseBuilder.build();
    return response.getServiceAccountName();
  }

  @Override
  public String getDefaultGcsBucketName() {
    GetDefaultGcsBucketNameRequest.Builder requestBuilder =
        GetDefaultGcsBucketNameRequest.newBuilder();
    GetDefaultGcsBucketNameResponse.Builder responseBuilder =
        GetDefaultGcsBucketNameResponse.newBuilder();
    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              PACKAGE_NAME, GET_DEFAULT_GCS_BUCKET_NAME, requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    GetDefaultGcsBucketNameResponse response = responseBuilder.build();
    if (response.hasDefaultGcsBucketName()) {
      return response.getDefaultGcsBucketName();
    } else {
      throw new AppIdentityServiceFailureException(
          "getDefaultGcsBucketNameResponse contained no data");
    }
  }

  @Override
  public GetAccessTokenResult getAccessTokenUncached(Iterable<String> scopes) {
    GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder();
    for (String scope : scopes) {
      requestBuilder.addScope(scope);
    }
    if (requestBuilder.getScopeCount() == 0) {
      // Avoid an RPC which will just fail with UNKNOWN_SCOPE.
      throw new AppIdentityServiceFailureException("No scopes specified.");
    }
    GetAccessTokenResponse.Builder responseBuilder = GetAccessTokenResponse.newBuilder();
    try {
      responseBuilder.mergeFrom(
          ApiProxy.makeSyncCall(
              getAccessTokenPackageName(),
              GET_ACCESS_TOKEN_METHOD_NAME,
              requestBuilder.build().toByteArray()));
    } catch (ApiProxy.ApplicationException e) {
      handleApplicationError(e);
    } catch (InvalidProtocolBufferException e) {
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    GetAccessTokenResponse response = responseBuilder.build();
    return new GetAccessTokenResult(
        response.getAccessToken(), new Date(response.getExpirationTime() * 1000));
  }

  private String getAccessTokenPackageName() {
    return Boolean.getBoolean("appengine.app_identity.use_robot")
            && SystemProperty.environment.value() != SystemProperty.Environment.Value.Production
        ? "robot_enabled_app_identity_service"
        : "app_identity_service";
  }

  String memcacheKeyForScopes(Iterable<String> scopes) {
    // Same format as Python MEMCACHE_KEY_PREFIX + scopes
    // We don't allow a single scope in Java (perhaps we should?) so a cross-
    // platform app may use both key_['scope1'] and key_scope1. No big deal.
    // TODO: Even the case of multi-scope is not going to match
    // the Python keys because the latter scope's delimiter is ', '.
    // This is actually good as we cross-platform memcache matching
    // is not desirable (values are serialized differently).
    StringBuilder builder = new StringBuilder();
    builder.append(MEMCACHE_KEY_PREFIX);
    builder.append('[');
    if (!Iterables.isEmpty(scopes)) {
      for (String scope : scopes) {
        builder.append('\'');
        builder.append(scope);
        builder.append("',");
      }
      builder.setLength(builder.length() - 1);
    }
    builder.append(']');
    return builder.toString();
  }

  @Override
  public GetAccessTokenResult getAccessToken(Iterable<String> scopes) {
    String cacheKey = memcacheKeyForScopes(scopes);
    CacheItem cacheItem = cache.getUnchecked(cacheKey);

    try {
      cacheItem.getAccessSemaphore().acquire();
    } catch (InterruptedException e) {
      // reset the interrupt flag and bail out
      Thread.currentThread().interrupt();
      throw new AppIdentityServiceFailureException(e.getMessage());
    }

    try {
      // First, Check locally
      GetAccessTokenResult result = cacheItem.get();
      if (result != null) {
        return result;
      }
      // Second, check at memcache. Though memcache should expire the value before
      // the local cache the latter may not have it upon startup.
      try {
        result = (GetAccessTokenResult) memcacheService.get(cacheKey);
      } catch (MemcacheServiceException e) {
        // Silently ignore this error, since storing the data in memcache
        // is purely an optimization.
      }
      if (result != null) {
        cacheItem.set(result);
        return result;
      }
      // Not found in both caches, get from service and populate caches.
      result = getAccessTokenUncached(scopes);
      Calendar cal = Calendar.getInstance();
      cal.setTime(result.getExpirationTime());
      cal.add(Calendar.MILLISECOND, -1 * MEMCACHE_EXPIRATION_DELTA_MILLIS);
      try {
        memcacheService.put(cacheKey, result, Expiration.onDate(cal.getTime()));
      } catch (MemcacheServiceException e) {
        // Silently ignore this error, since storing the data in memcache
        // is purely an optimization.
      }
      cacheItem.set(result);
      return result;
    } finally {
      cacheItem.getAccessSemaphore().release();
    }
  }

  @Override
  public ParsedAppId parseFullAppId(String fullAppId) {
    int partitionIdx = fullAppId.indexOf(APP_PARTITION_SEPARATOR);
    String partition;
    // ~x should not denote the empty partition to avoid having different
    // app ids mapping to the same ParsedAppId (same for domains below)
    if (partitionIdx > 0) {
      partition = fullAppId.substring(0, partitionIdx);
      fullAppId = fullAppId.substring(partitionIdx + 1);
    } else {
      partition = "";
    }

    int domainIdx = fullAppId.indexOf(APP_DOMAIN_SEPARATOR);
    String domain;
    if (domainIdx > 0) {
      domain = fullAppId.substring(0, domainIdx);
      fullAppId = fullAppId.substring(domainIdx + 1);
    } else {
      domain = "";
    }

    return new ParsedAppId(partition, domain, fullAppId);
  }
}
