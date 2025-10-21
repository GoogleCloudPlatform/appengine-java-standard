<!--
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Google App Engine App Identity API Documentation

*   [App Identity API for Bundled Services](#app-identity-api-for-bundled-services)
    *   [Getting the Project ID](#getting-the-project-id)
    *   [Getting the Application Hostname](#getting-the-application-hostname)
    *   [Asserting Identity to Other App Engine Apps](#asserting-identity-to-other-app-engine-apps)
    *   [Asserting Identity to Google APIs](#asserting-identity-to-google-apis)
    *   [Asserting Identity to Third-Party Services](#asserting-identity-to-third-party-services)
    *   [Getting the Default Cloud Storage Bucket Name](#getting-the-default-cloud-storage-bucket-name)

## App Identity API for Bundled Services

The App Identity API lets an application discover its application ID (also
called the project ID). Using the ID, an App Engine application can assert its
identity to other App Engine Apps, Google APIs, and third-party applications and
services. The application ID can also be used to generate a URL or email
address, or to make a run-time decision.

### Getting the Project ID

The project ID can be found using the
`ApiProxy.getCurrentEnvironment().getAppId()` method.

### Getting the Application Hostname

By default, App Engine apps are served from URLs in the form
`https://PROJECT_ID.REGION_ID.r.appspot.com`, where the project ID is part of
the hostname. If an app is served from a custom domain, it may be necessary to
retrieve the entire hostname component. You can do this using the
`com.google.appengine.runtime.default_version_hostname` attribute of the
CurrentEnvironment.

```java
@Override
public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
  resp.setContentType("text/plain");
  ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
  resp.getWriter().print("default_version_hostname: ");
  resp.getWriter()
      .println(
          env.getAttributes().get("com.google.appengine.runtime.default_version_hostname"));
}
```

### Asserting Identity to Other App Engine Apps

If you want to determine the identity of the App Engine app that is making a
request to your App Engine app, you can use the request header
`X-Appengine-Inbound-Appid`. This header is added to the request by the URLFetch
service and is not user modifiable, so it safely indicates the requesting
application's project ID, if present.

**Requirements:**

-   Only calls made to your app's appspot.com domain will contain the
    `X-Appengine-Inbound-Appid` header. Calls to custom domains do not contain
    the header.
-   Your requests must be set to not follow redirects.
-   If you use the URLFetchService class, your app must specify `doNotFollowRedirect`.
-   If your app uses java.net, update your code to not follow redirects: `java connection.setInstanceFollowRedirects(false);`

In your application handler, you can check the incoming ID by reading the
`X-Appengine-Inbound-Appid` header and comparing it to a list of IDs allowed to
make requests.

### Asserting Identity to Google APIs

Google APIs use the OAuth 2.0 protocol for authentication and authorization. The
App Identity API can create OAuth tokens that can be used to assert that the
source of a request is the application itself. The `getAccessToken()` method
returns an access token for a scope, or list of scopes. This token can then be
set in the HTTP headers of a call to identify the calling application.

**Example:**

```java
/**
 * Returns a shortened URL by calling the Google URL Shortener API.
 *
 * <p>Note: Error handling elided for simplicity.
 */
public String createShortUrl(String longUrl) throws Exception {
  ArrayList<String> scopes = new ArrayList<>();
  scopes.add("https://www.googleapis.com/auth/urlshortener");
  final AppIdentityService appIdentity = AppIdentityServiceFactory.getAppIdentityService();
  final AppIdentityService.GetAccessTokenResult accessToken = appIdentity.getAccessToken(scopes);
  // The token asserts the identity reported by appIdentity.getServiceAccountName()
  JSONObject request = new JSONObject();
  request.put("longUrl", longUrl);
  URL url = new URL("https://www.googleapis.com/urlshortener/v1/url?pp=1");
  HttpURLConnection connection = (HttpURLConnection) url.openConnection();
  connection.setDoOutput(true);
  connection.setRequestMethod("POST");
  connection.addRequestProperty("Content-Type", "application/json");
  connection.addRequestProperty("Authorization", "Bearer " + accessToken.getAccessToken());
  OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
  request.write(writer);
  writer.close();
  if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
    JSONTokener responseTokens = new JSONTokener(connection.getInputStream());
    JSONObject response = new JSONObject(responseTokens);
    return (String) response.get("id");
  } else {
    try (InputStream s = connection.getErrorStream();
        InputStreamReader r = new InputStreamReader(s, StandardCharsets.UTF_8)) {
      throw new RuntimeException(
          String.format(
              "got error (%d) response %s from %s",
              connection.getResponseCode(), CharStreams.toString(r), connection.toString()));
    }
  }
}
```

**Note:** The Google API Client Libraries can also manage much of this for you
automatically. Note that the application's identity is represented by the
service account name, which is typically
`applicationid@appspot.gserviceaccount.com`. You can get the exact value by
using the `getServiceAccountName()` method. For services which offer ACLs, you
can grant the application access by granting this account access.

### Asserting Identity to Third-Party Services

The token generated by `getAccessToken()` method only works against Google
services. However you can use the underlying signing technology to assert the
identity of your application to other services. The `signForApp()` method will
sign bytes using a private key unique to your application, and the
`getPublicCertificatesForApp()` method will return certificates which can be
used to validate the signature.

**Note:** The certificates may be rotated from time to time, and the method may
return multiple certificates. Only certificates that are currently valid are
returned.

**Example:**

```java
// Note that the
// algorithm used by AppIdentity.signForApp() and // getPublicCertificatesForApp()
// is "SHA256withRSA"
private byte[] signBlob(byte[] blob) {
  AppIdentityService.SigningResult result = appIdentity.signForApp(blob);
  return result.getSignature();
}

private byte[] getPublicCertificate() throws UnsupportedEncodingException {
  Collection<PublicCertificate> certs = appIdentity.getPublicCertificatesForApp();
  PublicCertificate publicCert = certs.iterator().next();
  return publicCert.getX509CertificateInPemFormat().getBytes("UTF-8");
}

private Certificate parsePublicCertificate(byte[] publicCert)
    throws CertificateException, NoSuchAlgorithmException {
  InputStream stream = new ByteArrayInputStream(publicCert);
  CertificateFactory cf = CertificateFactory.getInstance("X.509");
  return cf.generateCertificate(stream);
}

private boolean verifySignature(byte[] blob, byte[] blobSignature, PublicKey pk)
    throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
  Signature signature = Signature.getInstance("SHA256withRSA");
  signature.initVerify(pk);
  signature.update(blob);
  return signature.verify(blobSignature);
}

private String simulateIdentityAssertion()
    throws CertificateException, UnsupportedEncodingException, NoSuchAlgorithmException,
        InvalidKeyException, SignatureException {
  // Simulate
  // the sending app.
  String message = "abcdefg " + Calendar.getInstance().getTime().toString();
  byte[] blob = message.getBytes();
  byte[] blobSignature = signBlob(blob);
  byte[] publicCert = getPublicCertificate();
  // Simulate the receiving app, which gets the
  // certificate, blob, and signature
  Certificate cert = parsePublicCertificate(publicCert);
  PublicKey pk = cert.getPublicKey();
  boolean isValid = verifySignature(blob, blobSignature, pk);
  return String.format(
      "isValid=%b for message: %s\n\tsignature: %s\n\tpublic cert: %s",
      isValid, message, Arrays.toString(blobSignature), Arrays.toString(publicCert));
}
```

### Getting the Default Cloud Storage Bucket Name

Each application can have one default Cloud Storage bucket, which includes 5GB
of free storage and a free quota for I/O operations. To get the name of the
default bucket, you can use the App Identity API. Call
`AppIdentityService.getDefaultGcsBucketName()`.
