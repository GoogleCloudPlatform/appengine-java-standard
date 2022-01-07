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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.appengine.api.appidentity.AppIdentityServicePb;
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
import com.google.appengine.tools.development.AbstractLocalRpcService;
import com.google.appengine.tools.development.Clock;
import com.google.appengine.tools.development.LocalRpcService;
import com.google.appengine.tools.development.LocalServiceContext;
import com.google.apphosting.api.ApiProxy;
import com.google.auto.service.AutoService;
import com.google.common.base.CharMatcher;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
// <internal>
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stub implementation of the app identity service.
 *
 */
@AutoService(LocalRpcService.class)
public class LocalAppIdentityService extends AbstractLocalRpcService {

  private static final Logger log = Logger.getLogger(LocalAppIdentityService.class.getName());
  public static final String PACKAGE = "app_identity_service";

  public static final String PRIVATE_KEY_PATH =
    "/com/google/appengine/api/appidentity/dev/testkey/private";

  public static final String PUBLIC_CERT_PATH =
    "/com/google/appengine/api/appidentity/dev/testkey/public-pem";

  private static final String KEY_NAME = "key";

  private RSAPrivateKey privateKey;

  private String publicCert;

  private Clock clock;

  private String defaultGcsBucketName;

  /** {@inheritDoc} */
  @Override
  public String getPackage() {
    return PACKAGE;
  }

  /** {@inheritDoc} **/
  @Override
  public void init(LocalServiceContext context, Map<String, String> properties) {
    try {
      // Load private key.
      InputStream in = LocalAppIdentityService.class.getResourceAsStream(PRIVATE_KEY_PATH);
      byte[] bytes = new byte[in.available()];
      in.read(bytes);
      String privateKeyBytes = new String(bytes, UTF_8);
      // Load public cert.
      in = LocalAppIdentityService.class.getResourceAsStream(PUBLIC_CERT_PATH);
      bytes = new byte[in.available()];
      in.read(bytes);
      publicCert = new String(bytes, UTF_8);

      EncodedKeySpec spec =
          new PKCS8EncodedKeySpec(
              BaseEncoding.base64().decode(CharMatcher.whitespace().removeFrom(privateKeyBytes)));
      KeyFactory fac;
      fac = KeyFactory.getInstance("RSA");
      privateKey = (RSAPrivateKey) fac.generatePrivate(spec);
      defaultGcsBucketName = properties.get("appengine.default.gcs.bucket.name");
      if (defaultGcsBucketName == null) {
        defaultGcsBucketName = "app_default_bucket";
      }

      this.clock = context.getClock();
    } catch (GeneralSecurityException | IllegalArgumentException | IOException e) {
      throw new RuntimeException("Can not initialize app identity service.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void start() {
  }

  /** {@inheritDoc} */
  @Override
  public void stop() {
  }

  // <internal>
  public SignForAppResponse signForApp(Status status, SignForAppRequest request) throws Exception {
    SignForAppResponse.Builder responseBuilder = SignForAppResponse.newBuilder();
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey);
    signature.update(request.getBytesToSign().toByteArray());
    responseBuilder.setSignatureBytes(ByteString.copyFrom(signature.sign()));
    responseBuilder.setKeyName(KEY_NAME);
    return responseBuilder.build();
  }

  public GetPublicCertificateForAppResponse getPublicCertificatesForApp(
      Status status, GetPublicCertificateForAppRequest request) {
    GetPublicCertificateForAppResponse.Builder responseBuilder =
      GetPublicCertificateForAppResponse.newBuilder();
    responseBuilder.addPublicCertificateList(
        AppIdentityServicePb.PublicCertificate.newBuilder()
        .setKeyName(KEY_NAME).setX509CertificatePem(publicCert));
    return responseBuilder.build();
  }

  public GetServiceAccountNameResponse getServiceAccountName(
      Status status, GetServiceAccountNameRequest request) {
    ApiProxy.Environment environment = ApiProxy.getCurrentEnvironment();

    GetServiceAccountNameResponse.Builder responseBuilder =
      GetServiceAccountNameResponse.newBuilder();
    responseBuilder.setServiceAccountName(environment.getAppId() + "@localhost");
    return responseBuilder.build();
  }

  public GetDefaultGcsBucketNameResponse getDefaultGcsBucketName(
      Status status, GetDefaultGcsBucketNameRequest request) {
    GetDefaultGcsBucketNameResponse.Builder responseBuilder =
      GetDefaultGcsBucketNameResponse.newBuilder();

    // For the dev_appserver, the default is set with the --default_gcs_bucket command line option.
    // For unit tests, it can be set with LocalAppIdentityServiceTestConfig.setDefaultGcsBucketName.
    responseBuilder.setDefaultGcsBucketName(defaultGcsBucketName);

    return responseBuilder.build();
  }

  public GetAccessTokenResponse getAccessToken(Status status, GetAccessTokenRequest request) {
    GetAccessTokenResponse.Builder responseBuilder = GetAccessTokenResponse.newBuilder();
    String token = null;
    try {
      token = getApplicationDefaultToken(request);
      if (token != null) {
        responseBuilder.setAccessToken(token);
        return responseBuilder.build();
      }
    } catch (IOException ignored) {
      // Possible, when we cannot read default location for credentials, so token is still null.
    }
    // Return an invalid token. There is no application default and dev_appserver does not have
    // access to an actual service account.
    log.log(
        Level.WARNING,
        "No Application Default credential, using an invalid token. "
            + " If needed, you may want to call the gcloud command: "
            + "`gcloud auth application-default login`");

    StringBuilder builder = new StringBuilder();
    builder.append("InvalidToken");
    for (String scope : request.getScopeList()) {
      builder.append(":");
      builder.append(scope);
    }
    builder.append(":");
    builder.append(this.clock.getCurrentTime() % 10000);
    responseBuilder.setAccessToken(builder.toString());
    // We're using 3600 secs in the real service, but let's use half that here.
    responseBuilder.setExpirationTime(this.clock.getCurrentTime() / 1000 + 1800);

    return responseBuilder.build();
  }

  // @VisibleForTesting
  String getApplicationDefaultToken(GetAccessTokenRequest request) throws IOException {
    GoogleCredential credential = GoogleCredential.getApplicationDefault();
    if (credential.getClass().getSimpleName().equals("AppEngineCredentialWrapper")) {
      // If somehow we get back an instance of this class, we should not try to use it. It will
      // attempt to implement the credential with calls to the app_identity service, which will
      // end up looping back recursively to us.
      return null;
    }
    if (credential.createScopedRequired() && !request.getScopeList().isEmpty()) {
      credential = credential.createScoped(request.getScopeList());
    }
    credential.refreshToken();
    return credential.getAccessToken();
  }

  // @VisibleForTesting
  void setClock(Clock clock) {
    this.clock = clock;
  }

  // @VisibleForTesting
  RSAPrivateKey getPrivateKey() {
    return privateKey;
  }
}
