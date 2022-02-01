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

import static com.google.appengine.api.appidentity.AppIdentityServiceImpl.MEMCACHE_KEY_PREFIX;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.when;

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
import com.google.appengine.api.memcache.MemcacheSerialization;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheGetResponse;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetRequest;
import com.google.appengine.api.memcache.MemcacheServicePb.MemcacheSetResponse;
import com.google.appengine.api.testing.LocalServiceTestHelperRule;
import com.google.apphosting.api.ApiProxy;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Tests the {@link AppIdentityServiceImpl}.
 *
 */
@RunWith(JUnit4.class)
public class AppIdentityServiceImplTest {
  @Rule public LocalServiceTestHelperRule testHelperRule = new LocalServiceTestHelperRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private AppIdentityService service;

  @Mock private ApiProxy.Delegate<ApiProxy.Environment> mockDelegate;

  private static final String TEST_SERVICE_ACCOUNT_NAME = "test@a.appspot.com";

  private static final String TEST_DEFAULT_GCS_BUCKET_NAME = "app_default_bucket";

  private static final String BLOB = "sign blob";

  private static final String SIG = "sig";

  private static final String KEYNAME = "signing key name";

  private static final String CERT = "CERT";

  private static final String ERROR_DETAIL = "application error detail";

  private static final String ACCESS_TOKEN = "letmeiniamarobot";

  private static final long EXPIRATION_TIME_SEC = (System.currentTimeMillis() / 1000) + 60 * 60;

  private static final Date EXPIRATION_DATE = new Date(EXPIRATION_TIME_SEC * 1000L);

  @Before
  public void setUp() throws Exception {
    ApiProxy.setDelegate(mockDelegate);
    service = new AppIdentityServiceImpl();
    AppIdentityServiceImpl.clearCache();
  }

  @Test
  public void testMemcacheKeyForScopes() {
    Map<String, List<String>> scopesMap =
        new ImmutableMap.Builder<String, List<String>>()
            .put(MEMCACHE_KEY_PREFIX + "[]", ImmutableList.<String>of())
            .put(MEMCACHE_KEY_PREFIX + "['scope1']", ImmutableList.of("scope1"))
            .put(MEMCACHE_KEY_PREFIX + "['scope1','scope2']", ImmutableList.of("scope1", "scope2"))
            .buildOrThrow();
    AppIdentityServiceImpl service = new AppIdentityServiceImpl();
    for (Map.Entry<String, List<String>> entry : scopesMap.entrySet()) {
      assertThat(service.memcacheKeyForScopes(entry.getValue())).isEqualTo(entry.getKey());
    }
  }

  @Test
  public void testSignBlob() {
    SignForAppRequest.Builder requestBuilder = SignForAppRequest.newBuilder();
    requestBuilder.setBytesToSign(ByteString.copyFromUtf8(BLOB));

    SignForAppResponse.Builder responseBuilder = SignForAppResponse.newBuilder();
    responseBuilder.setKeyName(KEYNAME);
    responseBuilder.setSignatureBytes(ByteString.copyFromUtf8(SIG));

    when(mockDelegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(AppIdentityServiceImpl.PACKAGE_NAME),
            eq(AppIdentityServiceImpl.SIGN_FOR_APP_METHOD_NAME),
            eq(requestBuilder.build().toByteArray())))
        .thenReturn(responseBuilder.build().toByteArray());

    AppIdentityService.SigningResult signingResult = service.signForApp(BLOB.getBytes());

    assertThat(signingResult.getKeyName()).isEqualTo(KEYNAME);
    assertThat(new String(signingResult.getSignature(), UTF_8)).isEqualTo(SIG);
  }

  @Test
  public void testGetCerts() {
    GetPublicCertificateForAppRequest request =
        GetPublicCertificateForAppRequest.getDefaultInstance();
    GetPublicCertificateForAppResponse.Builder responseBuilder =
        GetPublicCertificateForAppResponse.newBuilder();
    responseBuilder.addPublicCertificateList(
        AppIdentityServicePb.PublicCertificate.newBuilder()
            .setKeyName(KEYNAME)
            .setX509CertificatePem(CERT));

    when(mockDelegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(AppIdentityServiceImpl.PACKAGE_NAME),
            eq(AppIdentityServiceImpl.GET_CERTS_METHOD_NAME),
            eq(request.toByteArray())))
        .thenReturn(responseBuilder.build().toByteArray());

    List<PublicCertificate> certList =
        (List<PublicCertificate>) service.getPublicCertificatesForApp();

    assertThat(certList).hasSize(1);
    assertThat(certList.get(0).getCertificateName()).isEqualTo(KEYNAME);
    assertThat(certList.get(0).getX509CertificateInPemFormat()).isEqualTo(CERT);
  }

  @Test
  public void testGetAppIdentifier() {
    GetServiceAccountNameRequest request = GetServiceAccountNameRequest.getDefaultInstance();

    GetServiceAccountNameResponse.Builder responseBuilder =
        GetServiceAccountNameResponse.newBuilder();
    responseBuilder.setServiceAccountName(TEST_SERVICE_ACCOUNT_NAME);

    when(mockDelegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(AppIdentityServiceImpl.PACKAGE_NAME),
            eq(AppIdentityServiceImpl.GET_SERVICE_ACCOUNT_NAME_METHOD_NAME),
            eq(request.toByteArray())))
        .thenReturn(responseBuilder.build().toByteArray());

    String serviceAccountName = service.getServiceAccountName();

    assertThat(serviceAccountName).isEqualTo(TEST_SERVICE_ACCOUNT_NAME);
  }

  @Test
  public void testGetDefaultGcsBucketName() {
    GetDefaultGcsBucketNameRequest request = GetDefaultGcsBucketNameRequest.getDefaultInstance();

    GetDefaultGcsBucketNameResponse.Builder responseBuilder =
        GetDefaultGcsBucketNameResponse.newBuilder();
    responseBuilder.setDefaultGcsBucketName(TEST_DEFAULT_GCS_BUCKET_NAME);

    when(mockDelegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(AppIdentityServiceImpl.PACKAGE_NAME),
            eq(AppIdentityServiceImpl.GET_DEFAULT_GCS_BUCKET_NAME),
            eq(request.toByteArray())))
        .thenReturn(responseBuilder.build().toByteArray());

    String defaultGcsBucketName = service.getDefaultGcsBucketName();

    assertThat(defaultGcsBucketName).isEqualTo(TEST_DEFAULT_GCS_BUCKET_NAME);
  }

  @Test
  public void testGetAccessTokenUncached() {
    GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder();
    requestBuilder.addScope("scope1");
    requestBuilder.addScope("scope2");

    GetAccessTokenResponse.Builder responseBuilder = GetAccessTokenResponse.newBuilder();
    responseBuilder.setAccessToken(ACCESS_TOKEN);
    responseBuilder.setExpirationTime(EXPIRATION_TIME_SEC);

    when(mockDelegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(AppIdentityServiceImpl.PACKAGE_NAME),
            eq(AppIdentityServiceImpl.GET_ACCESS_TOKEN_METHOD_NAME),
            eq(requestBuilder.build().toByteArray())))
        .thenReturn(responseBuilder.build().toByteArray());

    AppIdentityService.GetAccessTokenResult getAccessTokenResult =
        service.getAccessTokenUncached(Lists.newArrayList("scope1", "scope2"));

    assertThat(getAccessTokenResult.getAccessToken()).isEqualTo(ACCESS_TOKEN);
    assertThat(getAccessTokenResult.getExpirationTime()).isEqualTo(EXPIRATION_DATE);
  }

  private void expectAsyncMemcacheCall(String methodName, Message request, Message response) {
    when(mockDelegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq("memcache"),
            eq(methodName),
            eq(request.toByteArray()),
            eq(new ApiProxy.ApiConfig())))
        .thenReturn(immediateFuture(response.toByteArray()));
  }

  private void expectAsyncMemcacheCallFailure(String methodName, Message request) {
    when(mockDelegate.makeAsyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq("memcache"),
            eq(methodName),
            eq(request.toByteArray()),
            eq(new ApiProxy.ApiConfig())))
        .thenThrow(new MemcacheServiceException("testing"));
  }

  @Test
  public void testGetAccessTokenCached() throws IOException {
    doTestGetAccessTokenCached(false, MemcacheSetResponse.SetStatusCode.STORED);
  }

  @Test
  public void testGetAccessTokenCachedGetFailed() throws IOException {
    doTestGetAccessTokenCached(true, MemcacheSetResponse.SetStatusCode.STORED);
  }

  @Test
  public void testGetAccessTokenCachedSetFailed() throws IOException {
    doTestGetAccessTokenCached(false, MemcacheSetResponse.SetStatusCode.ERROR);
  }

  private void doTestGetAccessTokenCached(
      boolean shouldGetFail, MemcacheSetResponse.SetStatusCode setStatusCode) throws IOException {
    GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder();
    requestBuilder.addScope("scope1");
    requestBuilder.addScope("scope2");

    GetAccessTokenResponse.Builder responseBuilder = GetAccessTokenResponse.newBuilder();
    responseBuilder.setAccessToken(ACCESS_TOKEN);
    responseBuilder.setExpirationTime(EXPIRATION_TIME_SEC);

    byte[] memcacheKey = MemcacheSerialization.makePbKey("_ah_app_identity_['scope1','scope2']");
    byte[] memcacheValue =
        MemcacheSerialization.serialize(
                new AppIdentityService.GetAccessTokenResult(ACCESS_TOKEN, EXPIRATION_DATE))
            .value;

    // Surely there's a less fragile way to mock out memcache than at the apiproxy level.
    MemcacheGetRequest memcacheRequest =
        MemcacheGetRequest.newBuilder()
            .setNameSpace("_ah_")
            .addKey(ByteString.copyFrom(memcacheKey))
            .build();
    MemcacheGetResponse memcacheEmptyResponse = MemcacheGetResponse.getDefaultInstance();
    MemcacheSetRequest memcacheSetRequest =
        MemcacheSetRequest.newBuilder()
            .setNameSpace("_ah_")
            .addItem(
                MemcacheSetRequest.Item.newBuilder()
                    .setKey(ByteString.copyFrom(memcacheKey))
                    .setFlags(2) // Flag.OBJECT.ordinal()
                    .setValue(ByteString.copyFrom(memcacheValue))
                    .setSetPolicy(MemcacheSetRequest.SetPolicy.SET)
                    .setExpirationTime((int) (EXPIRATION_TIME_SEC - 360))
                    .build())
            .build();
    MemcacheSetResponse memcacheSetResponse =
        MemcacheSetResponse.newBuilder().addSetStatus(setStatusCode).build();

    if (shouldGetFail) {
      expectAsyncMemcacheCallFailure("Get", memcacheRequest);
    } else {
      expectAsyncMemcacheCall("Get", memcacheRequest, memcacheEmptyResponse);
    }
    when(mockDelegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(AppIdentityServiceImpl.PACKAGE_NAME),
            eq(AppIdentityServiceImpl.GET_ACCESS_TOKEN_METHOD_NAME),
            eq(requestBuilder.build().toByteArray())))
        .thenReturn(responseBuilder.build().toByteArray());
    expectAsyncMemcacheCall("Set", memcacheSetRequest, memcacheSetResponse);
    AppIdentityService.GetAccessTokenResult getAccessTokenResult =
        service.getAccessToken(Lists.newArrayList("scope1", "scope2"));

    assertThat(getAccessTokenResult.getAccessToken()).isEqualTo(ACCESS_TOKEN);
    assertThat(getAccessTokenResult.getExpirationTime()).isEqualTo(EXPIRATION_DATE);

    // Now it should be in the cache. Try again!
    MemcacheGetResponse memcacheResponse;
    memcacheResponse =
        MemcacheGetResponse.newBuilder()
            .addItem(
                MemcacheGetResponse.Item.newBuilder()
                    .setKey(ByteString.copyFrom(memcacheKey))
                    .setValue(ByteString.copyFrom(memcacheValue))
                    .setFlags(2)
                    .build())
            .build();

    expectAsyncMemcacheCall("Get", memcacheRequest, memcacheResponse);
    // clear local cache, so we take it from memcache
    AppIdentityServiceImpl.clearCache();
    getAccessTokenResult = service.getAccessToken(Lists.newArrayList("scope1", "scope2"));
    assertThat(getAccessTokenResult.getAccessToken()).isEqualTo(ACCESS_TOKEN);
    assertThat(getAccessTokenResult.getExpirationTime()).isEqualTo(EXPIRATION_DATE);

    // Now, check local cache
    getAccessTokenResult = service.getAccessToken(Lists.newArrayList("scope1", "scope2"));
    assertThat(getAccessTokenResult.getAccessToken()).isEqualTo(ACCESS_TOKEN);
    assertThat(getAccessTokenResult.getExpirationTime()).isEqualTo(EXPIRATION_DATE);
  }

  @Test
  public void testGetAccessTokenNoScope() {
    // Check that requesting no scopes skips the rpc.
    AppIdentityServiceFailureException ex =
        assertThrows(
            AppIdentityServiceFailureException.class,
            () -> service.getAccessTokenUncached(new ArrayList<String>()));
    assertThat(ex).hasMessageThat().isEqualTo("No scopes specified.");
  }

  private void createDelegateException(String methodName, int errorCode, String details) {
    when(mockDelegate.makeSyncCall(
            same(ApiProxy.getCurrentEnvironment()),
            eq(AppIdentityServiceImpl.PACKAGE_NAME),
            eq(methodName),
            any()))
        .thenThrow(new ApiProxy.ApplicationException(errorCode, details));
  }

  private void testSignBlobExceptionImpl(
      AppIdentityServiceError.ErrorCode errorCode,
      Class<? extends Exception> expectedException,
      String expectedErrorMessage) {
    testSignBlobExceptionImpl(errorCode.getNumber(), expectedException, expectedErrorMessage);
  }

  private void testSignBlobExceptionImpl(
      int errorCode, Class<? extends Exception> expectedException, String expectedErrorMessage) {
    createDelegateException(
        AppIdentityServiceImpl.SIGN_FOR_APP_METHOD_NAME, errorCode, ERROR_DETAIL);
    Exception ex = assertThrows(Exception.class, () -> service.signForApp(BLOB.getBytes(UTF_8)));
    assertThat(ex.getClass()).isEqualTo(expectedException);
    assertThat(ex).hasMessageThat().ignoringCase().contains(expectedErrorMessage);
  }

  @Test
  public void testSignBlobExceptions() {
    testSignBlobExceptionImpl(
        AppIdentityServiceError.ErrorCode.BLOB_TOO_LARGE,
        AppIdentityServiceFailureException.class,
        "blob");
    testSignBlobExceptionImpl(
        AppIdentityServiceError.ErrorCode.DEADLINE_EXCEEDED,
        AppIdentityServiceFailureException.class,
        "deadline");
    testSignBlobExceptionImpl(
        AppIdentityServiceError.ErrorCode.NOT_A_VALID_APP,
        AppIdentityServiceFailureException.class,
        "not valid");
    testSignBlobExceptionImpl(
        AppIdentityServiceError.ErrorCode.UNKNOWN_ERROR,
        AppIdentityServiceFailureException.class,
        "unknown error");
    testSignBlobExceptionImpl(
        4, // An error number from gaiamintservice.proto not copied to ErrorCode.
        AppIdentityServiceFailureException.class,
        "unexpected");
  }

  private void testGetAccessTokenExceptionImpl(
      AppIdentityServiceError.ErrorCode errorCode,
      Class<? extends Exception> expectedException,
      String expectedErrorMessage) {
    testGetAccessTokenExceptionImpl(errorCode.getNumber(), expectedException, expectedErrorMessage);
  }

  private void testGetAccessTokenExceptionImpl(
      int errorCode, Class<? extends Exception> expectedException, String expectedErrorMessage) {
    createDelegateException(
        AppIdentityServiceImpl.SIGN_FOR_APP_METHOD_NAME, errorCode, ERROR_DETAIL);
    Exception ex = assertThrows(Exception.class, () -> service.signForApp(BLOB.getBytes(UTF_8)));
    assertThat(ex.getClass()).isEqualTo(expectedException);
    assertThat(ex).hasMessageThat().ignoringCase().contains(expectedErrorMessage);
  }

  @Test
  public void testGetAccessTokenExceptions() {
    testGetAccessTokenExceptionImpl(
        AppIdentityServiceError.ErrorCode.UNKNOWN_SCOPE,
        AppIdentityServiceFailureException.class,
        "unknown scope");
    testGetAccessTokenExceptionImpl(
        AppIdentityServiceError.ErrorCode.DEADLINE_EXCEEDED,
        AppIdentityServiceFailureException.class,
        "deadline");
    testGetAccessTokenExceptionImpl(
        AppIdentityServiceError.ErrorCode.NOT_A_VALID_APP,
        AppIdentityServiceFailureException.class,
        "not valid");
    testGetAccessTokenExceptionImpl(
        AppIdentityServiceError.ErrorCode.UNKNOWN_ERROR,
        AppIdentityServiceFailureException.class,
        "unknown error");
    testGetAccessTokenExceptionImpl(
        AppIdentityServiceError.ErrorCode.NOT_ALLOWED,
        AppIdentityServiceFailureException.class,
        "unexpected");
    testGetAccessTokenExceptionImpl(
        4, // An error number from gaiamintservice.proto not copied to ErrorCode.
        AppIdentityServiceFailureException.class,
        "unexpected");
  }

  private void testParseFullAppId(String fullAppId, String partition, String domain, String id) {
    AppIdentityService.ParsedAppId pid = service.parseFullAppId(fullAppId);
    assertThat(pid.getPartition()).isEqualTo(partition);
    assertThat(pid.getDomain()).isEqualTo(domain);
    assertThat(pid.getId()).isEqualTo(id);
  }

  @Test
  public void testParseFullAppId() {
    testParseFullAppId("b", "", "", "b");
    testParseFullAppId("a:b", "", "a", "b");
    testParseFullAppId("x~b", "x", "", "b");
    testParseFullAppId("x~a:b", "x", "a", "b");

    // Tilda wins overs :
    testParseFullAppId("x:~a:b", "x:", "a", "b");
    testParseFullAppId("x:~b", "x:", "", "b");

    // No empty partitions or domains
    testParseFullAppId("~a", "", "", "~a");
    testParseFullAppId(":a", "", "", ":a");
    testParseFullAppId("x~:a", "x", "", ":a");
  }
}
