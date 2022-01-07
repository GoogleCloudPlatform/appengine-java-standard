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

package com.google.appengine.api.appidentity.dev;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.appengine.api.appidentity.AppIdentityService;
import com.google.appengine.api.appidentity.AppIdentityServiceFactory;
import com.google.appengine.api.appidentity.PublicCertificate;
import com.google.appengine.tools.development.testing.LocalAppIdentityServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.Lists;
// <internal>
import java.io.IOException;
import java.security.Signature;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for the {@link AppIdentityService}.
 */
@RunWith(JUnit4.class)
public class LocalAppIdentityServiceIntegrationTest {

  private AppIdentityService service;
  private LocalAppIdentityService localService;
  LocalServiceTestHelper helper;

  private static final String TEST_SERVICE_ACCOUNT_NAME = "test@localhost";
  private static final String TEST_DEFAULT_GCS_BUCKET_NAME = "app_default_bucket";
  private static final String BLOB = "sign blob";
  private static final String KEYNAME = "key";
  private static final String ACCESS_TOKEN_PREFIX = "InvalidToken:scope1:scope2";

  @Before
  public void setUp() throws Exception {
    helper = new LocalServiceTestHelper(new LocalAppIdentityServiceTestConfig());
    helper.setUp();
    service = AppIdentityServiceFactory.getAppIdentityService();
    localService =
        (LocalAppIdentityService) LocalServiceTestHelper.getLocalService("app_identity_service");
  }

  // <internal>
  @Test
  public void testSignBlob() throws Exception {
    AppIdentityService.SigningResult signingResult = service.signForApp(BLOB.getBytes(UTF_8));

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(localService.getPrivateKey());
    signature.update(BLOB.getBytes(UTF_8));

    assertThat(signingResult.getKeyName()).isEqualTo(KEYNAME);
    assertThat(signingResult.getSignature()).isEqualTo(signature.sign());
  }

  @Test
  public void testGetCerts() {
    List<PublicCertificate> certList =
        (List<PublicCertificate>) service.getPublicCertificatesForApp();
    String pemCertificate = certList.get(0).getX509CertificateInPemFormat().trim();

    assertThat(certList).hasSize(1);
    assertThat(certList.get(0).getCertificateName()).isEqualTo(KEYNAME);
    assertThat(pemCertificate).startsWith("-----BEGIN CERTIFICATE-----");
    assertThat(pemCertificate).endsWith("-----END CERTIFICATE-----");
  }

  @Test
  public void testGetAppIdentifier() {
    String serviceAccountName = service.getServiceAccountName();

    assertThat(serviceAccountName).isEqualTo(TEST_SERVICE_ACCOUNT_NAME);
  }

  @Test
  public void testGetDefaultGcsBucketName() {
    String defaultGcsBucketName = service.getDefaultGcsBucketName();

    assertThat(defaultGcsBucketName).isEqualTo(TEST_DEFAULT_GCS_BUCKET_NAME);
  }

  @Test
  public void testSetDefaultGcsBucketName() {
    LocalAppIdentityServiceTestConfig config = new LocalAppIdentityServiceTestConfig()
        .setDefaultGcsBucketName("foo");
    helper = new LocalServiceTestHelper(config);
    helper.setUp();
    String defaultGcsBucketName = service.getDefaultGcsBucketName();

    assertThat(defaultGcsBucketName).isEqualTo("foo");
  }

  private boolean haveApplicationDefaultCredential() {
    try {
      GoogleCredential.getApplicationDefault();
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }

  @Test
  public void testGetAccessTokenUncached() throws Exception {
    AppIdentityService.GetAccessTokenResult getAccessTokenResult =
        service.getAccessTokenUncached(Lists.newArrayList("scope1", "scope2"));

    /*
     * We validate the test token below  unless the calling user created an application
     * default credential which causes us to have a real token.
     */
    if (!haveApplicationDefaultCredential()) {
      assertThat(getAccessTokenResult.getAccessToken()).startsWith(ACCESS_TOKEN_PREFIX);
    }
  }

  @Test
  public void testGetAccessTokenCached() {
    AppIdentityService.GetAccessTokenResult getAccessTokenResult =
        service.getAccessToken(Lists.newArrayList("scope1", "scope2"));

    /*
     * We validate the test token below  unless the calling user created an application
     * default credential which causes us to have a real token.
     */
    if (!haveApplicationDefaultCredential()) {
      assertThat(getAccessTokenResult.getAccessToken()).startsWith(ACCESS_TOKEN_PREFIX);
    }
  }
}
