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

import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

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
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.testing.LocalAppIdentityServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.io.LineProcessor;
import com.google.protobuf.ByteString;
// <internal>
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for the LocalAppIdentityService implementation.
 *
 */
@RunWith(JUnit4.class)
public class LocalAppIdentityServiceTest {
  private final MockClock clock = new MockClock();
  private LocalServiceTestHelper helper;

  private final LocalRpcService.Status status = new LocalRpcService.Status();

  @After
  public void tearDown() throws Exception {
    if (helper != null) {
      helper.tearDown();
    }
  }

  private LocalAppIdentityService createService(LocalAppIdentityServiceTestConfig config,
                                                boolean withClock) {
    helper = new LocalServiceTestHelper(config);
    if (withClock) {
      helper = helper.setClock(clock);
    }
    helper.setUp();
    return (LocalAppIdentityService) LocalServiceTestHelper.getLocalService(
        LocalAppIdentityService.PACKAGE);
  }

  private LocalAppIdentityService createService(LocalAppIdentityServiceTestConfig config) {
    return createService(config, false);
  }

  // <internal>
  @Test
  public void testLocalAppSigning() throws Exception {
    LocalAppIdentityServiceTestConfig config = new LocalAppIdentityServiceTestConfig();
    LocalAppIdentityService service = createService(config);

    String blob = "blob";
    SignForAppRequest.Builder requestBuilder = SignForAppRequest.newBuilder();
    requestBuilder.setBytesToSign(ByteString.copyFromUtf8(blob));
    SignForAppResponse response = service.signForApp(status, requestBuilder.build());
    assertThat(status.isSuccessful()).isTrue();

    GetPublicCertificateForAppRequest certRequest =
        GetPublicCertificateForAppRequest.getDefaultInstance();
    GetPublicCertificateForAppResponse certResponse =
        service.getPublicCertificatesForApp(status, certRequest);
    assertThat(certResponse.getPublicCertificateListCount()).isEqualTo(1);
    String cert = certResponse.getPublicCertificateList(0).getX509CertificatePem();
    PublicKey publicKey = getCertForPem(cert).getPublicKey();

    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initVerify(publicKey);
    signer.update(blob.getBytes(UTF_8));
    assertThat(signer.verify(response.getSignatureBytes().toByteArray())).isTrue();

    GetServiceAccountNameRequest getServiceName = GetServiceAccountNameRequest.getDefaultInstance();
    GetServiceAccountNameResponse getServiceNameResponse =
        service.getServiceAccountName(status, getServiceName);
    assertThat(getServiceNameResponse.getServiceAccountName()).isEqualTo("test@localhost");
  }

  @Test
  public void testLocalGetAccessTokenNoApplicationCreadentials() throws Exception {
    LocalAppIdentityService service = new LocalAppIdentityService() {
      @Override
      String getApplicationDefaultToken(GetAccessTokenRequest request) throws IOException {
        throw new IOException("Cannot get application default credentials");
      }
    };
    service.setClock(clock);
    
    long now = 10002;  // arbitrary seconds since epoch.
    clock.setCurrentTime(now * 1000);
    GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder();
    requestBuilder.addScope("scope1");
    GetAccessTokenResponse response = service.getAccessToken(status, requestBuilder.build());
    // 2000 is 'now' in ms % 10000.
    assertThat(response.getAccessToken()).isEqualTo("InvalidToken:scope1:2000");
    long expectedTime = now + 1800;
    assertThat(response.getExpirationTime()).isEqualTo(expectedTime);
  }

  @Test
  public void testNullLocalGetAccessTokenApplicationCredentials() throws Exception {
    LocalAppIdentityService service = new LocalAppIdentityService() {
      @Override
      String getApplicationDefaultToken(GetAccessTokenRequest request) throws IOException {
        return null;
      }
    };
    service.setClock(clock);

    long now = 10002;  // arbitrary seconds since epoch.
    clock.setCurrentTime(now * 1000);
    GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder();
    requestBuilder.addScope("scope1");
    GetAccessTokenResponse response = service.getAccessToken(status, requestBuilder.build());
    // 2000 is 'now' in ms % 10000.
    assertThat(response.getAccessToken()).isEqualTo("InvalidToken:scope1:2000");
    long expectedTime = now + 1800;
    assertThat(response.getExpirationTime()).isEqualTo(expectedTime);
  }

  @Test
  public void testLocalGetAccessTokenWithApplicationCreadentials() throws Exception {
    LocalAppIdentityService service = new LocalAppIdentityService() {
      @Override
      String getApplicationDefaultToken(GetAccessTokenRequest request) throws IOException {
        return "accessToken: xyz";
      }
    };
    
    GetAccessTokenRequest.Builder requestBuilder = GetAccessTokenRequest.newBuilder();
    requestBuilder.addScope("scope1");
    GetAccessTokenResponse response = service.getAccessToken(status, requestBuilder.build());
    assertThat(response.getAccessToken()).isEqualTo("accessToken: xyz");
  }

  @Test
  public void testLocalGetDefaultGcsBucketNameIsDefault() throws Exception {
    LocalAppIdentityServiceTestConfig config = new LocalAppIdentityServiceTestConfig();
    LocalAppIdentityService service = createService(config);

    GetDefaultGcsBucketNameRequest request = GetDefaultGcsBucketNameRequest.getDefaultInstance();
    GetDefaultGcsBucketNameResponse response = service.getDefaultGcsBucketName(status, request);
    assertThat(response.getDefaultGcsBucketName()).isEqualTo("app_default_bucket");
  }

  @Test
  public void testLocalGetDefaultGcsBucketNameIsNotDefault() throws Exception {
    LocalAppIdentityServiceTestConfig config = new LocalAppIdentityServiceTestConfig();
    config.setDefaultGcsBucketName("nondefault_bucket");
    LocalAppIdentityService service = createService(config);

    GetDefaultGcsBucketNameRequest request = GetDefaultGcsBucketNameRequest.getDefaultInstance();
    GetDefaultGcsBucketNameResponse response = service.getDefaultGcsBucketName(status, request);
    assertThat(response.getDefaultGcsBucketName()).isEqualTo("nondefault_bucket");
  }

  // Sure would be nice if this MockClock was in a central helper file
  // instead of copy/paste/tweak'd to many tests.
  private static class MockClock implements Clock {
    private long currentTime = 0;

    @Override
    public long getCurrentTime() {
      return currentTime;
    }

    public void setCurrentTime(long millis) {
      currentTime = millis;
    }
  }

  private static X509Certificate getCertForPem(String pem) {
    try {
      List<String> base64EncodedDer = CharSource.wrap(pem).readLines(new PemProcessor());
      if (base64EncodedDer.isEmpty()) {
        throw new IllegalArgumentException("Invalid PEM: " + pem);
      }
      return getCertForBase64EncodedDer(base64EncodedDer.get(0));
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid PEM: " + pem, e);
    }
  }

  private static X509Certificate getCertForBase64EncodedDer(String base64EncodedDer) {
    try {
      byte[] certBytes = base64().decode(base64EncodedDer);
      CertificateFactory factory = CertificateFactory.getInstance("X509");
      return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
    } catch (CertificateException e) {
      throw new IllegalArgumentException("Invalid certificate: " + base64EncodedDer, e);
    }
  }

  /**
   * Line processor that scans over the input lines until it finds a line matching /^-----BEGIN/,
   * and then accumulates the input lines until it finds a line matching /^-----END/.
   */
  private static class PemProcessor implements LineProcessor<List<String>> {

    private final ImmutableList.Builder<String> listBuilder = ImmutableList.<String>builder();

    // where we're accumulating the input
    private StringBuilder builder = new StringBuilder();

    // tells us whether we're currently accumulating lines into the builder
    private boolean processLines = false;

    @Override
    public List<String> getResult() {
      return listBuilder.build();
    }

    @Override
    public boolean processLine(String line) {

      line = line.trim();

      // tell the caller to not even bother calling us again once we see
      // a line starting with -----END
      if (processLines && line.contains("----END")) {
        processLines = false;
        listBuilder.add(builder.toString());
        builder = new StringBuilder();
        return true;
      }

      // we start in the state processLines == false. Once we see a line
      // starting with -----BEGIN, we set processLines to true, but return
      // immediately, without pushing this line into the builder. This will have
      // the effect that starting with the next line, we will push lines into
      // the builder.
      if (!processLines && line.contains("----BEGIN")) {
        processLines = true;
        return true;
      }

      if (processLines) {
        builder.append(line);
      }

      // we're not done yet, please feed us more lines
      return true;
    }
  }
}
